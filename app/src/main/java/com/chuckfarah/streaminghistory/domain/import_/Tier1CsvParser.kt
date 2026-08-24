package com.chuckfarah.streaminghistory.domain.import_

import com.chuckfarah.streaminghistory.data.db.entity.ImportBatchEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.domain.matching.SeriesParser
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses a Netflix Tier 1 "NetflixViewingHistory.csv" stream (TS §3.1).
 *
 * Accepts an [InputStream] so that tests can supply arbitrary data without a
 * real file on disk.  All I/O decisions (obtaining the stream, computing the
 * fingerprint) are made by the caller.
 */
@Singleton
class Tier1CsvParser @Inject constructor(
    private val normalizer: TitleNormalizer,
    private val seriesParser: SeriesParser,
) {
    companion object {
        private val EXPECTED_HEADER = "title,date"    // lowercased for comparison
        private val DATE_FORMAT_MDY  = DateTimeFormatter.ofPattern("M/d/yyyy")
        private val DATE_FORMAT_ISO  = DateTimeFormatter.ISO_LOCAL_DATE

        /**
         * Compute SHA-256 hex digest of arbitrary bytes.
         * Used for both the file fingerprint and session keys.
         */
        fun sha256Hex(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(bytes).joinToString("") { "%02x".format(it) }
        }

        /**
         * Tier 1 session key: stable identifier that is unique per row in
         * a given file (TS §3.4).
         */
        fun tier1SessionKey(fileFingerprint: String, rowIndex: Int): String =
            sha256Hex("$fileFingerprint|$rowIndex".toByteArray(Charsets.UTF_8))
    }

    /**
     * Parse a Tier 1 CSV stream.
     *
     * @param inputStream      The CSV data to parse.
     * @param fileFingerprint  SHA-256 of the file content (pre-computed by caller).
     * @param importBatchId    The database ID of the [ImportBatchEntity] for this run.
     * @return A [ParseOutcome] with the produced entities and skip count.
     */
    fun parse(
        inputStream: InputStream,
        fileFingerprint: String,
        importBatchId: Long,
    ): ParseOutcome {
        val lines = inputStream.bufferedReader(Charsets.UTF_8).readLines()
        if (lines.isEmpty()) {
            return ParseOutcome.BadHeader("File is empty")
        }

        // Validate header (case-insensitive)
        val headerNormalized = lines[0].trim().lowercase()
        if (headerNormalized != EXPECTED_HEADER) {
            return ParseOutcome.BadHeader(
                "Unexpected header: \"${lines[0].trim()}\". Expected \"Title,Date\"."
            )
        }

        val records = mutableListOf<ViewingRecordEntity>()
        var skipped = 0
        var rowIndex = 0   // 0-based data row index (header excluded)

        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) continue   // skip blank lines silently

            val fields = parseCsvLine(line)
            if (fields.size < 2) {
                skipped++; continue
            }

            val rawTitle = fields[0].trim()
            val rawDate  = fields[1].trim()

            if (rawTitle.isBlank() || rawDate.isBlank()) {
                skipped++; continue
            }

            val viewDate = parseDate(rawDate)
            if (viewDate == null) {
                skipped++; continue
            }

            val sessionKey = tier1SessionKey(fileFingerprint, rowIndex)
            val parsed     = seriesParser.parse(rawTitle)
            val normTitle  = normalizer.normalize(rawTitle)

            records += ViewingRecordEntity(
                provider             = "Netflix",
                rawTitle             = rawTitle,
                displayTitle         = parsed.displayTitle,
                normalizedTitle      = normTitle,
                contentType          = parsed.contentType.name,
                seriesName           = parsed.seriesName,
                normalizedSeriesName = parsed.normalizedSeriesName,
                seasonLabel          = parsed.seasonLabel,
                seasonNumber         = parsed.seasonNumber,
                episodeTitle         = parsed.episodeTitle,
                viewDate             = viewDate,
                sourceTier           = 1,
                importId             = importBatchId,
                sessionKey           = sessionKey,
            )
            rowIndex++
        }

        return ParseOutcome.Ok(records = records, rowsSkipped = skipped)
    }

    // ─── CSV parsing ─────────────────────────────────────────────────────────

    /**
     * Split one CSV line into fields, respecting double-quoted fields that may
     * contain commas or embedded quotes (RFC 4180 subset).
     */
    internal fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val buf = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes  -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote ""
                        buf.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> {
                    fields += buf.toString(); buf.clear()
                }
                else -> buf.append(c)
            }
            i++
        }
        fields += buf.toString()
        return fields
    }

    // ─── Date parsing ─────────────────────────────────────────────────────────

    /**
     * Parse a date string using the Tier 1 format (M/D/YYYY) with ISO 8601
     * fallback (YYYY-MM-DD).  Returns null if neither format matches.
     */
    internal fun parseDate(raw: String): String? {
        // Try M/D/YYYY first
        runCatching {
            return LocalDate.parse(raw.trim(), DATE_FORMAT_MDY).toString()
        }
        // ISO fallback
        runCatching {
            LocalDate.parse(raw.trim(), DATE_FORMAT_ISO)
            return raw.trim()   // already ISO, return as-is after validation
        }
        return null
    }

    // ─── Result type ──────────────────────────────────────────────────────────

    sealed class ParseOutcome {
        data class Ok(
            val records: List<ViewingRecordEntity>,
            val rowsSkipped: Int,
        ) : ParseOutcome()

        data class BadHeader(val reason: String) : ParseOutcome()
    }
}
