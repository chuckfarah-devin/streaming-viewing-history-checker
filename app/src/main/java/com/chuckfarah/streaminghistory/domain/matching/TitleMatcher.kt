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
 * Threshold is 2 so that 1- and 2-character movie titles like "It" and
 * "Up" are protected from false fuzzy positives, while 3-character
 * queries like "run" or "war" go through the normal FTS path.
 *
 * Series handling: for SERIES records the lookup key returned in
 * Confident.normalizedTitle and TitleCandidate.normalizedTitle is the
 * normalizedSeriesName, not the individual episode's normalizedTitle.
 * This allows the repository to aggregate all episodes for that series.
 */
@Singleton
class TitleMatcher @Inject constructor(
    private val normalizer: TitleNormalizer,
    private val dao: ViewingRecordDao,
) {
    companion object {
        const val CONFIDENCE_THRESHOLD_HIGH     = 85
        const val CONFIDENCE_THRESHOLD_POSSIBLE = 55
        /** Only 1–2 character normalized titles use exact-match-only path. */
        const val SHORT_TITLE_MAX_LENGTH        = 2
    }

    suspend fun match(queryText: String): MatchResult {
        val nq = normalizer.normalize(queryText)
        if (nq.isBlank()) return MatchResult.None

        // ── Short-title path (TS §4.3 short-title handling) ─────────────────
        if (nq.length <= SHORT_TITLE_MAX_LENGTH) {
            return handleShortTitle(nq)
        }

        // ── Stage 1: FTS4 MATCH (with prefix wildcard for broader recall) ───────
        // "run*" matches "run", "running", "runs" etc. so partial queries like
        // "run" or "walk" find all titles containing words starting with that prefix.
        val ftsQuery = if (nq.contains(' ')) nq else "$nq*"
        val ftsHits = dao.searchFts(ftsQuery)
        val ftsFoundSomething = ftsHits.isNotEmpty()

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
        // Short keyword search (≤ 4 chars with FTS hits) always gets the wider
        // candidate floor, regardless of what score the best hit achieved.
        // Reason: tokenSortRatio("run","run away") ≈ 55 puts us in the normal
        // Ambiguous branch (minScore = 55), hiding other valid hits like
        // "Running Point" (37) or "Midnight Run" (40).  By treating such queries
        // as keyword searches we show ALL FTS prefix hits above a low floor of 20.
        val isKeywordSearch = nq.length <= 4 && ftsFoundSomething

        val best = scored.maxByOrNull { it.second } ?: return MatchResult.None
        return when {
            best.second >= CONFIDENCE_THRESHOLD_HIGH && !isKeywordSearch -> {
                val rec          = best.first
                val seriesCounts = scored.mapNotNull { it.first.normalizedSeriesName }.groupingBy { it }.eachCount()
                val candidate    = resolveConfidentCandidate(rec, nq, best.second, seriesCounts)
                MatchResult.Confident(
                    displayTitle    = candidate.displayTitle,
                    normalizedTitle = candidate.normalizedTitle,
                    contentType     = candidate.contentType,
                    score           = candidate.score,
                )
            }
            else -> {
                // Ambiguous OR keyword-search fallback.
                // minScore: keyword searches use 20 so all FTS prefix hits are shown;
                // normal longer queries use the standard 55 floor.
                val minScore = if (isKeywordSearch) 20 else CONFIDENCE_THRESHOLD_POSSIBLE
                val topCandidates = buildCandidateList(scored, nq, minScore = minScore)
                when {
                    topCandidates.isEmpty()                                         -> MatchResult.None
                    best.second < CONFIDENCE_THRESHOLD_POSSIBLE && !ftsFoundSomething -> MatchResult.None
                    else                                                            -> MatchResult.Ambiguous(topCandidates)
                }
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
            val rec          = distinctRaw.first()
            val seriesCounts = exactRecords.mapNotNull { it.normalizedSeriesName }.groupingBy { it }.eachCount()
            val candidate    = resolveConfidentCandidate(rec, normalizedQuery, 100, seriesCounts)
            MatchResult.Confident(
                displayTitle    = candidate.displayTitle,
                normalizedTitle = candidate.normalizedTitle,
                contentType     = candidate.contentType,
                score           = candidate.score,
            )
        } else {
            val seriesCounts = exactRecords.mapNotNull { it.normalizedSeriesName }.groupingBy { it }.eachCount()
            val candidates = distinctRaw.map { rec ->
                val key = lookupKey(rec, normalizedQuery, seriesCounts)
                val isSeriesKey = key == rec.normalizedSeriesName
                TitleCandidate(
                    displayTitle    = if (isSeriesKey) rec.displayTitle else (rec.episodeTitle ?: rec.displayTitle),
                    normalizedTitle = key,
                    score           = 100,
                    recordCount     = exactRecords.count { it.rawTitle == rec.rawTitle },
                    contentType     = if (isSeriesKey) ContentType.SERIES else ContentType.UNKNOWN,
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
        nq: String,
        minScore: Int = CONFIDENCE_THRESHOLD_POSSIBLE,
    ): List<TitleCandidate> {
        val seriesCounts = scored
            .mapNotNull { it.first.normalizedSeriesName }
            .groupingBy { it }
            .eachCount()

        return scored
            .groupBy { (rec, _) -> lookupKey(rec, nq, seriesCounts) }
            .map { (key, entries) ->
                val best = entries.maxByOrNull { it.second }!!
                val rec  = best.first
                val isSeriesKey = key == rec.normalizedSeriesName
                TitleCandidate(
                    displayTitle    = if (isSeriesKey) rec.displayTitle else (rec.episodeTitle ?: rec.displayTitle),
                    normalizedTitle = key,
                    score           = best.second,
                    recordCount     = entries.size,
                    contentType     = if (isSeriesKey) ContentType.SERIES else ContentType.UNKNOWN,
                )
            }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private data class ConfidentCandidate(
        val displayTitle: String,
        val normalizedTitle: String,
        val contentType: ContentType,
        val score: Int,
    )

    private fun resolveConfidentCandidate(
        rec: ViewingRecordEntity,
        nq: String,
        score: Int,
        seriesCounts: Map<String, Int>,
    ): ConfidentCandidate {
        val key = lookupKey(rec, nq, seriesCounts)
        val isSeriesKey = key == rec.normalizedSeriesName
        return ConfidentCandidate(
            displayTitle    = if (isSeriesKey) rec.displayTitle else (rec.episodeTitle ?: rec.displayTitle),
            normalizedTitle = key,
            contentType     = if (isSeriesKey) ContentType.SERIES else ContentType.UNKNOWN,
            score           = score,
        )
    }

    /**
     * Choose the lookup key for [rec] relative to the normalized query.
     * A series key is only used when at least two records share that
     * [normalizedSeriesName] and the query matches the series name at least
     * as well as the full episode title.  This lets "The Watcher" aggregate
     * all episodes while "Avengers: Endgame" still resolves to the exact full
     * title of a single record.
     */
    private fun lookupKey(
        rec: ViewingRecordEntity,
        nq: String,
        seriesCounts: Map<String, Int>,
    ): String {
        val series = rec.normalizedSeriesName
        if (series == null || (seriesCounts[series] ?: 0) < 2) {
            return rec.normalizedTitle
        }

        val titleScore  = FuzzySearch.tokenSortRatio(nq, rec.normalizedTitle)
        val seriesScore = FuzzySearch.tokenSortRatio(nq, series)

        return when {
            seriesScore > titleScore -> series
            titleScore > seriesScore -> rec.normalizedTitle
            // Equal scores: treat the query as specific if it is at least as
            // long as the full title, otherwise prefer the series key.
            else -> if (nq.length >= rec.normalizedTitle.length) rec.normalizedTitle else series
        }
    }
}
