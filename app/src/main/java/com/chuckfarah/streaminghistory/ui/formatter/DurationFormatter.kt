package com.chuckfarah.streaminghistory.ui.formatter

/**
 * Formats a duration in milliseconds into a consumer-facing string.
 *
 * Examples:
 *   4_000L   -> "4s"
 *   60_000L  -> "1m"
 *   90_000L  -> "1m 30s"
 *   1_680_000L -> "28m"
 *   5_000_000L -> "1h 23m"
 *
 * Nonpositive or null values are not displayed.
 */
fun formatDuration(ms: Long?): String? {
    if (ms == null) return null
    val totalSeconds = ms / 1000
    if (totalSeconds <= 0) return "0s"
    if (totalSeconds < 60) return "${totalSeconds}s"

    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> if (minutes > 0) "${hours}h ${minutes}m" else "${hours}h"
        seconds > 0 -> "${minutes}m ${seconds}s"
        else -> "${minutes}m"
    }
}

/**
 * Formats a bookmark/reached position in milliseconds.
 *
 * Same rules as [formatDuration].
 */
fun formatReached(ms: Long?): String? = formatDuration(ms)
