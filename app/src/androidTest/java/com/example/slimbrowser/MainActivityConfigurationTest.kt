package com.example.slimbrowser

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.slimbrowser.data.BrowserPreferences
import com.example.slimbrowser.data.BrowserSettings
import com.example.slimbrowser.ui.browser.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityConfigurationTest {
    @Test
    fun homeSettingsAndBrowserScenesSurviveActivityRecreation() {
        resetSettings()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.homeSettingsButton).performClick()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.settingsScene).isShown)
                activity.findViewById<View>(R.id.settingsBackButton).performClick()
                assertTrue(activity.findViewById<View>(R.id.homeScene).isShown)
                activity.findViewById<EditText>(R.id.homeInput).setText(LOCAL_TEST_PAGE_URL)
                activity.findViewById<View>(R.id.homeGoButton).performClick()
            }
            assertTrue(waitForBrowserPage(scenario))

            scenario.recreate()

            assertTrue(waitForBrowserPage(scenario))
        }
    }

    @Test
    fun settingsSceneSurvivesLandscapeAndPortraitConfigurationChanges() {
        resetSettings()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.homeSettingsButton).performClick()
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            assertTrue(waitForOrientation(scenario, Configuration.ORIENTATION_LANDSCAPE))
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.settingsScene).isShown)
                assertTrue(activity.findViewById<View>(R.id.settingsScroll).canScrollVertically(1))
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            assertTrue(waitForOrientation(scenario, Configuration.ORIENTATION_PORTRAIT))
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<View>(R.id.settingsScene).isShown)
            }
        }
    }

    private fun resetSettings() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { BrowserPreferences(targetContext).update(BrowserSettings()) }
    }

    private fun waitForBrowserPage(scenario: ActivityScenario<MainActivity>): Boolean {
        var restored = false
        for (attempt in 0 until POLL_ATTEMPTS) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                restored = activity.findViewById<View>(R.id.browserScene).isShown &&
                    activity.findViewById<WebView>(R.id.webView).url == LOCAL_TEST_PAGE_URL
            }
            if (restored) break
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        return restored
    }

    private fun waitForOrientation(
        scenario: ActivityScenario<MainActivity>,
        expectedOrientation: Int,
    ): Boolean {
        var actualOrientation = Configuration.ORIENTATION_UNDEFINED
        for (attempt in 0 until POLL_ATTEMPTS) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                actualOrientation = activity.resources.configuration.orientation
            }
            if (actualOrientation == expectedOrientation) break
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        assertEquals(expectedOrientation, actualOrientation)
        return actualOrientation == expectedOrientation
    }

    private companion object {
        const val LOCAL_TEST_PAGE_URL = "file:///android_asset/slimbrowser-test.html"
        const val POLL_ATTEMPTS = 50
        const val POLL_INTERVAL_MILLIS = 100L
    }
}
