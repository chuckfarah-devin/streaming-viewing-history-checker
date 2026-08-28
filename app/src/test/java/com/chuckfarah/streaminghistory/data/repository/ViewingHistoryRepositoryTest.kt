package com.chuckfarah.streaminghistory.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chuckfarah.streaminghistory.data.db.AppDatabase
import com.chuckfarah.streaminghistory.data.db.entity.ImportBatchEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordFts
import com.chuckfarah.streaminghistory.domain.import_.Tier1CsvParser
import com.chuckfarah.streaminghistory.domain.import_.Tier2CsvParser
import com.chuckfarah.streaminghistory.domain.import_.Tier2Reconciler
import com.chuckfarah.streaminghistory.domain.matching.SeriesParser
import com.chuckfarah.streaminghistory.domain.matching.TitleMatcher
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import com.chuckfarah.streaminghistory.domain.model.ViewingResult
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
class ViewingHistoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ViewingHistoryRepository
    private val normalizer = TitleNormalizer()
    private val seriesParser = SeriesParser(normalizer)
    private val titleMatcher by lazy { TitleMatcher(normalizer, db.viewingRecordDao()) }

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val profileRepository = ProfileRepository(ctx)
        profileRepository.setActiveProfile(null)

        repo = ViewingHistoryRepository(
            context            = ctx,
            viewingRecordDao   = db.viewingRecordDao(),
            importBatchDao     = db.importBatchDao(),
            tier1CsvParser     = Tier1CsvParser(normalizer, seriesParser),
            tier2CsvParser     = Tier2CsvParser(normalizer, seriesParser),
            tier2Reconciler    = Tier2Reconciler(db.viewingRecordDao()),
            titleMatcher       = titleMatcher,
            profileRepository  = profileRepository,
        )
    }

    @After fun teardown() { db.close() }

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
        rawTitle: String,
        viewDate: String = "2021-03-17",
        sessionKey: String = rawTitle + viewDate,
        batchId: Long = 1L,
        profileName: String? = null,
        durationMs: Long? = null,
        bookmarkMs: Long? = null,
        latestBookmarkMs: Long? = null,
    ): Long {
        val parsed = seriesParser.parse(rawTitle)
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
            profileName          = profileName,
            durationMs           = durationMs,
            bookmarkMs           = bookmarkMs,
            latestBookmarkMs     = latestBookmarkMs,
            sourceTier           = if (durationMs == null) 1 else 2,
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

    @Test fun `lookupByNormalizedTitle returns Watched with Tier 2 session and reached`() = runTest {
        insertBatch()
        insertRecord(
            rawTitle         = "The Irishman",
            viewDate         = "2021-03-17",
            sessionKey       = "sk1",
            durationMs       = 28 * 60_000L,
            bookmarkMs       = 29 * 60_000L,
            latestBookmarkMs = 30 * 60_000L,
        )

        val result = repo.lookupByNormalizedTitle(normalizer.normalize("The Irishman"))

        assertThat(result).isInstanceOf(ViewingResult.Watched::class.java)
        val watched = result as ViewingResult.Watched
        assertThat(watched.mostRecentDuration).isEqualTo(28 * 60_000L)
        assertThat(watched.reached).isEqualTo(30 * 60_000L)
    }

    @Test fun `reached prefers latestBookmarkMs and falls back to bookmarkMs`() = runTest {
        insertBatch()
        insertRecord(
            rawTitle         = "The Irishman",
            viewDate         = "2021-03-17",
            sessionKey       = "sk1",
            durationMs       = 10_000L,
            bookmarkMs       = 5_000L,
            latestBookmarkMs = null,
        )

        val result = repo.lookupByNormalizedTitle(normalizer.normalize("The Irishman")) as ViewingResult.Watched
        assertThat(result.reached).isEqualTo(5_000L)
    }

    @Test fun `reached omitted when no bookmark fields`() = runTest {
        insertBatch()
        insertRecord(
            rawTitle   = "The Irishman",
            viewDate   = "2021-03-17",
            sessionKey = "sk1",
            durationMs = 10_000L,
        )

        val result = repo.lookupByNormalizedTitle(normalizer.normalize("The Irishman")) as ViewingResult.Watched
        assertThat(result.reached).isNull()
    }

    @Test fun `lookupByNormalizedTitle returns NotWatched when title absent for profile`() = runTest {
        insertBatch()
        insertRecord(
            rawTitle    = "The Irishman",
            viewDate    = "2021-03-17",
            sessionKey  = "sk1",
            profileName = "Adult",
        )

        repo.setActiveProfile("Kids")
        val result = repo.lookupByNormalizedTitle(normalizer.normalize("The Irishman"))

        assertThat(result).isInstanceOf(ViewingResult.NotWatched::class.java)
    }

    @Test fun `repeated sessions are newest first and preserved`() = runTest {
        insertBatch()
        insertRecord("The Irishman", viewDate = "2021-03-15", sessionKey = "sk1")
        insertRecord("The Irishman", viewDate = "2021-03-17", sessionKey = "sk2")
        insertRecord("The Irishman", viewDate = "2021-03-16", sessionKey = "sk3")

        val result = repo.lookupByNormalizedTitle(normalizer.normalize("The Irishman")) as ViewingResult.Watched
        assertThat(result.sessions).hasSize(3)
        assertThat(result.sessions.map { it.viewDate }).containsExactly("2021-03-17", "2021-03-16", "2021-03-15").inOrder()
    }

    @Test fun `series result has three distinct statistics`() = runTest {
        insertBatch()
        insertRecord("Stranger Things: Season 1: Chapter One", viewDate = "2021-03-17", sessionKey = "sk1")
        insertRecord("Stranger Things: Season 1: Chapter One", viewDate = "2021-03-18", sessionKey = "sk2")
        insertRecord("Stranger Things: Season 1: Chapter Two", viewDate = "2021-03-19", sessionKey = "sk3")
        insertRecord("Stranger Things: Season 2: MADMAX", viewDate = "2021-03-20", sessionKey = "sk4")

        val result = repo.lookupByNormalizedTitle(normalizer.normalize("Stranger Things")) as ViewingResult.Watched
        val stats = requireNotNull(result.seriesStats)

        assertThat(stats.viewingOccurrences).isEqualTo(4)
        assertThat(stats.distinctEpisodes).isEqualTo(3)
        assertThat(stats.seasonsRepresented).isEqualTo(2)
    }

    @Test fun `series episodes are distinct and newest first`() = runTest {
        insertBatch()
        insertRecord("Stranger Things: Season 1: Chapter One", viewDate = "2021-03-17", sessionKey = "sk1")
        insertRecord("Stranger Things: Season 1: Chapter Two", viewDate = "2021-03-19", sessionKey = "sk2")
        insertRecord("Stranger Things: Season 2: MADMAX", viewDate = "2021-03-20", sessionKey = "sk3")

        val result = repo.lookupByNormalizedTitle(normalizer.normalize("Stranger Things")) as ViewingResult.Watched

        assertThat(result.episodes).hasSize(3)
        assertThat(result.episodes.first().mostRecentDate).isEqualTo("2021-03-20")
        assertThat(result.episodes.first().recordCount).isEqualTo(1)
    }

    @Test fun `reactive profile switching refreshes result without reimporting`() = runTest {
        insertBatch()
        insertRecord("The Irishman", viewDate = "2021-03-17", sessionKey = "sk1", profileName = "Adult")

        repo.setActiveProfile("Adult")
        val adult = repo.lookupByNormalizedTitle(normalizer.normalize("The Irishman"))
        assertThat(adult).isInstanceOf(ViewingResult.Watched::class.java)

        repo.setActiveProfile("Kids")
        val kids = repo.lookupByNormalizedTitle(normalizer.normalize("The Irishman"))
        assertThat(kids).isInstanceOf(ViewingResult.NotWatched::class.java)
    }
}
