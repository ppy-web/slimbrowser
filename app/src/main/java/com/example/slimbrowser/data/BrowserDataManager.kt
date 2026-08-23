package com.example.slimbrowser.data

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.example.slimbrowser.data.history.HistoryRepository
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class BrowsingDataSelection(
    val cache: Boolean = false,
    val cookies: Boolean = false,
    val webStorage: Boolean = false,
    val history: Boolean = false,
    val formData: Boolean = false,
    val session: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !cache && !cookies && !webStorage && !history && !formData && !session

    companion object {
        val ALL = BrowsingDataSelection(
            cache = true,
            cookies = true,
            webStorage = true,
            history = true,
            formData = true,
            session = true,
        )
    }
}

data class BrowsingDataClearResult(
    val selection: BrowsingDataSelection,
    val cookiesRemoved: Boolean? = null,
)

/**
 * Centralizes destructive browser-data operations and waits for asynchronous
 * cookie deletion before reporting completion. Bookmarks are intentionally not
 * part of [BrowsingDataSelection.ALL].
 */
class BrowserDataManager(
    private val preferences: BrowserPreferences,
    private val historyRepository: HistoryRepository,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
    private val webStorage: WebStorage = WebStorage.getInstance(),
) {
    suspend fun clear(
        selection: BrowsingDataSelection,
        webView: WebView,
    ): BrowsingDataClearResult {
        require(!selection.isEmpty)
        return withContext(Dispatchers.Main.immediate) {
            val cookiesRemoved = if (selection.cookies) removeAllCookiesAndWait() else null
            if (selection.cache) webView.clearCache(true)
            if (selection.webStorage) webStorage.deleteAllData()
            if (selection.history) {
                webView.clearHistory()
                historyRepository.clear()
            }
            if (selection.formData) webView.clearFormData()
            if (selection.session) preferences.clearSession()
            BrowsingDataClearResult(selection, cookiesRemoved)
        }
    }

    private suspend fun removeAllCookiesAndWait(): Boolean = suspendCancellableCoroutine { continuation ->
        cookieManager.removeAllCookies { removed ->
            cookieManager.flush()
            if (continuation.isActive) continuation.resume(removed)
        }
    }
}
