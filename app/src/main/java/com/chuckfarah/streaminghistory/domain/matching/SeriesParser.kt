package com.chuckfarah.streaminghistory.domain.matching

import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.chuckfarah.streaminghistory.domain.model.ParsedTitle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decomposes a raw Netflix title string into series, season, and episode
 * components (TS §4.2).
 *
 * Returns SERIES when a recognized pattern is found; UNKNOWN otherwise.
 * MOVIE is never assigned by this parser (reserved for future use).
 */
@Singleton
class SeriesParser @Inject constructor(
    private val normalizer: TitleNormalizer,
) {
    companion object {
        /**
         * Ordered list of season/part indicator patterns.
         * Each entry is (regex, label-group-index).
         * The regex captures the separator colon, the label text, and an
         * optional trailing colon that introduces the episode title.
         *
         * Matching is case-insensitive.
         */
        private val SERIES_PATTERNS: List<Regex> = listOf(
            // Numbered indicators (most common)
            Regex(""": (Season \d+)(?::|$)""",        RegexOption.IGNORE_CASE),
            Regex(""": (Part \d+)(?::|$)""",           RegexOption.IGNORE_CASE),
            Regex(""": (Volume \d+)(?::|$)""",         RegexOption.IGNORE_CASE),
            Regex(""": (Chapter \d+)(?::|$)""",        RegexOption.IGNORE_CASE),
            Regex(""": (Book \d+)(?::|$)""",           RegexOption.IGNORE_CASE),
            Regex(""": (Series \d+)(?::|$)""",         RegexOption.IGNORE_CASE),
            // Un-numbered indicators — very common on Netflix for limited runs
            Regex(""": (Limited Series)(?::|$)""",     RegexOption.IGNORE_CASE),
            Regex(""": (Miniseries)(?::|$)""",         RegexOption.IGNORE_CASE),
            Regex(""": (Mini-Series)(?::|$)""",        RegexOption.IGNORE_CASE),
            Regex(""": (Limited Season)(?::|$)""",     RegexOption.IGNORE_CASE),
        )

        private val SEASON_NUMBER_RE = Regex("""\d+""")
    }

    fun parse(rawTitle: String): ParsedTitle {
        for (pattern in SERIES_PATTERNS) {
            val match = pattern.find(rawTitle) ?: continue

            // Everything before ": <Season N>" is the series name
            val seriesName = rawTitle.substring(0, match.range.first).trim()

            // The label is the first capture group: "Season 1", "Part 2", …
            val seasonLabel = match.groupValues[1]
            val seasonNumber = SEASON_NUMBER_RE.find(seasonLabel)?.value?.toIntOrNull()

            // Everything after the full match is the episode title (may be empty)
            val afterMatch = rawTitle.substring(match.range.last + 1).trim()
            val episodeTitle = afterMatch.ifEmpty { null }

            // Display title for a series is the series name alone
            val normalizedSeriesName = normalizer.normalize(seriesName)

            return ParsedTitle(
                rawTitle              = rawTitle,
                displayTitle          = seriesName,
                contentType           = ContentType.SERIES,
                seriesName            = seriesName,
                normalizedSeriesName  = normalizedSeriesName,
                seasonLabel           = seasonLabel,
                seasonNumber          = seasonNumber,
                episodeTitle          = episodeTitle,
            )
        }

        // A colon alone is not enough to establish a series.  Without an explicit
        // season/part/volume/chapter pattern (or one of the limited-series labels),
        // a colon-containing title is treated as an UNKNOWN standalone title.  This
        // keeps movie subtitles like "El Camino: A Breaking Bad Movie" intact instead
        // of inventing a series/episode split.
        return ParsedTitle(
            rawTitle     = rawTitle,
            displayTitle = rawTitle,
            contentType  = ContentType.UNKNOWN,
        )
    }
}
