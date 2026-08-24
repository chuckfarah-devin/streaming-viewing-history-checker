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
        // When FTS found records via the prefix query but all fuzzy scores fall
        // below CONFIDENCE_THRESHOLD_POSSIBLE (common for very short queries like
        // "run" vs long titles), show them as Ambiguous candidates rather than
        // returning None.  A floor of 20 prevents completely unrelated FTS hits
        // from surfacing.
        val best = scored.maxByOrNull { it.second } ?: return MatchResult.None
        return when {
            best.second >= CONFIDENCE_THRESHOLD_HIGH -> {
                val rec = best.first
                val ct  = ContentType.valueOf(rec.contentType)
                MatchResult.Confident(
                    displayTitle    = rec.displayTitle,
                    // For SERIES return the series name so the repository can
                    // aggregate ALL episodes rather than one episode's records.
                    normalizedTitle = seriesLookupKey(rec, ct),
                    contentType     = ct,
                    score           = best.second,
                )
            }
            best.second >= CONFIDENCE_THRESHOLD_POSSIBLE -> {
                val topCandidates = buildCandidateList(scored)
                if (topCandidates.isEmpty()) MatchResult.None
                else MatchResult.Ambiguous(topCandidates)
            }
            ftsFoundSomething -> {
                // FTS found records via prefix search but scores are low (short query).
                // Show as low-confidence Ambiguous candidates with a floor of 20.
                val topCandidates = buildCandidateList(scored, minScore = 20)
                if (topCandidates.isEmpty()) MatchResult.None
                else MatchResult.Ambiguous(topCandidates)
            }
            else -> MatchResult.None
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
            val ct  = ContentType.valueOf(rec.contentType)
            MatchResult.Confident(
                displayTitle    = rec.displayTitle,
                normalizedTitle = seriesLookupKey(rec, ct),
                contentType     = ct,
                score           = 100,
            )
        } else {
            val candidates = distinctRaw.map { rec ->
                val ct = ContentType.valueOf(rec.contentType)
                TitleCandidate(
                    displayTitle    = rec.displayTitle,
                    normalizedTitle = seriesLookupKey(rec, ct),
                    score           = 100,
                    recordCount     = exactRecords.count { it.rawTitle == rec.rawTitle },
                    contentType     = ct,
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
        minScore: Int = CONFIDENCE_THRESHOLD_POSSIBLE,
    ): List<TitleCandidate> {
        // Group SERIES records by normalizedSeriesName so all episodes of a
        // series appear as a single candidate. Non-SERIES records are grouped
        // by their normalizedTitle as before.
        return scored
            .groupBy { (rec, _) ->
                val ct = ContentType.valueOf(rec.contentType)
                seriesLookupKey(rec, ct)
            }
            .map { (key, entries) ->
                val best = entries.maxByOrNull { it.second }!!
                val rec  = best.first
                val ct   = ContentType.valueOf(rec.contentType)
                TitleCandidate(
                    displayTitle    = rec.displayTitle,
                    normalizedTitle = key,
                    score           = best.second,
                    recordCount     = entries.size,
                    contentType     = ct,
                )
            }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the appropriate lookup key for a record.
     * For SERIES with a known normalizedSeriesName, that name is used so the
     * repository can retrieve all episodes via getSeriesRecords().
     * For all other records the normalizedTitle is used.
     */
    private fun seriesLookupKey(rec: ViewingRecordEntity, ct: ContentType): String =
        if (ct == ContentType.SERIES && rec.normalizedSeriesName != null)
            rec.normalizedSeriesName
        else
            rec.normalizedTitle
}
