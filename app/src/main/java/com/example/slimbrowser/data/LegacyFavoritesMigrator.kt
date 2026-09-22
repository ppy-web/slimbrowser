package com.example.slimbrowser.data

import com.example.slimbrowser.data.bookmark.BookmarkRepository

/**
 * Copies legacy string-set favorites into Room. URL uniqueness makes every copy idempotent, even
 * if a process dies after Room writes but before the DataStore completion marker is committed.
 */
class LegacyFavoritesMigrator(
    private val preferences: BrowserPreferences,
    private val bookmarkRepository: BookmarkRepository,
) {
    suspend fun migrateIfNeeded(): Int {
        if (preferences.isLegacyFavoritesMigrationComplete()) return 0

        val favorites = preferences.getFavorites().sortedBy { it.title.lowercase() }
        favorites.forEachIndexed { index, favorite ->
            bookmarkRepository.addOrUpdate(
                url = favorite.url,
                title = favorite.title,
                pinnedToHome = true,
                sortOrder = index,
            )
        }
        preferences.markLegacyFavoritesMigrationComplete()
        return favorites.size
    }
}
