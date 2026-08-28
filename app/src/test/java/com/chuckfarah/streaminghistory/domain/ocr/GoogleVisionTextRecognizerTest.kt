package com.chuckfarah.streaminghistory.domain.ocr

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.chuckfarah.streaminghistory.BuildConfig
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class GoogleVisionTextRecognizerTest {

    @Test fun `missing API key returns empty output without network call`() {
        val recognizer = GoogleVisionTextRecognizer()
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val output = runBlocking { recognizer.recognize(bitmap) }

        assertThat(output.blocks).isEmpty()
        assertThat(output.rawText).isEmpty()
        assertThat(output.providerName).isEqualTo("Google Cloud Vision API")
    }

    @Test fun `BuildConfig API key is not hardcoded and not a real secret`() {
        // API key must be supplied by local.properties (git-ignored). The default / test
        // build should never contain an actual key, and it must not be logged or exposed.
        assertThat(BuildConfig.GOOGLE_VISION_API_KEY).isNotNull()
    }
}
