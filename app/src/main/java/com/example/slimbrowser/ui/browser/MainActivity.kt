package com.example.slimbrowser.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.slimbrowser.R
import com.example.slimbrowser.data.BrowserPreferences
import com.example.slimbrowser.data.BrowserSettings
import com.example.slimbrowser.databinding.ActivityMainBinding
import com.example.slimbrowser.databinding.DialogSettingsBinding
import com.example.slimbrowser.domain.UrlPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity(), BrowserContract.View {
    private lateinit var binding: ActivityMainBinding
    private lateinit var presenter: BrowserContract.Presenter
    private lateinit var webView: WebView
    private var settings = BrowserSettings()
    private var restoredWebViewState: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webView
        configureWebView(webView)
        restoredWebViewState = savedInstanceState?.getBundle(KEY_WEBVIEW_STATE)
        presenter = BrowserPresenter(BrowserPreferences(applicationContext), lifecycleScope)

        binding.settingsFab.setOnClickListener { presenter.onSettingsRequested() }
        binding.fullscreenFab.setOnClickListener { presenter.onFullscreenShortcutRequested() }
        binding.retryButton.setOnClickListener { presenter.onRetryRequested() }
        binding.errorSettingsButton.setOnClickListener { presenter.onSettingsRequested() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyContentInsets(insets)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = presenter.onBackPressed()
        })

        presenter.attach(this, restoredWebViewState)
    }

    override fun onDestroy() {
        presenter.detach()
        if (isFinishing) {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val webViewState = Bundle()
        webView.saveState(webViewState)
        outState.putBundle(KEY_WEBVIEW_STATE, webViewState)
        super.onSaveInstanceState(outState)
    }

    override fun renderSettings(settings: BrowserSettings) {
        this.settings = settings
    }

    override fun loadUrl(url: String) {
        hideError()
        webView.loadUrl(url)
    }

    override fun showSettings(settings: BrowserSettings, required: Boolean) {
        showSystemBars()
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        dialogBinding.urlInput.setText(settings.homeUrl)
        dialogBinding.fullscreenSwitch.isChecked = settings.fullscreenEnabled

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .setCancelable(!required)
            .create()

        dialog.setOnDismissListener { applyFullscreen(this.settings.fullscreenEnabled) }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).isVisible = !required
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rawUrl = dialogBinding.urlInput.text?.toString().orEmpty()
                val normalized = UrlPolicy.normalize(rawUrl)
                if (normalized == null) {
                    dialogBinding.urlInputLayout.error = getString(R.string.invalid_url)
                } else {
                    dialogBinding.urlInputLayout.error = null
                    presenter.onSettingsSubmitted(normalized, dialogBinding.fullscreenSwitch.isChecked)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    override fun showValidationError() {
        showError(getString(R.string.invalid_url))
    }

    override fun applyFullscreen(enabled: Boolean) {
        val controller = WindowInsetsControllerCompat(window, binding.root)
        if (enabled) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun updateFullscreenButton(enabled: Boolean) {
        binding.fullscreenFab.setImageResource(
            if (enabled) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen_enter,
        )
        binding.fullscreenFab.contentDescription = getString(
            if (enabled) R.string.exit_fullscreen else R.string.enter_fullscreen,
        )
    }

    override fun showError(message: String) {
        binding.errorMessage.text = message
        binding.errorOverlay.isVisible = true
    }

    override fun hideError() {
        binding.errorOverlay.isVisible = false
    }

    override fun reloadPage() {
        val current = webView.url
        if (!current.isNullOrBlank() && current != "about:blank") {
            webView.reload()
        } else if (settings.homeUrl.isNotBlank()) {
            webView.loadUrl(settings.homeUrl)
        }
    }

    override fun restoreWebViewState(state: Bundle): Boolean = webView.restoreState(state) != null

    override fun canGoBack(): Boolean = webView.canGoBack()

    override fun goBack() = webView.goBack()

    override fun finishScreen() = finish()

    override fun onResume() {
        super.onResume()
        webView.onResume()
        applyFullscreen(settings.fullscreenEnabled)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun applyContentInsets(insets: WindowInsetsCompat) {
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val bars = if (settings.fullscreenEnabled) {
            cutout
        } else {
            insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        }
        binding.webViewContainer.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        binding.errorOverlay.setPadding(bars.left + 32.dp, bars.top + 32.dp, bars.right + 32.dp, bars.bottom + 32.dp)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(target: WebView) {
        WebView.setWebContentsDebuggingEnabled(false)
        with(target.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            loadsImagesAutomatically = true
            blockNetworkImage = false
            builtInZoomControls = true
            displayZoomControls = false
            setGeolocationEnabled(false)
            saveFormData = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(target.settings, true)
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(target, false)
        target.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.pageProgress.progress = newProgress
                binding.pageProgress.isVisible = newProgress in 1..99
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }
        }
        target.webViewClient = SecureWebViewClient()
    }

    private fun replaceCrashedWebView() {
        val parent = webView.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(webView)
        val layoutParams = webView.layoutParams
        parent.removeView(webView)
        webView.destroy()
        webView = WebView(this).apply {
            id = R.id.webView
            this.layoutParams = layoutParams
        }
        parent.addView(webView, index)
        configureWebView(webView)
    }

    private inner class SecureWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val normalized = UrlPolicy.normalize(request.url.toString())
            return if (normalized == null) {
                presenter.onMainFrameError(getString(R.string.invalid_url))
                true
            } else {
                false
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            presenter.onPageStarted()
        }

        override fun onPageFinished(view: WebView, url: String) {
            presenter.onPageFinished()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) {
                presenter.onMainFrameError(error.description?.toString().orEmpty())
            }
        }

        override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
        ) {
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                presenter.onMainFrameError(getString(R.string.error_http, errorResponse.statusCode))
            }
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            handler.cancel()
            presenter.onMainFrameError(getString(R.string.error_message_default))
        }

        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: SafeBrowsingResponse,
        ) {
            callback.backToSafety(true)
            presenter.onMainFrameError(getString(R.string.error_message_default))
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            replaceCrashedWebView()
            presenter.onRendererGone()
            return true
        }
    }

    private companion object {
        const val KEY_WEBVIEW_STATE = "webview_state"
    }
}
