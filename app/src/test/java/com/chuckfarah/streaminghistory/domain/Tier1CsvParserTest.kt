package com.chuckfarah.streaminghistory.domain

import com.chuckfarah.streaminghistory.domain.import_.Tier1CsvParser
import com.chuckfarah.streaminghistory.domain.matching.SeriesParser
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class Tier1CsvParserTest {

    private lateinit var parser: Tier1CsvParser
    private val fingerprint = "test_fingerprint_abc"
    private val batchId     = 1L

    @Before fun setup() {
        parser = Tier1CsvParser(TitleNormalizer(), SeriesParser(TitleNormalizer()))
    }

    private fun parse(csv: String) =
        parser.parse(csv.trimIndent().byteInputStream(), fingerprint, batchId)

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test fun `valid CSV with two rows produces two records`() {
        val outcome = parse("""
            Title,Date
            The Irishman,3/17/2021
            The Crown: Season 4: Favourites,11/1/2020
        """)
        assertThat(outcome).isInstanceOf(Tier1CsvParser.ParseOutcome.Ok::class.java)
        val ok = outcome as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records).hasSize(2)
        assertThat(ok.rowsSkipped).isEqualTo(0)
    }

    @Test fun `view dates are stored in ISO 8601 format`() {
        val ok = parse("""
            Title,Date
            The Irishman,3/17/2021
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records[0].viewDate).isEqualTo("2021-03-17")
    }

    @Test fun `ISO 8601 date fallback is accepted`() {
        val ok = parse("""
            Title,Date
            The Irishman,2021-03-17
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records[0].viewDate).isEqualTo("2021-03-17")
    }

    @Test fun `series titles are parsed to SERIES content type`() {
        val ok = parse("""
            Title,Date
            Stranger Things: Season 1: Chapter One,7/4/2022
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records[0].contentType).isEqualTo(ContentType.SERIES.name)
        assertThat(ok.records[0].seriesName).isEqualTo("Stranger Things")
    }

    @Test fun `non-series titles are UNKNOWN, never MOVIE`() {
        val ok = parse("""
            Title,Date
            The Irishman,3/17/2021
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records[0].contentType).isEqualTo(ContentType.UNKNOWN.name)
        assertThat(ok.records[0].contentType).isNotEqualTo(ContentType.MOVIE.name)
    }

    // ── Quoted fields (RFC 4180) ───────────────────────────────────────────────

    @Test fun `quoted title containing comma is parsed as single field`() {
        val ok = parse("""
            Title,Date
            "Hm, A Title With Comma",3/17/2021
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records).hasSize(1)
        assertThat(ok.records[0].rawTitle).isEqualTo("Hm, A Title With Comma")
    }

    @Test fun `quoted date field is accepted`() {
        val ok = parse("""
            Title,Date
            The Irishman,"3/17/2021"
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records[0].viewDate).isEqualTo("2021-03-17")
    }

    // ── Session keys ──────────────────────────────────────────────────────────

    @Test fun `each row gets a distinct session key`() {
        val ok = parse("""
            Title,Date
            The Irishman,3/17/2021
            The Irishman,3/17/2021
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records).hasSize(2)
        val keys = ok.records.map { it.sessionKey }
        assertThat(keys[0]).isNotEqualTo(keys[1])
    }

    @Test fun `session key is deterministic for same fingerprint and row index`() {
        val key1 = Tier1CsvParser.tier1SessionKey("fp1", 0)
        val key2 = Tier1CsvParser.tier1SessionKey("fp1", 0)
        assertThat(key1).isEqualTo(key2)
    }

    @Test fun `same title and date in same file produces two records (same-day repeat preserved)`() {
        val ok = parse("""
            Title,Date
            The Irishman,3/17/2021
            The Irishman,3/17/2021
        """) as Tier1CsvParser.ParseOutcome.Ok
        // Both rows are preserved; session keys differ by row index
        assertThat(ok.records).hasSize(2)
        assertThat(ok.records[0].rawTitle).isEqualTo(ok.records[1].rawTitle)
        assertThat(ok.records[0].viewDate).isEqualTo(ok.records[1].viewDate)
        assertThat(ok.records[0].sessionKey).isNotEqualTo(ok.records[1].sessionKey)
    }

    // ── Malformed rows ────────────────────────────────────────────────────────

    @Test fun `blank row is silently skipped`() {
        val ok = parse("""
            Title,Date
            The Irishman,3/17/2021

            Ozark: Part 1: Episode,1/1/2022
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records).hasSize(2)
        assertThat(ok.rowsSkipped).isEqualTo(0)
    }

    @Test fun `row with invalid date is skipped and counted`() {
        val ok = parse("""
            Title,Date
            The Irishman,not-a-date
            Valid Movie,3/17/2021
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records).hasSize(1)
        assertThat(ok.rowsSkipped).isEqualTo(1)
    }

    @Test fun `row with only one field is skipped`() {
        val ok = parse("""
            Title,Date
            JustATitle
            Valid Movie,3/17/2021
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records).hasSize(1)
        assertThat(ok.rowsSkipped).isEqualTo(1)
    }

    @Test fun `row with blank title is skipped`() {
        val ok = parse("""
            Title,Date
            ,3/17/2021
            Valid Movie,3/17/2021
        """) as Tier1CsvParser.ParseOutcome.Ok
        assertThat(ok.records).hasSize(1)
        assertThat(ok.rowsSkipped).isEqualTo(1)
    }

    // ── Header validation ─────────────────────────────────────────────────────

    @Test fun `wrong header returns BadHeader`() {
        val outcome = parse("""
            Name,WatchedOn
            The Irishman,3/17/2021
        """)
        assertThat(outcome).isInstanceOf(Tier1CsvParser.ParseOutcome.BadHeader::class.java)
    }

    @Test fun `empty file returns BadHeader`() {
        val outcome = parser.parse("".byteInputStream(), fingerprint, batchId)
        assertThat(outcome).isInstanceOf(Tier1CsvParser.ParseOutcome.BadHeader::class.java)
    }

    @Test fun `header is case-insensitive`() {
        val outcome = parse("""
            TITLE,DATE
            The Irishman,3/17/2021
        """)
        assertThat(outcome).isInstanceOf(Tier1CsvParser.ParseOutcome.Ok::class.java)
    }

    // ── parseCsvLine ──────────────────────────────────────────────────────────

    @Test fun `parseCsvLine splits simple two-field row`() {
        val fields = parser.parseCsvLine("The Irishman,3/17/2021")
        assertThat(fields).containsExactly("The Irishman", "3/17/2021").inOrder()
    }

    @Test fun `parseCsvLine handles quoted comma inside title`() {
        val fields = parser.parseCsvLine("\"Title, With Comma\",3/17/2021")
        assertThat(fields[0]).isEqualTo("Title, With Comma")
    }

    @Test fun `parseCsvLine handles escaped double-quote inside field`() {
        val fields = parser.parseCsvLine("\"Title \"\"Nickname\"\"\",3/17/2021")
        assertThat(fields[0]).isEqualTo("Title \"Nickname\"")
    }

    // ── parseDate ─────────────────────────────────────────────────────────────

    @Test fun `parseDate accepts M-D-YYYY`() {
        assertThat(parser.parseDate("3/17/2021")).isEqualTo("2021-03-17")
    }

    @Test fun `parseDate accepts single-digit month and day`() {
        assertThat(parser.parseDate("1/5/2020")).isEqualTo("2020-01-05")
    }

    @Test fun `parseDate accepts ISO 8601 fallback`() {
        assertThat(parser.parseDate("2021-03-17")).isEqualTo("2021-03-17")
    }

    @Test fun `parseDate returns null for invalid input`() {
        assertThat(parser.parseDate("not-a-date")).isNull()
    }

    @Test fun `parseDate returns null for empty string`() {
        assertThat(parser.parseDate("")).isNull()
    }
}
