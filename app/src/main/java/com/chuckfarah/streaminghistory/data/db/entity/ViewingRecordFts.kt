package com.chuckfarah.streaminghistory.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table that indexes normalized title strings for fast search
 * (TS §2.3).
 *
 * This is a standalone FTS4 table (no contentEntity) whose rows are kept in
 * sync with [ViewingRecordEntity] by the repository.  Queries return rowids
 * that correspond to [ViewingRecordEntity.id] values.
 *
 * [normalizedSeriesName] is stored as an empty string when null so FTS4
 * always has a consistent two-column schema.
 */
@Entity(tableName = "viewing_records_fts")
@Fts4
data class ViewingRecordFts(
    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String,

    @ColumnInfo(name = "normalized_series_name")
    val normalizedSeriesName: String,
)
