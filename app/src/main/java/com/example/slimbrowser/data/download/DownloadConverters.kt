package com.example.slimbrowser.data.download

import androidx.room.TypeConverter

class DownloadConverters {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus = runCatching { DownloadStatus.valueOf(value) }
        .getOrDefault(DownloadStatus.FAILED)
}
