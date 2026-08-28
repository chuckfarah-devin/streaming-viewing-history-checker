package com.chuckfarah.streaminghistory.ui.formatter

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val displayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

/**
 * Formats an ISO-8601 calendar date (YYYY-MM-DD) into a consumer-facing
 * display date.  Returns the original string if it cannot be parsed.
 */
fun formatDate(isoDate: String): String =
    try {
        LocalDate.parse(isoDate).format(displayFormatter)
    } catch (_: Exception) {
        isoDate
    }
