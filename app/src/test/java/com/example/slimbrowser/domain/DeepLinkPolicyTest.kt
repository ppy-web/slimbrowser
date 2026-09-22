package com.example.slimbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkPolicyTest {

    @Test
    fun `deep link source has user gesture`() {
        assertTrue(NavigationSource.DEEP_LINK.hasUserGesture)
    }

    @Test
    fun `https URLs allowed as deep link navigation`() {
        val policy = NavigationPolicy()
        val decision = policy.decide("https://www.slimbrowser.com/page", NavigationSource.DEEP_LINK)
        assertTrue("HTTPS URL should be allowed via deep link", decision is NavigationDecision.Allow)
    }

    @Test
    fun `http URLs allowed as deep link navigation`() {
        val policy = NavigationPolicy()
        val decision = policy.decide("http://example.com/path", NavigationSource.DEEP_LINK)
        assertTrue("HTTP URL should be allowed via deep link", decision is NavigationDecision.Allow)
    }

    @Test
    fun `deep link with user gesture allows external schemes`() {
        val policy = NavigationPolicy()
        val decision = policy.decide("custom://open/item", NavigationSource.DEEP_LINK)
        assertTrue(
            "Custom scheme should be delegated externally via deep link",
            decision is NavigationDecision.OpenExternal,
        )
    }
}
