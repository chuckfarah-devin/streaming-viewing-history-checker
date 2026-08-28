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
import com.chuckfarah.streaminghistory.domain.import_.Tier2CsvParser
import com.chuckfarah.streaminghistory.domain.import_.Tier2Reconciler
import com.chuckfarah.streaminghistory.domain.matching.TitleMatcher
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.chuckfarah.streaminghistory.domain.model.EpisodeRecord
import com.chuckfarah.streaminghistory.domain.model.MatchResult
import com.chuckfarah.streaminghistory.domain.model.SeriesStats
import com.chuckfarah.streaminghistory.domain.model.ViewingResult
import com.chuckfarah.streaminghistory.domain.model.ViewingSession
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
    private val tier2CsvParser: Tier2CsvParser,
    private val tier2Reconciler: Tier2Reconciler,
    private val titleMatcher: TitleMatcher,
    private val profileRepository: ProfileRepository,
) {
    // ── Tier 1 import ─────────────────────────────────────────────────────────

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
                importedAt      = now,
                sourceTier      = 1,
                sourceFileName  = fileName,
                fileFingerprint = fingerprint,
                recordCount     = 0,   // updated after parsing
            )
        )

        // Parse
        val outcome = tier1CsvParser.parse(bytes.inputStream(), fingerprint, batchId)
        if (outcome is Tier1CsvParser.ParseOutcome.BadHeader) {
            // Roll back only the batch we just inserted (other batches must survive)
            importBatchDao.deleteById(batchId)
            return@withContext ImportResult.Failure(outcome.reason)
        }
        val parsed = outcome as Tier1CsvParser.ParseOutcome.Ok

        // Replace existing Tier 1 records (updated export) then insert new ones.
        viewingRecordDao.deleteAllTier1Records()
        viewingRecordDao.pruneOrphanedFtsRows()

        insertRecordsBatch(parsed.records)

        ImportResult.Success(
            recordsImported = parsed.records.size,
            rowsSkipped     = parsed.rowsSkipped,
            fileFingerprint = fingerprint,
        )
    }

    // ── Tier 2 import ─────────────────────────────────────────────────────────

    /**
     * Import a Netflix Tier 2 export from an Android SAF [Uri].
     *
     * Accepts:
     *  - A raw `ViewingActivity.csv` file
     *  - The full Netflix ZIP archive (any filename)
     *
     * The format is detected by MIME type or file extension; ZIP is tried first.
     *
     * Idempotency (TS §3.5 Tier 2):
     *  - Same file fingerprint → AlreadyImported.
     *  - Re-importing: already-processed session_keys are skipped row-by-row.
     *
     * Reconciliation (TS §3.6):
     *  - Each Tier 2 row is reconciled against existing Tier 1 records.
     *  - Matching Tier 1 records are upgraded in-place; unmatched rows are inserted.
     */
    suspend fun importTier2(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val fileName = uri.lastPathSegment ?: "ViewingActivity"

        // Read into memory for fingerprint + parse
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.readBytes()
                ?: return@withContext ImportResult.Failure("Cannot open file: $uri")
        } catch (e: Exception) {
            return@withContext ImportResult.Failure("Error reading file: ${e.message}")
        }

        val fingerprint = Tier1CsvParser.sha256Hex(bytes)

        // File-level idempotency
        if (importBatchDao.existsByFingerprint(fingerprint)) {
            return@withContext ImportResult.AlreadyImported(fingerprint)
        }

        // Determine if ZIP or CSV
        val isZip = mimeType.contains("zip", ignoreCase = true)
            || fileName.endsWith(".zip", ignoreCase = true)

        val parseOutcome = if (isZip) {
            tier2CsvParser.parseZip(bytes.inputStream())
        } else {
            tier2CsvParser.parseCsv(bytes.inputStream())
        }

        if (parseOutcome is Tier2CsvParser.ParseOutcome.BadHeader) {
            return@withContext ImportResult.Failure(parseOutcome.reason)
        }
        val parsed = parseOutcome as Tier2CsvParser.ParseOutcome.Ok

        // Create import batch
        val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC))
        val batchId = importBatchDao.insert(
            ImportBatchEntity(
                importedAt      = now,
                sourceTier      = 2,
                sourceFileName  = fileName,
                fileFingerprint = fingerprint,
                recordCount     = parsed.rows.size,
            )
        )

        // Reconcile: upgrade Tier 1 records or insert new Tier 2 records
        val result = tier2Reconciler.reconcile(parsed.rows, batchId)

        // Collect distinct profiles from the newly imported data
        val profiles = viewingRecordDao.getDistinctProfiles()

        ImportResult.Tier2Success(
            recordsUpgraded = result.upgraded,
            recordsInserted = result.inserted,
            rowsSkipped     = parsed.rowsSkipped + result.skipped,
            fileFingerprint = fingerprint,
            profiles        = profiles,
        )
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
                is MatchResult.None       -> ViewingResult.NotWatched(
                    displayTitle    = queryText,
                    normalizedTitle = queryText,
                )
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
     *
     * Profile filtering is applied here (TS §7.2): only records matching the
     * active profile (or with null profile_name) are counted.
     */
    private suspend fun resolveWatched(confident: MatchResult.Confident): ViewingResult {
        val profile = profileRepository.activeProfile
        val records = if (confident.contentType == ContentType.SERIES) {
            // Series lookup first; if nothing is found (e.g. a specific episode
            // title was used as the normalized lookup key), fall back to an
            // exact title lookup so the user still gets the matched record.
            viewingRecordDao.getSeriesRecords(confident.normalizedTitle, profile)
                .ifEmpty { viewingRecordDao.getByExactNormalizedTitle(confident.normalizedTitle, profile) }
        } else {
            viewingRecordDao.getByExactNormalizedTitle(confident.normalizedTitle, profile)
        }
        if (records.isEmpty()) return ViewingResult.NotWatched(
            displayTitle    = confident.displayTitle,
            normalizedTitle = confident.normalizedTitle,
        )

        val isSeries = confident.contentType == ContentType.SERIES

        return buildWatched(
            displayTitle       = confident.displayTitle,
            normalizedTitle    = confident.normalizedTitle,
            contentType        = confident.contentType,
            profile            = profile,
            records            = records,
            isSeries           = isSeries,
        )
    }

    /**
     * Resolve a specific title chosen from an ambiguous list.
     *
     * Profile filtering is applied (TS §7.2).
     */
    suspend fun lookupByNormalizedTitle(normalizedTitle: String): ViewingResult =
        withContext(Dispatchers.IO) {
            try {
                val profile = profileRepository.activeProfile

                // Try series lookup first (handles SERIES candidates from TitleMatcher)
                var records  = viewingRecordDao.getSeriesRecords(normalizedTitle, profile)
                var isSeries = records.isNotEmpty()

                // Fall back to exact title lookup (handles UNKNOWN / MOVIE)
                if (records.isEmpty()) {
                    records  = viewingRecordDao.getByExactNormalizedTitle(normalizedTitle, profile)
                    isSeries = false
                }

                val rep = records.firstOrNull()
                val contentType = if (rep != null) ContentType.valueOf(rep.contentType) else null

                val displayTitle = when {
                    rep == null -> normalizedTitle
                    isSeries  -> rep.displayTitle
                    else      -> rep.episodeTitle ?: rep.displayTitle
                }

                if (records.isEmpty()) return@withContext ViewingResult.NotWatched(
                    displayTitle    = displayTitle,
                    normalizedTitle = normalizedTitle,
                )

                buildWatched(
                    displayTitle    = displayTitle,
                    normalizedTitle = normalizedTitle,
                    contentType     = contentType ?: ContentType.valueOf(records.first().contentType),
                    profile         = profile,
                    records         = records,
                    isSeries        = isSeries,
                )
            } catch (e: Exception) {
                ViewingResult.Error("Lookup failed: ${e.message}")
            }
        }

    // ── Shared watched builder ────────────────────────────────────────────────

    private fun buildWatched(
        displayTitle: String,
        normalizedTitle: String,
        contentType: ContentType,
        profile: String?,
        records: List<ViewingRecordEntity>,
        isSeries: Boolean,
    ): ViewingResult.Watched {
        val sortedRecords = records.sortedByDescending { it.viewDate }
        val mostRecent = sortedRecords.first()

        return ViewingResult.Watched(
            displayTitle       = displayTitle,
            normalizedTitle    = normalizedTitle,
            contentType        = contentType,
            profileName        = profile,
            viewingOccurrences = records.size,
            mostRecentDate     = mostRecent.viewDate,
            allDates           = records.map { it.viewDate }.distinct().sortedDescending(),
            mostRecentDuration = mostRecent.durationMs,
            reached            = mostRecent.latestBookmarkMs ?: mostRecent.bookmarkMs,
            sessions           = sortedRecords.map { it.toViewingSession() },
            seriesStats        = if (isSeries) buildSeriesStats(records) else null,
            episodes           = if (isSeries) buildEpisodeList(records) else emptyList(),
        )
    }

    // ── Episode and series helpers ────────────────────────────────────────────

    private fun buildEpisodeList(records: List<ViewingRecordEntity>): List<EpisodeRecord> =
        records
            .groupBy { EpisodeKey(it.seasonNumber, it.seasonLabel, it.episodeTitle ?: it.rawTitle) }
            .values
            .map { group ->
                val sorted = group.sortedByDescending { it.viewDate }
                val mostRecent = sorted.first()
                EpisodeRecord(
                    rawTitle     = mostRecent.rawTitle,
                    seasonLabel  = mostRecent.seasonLabel,
                    seasonNumber = mostRecent.seasonNumber,
                    episodeTitle = mostRecent.episodeTitle,
                    mostRecentDate = mostRecent.viewDate,
                    recordCount  = sorted.size,
                    records      = sorted.map { it.toViewingSession() },
                )
            }
            .sortedByDescending { it.mostRecentDate }

    private data class EpisodeKey(
        val seasonNumber: Int?,
        val seasonLabel: String?,
        val title: String,
    )

    private fun buildSeriesStats(records: List<ViewingRecordEntity>): SeriesStats? {
        if (records.isEmpty()) return null
        val rep = records.first()
        return SeriesStats(
            seriesName           = rep.seriesName ?: rep.displayTitle,
            normalizedSeriesName = rep.normalizedSeriesName ?: rep.normalizedTitle,
            viewingOccurrences   = records.size,
            distinctEpisodes     = records.map { it.episodeTitle ?: it.rawTitle }.distinct().size,
            seasonsRepresented   = records.mapNotNull { it.seasonNumber }.distinct().size,
            mostRecentDate       = records.maxOf { it.viewDate },
        )
    }

    private fun ViewingRecordEntity.toViewingSession(): ViewingSession =
        ViewingSession(
            rawTitle    = rawTitle,
            viewDate    = viewDate,
            durationMs  = durationMs,
            reachedMs   = latestBookmarkMs ?: bookmarkMs,
            profileName = profileName,
        )

    // ── Matching ──────────────────────────────────────────────────────────────

    suspend fun getMatchResult(queryText: String): MatchResult =
        titleMatcher.match(queryText)

    // ── Profile management ────────────────────────────────────────────────────

    /** Active profile name, or null if none is set. */
    val activeProfileFlow get() = profileRepository.activeProfileFlow

    fun setActiveProfile(profile: String?) = profileRepository.setActiveProfile(profile)

    /** Fetch all distinct profiles stored in the database. */
    suspend fun getAvailableProfiles(): List<String> =
        withContext(Dispatchers.IO) { viewingRecordDao.getDistinctProfiles() }

    // ── Utility ───────────────────────────────────────────────────────────────

    suspend fun getTotalRecordCount(): Int =
        withContext(Dispatchers.IO) { viewingRecordDao.totalCount() }

    /**
     * Returns the [limit] most recently viewed records, used on the Home screen.
     * Respects the active profile filter.
     */
    suspend fun getRecentViewings(limit: Int = 10): List<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            viewingRecordDao.getRecentViewings(limit).map { rec ->
                rec.displayTitle to rec.viewDate
            }
        }

    suspend fun clearAllHistory(): Unit = withContext(Dispatchers.IO) {
        viewingRecordDao.deleteAll()
        viewingRecordDao.deleteAllFts()
        importBatchDao.deleteAll()
        profileRepository.clearActiveProfile()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun insertRecordsBatch(records: List<ViewingRecordEntity>) {
        for (record in records) {
            viewingRecordDao.insert(record)
            viewingRecordDao.insertFts(
                ViewingRecordFts(
                    normalizedTitle      = record.normalizedTitle,
                    normalizedSeriesName = record.normalizedSeriesName ?: "",
                )
            )
        }
    }
}
