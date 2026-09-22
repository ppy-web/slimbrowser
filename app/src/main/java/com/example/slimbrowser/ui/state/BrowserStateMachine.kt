package com.example.slimbrowser.ui.state

import com.example.slimbrowser.domain.BrowserError

sealed interface BackDecision {
    data object NavigateWebHistory : BackDecision
    data object ShowHome : BackDecision
    data class LeaveSettings(val destination: AppScene) : BackDecision
    data object ExitApplication : BackDecision
}

/**
 * Pure reducer for application scenes and browser callback state.
 *
 * Android callbacks carry the navigation id returned by [navigationStarted].
 * A callback from an older navigation is ignored, preventing a late error or
 * title update from replacing a newer page.
 */
class BrowserStateMachine(initialState: BrowserUiState = BrowserUiState()) {
    var state: BrowserUiState = initialState
        private set

    fun showHome(): BrowserUiState = update {
        copy(
            scene = AppScene.HOME,
            previousScene = if (scene == AppScene.SETTINGS) previousScene else scene,
            isLoading = false,
            progress = 0,
            toolbarVisibility = ToolbarVisibility.VISIBLE,
            error = null,
        )
    }

    /** Restores scene navigation independently of whether WebView state restoration succeeded. */
    fun restoreScene(
        scene: AppScene,
        previousScene: AppScene,
        browserUrl: String = state.url,
    ): BrowserUiState = update {
        val safePreviousScene = previousScene.takeUnless { it == AppScene.SETTINGS }
            ?: AppScene.HOME
        copy(
            scene = scene,
            previousScene = if (scene == AppScene.SETTINGS) safePreviousScene else scene,
            url = browserUrl,
            displayHost = "",
            title = "",
            faviconPath = null,
            progress = 0,
            isLoading = false,
            canGoBack = false,
            canGoForward = false,
            toolbarVisibility = ToolbarVisibility.VISIBLE,
            error = null,
        )
    }

    fun showBrowser(url: String = state.url): BrowserUiState = update {
        copy(
            scene = AppScene.BROWSER,
            previousScene = if (scene == AppScene.SETTINGS) previousScene else scene,
            url = url,
            error = null,
            toolbarVisibility = ToolbarVisibility.VISIBLE,
        )
    }

    fun showSettings(): BrowserUiState = update {
        if (scene == AppScene.SETTINGS) this
        else copy(scene = AppScene.SETTINGS, previousScene = scene)
    }

    fun closeSettings(): BrowserUiState = update {
        copy(
            scene = previousScene.takeUnless { it == AppScene.SETTINGS } ?: AppScene.HOME,
            error = null,
        )
    }

    fun backDecision(): BackDecision = when (state.scene) {
        AppScene.SETTINGS -> BackDecision.LeaveSettings(
            state.previousScene.takeUnless { it == AppScene.SETTINGS } ?: AppScene.HOME,
        )
        AppScene.BROWSER -> if (state.canGoBack) {
            BackDecision.NavigateWebHistory
        } else {
            BackDecision.ShowHome
        }
        AppScene.HOME -> BackDecision.ExitApplication
    }

    fun navigationStarted(url: String, displayHost: String): Long {
        val nextNavigationId = state.navigationId + 1L
        update {
            copy(
                scene = AppScene.BROWSER,
                url = url,
                displayHost = displayHost,
                faviconPath = null,
                progress = 0,
                isLoading = true,
                toolbarVisibility = ToolbarVisibility.VISIBLE,
                error = null,
                navigationId = nextNavigationId,
            )
        }
        return nextNavigationId
    }

    fun progressChanged(navigationId: Long, progress: Int): BrowserUiState =
        updateIfCurrent(navigationId) {
            copy(
                progress = progress.coerceIn(0, 100),
                isLoading = progress < 100,
            )
        }

    fun titleChanged(navigationId: Long, title: String): BrowserUiState =
        updateIfCurrent(navigationId) {
            copy(title = title.trim().take(MAX_TITLE_LENGTH))
        }

    fun faviconChanged(navigationId: Long, faviconPath: String?): BrowserUiState =
        updateIfCurrent(navigationId) {
            copy(faviconPath = faviconPath)
        }

    fun historyChanged(
        navigationId: Long,
        url: String,
        displayHost: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ): BrowserUiState = updateIfCurrent(navigationId) {
        copy(
            url = url,
            displayHost = displayHost,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
        )
    }

    fun navigationFinished(
        navigationId: Long,
        url: String,
        displayHost: String,
        title: String,
        canGoBack: Boolean,
        canGoForward: Boolean,
    ): BrowserUiState = updateIfCurrent(navigationId) {
        copy(
            url = url,
            lastCommittedUrl = if (error == null) url else lastCommittedUrl,
            displayHost = displayHost,
            title = title.trim().take(MAX_TITLE_LENGTH),
            progress = 100,
            isLoading = false,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
        )
    }

    fun navigationFailed(navigationId: Long, error: BrowserError): BrowserUiState =
        updateIfCurrent(navigationId) {
            copy(
                progress = 0,
                isLoading = false,
                toolbarVisibility = ToolbarVisibility.VISIBLE,
                error = error,
            )
        }

    fun rendererGone(error: BrowserError): BrowserUiState = update {
        copy(
            progress = 0,
            isLoading = false,
            toolbarVisibility = ToolbarVisibility.VISIBLE,
            error = error,
        )
    }

    fun fullscreenChanged(enabled: Boolean): BrowserUiState = update {
        copy(isFullscreen = enabled)
    }

    fun desktopModeChanged(enabled: Boolean): BrowserUiState = update {
        copy(isDesktopMode = enabled)
    }

    fun toolbarChanged(visibility: ToolbarVisibility): BrowserUiState = update {
        copy(toolbarVisibility = visibility)
    }

    private fun update(transform: BrowserUiState.() -> BrowserUiState): BrowserUiState {
        state = state.transform()
        return state
    }

    private fun updateIfCurrent(
        navigationId: Long,
        transform: BrowserUiState.() -> BrowserUiState,
    ): BrowserUiState {
        if (navigationId == state.navigationId) state = state.transform()
        return state
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 512
    }
}
