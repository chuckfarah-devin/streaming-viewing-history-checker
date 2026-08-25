package com.chuckfarah.streaminghistory.ui.screen.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
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

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewModel = CameraViewModel()

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
}
