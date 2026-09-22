package com.example.slimbrowser.data.session

import com.example.slimbrowser.data.BrowserPreferences
import com.example.slimbrowser.data.BrowserSession
import kotlinx.coroutines.flow.Flow

class BrowserSessionRepository(private val preferences: BrowserPreferences) {
    val session: Flow<BrowserSession> = preferences.session

    suspend fun update(session: BrowserSession) = preferences.updateSession(session)

    suspend fun update(
        lastSafeUrl: String,
        title: String,
        timestamp: Long,
        lastScene: String,
    ) = preferences.updateSession(lastSafeUrl, title, timestamp, lastScene)

    suspend fun clear() = preferences.clearSession()
}
