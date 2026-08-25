package com.chuckfarah.streaminghistory.domain

import com.chuckfarah.streaminghistory.domain.import_.Tier2CsvParser
import com.chuckfarah.streaminghistory.domain.matching.SeriesParser
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [Tier2CsvParser] — TS §§3.2, 3.4.
 *
 * No Android runtime required; all parsing is pure JVM logic.
 */
class Tier2CsvParserTest {

    private lateinit var parser: Tier2CsvParser

    @Before fun setup() {
        val normalizer = TitleNormalizer()
        parser = Tier2CsvParser(normalizer, SeriesParser(normalizer))
    }

    private fun parse(csv: String) =
        parser.parseCsv(csv.trimIndent().byteInputStream())

    // ── Standard valid header ─────────────────────────────────────────────────

    private val VALID_HEADER =
        "Profile Name,Start Time,Duration,Attributes,Title," +
        "Supplemental Video Type,Device Type,Bookmark,Latest Bookmark,Country"

    @Test fun `valid CSV with one data row produces one record`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,2:02:00,,The Irishman,,Smart TV,1:30:00,2:02:00,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows).hasSize(1)
        assertThat(ok.rowsSkipped).isEqualTo(0)
    }

    @Test fun `profile name is preserved`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,2:02:00,,The Irishman,,Smart TV,1:30:00,2:02:00,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].profileName).isEqualTo("Chuck")
    }

    @Test fun `view_date is extracted as YYYY-MM-DD from start time`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,2:02:00,,The Irishman,,Smart TV,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].viewDate).isEqualTo("2021-03-17")
        assertThat(ok.rows[0].startTimeUtc).isEqualTo("2021-03-17 22:00:00")
    }

    @Test fun `start time with UTC suffix is normalised`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00 UTC,2:02:00,,The Irishman,,Smart TV,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].startTimeUtc).isEqualTo("2021-03-17 22:00:00")
    }

    @Test fun `ISO 8601 start time with Z suffix is accepted`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17T22:00:00Z,2:02:00,,The Irishman,,Smart TV,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].startTimeUtc).isEqualTo("2021-03-17 22:00:00")
    }

    // ── Duration parsing ──────────────────────────────────────────────────────

    @Test fun `duration H-MM-SS is converted to milliseconds`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,1:23:45,,The Irishman,,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        // 1h 23m 45s = 5025000 ms
        val expectedMs = 1 * 3_600_000L + 23 * 60_000L + 45 * 1_000L
        assertThat(ok.rows[0].durationMs).isEqualTo(expectedMs)
    }

    @Test fun `zero-hour duration 0-MM-SS is accepted`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,0:45:00,,The Irishman,,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].durationMs).isEqualTo(45 * 60_000L)
    }

    @Test fun `blank duration is stored as null`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,,,The Irishman,,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].durationMs).isNull()
    }

    // ── Bookmark parsing ──────────────────────────────────────────────────────

    @Test fun `latest bookmark 'Not latest view' is stored as null`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,2:02:00,,The Irishman,,Smart TV,1:30:00,Not latest view,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].latestBookmarkMs).isNull()
        assertThat(ok.rows[0].bookmarkMs).isNotNull()
    }

    @Test fun `both bookmarks present and parsed`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,2:02:00,,The Irishman,,Smart TV,1:00:00,1:30:00,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].bookmarkMs).isEqualTo(3_600_000L)
        assertThat(ok.rows[0].latestBookmarkMs).isEqualTo(90 * 60_000L)
    }

    // ── Attributes field ──────────────────────────────────────────────────────

    @Test fun `autoplayed flag detected from Attributes`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,0:00:30,"Autoplayed : user action: None",The Irishman,,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].isAutoplayed).isEqualTo(1)
    }

    @Test fun `hidden flag detected from Attributes`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,2:02:00,"View was hidden",The Irishman,,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].isHidden).isEqualTo(1)
    }

    @Test fun `no attribute flags set when Attributes is blank`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,2:02:00,,The Irishman,,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].isAutoplayed).isEqualTo(0)
        assertThat(ok.rows[0].isHidden).isEqualTo(0)
    }

    // ── Supplemental Video Type skipping ─────────────────────────────────────

    @Test fun `rows with non-blank Supplemental Video Type are skipped`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,0:02:00,,Trailer Title,TRAILER,,,,US
            Chuck,2021-03-17 22:00:00,2:02:00,,The Irishman,,,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows).hasSize(1)
        assertThat(ok.rowsSkipped).isEqualTo(1)
        assertThat(ok.rows[0].rawTitle).isEqualTo("The Irishman")
    }

    // ── Series parsing ────────────────────────────────────────────────────────

    @Test fun `episode titles are classified as SERIES`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-07-04 12:00:00,0:50:00,,Stranger Things: Season 1: Chapter One,,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].contentType).isEqualTo(ContentType.SERIES.name)
        assertThat(ok.rows[0].seriesName).isEqualTo("Stranger Things")
        assertThat(ok.rows[0].seasonNumber).isEqualTo(1)
    }

    @Test fun `non-series titles are UNKNOWN`() {
        val csv = """
            $VALID_HEADER
            Chuck,2021-03-17 22:00:00,2:02:00,,The Irishman,,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows[0].contentType).isEqualTo(ContentType.UNKNOWN.name)
    }

    // ── Session key ───────────────────────────────────────────────────────────

    @Test fun `session key is deterministic for same inputs`() {
        val key1 = Tier2CsvParser.tier2SessionKey("Netflix", "Chuck", "2021-03-17 22:00:00", "The Irishman")
        val key2 = Tier2CsvParser.tier2SessionKey("Netflix", "Chuck", "2021-03-17 22:00:00", "The Irishman")
        assertThat(key1).isEqualTo(key2)
    }

    @Test fun `different start times produce different session keys`() {
        val key1 = Tier2CsvParser.tier2SessionKey("Netflix", "Chuck", "2021-03-17 22:00:00", "The Irishman")
        val key2 = Tier2CsvParser.tier2SessionKey("Netflix", "Chuck", "2021-03-18 01:30:00", "The Irishman")
        assertThat(key1).isNotEqualTo(key2)
    }

    @Test fun `null and empty profile name produce different keys`() {
        val key1 = Tier2CsvParser.tier2SessionKey("Netflix", null,   "2021-03-17 22:00:00", "The Irishman")
        val key2 = Tier2CsvParser.tier2SessionKey("Netflix", "Chuck","2021-03-17 22:00:00", "The Irishman")
        assertThat(key1).isNotEqualTo(key2)
    }

    // ── Header validation ─────────────────────────────────────────────────────

    @Test fun `missing required column returns BadHeader`() {
        // 'Title' is absent
        val csv = """
            Profile Name,Start Time,Duration,Attributes
            Chuck,2021-03-17 22:00:00,2:02:00,
        """
        val outcome = parse(csv)
        assertThat(outcome).isInstanceOf(Tier2CsvParser.ParseOutcome.BadHeader::class.java)
    }

    @Test fun `empty file returns BadHeader`() {
        val outcome = parser.parseCsv("".byteInputStream())
        assertThat(outcome).isInstanceOf(Tier2CsvParser.ParseOutcome.BadHeader::class.java)
    }

    @Test fun `columns are identified case-insensitively`() {
        val csv = """
            PROFILE NAME,START TIME,DURATION,ATTRIBUTES,TITLE,SUPPLEMENTAL VIDEO TYPE,DEVICE TYPE,BOOKMARK,LATEST BOOKMARK,COUNTRY
            Chuck,2021-03-17 22:00:00,2:02:00,,The Irishman,,Smart TV,,,US
        """
        val ok = parse(csv) as Tier2CsvParser.ParseOutcome.Ok
        assertThat(ok.rows).hasSize(1)
    }

    // ── parseDurationMs ───────────────────────────────────────────────────────

    @Test fun `parseDurationMs converts H-MM-SS correctly`() {
        assertThat(parser.parseDurationMs("2:02:00")).isEqualTo(2 * 3_600_000L + 2 * 60_000L)
        assertThat(parser.parseDurationMs("0:01:30")).isEqualTo(90_000L)
        assertThat(parser.parseDurationMs("10:00:00")).isEqualTo(36_000_000L)
    }

    @Test fun `parseDurationMs returns null for blank`() {
        assertThat(parser.parseDurationMs("")).isNull()
        assertThat(parser.parseDurationMs("   ")).isNull()
    }

    @Test fun `parseDurationMs returns null for malformed input`() {
        assertThat(parser.parseDurationMs("not-a-duration")).isNull()
        assertThat(parser.parseDurationMs("1:23")).isNull()
    }

    // ── parseStartTime ────────────────────────────────────────────────────────

    @Test fun `parseStartTime accepts space-separated format`() {
        assertThat(parser.parseStartTime("2021-03-17 22:00:00")).isEqualTo("2021-03-17 22:00:00")
    }

    @Test fun `parseStartTime strips trailing UTC label`() {
        assertThat(parser.parseStartTime("2021-03-17 22:00:00 UTC")).isEqualTo("2021-03-17 22:00:00")
    }

    @Test fun `parseStartTime accepts ISO 8601 with Z`() {
        assertThat(parser.parseStartTime("2021-03-17T22:00:00Z")).isEqualTo("2021-03-17 22:00:00")
    }

    @Test fun `parseStartTime returns null for invalid input`() {
        assertThat(parser.parseStartTime("not-a-time")).isNull()
    }
}
