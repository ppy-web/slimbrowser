package com.example.slimbrowser.ui.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {
    @Test
    fun userPreferenceAlwaysReducesMotion() {
        assertTrue(MotionPolicy.shouldReduce(userPreference = true, systemAnimatorsEnabled = true))
        assertTrue(MotionPolicy.shouldReduce(userPreference = true, systemAnimatorsEnabled = false))
    }

    @Test
    fun disabledSystemAnimatorsReduceMotionWithoutAppOverride() {
        assertTrue(MotionPolicy.shouldReduce(userPreference = false, systemAnimatorsEnabled = false))
    }

    @Test
    fun motionRemainsEnabledOnlyWhenBothPoliciesAllowIt() {
        assertFalse(MotionPolicy.shouldReduce(userPreference = false, systemAnimatorsEnabled = true))
    }
}
