package com.chuckfarah.streaminghistory.ui.screen.camera

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.data.prefs.UserPreferences
import com.chuckfarah.streaminghistory.di.DefaultDispatcher
import com.chuckfarah.streaminghistory.di.IoDispatcher
import com.chuckfarah.streaminghistory.domain.matching.TitleMatcher
import javax.inject.Named
import com.chuckfarah.streaminghistory.domain.model.MatchResult
import com.chuckfarah.streaminghistory.domain.ocr.OcrCandidateExtractor
import com.chuckfarah.streaminghistory.domain.ocr.OcrMatchedCandidate
import com.chuckfarah.streaminghistory.domain.ocr.OcrResult
import com.chuckfarah.streaminghistory.domain.ocr.TextRecognizer
import com.chuckfarah.streaminghistory.domain.ocr.TextRecognizerOutput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** States of the optional Google Cloud Vision fallback consent/processing flow. */
sealed class VisionFallbackState {
    object Idle : VisionFallbackState()
    object AwaitingConsent : VisionFallbackState()
    object Recognizing : VisionFallbackState()
}

/**
 * Outcome of an enhanced (Google Cloud Vision) recognition attempt.
 *
 * [None]            – not attempted yet, or cleared.
 * [Success]         – Vision ran and produced a confident or ambiguous match.
 * [NoConfidentMatch]– Vision ran but the result was still below the confidence threshold.
 * [Error]           – Vision call failed due to a network or API error.
 * [Unavailable]     – Vision is disabled or no image was available to process.
 */
enum class EnhancedRecognitionOutcome {
    None, Success, NoConfidentMatch, Error, Unavailable
}

