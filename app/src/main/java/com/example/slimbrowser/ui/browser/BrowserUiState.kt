package com.example.slimbrowser.ui.browser

import com.example.slimbrowser.domain.BrowserError

enum class AppScene { HOME, BROWSER, SETTINGS }

data class BrowserUiState(
    val scene: AppScene = AppScene.HOME,
    val url: String = "",
    val displayHost: String = "",
    val title: String = "",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isFullscreen: Boolean = false,
    val toolbarVisible: Boolean = true,
    val error: BrowserError? = null,
)
