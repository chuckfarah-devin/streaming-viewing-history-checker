package com.chuckfarah.streaminghistory.ui.screen.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Holds a captured camera image in memory for later processing (Step 9).
 *
 * Step 10 will feed [capturedImage] into the OCR pipeline.  The image is never
 * persisted by this ViewModel; it lives only while the ViewModel is in scope.
 */
@HiltViewModel
class CameraViewModel @Inject constructor() : ViewModel() {

    private val _capturedImage = MutableStateFlow<Bitmap?>(null)
    val capturedImage: StateFlow<Bitmap?> = _capturedImage.asStateFlow()

    /** Called by [CameraScreen] after CameraX has produced a captured [Bitmap]. */
    fun onImageCaptured(bitmap: Bitmap) {
        _capturedImage.value = bitmap
    }

    /** Clears the captured image (e.g., when the user taps "Retake"). */
    fun clearImage() {
        _capturedImage.value = null
    }
}
