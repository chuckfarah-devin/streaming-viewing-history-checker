package com.chuckfarah.streaminghistory.ui.formatter

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatterTest {

    @Test fun `formatDuration shows seconds for sub-minute session`() {
        assertThat(formatDuration(4_000L)).isEqualTo("4s")
        assertThat(formatDuration(11_000L)).isEqualTo("11s")
        assertThat(formatDuration(42_000L)).isEqualTo("42s")
    }

    @Test fun `formatReached shows seconds for sub-minute bookmark`() {
        assertThat(formatReached(7_000L)).isEqualTo("7s")
    }

    @Test fun `formatDuration shows minutes and seconds for non-whole minute`() {
        assertThat(formatDuration(90_000L)).isEqualTo("1m 30s")
    }

    @Test fun `formatDuration shows minutes only for whole minute`() {
        assertThat(formatDuration(60_000L)).isEqualTo("1m")
        assertThat(formatDuration(1_680_000L)).isEqualTo("28m")
    }

    @Test fun `formatDuration shows hours and minutes for long sessions`() {
        assertThat(formatDuration(5_000_000L)).isEqualTo("1h 23m")
        assertThat(formatDuration(3_600_000L)).isEqualTo("1h")
    }

    @Test fun `formatDuration does not round nonzero short session down to 0m`() {
        assertThat(formatDuration(4_000L)).isNotEqualTo("0m")
    }

    @Test fun `formatDuration returns 0s for zero input`() {
        assertThat(formatDuration(0L)).isEqualTo("0s")
    }

    @Test fun `formatDuration returns null for null input`() {
        assertThat(formatDuration(null)).isNull()
    }

    @Test fun `formatDate parses ISO date`() {
        assertThat(formatDate("2026-08-23")).isEqualTo("Aug 23, 2026")
    }

    @Test fun `viewingRecords uses correct singular and plural`() {
        assertThat(viewingRecords(1)).isEqualTo("1 viewing record")
        assertThat(viewingRecords(3)).isEqualTo("3 viewing records")
    }

    @Test fun `distinctEpisodes uses correct singular and plural`() {
        assertThat(distinctEpisodes(1)).isEqualTo("1 episode")
        assertThat(distinctEpisodes(5)).isEqualTo("5 episodes")
    }

    @Test fun `seasons uses correct singular and plural`() {
        assertThat(seasons(1)).isEqualTo("1 season")
        assertThat(seasons(2)).isEqualTo("2 seasons")
    }

    @Test fun `repeatBadge only appears for multiple records`() {
        assertThat(repeatBadge(1)).isNull()
        assertThat(repeatBadge(3)).isEqualTo("×3")
    }
}
