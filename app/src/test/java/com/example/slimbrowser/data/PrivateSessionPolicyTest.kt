package com.example.slimbrowser.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateSessionPolicyTest {
    @Test
    fun `private mode never persists a restorable page`() {
        assertFalse(PrivateSessionPolicy.shouldPersistSession(BrowserSettings(privateMode = true)))
        assertTrue(PrivateSessionPolicy.shouldPersistSession(BrowserSettings(privateMode = false)))
    }

    @Test
    fun `private data is cleared only when a private activity is actually finishing`() {
        val privateSettings = BrowserSettings(privateMode = true, clearPrivateOnExit = true)
        assertTrue(PrivateSessionPolicy.shouldClearOnExit(privateSettings, isFinishing = true))
        assertFalse(PrivateSessionPolicy.shouldClearOnExit(privateSettings, isFinishing = false))
        assertFalse(
            PrivateSessionPolicy.shouldClearOnExit(
                BrowserSettings(privateMode = true, clearPrivateOnExit = false),
                isFinishing = true,
            ),
        )
    }
}
