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
        assertEquals("https://example.com/?#", UrlPolicy.normalize("https://example.com?#"))
    }

    @Test
    fun `normalizes scheme host trailing dot and idn`() {
        assertEquals(
            "https://example.com/path?q=1#section",
            UrlPolicy.normalize("HTTPS://EXAMPLE.COM./path?q=1#section"),
        )
        assertEquals(
            "https://xn--fsqu00a.xn--0zwm56d/",
            UrlPolicy.normalize("例子。测试。"),
        )
        assertEquals("https://xn--bcher-kva.example/", UrlPolicy.normalize("bücher.example"))
        assertEquals("https://xn--ypal-43d9g.com/", UrlPolicy.normalize("раypal.com"))
        assertEquals("https://example.com/", UrlPolicy.normalize("ｅｘａｍｐｌｅ.com"))
    }

    @Test
    fun `accepts every legal https port`() {
        assertEquals("https://example.com:1/", UrlPolicy.normalize("https://example.com:1"))
        assertEquals("https://example.com:443/", UrlPolicy.normalize("https://example.com:443"))
        assertEquals("https://example.com:8443/", UrlPolicy.normalize("https://example.com:8443"))
        assertEquals("https://example.com:65535/", UrlPolicy.normalize("https://example.com:65535"))
    }

    @Test
    fun `preserves raw escapes without double encoding`() {
        val escaped = "https://EXAMPLE.com/a%2Fb?q=%26#x%2Fy"
        val normalized = "https://example.com/a%2Fb?q=%26#x%2Fy"
        assertEquals(normalized, UrlPolicy.normalize(escaped))
        assertEquals(normalized, UrlPolicy.normalize(normalized))

        val alreadyEncoded =
            "https://example.com/%E4%B8%AD%E6%96%87?x=%23#f%2Fz"
        assertEquals(alreadyEncoded, UrlPolicy.normalize(alreadyEncoded))
        assertEquals(
            "https://example.com/%E8%B7%AF%E5%BE%84?q=%E4%B8%AD%E6%96%87#%E7%89%87%E6%AE%B5",
            UrlPolicy.normalize("https://example.com/路径?q=中文#片段"),
        )
    }

    @Test
    fun `normalization is idempotent for accepted urls`() {
        val inputs = listOf(
            "example.com",
            "HTTPS://EXAMPLE.COM.:65535/a/../b?x=%2F#片段",
            "https://例子.测试/路径?q=中文",
            "https://[2001:0DB8:0:0:1:0:0:1]:8443/a%2Fb",
            "https://192.0.2.1?#",
        )

        inputs.forEach { input ->
            val once = requireNotNull(UrlPolicy.normalize(input))
            assertEquals(input, once, UrlPolicy.normalize(once))
        }
    }

    @Test
    fun `accepts strict ipv4 and rejects ambiguous or malformed forms`() {
        assertEquals("https://0.0.0.0/", UrlPolicy.normalize("https://0.0.0.0"))
        assertEquals("https://255.255.255.255/", UrlPolicy.normalize("https://255.255.255.255"))
        assertEquals("https://127.0.0.1/", UrlPolicy.normalize("https://127.0.0.1."))
        assertEquals("https://127.0.0.1/", UrlPolicy.normalize("https://１２７。０。０。１"))

        listOf(
            "01.2.3.4",
            "1.02.3.4",
            "1.2.003.4",
            "256.1.1.1",
            "999.1.1.1",
            "1.2.3",
            "1.2.3.4.5",
            "2130706433",
            "0x7f.0.0.1",
            "example.123",
        ).forEach { host -> assertNull(host, UrlPolicy.normalize("https://$host")) }
    }

    @Test
    fun `validates and canonically formats ipv6`() {
        assertEquals("https://[::1]/", UrlPolicy.normalize("https://[0:0:0:0:0:0:0:1]"))
        assertEquals(
            "https://[2001:db8::1:0:0:1]/",
            UrlPolicy.normalize("https://[2001:0DB8:0:0:1:0:0:1]"),
        )
        assertEquals(
            "https://[::ffff:c000:280]/",
            UrlPolicy.normalize("https://[::ffff:192.0.2.128]"),
        )
        assertEquals(
            "https://[2001:db8::1]:65535/path",
            UrlPolicy.normalize("https://[2001:DB8::1]:65535/path"),
        )
    }

    @Test
    fun `rejects malformed ipv6 literals`() {
        listOf(
            "[1:2:3:4:5:6:7]",
            "[1:2:3:4:5:6:7:8:9]",
            "[1::2::3]",
            "[:::1]",
            "[12345::1]",
            "[gggg::1]",
            "[::ffff:192.168.001.1]",
            "[1:2:3:4:5:6:7:192.0.2.1]",
            "[192.0.2.1::]",
            "[1:192.0.2.1::]",
            "[fe80::1%25wlan0]",
            "2001:db8::1",
        ).forEach { host -> assertNull(host, UrlPolicy.normalize("https://$host")) }
    }

    @Test
    fun `rejects non https schemes credentials and invalid ports`() {
        listOf(
            "http://example.com",
            "file:///tmp/page.html",
            "javascript:alert(1)",
            "data:text/html,hello",
            "intent://example.com",
            "https://user:pass@example.com",
            "https://example.com:0",
            "https://example.com:65536",
            "https://example.com:+443",
            "https://example.com:４４３",
            "https://example.com:",
        ).forEach { input -> assertNull(input, UrlPolicy.normalize(input)) }
    }

    @Test
    fun `rejects malformed domain names and ace labels`() {
        val label64 = "a".repeat(64)
        listOf(
            ".example.com",
            "example..com",
            "example.com..",
            "-bad.example",
            "bad-.example",
            "bad_name.example",
            "xn--a.com",
            "faß.de",
            "$label64.example",
        ).forEach { host -> assertNull(host, UrlPolicy.normalize("https://$host")) }
    }

    @Test
    fun `rejects blank and control characters even outside trimmed content`() {
        assertNull(UrlPolicy.normalize(""))
        assertNull(UrlPolicy.normalize("   "))
        assertNull(UrlPolicy.normalize("\nhttps://example.com"))
        assertNull(UrlPolicy.normalize("https://example.com\r"))
        assertNull(UrlPolicy.normalize("https://example.com\n.evil.test"))
    }

    @Test
    fun `host extraction returns canonical ascii host`() {
        assertEquals("xn--fsqu00a.xn--0zwm56d", UrlPolicy.hostOf("https://例子.测试"))
        assertEquals("2001:db8::1", UrlPolicy.hostOf("https://[2001:0db8::1]"))
        assertNull(UrlPolicy.hostOf("http://example.com"))
    }

    @Test
    fun `strict normalization and lenient navigation are intentionally separate`() {
        assertTrue(UrlPolicy.isAllowed("https://example.com"))
        assertTrue(UrlPolicy.isAllowed("http://example.com"))
        assertTrue(UrlPolicy.isAllowed("file:///tmp/page.html"))
        assertTrue(UrlPolicy.isAllowed("custom://open/item"))
        assertFalse(UrlPolicy.isAllowed("https://example.com\n.evil.test"))
        assertTrue(UrlPolicy.isAllowedNavigation("https://other.example.net:8443", "https://example.com"))
        assertTrue(UrlPolicy.isAllowedNavigation("https://127.0.0.1", "https://example.com"))
    }

    @Test
    fun `lenient navigation preserves explicit protocols and supplies https for bare hosts`() {
        assertEquals("https://example.com/", UrlPolicy.normalizeForNavigation("example.com"))
        assertEquals("http://example.com", UrlPolicy.normalizeForNavigation("http://example.com"))
        assertEquals("https://user:pass@example.com", UrlPolicy.normalizeForNavigation("https://user:pass@example.com"))
        assertEquals("https://example.com/a%20copied%20path", UrlPolicy.normalizeForNavigation("https://example.com/a copied path"))
        assertEquals("file:///tmp/page.html", UrlPolicy.normalizeForNavigation("file:///tmp/page.html"))
        assertEquals("custom://open/item", UrlPolicy.normalizeForNavigation("custom://open/item"))
        assertNull(UrlPolicy.normalizeForNavigation("\ncustom://open/item"))
    }
}
