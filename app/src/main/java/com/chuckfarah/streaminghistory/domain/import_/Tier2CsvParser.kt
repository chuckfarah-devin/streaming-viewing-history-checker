package com.chuckfarah.streaminghistory.domain.import_

import com.chuckfarah.streaminghistory.domain.matching.SeriesParser
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses the Netflix full-data export file (Tier 2) — TS §3.2.
 *
 * Accepted inputs:
 *  - A raw `ViewingActivity.csv` stream
 *  - A ZIP archive stream; [extractFromZip] locates the correct entry
 *
 * Produces a list of [Tier2Row] objects ready for reconciliation /
 * insertion by [Tier2Reconciler].
 */
@Singleton
class Tier2CsvParser @Inject constructor(
    private val normalizer: TitleNormalizer,
    private val seriesParser: SeriesParser,
) {

    companion object {
        /** Path inside the Netflix ZIP export. */
        const val ZIP_ENTRY_PATH = "CONTENT_INTERACTION/ViewingActivity.csv"

        private const val PROVIDER = "Netflix"
        private const val NOT_LATEST_VIEW = "not latest view"

        // Required column names (compared case-insensitively after trimming)
        private val REQUIRED_COLS = setOf("profile name", "start time", "duration", "title")

        private val FMT_DATETIME_SPACE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val FMT_DATETIME_T_Z   = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

        /**
         * Compute the Tier 2 session key (TS §3.4):
         * SHA-256( provider "|" profileName "|" startTimeUtc "|" rawTitle )
         */
        fun tier2SessionKey(
            provider: String,
            profileName: String?,
            startTimeUtc: String,
            rawTitle: String,
        ): String = Tier1CsvParser.sha256Hex(
            "$provider|${profileName ?: ""}|$startTimeUtc|$rawTitle"
                .toByteArray(Charsets.UTF_8)
        )
    }

    // ── Public surface ────────────────────────────────────────────────────────

    sealed class ParseOutcome {
        data class Ok(
            val rows: List<Tier2Row>,
            val rowsSkipped: Int,
        ) : ParseOutcome()

        data class BadHeader(val reason: String) : ParseOutcome()
    }

    /**
     * Locate [ZIP_ENTRY_PATH] inside a ZIP archive and parse it.
     * Returns [ParseOutcome.BadHeader] if the entry is not found.
     */
    fun parseZip(zipStream: InputStream): ParseOutcome {
        val zis = ZipInputStream(zipStream)
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == ZIP_ENTRY_PATH) {
                return parseCsv(zis)
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        return ParseOutcome.BadHeader(
            "ZIP does not contain \"$ZIP_ENTRY_PATH\". " +
            "Please use the full Netflix personal-data export ZIP."
        )
    }

    /** Parse a raw `ViewingActivity.csv` input stream. */
    fun parseCsv(inputStream: InputStream): ParseOutcome {
        val lines = inputStream.bufferedReader(Charsets.UTF_8).readLines()
        if (lines.isEmpty()) return ParseOutcome.BadHeader("File is empty")

        // ── Header ─────────────────────────────────────────────────────────
        val headerFields = parseCsvLine(lines[0]).map { it.trim().lowercase() }

        val missingCols = REQUIRED_COLS.filter { col -> headerFields.none { it == col } }
        if (missingCols.isNotEmpty()) {
            return ParseOutcome.BadHeader(
                "Missing required columns: ${missingCols.joinToString()}. " +
                "Expected a Netflix ViewingActivity.csv header."
            )
        }

        val idx = headerFields.withIndex().associate { (i, name) -> name to i }

        val profileIdx     = idx["profile name"]!!
        val startTimeIdx   = idx["start time"]!!
        val durationIdx    = idx["duration"]!!
        val titleIdx       = idx["title"]!!
        val attributesIdx  = idx["attributes"]
        val suppTypeIdx    = idx["supplemental video type"]
        val deviceIdx      = idx["device type"]
        val bookmarkIdx    = idx["bookmark"]
        val latestBmIdx    = idx["latest bookmark"]

        // ── Data rows ──────────────────────────────────────────────────────
        val rows = mutableListOf<Tier2Row>()
        var skipped = 0

        for (lineIdx in 1 until lines.size) {
            val line = lines[lineIdx].trim()
            if (line.isBlank()) continue

            val fields = parseCsvLine(line)

            // Skip rows that are too short to have all required fields
            val minRequired = maxOf(profileIdx, startTimeIdx, durationIdx, titleIdx)
            if (fields.size <= minRequired) { skipped++; continue }

            val rawTitle = fields.getOrElse(titleIdx) { "" }.trim()
            if (rawTitle.isBlank()) { skipped++; continue }

            // Skip supplemental content (trailers, clips, etc.) — TS §3.2
            val suppType = suppTypeIdx?.let { fields.getOrElse(it) { "" }.trim() } ?: ""
            if (suppType.isNotBlank()) { skipped++; continue }

            val startTimeRaw = fields.getOrElse(startTimeIdx) { "" }.trim()
            val startTimeUtc = parseStartTime(startTimeRaw)
            if (startTimeUtc == null) { skipped++; continue }
            val viewDate     = startTimeUtc.substring(0, 10)   // YYYY-MM-DD

            val profileName    = fields.getOrElse(profileIdx) { "" }.trim().ifEmpty { null }
            val durationMs     = parseDurationMs(fields.getOrElse(durationIdx) { "" }.trim())
            val attributesRaw  = attributesIdx?.let {
                fields.getOrElse(it) { "" }.trim().ifEmpty { null }
            }
            val isAutoplayed   = if (
                attributesRaw?.contains("user action: None", ignoreCase = true) == true
            ) 1 else 0
            val isHidden       = if (
                attributesRaw?.contains("View was hidden", ignoreCase = true) == true
            ) 1 else 0
            val deviceType     = deviceIdx?.let {
                fields.getOrElse(it) { "" }.trim().ifEmpty { null }
            }
            val bookmarkMs     = bookmarkIdx?.let {
                parseDurationMs(fields.getOrElse(it) { "" }.trim())
            }
            val latestBmMs     = latestBmIdx?.let { i ->
                val raw = fields.getOrElse(i) { "" }.trim()
                when {
                    raw.isBlank()                             -> null
                    raw.equals(NOT_LATEST_VIEW, ignoreCase = true) -> null
                    else                                      -> parseDurationMs(raw)
                }
            }

            val sessionKey = tier2SessionKey(PROVIDER, profileName, startTimeUtc, rawTitle)
            val parsed     = seriesParser.parse(rawTitle)

            rows += Tier2Row(
                provider             = PROVIDER,
                rawTitle             = rawTitle,
                displayTitle         = parsed.displayTitle,
                normalizedTitle      = normalizer.normalize(rawTitle),
                contentType          = parsed.contentType.name,
                seriesName           = parsed.seriesName,
                normalizedSeriesName = parsed.normalizedSeriesName,
                seasonLabel          = parsed.seasonLabel,
                seasonNumber         = parsed.seasonNumber,
                episodeTitle         = parsed.episodeTitle,
                viewDate             = viewDate,
                startTimeUtc         = startTimeUtc,
                durationMs           = durationMs,
                bookmarkMs           = bookmarkMs,
                latestBookmarkMs     = latestBmMs,
                profileName          = profileName,
                isHidden             = isHidden,
                isAutoplayed         = isAutoplayed,
                attributesRaw        = attributesRaw,
                deviceType           = deviceType,
                sessionKey           = sessionKey,
            )
        }

        return ParseOutcome.Ok(rows = rows, rowsSkipped = skipped)
    }

    // ── CSV parsing ───────────────────────────────────────────────────────────

    /**
     * RFC 4180 CSV line parser — identical logic to Tier1CsvParser so both
     * parsers handle quoted commas and escaped quotes consistently.
     */
    internal fun parseCsvLine(line: String): List<String> {
        val fields   = mutableListOf<String>()
        val buf      = StringBuilder()
        var inQuotes = false
        var i        = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes  -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        buf.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> { fields += buf.toString(); buf.clear() }
                else                  -> buf.append(c)
            }
            i++
        }
        fields += buf.toString()
        return fields
    }

    // ── Time/duration parsing ─────────────────────────────────────────────────

    /**
     * Parse a Netflix start-time string to normalised "YYYY-MM-DD HH:MM:SS" UTC.
     * Accepted formats:
     *  - "2021-03-17 22:00:00"      (most common)
     *  - "2021-03-17 22:00:00 UTC"  (with trailing UTC label)
     *  - "2021-03-17T22:00:00Z"     (ISO 8601 with Z)
     */
    internal fun parseStartTime(raw: String): String? {
        val trimmed = raw.trim().removeSuffix(" UTC").trim()
        // Try "YYYY-MM-DD HH:MM:SS"
        runCatching {
            LocalDateTime.parse(trimmed, FMT_DATETIME_SPACE)
            return trimmed   // already in target format
        }
        // Try ISO 8601 "YYYY-MM-DDTHH:MM:SSZ"
        runCatching {
            val dt = LocalDateTime.parse(trimmed, FMT_DATETIME_T_Z)
            return dt.format(FMT_DATETIME_SPACE)
        }
        return null
    }

    /**
     * Parse Netflix duration/bookmark string "H:MM:SS" or "HH:MM:SS" to ms.
     * Returns null for blank or unparseable input (TS §3.2).
     */
    internal fun parseDurationMs(raw: String): Long? {
        if (raw.isBlank()) return null
        val parts = raw.trim().split(":")
        if (parts.size != 3) return null
        return runCatching {
            val h  = parts[0].toLong()
            val m  = parts[1].toLong()
            val s  = parts[2].toLong()
            h * 3_600_000L + m * 60_000L + s * 1_000L
        }.getOrNull()
    }
}

// ── Parsed row data class ─────────────────────────────────────────────────────

/**
 * One parsed Tier 2 row before reconciliation.
 * All fields are populated by [Tier2CsvParser]; [importId] is assigned
 * by the repository after the import batch record is created.
 */
data class Tier2Row(
    val provider: String,
    val rawTitle: String,
    val displayTitle: String,
    val normalizedTitle: String,
    val contentType: String,
    val seriesName: String?,
    val normalizedSeriesName: String?,
    val seasonLabel: String?,
    val seasonNumber: Int?,
    val episodeTitle: String?,
    val viewDate: String,        // YYYY-MM-DD
    val startTimeUtc: String,    // YYYY-MM-DD HH:MM:SS
    val durationMs: Long?,
    val bookmarkMs: Long?,
    val latestBookmarkMs: Long?,
    val profileName: String?,
    val isHidden: Int,           // 0 or 1
    val isAutoplayed: Int,       // 0 or 1
    val attributesRaw: String?,
    val deviceType: String?,
    val sessionKey: String,
)
