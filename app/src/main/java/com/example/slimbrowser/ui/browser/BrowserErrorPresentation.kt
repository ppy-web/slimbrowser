package com.example.slimbrowser.ui.browser

import androidx.annotation.StringRes
import com.example.slimbrowser.R
import com.example.slimbrowser.domain.BrowserError

enum class BrowserErrorAction {
    RETRY,
    GO_BACK,
    OPEN_NETWORK_SETTINGS,
    COPY_URL,
    NONE,
}

data class BrowserErrorPresentation(
    @StringRes val title: Int,
    @StringRes val message: Int,
    val messageArgument: Any? = null,
    val primaryAction: BrowserErrorAction,
    val secondaryAction: BrowserErrorAction = BrowserErrorAction.NONE,
)

object BrowserErrorPresenter {
    fun present(error: BrowserError): BrowserErrorPresentation = when (error) {
        BrowserError.Offline -> BrowserErrorPresentation(
            R.string.error_offline_title,
            R.string.error_offline_message,
            primaryAction = BrowserErrorAction.RETRY,
            secondaryAction = BrowserErrorAction.OPEN_NETWORK_SETTINGS,
        )
        BrowserError.DnsFailure -> BrowserErrorPresentation(
            R.string.error_dns_title,
            R.string.error_dns_message,
            primaryAction = BrowserErrorAction.RETRY,
            secondaryAction = BrowserErrorAction.GO_BACK,
        )
        BrowserError.TlsFailure -> BrowserErrorPresentation(
            R.string.error_tls_title,
            R.string.error_tls_message,
            primaryAction = BrowserErrorAction.GO_BACK,
            secondaryAction = BrowserErrorAction.COPY_URL,
        )
        BrowserError.SafeBrowsingBlocked -> BrowserErrorPresentation(
            R.string.error_safe_browsing_title,
            R.string.error_safe_browsing_message,
            primaryAction = BrowserErrorAction.GO_BACK,
            secondaryAction = BrowserErrorAction.COPY_URL,
        )
        is BrowserError.Http -> BrowserErrorPresentation(
            R.string.error_http_title,
            R.string.error_http_message,
            messageArgument = error.statusCode,
            primaryAction = BrowserErrorAction.RETRY,
            secondaryAction = BrowserErrorAction.GO_BACK,
        )
        BrowserError.NavigationBlocked -> BrowserErrorPresentation(
            R.string.error_navigation_title,
            R.string.error_navigation_message,
            primaryAction = BrowserErrorAction.GO_BACK,
            secondaryAction = BrowserErrorAction.COPY_URL,
        )
        BrowserError.Timeout -> BrowserErrorPresentation(
            R.string.error_timeout_title,
            R.string.error_timeout_message,
            primaryAction = BrowserErrorAction.RETRY,
            secondaryAction = BrowserErrorAction.GO_BACK,
        )
        BrowserError.RendererGone -> BrowserErrorPresentation(
            R.string.error_renderer_title,
            R.string.error_renderer_message,
            primaryAction = BrowserErrorAction.RETRY,
            secondaryAction = BrowserErrorAction.GO_BACK,
        )
        is BrowserError.Unknown -> BrowserErrorPresentation(
            R.string.error_unknown_title,
            R.string.error_unknown_message,
            messageArgument = error.diagnosticCode?.toString() ?: "unknown",
            primaryAction = BrowserErrorAction.RETRY,
            secondaryAction = BrowserErrorAction.GO_BACK,
        )
    }
}
