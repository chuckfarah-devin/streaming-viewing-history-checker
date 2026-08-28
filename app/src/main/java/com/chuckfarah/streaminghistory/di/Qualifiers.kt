package com.chuckfarah.streaminghistory.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MlKitTextRecognizer

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VisionTextRecognizer
