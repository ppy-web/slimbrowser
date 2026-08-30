package com.example.slimbrowser

import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.core.view.ViewCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.slimbrowser.data.AppDatabase
import com.example.slimbrowser.data.BrowserPreferences
import com.example.slimbrowser.data.BrowserSettings
import com.example.slimbrowser.ui.browser.MainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-level smoke coverage for the two local scenes that do not require network. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun homeAndSettingsScenesAreReachable() {
        ActivityScenario.launch(MainActivity::class.java).use {
            it.onActivity { activity ->
                val homeInput = activity.findViewById<View>(R.id.homeInput)
                assertTrue(homeInput.isShown)
                activity.findViewById<View>(R.id.homeSettingsButton).performClick()
                assertEquals(
                    View.VISIBLE,
                    activity.findViewById<View>(R.id.settingsScene).visibility,
                )
                activity.findViewById<View>(R.id.settingsBackButton).performClick()
                assertTrue(homeInput.isShown)
            }
        }
    }

    @Test
    fun explicitLocalAssetUriOpensInBrowserScene() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val historyDao = AppDatabase.getInstance(targetContext).historyDao()
        runBlocking {
            BrowserPreferences(targetContext).update(BrowserSettings())
            BrowserPreferences(targetContext).clearSession()
            historyDao.clear()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.homeInput).setText(LOCAL_TEST_PAGE_URL)
                activity.findViewById<View>(R.id.homeGoButton).performClick()
            }

            var pageLoaded = false
            var faviconSaved = false
            for (attempt in 0 until LOAD_ATTEMPTS) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    pageLoaded = activity.findViewById<View>(R.id.browserScene).isShown &&
                        activity.findViewById<WebView>(R.id.webView).url == LOCAL_TEST_PAGE_URL
                }
                val historyEntry = runBlocking { historyDao.getByUrl(LOCAL_TEST_PAGE_URL) }
                faviconSaved = historyEntry?.faviconUri
                    ?.let(Uri::parse)
                    ?.path
                    ?.let(::File)
                    ?.let { it.isFile && it.length() > 0L } == true
                if (pageLoaded && faviconSaved) break
                SystemClock.sleep(LOAD_RETRY_MILLIS)
            }

            assertTrue("Local compatibility page did not load", pageLoaded)
            assertTrue("Local page favicon was not persisted with history", faviconSaved)
        }
    }

    @Test
    fun privateSessionDoesNotPersistAndClearsCookiesOnExit() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = BrowserPreferences(targetContext)
        val historyDao = AppDatabase.getInstance(targetContext).historyDao()
        runBlocking {
            preferences.update(
                BrowserSettings(
                    privateMode = true,
                    clearPrivateOnExit = true,
                ),
            )
            preferences.clearSession()
            historyDao.clear()
        }
        setTestCookie()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.homeInput).setText(LOCAL_TEST_PAGE_URL)
                activity.findViewById<View>(R.id.homeGoButton).performClick()
            }
            assertTrue("Private local page did not load", waitForLocalPage(scenario))
            SystemClock.sleep(PERSISTENCE_SETTLE_MILLIS)
            assertEquals(null, runBlocking { historyDao.getByUrl(LOCAL_TEST_PAGE_URL) })
            assertTrue(runBlocking { preferences.session.first() }.lastSafeUrl.isBlank())
        } finally {
            scenario.close()
        }

        var cookiesCleared = false
        for (attempt in 0 until CLEANUP_ATTEMPTS) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            cookiesCleared = CookieManager.getInstance().getCookie(COOKIE_TEST_URL).isNullOrBlank()
            if (cookiesCleared) break
            SystemClock.sleep(CLEANUP_RETRY_MILLIS)
        }
        try {
            assertTrue("Private cookies were not cleared after Activity exit", cookiesCleared)
            assertTrue(runBlocking { preferences.session.first() }.lastSafeUrl.isBlank())
        } finally {
            runBlocking { preferences.update(BrowserSettings()) }
        }
    }

    @Test
    fun primaryControlsExposeAccessibleTouchTargetsAndHeadings() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { BrowserPreferences(targetContext).update(BrowserSettings()) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertAccessibleTouchTarget(activity, R.id.homeGoButton)
                assertAccessibleTouchTarget(activity, R.id.homeSettingsButton)
                activity.findViewById<View>(R.id.homeSettingsButton).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertAccessibleTouchTarget(activity, R.id.settingsBackButton)
                assertTrue(ViewCompat.isAccessibilityHeading(activity.findViewById<View>(R.id.settingsTitle)))
                assertTrue(ViewCompat.isAccessibilityHeading(activity.findViewById<View>(R.id.errorTitle)))
                activity.findViewById<View>(R.id.settingsBackButton).performClick()
                activity.findViewById<EditText>(R.id.homeInput).setText(LOCAL_TEST_PAGE_URL)
                activity.findViewById<View>(R.id.homeGoButton).performClick()
            }
            assertTrue("Browser scene did not open for accessibility checks", waitForLocalPage(scenario))
            scenario.onActivity { activity ->
                listOf(
                    R.id.backButton,
                    R.id.forwardButton,
                    R.id.reloadButton,
                    R.id.moreButton,
                ).forEach { assertAccessibleTouchTarget(activity, it) }
            }
        }
    }

    private fun assertAccessibleTouchTarget(activity: MainActivity, viewId: Int) {
        val view = activity.findViewById<View>(viewId)
        val minimumPixels = (MIN_TOUCH_TARGET_DP * activity.resources.displayMetrics.density).toInt()
        assertTrue("View $viewId is narrower than 48dp", view.width >= minimumPixels)
        assertTrue("View $viewId is shorter than 48dp", view.height >= minimumPixels)
        assertFalse("View $viewId has no accessible label", view.contentDescription.isNullOrBlank())
    }

    private fun waitForLocalPage(scenario: ActivityScenario<MainActivity>): Boolean {
        var loaded = false
        for (attempt in 0 until LOAD_ATTEMPTS) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                loaded = activity.findViewById<View>(R.id.browserScene).isShown &&
                    activity.findViewById<WebView>(R.id.webView).url == LOCAL_TEST_PAGE_URL
            }
            if (loaded) break
            SystemClock.sleep(LOAD_RETRY_MILLIS)
        }
        return loaded
    }

    private fun setTestCookie() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = CookieManager.getInstance()
        val removed = CountDownLatch(1)
        instrumentation.runOnMainSync {
            manager.removeAllCookies { removed.countDown() }
        }
        assertTrue("Timed out resetting cookies", removed.await(COOKIE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        val set = CountDownLatch(1)
        instrumentation.runOnMainSync {
            manager.setCookie(COOKIE_TEST_URL, "private_test=1") { set.countDown() }
        }
        assertTrue("Timed out setting private cookie", set.await(COOKIE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        manager.flush()
        assertTrue(manager.getCookie(COOKIE_TEST_URL)?.contains("private_test=1") == true)
    }

    private companion object {
        const val LOCAL_TEST_PAGE_URL = "file:///android_asset/slimbrowser-test.html"
        const val LOAD_ATTEMPTS = 50
        const val LOAD_RETRY_MILLIS = 100L
        const val PERSISTENCE_SETTLE_MILLIS = 300L
        const val CLEANUP_ATTEMPTS = 50
        const val CLEANUP_RETRY_MILLIS = 100L
        const val COOKIE_TIMEOUT_SECONDS = 5L
        const val COOKIE_TEST_URL = "https://example.com/"
        const val MIN_TOUCH_TARGET_DP = 48
    }
}
