package com.chuckfarah.streaminghistory.domain.model

/**
 * Result of running the title-matching pipeline (TS §4.3).
 *
 * Confident  — score ≥ 85, or exact normalized match for short titles.
 * Ambiguous  — score 55–84 (standard path), or multiple exact normalized
 *              matches for short titles.
 * None       — score < 55 or short title with no exact match.
 *
 * A MatchResult is NEVER derived from a technical failure; failures produce
 * an error state in the calling layer, not MatchResult.None.
 */
sealed class MatchResult {
    /**
     * A single title matched with sufficient confidence.
     * [normalizedTitle] is the matched form; the caller retrieves full
     * records via the repository.
     */
    data class Confident(
        val displayTitle: String,
        val normalizedTitle: String,
        val contentType: ContentType,
        val score: Int,
    ) : MatchResult()

    /**
     * Multiple candidate titles are plausible. [candidates] is ordered by
     * descending score. The user selects one.
     */
    data class Ambiguous(val candidates: List<TitleCandidate>) : MatchResult()

    /** No title in history is similar enough to the query. */
    object None : MatchResult()
}
