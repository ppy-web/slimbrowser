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

    override fun attach(view: BrowserContract.View, savedWebViewState: Bundle?) {
        this.view = view
        observeJob?.cancel()
        observeJob = scope.launch {
            settings = preferences.settings.first()
            currentUrl = settings.homeUrl
            view.renderSettings(settings)
            view.applyFullscreen(settings.fullscreenEnabled)
            view.updateFullscreenButton(settings.fullscreenEnabled)

            val restored = savedWebViewState != null && view.restoreWebViewState(savedWebViewState)
            when {
                restored -> Unit
                settings.homeUrl.isBlank() -> view.showSettings(settings, required = true)
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

    override fun onSettingsSubmitted(rawUrl: String, fullscreenEnabled: Boolean) {
        val normalizedUrl = UrlPolicy.normalize(rawUrl)
        if (normalizedUrl == null) {
            view?.showValidationError()
            return
        }

        val urlChanged = normalizedUrl != settings.homeUrl
        val fullscreenChanged = fullscreenEnabled != settings.fullscreenEnabled
        settings = BrowserSettings(normalizedUrl, fullscreenEnabled)
        currentUrl = normalizedUrl
        view?.renderSettings(settings)
        view?.applyFullscreen(fullscreenEnabled)
        view?.updateFullscreenButton(fullscreenEnabled)
        if (urlChanged) view?.loadUrl(normalizedUrl)
        if (urlChanged || fullscreenChanged) {
            scope.launch { preferences.update(normalizedUrl, fullscreenEnabled) }
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

    override fun onBackPressed() {
        when {
            view?.canGoBack() == true -> view?.goBack()
            else -> view?.finishScreen()
        }
    }

    override fun onPageStarted() {
        view?.hideError()
    }

    override fun onPageFinished() = Unit

    override fun onMainFrameError(message: String) {
        view?.showError(message)
    }

    override fun onRendererGone() {
        view?.showError("The web rendering process stopped. Reload the page to continue.")
    }

    override fun onRetryRequested() {
        view?.hideError()
        if (currentUrl.isNotBlank()) view?.reloadPage()
    }
}
