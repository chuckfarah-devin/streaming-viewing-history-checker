package com.chuckfarah.streaminghistory.domain.model

/**
 * One raw viewing-history row returned by a manual substring search.
 *
 * Manual search bypasses TitleMatcher, SeriesParser, resolveWatched, and
 * buildWatched.  Every accessible record whose normalized_title or
 * normalized_series_name contains the query is included without deduplication
 * or aggregation.
 */
data class ManualSearchRow(
    val rawTitle: String,
    val viewDate: String,
    val sourceTier: Int,
    val profileName: String?,
    val durationMs: Long?,
    val reachedMs: Long?,
)
