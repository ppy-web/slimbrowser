package com.example.slimbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {
    @Test
    fun `adds https scheme and root path`() {
        assertEquals("https://example.com/", UrlPolicy.normalize("example.com"))
    }

    @Test
    fun `normalizes scheme and host case`() {
        assertEquals(
            "https://example.com/path?q=1#section",
            UrlPolicy.normalize("HTTPS://EXAMPLE.COM/path?q=1#section"),
        )
    }

    @Test
    fun `accepts explicit 443 and international host`() {
        assertEquals("https://example.com:443/", UrlPolicy.normalize("https://example.com:443"))
        assertEquals("https://xn--fsqu00a.xn--0zwm56d/", UrlPolicy.normalize("例子.测试"))
    }

    @Test
    fun `rejects non https schemes`() {
        assertNull(UrlPolicy.normalize("http://example.com"))
        assertNull(UrlPolicy.normalize("file:///tmp/page.html"))
        assertNull(UrlPolicy.normalize("javascript:alert(1)"))
        assertNull(UrlPolicy.normalize("data:text/html,hello"))
    }

    @Test
    fun `rejects credentials localhost malformed hosts and non 443 ports`() {
        assertNull(UrlPolicy.normalize("https://user:pass@example.com"))
        assertNull(UrlPolicy.normalize("https://localhost"))
        assertNull(UrlPolicy.normalize("https://intranet"))
        assertNull(UrlPolicy.normalize("https://-bad.example"))
        assertNull(UrlPolicy.normalize("https://example.com:8443"))
        assertNull(UrlPolicy.normalize("https://999.1.1.1"))
    }

    @Test
    fun `rejects blank and control characters`() {
        assertNull(UrlPolicy.normalize(""))
        assertNull(UrlPolicy.normalize("   "))
        assertNull(UrlPolicy.normalize("https://example.com\n.evil.test"))
    }

    @Test
    fun `isAllowed mirrors normalization`() {
        assertTrue(UrlPolicy.isAllowed("https://example.com"))
        assertFalse(UrlPolicy.isAllowed("http://example.com"))
    }
}
