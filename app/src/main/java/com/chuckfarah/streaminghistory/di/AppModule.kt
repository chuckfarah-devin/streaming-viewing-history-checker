package com.chuckfarah.streaminghistory.di

import android.content.Context
import androidx.room.Room
import com.chuckfarah.streaminghistory.data.db.AppDatabase
import com.chuckfarah.streaminghistory.data.db.dao.ImportBatchDao
import com.chuckfarah.streaminghistory.data.db.dao.ViewingRecordDao
import com.chuckfarah.streaminghistory.data.prefs.UserPreferences
import com.chuckfarah.streaminghistory.domain.ocr.GoogleVisionTextRecognizer
import com.chuckfarah.streaminghistory.domain.ocr.MlKitTextRecognizer
import com.chuckfarah.streaminghistory.domain.ocr.TextRecognizer
import javax.inject.Named
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

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

    @Singleton
    @Provides
    @Named("mlKit")
    fun provideMlKitTextRecognizer(): TextRecognizer = MlKitTextRecognizer()

    @Singleton
    @Provides
    @Named("vision")
    fun provideVisionTextRecognizer(): TextRecognizer = GoogleVisionTextRecognizer()

    @Singleton
    @Provides
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences = UserPreferences(context)

    @Singleton
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Singleton
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
