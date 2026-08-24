package com.chuckfarah.streaminghistory.domain.model

/**
 * Series-level aggregate computed from all SERIES viewing records that share
 * the same [normalizedSeriesName].
 *
 * Per TS §4.4 these three values are always presented separately and never
 * summed or conflated.
 */
data class SeriesStats(
    val seriesName: String,
    val normalizedSeriesName: String,
    /** Total number of viewing sessions in history, including re-watches. */
    val viewingOccurrences: Int,
    /** Count of distinct episode titles (by episode_title or raw_title). */
    val distinctEpisodes: Int,
    /** Count of distinct season numbers present. */
    val seasonsRepresented: Int,
    val mostRecentDate: String,
)
