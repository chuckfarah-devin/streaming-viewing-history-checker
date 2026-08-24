package com.chuckfarah.streaminghistory.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chuckfarah.streaminghistory.data.db.AppDatabase
import com.chuckfarah.streaminghistory.data.db.entity.ImportBatchEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordFts
import com.chuckfarah.streaminghistory.domain.import_.Tier1CsvParser
import com.chuckfarah.streaminghistory.domain.matching.SeriesParser
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ViewingHistoryDatabaseTest {

    private lateinit var db: AppDatabase

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun teardown() { db.close() }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val normalizer = TitleNormalizer()

    private suspend fun insertBatch(fingerprint: String = "fp1"): Long =
        db.importBatchDao().insert(
            ImportBatchEntity(
                importedAt      = "2024-01-01T00:00:00Z",
                sourceTier      = 1,
                sourceFileName  = "NetflixViewingHistory.csv",
                fileFingerprint = fingerprint,
                recordCount     = 0,
            )
        )

    private suspend fun insertRecord(
        rawTitle:   String,
        viewDate:   String  = "2021-03-17",
        sessionKey: String  = rawTitle + viewDate,
        batchId:    Long    = 1L,
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
            importId             = batchId,
            sessionKey           = sessionKey,
        )
        val id = db.viewingRecordDao().insert(entity)
        db.viewingRecordDao().insertFts(
            ViewingRecordFts(
                normalizedTitle      = entity.normalizedTitle,
                normalizedSeriesName = entity.normalizedSeriesName ?: "",
            )
        )
        return id
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Test fun `inserted record is retrievable by exact normalized title`() = runTest {
        insertBatch()
        insertRecord("The Irishman")
        val results = db.viewingRecordDao().getByExactNormalizedTitle(
            normalizer.normalize("The Irishman")
        )
        assertThat(results).hasSize(1)
        assertThat(results[0].rawTitle).isEqualTo("The Irishman")
    }

    @Test fun `FTS search returns matching record`() = runTest {
        insertBatch()
        insertRecord("The Irishman")
        val results = db.viewingRecordDao().searchFts("irishman")
        assertThat(results).hasSize(1)
    }

    @Test fun `FTS search on unknown title returns empty list`() = runTest {
        insertBatch()
        insertRecord("The Irishman")
        val results = db.viewingRecordDao().searchFts("stranger")
        assertThat(results).isEmpty()
    }

    // ── Import idempotency ────────────────────────────────────────────────────

    @Test fun `fingerprint check detects already-imported file`() = runTest {
        insertBatch("fp_abc")
        assertThat(db.importBatchDao().existsByFingerprint("fp_abc")).isTrue()
        assertThat(db.importBatchDao().existsByFingerprint("fp_xyz")).isFalse()
    }

    @Test fun `importing same file twice does not create duplicate records`() = runTest {
        val batchId = insertBatch("fp_abc")
        insertRecord("The Irishman", batchId = batchId, sessionKey = "key1")

        // Simulate re-import: fingerprint already exists → no new records
        val alreadyImported = db.importBatchDao().existsByFingerprint("fp_abc")
        assertThat(alreadyImported).isTrue()
        // Count remains 1
        assertThat(db.viewingRecordDao().totalCount()).isEqualTo(1)
    }

    // ── Same-day repeat viewing preservation ──────────────────────────────────

    @Test fun `two identical Title+Date rows stored as two records with distinct session keys`() = runTest {
        insertBatch()
        insertRecord("The Irishman", viewDate = "2021-03-17", sessionKey = "fp1|0")
        insertRecord("The Irishman", viewDate = "2021-03-17", sessionKey = "fp1|1")

        val records = db.viewingRecordDao().getByExactNormalizedTitle(
            normalizer.normalize("The Irishman")
        )
        assertThat(records).hasSize(2)
        assertThat(records[0].sessionKey).isNotEqualTo(records[1].sessionKey)
    }

    // ── Clear history ─────────────────────────────────────────────────────────

    @Test fun `deleteAll removes all records and FTS`() = runTest {
        insertBatch()
        insertRecord("The Irishman", sessionKey = "k1")
        insertRecord("Stranger Things: Season 1: Chapter One", sessionKey = "k2")

        db.viewingRecordDao().deleteAll()
        db.viewingRecordDao().deleteAllFts()

        assertThat(db.viewingRecordDao().totalCount()).isEqualTo(0)
        assertThat(db.viewingRecordDao().searchFts("irishman")).isEmpty()
    }

    // ── Tier 1 replacement ────────────────────────────────────────────────────

    @Test fun `deleteAllTier1Records removes only Tier 1 rows`() = runTest {
        insertBatch("fp1")
        insertRecord("Movie One", sessionKey = "k1")   // source_tier=1

        // Simulate a Tier 2 record
        val tier2 = ViewingRecordEntity(
            provider        = "Netflix",
            rawTitle        = "Movie Two",
            displayTitle    = "Movie Two",
            normalizedTitle = normalizer.normalize("Movie Two"),
            contentType     = "UNKNOWN",
            viewDate        = "2022-01-01",
            sourceTier      = 2,
            importId        = 1L,
            sessionKey      = "tier2key",
        )
        db.viewingRecordDao().insert(tier2)

        db.viewingRecordDao().deleteAllTier1Records()

        val remaining = db.viewingRecordDao().totalCount()
        assertThat(remaining).isEqualTo(1)
        val records = db.viewingRecordDao().getByExactNormalizedTitle(
            normalizer.normalize("Movie Two")
        )
        assertThat(records[0].sourceTier).isEqualTo(2)
    }

    // ── Series statistics queries ─────────────────────────────────────────────

    @Test fun `getSeriesRecords returns only SERIES records for the given series`() = runTest {
        insertBatch()
        // 3 views of S1E01, 1 view of S1E02, 1 view of S2E01
        insertRecord("Stranger Things: Season 1: Chapter One", sessionKey = "sk1")
        insertRecord("Stranger Things: Season 1: Chapter One", sessionKey = "sk2")
        insertRecord("Stranger Things: Season 1: Chapter One", sessionKey = "sk3")
        insertRecord("Stranger Things: Season 1: Chapter Two", sessionKey = "sk4")
        insertRecord("Stranger Things: Season 2: MADMAX",      sessionKey = "sk5")

        val normSeriesName = normalizer.normalize("Stranger Things")
        val records = db.viewingRecordDao().getSeriesRecords(normSeriesName)

        assertThat(records).hasSize(5)

        val viewingOccurrences = records.size
        val distinctEpisodes   = records.map { it.episodeTitle ?: it.rawTitle }.distinct().size
        val seasonsRepresented = records.mapNotNull { it.seasonNumber }.distinct().size

        assertThat(viewingOccurrences).isEqualTo(5)
        assertThat(distinctEpisodes).isEqualTo(3)
        assertThat(seasonsRepresented).isEqualTo(2)
    }

    // ── Session key existence ─────────────────────────────────────────────────

    @Test fun `existsBySessionKey returns true for existing key`() = runTest {
        insertBatch()
        insertRecord("The Irishman", sessionKey = "unique_key_xyz")
        assertThat(db.viewingRecordDao().existsBySessionKey("unique_key_xyz")).isTrue()
        assertThat(db.viewingRecordDao().existsBySessionKey("nonexistent")).isFalse()
    }
}
