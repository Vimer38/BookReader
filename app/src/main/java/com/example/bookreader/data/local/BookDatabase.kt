package com.example.bookreader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [BookEntity::class, TodoEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(BookConverters::class)
abstract class BookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: BookDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                CREATE TABLE IF NOT EXISTS `todos` (
                    `id` INTEGER NOT NULL,
                    `userId` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `syncedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): BookDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BookDatabase::class.java,
                    "book_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

