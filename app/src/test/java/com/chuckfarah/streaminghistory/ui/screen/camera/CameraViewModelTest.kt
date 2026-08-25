package com.chuckfarah.streaminghistory.ui.screen.camera

import android.graphics.Bitmap
import android.graphics.Rect
import com.chuckfarah.streaminghistory.domain.ocr.OcrCandidateExtractor
import com.chuckfarah.streaminghistory.domain.ocr.OcrResult
import com.chuckfarah.streaminghistory.domain.ocr.TextBlock
import com.chuckfarah.streaminghistory.domain.ocr.TextRecognizer
import com.chuckfarah.streaminghistory.domain.ocr.TextRecognizerOutput
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CameraViewModelTest {

    private val fakeTextRecognizer = object : TextRecognizer {
        override val name: String = "Fake"
        override val requiresNetwork: Boolean = false
        override suspend fun recognize(imageBitmap: Bitmap): TextRecognizerOutput =
            TextRecognizerOutput(
                rawText      = "fake raw text",
                providerName = name,
                blocks       = listOf(
                    TextBlock("Stranger Things", Rect(0, 0, 100, 30), 0.95f),
                    TextBlock("Play",             Rect(0, 0, 30,  10), 0.90f),
                    TextBlock("More Info",        Rect(0, 0, 50,  10), 0.88f),
                ),
            )
    }

    private val candidateExtractor = OcrCandidateExtractor()
    private val viewModel          = CameraViewModel(fakeTextRecognizer, candidateExtractor)

    @Test fun `captured image is emitted in the StateFlow`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        viewModel.onImageCaptured(bitmap)

        assertThat(viewModel.capturedImage.value).isEqualTo(bitmap)
        assertThat(viewModel.capturedImage.first()).isEqualTo(bitmap)
    }

    @Test fun `clearImage removes the captured image`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        viewModel.onImageCaptured(bitmap)

        viewModel.clearImage()

        assertThat(viewModel.capturedImage.value).isNull()
    }

    @Test fun `initial captured image is null`() = runTest {
        assertThat(viewModel.capturedImage.value).isNull()
    }

    @Test fun `recognizeCapturedImage produces OcrResult`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        viewModel.onImageCaptured(bitmap)

        viewModel.recognizeCapturedImage()

        val result = viewModel.ocrResult.first { it != null }
        assertThat(result).isNotNull()
        assertThat(result!!.rawText).isEqualTo("fake raw text")
        assertThat(result.titleCandidates).isNotEmpty()
        assertThat(result.titleCandidates[0].text).isEqualTo("Stranger Things")
    }

    @Test fun `clearImage removes ocr result`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        viewModel.onImageCaptured(bitmap)
        viewModel.recognizeCapturedImage()
        viewModel.ocrResult.first { it != null }

        viewModel.clearImage()

        assertThat(viewModel.ocrResult.value).isNull()
    }
}
