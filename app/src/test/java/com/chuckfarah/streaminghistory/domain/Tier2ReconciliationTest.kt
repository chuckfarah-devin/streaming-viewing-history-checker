package com.chuckfarah.streaminghistory.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chuckfarah.streaminghistory.data.db.AppDatabase
import com.chuckfarah.streaminghistory.data.db.entity.ImportBatchEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordFts
import com.chuckfarah.streaminghistory.domain.import_.Tier2CsvParser
import com.chuckfarah.streaminghistory.domain.import_.Tier2Reconciler
import com.chuckfarah.streaminghistory.domain.import_.Tier2Row
import com.chuckfarah.streaminghistory.domain.matching.SeriesParser
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for Tier 2 reconciliation — TS §§3.5 (Tier 2), 3.6, 7.2.
 *
 * Each test drives a real in-memory Room database so that DAO queries (including
 * the NOT IN exclusion list and the ±1-day fallback) run against actual SQLite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class Tier2ReconciliationTest {

    private lateinit var db: AppDatabase
    private lateinit var reconciler: Tier2Reconciler
    private val normalizer = TitleNormalizer()

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        reconciler = Tier2Reconciler(db.viewingRecordDao())
    }

    @After fun teardown() { db.close() }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun tier2Batch(): Long =
        db.importBatchDao().insert(
            ImportBatchEntity(
                importedAt      = "2024-01-02T00:00:00Z",
                sourceTier      = 2,
                sourceFileName  = "ViewingActivity.csv",
                fileFingerprint = "fp_t2",
                recordCount     = 0,
            )
        )

    private suspend fun insertTier1Record(
        rawTitle:   String,
        viewDate:   String,
        sessionKey: String,
    ): Long {
        val parser = SeriesParser(normalizer)
        val parsed = parser.parse(rawTitle)
        val entity = ViewingRecordEntity(
            provider             = "Netflix",
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
            sourceTier           = 1,
            importId             = 1L,
            sessionKey           = sessionKey,
        )
        val id = db.viewingRecordDao().insert(entity)
        db.viewingRecordDao().insertFts(
            ViewingRecordFts(entity.normalizedTitle, entity.normalizedSeriesName ?: "")
        )
        return id
    }

    private fun tier2Row(
        rawTitle:     String,
        startTimeUtc: String,  // "YYYY-MM-DD HH:MM:SS"
        profileName:  String?  = "Chuck",
        durationMs:   Long?    = 7_200_000L,
    ): Tier2Row {
        val parser     = SeriesParser(normalizer)
        val parsed     = parser.parse(rawTitle)
        val sessionKey = Tier2CsvParser.tier2SessionKey(
            "Netflix", profileName, startTimeUtc, rawTitle
        )
        return Tier2Row(
            provider             = "Netflix",
            rawTitle             = rawTitle,
            displayTitle         = parsed.displayTitle,
            normalizedTitle      = normalizer.normalize(rawTitle),
            contentType          = parsed.contentType.name,
            seriesName           = parsed.seriesName,
            normalizedSeriesName = parsed.normalizedSeriesName,
            seasonLabel          = parsed.seasonLabel,
            seasonNumber         = parsed.seasonNumber,
            episodeTitle         = parsed.episodeTitle,
            viewDate             = startTimeUtc.substring(0, 10),
            startTimeUtc         = startTimeUtc,
            durationMs           = durationMs,
            bookmarkMs           = null,
            latestBookmarkMs     = null,
            profileName          = profileName,
            isHidden             = 0,
            isAutoplayed         = 0,
            attributesRaw        = null,
            deviceType           = "Smart TV",
            sessionKey           = sessionKey,
        )
    }

    // ── Tier 2 idempotency (TS §3.5) ─────────────────────────────────────────

    @Test fun `importing same Tier 2 row twice skips it the second time`() = runTest {
        val batchId = tier2Batch()
        val row     = tier2Row("The Irishman", "2021-03-17 22:00:00")

        val first  = reconciler.reconcile(listOf(row), batchId)
        val second = reconciler.reconcile(listOf(row), batchId)

        assertThat(first.inserted).isEqualTo(1)
        assertThat(second.skipped).isEqualTo(1)
        assertThat(second.inserted).isEqualTo(0)
        assertThat(db.viewingRecordDao().totalCount()).isEqualTo(1)
    }

    // ── Upgrade: Tier 1 → Tier 2 (TS §3.6) ──────────────────────────────────

    @Test fun `Tier 2 row upgrades matching Tier 1 record in place`() = runTest {
        val tier1Id = insertTier1Record(
            rawTitle   = "The Irishman",
            viewDate   = "2021-03-17",
            sessionKey = "t1key",
        )
        val batchId = tier2Batch()
        val row     = tier2Row("The Irishman", "2021-03-17 22:00:00")

        val result = reconciler.reconcile(listOf(row), batchId)

        assertThat(result.upgraded).isEqualTo(1)
        assertThat(result.inserted).isEqualTo(0)
        // Record count stays the same (upgrade not insert)
        assertThat(db.viewingRecordDao().totalCount()).isEqualTo(1)

        // Verify upgrade: source_tier = 2, Tier 2 fields populated
        val upgraded = db.viewingRecordDao().getBySessionKey(row.sessionKey)!!
        assertThat(upgraded.sourceTier).isEqualTo(2)
        assertThat(upgraded.startTimeUtc).isEqualTo("2021-03-17 22:00:00")
        assertThat(upgraded.profileName).isEqualTo("Chuck")
        assertThat(upgraded.durationMs).isEqualTo(7_200_000L)
        // The Tier 1 id is reused (record updated, not re-inserted)
        assertThat(upgraded.id).isEqualTo(tier1Id)
    }

    @Test fun `Tier 2 upgrade never reverts a Tier 2 record to Tier 1`() = runTest {
        // Insert an already-upgraded (Tier 2) record
        val batchId = tier2Batch()
        val row     = tier2Row("The Irishman", "2021-03-17 22:00:00")
        reconciler.reconcile(listOf(row), batchId)

        // A subsequent Tier 1 import cannot downgrade it
        val tier2Record = db.viewingRecordDao().getBySessionKey(row.sessionKey)!!
        assertThat(tier2Record.sourceTier).isEqualTo(2)
    }

    // ── Insert path: no matching Tier 1 ──────────────────────────────────────

    @Test fun `Tier 2 row with no matching Tier 1 is inserted as new record`() = runTest {
        val batchId = tier2Batch()
        val row     = tier2Row("The Irishman", "2021-03-17 22:00:00")

        val result = reconciler.reconcile(listOf(row), batchId)

        assertThat(result.inserted).isEqualTo(1)
        assertThat(result.upgraded).isEqualTo(0)
        val stored = db.viewingRecordDao().getBySessionKey(row.sessionKey)!!
        assertThat(stored.sourceTier).isEqualTo(2)
        assertThat(stored.startTimeUtc).isEqualTo("2021-03-17 22:00:00")
    }

    // ── Same-day repeat viewings (TS §3.6 example) ───────────────────────────

    @Test fun `two Tier 1 records upgraded by two Tier 2 sessions, viewingOccurrences = 2`() = runTest {
        // Tier 1: two records on 2021-03-17
        insertTier1Record("The Irishman", "2021-03-17", "t1_key0")
        insertTier1Record("The Irishman", "2021-03-17", "t1_key1")

        val batchId = tier2Batch()
        // Session A: UTC date = 2021-03-17 (exact match)
        val rowA = tier2Row("The Irishman", "2021-03-17 22:00:00")
        // Session B: UTC date = 2021-03-18 (UTC midnight boundary → adjacent match)
        val rowB = tier2Row("The Irishman", "2021-03-18 01:30:00",
            profileName = "Chuck",
            durationMs  = 7_200_000L,
        ).copy(sessionKey = Tier2CsvParser.tier2SessionKey(
            "Netflix", "Chuck", "2021-03-18 01:30:00", "The Irishman"
        ))

        val result = reconciler.reconcile(listOf(rowA, rowB), batchId)

        assertThat(result.upgraded).isEqualTo(2)
        assertThat(result.inserted).isEqualTo(0)
        assertThat(db.viewingRecordDao().totalCount()).isEqualTo(2)

        // Both records now have source_tier = 2; one has the March 17 start, one March 18
        val allRecords = db.viewingRecordDao().getByExactNormalizedTitle(
            normalizer.normalize("The Irishman")
        )
        assertThat(allRecords).hasSize(2)
        val utcTimes = allRecords.map { it.startTimeUtc }.toSet()
        assertThat(utcTimes).containsExactly("2021-03-17 22:00:00", "2021-03-18 01:30:00")
    }

    @Test fun `two Tier 2 rows cannot both match the same Tier 1 record`() = runTest {
        // Only ONE Tier 1 record
        insertTier1Record("The Irishman", "2021-03-17", "t1_only")

        val batchId = tier2Batch()
        val rowA = tier2Row("The Irishman", "2021-03-17 22:00:00")
        val rowB = tier2Row("The Irishman", "2021-03-17 23:00:00",
            profileName = "Chuck",
        ).copy(sessionKey = Tier2CsvParser.tier2SessionKey(
            "Netflix", "Chuck", "2021-03-17 23:00:00", "The Irishman"
        ))

        val result = reconciler.reconcile(listOf(rowA, rowB), batchId)

        // Row A upgrades the single Tier 1 record; Row B must insert as new
        assertThat(result.upgraded).isEqualTo(1)
        assertThat(result.inserted).isEqualTo(1)
        assertThat(db.viewingRecordDao().totalCount()).isEqualTo(2)
    }

    // ── ±1-day adjacent-date fallback (TS §3.6) ───────────────────────────────

    @Test fun `UTC midnight boundary: Tier 2 on D+1 upgrades Tier 1 record on D`() = runTest {
        // Tier 1 has date 2021-03-17 (local time), Tier 2 starts at 01:30 UTC on 2021-03-18
        insertTier1Record("The Irishman", "2021-03-17", "t1_boundary")

        val batchId = tier2Batch()
        // t2_date extracted from UTC start = 2021-03-18 (next day)
        val row = tier2Row("The Irishman", "2021-03-18 01:30:00")

        val result = reconciler.reconcile(listOf(row), batchId)

        assertThat(result.upgraded).isEqualTo(1)   // adjacent-date fallback matched
        assertThat(result.inserted).isEqualTo(0)
        assertThat(db.viewingRecordDao().totalCount()).isEqualTo(1)
    }

    @Test fun `exact-date match is preferred over adjacent-date match`() = runTest {
        // Two Tier 1 records: one on 2021-03-17 (exact), one on 2021-03-16 (adjacent)
        insertTier1Record("The Irishman", "2021-03-17", "t1_exact")
        insertTier1Record("The Irishman", "2021-03-16", "t1_adjacent")

        val batchId = tier2Batch()
        // Tier 2 UTC date = 2021-03-17 (exact match available)
        val row = tier2Row("The Irishman", "2021-03-17 12:00:00")

        reconciler.reconcile(listOf(row), batchId)

        // The 2021-03-17 record (exact match) should have been upgraded
        val records = db.viewingRecordDao().getByExactNormalizedTitle(
            normalizer.normalize("The Irishman")
        )
        val upgraded = records.first { it.sourceTier == 2 }
        assertThat(upgraded.viewDate).isEqualTo("2021-03-17")
        // The 2021-03-16 record stays as Tier 1
        val remaining = records.first { it.sourceTier == 1 }
        assertThat(remaining.viewDate).isEqualTo("2021-03-16")
    }

    // ── Profile detection (TS §7.2) ───────────────────────────────────────────

    @Test fun `distinct profiles are recorded in database`() = runTest {
        val batchId = tier2Batch()
        val rowChuck = tier2Row("The Irishman",   "2021-03-17 22:00:00", profileName = "Chuck")
        val rowSarah = tier2Row("The Crown: Season 4: Favourites",
                                "2020-11-01 20:00:00", profileName = "Sarah")

        reconciler.reconcile(listOf(rowChuck, rowSarah), batchId)

        val profiles = db.viewingRecordDao().getDistinctProfiles()
        assertThat(profiles).containsExactly("Chuck", "Sarah")
    }

    @Test fun `profile filter returns only matching profile records`() = runTest {
        val batchId = tier2Batch()
        val rowChuck = tier2Row("The Irishman",   "2021-03-17 22:00:00", profileName = "Chuck")
        val rowSarah = tier2Row("The Irishman",   "2021-03-18 20:00:00", profileName = "Sarah")

        reconciler.reconcile(listOf(rowChuck, rowSarah), batchId)

        // Unfiltered: both records
        val all     = db.viewingRecordDao().getByExactNormalizedTitle(
            normalizer.normalize("The Irishman"), profile = null
        )
        assertThat(all).hasSize(2)

        // Filtered to Chuck: one record
        val chucks  = db.viewingRecordDao().getByExactNormalizedTitle(
            normalizer.normalize("The Irishman"), profile = "Chuck"
        )
        assertThat(chucks).hasSize(1)
        assertThat(chucks[0].profileName).isEqualTo("Chuck")
    }

    @Test fun `profile filter includes null-profile (Tier 1) records`() = runTest {
        // Tier 1 record (no profile_name)
        insertTier1Record("The Irishman", "2021-03-17", "t1_no_profile")

        // Tier 2 record for a different profile
        val batchId  = tier2Batch()
        val rowSarah = tier2Row("The Irishman", "2021-03-18 20:00:00", profileName = "Sarah")
        reconciler.reconcile(listOf(rowSarah), batchId)

        // Filtering to "Chuck" should still include the null-profile Tier 1 record
        val chucksView = db.viewingRecordDao().getByExactNormalizedTitle(
            normalizer.normalize("The Irishman"), profile = "Chuck"
        )
        assertThat(chucksView).hasSize(1)
        assertThat(chucksView[0].sourceTier).isEqualTo(1)
        assertThat(chucksView[0].profileName).isNull()
    }

    // ── Tier 2 FTS search for newly inserted records ──────────────────────────

    @Test fun `newly inserted Tier 2 record is findable via FTS`() = runTest {
        val batchId = tier2Batch()
        reconciler.reconcile(
            listOf(tier2Row("The Irishman", "2021-03-17 22:00:00")),
            batchId
        )

        val ftsResults = db.viewingRecordDao().searchFts("irishman")
        assertThat(ftsResults).hasSize(1)
        assertThat(ftsResults[0].rawTitle).isEqualTo("The Irishman")
    }

    @Test fun `series episode inserted via Tier 2 is findable by series name in FTS`() = runTest {
        val batchId = tier2Batch()
        reconciler.reconcile(
            listOf(tier2Row(
                "Stranger Things: Season 1: Chapter One",
                "2022-07-04 12:00:00"
            )),
            batchId
        )

        val ftsResults = db.viewingRecordDao().searchFts("stranger*")
        assertThat(ftsResults).hasSize(1)
        assertThat(ftsResults[0].seriesName).isEqualTo("Stranger Things")
        assertThat(ftsResults[0].contentType).isEqualTo(ContentType.SERIES.name)
    }
}
