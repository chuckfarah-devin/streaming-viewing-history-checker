package com.chuckfarah.streaminghistory.ui.screen.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.di.DefaultDispatcher
import com.chuckfarah.streaminghistory.di.IoDispatcher
import com.chuckfarah.streaminghistory.domain.matching.TitleMatcher
import com.chuckfarah.streaminghistory.domain.model.MatchResult
import com.chuckfarah.streaminghistory.domain.ocr.OcrCandidateExtractor
import com.chuckfarah.streaminghistory.domain.ocr.OcrMatchedCandidate
import com.chuckfarah.streaminghistory.domain.ocr.OcrResult
import com.chuckfarah.streaminghistory.domain.ocr.TextRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Holds the captured camera image and drives the OCR → title-matching pipeline
 * for the camera flow (Steps 9–11).
 *
 * The captured [Bitmap] is held in memory only while this ViewModel is in scope.
 * It is never persisted.  The matching logic is the same [TitleMatcher] used by
 * manual search, so camera and manual search share the normalization, scoring,
 * and confidence behavior.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val textRecognizer: TextRecognizer,
    private val candidateExtractor: OcrCandidateExtractor,
    private val titleMatcher: TitleMatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _capturedImage = MutableStateFlow<Bitmap?>(null)
    val capturedImage: StateFlow<Bitmap?> = _capturedImage.asStateFlow()

    private val _ocrResult = MutableStateFlow<OcrResult?>(null)
    val ocrResult: StateFlow<OcrResult?> = _ocrResult.asStateFlow()

    private val _isRecognizing = MutableStateFlow(false)
    val isRecognizing: StateFlow<Boolean> = _isRecognizing.asStateFlow()

    /** Called by [CameraScreen] after CameraX has produced a captured [Bitmap]. */
    fun onImageCaptured(bitmap: Bitmap) {
        _capturedImage.value = bitmap
    }

    /** Clears the captured image and any OCR result (e.g., when the user taps "Retake"). */
    fun clearImage() {
        _capturedImage.value = null
        _ocrResult.value = null
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
                val output = withContext(ioDispatcher) {
                    textRecognizer.recognize(bitmap)
                }

                if (output.blocks.isEmpty()) {
                    OcrResult(
                        rawText         = "",
                        allBlocks       = emptyList(),
                        titleCandidates = emptyList(),
                    )
                } else {
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

                    OcrResult(
                        rawText           = output.rawText,
                        allBlocks         = output.blocks,
                        titleCandidates   = candidates,
                        matchedCandidates = matched,
                        bestMatch         = selectBestMatch(matched.map { it.matchResult }),
                    )
                }
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

    /** Select the strongest match result across OCR candidates. */
    private fun selectBestMatch(matchResults: List<MatchResult>): MatchResult? {
        if (matchResults.isEmpty()) return null

        val confident = matchResults.filterIsInstance<MatchResult.Confident>()
        if (confident.isNotEmpty()) {
            return confident.maxByOrNull { it.score }
        }

        val ambiguous = matchResults.filterIsInstance<MatchResult.Ambiguous>()
        if (ambiguous.isNotEmpty()) {
            // Pick the ambiguous group whose top history candidate has the highest score.
            return ambiguous.maxByOrNull { it.candidates.firstOrNull()?.score ?: 0 }
        }

        val hasNone = matchResults.any { it is MatchResult.None }
        if (hasNone) return MatchResult.None

        return null
    }
}
