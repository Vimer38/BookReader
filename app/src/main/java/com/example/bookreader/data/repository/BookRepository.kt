package com.example.bookreader.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.example.bookreader.data.local.BookDao
import com.example.bookreader.data.local.BookEntity
import com.example.bookreader.data.model.BookFormat
import com.example.bookreader.data.model.LocalBook
import com.example.bookreader.data.model.RemoteBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
    private val booksDir: File
) {
    init {
        booksDir.mkdirs()
    }

    fun getAllBooks(): Flow<List<LocalBook>> {
        return bookDao.getAllBooks().map { entities ->
            entities.map { it.toLocalBook() }
        }
    }

    fun getBookById(bookId: String): Flow<LocalBook?> {
        return bookDao.getBookByIdFlow(bookId).map { it?.toLocalBook() }
    }

    suspend fun insertBook(book: LocalBook) {
        val entity = book.toEntity()
        bookDao.insertBook(entity)
    }

    suspend fun insertRemoteBook(remoteBook: RemoteBook, format: BookFormat, file: File): LocalBook {
        val localBook = LocalBook(
            bookId = remoteBook.id,
            title = remoteBook.title,
            author = remoteBook.author,
            filePath = file.absolutePath,
            format = format,
            coverUri = remoteBook.coverUrl?.let { Uri.parse(it) },
            downloadedAt = System.currentTimeMillis()
        )
        val entity = localBook.toEntity().copy(
            fileUrl = remoteBook.fileUrl,
            userId = remoteBook.userId,
            createdAt = remoteBook.createdAt
        )
        bookDao.insertBook(entity)
        return localBook
    }

    suspend fun importExternalBook(
        bookId: String,
        title: String,
        author: String,
        format: BookFormat,
        contentResolver: ContentResolver,
        sourceUri: Uri
    ): LocalBook {
        val destination = destinationFor(bookId, format)
        contentResolver.openInputStream(sourceUri)?.use { input ->
            writeToFile(destination, input)
        } ?: error("Не удалось открыть файл")
        
        val localBook = LocalBook(
            bookId = bookId,
            title = title,
            author = author,
            filePath = destination.absolutePath,
            format = format,
            downloadedAt = System.currentTimeMillis()
        )
        bookDao.insertBook(localBook.toEntity())
        return localBook
    }

    suspend fun deleteBook(bookId: String): Boolean {
        val entity = bookDao.getBookById(bookId) ?: return false
        val file = File(entity.filePath)
        if (file.exists()) {
            file.delete()
        }
        val dir = File(booksDir, bookId)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        bookDao.deleteBookById(bookId)
        return true
    }

    fun getFile(bookId: String, format: BookFormat): File {
        val dir = File(booksDir, bookId).apply { mkdirs() }
        val extension = format.primaryExtension
        return File(dir, "content.$extension")
    }

    fun searchBooks(query: String): Flow<List<LocalBook>> {
        return bookDao.searchBooks(query).map { entities ->
            entities.map { it.toLocalBook() }
        }
    }

    private fun destinationFor(bookId: String, format: BookFormat): File {
        val dir = File(booksDir, bookId).apply { mkdirs() }
        return File(dir, "content.${format.primaryExtension}")
    }

    private fun writeToFile(destination: File, input: InputStream) {
        FileOutputStream(destination).use { output ->
            input.copyTo(output)
        }
    }

    private fun BookEntity.toLocalBook(): LocalBook {
        return LocalBook(
            bookId = bookId,
            title = title,
            author = author,
            filePath = filePath,
            format = format,
            coverUri = coverUri?.let { Uri.parse(it) },
            downloadedAt = downloadedAt
        )
    }

    private fun LocalBook.toEntity(): BookEntity {
        return BookEntity(
            bookId = bookId,
            title = title,
            author = author,
            filePath = filePath,
            format = format,
            coverUri = coverUri?.toString(),
            downloadedAt = downloadedAt
        )
    }
}

