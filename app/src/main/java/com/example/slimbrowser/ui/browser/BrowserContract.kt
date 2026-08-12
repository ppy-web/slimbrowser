package com.example.slimbrowser.ui.browser

import android.os.Bundle
import com.example.slimbrowser.data.BrowserSettings

interface BrowserContract {
    interface View {
        fun renderSettings(settings: BrowserSettings)
        fun loadUrl(url: String)
        fun showSettings(settings: BrowserSettings, required: Boolean)
        fun showValidationError()
        fun applyFullscreen(enabled: Boolean)
        fun updateFullscreenButton(enabled: Boolean)
        fun showError(message: String)
        fun hideError()
        fun reloadPage()
        fun restoreWebViewState(state: Bundle): Boolean
        fun canGoBack(): Boolean
        fun goBack()
        fun finishScreen()
    }

    interface Presenter {
        fun attach(view: View, savedWebViewState: Bundle?)
        fun detach()
        fun onSettingsRequested()
        fun onSettingsSubmitted(rawUrl: String, fullscreenEnabled: Boolean)
        fun onFullscreenShortcutRequested()
        fun onBackPressed()
        fun onPageStarted()
        fun onPageFinished()
        fun onMainFrameError(message: String)
        fun onRendererGone()
        fun onRetryRequested()
    }
}
