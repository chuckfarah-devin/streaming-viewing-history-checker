package com.chuckfarah.streaminghistory.di

import android.content.Context
import androidx.room.Room
import com.chuckfarah.streaminghistory.data.db.AppDatabase
import com.chuckfarah.streaminghistory.data.db.dao.ImportBatchDao
import com.chuckfarah.streaminghistory.data.db.dao.ViewingRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Singleton
    @Provides
    fun provideViewingRecordDao(db: AppDatabase): ViewingRecordDao = db.viewingRecordDao()

    @Singleton
    @Provides
    fun provideImportBatchDao(db: AppDatabase): ImportBatchDao = db.importBatchDao()
}
