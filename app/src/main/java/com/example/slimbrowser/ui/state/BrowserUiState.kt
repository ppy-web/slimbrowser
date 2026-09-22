package com.example.slimbrowser.ui.state

import com.example.slimbrowser.domain.BrowserError

data class BrowserUiState(
    val scene: AppScene = AppScene.HOME,
    val previousScene: AppScene = AppScene.HOME,
    val url: String = "",
    val lastCommittedUrl: String = "",
    val displayHost: String = "",
    val title: String = "",
    val faviconPath: String? = null,
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isFullscreen: Boolean = false,
    val isDesktopMode: Boolean = false,
    val toolbarVisibility: ToolbarVisibility = ToolbarVisibility.VISIBLE,
    val error: BrowserError? = null,
    val navigationId: Long = 0L,
) {
    val isSecure: Boolean
        get() = url.startsWith("https://", ignoreCase = true)

    /**
     * Renderer recovery must not reload an address that only reached
     * onPageStarted. Prefer the most recent successfully committed page and
     * let the caller supply the persisted-session/home fallback.
     */
    fun recoveryUrl(fallbackUrl: String): String = lastCommittedUrl.ifBlank { fallbackUrl }
}
