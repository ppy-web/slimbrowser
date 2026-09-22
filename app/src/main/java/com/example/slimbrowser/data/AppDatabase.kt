package com.example.slimbrowser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.slimbrowser.data.bookmark.BookmarkDao
import com.example.slimbrowser.data.bookmark.BookmarkEntity
import com.example.slimbrowser.data.download.DownloadConverters
import com.example.slimbrowser.data.download.DownloadDao
import com.example.slimbrowser.data.download.DownloadEntity
import com.example.slimbrowser.data.history.HistoryDao
import com.example.slimbrowser.data.history.HistoryEntity

@Database(
    entities = [
        HistoryEntity::class,
        BookmarkEntity::class,
        DownloadEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DownloadConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun downloadDao(): DownloadDao

    companion object {
        const val DATABASE_NAME = "slimbrowser.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
        }
    }
}
