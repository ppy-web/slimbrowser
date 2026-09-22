package com.example.slimbrowser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserErrorTest {
    @Test
    fun `maps stable integer load codes without Android dependencies`() {
        assertEquals(
            BrowserError.DnsFailure,
            BrowserErrorMapper.fromWebViewError(BrowserLoadErrorCode.HOST_LOOKUP),
        )
        assertEquals(
            BrowserError.Timeout,
            BrowserErrorMapper.fromWebViewError(BrowserLoadErrorCode.TIMEOUT),
        )
        assertEquals(
            BrowserError.TlsFailure,
            BrowserErrorMapper.fromWebViewError(BrowserLoadErrorCode.FAILED_SSL_HANDSHAKE),
        )
        assertEquals(
            BrowserError.SafeBrowsingBlocked,
            BrowserErrorMapper.fromWebViewError(BrowserLoadErrorCode.UNSAFE_RESOURCE),
        )
        assertEquals(
            BrowserError.NavigationBlocked,
            BrowserErrorMapper.fromWebViewError(BrowserLoadErrorCode.BAD_URL),
        )
        assertEquals(
            BrowserError.Unknown(BrowserLoadErrorCode.CONNECT),
            BrowserErrorMapper.fromWebViewError(BrowserLoadErrorCode.CONNECT),
        )
    }

    @Test
    fun `network state refines network related failures to offline`() {
        listOf(
            BrowserLoadErrorCode.HOST_LOOKUP,
            BrowserLoadErrorCode.CONNECT,
            BrowserLoadErrorCode.IO,
            BrowserLoadErrorCode.TIMEOUT,
        ).forEach { code ->
            assertEquals(
                BrowserError.Offline,
                BrowserErrorMapper.fromWebViewError(code, isNetworkAvailable = false),
            )
        }
        assertEquals(
            BrowserError.TlsFailure,
            BrowserErrorMapper.fromWebViewError(
                BrowserLoadErrorCode.FAILED_SSL_HANDSHAKE,
                isNetworkAvailable = false,
            ),
        )
    }

    @Test
    fun `maps http status policy and renderer errors`() {
        assertEquals(BrowserError.Http(404), BrowserErrorMapper.fromHttpStatus(404))
        assertEquals(BrowserError.Http(503), BrowserErrorMapper.fromHttpStatus(503))
        assertEquals(BrowserError.Unknown(999), BrowserErrorMapper.fromHttpStatus(999))
        assertEquals(BrowserError.RendererGone, BrowserErrorMapper.rendererGone())
        assertEquals(BrowserError.TlsFailure, BrowserErrorMapper.tlsFailure())
        assertEquals(BrowserError.SafeBrowsingBlocked, BrowserErrorMapper.safeBrowsingBlocked())

        assertNull(
            BrowserErrorMapper.fromNavigationDecision(NavigationDecision.Allow("https://example.com/")),
        )
        assertEquals(
            BrowserError.NavigationBlocked,
            BrowserErrorMapper.fromNavigationDecision(
                NavigationDecision.Block(NavigationBlockReason.INVALID_URL),
            ),
        )
    }
}
