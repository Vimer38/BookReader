package com.example.bookreader.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.bookreader.data.model.BookFormat
import com.example.bookreader.data.model.LocalBook
import com.example.bookreader.data.model.RemoteBook
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import org.json.JSONObject

class BookFileManager(context: Context) {
    private val appContext = context.applicationContext
    private val booksDir = File(appContext.filesDir, "books").apply { mkdirs() }

    fun listLocalBooks(): List<LocalBook> {
        if (!booksDir.exists()) return emptyList()
        return booksDir.listFiles()?.mapNotNull { dir ->
            runCatching { readMeta(dir) }.getOrNull()
        } ?: emptyList()
    }

    fun destinationFor(bookId: String, format: BookFormat): File {
        val dir = File(booksDir, bookId).apply { mkdirs() }
        return File(dir, "content.${format.primaryExtension}")
    }

    fun persistRemoteBook(remoteBook: RemoteBook, format: BookFormat, file: File): LocalBook {
        val localBook = LocalBook(
            bookId = remoteBook.id,
            title = remoteBook.title,
            author = remoteBook.author,
            filePath = file.absolutePath,
            format = format,
            coverUri = remoteBook.coverUrl?.let { Uri.parse(it) },
            downloadedAt = System.currentTimeMillis()
        )
        writeMeta(localBook)
        return localBook
    }

    fun importExternalBook(
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
        writeMeta(localBook)
        return localBook
    }

    fun deleteBook(bookId: String): Boolean {
        val dir = File(booksDir, bookId)
        return dir.takeIf { it.exists() }?.deleteRecursively() ?: false
    }

    fun getFile(bookId: String, format: BookFormat): File? {
        val dir = File(booksDir, bookId)
        if (!dir.exists()) return null
        val extension = format.primaryExtension
        return File(dir, "content.$extension").takeIf { it.exists() }
    }

    private fun writeMeta(localBook: LocalBook) {
        val dir = File(booksDir, localBook.bookId).apply { mkdirs() }
        val metaFile = File(dir, META_FILE)
        val payload = JSONObject().apply {
            put("bookId", localBook.bookId)
            put("title", localBook.title)
            put("author", localBook.author)
            put("filePath", localBook.filePath)
            put("format", localBook.format.name)
            put("coverUri", localBook.coverUri?.toString())
            put("downloadedAt", localBook.downloadedAt)
        }
        metaFile.writeText(payload.toString())
    }

    private fun readMeta(dir: File): LocalBook {
        val metaFile = File(dir, META_FILE)
        val json = JSONObject(metaFile.readText())
        return LocalBook(
            bookId = json.getString("bookId"),
            title = json.getString("title"),
            author = json.getString("author"),
            filePath = json.getString("filePath"),
            format = BookFormat.fromString(json.getString("format")),
            coverUri = json.optString("coverUri").takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
            downloadedAt = json.optLong("downloadedAt", System.currentTimeMillis())
        )
    }

    private fun writeToFile(destination: File, inputStream: InputStream) {
        destination.outputStream().use { output ->
            inputStream.copyTo(output)
        }
    }

    companion object {
        private const val META_FILE = "meta.json"
    }
}

