package com.example.slimbrowser

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.slimbrowser.ui.browser.MainActivity
import org.junit.Assert.assertEquals
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
                val homeInput = activity.findViewById<android.view.View>(R.id.homeInput)
                assertTrue(homeInput.isShown)
                activity.findViewById<android.view.View>(R.id.homeSettingsButton).performClick()
                assertEquals(
                    android.view.View.VISIBLE,
                    activity.findViewById<android.view.View>(R.id.settingsScene).visibility,
                )
                activity.findViewById<android.view.View>(R.id.settingsBackButton).performClick()
                assertTrue(homeInput.isShown)
            }
        }
    }
}
