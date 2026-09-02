package com.chuckfarah.streaminghistory.domain.model

/**
 * One viewing session shown in the result-screen history list.
 *
 * Tier 1 records leave [durationMs] and [reachedMs] null.
 * Tier 2 records populate them where available.
 */
data class ViewingSession(
    val rawTitle: String,
    val viewDate: String,
    val durationMs: Long?,
    val reachedMs: Long?,
    val profileName: String?,
)
