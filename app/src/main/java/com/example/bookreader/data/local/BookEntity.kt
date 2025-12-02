package com.example.bookreader.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.bookreader.data.model.BookFormat

@Entity(tableName = "books")
@TypeConverters(BookConverters::class)
data class BookEntity(
    @PrimaryKey
    val bookId: String,
    val title: String,
    val author: String,
    val filePath: String,
    val format: BookFormat,
    val coverUri: String? = null,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileUrl: String? = null,
    val userId: String? = null,
    val createdAt: Long? = null
)

class BookConverters {
    @TypeConverter
    fun fromBookFormat(format: BookFormat): String = format.name

    @TypeConverter
    fun toBookFormat(value: String): BookFormat = BookFormat.fromString(value)

    @TypeConverter
    fun fromUri(uri: Uri?): String? = uri?.toString()

    @TypeConverter
    fun toUri(value: String?): Uri? = value?.let { Uri.parse(it) }
}

