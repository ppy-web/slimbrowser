package com.example.slimbrowser.data.bookmark

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["sortOrder"]),
        Index(value = ["pinnedToHome"]),
    ],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int,
    val pinnedToHome: Boolean = false,
)
