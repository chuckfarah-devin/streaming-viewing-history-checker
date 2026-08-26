package com.chuckfarah.streaminghistory.ui.screen.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chuckfarah.streaminghistory.data.db.AppDatabase
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordFts
import com.chuckfarah.streaminghistory.domain.matching.TitleMatcher
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.chuckfarah.streaminghistory.domain.model.MatchResult
import com.chuckfarah.streaminghistory.domain.ocr.OcrCandidateExtractor
import com.chuckfarah.streaminghistory.domain.ocr.OcrResult
import com.chuckfarah.streaminghistory.domain.ocr.TextBlock
import com.chuckfarah.streaminghistory.domain.ocr.TextRecognizer
import com.chuckfarah.streaminghistory.domain.ocr.TextRecognizerOutput
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class CameraViewModelTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var titleMatcher: TitleMatcher
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        titleMatcher = TitleMatcher(TitleNormalizer(), db.viewingRecordDao())
        viewModel = CameraViewModel(
            FakeTextRecognizer(),
            OcrCandidateExtractor(),
            titleMatcher,
            Dispatchers.IO,
            Dispatchers.Default,
        )
    }

    @After
    fun teardown() { db.close() }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun TestScope.viewModelFor(blocks: List<TextBlock>): CameraViewModel {
        val testDispatcher = coroutineContext[CoroutineDispatcher] as kotlinx.coroutines.test.TestDispatcher
        return CameraViewModel(
            textRecognizerWith(blocks),
            OcrCandidateExtractor(),
            titleMatcher,
            testDispatcher,
            testDispatcher,
        )
    }

    private fun textRecognizerWith(blocks: List<TextBlock>) = object : TextRecognizer {
        override val name: String = "Fake"
        override val requiresNetwork: Boolean = false
        override suspend fun recognize(imageBitmap: Bitmap): TextRecognizerOutput =
            TextRecognizerOutput(rawText = blocks.joinToString("\n") { it.text }, blocks = blocks, providerName = name)
    }

    private suspend fun insertRecord(
        rawTitle:    String,
        contentType: ContentType = ContentType.UNKNOWN,
        seriesName:  String? = null,
        episodeTitle: String? = null,
        viewDate:    String = "2021-03-17",
    ) {
        val normalizer = TitleNormalizer()
        val entity = ViewingRecordEntity(
            provider             = "Netflix",
            rawTitle             = rawTitle,
            displayTitle         = seriesName ?: rawTitle,
            normalizedTitle      = normalizer.normalize(rawTitle),
            contentType          = contentType.name,
            seriesName           = seriesName,
            normalizedSeriesName = seriesName?.let { normalizer.normalize(it) },
            episodeTitle         = episodeTitle,
            viewDate             = viewDate,
            sourceTier           = 1,
            importId             = 1L,
            sessionKey           = rawTitle + viewDate,
        )
        val id = db.viewingRecordDao().insert(entity)
        db.viewingRecordDao().insertFts(
            ViewingRecordFts(
                normalizedTitle      = entity.normalizedTitle,
                normalizedSeriesName = entity.normalizedSeriesName ?: "",
            )
        )
    }

    private suspend fun TestScope.captureAndRecognize(bitmap: Bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)): OcrResult? {
        viewModel.onImageCaptured(bitmap)
        viewModel.recognizeCapturedImage()
        val result = viewModel.ocrResult.first { it != null }
        // Let the CameraViewModel main job finish (e.g. _isRecognizing = false) before
        // the runTest scope is torn down, otherwise UncompletedCoroutinesError is thrown.
        advanceUntilIdle()
        return result
    }

    // ── Core pipeline tests ─────────────────────────────────────────────────────

    @Test fun `captured image is emitted in the StateFlow`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        viewModel.onImageCaptured(bitmap)
        assertThat(viewModel.capturedImage.value).isEqualTo(bitmap)
    }

    @Test fun `clearImage removes the captured image and OCR result`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        viewModel.onImageCaptured(bitmap)
        viewModel.clearImage()
        assertThat(viewModel.capturedImage.value).isNull()
        assertThat(viewModel.ocrResult.value).isNull()
    }

    @Test fun `strong OCR candidate produces a Confident history match`() = runTest {
        insertRecord("Stranger Things")
        viewModel = viewModelFor(listOf(TextBlock("Stranger Things", Rect(0, 0, 200, 80), 0.95f)))

        val result = captureAndRecognize()

        assertThat(result).isNotNull()
        assertThat(result!!.bestMatch).isInstanceOf(MatchResult.Confident::class.java)
        assertThat((result.bestMatch as MatchResult.Confident).normalizedTitle).isEqualTo("stranger things")
    }

    @Test fun `OCR typo can produce an Ambiguous result`() = runTest {
        insertRecord("Stranger Things")
        insertRecord("The Stranger")
        // "Stranger" is likely to match both titles ambiguously
        viewModel = viewModelFor(listOf(TextBlock("Stranger", Rect(0, 0, 200, 80), 0.95f)))

        val result = captureAndRecognize()

        assertThat(result).isNotNull()
        assertThat(result!!.bestMatch).isInstanceOf(MatchResult.Ambiguous::class.java)
    }

    @Test fun `multiple OCR candidates selects the strongest valid match`() = runTest {
        insertRecord("Stranger Things")
        viewModel = viewModelFor(
            listOf(
                TextBlock("Avatar",          Rect(0, 0,   200, 80), 0.95f), // no history match
                TextBlock("Stranger Things", Rect(0, 200, 200, 290), 0.95f), // confident
            )
        )

        val result = captureAndRecognize()

        assertThat(result).isNotNull()
        assertThat(result!!.bestMatch).isInstanceOf(MatchResult.Confident::class.java)
        val confident = result.bestMatch as MatchResult.Confident
        assertThat(confident.normalizedTitle).isEqualTo("stranger things")
    }

    @Test fun `OCR candidate with no history match returns None not a watched result`() = runTest {
        insertRecord("Stranger Things")
        viewModel = viewModelFor(listOf(TextBlock("Avatar", Rect(0, 0, 200, 80), 0.95f)))

        val result = captureAndRecognize()

        assertThat(result).isNotNull()
        assertThat(result!!.bestMatch).isInstanceOf(MatchResult.None::class.java)
    }

    @Test fun `OCR failure is reported as error not watched or None`() = runTest {
        val failing = object : TextRecognizer {
            override val name: String = "FakeFailing"
            override val requiresNetwork: Boolean = false
            override suspend fun recognize(imageBitmap: Bitmap): TextRecognizerOutput =
                throw RuntimeException("OCR failed")
        }
        val testDispatcher = coroutineContext[CoroutineDispatcher] as kotlinx.coroutines.test.TestDispatcher
        viewModel = CameraViewModel(failing, OcrCandidateExtractor(), titleMatcher, testDispatcher, testDispatcher)

        val result = captureAndRecognize()

        assertThat(result).isNotNull()
        assertThat(result!!.error).isNotNull()
        assertThat(result.bestMatch).isNull()
        assertThat(result.titleCandidates).isEmpty()
    }

    @Test fun `empty OCR blocks are treated as recognition failure not a match`() = runTest {
        viewModel = viewModelFor(emptyList())

        val result = captureAndRecognize()

        assertThat(result).isNotNull()
        assertThat(result!!.bestMatch).isNull()
        assertThat(result.titleCandidates).isEmpty()
        assertThat(result.error).isNull()
        assertThat(result.bestMatch).isNotInstanceOf(MatchResult.None::class.java)
    }

    @Test fun `camera and manual search use the same matching logic`() = runTest {
        insertRecord("Stranger Things")
        viewModel = viewModelFor(listOf(TextBlock("Stranger Things", Rect(0, 0, 200, 80), 0.95f)))

        val ocrResult = captureAndRecognize()
        val manualResult = titleMatcher.match("Stranger Things")

        assertThat(ocrResult).isNotNull()
        assertThat(ocrResult!!.bestMatch).isInstanceOf(MatchResult.Confident::class.java)
        assertThat(manualResult).isInstanceOf(MatchResult.Confident::class.java)
        assertThat((ocrResult.bestMatch as MatchResult.Confident).normalizedTitle)
            .isEqualTo((manualResult as MatchResult.Confident).normalizedTitle)
    }

    @Test fun `short title exact match is handled by the same matcher`() = runTest {
        insertRecord("It")
        val manualResult = titleMatcher.match("It")
        assertThat(manualResult).isInstanceOf(MatchResult.Confident::class.java)
    }

    @Test fun `short title false positive is rejected by the same matcher`() = runTest {
        insertRecord("It Chapter Two")
        val manualResult = titleMatcher.match("It")
        assertThat(manualResult).isInstanceOf(MatchResult.None::class.java)
    }

    @Test fun `two-line OCR title THE RIP matches The Rip confidently`() = runTest {
        insertRecord("The Rip")
        viewModel = viewModelFor(
            listOf(
                TextBlock("THE", Rect(10, 0,   110, 80), 0.95f),
                TextBlock("RIP", Rect(10, 100, 110, 180), 0.95f),
            )
        )

        val result = captureAndRecognize()

        assertThat(result).isNotNull()
        assertThat(result!!.bestMatch).isInstanceOf(MatchResult.Confident::class.java)
        assertThat((result.bestMatch as MatchResult.Confident).normalizedTitle).isEqualTo("the rip")
    }

    @Test fun `OCR recognized series title matches episode history`() = runTest {
        val episodes = listOf("Götterdämmerung", "Blood Sacrifice", "The Gloaming")
        episodes.forEachIndexed { index, ep ->
            insertRecord(
                rawTitle    = "The Watcher: $ep",
                contentType = ContentType.SERIES,
                seriesName  = "The Watcher",
                episodeTitle = ep,
                viewDate    = "2021-03-${17 + index}",
            )
        }
        viewModel = viewModelFor(listOf(TextBlock("The Watcher", Rect(0, 0, 200, 80), 0.95f)))

        val result = captureAndRecognize()

        assertThat(result).isNotNull()
        assertThat(result!!.bestMatch).isInstanceOf(MatchResult.Confident::class.java)
        val confident = result.bestMatch as MatchResult.Confident
        assertThat(confident.normalizedTitle).isEqualTo("the watcher")
        assertThat(confident.contentType).isEqualTo(ContentType.SERIES)
        assertThat(confident.displayTitle).isEqualTo("The Watcher")
    }

    // ── Fake TextRecognizer used by the non-recognizer tests ──────────────────

    private class FakeTextRecognizer : TextRecognizer {
        override val name: String = "Fake"
        override val requiresNetwork: Boolean = false
        override suspend fun recognize(imageBitmap: Bitmap): TextRecognizerOutput =
            TextRecognizerOutput(rawText = "", blocks = emptyList(), providerName = name)
    }
}
