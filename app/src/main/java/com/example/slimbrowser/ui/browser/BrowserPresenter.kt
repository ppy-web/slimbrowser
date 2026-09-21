package com.example.slimbrowser.ui.browser

import android.os.Bundle
import com.example.slimbrowser.data.BrowserPreferences
import com.example.slimbrowser.data.BrowserSettings
import com.example.slimbrowser.domain.UrlPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BrowserPresenter(
    private val preferences: BrowserPreferences,
    private val scope: CoroutineScope,
) : BrowserContract.Presenter {
    private var view: BrowserContract.View? = null
    private var settings = BrowserSettings()
    private var currentUrl = ""
    private var observeJob: Job? = null

    override fun attach(view: BrowserContract.View, savedWebViewState: Bundle?, fallbackUrl: String?) {
        this.view = view
        observeJob?.cancel()
        observeJob = scope.launch {
            settings = preferences.settings.first()
            val persistedSessionUrl = preferences.lastSafeUrl.first()
                .takeIf { it.isNotBlank() && UrlPolicy.isAllowedNavigation(it, settings.homeUrl) }
            currentUrl = persistedSessionUrl ?: settings.homeUrl
            view.renderSettings(settings)
            view.applyFullscreen(settings.fullscreenEnabled)
            view.applyTheme(settings.darkThemeEnabled)
            view.applyBackground(settings.backgroundUri)
            view.updateFullscreenButton(settings.fullscreenEnabled)

            val restored = settings.homeUrl.isNotBlank() &&
                savedWebViewState != null && view.restoreWebViewState(savedWebViewState)
            when {
                restored -> Unit
                !fallbackUrl.isNullOrBlank() && UrlPolicy.isAllowedNavigation(fallbackUrl, settings.homeUrl) ->
                    view.loadUrl(fallbackUrl)
                !persistedSessionUrl.isNullOrBlank() -> view.loadUrl(persistedSessionUrl)
                settings.homeUrl.isBlank() -> view.showBlankHome()
                else -> view.loadUrl(settings.homeUrl)
            }
        }
    }

    override fun detach() {
        observeJob?.cancel()
        observeJob = null
        view = null
    }

    override fun onSettingsRequested() {
        view?.showSettings(settings, required = false)
    }

    override fun onSettingsSubmitted(
        rawUrl: String,
        fullscreenEnabled: Boolean,
        darkThemeEnabled: Boolean,
        backgroundUri: String?,
    ) {
        val normalizedUrl = if (rawUrl.trim().isEmpty()) "" else UrlPolicy.normalize(rawUrl)
        if (normalizedUrl == null) {
            view?.showValidationError()
            return
        }

        val urlChanged = normalizedUrl != settings.homeUrl
        val fullscreenChanged = fullscreenEnabled != settings.fullscreenEnabled
        val themeChanged = darkThemeEnabled != settings.darkThemeEnabled
        val backgroundChanged = backgroundUri != settings.backgroundUri
        settings = BrowserSettings(normalizedUrl, fullscreenEnabled, darkThemeEnabled, backgroundUri)
        currentUrl = normalizedUrl
        view?.renderSettings(settings)
        view?.applyFullscreen(fullscreenEnabled)
        view?.applyTheme(darkThemeEnabled)
        view?.applyBackground(backgroundUri)
        view?.updateFullscreenButton(fullscreenEnabled)
        if (urlChanged) {
            if (normalizedUrl.isBlank()) view?.showBlankHome() else view?.loadUrl(normalizedUrl)
        }
        if (urlChanged || fullscreenChanged || themeChanged || backgroundChanged) {
            scope.launch {
                preferences.update(normalizedUrl, fullscreenEnabled, darkThemeEnabled, backgroundUri)
                if (urlChanged) preferences.setLastSafeUrl(normalizedUrl.takeUnless { it.isBlank() })
            }
        }
    }

    override fun onFullscreenShortcutRequested() {
        val enabled = !settings.fullscreenEnabled
        settings = settings.copy(fullscreenEnabled = enabled)
        view?.renderSettings(settings)
        view?.applyFullscreen(enabled)
        view?.updateFullscreenButton(enabled)
        scope.launch { preferences.setFullscreenEnabled(enabled) }
    }

    override fun onRefreshRequested() {
        view?.reloadPage()
    }

    override fun onBackPressed() {
        when {
            view?.canGoBack() == true -> view?.goBack()
            else -> view?.finishScreen()
        }
    }

    override fun onPageStarted(url: String) {
        currentUrl = url
        view?.hideError()
        persistSafeUrl(url)
    }

    override fun onPageFinished(url: String) {
        currentUrl = url
        persistSafeUrl(url)
    }

    override fun onMainFrameError(message: String) {
        view?.showError(message)
    }

    override fun onRendererGone(message: String) {
        view?.showError(message)
    }

    override fun onRetryRequested() {
        view?.hideError()
        if (currentUrl.isNotBlank() && currentUrl != "about:blank") {
            // A renderer replacement creates a fresh WebView with no URL, so reload()
            // alone would be a no-op. Loading the policy-approved URL handles both
            // ordinary errors and renderer recovery.
            view?.loadUrl(currentUrl)
        } else {
            view?.reloadPage()
        }
    }

    private fun persistSafeUrl(url: String) {
        if (url == "about:blank" || !UrlPolicy.isAllowedNavigation(url, settings.homeUrl)) return
        scope.launch { preferences.setLastSafeUrl(UrlPolicy.normalize(url)) }
    }
}