/**
 * Holds the captured camera image and drives the OCR → title-matching pipeline
 * for the camera flow (Steps 9–12).
 *
 * The captured [Bitmap] is held in memory only while this ViewModel is in scope.
 * It is never persisted.  The matching logic is the same [TitleMatcher] used by
 * manual search, so camera and manual search share the normalization, scoring,
 * and confidence behavior.
 *
 * ML Kit is always the primary recognizer.  Google Cloud Vision is an optional,
 * consent-gated fallback triggered from the uncertain-match UI.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    @Named("mlKit") private val mlKitTextRecognizer: TextRecognizer,
    @Named("vision") private val visionTextRecognizer: TextRecognizer,
    private val candidateExtractor: OcrCandidateExtractor,
    private val titleMatcher: TitleMatcher,
    private val userPreferences: UserPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _capturedImage = MutableStateFlow<Bitmap?>(null)
    val capturedImage: StateFlow<Bitmap?> = _capturedImage.asStateFlow()

    private val _ocrResult = MutableStateFlow<OcrResult?>(null)
    val ocrResult: StateFlow<OcrResult?> = _ocrResult.asStateFlow()

    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing.asStateFlow()

    private val _visionFallbackState = MutableStateFlow<VisionFallbackState>(VisionFallbackState.Idle)
    val visionFallbackState: StateFlow<VisionFallbackState> = _visionFallbackState.asStateFlow()

    private val _enhancedRecognitionOutcome =
        MutableStateFlow(EnhancedRecognitionOutcome.None)
    val enhancedRecognitionOutcome: StateFlow<EnhancedRecognitionOutcome> =
        _enhancedRecognitionOutcome.asStateFlow()

    /** Whether the user has enabled the Vision fallback in Settings. */
    val visionEnabled: Boolean get() = userPreferences.visionEnabled

    /** Called by [CameraScreen] after CameraX has produced a captured [Bitmap]. */
    fun onImageCaptured(bitmap: Bitmap) {
        _capturedImage.value = bitmap
    }

    /** Clears the captured image and any OCR result (e.g., when the user taps "Retake"). */
    fun clearImage() {
        _capturedImage.value = null
        _ocrResult.value = null
        _visionFallbackState.value = VisionFallbackState.Idle
        _enhancedRecognitionOutcome.value = EnhancedRecognitionOutcome.None
    }

    /**
     * Run the bundled ML Kit OCR and then match each candidate title through
     * the existing [titleMatcher].  The strongest match is published in
     * [ocrResult] together with diagnostic information.
     */
    fun recognizeCapturedImage() {
        viewModelScope.launch {
            val bitmap = _capturedImage.value
            if (bitmap == null) {
                _isRecognizing.value = false
                return@launch
            }

            _isRecognizing.value = true

            val result = try {
                recognizeWith(mlKitTextRecognizer, bitmap)
            } catch (e: Exception) {
                OcrResult(
                    rawText         = "",
                    allBlocks       = emptyList(),
                    titleCandidates = emptyList(),
                    error           = e,
                )
            }

            _ocrResult.value = result
            _isRecognizing.value = false
        }
    }

    /** Initiates the enhanced recognition flow from the uncertain-match screen. */
    fun onTryEnhancedRecognition() {
        if (!userPreferences.visionEnabled) {
            _enhancedRecognitionOutcome.value = EnhancedRecognitionOutcome.Unavailable
            return
        }

        if (userPreferences.visionConsentGranted) {
            runVisionFallback()
        } else {
            _visionFallbackState.value = VisionFallbackState.AwaitingConsent
        }
    }

    /** Called when the user grants one-time consent to send the image to Google. */
    fun onVisionConsentGranted() {
        userPreferences.visionConsentGranted = true
        _visionFallbackState.value = VisionFallbackState.Idle
        runVisionFallback()
    }

    /** Called when the user declines consent. Disables the fallback and closes the prompt. */
    fun onVisionConsentDeclined() {
        userPreferences.visionEnabled = false
        userPreferences.visionConsentGranted = false
        _visionFallbackState.value = VisionFallbackState.Idle
    }

    private fun runVisionFallback() {
        viewModelScope.launch {
            val bitmap = _capturedImage.value
            if (bitmap == null) {
                _visionFallbackState.value = VisionFallbackState.Idle
                _enhancedRecognitionOutcome.value = EnhancedRecognitionOutcome.Unavailable
                return@launch
            }

            _isRecognizing.value = true
            _visionFallbackState.value = VisionFallbackState.Recognizing

            try {
                val result = recognizeWith(visionTextRecognizer, bitmap)
                _ocrResult.value = result
                // Determine outcome: Success if the result has a confident/ambiguous match,
                // NoConfidentMatch if Vision ran but nothing crossed the threshold.
                _enhancedRecognitionOutcome.value = when (result.bestMatch) {
                    is MatchResult.Confident, is MatchResult.Ambiguous ->
                        EnhancedRecognitionOutcome.Success
                    else -> EnhancedRecognitionOutcome.NoConfidentMatch
                }
            } catch (e: Exception) {
                // Network/API failures: retain the ML Kit result already displayed.
                Log.w(TAG, "Vision fallback failed", e)
                _enhancedRecognitionOutcome.value = EnhancedRecognitionOutcome.Error
            } finally {
                _isRecognizing.value = false
                _visionFallbackState.value = VisionFallbackState.Idle
            }
        }
    }

    private suspend fun recognizeWith(
        recognizer: TextRecognizer,
        bitmap: Bitmap,
    ): OcrResult {
        val output = withContext(ioDispatcher) {
            recognizer.recognize(bitmap)
        }

        if (output.blocks.isEmpty()) {
            return OcrResult(
                rawText         = output.rawText,
                allBlocks       = output.blocks,
                titleCandidates = emptyList(),
                providerName    = output.providerName,
            )
        }

        val candidates = withContext(defaultDispatcher) {
            candidateExtractor.extractCandidates(output.blocks)
        }

        val matched = withContext(ioDispatcher) {
            candidates.map { candidate ->
                OcrMatchedCandidate(
                    ocrText     = candidate.text,
                    matchResult = titleMatcher.match(candidate.text),
                )
            }
        }

        return OcrResult(
            rawText           = output.rawText,
            allBlocks         = output.blocks,
            titleCandidates   = candidates,
            matchedCandidates = matched,
            providerName      = output.providerName,
            bestMatch         = selectBestMatch(matched.map { it.matchResult }),
        )
    }

    /** Select the strongest match result across OCR candidates. */
    private fun selectBestMatch(matchResults: List<MatchResult>): MatchResult? {
        if (matchResults.isEmpty()) return null

        val confident = matchResults.filterIsInstance<MatchResult.Confident>()
        if (confident.isNotEmpty()) {
            return confident.maxByOrNull { it.score }
        }

        val ambiguous = matchResults.filterIsInstance<MatchResult.Ambiguous>()
        if (ambiguous.isNotEmpty()) {
            return ambiguous.maxByOrNull { it.candidates.firstOrNull()?.score ?: 0 }
        }

        val hasNone = matchResults.any { it is MatchResult.None }
        if (hasNone) return MatchResult.None

        return null
    }

    companion object {
        private const val TAG = "CameraViewModel"
    }
}
