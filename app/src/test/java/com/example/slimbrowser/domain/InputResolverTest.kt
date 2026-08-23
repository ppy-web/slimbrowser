package com.example.slimbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InputResolverTest {
    @Test
    fun `resolves domain ipv4 idn and explicit https as urls`() {
        assertEquals(InputResolution.Url("https://example.com/"), InputResolver.resolve(" example.com "))
        assertEquals(
            InputResolution.Url("https://example.com:8443/path"),
            InputResolver.resolve("example.com:8443/path"),
        )
        assertEquals(InputResolution.Url("https://192.0.2.1/"), InputResolver.resolve("192.0.2.1"))
        assertEquals(
            InputResolution.Url("https://xn--fsqu00a.xn--0zwm56d/"),
            InputResolver.resolve("例子.测试"),
        )
        assertEquals(
            InputResolution.Url("https://[2001:db8::1]/"),
            InputResolver.resolve("https://[2001:db8::1]"),
        )
    }

    @Test
    fun `resolves chinese english and spaced phrases as encoded searches`() {
        assertEquals(
            InputResolution.Search("https://www.baidu.com/s?wd=android+webview", "android webview"),
            InputResolver.resolve("  android webview  "),
        )
        assertEquals(
            InputResolution.Search(
                "https://www.baidu.com/s?wd=%E5%AE%89%E5%8D%93+WebView",
                "安卓 WebView",
            ),
            InputResolver.resolve("安卓 WebView"),
        )
        assertEquals(
            InputResolution.Search("https://www.baidu.com/s?wd=browser", "browser"),
            InputResolver.resolve("browser"),
        )
    }

    @Test
    fun `uses selected built in search engine`() {
        assertEquals(
            InputResolution.Search("https://www.bing.com/search?q=kotlin+flow", "kotlin flow"),
            InputResolver.resolve("kotlin flow", SearchEngine.BING),
        )
        assertEquals(
            InputResolution.Search("https://duckduckgo.com/?q=kotlin+flow", "kotlin flow"),
            InputResolver.resolve("kotlin flow", SearchEngine.DUCKDUCKGO),
        )
        assertEquals(SearchEngine.BAIDU, SearchEngine.DEFAULT)
        assertEquals(SearchEngine.DUCKDUCKGO, SearchEngine.fromId("DuckDuckGo"))
        assertEquals(SearchEngine.BAIDU, SearchEngine.fromId("missing"))
    }

    @Test
    fun `keeps explicit protocols and unusual addresses as navigation instead of a search`() {
        assertEquals(
            InputResolution.Url("http://example.com"),
            InputResolver.resolve("http://example.com"),
        )
        assertEquals(
            InputResolution.Url("javascript:alert(1)"),
            InputResolver.resolve("javascript:alert(1)"),
        )
        assertEquals(
            InputResolution.Url("custom://example"),
            InputResolver.resolve("custom://example"),
        )
        assertEquals(
            InputResolution.Url("https://user:pass@example.com"),
            InputResolver.resolve("https://user:pass@example.com"),
        )
        assertEquals(
            InputResolution.Url("https://999.1.1.1"),
            InputResolver.resolve("999.1.1.1"),
        )
    }

    @Test
    fun `reports empty and control character input`() {
        assertEquals(InputResolution.Invalid(InputError.Empty), InputResolver.resolve("   "))
        assertEquals(
            InputResolution.Invalid(InputError.ContainsControlCharacters),
            InputResolver.resolve("example.com\n"),
        )
        assertTrue(InputResolver.resolve("hello world") is InputResolution.Search)
    }
}
