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
        startTimeUtc: String? = null,
        sourceTier: Int? = null,
    ): Long {
        val parsed = seriesParser.parse(rawTitle)
        val tier = sourceTier ?: if (durationMs == null) 1 else 2
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
            startTimeUtc         = startTimeUtc,
            profileName          = profileName,
            durationMs           = durationMs,
            bookmarkMs           = bookmarkMs,
            latestBookmarkMs     = latestBookmarkMs,
            sourceTier           = tier,
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

    @Test fun `same title and date Tier 2 wins over Tier 1`() = runTest {
        insertBatch()
        // Tier 1 row for the same day
        insertRecord(
            "Extraction",
            viewDate   = "2023-06-21",
            sessionKey = "t1",
            profileName = null,
            sourceTier = 1,
        )
        // Reconciled Tier 2 row with precise session information
        insertRecord(
            "Extraction",
            viewDate   = "2023-06-21",
            sessionKey = "t2",
            profileName = "Chuck",
            durationMs = 11_000L,
            latestBookmarkMs = 7_000L,
            startTimeUtc = "2023-06-21T20:30:00Z",
            sourceTier = 2,
        )

        repo.setActiveProfile("Chuck")
        val result = repo.lookupByNormalizedTitle(normalizer.normalize("Extraction")) as ViewingResult.Watched

        assertThat(result.mostRecentDuration).isEqualTo(11_000L)
        assertThat(result.reached).isEqualTo(7_000L)
        assertThat(result.viewingOccurrences).isEqualTo(1)
        assertThat(result.profileName).isEqualTo("Chuck")
    }

    @Test fun `genuinely newer Tier 1 record remains most recent but uses older Tier 2 for duration`() = runTest {
        insertBatch()
        insertRecord(
            "Extraction",
            viewDate   = "2021-08-02",
            sessionKey = "t2",
            profileName = "Chuck",
            durationMs = 6_000L,
            bookmarkMs = 1 * 3_600_000L + 43 * 60_000L + 25_000L,
            startTimeUtc = "2021-08-02T19:00:00Z",
            sourceTier = 2,
        )
        insertRecord(
            "Extraction",
            viewDate   = "2023-06-21",
            sessionKey = "t1",
            profileName = null,
            sourceTier = 1,
        )

        repo.setActiveProfile("Chuck")
        val result = repo.lookupByNormalizedTitle(normalizer.normalize("Extraction")) as ViewingResult.Watched

        assertThat(result.mostRecentDate).isEqualTo("2023-06-21")
        assertThat(result.mostRecentDuration).isEqualTo(6_000L)
        assertThat(result.reached).isEqualTo(1 * 3_600_000L + 43 * 60_000L + 25_000L)
        assertThat(result.viewingOccurrences).isEqualTo(2)
    }

    @Test fun `repeated Tier 2 sessions remain visible`() = runTest {
        insertBatch()
        insertRecord(
            "Extraction",
            viewDate   = "2020-05-07",
            sessionKey = "t2a",
            profileName = "Chuck",
            durationMs = 45 * 60_000L + 3_000L,
            startTimeUtc = "2020-05-07T18:00:00Z",
            sourceTier = 2,
        )
        insertRecord(
            "Extraction",
            viewDate   = "2020-05-07",
            sessionKey = "t2b",
            profileName = "Chuck",
            durationMs = 1 * 3_600_000L + 39 * 60_000L + 41_000L,
            startTimeUtc = "2020-05-07T20:00:00Z",
            sourceTier = 2,
        )

        repo.setActiveProfile("Chuck")
        val result = repo.lookupByNormalizedTitle(normalizer.normalize("Extraction")) as ViewingResult.Watched

        assertThat(result.viewingOccurrences).isEqualTo(2)
        assertThat(result.sessions).hasSize(2)
        assertThat(result.sessions.map { it.durationMs }).containsExactly(1 * 3_600_000L + 39 * 60_000L + 41_000L, 45 * 60_000L + 3_000L).inOrder()
    }

    @Test fun `Tier 1 only result still omits duration and does not attribute active profile`() = runTest {
        insertBatch()
        insertRecord(
            "Extraction",
            viewDate   = "2021-08-02",
            sessionKey = "t1",
            profileName = null,
            sourceTier = 1,
        )

        repo.setActiveProfile("Chuck")
        val result = repo.lookupByNormalizedTitle(normalizer.normalize("Extraction")) as ViewingResult.Watched

        assertThat(result.mostRecentDuration).isNull()
        assertThat(result.reached).isNull()
        assertThat(result.profileName).isNull()
    }

    @Test fun `same-date ordering is deterministic across multiple lookups`() = runTest {
        insertBatch()
        insertRecord(
            "Extraction",
            viewDate   = "2023-06-21",
            sessionKey = "t1",
            profileName = null,
            sourceTier = 1,
        )
        insertRecord(
            "Extraction",
            viewDate   = "2023-06-21",
            sessionKey = "t2",
            profileName = "Chuck",
            durationMs = 11_000L,
            latestBookmarkMs = 7_000L,
            startTimeUtc = "2023-06-21T20:30:00Z",
            sourceTier = 2,
        )

        repo.setActiveProfile("Chuck")
        val first = repo.lookupByNormalizedTitle(normalizer.normalize("Extraction")) as ViewingResult.Watched
        val second = repo.lookupByNormalizedTitle(normalizer.normalize("Extraction")) as ViewingResult.Watched

        assertThat(first.mostRecentDuration).isEqualTo(second.mostRecentDuration)
        assertThat(first.sessions.map { it.durationMs }).isEqualTo(second.sessions.map { it.durationMs })
    }

    @Test fun `newer Tier 1 episode falls back to older Tier 2 episode for duration`() = runTest {
        insertBatch()
        // Older Tier 2 episode has timing.
        insertRecord(
            "Stranger Things: Season 1: Chapter One",
            viewDate     = "2021-03-17",
            sessionKey   = "t2a",
            profileName  = "Chuck",
            durationMs   = 10_000L,
            latestBookmarkMs = 8_000L,
            startTimeUtc = "2021-03-17T20:00:00Z",
            sourceTier   = 2,
        )
        // Newer Tier 1 episode has no timing.
        insertRecord(
            "Stranger Things: Season 1: Chapter Two",
            viewDate    = "2021-03-18",
            sessionKey  = "t1",
            profileName = null,
            sourceTier  = 1,
        )

        repo.setActiveProfile("Chuck")
        val result = repo.lookupByNormalizedTitle(normalizer.normalize("Stranger Things")) as ViewingResult.Watched

        assertThat(result.mostRecentDate).isEqualTo("2021-03-18")
        assertThat(result.mostRecentDuration).isEqualTo(10_000L)
        assertThat(result.reached).isEqualTo(8_000L)
        assertThat(result.viewingOccurrences).isEqualTo(2)
    }

    @Test fun `newer Tier 1 record with no Tier 2 at all still has null duration`() = runTest {
        insertBatch()
        insertRecord(
            "The Irishman",
            viewDate   = "2021-03-17",
            sessionKey = "t1",
            profileName = null,
            sourceTier = 1,
        )

        val result = repo.lookupByNormalizedTitle(normalizer.normalize("The Irishman")) as ViewingResult.Watched

        assertThat(result.mostRecentDuration).isNull()
        assertThat(result.reached).isNull()
    }
}
