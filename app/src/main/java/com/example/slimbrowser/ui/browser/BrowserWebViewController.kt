package com.example.slimbrowser.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.webkit.SafeBrowsingResponseCompat
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import com.example.slimbrowser.data.BrowserSettings
import com.example.slimbrowser.databinding.ViewBrowserBinding
import com.example.slimbrowser.domain.BrowserError
import com.example.slimbrowser.domain.BrowserErrorMapper
import com.example.slimbrowser.domain.BrowserLoadErrorCode
import com.example.slimbrowser.domain.ExternalAction
import com.example.slimbrowser.domain.NavigationBlockReason
import com.example.slimbrowser.domain.NavigationDecision
import com.example.slimbrowser.domain.NavigationPolicy
import com.example.slimbrowser.domain.NavigationSource
import com.example.slimbrowser.domain.UrlPolicy
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class WebDownloadRequest(
    val url: String,
    val userAgent: String,
    val contentDisposition: String,
    val mimeType: String,
    val contentLength: Long,
)

/**
 * Owns the replaceable WebView and translates platform callbacks into typed
 * browser events. The Activity remains responsible for scene rendering and
 * executing explicitly approved platform actions.
 */
class BrowserWebViewController(
    private val activity: AppCompatActivity,
    private val binding: ViewBrowserBinding,
    private val scope: CoroutineScope,
    private val listener: Listener,
) {
    interface Listener {
        fun currentSettings(): BrowserSettings
        fun currentNavigationPolicy(): NavigationPolicy
        fun isNetworkAvailable(): Boolean?
        fun onPageStarted(url: String): Long
        fun onProgressChanged(navigationId: Long, progress: Int)
        fun onTitleChanged(navigationId: Long, title: String)
        fun onFaviconChanged(navigationId: Long, pageUrl: String, icon: Bitmap?)
        fun onVisitedHistoryChanged(
            navigationId: Long,
            url: String,
            canGoBack: Boolean,
            canGoForward: Boolean,
        )
        fun onPageFinished(
            navigationId: Long,
            url: String,
            title: String,
            canGoBack: Boolean,
            canGoForward: Boolean,
        )
        fun onPageError(navigationId: Long, error: BrowserError, failedUrl: String)
        fun onNavigationBlocked(reason: NavigationBlockReason, attemptedUrl: String)
        fun onExternalAction(action: ExternalAction)
        fun onLongPressTarget(url: String, isImage: Boolean)
        fun onDownloadRequested(request: WebDownloadRequest)
        fun onFileChooserRequested(
            callback: ValueCallback<Array<Uri>>,
            params: WebChromeClient.FileChooserParams,
        ): Boolean
        fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback)
        fun onHideCustomView()
        fun onRendererGone(lastNavigationId: Long)
    }

    var webView: WebView = binding.webView
        private set

    private var activeNavigationId = 0L
    private val navigationIdsByUrl = object : LinkedHashMap<String, Long>(MAX_TRACKED_URLS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_TRACKED_URLS
    }
    private var timeoutJob: Job? = null
    private val mobileUserAgent = WebSettings.getDefaultUserAgent(activity)

    init {
        configureWebView(webView)
    }

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    fun reload() {
        webView.reload()
    }

    fun stopLoading() {
        webView.stopLoading()
    }

    fun canGoBack(): Boolean = webView.canGoBack()

    fun canGoForward(): Boolean = webView.canGoForward()

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    fun goForward() {
        if (webView.canGoForward()) webView.goForward()
    }

    fun saveState(outState: Bundle): Boolean = webView.saveState(outState) != null

    fun restoreState(state: Bundle): Boolean = webView.restoreState(state) != null

    /** Recreates the WebView through the same path used after a renderer crash for manual QA. */
    fun recreateForRendererRecoveryTest() {
        cancelTimeout()
        replaceCrashedWebView()
        listener.onRendererGone(activeNavigationId)
    }

    fun findAll(query: String) {
        webView.findAllAsync(query)
    }

    fun findNext(forward: Boolean) {
        webView.findNext(forward)
    }

    fun clearFindMatches() {
        webView.clearMatches()
    }

    fun setFindListener(listener: WebView.FindListener?) {
        webView.setFindListener(listener)
    }

    fun setDesktopMode(enabled: Boolean) {
        webView.settings.userAgentString = if (enabled) desktopUserAgent(mobileUserAgent) else mobileUserAgent
    }

    fun applySettings(settings: BrowserSettings) {
        webView.settings.textZoom = settings.textZoom
        setDesktopMode(settings.desktopModeDefault)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, settings.darkWebMode)
        }
    }

    fun onResume() {
        webView.onResume()
    }

    fun onPause() {
        webView.onPause()
    }

    fun destroy() {
        timeoutJob?.cancel()
        timeoutJob = null
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = DisposedWebViewClient()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION") // Required compatibility/security setters remain relevant on API 26.
    private fun configureWebView(target: WebView) {
        WebView.setWebContentsDebuggingEnabled(false)
        target.setBackgroundColor(Color.TRANSPARENT)
        with(target.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = true
            allowContentAccess = true
            // This app is a personal website/debugging browser. Let local fixtures and legacy
            // pages behave like they do in a full browser; the user controls what is opened.
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            blockNetworkImage = false
            builtInZoomControls = true
            displayZoomControls = false
            setGeolocationEnabled(true)
            saveFormData = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(target.settings, true)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(target, true)
        target.webChromeClient = SecureChromeClient()
        target.webViewClient = SecureClient()
        target.setOnLongClickListener { view ->
            val hit = (view as? WebView)?.hitTestResult ?: return@setOnLongClickListener false
            val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            val isLink = hit.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            val url = hit.extra?.trim().orEmpty()
            if (!isImage && !isLink || url.isBlank()) return@setOnLongClickListener false
            listener.onLongPressTarget(url, isImage)
            true
        }
        target.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            val policy = listener.currentNavigationPolicy().decide(url, NavigationSource.LINK_CLICK)
            if (policy is NavigationDecision.Allow) {
                listener.onDownloadRequested(
                    WebDownloadRequest(
                        url = policy.url,
                        userAgent = userAgent.orEmpty(),
                        contentDisposition = contentDisposition.orEmpty(),
                        mimeType = mimeType.orEmpty(),
                        contentLength = contentLength,
                    ),
                )
            } else {
                listener.onNavigationBlocked(NavigationBlockReason.INVALID_URL, url)
            }
        }
        applySettings(listener.currentSettings())
    }

    private inner class SecureChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            listener.onProgressChanged(activeNavigationId, newProgress)
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            title?.let { listener.onTitleChanged(activeNavigationId, it) }
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            val pageUrl = view.url.orEmpty()
            listener.onFaviconChanged(navigationIdFor(pageUrl), pageUrl, icon)
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            // Do not silently reject a site capability request. WebView/Android still applies
            // the platform permission checks; this only removes the browser's blanket deny.
            request.grant(request.resources)
        }

        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback,
        ) {
            callback.invoke(origin, true, true)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean = listener.onFileChooserRequested(filePathCallback, fileChooserParams)

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            listener.onShowCustomView(view, callback)
        }

        override fun onHideCustomView() {
            listener.onHideCustomView()
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean = false
    }

    @SuppressLint("MissingOnRenderProcessGone")
    private inner class SecureClient : WebViewClientCompat() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val source = if (request.hasGesture()) NavigationSource.LINK_CLICK else NavigationSource.REDIRECT
            return when (val decision = listener.currentNavigationPolicy().decide(request.url.toString(), source)) {
                is NavigationDecision.Allow -> {
                    if (decision.url == request.url.toString()) {
                        false
                    } else {
                        view.loadUrl(decision.url)
                        true
                    }
                }
                is NavigationDecision.OpenExternal -> {
                    listener.onExternalAction(decision.action)
                    true
                }
                is NavigationDecision.Block -> {
                    listener.onNavigationBlocked(decision.reason, request.url.toString())
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            val decision = listener.currentNavigationPolicy().decide(url, NavigationSource.REDIRECT)
            when (decision) {
                is NavigationDecision.Allow -> {
                    activeNavigationId = listener.onPageStarted(decision.url)
                    trackNavigationUrl(url, activeNavigationId)
                    trackNavigationUrl(decision.url, activeNavigationId)
                    startTimeout(activeNavigationId, decision.url)
                    favicon?.let { listener.onFaviconChanged(activeNavigationId, decision.url, it) }
                }
                is NavigationDecision.OpenExternal -> {
                    // A redirect to a platform URI is still a valid user-requested navigation.
                    // Stop WebView's unsupported load after handing it to the system handler.
                    view.stopLoading()
                    listener.onExternalAction(decision.action)
                }
                is NavigationDecision.Block -> {
                    // Only malformed/control-character input reaches this path.
                    view.stopLoading()
                    listener.onNavigationBlocked(decision.reason, url)
                }
            }
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            val id = navigationIdFor(url)
            listener.onVisitedHistoryChanged(id, url, view.canGoBack(), view.canGoForward())
        }

        override fun onPageFinished(view: WebView, url: String) {
            val id = navigationIdFor(url)
            if (id == activeNavigationId) cancelTimeout()
            listener.onPageFinished(
                id,
                url,
                view.title.orEmpty(),
                view.canGoBack(),
                view.canGoForward(),
            )
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceErrorCompat,
        ) {
            if (!request.isForMainFrame) return
            val code = if (WebViewFeature.isFeatureSupported(
                    WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE,
                )
            ) {
                error.errorCode
            } else {
                BrowserLoadErrorCode.UNKNOWN
            }
            reportError(
                request.url.toString(),
                BrowserErrorMapper.fromWebViewError(code, listener.isNetworkAvailable()),
            )
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                reportError(
                    request.url.toString(),
                    BrowserErrorMapper.fromHttpStatus(errorResponse.statusCode),
                )
            }
        }

        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: android.net.http.SslError,
        ) {
            // This is intentionally a permissive debugging browser. The user chose the URL;
            // allow inspection of sites with self-signed/expired certificates.
            handler.proceed()
        }

        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: SafeBrowsingResponseCompat,
        ) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_RESPONSE_PROCEED)) {
                callback.proceed(true)
            } else if (WebViewFeature.isFeatureSupported(
                    WebViewFeature.SAFE_BROWSING_RESPONSE_SHOW_INTERSTITIAL,
                )
            ) {
                // Older WebViews can still show their own warning, but do not replace it with a
                // native hard block in this app.
                callback.showInterstitial(false)
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            cancelTimeout()
            replaceCrashedWebView()
            listener.onRendererGone(activeNavigationId)
            return true
        }
    }

    private fun reportError(url: String, error: BrowserError) {
        val navigationId = navigationIdFor(url)
        if (navigationId == activeNavigationId) cancelTimeout()
        listener.onPageError(navigationId, error, url)
    }

    private fun navigationIdFor(url: String): Long =
        navigationIdsByUrl[url] ?: UrlPolicy.normalizeForNavigation(url)?.let(navigationIdsByUrl::get)
            ?: activeNavigationId

    private fun trackNavigationUrl(url: String, navigationId: Long) {
        navigationIdsByUrl[url] = navigationId
    }

    private fun startTimeout(navigationId: Long, url: String) {
        cancelTimeout()
        timeoutJob = scope.launch {
            delay(PAGE_TIMEOUT_MILLIS)
            if (navigationId == activeNavigationId) {
                webView.stopLoading()
                listener.onPageError(navigationId, BrowserError.Timeout, url)
            }
        }
    }

    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    private fun replaceCrashedWebView() {
        val parent = webView.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(webView)
        val params = webView.layoutParams
        parent.removeView(webView)
        webView.destroy()
        webView = WebView(activity).apply {
            id = binding.webView.id
            layoutParams = params
            isVisible = true
        }
        parent.addView(webView, index)
        configureWebView(webView)
    }

    private fun desktopUserAgent(userAgent: String): String = userAgent
        .replace("; wv", "")
        .replace(" Mobile ", " ")

    private companion object {
        const val PAGE_TIMEOUT_MILLIS = 30_000L
        const val MAX_TRACKED_URLS = 32
    }

    /** Absorbs a late renderer callback while a WebView is being destroyed. */
    @SuppressLint("MissingOnRenderProcessGone") // The override below is intentionally terminal.
    private class DisposedWebViewClient : WebViewClient() {
        override fun onRenderProcessGone(
            view: WebView,
            detail: RenderProcessGoneDetail,
        ): Boolean = true
    }
}
