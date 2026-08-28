package com.chuckfarah.streaminghistory.domain.model

/**
 * The fully-resolved result shown on the result screen.
 *
 * Watched    — title found in history.
 * NotWatched — no record found for the requested profile. Never produced by a technical failure.
 * Error      — a technical failure occurred (recognition failure, DB error, etc.).
 *              Must never be displayed as "not watched."
 */
sealed class ViewingResult {
    data class Watched(
        val displayTitle: String,
        val normalizedTitle: String,
        val contentType: ContentType,
        /** Active profile used for this result, or null for Tier 1 / no profile. */
        val profileName: String?,
        /** Total viewing occurrences in history (including re-watches). */
        val viewingOccurrences: Int,
        val mostRecentDate: String,
        /** All recorded viewing dates, newest first. */
        val allDates: List<String>,
        /** Duration of the most recent viewing session, if known. */
        val mostRecentDuration: Long?,
        /** Furthest position reached in the most recent session, if known. */
        val reached: Long?,
        /** All viewing sessions for this title, newest first. */
        val sessions: List<ViewingSession>,
        /** Populated only for SERIES records (TS §4.4). Null for UNKNOWN/MOVIE. */
        val seriesStats: SeriesStats?,
        /** Distinct episode viewing records for SERIES, newest first. Empty for UNKNOWN/MOVIE. */
        val episodes: List<EpisodeRecord> = emptyList(),
    ) : ViewingResult()

    data class NotWatched(
        val displayTitle: String,
        val normalizedTitle: String,
    ) : ViewingResult()

    data class Error(val message: String) : ViewingResult()
}
