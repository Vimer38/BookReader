package com.example.bookreader.data.model

import android.net.Uri

enum class BookFormat(val extensions: List<String>) {
    TXT(listOf("txt")),
    PDF(listOf("pdf")),
    EPUB(listOf("epub"));

    val primaryExtension: String get() = extensions.first()

    companion object {
        fun fromFileName(fileName: String?): BookFormat {
            val extension = fileName
                ?.substringAfterLast('.', "")
                ?.lowercase()
                ?: return TXT
            return values().firstOrNull { format ->
                format.extensions.any { it.equals(extension, ignoreCase = true) }
            } ?: TXT
        }

        fun fromString(value: String): BookFormat =
            values().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TXT
    }
}

data class RemoteBook(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val fileUrl: String = "",
    val userId: String = "",
    val coverUrl: String? = null,
    val format: String = BookFormat.TXT.name,
    val createdAt: Long = System.currentTimeMillis()
)

data class LocalBook(
    val bookId: String,
    val title: String,
    val author: String,
    val filePath: String,
    val format: BookFormat,
    val coverUri: Uri? = null,
    val downloadedAt: Long = System.currentTimeMillis()
)

