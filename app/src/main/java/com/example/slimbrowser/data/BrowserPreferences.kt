package com.example.slimbrowser.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.browserDataStore by preferencesDataStore(name = "browser_preferences")

enum class StartBehavior {
    HOME,
    LAST_PAGE,
    BLANK,
}

enum class ToolbarPosition {
    TOP,
    BOTTOM,
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * The first four fields intentionally retain their original order so existing positional callers
 * remain source-compatible while newer settings can evolve independently.
 */
data class BrowserSettings(
    val homeUrl: String = "",
    val fullscreenEnabled: Boolean = false,
    val darkThemeEnabled: Boolean = false,
    val backgroundUri: String? = null,
    val searchEngineId: String = DEFAULT_SEARCH_ENGINE_ID,
    val startBehavior: StartBehavior = StartBehavior.HOME,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
    val toolbarAutoHide: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reducedTransparency: Boolean = false,
    val reduceMotion: Boolean = false,
    val desktopModeDefault: Boolean = false,
    val textZoom: Int = DEFAULT_TEXT_ZOOM,
    val darkWebMode: Boolean = false,
    val allowLocalNetwork: Boolean = true,
    val privateMode: Boolean = false,
    val clearPrivateOnExit: Boolean = true,
) {
    companion object {
        const val DEFAULT_SEARCH_ENGINE_ID = "baidu"
        const val DEFAULT_TEXT_ZOOM = 100
        const val MIN_TEXT_ZOOM = 50
        const val MAX_TEXT_ZOOM = 200
    }
}

data class BrowserSession(
    val lastSafeUrl: String = "",
    val title: String = "",
    val timestamp: Long = 0L,
    val lastScene: String = DEFAULT_SCENE,
) {
    companion object {
        const val DEFAULT_SCENE = "HOME"
    }
}

/** Legacy DataStore model retained until every UI consumer has migrated to Room bookmarks. */
data class Favorite(
    val title: String,
    val url: String,
)

internal object FavoriteCodec {
    fun encode(favorite: Favorite): String = Base64.getEncoder().encodeToString(
        "${favorite.title}\n${favorite.url}".toByteArray(StandardCharsets.UTF_8),
    )

    fun decode(encoded: String): Favorite? = runCatching {
        val value = String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
        val separator = value.indexOf('\n')
        require(separator > 0 && separator < value.lastIndex)
        Favorite(value.substring(0, separator), value.substring(separator + 1))
    }.getOrNull()
}

class BrowserPreferences(private val context: Context) {
    private val data: Flow<Preferences> = context.browserDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }

