package com.chuckfarah.streaminghistory.domain

import com.chuckfarah.streaminghistory.domain.matching.SeriesParser
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class SeriesParserTest {

    private lateinit var parser: SeriesParser

    @Before fun setup() { parser = SeriesParser(TitleNormalizer()) }

    // ── SERIES detection ──────────────────────────────────────────────────────

    @Test fun `standard Season N record is SERIES`() {
        val r = parser.parse("Stranger Things: Season 1: Chapter One: The Vanishing of Will Byers")
        assertThat(r.contentType).isEqualTo(ContentType.SERIES)
        assertThat(r.seriesName).isEqualTo("Stranger Things")
        assertThat(r.seasonLabel).isEqualTo("Season 1")
        assertThat(r.seasonNumber).isEqualTo(1)
        assertThat(r.episodeTitle).isEqualTo("Chapter One: The Vanishing of Will Byers")
    }

    @Test fun `Part N record is SERIES`() {
        val r = parser.parse("Ozark: Part 1: Blue Cat")
        assertThat(r.contentType).isEqualTo(ContentType.SERIES)
        assertThat(r.seriesName).isEqualTo("Ozark")
        assertThat(r.seasonLabel).isEqualTo("Part 1")
        assertThat(r.seasonNumber).isEqualTo(1)
    }

    @Test fun `Volume N record is SERIES`() {
        val r = parser.parse("Cobra Kai: Volume 1: Ace Degenerate")
        assertThat(r.contentType).isEqualTo(ContentType.SERIES)
        assertThat(r.seriesName).isEqualTo("Cobra Kai")
        assertThat(r.seasonLabel).isEqualTo("Volume 1")
    }

    @Test fun `Chapter N record is SERIES`() {
        val r = parser.parse("Some Show: Chapter 3: Title")
        assertThat(r.contentType).isEqualTo(ContentType.SERIES)
        assertThat(r.seasonLabel).isEqualTo("Chapter 3")
        assertThat(r.seasonNumber).isEqualTo(3)
    }

    @Test fun `Book N record is SERIES`() {
        val r = parser.parse("Avatar: The Last Airbender: Book 1: The Boy in the Iceberg")
        assertThat(r.contentType).isEqualTo(ContentType.SERIES)
        // Series name includes sub-series name up to the Book pattern
        assertThat(r.seriesName).isEqualTo("Avatar: The Last Airbender")
        assertThat(r.seasonLabel).isEqualTo("Book 1")
    }

    @Test fun `Season N at end of string (no episode) is SERIES`() {
        val r = parser.parse("The Crown: Season 4")
        assertThat(r.contentType).isEqualTo(ContentType.SERIES)
        assertThat(r.seriesName).isEqualTo("The Crown")
        assertThat(r.episodeTitle).isNull()
    }

    // ── Series name containing colons ─────────────────────────────────────────

    @Test fun `series name with colons is extracted correctly`() {
        val r = parser.parse("Avatar: The Last Airbender: Book 1: The Boy in the Iceberg")
        // Everything before ": Book 1" is the series name
        assertThat(r.seriesName).isEqualTo("Avatar: The Last Airbender")
    }

    // ── Episode title containing colons ───────────────────────────────────────

    @Test fun `episode title with internal colon is preserved`() {
        val r = parser.parse("Stranger Things: Season 1: Chapter One: The Vanishing of Will Byers")
        assertThat(r.episodeTitle).isEqualTo("Chapter One: The Vanishing of Will Byers")
    }

    // ── UNKNOWN fallback ──────────────────────────────────────────────────────

    @Test fun `plain title with no series pattern is UNKNOWN`() {
        val r = parser.parse("The Irishman")
        assertThat(r.contentType).isEqualTo(ContentType.UNKNOWN)
        assertThat(r.seriesName).isNull()
        assertThat(r.seasonLabel).isNull()
    }

    @Test fun `title with colon but no season indicator is UNKNOWN, not MOVIE`() {
        // "Knives Out: Glass Onion" has a colon but no Season/Part indicator
        val r = parser.parse("Glass Onion: A Knives Out Mystery")
        assertThat(r.contentType).isEqualTo(ContentType.UNKNOWN)
    }

    @Test fun `MOVIE is never assigned by parser`() {
        // Exhaustive check: no input should ever produce MOVIE
        val inputs = listOf(
            "The Irishman",
            "It",
            "Up",
            "Glass Onion: A Knives Out Mystery",
            "Avengers: Endgame",
        )
        for (input in inputs) {
            assertThat(parser.parse(input).contentType).isNotEqualTo(ContentType.MOVIE)
        }
    }

    // ── Display title ─────────────────────────────────────────────────────────

    @Test fun `SERIES display title is the series name, not the full raw title`() {
        val r = parser.parse("Stranger Things: Season 1: Chapter One")
        assertThat(r.displayTitle).isEqualTo("Stranger Things")
        assertThat(r.displayTitle).isNotEqualTo(r.rawTitle)
    }

    @Test fun `UNKNOWN display title is the full raw title`() {
        val r = parser.parse("The Irishman")
        assertThat(r.displayTitle).isEqualTo("The Irishman")
    }

    // ── Season number extraction ──────────────────────────────────────────────

    @Test fun `multi-digit season number is extracted`() {
        val r = parser.parse("Long Running Show: Season 12: Some Episode")
        assertThat(r.seasonNumber).isEqualTo(12)
    }

    @Test fun `case-insensitive pattern matching`() {
        val r = parser.parse("Some Show: season 2: Episode Title")
        assertThat(r.contentType).isEqualTo(ContentType.SERIES)
        assertThat(r.seasonNumber).isEqualTo(2)
    }
}
