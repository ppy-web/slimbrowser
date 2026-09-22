package com.example.slimbrowser.data.download

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    SUCCESSFUL,
    FAILED,
    CANCELLED,
}

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["downloadManagerId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["updatedAt"]),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val downloadManagerId: Long? = null,
    val sourceUrl: String,
    val fileName: String,
    val mimeType: String? = null,
    val contentDisposition: String? = null,
    val userAgent: String? = null,
    val cookieHeader: String? = null,
    val destinationUri: String? = null,
    val localUri: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val failureReason: Int? = null,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val retryCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAttemptAt: Long = updatedAt,
)
