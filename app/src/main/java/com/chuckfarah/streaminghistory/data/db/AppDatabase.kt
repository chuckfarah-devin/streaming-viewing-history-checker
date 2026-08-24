package com.chuckfarah.streaminghistory.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chuckfarah.streaminghistory.data.db.dao.ImportBatchDao
import com.chuckfarah.streaminghistory.data.db.dao.ViewingRecordDao
import com.chuckfarah.streaminghistory.data.db.entity.ImportBatchEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordEntity
import com.chuckfarah.streaminghistory.data.db.entity.ViewingRecordFts

/**
 * Single Room database for the application.
 *
 * Schema version history:
 *   1 — Initial schema (Phase 1, Steps 1–5)
 */
@Database(
    entities  = [ViewingRecordEntity::class, ViewingRecordFts::class, ImportBatchEntity::class],
    version   = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun viewingRecordDao(): ViewingRecordDao
    abstract fun importBatchDao(): ImportBatchDao

    companion object {
        const val DATABASE_NAME = "streaming_history.db"
    }
}
