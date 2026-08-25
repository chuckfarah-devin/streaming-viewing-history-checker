package com.chuckfarah.streaminghistory.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chuckfarah.streaminghistory.data.db.entity.ImportBatchEntity

@Dao
interface ImportBatchDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(batch: ImportBatchEntity): Long

    /** Returns true if a batch with this fingerprint already exists (Tier 1 idempotency). */
    @Query("SELECT COUNT(*) > 0 FROM import_batches WHERE file_fingerprint = :fingerprint")
    suspend fun existsByFingerprint(fingerprint: String): Boolean

    @Query("SELECT * FROM import_batches ORDER BY id DESC")
    suspend fun getAll(): List<ImportBatchEntity>

    @Query("DELETE FROM import_batches WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM import_batches")
    suspend fun deleteAll()
}
