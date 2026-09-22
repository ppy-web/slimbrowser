package com.example.slimbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserErrorClassifierTest {
    @Test fun `classifies network and HTTP errors`() {
        assertEquals(BrowserError.Offline, BrowserErrorClassifier.fromDescription("网络连接失败"))
        assertEquals(BrowserError.Http(503), BrowserErrorClassifier.fromDescription("服务器返回 HTTP 503。"))
        assertEquals(BrowserError.TlsBlocked, BrowserErrorClassifier.fromDescription("证书无效"))
    }
}
