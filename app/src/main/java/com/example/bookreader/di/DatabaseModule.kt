package com.example.bookreader.di

import android.content.Context
import com.example.bookreader.data.local.BookDatabase
import com.example.bookreader.data.repository.BookRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideBookDatabase(@ApplicationContext context: Context): BookDatabase {
        return BookDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideBookDao(database: BookDatabase) = database.bookDao()

    @Provides
    @Singleton
    fun provideBooksDir(@ApplicationContext context: Context): File {
        return File(context.filesDir, "books").apply { mkdirs() }
    }

    @Provides
    @Singleton
    fun provideBookRepository(
        bookDao: com.example.bookreader.data.local.BookDao,
        booksDir: File
    ): BookRepository {
        return BookRepository(bookDao, booksDir)
    }
}

