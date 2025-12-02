package com.example.bookreader.di

import android.content.Context
import com.example.bookreader.data.preferences.ReadingPreferencesStore
import com.example.bookreader.data.preferences.ReadingPositionStore
import com.example.bookreader.data.storage.YandexStorageManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideReadingPreferencesStore(@ApplicationContext context: Context): ReadingPreferencesStore {
        return ReadingPreferencesStore(context)
    }

    @Provides
    @Singleton
    fun provideReadingPositionStore(@ApplicationContext context: Context): ReadingPositionStore {
        return ReadingPositionStore(context)
    }

    @Provides
    @Singleton
    fun provideYandexStorageManager(@ApplicationContext context: Context): YandexStorageManager {
        return YandexStorageManager(context)
    }
}

