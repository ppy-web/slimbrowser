package com.example.slimbrowser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.browserDataStore by preferencesDataStore(name = "browser_preferences")

data class BrowserSettings(
    val homeUrl: String = "",
    val fullscreenEnabled: Boolean = false,
    val darkThemeEnabled: Boolean = false,
    val backgroundUri: String? = null,
)

data class Favorite(
    val title: String,
    val url: String,
)

class BrowserPreferences(private val context: Context) {
    val settings: Flow<BrowserSettings> = context.browserDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            BrowserSettings(
                homeUrl = preferences[HOME_URL].orEmpty(),
                fullscreenEnabled = preferences[FULLSCREEN_ENABLED] ?: false,
                darkThemeEnabled = preferences[DARK_THEME_ENABLED] ?: false,
                backgroundUri = preferences[BACKGROUND_URI],
            )
        }

    val favorites: Flow<List<Favorite>> = context.browserDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences ->
            preferences[FAVORITES].orEmpty().mapNotNull(::decodeFavorite)
        }

    /** The last main-frame URL accepted by the navigation policy. */
    val lastSafeUrl: Flow<String> = context.browserDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map { preferences -> preferences[LAST_SAFE_URL].orEmpty() }

    suspend fun addFavorite(url: String, title: String) {
        val favorite = Favorite(
            title = title.trim().replace(Regex("\\s+"), " ").take(120).ifBlank { url },
            url = url,
        )
        val encoded = encodeFavorite(favorite)
        context.browserDataStore.edit { preferences ->
            val updated = preferences[FAVORITES].orEmpty()
                .filterNot { decodeFavorite(it)?.url == url }
                .toMutableSet()
            updated += encoded
            preferences[FAVORITES] = updated
        }
    }

    suspend fun removeFavorite(url: String) {
        context.browserDataStore.edit { preferences ->
            preferences[FAVORITES] = preferences[FAVORITES].orEmpty()
                .filterNot { decodeFavorite(it)?.url == url }
                .toSet()
        }
    }

    suspend fun getFavorites(): List<Favorite> = favorites.first()

    suspend fun update(
        homeUrl: String,
        fullscreenEnabled: Boolean,
        darkThemeEnabled: Boolean,
        backgroundUri: String?,
    ) {
        context.browserDataStore.edit { preferences ->
            preferences[HOME_URL] = homeUrl
            preferences[FULLSCREEN_ENABLED] = fullscreenEnabled
            preferences[DARK_THEME_ENABLED] = darkThemeEnabled
            if (backgroundUri.isNullOrBlank()) {
                preferences.remove(BACKGROUND_URI)
            } else {
                preferences[BACKGROUND_URI] = backgroundUri
            }
        }
    }

    suspend fun setFullscreenEnabled(enabled: Boolean) {
        context.browserDataStore.edit { preferences ->
            preferences[FULLSCREEN_ENABLED] = enabled
        }
    }

    suspend fun setLastSafeUrl(url: String?) {
        context.browserDataStore.edit { preferences ->
            if (url.isNullOrBlank()) preferences.remove(LAST_SAFE_URL)
            else preferences[LAST_SAFE_URL] = url
        }
    }

    private companion object {
        val HOME_URL = stringPreferencesKey("home_url")
        val FULLSCREEN_ENABLED = booleanPreferencesKey("fullscreen_enabled")
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val BACKGROUND_URI = stringPreferencesKey("background_uri")
        val LAST_SAFE_URL = stringPreferencesKey("last_safe_url")
        val FAVORITES = stringSetPreferencesKey("favorites")

        fun encodeFavorite(favorite: Favorite): String = Base64.getEncoder().encodeToString(
            "${favorite.title}\n${favorite.url}".toByteArray(StandardCharsets.UTF_8),
        )

        fun decodeFavorite(encoded: String): Favorite? = runCatching {
            val value = String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
            val separator = value.indexOf('\n')
            if (separator <= 0 || separator == value.lastIndex) return null
            Favorite(value.substring(0, separator), value.substring(separator + 1))
        }.getOrNull()
    }
}
