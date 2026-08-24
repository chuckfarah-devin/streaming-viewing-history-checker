package com.chuckfarah.streaminghistory.domain.model

/**
 * One individual viewing session for a SERIES episode.
 * Displayed as a row in the episode list on the result screen.
 */
data class EpisodeRecord(
    /** Full raw Netflix title, e.g. "Stranger Things: Season 1: Chapter One: …" */
    val rawTitle: String,
    /** Season/Part/Volume label, e.g. "Season 1". Null when not parseable. */
    val seasonLabel: String?,
    /** Episode title portion only, e.g. "Chapter One: The Vanishing of Will Byers". */
    val episodeTitle: String?,
    /** ISO 8601 view date, e.g. "2026-08-23". */
    val viewDate: String,
)
