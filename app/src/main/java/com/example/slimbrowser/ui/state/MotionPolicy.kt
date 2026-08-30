package com.example.slimbrowser.ui.state

/** Combines the app preference with the platform-wide animator setting. */
object MotionPolicy {
    fun shouldReduce(
        userPreference: Boolean,
        systemAnimatorsEnabled: Boolean,
    ): Boolean = userPreference || !systemAnimatorsEnabled
}
