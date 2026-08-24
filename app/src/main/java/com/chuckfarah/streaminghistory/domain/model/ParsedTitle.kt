package com.chuckfarah.streaminghistory.domain.model

/**
 * Result of parsing one raw Netflix title string.
 *
 * For SERIES records all series-specific fields are populated.
 * For UNKNOWN records only [rawTitle] and [contentType] are meaningful.
 */
data class ParsedTitle(
    val rawTitle: String,
    val displayTitle: String,
    val contentType: ContentType,
    /** Series name portion, e.g. "Stranger Things". Null for UNKNOWN/MOVIE. */
    val seriesName: String? = null,
    /** Normalized series name for matching. Null for UNKNOWN/MOVIE. */
    val normalizedSeriesName: String? = null,
    /** Raw season/part label, e.g. "Season 1", "Part 2". Null for non-SERIES. */
    val seasonLabel: String? = null,
    /** Integer extracted from [seasonLabel]. Null if not determinable. */
    val seasonNumber: Int? = null,
    /** Episode title. Null for non-SERIES or when absent from the raw title. */
    val episodeTitle: String? = null,
)
