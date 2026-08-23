package com.example.slimbrowser.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history_entries",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["visitedAt"]),
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val host: String,
    val faviconUri: String? = null,
    val visitedAt: Long,
    val visitCount: Int = 1,
)
