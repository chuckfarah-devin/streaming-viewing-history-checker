package com.chuckfarah.streaminghistory.domain.model

/**
 * Content classification assigned during import.
 *
 * SERIES  — a recognized series/episode pattern was found in the raw title.
 * UNKNOWN — no recognized pattern; the title could be a movie, documentary,
 *           limited series, special, or any other content type. This is the
 *           default for non-SERIES records in Phase 1.
 * MOVIE   — reserved for future use when an explicit movie signal is available.
 *           The Phase 1 parser never assigns this value.
 */
enum class ContentType { SERIES, UNKNOWN, MOVIE }
