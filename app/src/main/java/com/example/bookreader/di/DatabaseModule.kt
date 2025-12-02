package com.example.bookreader.di

import android.content.Context
import com.example.bookreader.data.local.BookDatabase
import com.example.bookreader.data.local.BookDao
import com.example.bookreader.data.local.TodoDao
import com.example.bookreader.data.remote.JsonPlaceholderApi
import com.example.bookreader.data.repository.BookRepository
import com.example.bookreader.data.repository.TodoRepository
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
    fun provideBookDao(database: BookDatabase): BookDao = database.bookDao()

    @Provides
    @Singleton
    fun provideTodoDao(database: BookDatabase): TodoDao = database.todoDao()

    @Provides
    @Singleton
    fun provideBooksDir(@ApplicationContext context: Context): File {
        return File(context.filesDir, "books").apply { mkdirs() }
    }

    @Provides
    @Singleton
    fun provideBookRepository(
        bookDao: BookDao,
        booksDir: File
    ): BookRepository {
        return BookRepository(bookDao, booksDir)
    }

    @Provides
    @Singleton
    fun provideTodoRepository(
        database: BookDatabase,
        todoDao: TodoDao,
        api: JsonPlaceholderApi
    ): TodoRepository = TodoRepository(api, todoDao, database)
}

