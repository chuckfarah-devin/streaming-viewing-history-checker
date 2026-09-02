package com.chuckfarah.streaminghistory.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordFts

@Dao
interface ViewingRecordDao {

    // ── Inserts ───────────────────────────────────────────────────────────────

    /** Insert a viewing record; returns its auto-generated id. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: ViewingRecordEntity): Long

    /**
     * Insert an FTS entry.  [ViewingRecordFts] does not have a PrimaryKey field;
     * Room will use the auto-generated rowid.  We rely on the caller to insert
     * into the FTS table immediately after inserting the main record so that
     * FTS rowid == viewing_records.id.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(fts: ViewingRecordFts)

    // ── Existence checks ──────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) > 0 FROM viewing_records WHERE session_key = :key")
    suspend fun existsBySessionKey(key: String): Boolean

    /** Returns the full record for a session key, or null if not yet imported. */
    @Query("SELECT * FROM viewing_records WHERE session_key = :key LIMIT 1")
    suspend fun getBySessionKey(key: String): ViewingRecordEntity?

    // ── Lookups by exact normalized title ─────────────────────────────────────

    @Query("""
        SELECT * FROM viewing_records
        WHERE normalized_title = :normalizedTitle
          AND (:profile IS NULL OR profile_name = :profile OR profile_name IS NULL)
        ORDER BY view_date DESC
    """)
    suspend fun getByExactNormalizedTitle(
        normalizedTitle: String,
        profile: String? = null,
    ): List<ViewingRecordEntity>

    // ── FTS-backed search ─────────────────────────────────────────────────────

    /**
     * Returns all records whose normalized_title or normalized_series_name
     * matches the FTS4 query.
     *
     * The subquery returns the rowid from the FTS table, which was explicitly
     * set to match viewing_records.id at insert time.
     *
     * Note: matching is always unfiltered so TitleMatcher can find titles
     * regardless of the active profile; profile filtering is applied at the
     * result-resolution stage by [getByExactNormalizedTitle] / [getSeriesRecords].
     */
    @Query("""
        SELECT vr.* FROM viewing_records vr
        WHERE vr.id IN (
            SELECT rowid FROM viewing_records_fts
            WHERE viewing_records_fts MATCH :ftsQuery
        )
        ORDER BY vr.view_date DESC
    """)
    suspend fun searchFts(ftsQuery: String): List<ViewingRecordEntity>

    // ── Distinct title projection for fuzzy fallback ──────────────────────────

    /** Used by the fuzzy fallback (Stage 3) when FTS returns nothing. */
    @Query("""
        SELECT DISTINCT normalized_title, normalized_series_name
        FROM viewing_records
    """)
    suspend fun getAllDistinctTitlePairs(): List<TitlePair>

    // ── Series stats ──────────────────────────────────────────────────────────

    @Query("""
        SELECT * FROM viewing_records
        WHERE normalized_series_name = :normalizedSeriesName
          AND content_type = 'SERIES'
          AND (:profile IS NULL OR profile_name = :profile OR profile_name IS NULL)
        ORDER BY view_date DESC
    """)
    suspend fun getSeriesRecords(
        normalizedSeriesName: String,
        profile: String? = null,
    ): List<ViewingRecordEntity>

    // ── Counts ────────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM viewing_records")
    suspend fun totalCount(): Int

    // ── Recent viewings ───────────────────────────────────────────────────────

    /**
     * Returns the [limit] most recently viewed records (by view_date DESC).
     * Used to populate the "Recent history" list on the Home screen.
     */
    @Query("""
        SELECT * FROM viewing_records
        ORDER BY view_date DESC
        LIMIT :limit
    """)
    suspend fun getRecentViewings(limit: Int = 10): List<ViewingRecordEntity>

    // ── Tier 2 reconciliation ─────────────────────────────────────────────────

    /**
     * Find unmatched Tier 1 records for the reconciliation exact-date pass (TS §3.6).
     * Results are ordered by id ASC so we always take the earliest unmatched row.
     */
    @Query("""
        SELECT * FROM viewing_records
        WHERE source_tier = 1
          AND normalized_title = :normalizedTitle
          AND view_date = :viewDate
          AND id NOT IN (:excludedIds)
        ORDER BY id ASC
    """)
    suspend fun getTier1ExactDate(
        normalizedTitle: String,
        viewDate: String,
        excludedIds: List<Long>,
    ): List<ViewingRecordEntity>

