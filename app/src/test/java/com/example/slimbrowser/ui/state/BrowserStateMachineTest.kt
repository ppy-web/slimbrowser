package com.example.slimbrowser.ui.state

import com.example.slimbrowser.domain.BrowserError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserStateMachineTest {
    @Test
    fun pageFinishedDoesNotEraseAnErrorReportedForTheSameNavigation() {
        val machine = BrowserStateMachine()
        val navigationId = machine.navigationStarted("https://example.com/", "example.com")

        machine.navigationFailed(navigationId, BrowserError.Http(500))
        machine.navigationFinished(
            navigationId = navigationId,
            url = "https://example.com/",
            displayHost = "example.com",
            title = "Server error",
            canGoBack = false,
            canGoForward = false,
        )

        assertEquals(BrowserError.Http(500), machine.state.error)
    }

    @Test
    fun backRulesFollowSceneAndHistory() {
        val machine = BrowserStateMachine()
        assertEquals(BackDecision.ExitApplication, machine.backDecision())

        machine.showBrowser("https://example.com/")
        assertEquals(BackDecision.ShowHome, machine.backDecision())

        val id = machine.navigationStarted("https://example.com/", "example.com")
        machine.historyChanged(id, "https://example.com/", "example.com", true, false)
        assertEquals(BackDecision.NavigateWebHistory, machine.backDecision())

        machine.showSettings()
        assertEquals(BackDecision.LeaveSettings(AppScene.BROWSER), machine.backDecision())
        assertEquals(AppScene.BROWSER, machine.closeSettings().scene)
    }

    @Test
    fun staleCallbacksCannotReplaceNewNavigation() {
        val machine = BrowserStateMachine()
        val first = machine.navigationStarted("https://one.example/", "one.example")
        val second = machine.navigationStarted("https://two.example/", "two.example")

        machine.titleChanged(first, "stale")
        machine.navigationFailed(first, BrowserError.NavigationBlocked)
        assertEquals("https://two.example/", machine.state.url)
        assertEquals("", machine.state.title)
        assertNull(machine.state.error)
        assertTrue(machine.state.isLoading)

        machine.titleChanged(second, "current")
        assertEquals("current", machine.state.title)
    }

    @Test
    fun finishSynchronizesObservableBrowserState() {
        val machine = BrowserStateMachine()
        val id = machine.navigationStarted("https://example.com/start", "example.com")
        machine.progressChanged(id, 72)
        assertEquals(72, machine.state.progress)
        assertTrue(machine.state.isLoading)

        val state = machine.navigationFinished(
            id,
            "https://example.com/final",
            "example.com",
            "Final page",
            canGoBack = true,
            canGoForward = false,
        )
        assertEquals(100, state.progress)
        assertFalse(state.isLoading)
        assertTrue(state.canGoBack)
        assertFalse(state.canGoForward)
        assertEquals("Final page", state.title)
        assertEquals("https://example.com/final", state.lastCommittedUrl)
    }

    @Test
    fun rendererRecoveryUsesLastSuccessfullyCommittedUrl() {
        val machine = BrowserStateMachine()
        val committed = machine.navigationStarted("https://example.com/good", "example.com")
        machine.navigationFinished(
            committed,
            "https://example.com/good",
            "example.com",
            "Good page",
            canGoBack = false,
            canGoForward = false,
        )

        machine.navigationStarted("https://example.com/loading", "example.com")
        machine.rendererGone(BrowserError.RendererGone)

        assertEquals("https://example.com/loading", machine.state.url)
        assertEquals(
            "https://example.com/good",
            machine.state.recoveryUrl("https://fallback.example/"),
        )
    }

    @Test
    fun startingANewNavigationClearsThePreviousPageFavicon() {
        val machine = BrowserStateMachine()
        val first = machine.navigationStarted("https://one.example/", "one.example")
        machine.faviconChanged(first, "file:///one.png")
        assertEquals("file:///one.png", machine.state.faviconPath)

        machine.navigationStarted("https://two.example/", "two.example")

        assertNull(machine.state.faviconPath)
    }

    @Test
    fun restoringSettingsPreservesItsValidatedReturnScene() {
        val machine = BrowserStateMachine()

        val restored = machine.restoreScene(
            scene = AppScene.SETTINGS,
            previousScene = AppScene.BROWSER,
            browserUrl = "https://example.com/restored",
        )

        assertEquals(AppScene.SETTINGS, restored.scene)
        assertEquals(AppScene.BROWSER, restored.previousScene)
        assertEquals("https://example.com/restored", restored.url)
        assertEquals(BackDecision.LeaveSettings(AppScene.BROWSER), machine.backDecision())

        machine.restoreScene(AppScene.SETTINGS, AppScene.SETTINGS)
        assertEquals(AppScene.HOME, machine.state.previousScene)
    }

    @Test
    fun failedPageIsNotRecordedAsCommittedRecoveryTarget() {
        val machine = BrowserStateMachine()
        val navigationId = machine.navigationStarted("https://broken.example/", "broken.example")
        machine.navigationFailed(navigationId, BrowserError.Http(500))
        machine.navigationFinished(
            navigationId,
            "https://broken.example/",
            "broken.example",
            "Server error",
            canGoBack = false,
            canGoForward = false,
        )

        assertEquals("", machine.state.lastCommittedUrl)
        assertEquals(
            "https://fallback.example/",
            machine.state.recoveryUrl("https://fallback.example/"),
        )
    }
}
