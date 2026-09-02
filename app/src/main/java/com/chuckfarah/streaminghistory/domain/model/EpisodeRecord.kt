package com.chuckfarah.streaminghistory.domain.model

/**
 * One distinct episode in a series result.
 *
 * [recordCount] is the number of viewing sessions for this episode.
 * [records] lists each individual session, newest first.
 */
data class EpisodeRecord(
    /** Full raw Netflix title, e.g. "Stranger Things: Season 1: Chapter One: …" */
    val rawTitle: String,
    /** Season/Part/Volume label, e.g. "Season 1". Null when not parseable. */
    val seasonLabel: String?,
    /** Parsed season number when available. */
    val seasonNumber: Int?,
    /** Episode title portion only, e.g. "Chapter One: The Vanishing of Will Byers". */
    val episodeTitle: String?,
    /** ISO 8601 view date of the most recent viewing of this episode. */
    val mostRecentDate: String,
    /** Number of viewing sessions for this episode (including re-watches). */
    val recordCount: Int,
    /** Individual viewing sessions for this episode, newest first. */
    val records: List<ViewingSession>,
)
