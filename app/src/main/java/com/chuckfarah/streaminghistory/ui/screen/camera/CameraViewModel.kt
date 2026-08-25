package com.chuckfarah.streaminghistory.ui.screen.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chuckfarah.streaminghistory.domain.ocr.OcrCandidateExtractor
import com.chuckfarah.streaminghistory.domain.ocr.OcrResult
import com.chuckfarah.streaminghistory.domain.ocr.TextRecognizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the captured camera image and the OCR result for the camera flow (Steps 9–10).
 *
 * The captured [Bitmap] is held in memory only while this ViewModel is in scope.
 * It is never persisted.  Step 10 runs the bundled ML Kit recognizer over the
 * captured image and extracts the top title candidates.
 */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val textRecognizer: TextRecognizer,
    private val candidateExtractor: OcrCandidateExtractor,
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
     * Run the bundled ML Kit OCR on the captured image and update [ocrResult].
     *
     * Must only be called when [capturedImage] is non-null.
     */
    fun recognizeCapturedImage() {
        viewModelScope.launch {
            val bitmap = _capturedImage.value ?: return@launch

            _isRecognizing.value = true
            _ocrResult.value = try {
                val output = textRecognizer.recognize(bitmap)
                val candidates = candidateExtractor.extractCandidates(output.blocks)
                OcrResult(
                    rawText         = output.rawText,
                    allBlocks       = output.blocks,
                    titleCandidates = candidates,
                )
            } catch (e: Exception) {
                null
            } finally {
                _isRecognizing.value = false
            }
        }
    }
}
