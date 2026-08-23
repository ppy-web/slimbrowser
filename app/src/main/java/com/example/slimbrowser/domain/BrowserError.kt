package com.example.slimbrowser.domain

sealed interface BrowserError {
    data object Offline : BrowserError
    data object DnsFailure : BrowserError
    data object TlsFailure : BrowserError
    data object SafeBrowsingBlocked : BrowserError
    data class Http(val statusCode: Int) : BrowserError
    data object NavigationBlocked : BrowserError
    data object Timeout : BrowserError
    data object RendererGone : BrowserError
    data class Unknown(val diagnosticCode: Int?) : BrowserError
}

/** Integer values mirror the stable WebView load-error contract without importing Android. */
object BrowserLoadErrorCode {
    const val UNKNOWN = -1
    const val HOST_LOOKUP = -2
    const val UNSUPPORTED_AUTH_SCHEME = -3
    const val AUTHENTICATION = -4
    const val PROXY_AUTHENTICATION = -5
    const val CONNECT = -6
    const val IO = -7
    const val TIMEOUT = -8
    const val REDIRECT_LOOP = -9
    const val UNSUPPORTED_SCHEME = -10
    const val FAILED_SSL_HANDSHAKE = -11
    const val BAD_URL = -12
    const val FILE = -13
    const val FILE_NOT_FOUND = -14
    const val TOO_MANY_REQUESTS = -15
    const val UNSAFE_RESOURCE = -16
}

object BrowserErrorMapper {
    fun fromWebViewError(
        errorCode: Int,
        isNetworkAvailable: Boolean? = null,
    ): BrowserError {
        if (isNetworkAvailable == false && errorCode in NETWORK_RELATED_CODES) {
            return BrowserError.Offline
        }

        return when (errorCode) {
            BrowserLoadErrorCode.HOST_LOOKUP -> BrowserError.DnsFailure
            BrowserLoadErrorCode.TIMEOUT -> BrowserError.Timeout
            BrowserLoadErrorCode.FAILED_SSL_HANDSHAKE -> BrowserError.TlsFailure
            BrowserLoadErrorCode.UNSAFE_RESOURCE -> BrowserError.SafeBrowsingBlocked
            BrowserLoadErrorCode.UNSUPPORTED_SCHEME,
            BrowserLoadErrorCode.BAD_URL,
            BrowserLoadErrorCode.FILE,
            BrowserLoadErrorCode.FILE_NOT_FOUND,
            -> BrowserError.NavigationBlocked
            else -> BrowserError.Unknown(errorCode)
        }
    }

    fun fromHttpStatus(statusCode: Int): BrowserError =
        if (statusCode in 100..599) BrowserError.Http(statusCode)
        else BrowserError.Unknown(statusCode)

    fun fromNavigationDecision(decision: NavigationDecision): BrowserError? = when (decision) {
        is NavigationDecision.Allow,
        is NavigationDecision.OpenExternal,
        -> null
        is NavigationDecision.Block -> BrowserError.NavigationBlocked
    }

    fun tlsFailure(): BrowserError = BrowserError.TlsFailure

    fun safeBrowsingBlocked(): BrowserError = BrowserError.SafeBrowsingBlocked

    fun rendererGone(): BrowserError = BrowserError.RendererGone

    private val NETWORK_RELATED_CODES = setOf(
        BrowserLoadErrorCode.HOST_LOOKUP,
        BrowserLoadErrorCode.CONNECT,
        BrowserLoadErrorCode.IO,
        BrowserLoadErrorCode.TIMEOUT,
    )
}
