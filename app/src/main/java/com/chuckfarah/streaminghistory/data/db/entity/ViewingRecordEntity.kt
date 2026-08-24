package com.chuckfarah.streaminghistory.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One viewing session from an imported Netflix history (TS §2.1).
 *
 * Tier 1 imports leave all Tier-2-only columns null.
 * Tier 2 imports (future) populate start_time_utc, duration_ms, bookmark_ms,
 * latest_bookmark_ms, profile_name, is_autoplayed, attributes_raw, device_type.
 */
@Entity(
    tableName = "viewing_records",
    indices   = [
        Index("session_key",           unique = true),
        Index("normalized_title"),
        Index("normalized_series_name"),
        Index("view_date"),
        Index("source_tier"),
    ]
)
data class ViewingRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** "Netflix" in Phase 1; reserved for future providers. */
    val provider: String,

    /** Original title string from the Netflix export; always preserved. */
    @ColumnInfo(name = "raw_title")
    val rawTitle: String,

    /** Title shown to the user (series name for SERIES; raw title for UNKNOWN). */
    @ColumnInfo(name = "display_title")
    val displayTitle: String,

    /** Lowercased, punctuation-normalized title for matching. */
    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String,

    /** "SERIES", "UNKNOWN", or "MOVIE" (reserved; never set in Phase 1). */
    @ColumnInfo(name = "content_type")
    val contentType: String,

    /** Extracted series name; null for non-SERIES records. */
    @ColumnInfo(name = "series_name")
    val seriesName: String? = null,

    @ColumnInfo(name = "normalized_series_name")
    val normalizedSeriesName: String? = null,

    @ColumnInfo(name = "season_label")
    val seasonLabel: String? = null,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int? = null,

    @ColumnInfo(name = "episode_title")
    val episodeTitle: String? = null,

    /** ISO 8601 calendar date YYYY-MM-DD. */
    @ColumnInfo(name = "view_date")
    val viewDate: String,

    // ── Tier 2 only ──────────────────────────────────────────────────────────

    @ColumnInfo(name = "start_time_utc")
    val startTimeUtc: String? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,

    @ColumnInfo(name = "bookmark_ms")
    val bookmarkMs: Long? = null,

    @ColumnInfo(name = "latest_bookmark_ms")
    val latestBookmarkMs: Long? = null,

    @ColumnInfo(name = "profile_name")
    val profileName: String? = null,

    /** 0 or 1; always 0 for Tier 1. */
    @ColumnInfo(name = "is_hidden")
    val isHidden: Int = 0,

    /** 0 or 1; always 0 for Tier 1 (no Attributes field). */
    @ColumnInfo(name = "is_autoplayed")
    val isAutoplayed: Int = 0,

    @ColumnInfo(name = "attributes_raw")
    val attributesRaw: String? = null,

    @ColumnInfo(name = "device_type")
    val deviceType: String? = null,

    // ── Import provenance ────────────────────────────────────────────────────

    /** 1 or 2; indicates which import format provided this row's data. */
    @ColumnInfo(name = "source_tier")
    val sourceTier: Int,

    /** FK → import_batches.id of the batch that created this row. */
    @ColumnInfo(name = "import_id")
    val importId: Long,

    /**
     * Stable session identifier (TS §3.4).
     * Tier 1: SHA-256(file_fingerprint + "|" + row_index)
     * Tier 2: SHA-256(provider + "|" + profile_name + "|" + start_time_utc + "|" + raw_title)
     */
    @ColumnInfo(name = "session_key")
    val sessionKey: String,
)
