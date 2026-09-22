package com.example.slimbrowser.ui.browser

import android.os.Bundle
import com.example.slimbrowser.data.BrowserSettings
import com.example.slimbrowser.data.StartupMode

interface BrowserContract {
    interface View {
        fun renderSettings(settings: BrowserSettings)
        fun loadUrl(url: String)
        fun showBlankHome()
        fun showSettings(settings: BrowserSettings, required: Boolean)
        fun showValidationError()
        fun applyFullscreen(enabled: Boolean)
        fun applyTheme(darkThemeEnabled: Boolean)
        fun applyBackground(uri: String?)
        fun updateFullscreenButton(enabled: Boolean)
        fun showError(message: String)
        fun hideError()
        fun reloadPage()
        fun restoreWebViewState(state: Bundle): Boolean
        fun canGoBack(): Boolean
        fun isHomeVisible(): Boolean
        fun goBack()
        fun finishScreen()
    }

    interface Presenter {
        fun attach(view: View, savedWebViewState: Bundle?, fallbackUrl: String?)
        fun detach()
        fun onSettingsRequested()
        fun onSettingsSubmitted(
            rawUrl: String,
            fullscreenEnabled: Boolean,
            darkThemeEnabled: Boolean,
            backgroundUri: String?,
            searchEngine: String,
            startupMode: StartupMode,
            toolbarAtBottom: Boolean,
            desktopMode: Boolean,
            fontScale: Int,
            darkWebMode: Boolean,
            privateMode: Boolean,
        )
        fun onFullscreenShortcutRequested()
        fun onRefreshRequested()
        fun onBackPressed()
        fun onPageStarted(url: String)
        fun onPageFinished(url: String)
        fun onMainFrameError(message: String)
        fun onRendererGone(message: String)
        fun onRetryRequested()
    }
}
