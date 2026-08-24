package com.chuckfarah.streaminghistory.domain.matching

import com.chuckfarah.streaminghistory.data.db.dao.TitlePair
import com.chuckfarah.streaminghistory.data.db.dao.ViewingRecordDao
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.chuckfarah.streaminghistory.domain.model.MatchResult
import com.chuckfarah.streaminghistory.domain.model.TitleCandidate
import me.xdrop.fuzzywuzzy.FuzzySearch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the four-stage title matching pipeline (TS §4.3).
 *
 * Stage 1: FTS4 MATCH
 * Stage 2: Score FTS results with tokenSortRatio
 * Stage 3: Full-scan fuzzy fallback when FTS returns nothing
 * Stage 4: Threshold classification → Confident / Ambiguous / None
 *
 * Short titles (normalized length ≤ SHORT_TITLE_MAX_LENGTH) bypass the
 * fuzzy pipeline and require an exact normalized match.
 */
@Singleton
class TitleMatcher @Inject constructor(
    private val normalizer: TitleNormalizer,
    private val dao: ViewingRecordDao,
) {
    companion object {
        const val CONFIDENCE_THRESHOLD_HIGH     = 85
        const val CONFIDENCE_THRESHOLD_POSSIBLE = 55
        const val SHORT_TITLE_MAX_LENGTH        = 3
    }

    suspend fun match(queryText: String): MatchResult {
        val nq = normalizer.normalize(queryText)
        if (nq.isBlank()) return MatchResult.None

        // ── Short-title path (TS §4.3 short-title handling) ─────────────────
        if (nq.length <= SHORT_TITLE_MAX_LENGTH) {
            return handleShortTitle(nq)
        }

        // ── Stage 1: FTS4 MATCH ───────────────────────────────────────────────
        val ftsHits = dao.searchFts(nq)

        // ── Stage 2: Score FTS results ────────────────────────────────────────
        val scored: List<Pair<ViewingRecordEntity, Int>> = if (ftsHits.isNotEmpty()) {
            ftsHits.map { rec -> rec to scoreRecord(nq, rec) }
        } else {
            // ── Stage 3: Full-scan fuzzy fallback ─────────────────────────────
            val allPairs = dao.getAllDistinctTitlePairs()
            val candidates = allPairs.mapNotNull { pair ->
                val s = scorePair(nq, pair)
                if (s >= CONFIDENCE_THRESHOLD_POSSIBLE) pair.normalized_title to s else null
            }
            if (candidates.isEmpty()) return MatchResult.None
            // Load actual records for candidate titles
            candidates.flatMap { (title, score) ->
                dao.getByExactNormalizedTitle(title).map { rec -> rec to score }
            }
        }

        if (scored.isEmpty()) return MatchResult.None

        // ── Stage 4: Classify ─────────────────────────────────────────────────
        val best = scored.maxByOrNull { it.second } ?: return MatchResult.None
        return when {
            best.second < CONFIDENCE_THRESHOLD_POSSIBLE -> MatchResult.None
            best.second >= CONFIDENCE_THRESHOLD_HIGH    -> {
                val rec = best.first
                MatchResult.Confident(
                    displayTitle    = rec.displayTitle,
                    normalizedTitle = rec.normalizedTitle,
                    contentType     = ContentType.valueOf(rec.contentType),
                    score           = best.second,
                )
            }
            else -> {
                val topCandidates = buildCandidateList(scored)
                MatchResult.Ambiguous(topCandidates)
            }
        }
    }

    // ── Short-title exact-match path ──────────────────────────────────────────

    private suspend fun handleShortTitle(normalizedQuery: String): MatchResult {
        val exactRecords = dao.getByExactNormalizedTitle(normalizedQuery)
        if (exactRecords.isEmpty()) return MatchResult.None

        // Distinct by original raw_title; different raw titles normalising to
        // the same short string represent genuinely different catalog entries.
        val distinctRaw = exactRecords.distinctBy { it.rawTitle }
        return if (distinctRaw.size == 1) {
            val rec = distinctRaw.first()
            MatchResult.Confident(
                displayTitle    = rec.displayTitle,
                normalizedTitle = rec.normalizedTitle,
                contentType     = ContentType.valueOf(rec.contentType),
                score           = 100,
            )
        } else {
            val candidates = distinctRaw.map { rec ->
                TitleCandidate(
                    displayTitle    = rec.displayTitle,
                    normalizedTitle = rec.normalizedTitle,
                    score           = 100,
                    recordCount     = exactRecords.count { it.rawTitle == rec.rawTitle },
                    contentType     = ContentType.valueOf(rec.contentType),
                )
            }
            MatchResult.Ambiguous(candidates)
        }
    }

    // ── Scoring helpers ───────────────────────────────────────────────────────

    private fun scoreRecord(nq: String, rec: ViewingRecordEntity): Int {
        val titleScore  = FuzzySearch.tokenSortRatio(nq, rec.normalizedTitle)
        val seriesScore = rec.normalizedSeriesName
            ?.let { FuzzySearch.tokenSortRatio(nq, it) } ?: 0
        return maxOf(titleScore, seriesScore)
    }

    private fun scorePair(nq: String, pair: TitlePair): Int {
        val titleScore  = FuzzySearch.tokenSortRatio(nq, pair.normalized_title)
        val seriesScore = pair.normalized_series_name
            ?.let { FuzzySearch.tokenSortRatio(nq, it) } ?: 0
        return maxOf(titleScore, seriesScore)
    }

    private fun buildCandidateList(
        scored: List<Pair<ViewingRecordEntity, Int>>,
    ): List<TitleCandidate> {
        // Group by normalizedTitle and keep the best score per title
        return scored
            .groupBy { it.first.normalizedTitle }
            .map { (_, entries) ->
                val best = entries.maxByOrNull { it.second }!!
                val rec  = best.first
                TitleCandidate(
                    displayTitle    = rec.displayTitle,
                    normalizedTitle = rec.normalizedTitle,
                    score           = best.second,
                    recordCount     = entries.size,
                    contentType     = ContentType.valueOf(rec.contentType),
                )
            }
            .filter { it.score >= CONFIDENCE_THRESHOLD_POSSIBLE }
            .sortedByDescending { it.score }
    }
}