    val settings: Flow<BrowserSettings> = data.map { preferences ->
        val legacyDarkTheme = preferences[DARK_THEME_ENABLED] ?: false
        val themeMode = preferences[THEME_MODE].toEnumOrNull<ThemeMode>()
            ?: if (preferences.contains(DARK_THEME_ENABLED)) {
                if (legacyDarkTheme) ThemeMode.DARK else ThemeMode.LIGHT
            } else {
                ThemeMode.SYSTEM
            }
        BrowserSettings(
            homeUrl = preferences[HOME_URL].orEmpty(),
            fullscreenEnabled = preferences[FULLSCREEN_ENABLED] ?: false,
            darkThemeEnabled = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> legacyDarkTheme
            },
            backgroundUri = preferences[BACKGROUND_URI],
            searchEngineId = preferences[SEARCH_ENGINE_ID]
                ?.takeIf(String::isNotBlank)
                ?: BrowserSettings.DEFAULT_SEARCH_ENGINE_ID,
            startBehavior = preferences[START_BEHAVIOR].toEnumOrNull<StartBehavior>()
                ?: StartBehavior.HOME,
            toolbarPosition = preferences[TOOLBAR_POSITION].toEnumOrNull<ToolbarPosition>()
                ?: ToolbarPosition.BOTTOM,
            toolbarAutoHide = preferences[TOOLBAR_AUTO_HIDE] ?: true,
            themeMode = themeMode,
            reducedTransparency = preferences[REDUCED_TRANSPARENCY] ?: false,
            reduceMotion = preferences[REDUCE_MOTION] ?: false,
            desktopModeDefault = preferences[DESKTOP_MODE_DEFAULT] ?: false,
            textZoom = (preferences[TEXT_ZOOM] ?: BrowserSettings.DEFAULT_TEXT_ZOOM)
                .coerceIn(BrowserSettings.MIN_TEXT_ZOOM, BrowserSettings.MAX_TEXT_ZOOM),
            darkWebMode = preferences[DARK_WEB_MODE] ?: false,
            allowLocalNetwork = preferences[ALLOW_LOCAL_NETWORK] ?: true,
            privateMode = preferences[PRIVATE_MODE] ?: false,
            clearPrivateOnExit = preferences[CLEAR_PRIVATE_ON_EXIT] ?: true,
        )
    }

    val session: Flow<BrowserSession> = data.map { preferences ->
        BrowserSession(
            lastSafeUrl = preferences[SESSION_LAST_SAFE_URL].orEmpty(),
            title = preferences[SESSION_TITLE].orEmpty(),
            timestamp = preferences[SESSION_TIMESTAMP] ?: 0L,
            lastScene = preferences[SESSION_LAST_SCENE]
                ?.takeIf(String::isNotBlank)
                ?: BrowserSession.DEFAULT_SCENE,
        )
    }

    val favorites: Flow<List<Favorite>> = data.map { preferences ->
        preferences[FAVORITES].orEmpty().mapNotNull(FavoriteCodec::decode)
    }

    suspend fun addFavorite(url: String, title: String) {
        val favorite = Favorite(
            title = normalizeTitle(title, url),
            url = url,
        )
        val encoded = FavoriteCodec.encode(favorite)
        context.browserDataStore.edit { preferences ->
            val updated = preferences[FAVORITES].orEmpty()
                .filterNot { FavoriteCodec.decode(it)?.url == url }
                .toMutableSet()
            updated += encoded
            preferences[FAVORITES] = updated
        }
    }

    suspend fun removeFavorite(url: String) {
        context.browserDataStore.edit { preferences ->
            preferences[FAVORITES] = preferences[FAVORITES].orEmpty()
                .filterNot { FavoriteCodec.decode(it)?.url == url }
                .toSet()
        }
    }

    suspend fun getFavorites(): List<Favorite> = favorites.first()

    /** Compatibility API used by the current presenter. New code should update BrowserSettings. */
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
            preferences[THEME_MODE] = if (darkThemeEnabled) ThemeMode.DARK.name else ThemeMode.LIGHT.name
            preferences.writeNullableString(BACKGROUND_URI, backgroundUri)
        }
    }

    suspend fun update(settings: BrowserSettings) {
        context.browserDataStore.edit { preferences ->
            preferences[HOME_URL] = settings.homeUrl
            preferences[FULLSCREEN_ENABLED] = settings.fullscreenEnabled
            preferences[DARK_THEME_ENABLED] = settings.darkThemeEnabled
            preferences[SEARCH_ENGINE_ID] = settings.searchEngineId
                .takeIf(String::isNotBlank)
                ?: BrowserSettings.DEFAULT_SEARCH_ENGINE_ID
            preferences[START_BEHAVIOR] = settings.startBehavior.name
            preferences[TOOLBAR_POSITION] = settings.toolbarPosition.name
            preferences[TOOLBAR_AUTO_HIDE] = settings.toolbarAutoHide
            preferences[THEME_MODE] = settings.themeMode.name
            preferences[REDUCED_TRANSPARENCY] = settings.reducedTransparency
            preferences[REDUCE_MOTION] = settings.reduceMotion
            preferences[DESKTOP_MODE_DEFAULT] = settings.desktopModeDefault
            preferences[TEXT_ZOOM] = settings.textZoom
                .coerceIn(BrowserSettings.MIN_TEXT_ZOOM, BrowserSettings.MAX_TEXT_ZOOM)
            preferences[DARK_WEB_MODE] = settings.darkWebMode
            preferences[ALLOW_LOCAL_NETWORK] = settings.allowLocalNetwork
            preferences[PRIVATE_MODE] = settings.privateMode
            preferences[CLEAR_PRIVATE_ON_EXIT] = settings.clearPrivateOnExit
            preferences.writeNullableString(BACKGROUND_URI, settings.backgroundUri)
        }
    }

    suspend fun setFullscreenEnabled(enabled: Boolean) {
        context.browserDataStore.edit { preferences ->
            preferences[FULLSCREEN_ENABLED] = enabled
        }
    }

    suspend fun updateSession(session: BrowserSession) {
        context.browserDataStore.edit { preferences ->
            preferences.writeNullableString(SESSION_LAST_SAFE_URL, session.lastSafeUrl)
            preferences.writeNullableString(SESSION_TITLE, session.title)
            preferences[SESSION_TIMESTAMP] = session.timestamp
            preferences[SESSION_LAST_SCENE] = session.lastScene
                .takeIf(String::isNotBlank)
                ?: BrowserSession.DEFAULT_SCENE
        }
    }

    suspend fun updateSession(
        lastSafeUrl: String,
        title: String,
        timestamp: Long,
        lastScene: String,
    ) = updateSession(BrowserSession(lastSafeUrl, title, timestamp, lastScene))

    suspend fun clearSession() {
        context.browserDataStore.edit { preferences ->
            preferences.remove(SESSION_LAST_SAFE_URL)
            preferences.remove(SESSION_TITLE)
            preferences.remove(SESSION_TIMESTAMP)
            preferences.remove(SESSION_LAST_SCENE)
        }
    }

    suspend fun isLegacyFavoritesMigrationComplete(): Boolean =
        data.map { it[LEGACY_FAVORITES_MIGRATED] ?: false }.first()

    suspend fun markLegacyFavoritesMigrationComplete() {
        context.browserDataStore.edit { preferences ->
            preferences[LEGACY_FAVORITES_MIGRATED] = true
        }
    }

    private fun MutablePreferences.writeNullableString(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        if (value.isNullOrBlank()) remove(key) else this[key] = value
    }

    private companion object {
        val HOME_URL = stringPreferencesKey("home_url")
        val FULLSCREEN_ENABLED = booleanPreferencesKey("fullscreen_enabled")
        val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        val BACKGROUND_URI = stringPreferencesKey("background_uri")
        val FAVORITES = stringSetPreferencesKey("favorites")

        val SEARCH_ENGINE_ID = stringPreferencesKey("search_engine_id")
        val START_BEHAVIOR = stringPreferencesKey("start_behavior")
        val TOOLBAR_POSITION = stringPreferencesKey("toolbar_position")
        val TOOLBAR_AUTO_HIDE = booleanPreferencesKey("toolbar_auto_hide")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val REDUCED_TRANSPARENCY = booleanPreferencesKey("reduced_transparency")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val DESKTOP_MODE_DEFAULT = booleanPreferencesKey("desktop_mode_default")
        val TEXT_ZOOM = intPreferencesKey("text_zoom")
        val DARK_WEB_MODE = booleanPreferencesKey("dark_web_mode")
        val ALLOW_LOCAL_NETWORK = booleanPreferencesKey("allow_local_network")
        val PRIVATE_MODE = booleanPreferencesKey("private_mode")
        val CLEAR_PRIVATE_ON_EXIT = booleanPreferencesKey("clear_private_on_exit")

        val SESSION_LAST_SAFE_URL = stringPreferencesKey("session_last_safe_url")
        val SESSION_TITLE = stringPreferencesKey("session_title")
        val SESSION_TIMESTAMP = longPreferencesKey("session_timestamp")
        val SESSION_LAST_SCENE = stringPreferencesKey("session_last_scene")
        val LEGACY_FAVORITES_MIGRATED = booleanPreferencesKey("legacy_favorites_migrated_to_room")

        fun normalizeTitle(title: String, fallbackUrl: String): String = title.trim()
            .replace(Regex("\\s+"), " ")
            .take(120)
            .ifBlank { fallbackUrl }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
