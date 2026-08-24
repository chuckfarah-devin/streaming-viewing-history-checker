package com.chuckfarah.streaminghistory.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata for one CSV import operation (TS §2.2).
 *
 * [fileFingerprint] is the SHA-256 hex of the imported file's full byte
 * content and is used for file-level idempotency (Tier 1 only).
 */
@Entity(
    tableName = "import_batches",
    indices   = [Index("file_fingerprint", unique = true)]
)
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** ISO 8601 timestamp of when this import was performed. */
    @ColumnInfo(name = "imported_at")
    val importedAt: String,

    /** 1 or 2. */
    @ColumnInfo(name = "source_tier")
    val sourceTier: Int,

    /** File name only (not path) for display purposes. */
    @ColumnInfo(name = "source_file_name")
    val sourceFileName: String,

    /**
     * SHA-256 hex of the full file content.
     * A second import of the same file (identical bytes) matches this fingerprint
     * and is rejected as already imported.
     */
    @ColumnInfo(name = "file_fingerprint")
    val fileFingerprint: String,

    /** Number of records inserted in this batch. */
    @ColumnInfo(name = "record_count")
    val recordCount: Int,
)
