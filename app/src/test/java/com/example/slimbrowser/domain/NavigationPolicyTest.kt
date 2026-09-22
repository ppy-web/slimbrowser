package com.example.slimbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationPolicyTest {
    private val policy = NavigationPolicy()

    @Test
    fun `webview compatible protocols include local legacy and inline resources`() {
        listOf(
            "http://127.0.0.1:8080/",
            "https://192.168.1.1/",
            "file:///tmp/page.html",
            "content://com.example.provider/document/42",
            "data:text/plain,hello",
            "javascript:alert(1)",
            "about:blank",
        ).forEach { input ->
            assertTrue("$input should load in WebView", policy.decide(input) is NavigationDecision.Allow)
        }
    }

    @Test
    fun `explicit user navigation delegates arbitrary external protocols`() {
        val custom = policy.decide("custom://open/item", NavigationSource.MANUAL_INPUT)
        assertEquals(NavigationDecision.OpenExternal(ExternalAction.OpenUri("custom://open/item")), custom)

        val intent = policy.decide("intent://scan/#Intent;scheme=zxing;end", NavigationSource.LINK_CLICK)
        assertEquals(
            NavigationDecision.OpenExternal(ExternalAction.OpenUri("intent://scan/#Intent;scheme=zxing;end")),
            intent,
        )
    }

    @Test
    fun `automatic redirects also delegate valid external protocols`() {
        assertEquals(
            NavigationDecision.OpenExternal(ExternalAction.OpenUri("custom://open/item")),
            policy.decide("custom://open/item", NavigationSource.REDIRECT),
        )
        assertEquals(
            NavigationDecision.OpenExternal(ExternalAction.OpenUri("custom://open/item")),
            policy.decide("custom://open/item", NavigationSource.RESTORE),
        )
    }

    @Test
    fun `local network remains available regardless of legacy preference value`() {
        val restrictiveSetting = NavigationPolicy(allowLocalNetwork = false)
        assertEquals(
            NavigationDecision.Allow("http://127.0.0.1:8080/"),
            restrictiveSetting.decide("http://127.0.0.1:8080/", NavigationSource.MANUAL_INPUT),
        )
    }

    @Test
    fun `malformed or controlled input remains blocked`() {
        assertEquals(
            NavigationDecision.Block(NavigationBlockReason.INVALID_URL),
            policy.decide("custom://open\n/item", NavigationSource.MANUAL_INPUT),
        )
        assertEquals(
            NavigationDecision.Block(NavigationBlockReason.INVALID_URL),
            policy.decide("https://", NavigationSource.MANUAL_INPUT),
        )
    }
}
