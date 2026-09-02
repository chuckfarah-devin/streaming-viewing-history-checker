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
        // Place blocks far enough apart vertically that they do not get combined
        // under the larger vertical proximity factor.
        val blocks = listOf(
            TextBlock("Tall Title",        Rect(0, 0,   100, 80),  0.95f),
            TextBlock("Medium Title",      Rect(0, 300, 100, 350), 0.95f),
            TextBlock("Short Title",       Rect(0, 600, 100, 630), 0.95f),
            TextBlock("Also a candidate",  Rect(0, 900, 100, 920), 0.95f),
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
            TextBlock("RIP", Rect(10, 100, 110, 180), 0.95f),
            TextBlock("Runtime and cast description", Rect(0, 500, 200, 530), 0.95f),
        )

        val candidates = extractor.extractCandidates(blocks)

        // The combined "THE RIP" / "RIP THE" should outrank the individual
        // words because their scores are added.
        assertThat(candidates.map { it.text })
            .containsAtLeast("THE RIP", "RIP THE")
    }

    @Test fun `reversed nearby title blocks produce QUEEN CHARLOTTE and outrank partial BRIDGE`() {
        // Real Samsung OCR returned the words as CHARLOTTE, QUEEN, BRIDGE (the
        // last an incomplete "Bridgerton").  The extractor must still form the
        // title "QUEEN CHARLOTTE" from the two large, nearby blocks regardless
        // of OCR order, and the partial/lower BRIDGE block must not take a
        // top slot from it.
        val blocks = listOf(
            TextBlock("CHARLOTTE", Rect(0, 100, 180, 180), 0.95f), // large title, bottom word
            TextBlock("QUEEN",     Rect(0, 0,   140, 80),  0.95f), // large title, top word
            TextBlock("BRIDGE",    Rect(0, 260, 100, 280), 0.95f), // small/far subtitle fragment
        )

        val candidates = extractor.extractCandidates(blocks)

        assertThat(candidates).hasSize(3)
        assertThat(candidates.map { it.text })
            .containsAtLeast("QUEEN CHARLOTTE", "CHARLOTTE QUEEN")
        // The strongest candidate must come from the two large title words.
        assertThat(candidates[0].text).isAnyOf("QUEEN CHARLOTTE", "CHARLOTTE QUEEN")
    }

    @Test fun `two-letter short word is preserved for combination but not returned standalone`() {
        // "El Camino": ML Kit may split "EL" and "CAMINO".  The short word must
        // be allowed to combine but not appear as a standalone candidate.
        val blocks = listOf(
            TextBlock("EL",     Rect(10, 0,  110, 80), 0.95f),
            TextBlock("CAMINO", Rect(10, 0,  330, 80), 0.95f),
        )

        val candidates = extractor.extractCandidates(blocks)

        assertThat(candidates.map { it.text }).contains("EL CAMINO")
        assertThat(candidates.map { it.text }).doesNotContain("EL")
    }

    @Test fun `vertically stacked title words with larger gap are combined`() {
        // "Peaky Blinders": stacked two-line title with a gap that is larger
        // than the old 1.5x proximity factor but within the new vertical factor.
        val blocks = listOf(
            TextBlock("PEAKY",    Rect(10, 0,   110, 80),  0.95f), // top word
            TextBlock("BLINDERS", Rect(10, 130, 210, 210), 0.95f), // bottom word, 130 px gap
        )

        val candidates = extractor.extractCandidates(blocks)

        assertThat(candidates.map { it.text })
            .containsAtLeast("PEAKY BLINDERS", "BLINDERS PEAKY")
    }

    @Test fun `short non-title badges are filtered`() {
        val blocks = listOf(
            TextBlock("HD",             Rect(0, 0, 50, 30), 0.95f),
            TextBlock("4K",             Rect(0, 0, 50, 30), 0.95f),
            TextBlock("OK",             Rect(0, 0, 50, 30), 0.95f),
            TextBlock("CC",             Rect(0, 0, 50, 30), 0.95f),
            TextBlock("Stranger Things", Rect(0, 0, 120, 30), 0.95f),
        )

        val candidates = extractor.extractCandidates(blocks)

        assertThat(candidates.map { it.text }).containsExactly("Stranger Things")
    }
}
