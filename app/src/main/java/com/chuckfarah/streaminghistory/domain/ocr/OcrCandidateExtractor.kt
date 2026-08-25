package com.chuckfarah.streaminghistory.domain.ocr

import android.graphics.Rect
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts likely program-title candidates from OCR output using the heuristics
 * described in TS §5.3.
 *
 * In addition to returning individual title-sized blocks, it now generates
 * spatially related two-block combinations.  Stylized or multi-line titles are
 * often split into several OCR blocks (e.g., "THE" and "RIP"), so the extractor
 * also produces candidates such as "THE RIP" and "RIP THE" by looking at
 * bounding-box proximity, and lets the downstream matcher decide which one is
 * correct.
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
            "netflix",
        )

        /** Whole-string numeric (year, rating percentage, runtime). */
        private val NUMERIC_ONLY = Regex("^\\d+$")

        /** Elapsed / remaining time like "12:34" or "1:23:45". */
        private val TIME_PATTERN = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")

        /** Length below which a block is too short to be a title. */
        private const val MIN_TITLE_LENGTH = 3

        /** Number of title candidates returned. */
        private const val TOP_CANDIDATE_COUNT = 3

        /**
         * Number of top individual candidates considered for combination.
         * Keeping this small avoids an explosion of synthetic candidates while
         * still catching stylised two-part titles.
         */
        private const val TOP_BLOCKS_FOR_COMBINATION = 4

        /**
         * Minimum average line height (pixels) a block must have to be a title
         * candidate.  Blocks smaller than this are too low-quality to be a title
         * (e.g., tiny subtitles or multi-line body text).
         */
        private const val MIN_AVERAGE_LINE_HEIGHT = 15f

        /** Score bonus for single-line blocks (titles are typically one line). */
        private const val SINGLE_LINE_MULTIPLIER = 10f

        /** Score multiplier for multi-line blocks (descriptions, cast). */
        private const val MULTI_LINE_MULTIPLIER = 1f

        /** Spatial closeness factor: a second block is considered a neighbour
         *  if its centre is within 1.5x the larger block's dimensions. */
        private const val PROXIMITY_FACTOR = 1.5f
    }

    /**
     * Apply the title extraction heuristic:
     *  1. Filter obvious non-titles and tiny blocks.
     *  2. Score the remaining individual blocks.
     *  3. Generate nearby two-block combinations (both reading orders).
     *  4. Return the top [TOP_CANDIDATE_COUNT] candidates by score.
     */
    fun extractCandidates(blocks: List<TextBlock>): List<OcrTitleCandidate> {
        val individuals = blocks
            .mapNotNull { scoreBlock(it) }
            .sortedByDescending { it.score }

        val combos = generateCombinations(individuals.take(TOP_BLOCKS_FOR_COMBINATION))

        return (individuals + combos)
            .sortedByDescending { it.score }
            .take(TOP_CANDIDATE_COUNT)
            .map { OcrTitleCandidate(text = it.text, score = it.score) }
    }

    private fun scoreBlock(block: TextBlock): ScoredBlock? {
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
        if (averageLineHeight < MIN_AVERAGE_LINE_HEIGHT) return null

        val multiplier = if (isMultiLine) MULTI_LINE_MULTIPLIER else SINGLE_LINE_MULTIPLIER
        val score = averageLineHeight * multiplier

        return ScoredBlock(
            text        = text,
            score       = score,
            boundingBox = block.boundingBox,
            isMultiLine = isMultiLine,
        )
    }

    /**
     * Generates two-block combinations for the top title-sized blocks that are
     * spatially close.  Because OCR block ordering is unreliable, both word
     * orders are produced for each neighbouring pair (e.g. "THE RIP" and
     * "RIP THE").  The downstream matcher then selects the plausible one.
     */
    private fun generateCombinations(topBlocks: List<ScoredBlock>): List<ScoredBlock> {
        if (topBlocks.size < 2) return emptyList()

        val combos = mutableListOf<ScoredBlock>()
        for (i in topBlocks.indices) {
            for (j in i + 1 until topBlocks.size) {
                val a = topBlocks[i]
                val b = topBlocks[j]
                if (!canCombine(a, b)) continue

                val combinedScore = a.score + b.score
                val combinedBox = combineBoundingBoxes(a.boundingBox, b.boundingBox)

                // Generate both reading orders; the matcher is responsible for
                // rejecting implausible ones such as "RIP THE".
                combos += ScoredBlock(
                    text        = "${a.text} ${b.text}",
                    score       = combinedScore,
                    boundingBox = combinedBox,
                    isMultiLine = false,
                )
                combos += ScoredBlock(
                    text        = "${b.text} ${a.text}",
                    score       = combinedScore,
                    boundingBox = combinedBox,
                    isMultiLine = false,
                )
            }
        }
        return combos
    }

    /** Two blocks can be combined if they are both single-line and close in space. */
    private fun canCombine(a: ScoredBlock, b: ScoredBlock): Boolean {
        // Only combine one-line title-sized blocks.  Multi-line blocks are
        // already descriptions/cast and combining them tends to produce noise.
        if (a.isMultiLine || b.isMultiLine) return false

        val r1 = a.boundingBox ?: return false
        val r2 = b.boundingBox ?: return false

        val maxW = maxOf(r1.width(), r2.width()).toFloat()
        val maxH = maxOf(r1.height(), r2.height()).toFloat()
        if (maxW == 0f || maxH == 0f) return false

        val dx = kotlin.math.abs(r1.centerX() - r2.centerX()).toFloat()
        val dy = kotlin.math.abs(r1.centerY() - r2.centerY()).toFloat()

        // The centre must be within ~1.5 box dimensions in both axes.
        // This allows stacked two-line titles as well as side-by-side words
        // while rejecting unrelated text elsewhere on the screen.
        return dx <= maxW * PROXIMITY_FACTOR && dy <= maxH * PROXIMITY_FACTOR
    }

    private fun combineBoundingBoxes(a: Rect?, b: Rect?): Rect? {
        if (a == null || b == null) return null
        return Rect(a).apply { union(b) }
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

    private data class ScoredBlock(
        val text: String,
        val score: Float,
        val boundingBox: Rect?,
        val isMultiLine: Boolean,
    )
}
