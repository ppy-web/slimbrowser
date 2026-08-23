package com.example.slimbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinkPolicyTest {
    @Test
    fun `telephone becomes a canonical dial-only action`() {
        val action = ExternalLinkPolicy.resolve("TEL:+1-(202)-555-0123")
        assertEquals(ExternalAction.Dial("tel:+12025550123"), action)
        assertFalse(action.requiresConfirmation)

        assertEquals(ExternalAction.OpenUri("tel:*123%23"), ExternalLinkPolicy.resolve("tel:*123%23"))
        assertEquals(ExternalAction.OpenUri("tel://12025550123"), ExternalLinkPolicy.resolve("tel://12025550123"))
    }

    @Test
    fun `mailto is limited to send-to headers and rejects injection`() {
        assertEquals(
            ExternalAction.SendEmail("mailto:user@example.com?subject=Hello&body=World"),
            ExternalLinkPolicy.resolve("MAILTO:user@example.com?subject=Hello&body=World"),
        )
        assertEquals(
            ExternalAction.OpenUri("mailto:user@example.com?attach=file"),
            ExternalLinkPolicy.resolve("mailto:user@example.com?attach=file"),
        )
        assertRejected("mailto:user@example.com?subject=ok%0D%0ABcc:x@y.test", ExternalLinkRejection.INVALID_TARGET)
        assertEquals(ExternalAction.OpenUri("mailto:not-an-address"), ExternalLinkPolicy.resolve("mailto:not-an-address"))
    }

    @Test
    fun `sms is limited to one target and body`() {
        assertEquals(
            ExternalAction.SendSms("sms:+12025550123?body=Hello%20there"),
            ExternalLinkPolicy.resolve("sms:+1-202-555-0123?body=Hello%20there"),
        )
        assertEquals(ExternalAction.OpenUri("sms:+12025550123?subject=no"), ExternalLinkPolicy.resolve("sms:+12025550123?subject=no"))
        assertRejected("sms:+12025550123?body=bad%0Avalue", ExternalLinkRejection.INVALID_TARGET)
    }

    @Test
    fun `geo and market actions always require confirmation`() {
        val geo = ExternalLinkPolicy.resolve("geo:31.2304,121.4737?q=Shanghai")
        assertEquals(ExternalAction.OpenGeo("geo:31.2304,121.4737?q=Shanghai"), geo)
        assertTrue(geo.requiresConfirmation)
        assertEquals(ExternalAction.OpenUri("geo:91,0"), ExternalLinkPolicy.resolve("geo:91,0"))

        val market = ExternalLinkPolicy.resolve("market://details?id=com.example.app")
        assertEquals(ExternalAction.OpenMarket("market://details?id=com.example.app"), market)
        assertTrue(market.requiresConfirmation)
    }

    @Test
    fun `intent and unknown protocols are delegated while malformed input is rejected`() {
        assertEquals(
            ExternalAction.OpenUri("intent://scan/#Intent;scheme=zxing;end"),
            ExternalLinkPolicy.resolve("intent://scan/#Intent;scheme=zxing;end"),
        )
        assertEquals(ExternalAction.OpenUri("custom://example"), ExternalLinkPolicy.resolve("custom://example"))
        assertEquals(ExternalAction.OpenUri("https://example.com"), ExternalLinkPolicy.resolve("https://example.com"))
        assertRejected("not a uri", ExternalLinkRejection.MALFORMED_URI)
        assertRejected("tel:+12025550123\n", ExternalLinkRejection.CONTROL_CHARACTER)
        assertRejected("", ExternalLinkRejection.EMPTY)
    }

    private fun assertRejected(input: String, reason: ExternalLinkRejection) {
        assertEquals(ExternalAction.Reject(reason), ExternalLinkPolicy.resolve(input))
    }
}
