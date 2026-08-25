package com.chuckfarah.streaminghistory.domain.ocr

import android.graphics.Rect
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts likely program-title candidates from OCR output using the heuristics
 * described in TS §5.3.
 *
 * The extractor is deterministic and stateless, so it is trivially testable.
 */
@Singleton
class OcrCandidateExtractor @Inject constructor() {

    companion object {
        /** Obvious Netflix UI labels that are never the program title. */
        private val NETFLIX_UI_LABELS = setOf(
            "play",
            "more info",
            "+ my list",
            "add to my list",
            "preview",
            "continue watching",
            "my list",
            "episodes",
            "info",
            "rate",
            "remove",
        )

        /** Whole-string numeric (year, rating percentage, runtime). */
        private val NUMERIC_ONLY = Regex("^\\d+$")

        /** Elapsed / remaining time like "12:34" or "1:23:45". */
        private val TIME_PATTERN = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")

        /** Length below which a block is too short to be a title. */
        private const val MIN_TITLE_LENGTH = 3

        /** Number of title candidates returned. */
        private const val TOP_CANDIDATE_COUNT = 3

        /** Score bonus for single-line blocks (titles are typically one line). */
        private const val SINGLE_LINE_MULTIPLIER = 10f

        /** Score multiplier for multi-line blocks (descriptions, cast). */
        private const val MULTI_LINE_MULTIPLIER = 1f
    }

    /**
     * Apply the three-pass heuristic from TS §5.3:
     *  1. Filter obvious non-titles.
     *  2. Score remaining blocks by (average line height) * (single-line bonus).
     *  3. Return the top [TOP_CANDIDATE_COUNT] candidates.
     */
    fun extractCandidates(blocks: List<TextBlock>): List<OcrTitleCandidate> {
        val scored = blocks
            .mapNotNull { block -> scoreBlock(block) }
            .sortedByDescending { it.score }

        return scored.take(TOP_CANDIDATE_COUNT)
    }

    private fun scoreBlock(block: TextBlock): OcrTitleCandidate? {
        val text = block.text.trim()
        if (text.isBlank()) return null
        if (isObviousNonTitle(text)) return null

        val lines = text.split("\n")
        val lineCount = lines.size
        val isMultiLine = lineCount > 1

        val height = block.boundingBox?.height() ?: 0
        if (height <= 0) return null

        // Use average line height so multi-line descriptions don't outrank titles
        // just because the block is tall.
        val averageLineHeight = height.toFloat() / lineCount
        val multiplier = if (isMultiLine) MULTI_LINE_MULTIPLIER else SINGLE_LINE_MULTIPLIER
        val score = averageLineHeight * multiplier

        return OcrTitleCandidate(text = text, score = score)
    }

    private fun isObviousNonTitle(text: String): Boolean {
        val lower = text.lowercase()
        return when {
            lower in NETFLIX_UI_LABELS              -> true
            text.length < MIN_TITLE_LENGTH          -> true
            text.matches(NUMERIC_ONLY)              -> true
            text.matches(TIME_PATTERN)              -> true
            else                                    -> false
        }
    }
}
