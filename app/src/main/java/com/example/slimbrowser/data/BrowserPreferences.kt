package com.example.slimbrowser.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserDataStore by preferencesDataStore(name = "browser_preferences")

data class BrowserSettings(
    val homeUrl: String = "",
    val fullscreenEnabled: Boolean = false,
)

class BrowserPreferences(private val context: Context) {
    val settings: Flow<BrowserSettings> = context.browserDataStore.data.map { preferences ->
        BrowserSettings(
            homeUrl = preferences[HOME_URL].orEmpty(),
            fullscreenEnabled = preferences[FULLSCREEN_ENABLED] ?: false,
        )
    }

    suspend fun update(homeUrl: String, fullscreenEnabled: Boolean) {
        context.browserDataStore.edit { preferences ->
            preferences[HOME_URL] = homeUrl
            preferences[FULLSCREEN_ENABLED] = fullscreenEnabled
        }
    }

    suspend fun setFullscreenEnabled(enabled: Boolean) {
        context.browserDataStore.edit { preferences ->
            preferences[FULLSCREEN_ENABLED] = enabled
        }
    }

    private companion object {
        val HOME_URL = stringPreferencesKey("home_url")
        val FULLSCREEN_ENABLED = booleanPreferencesKey("fullscreen_enabled")
    }
}
