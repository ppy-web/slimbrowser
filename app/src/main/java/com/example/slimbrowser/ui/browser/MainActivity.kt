package com.example.slimbrowser.ui.browser

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.io.File
import java.net.URLEncoder
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.webkit.SafeBrowsingResponseCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import com.example.slimbrowser.BuildConfig
import com.example.slimbrowser.R
import com.example.slimbrowser.data.BrowserPreferences
import com.example.slimbrowser.data.BrowserSettings
import com.example.slimbrowser.data.Favorite
import com.example.slimbrowser.databinding.ActivityMainBinding
import com.example.slimbrowser.databinding.DialogSettingsBinding
import com.example.slimbrowser.databinding.DialogSearchBinding
import com.example.slimbrowser.domain.UrlPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), BrowserContract.View {
    private lateinit var binding: ActivityMainBinding
    private lateinit var presenter: BrowserContract.Presenter
    private lateinit var browserPreferences: BrowserPreferences
    private lateinit var webView: WebView
    private var settings = BrowserSettings()
    private var restoredWebViewState: Bundle? = null
    private var settingsDialogBinding: DialogSettingsBinding? = null
    private var pendingBackgroundUri: String? = null
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val allowedFavoriteHosts = mutableSetOf<String>()
    private var searchSessionActive = false
    private val backgroundPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val localFile = File(filesDir, BACKGROUND_FILE_NAME)
        val copied = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("empty background")
        }.isSuccess
        if (copied) {
            pendingBackgroundUri = Uri.fromFile(localFile).toString()
            settingsDialogBinding?.backgroundStatus?.setText(R.string.custom_background)
        } else {
            Toast.makeText(this, R.string.background_load_failed, Toast.LENGTH_SHORT).show()
        }
    }
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        filePathCallback = null
        callback.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
        )
    }
    private val controlsHandler = Handler(Looper.getMainLooper())
    private var initialPinchSpan = 0f
    private var pinchRevealTriggered = false
    private val hideControlsRunnable = Runnable {
        binding.actionButtons.animate()
            .alpha(0f)
            .setDuration(CONTROLS_ANIMATION_MS)
            .withEndAction { binding.actionButtons.isVisible = false }
            .start()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webView = binding.webView
        configureWebView(webView)
        restoredWebViewState = savedInstanceState?.getBundle(KEY_WEBVIEW_STATE)
        val fallbackUrl = savedInstanceState?.getString(KEY_CURRENT_URL)
        browserPreferences = BrowserPreferences(applicationContext)
        presenter = BrowserPresenter(browserPreferences, lifecycleScope)

        binding.settingsFab.setOnClickListener {
            presenter.onSettingsRequested()
        }
        binding.fullscreenFab.setOnClickListener {
            presenter.onFullscreenShortcutRequested()
        }
        binding.refreshFab.setOnClickListener {
            presenter.onRefreshRequested()
        }
        binding.retryButton.setOnClickListener { presenter.onRetryRequested() }
        binding.errorSettingsButton.setOnClickListener { presenter.onSettingsRequested() }
        binding.searchFab.setOnClickListener { showSearchDialog() }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.canScrollVertically(-1) }
        binding.swipeRefresh.setOnRefreshListener { presenter.onRefreshRequested() }
        webView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialPinchSpan = 0f
                    pinchRevealTriggered = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                    initialPinchSpan = event.pinchSpan()
                }
                MotionEvent.ACTION_MOVE -> if (
                    event.pointerCount >= 2 &&
                    initialPinchSpan > 0f &&
                    !pinchRevealTriggered &&
                    kotlin.math.abs(event.pinchSpan() - initialPinchSpan) >= PINCH_REVEAL_DISTANCE_DP.dp
                ) {
                    pinchRevealTriggered = true
                    revealControls()
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL,
                -> {
                    initialPinchSpan = 0f
                    pinchRevealTriggered = false
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            false
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyContentInsets(insets)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = presenter.onBackPressed()
        })

        presenter.attach(this, restoredWebViewState, fallbackUrl)
        hideControlsImmediately()
    }

    override fun onDestroy() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        settingsDialogBinding = null
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        presenter.detach()
        webView.stopLoading()
        webView.webChromeClient = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val webViewState = Bundle()
        webView.saveState(webViewState)
        outState.putBundle(KEY_WEBVIEW_STATE, webViewState)
        outState.putString(KEY_CURRENT_URL, webView.url)
        super.onSaveInstanceState(outState)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyFullscreen(settings.fullscreenEnabled)
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun renderSettings(settings: BrowserSettings) {
        this.settings = settings
    }

    override fun loadUrl(url: String) {
        hideError()
        binding.searchFab.isVisible = false
        webView.loadUrl(url)
    }

    override fun showBlankHome() {
        searchSessionActive = false
        webView.stopLoading()
        webView.loadUrl("about:blank")
        binding.swipeRefresh.isRefreshing = false
        binding.loadingStatus.isVisible = false
        binding.pageProgress.isVisible = false
        binding.errorOverlay.isVisible = false
        binding.searchFab.isVisible = true
    }

    override fun showSettings(settings: BrowserSettings, required: Boolean) {
        showSystemBars()
        val dialogBinding = DialogSettingsBinding.inflate(layoutInflater)
        dialogBinding.urlInput.setText(settings.homeUrl)
        dialogBinding.fullscreenSwitch.isChecked = settings.fullscreenEnabled
        dialogBinding.darkThemeSwitch.isChecked = settings.darkThemeEnabled
        pendingBackgroundUri = settings.backgroundUri
        dialogBinding.backgroundStatus.setText(
            if (settings.backgroundUri.isNullOrBlank()) R.string.default_background
            else R.string.custom_background,
        )

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .setCancelable(!required)
            .create()

        settingsDialogBinding = dialogBinding
        dialog.setOnDismissListener {
            applyFullscreen(this.settings.fullscreenEnabled)
            settingsDialogBinding = null
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).isVisible = !required
            dialogBinding.chooseBackgroundButton.setOnClickListener {
                backgroundPicker.launch(arrayOf("image/*"))
            }
            dialogBinding.clearBackgroundButton.setOnClickListener {
                pendingBackgroundUri = null
                dialogBinding.backgroundStatus.setText(R.string.default_background)
            }
            dialogBinding.clearSiteDataButton.setOnClickListener { clearSiteData() }
            dialogBinding.addFavoriteButton.setOnClickListener { addCurrentPageToFavorites() }
            dialogBinding.openFavoritesButton.setOnClickListener {
                dialog.dismiss()
                showFavoritesDialog()
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rawUrl = dialogBinding.urlInput.text?.toString().orEmpty()
                val normalized = if (rawUrl.trim().isEmpty()) "" else UrlPolicy.normalize(rawUrl)
                if (normalized == null) {
                    dialogBinding.urlInputLayout.error = getString(R.string.invalid_url)
                } else {
                    dialogBinding.urlInputLayout.error = null
                    if (normalized.isNotBlank()) {
                        searchSessionActive = false
                    }
                    presenter.onSettingsSubmitted(
                        normalized,
                        dialogBinding.fullscreenSwitch.isChecked,
                        dialogBinding.darkThemeSwitch.isChecked,
                        pendingBackgroundUri,
                    )
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

    override fun applyTheme(darkThemeEnabled: Boolean) {
        binding.themeWallpaper.setImageResource(
            if (darkThemeEnabled) R.drawable.wallpaper_dark else R.drawable.wallpaper_light,
        )
        binding.errorOverlay.setBackgroundResource(
            if (darkThemeEnabled) R.drawable.wallpaper_dark else R.drawable.wallpaper_light,
        )
        val mode = if (darkThemeEnabled) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    override fun applyBackground(uri: String?) {
        val fallback = if (settings.darkThemeEnabled) R.drawable.wallpaper_dark else R.drawable.wallpaper_light
        if (uri.isNullOrBlank()) {
            binding.themeWallpaper.setImageResource(fallback)
        } else {
            val applied = runCatching {
                binding.themeWallpaper.setImageURI(uri.toUri())
            }.isSuccess
            if (!applied || binding.themeWallpaper.drawable == null) {
                binding.themeWallpaper.setImageResource(fallback)
            }
        }
        binding.errorOverlay.background = binding.themeWallpaper.drawable?.constantState
            ?.newDrawable(resources)
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
        binding.swipeRefresh.isRefreshing = false
        binding.loadingStatus.isVisible = false
        binding.searchFab.isVisible = false
        binding.errorMessage.text = message
        binding.errorOverlay.isVisible = true
    }

    override fun hideError() {
        binding.errorOverlay.isVisible = false
    }

    override fun reloadPage() {
        binding.swipeRefresh.isRefreshing = true
        binding.loadingStatus.text = getString(R.string.loading)
        binding.loadingStatus.isVisible = true
        val current = webView.url
        if (!current.isNullOrBlank() && current != "about:blank") {
            webView.reload()
        } else if (settings.homeUrl.isNotBlank()) {
            webView.loadUrl(settings.homeUrl)
        } else {
            binding.swipeRefresh.isRefreshing = false
            binding.loadingStatus.isVisible = false
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
        hideControlsImmediately()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSearchDialog() {
        showSystemBars()
        val dialogBinding = DialogSearchBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.search, null)
            .create()
        dialog.setOnShowListener {
            val searchButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            searchButton.setOnClickListener {
                val query = dialogBinding.searchInput.text?.toString()?.trim().orEmpty()
                if (query.isBlank()) {
                    dialogBinding.searchInputLayout.error = getString(R.string.empty_search_query)
                    return@setOnClickListener
                }
                dialogBinding.searchInputLayout.error = null
                val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
                searchSessionActive = true
                loadUrl("https://www.baidu.com/s?wd=$encodedQuery")
                dialog.dismiss()
            }
            dialogBinding.searchInput.setOnEditorActionListener { _, _, _ ->
                searchButton.performClick()
                true
            }
            dialogBinding.searchInput.requestFocus()
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    private fun addCurrentPageToFavorites() {
        val url = webView.url?.takeIf { isAllowedWebUrl(it) }
        if (url.isNullOrBlank()) {
            Toast.makeText(this, R.string.favorite_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val title = webView.title.orEmpty()
        lifecycleScope.launch {
            browserPreferences.addFavorite(url, title)
            Toast.makeText(this@MainActivity, R.string.favorite_added, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFavoritesDialog() {
        lifecycleScope.launch {
            val favorites = browserPreferences.getFavorites().sortedBy { it.title.lowercase() }
            if (favorites.isEmpty()) {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.favorites)
                    .setMessage(R.string.no_favorites)
                    .setPositiveButton(R.string.cancel, null)
                    .show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.favorites)
                .setItems(favorites.map { it.title }.toTypedArray()) { _, which ->
                    showFavoriteActions(favorites[which])
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showFavoriteActions(favorite: Favorite) {
        MaterialAlertDialogBuilder(this)
            .setTitle(favorite.title)
            .setItems(arrayOf(getString(R.string.open_favorite), getString(R.string.delete_favorite))) { _, which ->
                if (which == 0) {
                    val normalized = UrlPolicy.normalize(favorite.url)
                    if (normalized == null) {
                        Toast.makeText(this, R.string.favorite_unavailable, Toast.LENGTH_SHORT).show()
                    } else {
                        allowedFavoriteHosts += normalized
                        loadUrl(normalized)
                    }
                } else {
                    lifecycleScope.launch {
                        browserPreferences.removeFavorite(favorite.url)
                        Toast.makeText(this@MainActivity, R.string.favorite_deleted, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun isAllowedWebUrl(url: String): Boolean {
        if (searchSessionActive && UrlPolicy.isAllowed(url)) return true
        if (UrlPolicy.isAllowedNavigation(url, settings.homeUrl)) return true
        return allowedFavoriteHosts.any { UrlPolicy.isAllowedNavigation(url, it) }
    }

    private fun revealControls() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        binding.actionButtons.animate().cancel()
        binding.actionButtons.isVisible = true
        binding.actionButtons.alpha = 1f
        controlsHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MS)
    }

    private fun hideControlsImmediately() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        binding.actionButtons.animate().cancel()
        binding.actionButtons.alpha = 0f
        binding.actionButtons.isVisible = false
    }

    private fun MotionEvent.pinchSpan(): Float {
        if (pointerCount < 2) return 0f
        val deltaX = getX(0) - getX(1)
        val deltaY = getY(0) - getY(1)
        return kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
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
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        target.setBackgroundColor(Color.TRANSPARENT)
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
                if (newProgress in 1..99) {
                    binding.loadingStatus.text = getString(R.string.loading_percent, newProgress)
                    binding.loadingStatus.isVisible = true
                } else if (newProgress >= 100) {
                    binding.loadingStatus.isVisible = false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                return runCatching {
                    val acceptTypes = fileChooserParams.acceptTypes
                        .filter { it.isNotBlank() && it != "*/*" }
                    val chooserIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = acceptTypes.firstOrNull() ?: "*/*"
                        if (acceptTypes.size > 1) {
                            putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
                        }
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                    }
                    this@MainActivity.fileChooserLauncher.launch(chooserIntent)
                    true
                }.getOrElse {
                    this@MainActivity.filePathCallback = null
                    Toast.makeText(this@MainActivity, R.string.file_chooser_failed, Toast.LENGTH_SHORT).show()
                    false
                }
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message,
            ): Boolean = false
        }
        target.webViewClient = SecureWebViewClient()
        target.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
    ) {
        if (!isAllowedWebUrl(url)) {
            Toast.makeText(this, R.string.download_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        val uri = runCatching { url.toUri() }.getOrNull() ?: return
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            .substringAfterLast('/')
            .ifBlank { "download" }
        val request = DownloadManager.Request(uri)
            .setTitle(fileName)
            .setDescription(getString(R.string.app_name))
            .setMimeType(mimeType)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .addRequestHeader("User-Agent", userAgent)
        CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        } else {
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
        }
        runCatching {
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, R.string.download_blocked, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExternal(uri: Uri): Boolean {
        val action = when {
            uri.scheme.equals("tel", ignoreCase = true) -> Intent.ACTION_DIAL
            uri.scheme.equals("mailto", ignoreCase = true) || uri.scheme.equals("sms", ignoreCase = true) ->
                Intent.ACTION_SENDTO
            else -> Intent.ACTION_VIEW
        }
        return try {
            startActivity(Intent(action, uri))
            true
        } catch (_: ActivityNotFoundException) {
            presenter.onMainFrameError(getString(R.string.no_external_app))
            true
        }
    }

    private fun openIntent(uri: Uri): Boolean {
        return runCatching {
            val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                component = null
                selector = null
            }
            check(intent.action.isNullOrBlank() || intent.action in setOf(
                Intent.ACTION_VIEW,
                Intent.ACTION_SENDTO,
                Intent.ACTION_DIAL,
            ))
            startActivity(intent)
            true
        }.getOrElse {
            presenter.onMainFrameError(getString(R.string.no_external_app))
            true
        }
    }

    private fun clearSiteData() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_site_data)
            .setMessage(R.string.clear_site_data_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear_site_data) { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                webView.clearCache(true)
                webView.clearHistory()
                webView.clearFormData()
                Toast.makeText(this, R.string.site_data_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
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

    @SuppressLint("MissingOnRenderProcessGone")
    private inner class SecureWebViewClient : WebViewClientCompat() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            return when (request.url.scheme?.lowercase()) {
                "https" -> if (isAllowedWebUrl(request.url.toString())) {
                    false
                } else {
                    presenter.onMainFrameError(getString(R.string.navigation_blocked))
                    true
                }
                "mailto", "tel", "sms" -> openExternal(request.url)
                "intent" -> openIntent(request.url)
                else -> {
                    presenter.onMainFrameError(getString(R.string.invalid_url))
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (url != "about:blank" && !isAllowedWebUrl(url)) {
                view.stopLoading()
                presenter.onMainFrameError(getString(R.string.navigation_blocked))
                return
            }
            binding.swipeRefresh.isRefreshing = true
            binding.loadingStatus.text = getString(R.string.loading)
            binding.loadingStatus.isVisible = true
            presenter.onPageStarted(url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            binding.swipeRefresh.isRefreshing = false
            binding.pageProgress.isVisible = false
            binding.loadingStatus.isVisible = false
            presenter.onPageFinished(url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceErrorCompat,
        ) {
            if (request.isForMainFrame) {
                val description = if (WebViewFeature.isFeatureSupported(
                        WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION,
                    )
                ) {
                    error.description?.toString()
                } else {
                    null
                }
                presenter.onMainFrameError(
                    description.takeUnless { it.isNullOrBlank() }
                        ?: getString(R.string.error_message_default),
                )
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
            if (view.url == error.url) {
                presenter.onMainFrameError(getString(R.string.error_message_default))
            }
        }

        override fun onSafeBrowsingHit(
            view: WebView,
            request: WebResourceRequest,
            threatType: Int,
            callback: SafeBrowsingResponseCompat,
        ) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_RESPONSE_BACK_TO_SAFETY)) {
                callback.backToSafety(true)
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_RESPONSE_SHOW_INTERSTITIAL)) {
                callback.showInterstitial(true)
            }
            if (request.isForMainFrame) {
                presenter.onMainFrameError(getString(R.string.error_message_default))
            }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            replaceCrashedWebView()
            presenter.onRendererGone(getString(R.string.error_renderer))
            return true
        }
    }

    private companion object {
        const val KEY_WEBVIEW_STATE = "webview_state"
        const val KEY_CURRENT_URL = "current_url"
        const val CONTROLS_HIDE_DELAY_MS = 3_000L
        const val CONTROLS_ANIMATION_MS = 220L
        const val PINCH_REVEAL_DISTANCE_DP = 24
        const val BACKGROUND_FILE_NAME = "custom_background_image"
    }
}
