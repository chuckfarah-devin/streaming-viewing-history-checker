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

    // ── Lookups by exact normalized title ─────────────────────────────────────

    @Query("""
        SELECT * FROM viewing_records
        WHERE normalized_title = :normalizedTitle
        ORDER BY view_date DESC
    """)
    suspend fun getByExactNormalizedTitle(normalizedTitle: String): List<ViewingRecordEntity>

    // ── FTS-backed search ─────────────────────────────────────────────────────

    /**
     * Returns all records whose normalized_title or normalized_series_name
     * matches the FTS4 query.
     *
     * The subquery returns the rowid from the FTS table, which was explicitly
     * set to match viewing_records.id at insert time.
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
        ORDER BY view_date DESC
    """)
    suspend fun getSeriesRecords(normalizedSeriesName: String): List<ViewingRecordEntity>

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
