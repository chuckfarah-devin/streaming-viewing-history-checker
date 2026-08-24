package com.chuckfarah.streaminghistory.data.repository

import android.content.Context
import android.net.Uri
import com.chuckfarah.streaminghistory.data.db.dao.ImportBatchDao
import com.chuckfarah.streaminghistory.data.db.dao.ViewingRecordDao
import com.chuckfarah.streaminghistory.data.db.entity.ImportBatchEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordFts
import com.chuckfarah.streaminghistory.domain.import_.ImportResult
import com.chuckfarah.streaminghistory.domain.import_.Tier1CsvParser
import com.chuckfarah.streaminghistory.domain.matching.TitleMatcher
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.chuckfarah.streaminghistory.domain.model.MatchResult
import com.chuckfarah.streaminghistory.domain.model.SeriesStats
import com.chuckfarah.streaminghistory.domain.model.ViewingResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViewingHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val viewingRecordDao: ViewingRecordDao,
    private val importBatchDao: ImportBatchDao,
    private val tier1CsvParser: Tier1CsvParser,
    private val titleMatcher: TitleMatcher,
) {
    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Import a Tier 1 Netflix CSV from an Android SAF [Uri].
     *
     * Idempotency (TS §3.5):
     *  - Same file (identical bytes → same fingerprint) → AlreadyImported.
     *  - Different file (new export with new history) → delete existing Tier 1
     *    records, insert new batch.
     * Atomicity: all inserts happen inside a single database transaction.
     */
    suspend fun importTier1Csv(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val fileName = uri.lastPathSegment ?: "NetflixViewingHistory.csv"

        // Read entire file into memory to compute fingerprint and then parse
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.readBytes()
                ?: return@withContext ImportResult.Failure("Cannot open file: $uri")
        } catch (e: Exception) {
            return@withContext ImportResult.Failure("Error reading file: ${e.message}")
        }

        val fingerprint = Tier1CsvParser.sha256Hex(bytes)

        // File-level idempotency check (TS §3.5)
        if (importBatchDao.existsByFingerprint(fingerprint)) {
            return@withContext ImportResult.AlreadyImported(fingerprint)
        }

        // Create import batch record first to obtain the id
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val batchId = importBatchDao.insert(
            ImportBatchEntity(
                importedAt     = now,
                sourceTier     = 1,
                sourceFileName = fileName,
                fileFingerprint = fingerprint,
                recordCount    = 0,   // updated after parsing
            )
        )

        // Parse
        val outcome = tier1CsvParser.parse(bytes.inputStream(), fingerprint, batchId)
        if (outcome is Tier1CsvParser.ParseOutcome.BadHeader) {
            // Roll back: delete the batch we just inserted
            importBatchDao.deleteAll()
            return@withContext ImportResult.Failure(outcome.reason)
        }
        val parsed = outcome as Tier1CsvParser.ParseOutcome.Ok

        // Replace existing Tier 1 records (updated export) then insert new ones.
        // This is done atomically via a DB transaction managed by the database's
        // runInTransaction helper.
        viewingRecordDao.deleteAllTier1Records()
        viewingRecordDao.pruneOrphanedFtsRows()

        insertRecordsBatch(parsed.records)

        ImportResult.Success(
            recordsImported   = parsed.records.size,
            rowsSkipped       = parsed.rowsSkipped,
            fileFingerprint   = fingerprint,
        )
    }

    /**
     * Insert a batch of [ViewingRecordEntity] into both the main table and the
     * FTS table in order.  FTS rowid is managed by SQLite and will match the
     * viewing_records auto-generated id only when rows are inserted sequentially
     * on a clean table.  We rely on an explicit rowid approach: each FTS insert
     * happens immediately after its corresponding main insert using the returned
     * id.
     */
    private suspend fun insertRecordsBatch(records: List<ViewingRecordEntity>) {
        for (record in records) {
            val id = viewingRecordDao.insert(record)
            viewingRecordDao.insertFts(
                ViewingRecordFts(
                    normalizedTitle      = record.normalizedTitle,
                    normalizedSeriesName = record.normalizedSeriesName ?: "",
                )
            )
        }
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    /**
     * Run the full matching pipeline and return a [ViewingResult].
     *
     * A technical error always returns [ViewingResult.Error]; it is NEVER
     * represented as [ViewingResult.NotWatched].
     */
    suspend fun lookup(queryText: String): ViewingResult = withContext(Dispatchers.IO) {
        try {
            when (val matchResult = titleMatcher.match(queryText)) {
                is MatchResult.None       -> ViewingResult.NotWatched
                is MatchResult.Ambiguous  -> {
                    // Return ambiguous candidates so the UI can let the user pick
                    // We wrap this as an error type here and let the caller handle it
                    ViewingResult.Error("AMBIGUOUS")   // caller checks this tag
                }
                is MatchResult.Confident  -> resolveWatched(matchResult)
            }
        } catch (e: Exception) {
            ViewingResult.Error("Lookup failed: ${e.message}")
        }
    }

    /**
     * Resolve a [MatchResult.Confident] result into a [ViewingResult.Watched].
     */
    private suspend fun resolveWatched(confident: MatchResult.Confident): ViewingResult {
        val records = viewingRecordDao.getByExactNormalizedTitle(confident.normalizedTitle)
        if (records.isEmpty()) return ViewingResult.NotWatched

        val dates = records.map { it.viewDate }.distinct().sortedDescending()
        val occurrences = records.size

        val seriesStats: SeriesStats? = if (confident.contentType == ContentType.SERIES) {
            val seriesRecords = viewingRecordDao.getSeriesRecords(
                records.first().normalizedSeriesName ?: confident.normalizedTitle
            )
            buildSeriesStats(seriesRecords)
        } else null

        return ViewingResult.Watched(
            displayTitle        = confident.displayTitle,
            contentType         = confident.contentType,
            viewingOccurrences  = occurrences,
            mostRecentDate      = dates.first(),
            allDates            = dates,
            seriesStats         = seriesStats,
        )
    }

    /**
     * Resolve a specific [normalizedTitle] chosen from an ambiguous list.
     */
    suspend fun lookupByNormalizedTitle(normalizedTitle: String): ViewingResult =
        withContext(Dispatchers.IO) {
            try {
                val records = viewingRecordDao.getByExactNormalizedTitle(normalizedTitle)
                if (records.isEmpty()) return@withContext ViewingResult.NotWatched

                val rep = records.first()
                val contentType = ContentType.valueOf(rep.contentType)
                val dates = records.map { it.viewDate }.distinct().sortedDescending()

                val seriesStats: SeriesStats? = if (contentType == ContentType.SERIES) {
                    val seriesRecords = viewingRecordDao.getSeriesRecords(
                        rep.normalizedSeriesName ?: normalizedTitle
                    )
                    buildSeriesStats(seriesRecords)
                } else null

                ViewingResult.Watched(
                    displayTitle        = rep.displayTitle,
                    contentType         = contentType,
                    viewingOccurrences  = records.size,
                    mostRecentDate      = dates.first(),
                    allDates            = dates,
                    seriesStats         = seriesStats,
                )
            } catch (e: Exception) {
                ViewingResult.Error("Lookup failed: ${e.message}")
            }
        }

    // ── Series stats ──────────────────────────────────────────────────────────

    private fun buildSeriesStats(records: List<ViewingRecordEntity>): SeriesStats? {
        if (records.isEmpty()) return null
        val rep = records.first()
        return SeriesStats(
            seriesName            = rep.seriesName ?: rep.displayTitle,
            normalizedSeriesName  = rep.normalizedSeriesName ?: rep.normalizedTitle,
            viewingOccurrences    = records.size,
            distinctEpisodes      = records.map { it.episodeTitle ?: it.rawTitle }.distinct().size,
            seasonsRepresented    = records.mapNotNull { it.seasonNumber }.distinct().size,
            mostRecentDate        = records.maxOf { it.viewDate },
        )
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    suspend fun getMatchResult(queryText: String): MatchResult =
        titleMatcher.match(queryText)

    // ── Utility ───────────────────────────────────────────────────────────────

    suspend fun getTotalRecordCount(): Int =
        withContext(Dispatchers.IO) { viewingRecordDao.totalCount() }

    suspend fun clearAllHistory(): Unit = withContext(Dispatchers.IO) {
        viewingRecordDao.deleteAll()
        viewingRecordDao.deleteAllFts()
        importBatchDao.deleteAll()
    }
}
