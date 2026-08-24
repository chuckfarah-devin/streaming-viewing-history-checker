package com.chuckfarah.streaminghistory.domain.model

/**
 * A candidate title surfaced during an ambiguous match.
 * Displayed to the user for selection.
 */
data class TitleCandidate(
    val displayTitle: String,
    val normalizedTitle: String,
    /** The fuzzy score that produced this candidate (0–100). */
    val score: Int,
    /** Total number of viewing records for this title. */
    val recordCount: Int,
    val contentType: ContentType,
)
