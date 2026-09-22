package com.example.slimbrowser.data

import android.content.Context

class DownloadRepository private constructor(
    private val room: RoomDownloadRepository?,
    private val legacy: BrowserPreferences?,
) {
    constructor(context: Context) : this(RoomDownloadRepository(context), null)
    constructor(preferences: BrowserPreferences) : this(null, preferences)

    suspend fun list(): List<DownloadEntry> = room?.list() ?: legacy!!.getDownloads()
    suspend fun add(fileName: String, url: String) = room?.add(fileName, url) ?: legacy!!.addDownload(fileName, url)
    suspend fun clear() = room?.clear() ?: legacy!!.clearDownloads()
}
