package com.example.slimbrowser.data

class ReadingListRepository(private val preferences: BrowserPreferences) {
    suspend fun list(): List<ReadingEntry> = preferences.getReadingList()
    suspend fun add(url: String, title: String) = preferences.addReading(url, title)
    suspend fun remove(url: String) = preferences.removeReading(url)
}
