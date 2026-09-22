package com.example.slimbrowser.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomHistoryRepository(context: Context) {
    private val dao = BrowserDatabase.getInstance(context).historyDao()

    suspend fun recent(limit: Int = 100): List<HistoryEntry> = withContext(Dispatchers.IO) {
        dao.recent(limit.coerceIn(0, 1000)).map { HistoryEntry(it.title, it.url, it.visitedAt) }
    }

    suspend fun add(url: String, title: String, visitedAt: Long, privateSession: Boolean) = withContext(Dispatchers.IO) {
        if (privateSession) return@withContext
        dao.deleteByUrl(url)
        dao.insert(HistoryRecord().apply {
            this.url = url
            this.title = title.ifBlank { url }.take(120)
            this.visitedAt = visitedAt
            this.privateSession = false
        })
    }

    suspend fun remove(url: String) = withContext(Dispatchers.IO) { dao.deleteByUrl(url) }
    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }
}

class RoomBookmarkRepository(context: Context) {
    private val dao = BrowserDatabase.getInstance(context).bookmarkDao()

    suspend fun list(): List<Favorite> = withContext(Dispatchers.IO) {
        dao.all().map { Favorite(it.title, it.url) }
    }

    suspend fun add(url: String, title: String) = withContext(Dispatchers.IO) {
        dao.insert(BookmarkRecord().apply {
            this.url = url
            this.title = title.ifBlank { url }.take(120)
            this.createdAt = System.currentTimeMillis()
        })
    }

    suspend fun remove(url: String) = withContext(Dispatchers.IO) { dao.deleteByUrl(url) }
    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }
}

class RoomDownloadRepository(context: Context) {
    private val dao = BrowserDatabase.getInstance(context).downloadDao()

    suspend fun list(): List<DownloadEntry> = withContext(Dispatchers.IO) {
        dao.recent(100).map { DownloadEntry(it.fileName, it.url, it.createdAt) }
    }

    suspend fun add(fileName: String, url: String) = withContext(Dispatchers.IO) {
        dao.insert(DownloadRecord().apply {
            this.fileName = fileName.take(180)
            this.url = url
            this.createdAt = System.currentTimeMillis()
            this.status = 0
        })
    }

    suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }
}