    /**
     * Find unmatched Tier 1 records for the reconciliation ±1-day fallback (TS §3.6).
     */
    @Query("""
        SELECT * FROM viewing_records
        WHERE source_tier = 1
          AND normalized_title = :normalizedTitle
          AND view_date IN (:dateBefore, :dateAfter)
          AND id NOT IN (:excludedIds)
        ORDER BY id ASC
    """)
    suspend fun getTier1AdjacentDates(
        normalizedTitle: String,
        dateBefore: String,
        dateAfter: String,
        excludedIds: List<Long>,
    ): List<ViewingRecordEntity>

    /**
     * Upgrade an existing Tier 1 record to Tier 2, populating all Tier-2-only
     * fields.  source_tier and session_key are updated; id, normalized_title,
     * and all other parse-derived fields are left unchanged (TS §3.6).
     */
    @Query("""
        UPDATE viewing_records SET
            start_time_utc    = :startTimeUtc,
            duration_ms       = :durationMs,
            bookmark_ms       = :bookmarkMs,
            latest_bookmark_ms = :latestBookmarkMs,
            profile_name      = :profileName,
            is_autoplayed     = :isAutoplayed,
            is_hidden         = :isHidden,
            attributes_raw    = :attributesRaw,
            device_type       = :deviceType,
            source_tier       = 2,
            session_key       = :sessionKey,
            import_id         = :importId
        WHERE id = :id
    """)
    suspend fun upgradeToTier2(
        id: Long,
        startTimeUtc: String,
        durationMs: Long?,
        bookmarkMs: Long?,
        latestBookmarkMs: Long?,
        profileName: String?,
        isAutoplayed: Int,
        isHidden: Int,
        attributesRaw: String?,
        deviceType: String?,
        sessionKey: String,
        importId: Long,
    )

    /** All distinct non-null profile names from Tier 2 records. */
    @Query("""
        SELECT DISTINCT profile_name FROM viewing_records
        WHERE source_tier = 2 AND profile_name IS NOT NULL
        ORDER BY profile_name ASC
    """)
    suspend fun getDistinctProfiles(): List<String>

    // ── Tier 1 management ─────────────────────────────────────────────────────

    /**
     * Delete all Tier 1 records (source_tier = 1).
     * Called before inserting a replacement Tier 1 export (TS §3.5).
     * Tier 2 records (source_tier = 2) are never affected.
     */
    @Query("DELETE FROM viewing_records WHERE source_tier = 1")
    suspend fun deleteAllTier1Records()

    /** Corresponding FTS cleanup after deleting Tier 1 records. */
    @Query("""
        DELETE FROM viewing_records_fts
        WHERE rowid NOT IN (SELECT id FROM viewing_records)
    """)
    suspend fun pruneOrphanedFtsRows()

    // ── Manual search ────────────────────────────────────────────────────────

    /**
     * Returns every accessible record whose [normalizedTitle] or
     * [normalizedSeriesName] contains [query] as a substring.
     *
     * Title predicates are parenthesized so the OR cannot escape the AND
     * profile filter.  Accessible means: profile_name matches the active
     * profile, or profile_name is NULL (Tier 1 rows carry no profile), or no
     * profile filter is active (:profile IS NULL).
     *
     * Results are ordered deterministically: view_date DESC, then source_tier
     * DESC (Tier 2 before Tier 1 on the same date), then start_time_utc DESC,
     * then id ASC — matching the sort convention used in buildWatched.
     */
    @Query("""
        SELECT * FROM viewing_records
        WHERE (
            normalized_title LIKE '%' || :query || '%'
            OR normalized_series_name LIKE '%' || :query || '%'
        )
        AND (:profile IS NULL OR profile_name = :profile OR profile_name IS NULL)
        ORDER BY view_date DESC, source_tier DESC, start_time_utc DESC, id ASC
    """)
    suspend fun searchBySubstring(
        query: String,
        profile: String? = null,
    ): List<ViewingRecordEntity>

    // ── Full clear (user-initiated) ───────────────────────────────────────────

    @Query("DELETE FROM viewing_records")
    suspend fun deleteAll()

    @Query("DELETE FROM viewing_records_fts")
    suspend fun deleteAllFts()
}

/** Lightweight projection used by the fuzzy fallback. */
data class TitlePair(
    val normalized_title: String,
    val normalized_series_name: String?,
)
