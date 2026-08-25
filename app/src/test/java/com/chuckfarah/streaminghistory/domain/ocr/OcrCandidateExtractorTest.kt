package com.chuckfarah.streaminghistory.domain.ocr

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class OcrCandidateExtractorTest {

    private val extractor = OcrCandidateExtractor()

    @Test fun `filters obvious non-titles`() {
        val blocks = listOf(
            TextBlock("Play",       Rect(0, 0, 50,  15), 0.95f),
            TextBlock("More Info",  Rect(0, 0, 80,  15), 0.95f),
            TextBlock("2021",       Rect(0, 0, 40,  15), 0.95f),
            TextBlock("12:34",      Rect(0, 0, 50,  15), 0.95f),
            TextBlock("OK",         Rect(0, 0, 20,  15), 0.95f),
            TextBlock("Stranger Things", Rect(0, 0, 120, 30), 0.95f),
        )

        val candidates = extractor.extractCandidates(blocks)

        assertThat(candidates.map { it.text }).containsExactly("Stranger Things")
    }

    @Test fun `single-line tall block scores higher than multi-line block`() {
        val blocks = listOf(
            TextBlock("Stranger Things",       Rect(0, 0, 100, 40), 0.95f), // single line
            TextBlock("A long description\nthat spans\nmultiple lines", Rect(0, 0, 200, 150), 0.95f), // multi-line
        )

        val candidates = extractor.extractCandidates(blocks)

        assertThat(candidates).hasSize(2)
        assertThat(candidates[0].text).isEqualTo("Stranger Things")
        assertThat(candidates[0].score).isGreaterThan(candidates[1].score)
    }

    @Test fun `returns top 3 candidates sorted by score`() {
        // Place blocks far enough apart that they do not get combined.
        val blocks = listOf(
            TextBlock("Tall Title",        Rect(0, 0,   100, 80), 0.95f),
            TextBlock("Medium Title",      Rect(0, 200, 100, 50), 0.95f),
            TextBlock("Short Title",       Rect(0, 400, 100, 30), 0.95f),
            TextBlock("Also a candidate",  Rect(0, 600, 100, 20), 0.95f),
        )

        val candidates = extractor.extractCandidates(blocks)

        assertThat(candidates).hasSize(3)
        assertThat(candidates.map { it.text })
            .containsExactly("Tall Title", "Medium Title", "Short Title")
            .inOrder()
    }

    @Test fun `low-scoring blocks below multi-line threshold are not returned`() {
        val blocks = listOf(
            TextBlock("Stranger Things", Rect(0, 0, 100, 40), 0.95f),
            TextBlock("minor",           Rect(0, 0, 100, 5),  0.95f),
        )

        val candidates = extractor.extractCandidates(blocks)

        assertThat(candidates.map { it.text }).containsExactly("Stranger Things")
    }

    @Test fun `combines nearby two-line title blocks`() {
        // A stylised/two-line title where ML Kit returns the words as
        // separate, vertically stacked blocks.
        val blocks = listOf(
            TextBlock("THE", Rect(10, 0,   110, 80), 0.95f),
            TextBlock("RIP", Rect(10, 100, 110, 80), 0.95f),
            TextBlock("Runtime and cast description", Rect(0, 500, 200, 30), 0.95f),
        )

        val candidates = extractor.extractCandidates(blocks)

        // The combined "THE RIP" / "RIP THE" should outrank the individual
        // words because their scores are added.
        assertThat(candidates.map { it.text })
            .containsAtLeast("THE RIP", "RIP THE")
    }
}
