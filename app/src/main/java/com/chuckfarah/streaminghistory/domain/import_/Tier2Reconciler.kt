package com.chuckfarah.streaminghistory.domain.import_

import com.chuckfarah.streaminghistory.data.db.dao.ViewingRecordDao
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordFts
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles a list of parsed Tier 2 rows against the existing database (TS §3.6).
 *
 * For each [Tier2Row] the algorithm:
 *  1. Skips the row if its session_key already exists (idempotency).
 *  2. Tries to upgrade an existing Tier 1 record:
 *     a. Exact view_date match (preferred).
 *     b. ±1-day adjacent-date fallback (handles UTC/local-time boundary).
 *  3. Inserts a new Tier 2 record if no Tier 1 candidate is available.
 *
 * To prevent two Tier 2 rows from both matching the same Tier 1 record, a set of
 * already-matched Tier 1 IDs is maintained across the whole batch.
 */
@Singleton
class Tier2Reconciler @Inject constructor(
    private val dao: ViewingRecordDao,
) {

    data class ReconciliationResult(
        val upgraded: Int,
        val inserted: Int,
        val skipped: Int,
    )

    /**
     * Process [rows] one at a time and return aggregate counts.
     *
     * @param rows        Parsed Tier 2 rows from [Tier2CsvParser].
     * @param importBatchId The id of the newly-created Tier 2 import batch.
     */
    suspend fun reconcile(rows: List<Tier2Row>, importBatchId: Long): ReconciliationResult {
        var upgraded = 0
        var inserted = 0
        var skipped  = 0

        // Track which Tier 1 record IDs have already been matched in this batch
        // so two Tier 2 rows cannot claim the same Tier 1 row (TS §3.6).
        val matchedTier1Ids = mutableSetOf<Long>()

        for (row in rows) {

            // ── 1. Idempotency: session already imported ──────────────────
            if (dao.existsBySessionKey(row.sessionKey)) {
                skipped++; continue
            }

            // ── 2a. Exact-date Tier 1 candidate search ───────────────────
            val exactCandidates = dao.getTier1ExactDate(
                normalizedTitle = row.normalizedTitle,
                viewDate        = row.viewDate,
                excludedIds     = matchedTier1Ids.toList().ifEmpty { listOf(-1L) },
            )

            // ── 2b. ±1-day adjacent-date fallback (TS §3.6) ──────────────
            val candidates = exactCandidates.ifEmpty {
                val date       = LocalDate.parse(row.viewDate)
                val dateBefore = date.minusDays(1).toString()
                val dateAfter  = date.plusDays(1).toString()
                dao.getTier1AdjacentDates(
                    normalizedTitle = row.normalizedTitle,
                    dateBefore      = dateBefore,
                    dateAfter       = dateAfter,
                    excludedIds     = matchedTier1Ids.toList().ifEmpty { listOf(-1L) },
                )
            }

            // ── 3. Upgrade or insert ──────────────────────────────────────
            val target = candidates.firstOrNull()
            if (target != null) {
                // Upgrade the Tier 1 record with all Tier 2 fields (TS §3.6).
                // The record's id, normalized_title, series fields, etc. are preserved;
                // only Tier-2-only columns and session metadata are updated.
                dao.upgradeToTier2(
                    id               = target.id,
                    startTimeUtc     = row.startTimeUtc,
                    durationMs       = row.durationMs,
                    bookmarkMs       = row.bookmarkMs,
                    latestBookmarkMs = row.latestBookmarkMs,
                    profileName      = row.profileName,
                    isAutoplayed     = row.isAutoplayed,
                    isHidden         = row.isHidden,
                    attributesRaw    = row.attributesRaw,
                    deviceType       = row.deviceType,
                    sessionKey       = row.sessionKey,
                    importId         = importBatchId,
                )
                matchedTier1Ids += target.id
                upgraded++
            } else {
                // No Tier 1 record to upgrade — insert as a fresh Tier 2 record.
                val entity = row.toEntity(importBatchId)
                val id     = dao.insert(entity)
                dao.insertFts(
                    ViewingRecordFts(
                        normalizedTitle      = entity.normalizedTitle,
                        normalizedSeriesName = entity.normalizedSeriesName ?: "",
                    )
                )
                inserted++
            }
        }

        return ReconciliationResult(upgraded = upgraded, inserted = inserted, skipped = skipped)
    }

    // ── Extension ─────────────────────────────────────────────────────────────

    private fun Tier2Row.toEntity(importBatchId: Long) = ViewingRecordEntity(
        provider             = provider,
        rawTitle             = rawTitle,
        displayTitle         = displayTitle,
        normalizedTitle      = normalizedTitle,
        contentType          = contentType,
        seriesName           = seriesName,
        normalizedSeriesName = normalizedSeriesName,
        seasonLabel          = seasonLabel,
        seasonNumber         = seasonNumber,
        episodeTitle         = episodeTitle,
        viewDate             = viewDate,
        startTimeUtc         = startTimeUtc,
        durationMs           = durationMs,
        bookmarkMs           = bookmarkMs,
        latestBookmarkMs     = latestBookmarkMs,
        profileName          = profileName,
        isHidden             = isHidden,
        isAutoplayed         = isAutoplayed,
        attributesRaw        = attributesRaw,
        deviceType           = deviceType,
        sourceTier           = 2,
        importId             = importBatchId,
        sessionKey           = sessionKey,
    )
}
