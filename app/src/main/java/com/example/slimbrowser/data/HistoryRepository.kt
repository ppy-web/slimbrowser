package com.example.slimbrowser.data

import android.content.Context

/** Structured history API kept behind a repository boundary for future Room migration. */
class HistoryRepository private constructor(
    private val room: RoomHistoryRepository?,
    private val legacy: BrowserPreferences?,
) {
    constructor(context: Context) : this(RoomHistoryRepository(context), null)
    /** Compatibility constructor for callers migrating from the pre-Room store. */
    constructor(preferences: BrowserPreferences) : this(null, preferences)

    suspend fun recent(limit: Int = 100): List<HistoryEntry> = room?.recent(limit) ?: legacy!!.getHistory().take(limit.coerceAtLeast(0))
    suspend fun add(entry: HistoryEntry) = room?.add(entry.url, entry.title, entry.visitedAt, false)
        ?: legacy!!.addHistory(entry.url, entry.title, entry.visitedAt)
    suspend fun add(url: String, title: String, visitedAt: Long, privateSession: Boolean) =
        room?.add(url, title, visitedAt, privateSession)
            ?: if (!privateSession) legacy!!.addHistory(url, title, visitedAt) else Unit
    suspend fun remove(url: String) = room?.remove(url) ?: legacy!!.removeHistory(url)
    suspend fun clear() = room?.clear() ?: legacy!!.clearHistory()
    suspend fun search(query: String): List<HistoryEntry> {
        val value = query.trim().lowercase()
        if (value.isBlank()) return recent()
        return recent().filter { it.title.lowercase().contains(value) || it.url.lowercase().contains(value) }
    }

    suspend fun groupedByDay(): Map<String, List<HistoryEntry>> = recent().groupBy {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(it.visitedAt))
    }
}
