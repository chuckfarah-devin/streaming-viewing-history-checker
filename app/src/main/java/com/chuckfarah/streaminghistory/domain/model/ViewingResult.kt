package com.chuckfarah.streaminghistory.domain.model

/**
 * The fully-resolved result shown on the result screen.
 *
 * Watched    — title found in history.
 * NotWatched — no record found. Never produced by a technical failure.
 * Error      — a technical failure occurred (recognition failure, DB error, etc.).
 *              Must never be displayed as "not watched."
 */
sealed class ViewingResult {
    data class Watched(
        val displayTitle: String,
        val contentType: ContentType,
        /** Total viewing occurrences in history (including re-watches). */
        val viewingOccurrences: Int,
        val mostRecentDate: String,
        /** All recorded viewing dates, newest first. */
        val allDates: List<String>,
        /** Populated only for SERIES records (TS §4.4). Null for UNKNOWN/MOVIE. */
        val seriesStats: SeriesStats?,
        /** Individual episode viewing records for SERIES, newest first. Empty for UNKNOWN/MOVIE. */
        val episodes: List<EpisodeRecord> = emptyList(),
    ) : ViewingResult()

    object NotWatched : ViewingResult()

    data class Error(val message: String) : ViewingResult()
}
