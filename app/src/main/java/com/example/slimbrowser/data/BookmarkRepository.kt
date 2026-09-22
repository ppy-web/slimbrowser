package com.example.slimbrowser.data

import android.content.Context

/** Bookmark API independent from the UI; storage can be moved to Room without callers changing. */
class BookmarkRepository private constructor(
    private val room: RoomBookmarkRepository?,
    private val legacy: BrowserPreferences?,
) {
    constructor(context: Context) : this(RoomBookmarkRepository(context), null)
    constructor(preferences: BrowserPreferences) : this(null, preferences)

    suspend fun list(): List<Favorite> = room?.list() ?: legacy!!.getFavorites()
    suspend fun add(url: String, title: String) = room?.add(url, title) ?: legacy!!.addFavorite(url, title)
    suspend fun remove(url: String) = room?.remove(url) ?: legacy!!.removeFavorite(url)
    suspend fun clear() = room?.clear() ?: Unit
}
