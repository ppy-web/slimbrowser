package com.example.slimbrowser.data

/** Pure rules for keeping private browsing data out of persistent session state. */
object PrivateSessionPolicy {
    fun shouldPersistSession(settings: BrowserSettings): Boolean = !settings.privateMode

    fun shouldClearOnExit(settings: BrowserSettings, isFinishing: Boolean): Boolean =
        isFinishing && settings.privateMode && settings.clearPrivateOnExit
}
