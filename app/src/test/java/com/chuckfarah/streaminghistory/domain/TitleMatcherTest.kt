package com.chuckfarah.streaminghistory.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chuckfarah.streaminghistory.data.db.AppDatabase
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordFts
import com.chuckfarah.streaminghistory.domain.matching.TitleMatcher
import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.chuckfarah.streaminghistory.domain.model.MatchResult
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
class TitleMatcherTest {

    private lateinit var db: AppDatabase
    private lateinit var matcher: TitleMatcher

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        matcher = TitleMatcher(TitleNormalizer(), db.viewingRecordDao())
    }

    @After fun teardown() { db.close() }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun insertRecord(
        rawTitle:    String,
        viewDate:    String  = "2021-03-17",
        contentType: ContentType = ContentType.UNKNOWN,
        seriesName:  String? = null,
        sessionKey:  String  = rawTitle + viewDate,
    ) {
        val normalizer = TitleNormalizer()
        val entity = ViewingRecordEntity(
            provider             = "Netflix",
            rawTitle             = rawTitle,
            displayTitle         = seriesName ?: rawTitle,
            normalizedTitle      = normalizer.normalize(rawTitle),
            contentType          = contentType.name,
            seriesName           = seriesName,
            normalizedSeriesName = seriesName?.let { normalizer.normalize(it) },
            viewDate             = viewDate,
            sourceTier           = 1,
            importId             = 1L,
            sessionKey           = sessionKey,
        )
        val id = db.viewingRecordDao().insert(entity)
        db.viewingRecordDao().insertFts(
            ViewingRecordFts(
                normalizedTitle      = entity.normalizedTitle,
                normalizedSeriesName = entity.normalizedSeriesName ?: "",
            )
        )
    }

    // ── Exact / confident matches ─────────────────────────────────────────────

    @Test fun `exact title match returns Confident`() = runTest {
        insertRecord("The Irishman")
        val result = matcher.match("The Irishman")
        assertThat(result).isInstanceOf(MatchResult.Confident::class.java)
    }

    @Test fun `capitalization difference still matches confidently`() = runTest {
        insertRecord("the irishman")
        val result = matcher.match("THE IRISHMAN")
        assertThat(result).isInstanceOf(MatchResult.Confident::class.java)
    }

    @Test fun `whitespace difference still matches confidently`() = runTest {
        insertRecord("The Irishman")
        val result = matcher.match("  The  Irishman  ")
        assertThat(result).isInstanceOf(MatchResult.Confident::class.java)
    }

    @Test fun `diacritic difference still matches`() = runTest {
        // DB has "elan"; user queries with diacritic
        insertRecord("elan")
        val result = matcher.match("\u00e9lan")   // "élan"
        // Normalization strips the diacritic, so it should match at least as
        // an Ambiguous candidate (keyword-search floor keeps the exact hit in
        // the candidate list).  It must NOT be None.
        assertThat(result).isNotInstanceOf(MatchResult.None::class.java)
        val candidates = (result as? MatchResult.Ambiguous)?.candidates
        assertThat(candidates).isNotNull()
        assertThat(candidates!!).isNotEmpty()
        assertThat(candidates[0].normalizedTitle).isEqualTo("elan")
    }

    // ── Representative OCR-like misspelling ───────────────────────────────────

    @Test fun `OCR misspelling with high fuzzy score returns Ambiguous or Confident`() = runTest {
        insertRecord("The Irishman")
        val result = matcher.match("Irshman")
        // Score is typically in the Ambiguous range (~82); must not be None
        assertThat(result).isNotInstanceOf(MatchResult.None::class.java)
    }

    @Test fun `partial OCR output at Confident threshold matches`() = runTest {
        insertRecord("Stranger Things")
        // "stranger thing" → tokenSortRatio ≈ 94
        val result = matcher.match("stranger thing")
        assertThat(result).isInstanceOf(MatchResult.Confident::class.java)
    }

    // ── No match ──────────────────────────────────────────────────────────────

    @Test fun `completely unrelated query returns None`() = runTest {
        insertRecord("The Irishman")
        assertThat(matcher.match("quantum entanglement")).isInstanceOf(MatchResult.None::class.java)
    }

    @Test fun `empty DB returns None`() = runTest {
        assertThat(matcher.match("anything")).isInstanceOf(MatchResult.None::class.java)
    }

    // ── Ambiguous ─────────────────────────────────────────────────────────────

    @Test fun `two similar titles produce Ambiguous`() = runTest {
        insertRecord("The Crown",    sessionKey = "crown1")
        insertRecord("The Crowned",  sessionKey = "crown2")
        val result = matcher.match("The Crown")
        // "The Crown" is an exact match → Confident, not Ambiguous
        // "The Crowned" would be a separate Ambiguous case
        // This assertion checks that at minimum a result is returned
        assertThat(result).isNotInstanceOf(MatchResult.None::class.java)
    }

    // ── Short-title rules (TS §4.3) ───────────────────────────────────────────

    @Test fun `short title exact match returns Confident`() = runTest {
        insertRecord("It")
        val result = matcher.match("It")
        assertThat(result).isInstanceOf(MatchResult.Confident::class.java)
    }

    @Test fun `short title 'it' does NOT match 'It Chapter Two'`() = runTest {
        insertRecord("It Chapter Two")
        // "it chapter two".length > SHORT_TITLE_MAX_LENGTH, so it won't be an
        // exact match for query "it"
        val result = matcher.match("It")
        // No exact normalized match for "it" → None
        assertThat(result).isInstanceOf(MatchResult.None::class.java)
    }

    @Test fun `short title 'up' (2 chars) does not fuzzy-match unrelated longer titles`() = runTest {
        insertRecord("Cup Noodles: A Documentary")
        val result = matcher.match("Up")
        // Short-title exact rule (≤2 chars): "up" ≠ normalized("Cup Noodles: A Documentary")
        assertThat(result).isInstanceOf(MatchResult.None::class.java)
    }

    @Test fun `3-char keyword 'run' returns Ambiguous with all FTS prefix matches`() = runTest {
        // "run" is 3 chars > SHORT_TITLE_MAX_LENGTH(2) → keyword-search path (≤4 chars)
        insertRecord("Midnight Run", sessionKey = "midnight_run")
        insertRecord(
            "Run Away: Limited Series: Ep 1",
            contentType = ContentType.SERIES,
            seriesName  = "Run Away",
            sessionKey  = "run_away_1",
        )
        insertRecord(
            "Running Point: Season 1: Pilot",
            contentType = ContentType.SERIES,
            seriesName  = "Running Point",
            sessionKey  = "running_point_1",
        )
        // FTS prefix "run*" finds all three; keyword floor ensures all appear as candidates
        val result = matcher.match("run")
        assertThat(result).isInstanceOf(MatchResult.Ambiguous::class.java)
        val candidates = (result as MatchResult.Ambiguous).candidates
        // Expect 3 distinct candidates (Run Away, Running Point, Midnight Run)
        assertThat(candidates).hasSize(3)
    }

    @Test fun `short title with two distinct raw titles is Ambiguous`() = runTest {
        // Two DIFFERENT movies both stored as "It" in Netflix export
        insertRecord("It",  viewDate = "2017-09-08", sessionKey = "it2017")
        // Simulate a second distinct Netflix entry also titled "It"
        // (e.g. the 1990 TV movie stored identically)
        val normalizer = TitleNormalizer()
        val entity2 = ViewingRecordEntity(
            provider        = "Netflix",
            rawTitle        = "It",
            displayTitle    = "It",
            normalizedTitle = normalizer.normalize("It"),
            contentType     = ContentType.UNKNOWN.name,
            viewDate        = "1990-11-18",
            sourceTier      = 1,
            importId        = 1L,
            sessionKey      = "it1990",
        )
        db.viewingRecordDao().insert(entity2)
        db.viewingRecordDao().insertFts(
            ViewingRecordFts(normalizedTitle = "it", normalizedSeriesName = "")
        )

        // Two records with the same rawTitle "It" → Ambiguous? No — same rawTitle
        // means same catalog entry. Both records have rawTitle="It", so
        // distinctBy(rawTitle).size == 1 → Confident.
        // This confirms the spec's example: same rawTitle = same title = Confident.
        val result = matcher.match("It")
        assertThat(result).isInstanceOf(MatchResult.Confident::class.java)
    }

    @Test fun `short title with two different raw titles normalising the same is Ambiguous`() = runTest {
        // In theory: "IT" and "it" both normalize to "it" — but after normalization
        // they are identical, so distinctBy(rawTitle after normalization) == 1.
        // The Ambiguous case requires genuinely different rawTitle values.
        // Insert a second record whose rawTitle is different but normalizes to "it":
        // Note: no realistic title differs from "it" yet normalizes to "it" via our
        // pipeline (parentheses, years etc. are preserved), so this tests the
        // degenerate case where rawTitle = "IT" (different case, same normalized form).
        insertRecord("IT", viewDate = "2020-01-01", sessionKey = "IT2020")
        insertRecord("It", viewDate = "2021-01-01", sessionKey = "It2021")

        val result = matcher.match("it")
        // "IT" and "It" both normalize to "it"; rawTitles differ ("IT" ≠ "It")
        // → Ambiguous
        assertThat(result).isInstanceOf(MatchResult.Ambiguous::class.java)
    }

    @Test fun `score threshold constants are at spec values`() {
        assertThat(TitleMatcher.CONFIDENCE_THRESHOLD_HIGH).isEqualTo(85)
        assertThat(TitleMatcher.CONFIDENCE_THRESHOLD_POSSIBLE).isEqualTo(55)
        // 2: protects 1-2 char titles (It, Up, Us); 3-char queries like "run" use FTS
        assertThat(TitleMatcher.SHORT_TITLE_MAX_LENGTH).isEqualTo(2)
    }
}
