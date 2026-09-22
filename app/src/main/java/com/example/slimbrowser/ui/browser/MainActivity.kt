package com.example.slimbrowser.ui.browser

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.slimbrowser.R
import com.example.slimbrowser.data.AppDatabase
import com.example.slimbrowser.data.BrowserDataManager
import com.example.slimbrowser.data.BrowserPreferences
import com.example.slimbrowser.data.BrowserSession
import com.example.slimbrowser.data.BrowserSettings
import com.example.slimbrowser.data.BrowsingDataSelection
import com.example.slimbrowser.data.LegacyFavoritesMigrator
import com.example.slimbrowser.data.PrivateSessionPolicy
import com.example.slimbrowser.data.StartBehavior
import com.example.slimbrowser.data.ThemeMode
import com.example.slimbrowser.data.ToolbarPosition
import com.example.slimbrowser.data.bookmark.BookmarkEntity
import com.example.slimbrowser.data.bookmark.BookmarkRepository
import com.example.slimbrowser.data.download.DownloadRepository
import com.example.slimbrowser.data.history.HistoryEntity
import com.example.slimbrowser.data.history.HistoryRepository
import com.example.slimbrowser.databinding.ActivityMainNextBinding
import com.example.slimbrowser.databinding.DialogBookmarkListBinding
import com.example.slimbrowser.databinding.DialogDownloadListBinding
import com.example.slimbrowser.databinding.DialogEditBookmarkBinding
import com.example.slimbrowser.databinding.DialogHistoryListBinding
import com.example.slimbrowser.databinding.ViewBrowserBinding
import com.example.slimbrowser.databinding.ViewHomeBinding
import com.example.slimbrowser.databinding.ViewSettingsBinding
import com.example.slimbrowser.domain.BrowserError
import com.example.slimbrowser.domain.ExternalAction
import com.example.slimbrowser.domain.InputResolution
import com.example.slimbrowser.domain.InputResolver
import com.example.slimbrowser.domain.NavigationBlockReason
import com.example.slimbrowser.domain.NavigationDecision
import com.example.slimbrowser.domain.NavigationPolicy
import com.example.slimbrowser.domain.NavigationSource
import com.example.slimbrowser.domain.SearchEngine
import com.example.slimbrowser.domain.UrlPolicy
import com.example.slimbrowser.ui.browser.actions.BrowserMoreAction
import com.example.slimbrowser.ui.browser.actions.BrowserMoreActionsBottomSheet
import com.example.slimbrowser.ui.browser.actions.BrowserMoreActionsState
import com.example.slimbrowser.ui.settings.SettingsSceneController
import com.example.slimbrowser.ui.library.bookmark.BookmarkEditDraft
import com.example.slimbrowser.ui.library.bookmark.BookmarkEditorController
import com.example.slimbrowser.ui.library.bookmark.BookmarkListController
import com.example.slimbrowser.ui.library.download.DownloadListController
import com.example.slimbrowser.ui.library.history.HistoryListController
import com.example.slimbrowser.ui.state.AppScene
import com.example.slimbrowser.ui.state.BackDecision
import com.example.slimbrowser.ui.state.BrowserStateMachine
import com.example.slimbrowser.ui.state.MotionPolicy
import com.example.slimbrowser.ui.state.ToolbarEvent
import com.example.slimbrowser.ui.state.ToolbarVisibility
import com.example.slimbrowser.ui.state.ToolbarVisibilityController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The single activity owns scene navigation; specialised controllers own WebView/settings UI. */
class MainActivity : AppCompatActivity(), BrowserWebViewController.Listener, SettingsSceneController.Callback {
    private lateinit var binding: ActivityMainNextBinding
    private lateinit var home: ViewHomeBinding
    private lateinit var browser: ViewBrowserBinding
    private lateinit var settingsView: ViewSettingsBinding
    private lateinit var preferences: BrowserPreferences
    private lateinit var history: HistoryRepository
    private lateinit var bookmarks: BookmarkRepository
    private lateinit var downloads: DownloadRepository
    private lateinit var dataManager: BrowserDataManager
    private lateinit var web: BrowserWebViewController
    private lateinit var settingsController: SettingsSceneController
    private lateinit var downloadsCoordinator: DownloadCoordinator
    private lateinit var externalActions: ExternalActionExecutor

    private val state = BrowserStateMachine()
    private val toolbar = ToolbarVisibilityController()
    private var settings = BrowserSettings()
    private var session = BrowserSession()
    private var recent: List<HistoryEntity> = emptyList()
    private var pinned: List<BookmarkEntity> = emptyList()
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var privateCleanupStarted = false
    private var hideToolbarJob: Job? = null
    private var lastScrollY = 0
    private var errorPrimaryAction = BrowserErrorAction.NONE
    private var errorSecondaryAction = BrowserErrorAction.NONE
    private var diagnosticsText: String? = null
    private var renderedScene: AppScene? = null

    private val backgroundPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val copied = runCatching {
                    val target = java.io.File(filesDir, "background")
                    contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use(input::copyTo)
                    } ?: error("Cannot open background")
                    preferences.update(settings.copy(backgroundUri = Uri.fromFile(target).toString()))
                }.isSuccess
                if (!copied) toast(R.string.background_load_failed)
            }
        }
    }
    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
        fileCallback = null
    }
    private val diagnosticsWriter = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val content = diagnosticsText
        diagnosticsText = null
        if (uri != null && content != null) {
            val written = runCatching {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
                    ?: error("Unable to open diagnostics destination")
            }.isSuccess
            toast(if (written) R.string.site_data_cleared else R.string.background_load_failed)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainNextBinding.inflate(layoutInflater)
        setContentView(binding.root)
        home = ViewHomeBinding.inflate(layoutInflater, binding.homeContainer, true)
        browser = ViewBrowserBinding.inflate(layoutInflater, binding.browserContainer, true)
        settingsView = ViewSettingsBinding.inflate(layoutInflater, binding.settingsContainer, true)

        val database = AppDatabase.getInstance(applicationContext)
        preferences = BrowserPreferences(applicationContext)
        history = HistoryRepository(database.historyDao())
        bookmarks = BookmarkRepository(database.bookmarkDao())
        downloads = DownloadRepository(database.downloadDao())
        dataManager = BrowserDataManager(preferences, history)
        web = BrowserWebViewController(this, browser, lifecycleScope, this)
        externalActions = ExternalActionExecutor(this)
        downloadsCoordinator = DownloadCoordinator(this, downloads)
        settingsController = SettingsSceneController(this, settingsView, { settings }, this)

        configureViews()
        observeData()
        lifecycleScope.launch {
            settings = preferences.settings.first()
            session = preferences.session.first()
            LegacyFavoritesMigrator(preferences, bookmarks).migrateIfNeeded()
            applySettings(settings)
            val restored = savedInstanceState?.getBundle(KEY_WEB_STATE)?.let(web::restoreState) == true
            restoreSavedScene(savedInstanceState, restored)
            render()
        }
    }

    private fun restoreSavedScene(savedInstanceState: Bundle?, webViewRestored: Boolean) {
        // Deep link takes highest priority over any saved scene state
        val deepLinkUri = intent?.data
        if (deepLinkUri != null) {
            intent = Intent(intent).apply { data = null }   // consume so it is not re-processed
            handleDeepLink(deepLinkUri)
            return
        }

        val savedScene = savedInstanceState.enumValueOrNull<AppScene>(KEY_APP_SCENE)
        val savedPreviousScene = savedInstanceState.enumValueOrNull<AppScene>(KEY_PREVIOUS_SCENE)
            ?: AppScene.HOME
        val restoredUrl = web.webView.url.orEmpty()
        when {
            savedScene == AppScene.HOME -> state.restoreScene(AppScene.HOME, AppScene.HOME, restoredUrl)
            savedScene == AppScene.SETTINGS -> {
                val restoredPreviousScene = if (
                    savedPreviousScene == AppScene.BROWSER && !webViewRestored
                ) AppScene.HOME else savedPreviousScene
                state.restoreScene(
                    scene = AppScene.SETTINGS,
                    previousScene = restoredPreviousScene,
                    browserUrl = restoredUrl,
                )
                if (webViewRestored && restoredPreviousScene == AppScene.BROWSER) {
                    synchronizeRestoredWebState()
                }
            }
            savedScene == AppScene.BROWSER && webViewRestored -> {
                state.restoreScene(AppScene.BROWSER, AppScene.BROWSER, restoredUrl)
                synchronizeRestoredWebState()
            }
            savedScene == AppScene.BROWSER -> openUrl(
                session.lastSafeUrl.ifBlank { settings.homeUrl },
                NavigationSource.RESTORE,
            )
            webViewRestored -> {
                state.showBrowser(restoredUrl)
                synchronizeRestoredWebState()
            }
            else -> openStartupPage()
        }
    }

    private fun synchronizeRestoredWebState() {
        val restoredUrl = web.webView.url.orEmpty()
        if (restoredUrl.isBlank()) return
        state.historyChanged(
            navigationId = state.state.navigationId,
            url = restoredUrl,
            displayHost = UrlPolicy.hostOf(restoredUrl).orEmpty(),
            canGoBack = web.canGoBack(),
            canGoForward = web.canGoForward(),
        )
        state.titleChanged(state.state.navigationId, web.webView.title.orEmpty())
    }

    private fun configureViews() {
        ViewCompat.setAccessibilityHeading(settingsView.settingsTitle, true)
        ViewCompat.setAccessibilityHeading(browser.errorTitle, true)
        home.homeSearchSurface.setBackdropSource(binding.themeWallpaper)
        browser.browserToolbar.setBackdropSource(web.webView)
        browser.findBar.setBackdropSource(web.webView)
        browser.errorCard.setBackdropSource(binding.themeWallpaper)
        listOf(settingsView.browsingSettingsCard, settingsView.appearanceSettingsCard,
            settingsView.privacySettingsCard, settingsView.dataSettingsCard, settingsView.aboutSettingsCard)
            .forEach { it.setBackdropSource(binding.themeWallpaper) }
        home.homeGoButton.setOnClickListener { submitInput(home.homeInput.text?.toString().orEmpty(), true) }
        home.homeInput.setOnEditorActionListener { _, action, _ ->
            (action == EditorInfo.IME_ACTION_GO).also { if (it) home.homeGoButton.performClick() }
        }
        home.continueButton.setOnClickListener { openUrl(session.lastSafeUrl, NavigationSource.RESTORE) }
        home.homeSettingsButton.setOnClickListener { showSettings() }
        browser.backButton.setOnClickListener { handleBack() }
        browser.forwardButton.setOnClickListener { web.goForward() }
        browser.reloadButton.setOnClickListener { if (state.state.isLoading) web.stopLoading() else retry() }
        browser.addressTitle.setOnClickListener { editAddress(true) }
        browser.addressGoButton.setOnClickListener { submitInput(browser.addressInput.text?.toString().orEmpty(), false) }
        browser.addressCancelButton.setOnClickListener { editAddress(false) }
        browser.addressInput.setOnEditorActionListener { _, action, _ ->
            (action == EditorInfo.IME_ACTION_GO).also { if (it) browser.addressGoButton.performClick() }
        }
        browser.moreButton.setOnClickListener { openMoreActions() }
        browser.toolbarRevealHandle.setOnClickListener { toolbarEvent(ToolbarEvent.EDGE_REVEAL) }
        browser.swipeRefresh.setOnChildScrollUpCallback { _, _ -> web.webView.canScrollVertically(-1) }
        browser.swipeRefresh.setOnRefreshListener { retry() }
        web.webView.setOnScrollChangeListener { _, _, y, _, _ ->
            val direction = y - lastScrollY
            lastScrollY = y
            if (kotlin.math.abs(direction) >= 12) toolbarEvent(
                if (direction > 0) ToolbarEvent.SCROLL_DOWN else ToolbarEvent.SCROLL_UP,
            )
        }
        browser.findCloseButton.setOnClickListener { closeFind() }
        browser.findNextButton.setOnClickListener { web.findNext(true) }
        browser.findPreviousButton.setOnClickListener { web.findNext(false) }
        browser.findInput.setOnEditorActionListener { _, _, _ -> web.findAll(browser.findInput.text?.toString().orEmpty()); true }
        web.setFindListener { active, count, _ ->
            browser.findResult.text = if (count == 0) getString(R.string.find_no_result) else "$active/$count"
        }
        browser.errorPrimaryButton.setOnClickListener { runErrorAction(errorPrimaryAction) }
        browser.errorSecondaryButton.setOnClickListener { runErrorAction(errorSecondaryAction) }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val values = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(values.left, values.top, values.right, values.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = handleBack()
        })
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { preferences.settings.collect { if (it != settings) { settings = it; applySettings(it) } } }
                launch { preferences.session.collect { session = it; renderHome() } }
                launch { history.observeRecent().collect { recent = it; renderHome() } }
                launch { bookmarks.observePinned().collect { pinned = it; renderHome() } }
            }
        }
    }

    private fun openStartupPage() {
        when (settings.startBehavior) {
            StartBehavior.BLANK -> showHome()
            StartBehavior.HOME -> if (settings.homeUrl.isBlank()) showHome() else openUrl(settings.homeUrl, NavigationSource.RESTORE)
            StartBehavior.LAST_PAGE -> openUrl(session.lastSafeUrl.ifBlank { settings.homeUrl }, NavigationSource.RESTORE)
        }
    }

    private fun submitInput(value: String, fromHome: Boolean) {
        when (val input = InputResolver.resolve(value, SearchEngine.fromId(settings.searchEngineId))) {
            is InputResolution.Invalid -> {
                if (fromHome) {
                    home.homeInputError.setText(R.string.invalid_input)
                    home.homeInputError.isVisible = true
                } else browser.addressInput.error = getString(R.string.invalid_input)
            }
            is InputResolution.Url -> navigate(NavigationPolicy(settings.allowLocalNetwork).decide(input, NavigationSource.MANUAL_INPUT), input.normalized)
            is InputResolution.Search -> navigate(NavigationPolicy(settings.allowLocalNetwork).decide(input, NavigationSource.MANUAL_INPUT), input.url)
        }
    }

    private fun openUrl(url: String, source: NavigationSource = NavigationSource.LINK_CLICK) {
        if (url.isNotBlank()) navigate(NavigationPolicy(settings.allowLocalNetwork).decide(url, source), url)
        else showHome()
    }

    private fun navigate(decision: NavigationDecision, attempted: String) {
        when (decision) {
            is NavigationDecision.Allow -> {
                state.showBrowser(decision.url)
                render()
                web.loadUrl(decision.url)
                hideKeyboard()
            }
            is NavigationDecision.OpenExternal -> externalActions.execute(decision.action)
            is NavigationDecision.Block -> onNavigationBlocked(decision.reason, attempted)
        }
    }

    private fun showHome() {
        state.showHome()
        render()
    }

    private fun showSettings() {
        state.showSettings()
        WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
        render()
    }

    private fun handleBack() {
        if (customView != null) { hideCustomView(); return }
        if (browser.findBar.isVisible) { closeFind(); return }
        if (browser.toolbarEditRow.isVisible) { editAddress(false); return }
        when (state.backDecision()) {
            BackDecision.NavigateWebHistory -> web.goBack()
            BackDecision.ShowHome -> showHome()
            is BackDecision.LeaveSettings -> { state.closeSettings(); render() }
            BackDecision.ExitApplication -> finish()
        }
    }

    private fun render() {
        val current = state.state
        val sceneChanged = renderedScene != current.scene
        binding.homeContainer.isVisible = customView == null && current.scene == AppScene.HOME
        binding.browserContainer.isVisible = customView == null && current.scene == AppScene.BROWSER
        binding.settingsContainer.isVisible = customView == null && current.scene == AppScene.SETTINGS
        if (sceneChanged) animateSceneEntry(current.scene)
        renderedScene = current.scene
        browser.pageProgress.setProgress(current.progress, !shouldReduceMotion())
        browser.pageProgress.isVisible = current.isLoading && current.progress in 1..99
        browser.loadingStatus.isVisible = current.isLoading
        browser.swipeRefresh.isRefreshing = current.isLoading && current.progress == 0
        browser.backButton.isEnabled = current.canGoBack || current.scene == AppScene.BROWSER
        browser.forwardButton.isEnabled = current.canGoForward
        browser.reloadButton.setImageResource(if (current.isLoading) R.drawable.ic_stop else R.drawable.ic_refresh)
        browser.addressTitle.text = current.displayHost.ifBlank { current.title.ifBlank { current.url.ifBlank { getString(R.string.address_bar_empty) } } }
        browser.securityIcon.isVisible = current.isSecure
        renderError(current.error)
        renderToolbar(current.toolbarVisibility)
        when (current.scene) {
            AppScene.HOME -> renderHome()
            AppScene.SETTINGS -> settingsController.render(settings)
            AppScene.BROWSER -> Unit
        }
    }

    private fun renderHome() {
        if (!::home.isInitialized || state.state.scene != AppScene.HOME) return
        home.continueButton.isVisible = session.lastSafeUrl.isNotBlank()
        home.pinnedTitle.isVisible = pinned.isNotEmpty()
        home.pinnedGrid.removeAllViews()
        pinned.forEach { bookmark ->
            val shortcut = layoutInflater.inflate(
                R.layout.item_home_pinned,
                home.pinnedGrid,
                false,
            ) as com.google.android.material.button.MaterialButton
            home.pinnedGrid.addView(shortcut.apply {
                text = bookmark.title
                setOnClickListener { openUrl(bookmark.url) }
                layoutParams = (layoutParams as android.widget.GridLayout.LayoutParams).apply {
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                }
            })
        }
        home.recentTitle.isVisible = recent.isNotEmpty()
        home.recentList.removeAllViews()
        recent.forEach { item ->
            val recentButton = layoutInflater.inflate(
                R.layout.item_home_recent,
                home.recentList,
                false,
            ) as com.google.android.material.button.MaterialButton
            home.recentList.addView(recentButton.apply {
                text = item.title.ifBlank { item.host }
                setOnClickListener { openUrl(item.url) }
            })
        }
        home.homeEmptyState.isVisible = pinned.isEmpty() && recent.isEmpty()
    }

    private fun renderError(error: BrowserError?) {
        browser.errorOverlay.isVisible = error != null
        if (error == null) return
        val present = BrowserErrorPresenter.present(error)
        browser.errorTitle.setText(present.title)
        browser.errorMessage.text = present.messageArgument?.let { getString(present.message, it) } ?: getString(present.message)
        errorPrimaryAction = present.primaryAction
        errorSecondaryAction = present.secondaryAction
        browser.errorPrimaryButton.text = errorActionLabel(present.primaryAction)
        browser.errorSecondaryButton.isVisible = present.secondaryAction != BrowserErrorAction.NONE
        browser.errorSecondaryButton.text = errorActionLabel(present.secondaryAction)
    }

    private fun errorActionLabel(action: BrowserErrorAction): String = getString(
        when (action) {
            BrowserErrorAction.RETRY -> R.string.retry
            BrowserErrorAction.GO_BACK -> R.string.go_back
            BrowserErrorAction.OPEN_NETWORK_SETTINGS -> R.string.open_network_settings
            BrowserErrorAction.COPY_URL -> R.string.copy_url
            BrowserErrorAction.NONE -> R.string.close
        },
    )

    private fun runErrorAction(action: BrowserErrorAction) {
        when (action) {
            BrowserErrorAction.RETRY -> retry()
            BrowserErrorAction.GO_BACK -> handleBack()
            BrowserErrorAction.OPEN_NETWORK_SETTINGS -> runCatching {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }.onFailure { toast(R.string.no_external_app) }
            BrowserErrorAction.COPY_URL -> copyUrl()
            BrowserErrorAction.NONE -> Unit
        }
    }

    private fun toolbarEvent(event: ToolbarEvent) {
        state.toolbarChanged(toolbar.onEvent(event, settings.toolbarAutoHide))
        renderToolbar(state.state.toolbarVisibility)
        if (event == ToolbarEvent.PAGE_FINISHED && settings.toolbarAutoHide) {
            hideToolbarJob?.cancel()
            hideToolbarJob = lifecycleScope.launch {
                delay(3000)
                state.toolbarChanged(toolbar.onEvent(ToolbarEvent.IDLE_TIMEOUT, true))
                renderToolbar(state.state.toolbarVisibility)
            }
        }
    }

    private fun renderToolbar(visibility: ToolbarVisibility) {
        browser.toolbarRevealHandle.isVisible = visibility != ToolbarVisibility.VISIBLE
        val hidden = if (settings.toolbarPosition == ToolbarPosition.BOTTOM) 120.dp.toFloat() else -120.dp.toFloat()
        val targetTranslation = when (visibility) {
            ToolbarVisibility.VISIBLE -> 0f
            ToolbarVisibility.COLLAPSED -> hidden * .55f
            ToolbarVisibility.HIDDEN -> hidden
        }
        browser.browserToolbar.animate().cancel()
        if (shouldReduceMotion()) {
            browser.browserToolbar.translationY = targetTranslation
        } else {
            browser.browserToolbar.animate()
                .translationY(targetTranslation)
                .setDuration(TOOLBAR_ANIMATION_MILLIS)
                .start()
        }
    }

    private fun animateSceneEntry(scene: AppScene) {
        val target = when (scene) {
            AppScene.HOME -> binding.homeContainer
            AppScene.BROWSER -> binding.browserContainer
            AppScene.SETTINGS -> binding.settingsContainer
        }
        target.animate().cancel()
        if (shouldReduceMotion()) {
            target.alpha = 1f
            return
        }
        target.alpha = 0f
        target.animate()
            .alpha(1f)
            .setDuration(SCENE_FADE_MILLIS)
            .start()
    }

    private fun shouldReduceMotion(): Boolean = MotionPolicy.shouldReduce(
        userPreference = settings.reduceMotion,
        systemAnimatorsEnabled = ValueAnimator.areAnimatorsEnabled(),
    )

    private fun editAddress(editing: Boolean) {
        browser.toolbarCompactRow.isVisible = !editing
        browser.toolbarEditRow.isVisible = editing
        toolbarEvent(if (editing) ToolbarEvent.EDIT_STARTED else ToolbarEvent.EDIT_FINISHED)
        if (editing) {
            browser.addressInput.setText(state.state.url)
            browser.addressInput.requestFocus()
            browser.addressInput.selectAll()
            getSystemService<InputMethodManager>()?.showSoftInput(browser.addressInput, 0)
        } else hideKeyboard()
    }

    private fun closeFind() {
        browser.findBar.isVisible = false
        web.clearFindMatches()
        hideKeyboard()
    }

    private fun retry() {
        val fallbackUrl = session.lastSafeUrl.ifBlank { settings.homeUrl }
        val url = if (state.state.error == BrowserError.RendererGone) {
            state.state.recoveryUrl(fallbackUrl)
        } else {
            state.state.url.ifBlank { fallbackUrl }
        }
        openUrl(url, NavigationSource.RESTORE)
    }

    private fun openMoreActions() {
        BrowserMoreActionsBottomSheet(this) { action ->
            when (action) {
                BrowserMoreAction.SHARE -> share()
                BrowserMoreAction.COPY_URL -> copyUrl()
                BrowserMoreAction.OPEN_EXTERNAL -> openExternalBrowser()
                BrowserMoreAction.FIND_IN_PAGE -> { browser.findBar.isVisible = true; browser.findInput.requestFocus() }
                BrowserMoreAction.TOGGLE_DESKTOP_MODE -> {
                    web.setDesktopMode(!state.state.isDesktopMode)
                    state.desktopModeChanged(!state.state.isDesktopMode)
                }
                BrowserMoreAction.TOGGLE_BOOKMARK -> addBookmark()
                BrowserMoreAction.OPEN_SETTINGS -> showSettings()
                BrowserMoreAction.GO_HOME -> showHome()
            }
        }.show(BrowserMoreActionsState(state.state.url.isNotBlank(), state.state.isDesktopMode, false))
    }

    private fun share(url: String = state.state.url) {
        if (!UrlPolicy.isAllowed(url)) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), getString(R.string.share_page)))
    }

    private fun openExternalBrowser() {
        state.state.url.takeIf(UrlPolicy::isAllowed)?.let {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }.onFailure { toast(R.string.no_external_app) }
        }
    }

    private fun addBookmark() {
        val url = state.state.url.takeIf(UrlPolicy::isAllowed) ?: return
        lifecycleScope.launch { bookmarks.addOrUpdate(url, state.state.title.ifBlank { url }); toast(R.string.favorite_added) }
    }

    private fun copyUrl(url: String = state.state.url) {
        url.takeIf(String::isNotBlank)?.let {
            getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("URL", it))
            toast(R.string.copied_to_clipboard)
        }
    }

    private fun applySettings(value: BrowserSettings) {
        val night = when (value.themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != night) AppCompatDelegate.setDefaultNightMode(night)
        binding.themeWallpaper.setImageResource(if (value.darkThemeEnabled) R.drawable.wallpaper_dark else R.drawable.wallpaper_light)
        value.backgroundUri?.let { binding.themeWallpaper.setImageURI(Uri.parse(it)) }
        web.applySettings(value)
        state.desktopModeChanged(value.desktopModeDefault)
        listOf(
            home.homeSearchSurface,
            browser.browserToolbar,
            browser.findBar,
            browser.errorCard,
            settingsView.browsingSettingsCard,
            settingsView.appearanceSettingsCard,
            settingsView.privacySettingsCard,
            settingsView.dataSettingsCard,
            settingsView.aboutSettingsCard,
        ).forEach {
            it.setReducedTransparency(value.reducedTransparency)
        }
        val controller = WindowInsetsControllerCompat(window, binding.root)
        if (value.fullscreenEnabled && state.state.scene == AppScene.BROWSER) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        (browser.browserToolbar.layoutParams as? android.widget.FrameLayout.LayoutParams)?.let {
            it.gravity = if (value.toolbarPosition == ToolbarPosition.TOP) Gravity.TOP else Gravity.BOTTOM
            browser.browserToolbar.layoutParams = it
        }
        render()
    }

    private fun hideKeyboard() {
        getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun toast(res: Int) = android.widget.Toast.makeText(this, res, android.widget.Toast.LENGTH_SHORT).show()
    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    override fun currentSettings(): BrowserSettings = settings
    override fun currentNavigationPolicy(): NavigationPolicy = NavigationPolicy(settings.allowLocalNetwork)
    override fun isNetworkAvailable(): Boolean? {
        val manager = getSystemService<ConnectivityManager>() ?: return null
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false
    }
    override fun onPageStarted(url: String): Long {
        val id = state.navigationStarted(url, UrlPolicy.hostOf(url).orEmpty())
        toolbarEvent(ToolbarEvent.PAGE_STARTED); render(); return id
    }
    override fun onProgressChanged(navigationId: Long, progress: Int) { state.progressChanged(navigationId, progress); render() }
    override fun onTitleChanged(navigationId: Long, title: String) { state.titleChanged(navigationId, title); render() }
    override fun onFaviconChanged(navigationId: Long, pageUrl: String, icon: Bitmap?) {
        if (icon == null || settings.privateMode) {
            state.faviconChanged(navigationId, null)
            render()
            return
        }
        val url = UrlPolicy.normalizeForNavigation(pageUrl) ?: return
        if (navigationId != state.state.navigationId ||
            UrlPolicy.normalizeForNavigation(state.state.url) != url
        ) return
        lifecycleScope.launch {
            val faviconUri = persistFavicon(url, icon)
            state.faviconChanged(navigationId, faviconUri)
            if (faviconUri != null && navigationId == state.state.navigationId) {
                history.updateFavicon(url, faviconUri)
            }
            render()
        }
    }
    override fun onLongPressTarget(url: String, isImage: Boolean) {
        val target = UrlPolicy.normalizeForNavigation(url) ?: return
        val labels = arrayOf(
            getString(if (isImage) R.string.open_image else R.string.open_link),
            getString(if (isImage) R.string.copy_image_link else R.string.copy_link),
            getString(if (isImage) R.string.share_image_link else R.string.share_link),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(if (isImage) R.string.image_actions else R.string.link_actions)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> openUrl(target)
                    1 -> copyUrl(target)
                    2 -> share(target)
                }
            }
            .show()
    }
    override fun onVisitedHistoryChanged(navigationId: Long, url: String, canGoBack: Boolean, canGoForward: Boolean) {
        state.historyChanged(navigationId, url, UrlPolicy.hostOf(url).orEmpty(), canGoBack, canGoForward); render()
    }
    override fun onPageFinished(navigationId: Long, url: String, title: String, canGoBack: Boolean, canGoForward: Boolean) {
        state.navigationFinished(navigationId, url, UrlPolicy.hostOf(url).orEmpty(), title, canGoBack, canGoForward)
        if (navigationId == state.state.navigationId && state.state.error == null) lifecycleScope.launch {
            if (PrivateSessionPolicy.shouldPersistSession(settings)) {
                preferences.updateSession(url, title, System.currentTimeMillis(), AppScene.BROWSER.name)
            } else {
                preferences.clearSession()
            }
            history.recordVisit(
                url,
                title,
                faviconUri = state.state.faviconPath,
                isPrivate = settings.privateMode,
            )
        }
        toolbarEvent(ToolbarEvent.PAGE_FINISHED); render()
    }
    override fun onPageError(navigationId: Long, error: BrowserError, failedUrl: String) { state.navigationFailed(navigationId, error); render() }
    override fun onNavigationBlocked(reason: NavigationBlockReason, attemptedUrl: String) {
        if (state.state.scene == AppScene.HOME) { home.homeInputError.setText(if (reason == NavigationBlockReason.LOCAL_NETWORK_BLOCKED) R.string.local_address_blocked else R.string.navigation_blocked); home.homeInputError.isVisible = true }
        else { state.navigationFailed(state.state.navigationId, BrowserError.NavigationBlocked); render() }
    }
    override fun onExternalAction(action: ExternalAction) = externalActions.execute(action)
    override fun onDownloadRequested(request: WebDownloadRequest) = downloadsCoordinator.requestDownload(request, settings.allowLocalNetwork)
    override fun onFileChooserRequested(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean = try {
        fileCallback?.onReceiveValue(null); fileCallback = callback; chooser.launch(params.createIntent()); true
    } catch (_: Exception) { fileCallback = null; callback.onReceiveValue(null); toast(R.string.file_chooser_failed); false }
    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        customView = view; customCallback = callback; binding.customViewContainer.addView(view); binding.customViewContainer.isVisible = true; render()
    }
    override fun onHideCustomView() = hideCustomView()
    private fun hideCustomView() {
        binding.customViewContainer.removeAllViews(); binding.customViewContainer.isVisible = false
        customView = null; customCallback?.onCustomViewHidden(); customCallback = null; render()
    }
    override fun onRendererGone(lastNavigationId: Long) { state.rendererGone(BrowserError.RendererGone); render() }

    override fun onBack() { state.closeSettings(); render() }
    override fun onSettingsChanged(settings: BrowserSettings) {
        val enteringPrivateMode = !this.settings.privateMode && settings.privateMode
        lifecycleScope.launch {
            preferences.update(settings)
            if (enteringPrivateMode) preferences.clearSession()
        }
    }
    override fun onClearBrowsingData(target: SettingsSceneController.BrowsingDataTarget) {
        val selection = when (target) {
            SettingsSceneController.BrowsingDataTarget.CACHE -> BrowsingDataSelection(cache = true)
            SettingsSceneController.BrowsingDataTarget.COOKIES -> BrowsingDataSelection(cookies = true)
            SettingsSceneController.BrowsingDataTarget.WEB_STORAGE -> BrowsingDataSelection(webStorage = true)
            SettingsSceneController.BrowsingDataTarget.HISTORY -> BrowsingDataSelection(history = true)
            SettingsSceneController.BrowsingDataTarget.FORM_DATA -> BrowsingDataSelection(formData = true)
            SettingsSceneController.BrowsingDataTarget.ALL -> BrowsingDataSelection.ALL
        }
        MaterialAlertDialogBuilder(this).setTitle(R.string.clear_site_data).setMessage(R.string.clear_site_data_confirmation)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.clear_site_data) { _, _ ->
                lifecycleScope.launch { dataManager.clear(selection, web.webView); toast(R.string.site_data_cleared) }
            }.show()
    }
    override fun onChooseBackground() = backgroundPicker.launch(arrayOf("image/*"))
    override fun onClearBackground() { lifecycleScope.launch { preferences.update(settings.copy(backgroundUri = null)) } }
    override fun onOpenLibrary(target: SettingsSceneController.LibraryTarget) {
        when (target) {
            SettingsSceneController.LibraryTarget.HISTORY -> showHistoryLibrary()
            SettingsSceneController.LibraryTarget.BOOKMARKS -> showBookmarkLibrary()
            SettingsSceneController.LibraryTarget.DOWNLOADS -> showDownloadLibrary()
        }
    }
    override fun onOpenAbout(target: SettingsSceneController.AboutTarget) {
        when (target) {
            SettingsSceneController.AboutTarget.PRIVACY_AND_TRUST -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.privacy_and_trust)
                .setMessage(R.string.privacy_statement_body)
                .setPositiveButton(R.string.close, null)
                .show()
            SettingsSceneController.AboutTarget.OPEN_SOURCE_LICENSES -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.open_source_licenses)
                .setMessage(R.string.licenses_body)
                .setPositiveButton(R.string.close, null)
                .show()
            SettingsSceneController.AboutTarget.EXPORT_DIAGNOSTICS -> exportDiagnostics()
            SettingsSceneController.AboutTarget.MANUAL_TEST_TOOLS -> showManualTestTools()
        }
    }

    private fun showHistoryLibrary() {
        val binding = DialogHistoryListBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this).apply { setContentView(binding.root) }
        val controller = HistoryListController(binding, object : HistoryListController.Callbacks {
            override fun onOpenRequested(entry: HistoryEntity) { dialog.dismiss(); openUrl(entry.url) }
            override fun onDeleteRequested(entry: HistoryEntity) { lifecycleScope.launch { history.deleteById(entry.id) } }
            override fun onClearAllRequested() {
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.clear_history)
                    .setMessage(R.string.clear_site_data_confirmation)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.clear_history) { _, _ -> lifecycleScope.launch { history.clear() } }
                    .show()
            }
            override fun onDismissRequested() = dialog.dismiss()
        })
        val job = lifecycleScope.launch { history.observeAll().collect(controller::submitEntries) }
        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
    }

    private fun showBookmarkLibrary() {
        val binding = DialogBookmarkListBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this).apply { setContentView(binding.root) }
        val controller = BookmarkListController(binding, object : BookmarkListController.Callbacks {
            override fun onOpenRequested(bookmark: BookmarkEntity) { dialog.dismiss(); openUrl(bookmark.url) }
            override fun onEditRequested(bookmark: BookmarkEntity) = showBookmarkEditor(bookmark)
            override fun onDeleteRequested(bookmark: BookmarkEntity) { lifecycleScope.launch { bookmarks.delete(bookmark.id) } }
            override fun onPinnedChangeRequested(bookmark: BookmarkEntity, pinned: Boolean) {
                lifecycleScope.launch { bookmarks.update(bookmark.id, bookmark.url, bookmark.title, pinned, bookmark.sortOrder) }
            }
            override fun onReorderRequested(idsInOrder: List<Long>) { lifecycleScope.launch { bookmarks.reorder(idsInOrder) } }
            override fun onDismissRequested() = dialog.dismiss()
        })
        val job = lifecycleScope.launch { bookmarks.observeAll().collect(controller::submitBookmarks) }
        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
    }

    private fun showBookmarkEditor(bookmark: BookmarkEntity?) {
        val binding = DialogEditBookmarkBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this).apply { setContentView(binding.root) }
        val controller = BookmarkEditorController(binding, object : BookmarkEditorController.Callbacks {
            override fun onSaveRequested(draft: BookmarkEditDraft) {
                val normalized = UrlPolicy.normalizeForNavigation(draft.url)
                if (normalized == null) { binding.bookmarkUrlLayout.error = getString(R.string.invalid_url); return }
                lifecycleScope.launch {
                    if (draft.id == null) bookmarks.addOrUpdate(normalized, draft.title, draft.pinnedToHome, draft.sortOrder)
                    else bookmarks.update(draft.id, normalized, draft.title, draft.pinnedToHome, draft.sortOrder)
                    dialog.dismiss()
                }
            }
            override fun onCancelRequested() = dialog.dismiss()
        })
        controller.bind(bookmark)
        dialog.show()
    }

    private fun showDownloadLibrary() {
        val binding = DialogDownloadListBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this).apply { setContentView(binding.root) }
        val controller = DownloadListController(binding, object : DownloadListController.Callbacks {
            override fun onOpenRequested(download: com.example.slimbrowser.data.download.DownloadEntity) = downloadsCoordinator.open(download)
            override fun onRetryRequested(download: com.example.slimbrowser.data.download.DownloadEntity) =
                downloadsCoordinator.retry(download, settings.allowLocalNetwork)
            override fun onDeleteRequested(download: com.example.slimbrowser.data.download.DownloadEntity) =
                downloadsCoordinator.deleteRecord(download)
            override fun onClearAllRequested() { lifecycleScope.launch { downloads.clear() } }
            override fun onDismissRequested() = dialog.dismiss()
        })
        val job = lifecycleScope.launch { downloads.observeAll().collect(controller::submitDownloads) }
        dialog.setOnDismissListener { job.cancel() }
        dialog.show()
    }

    private fun exportDiagnostics() {
        diagnosticsText = listOf(
            "SlimBrowser diagnostics",
            "version=" + com.example.slimbrowser.BuildConfig.VERSION_NAME,
            "scene=" + state.state.scene,
            "urlHost=" + state.state.displayHost,
            "timestamp=" + System.currentTimeMillis(),
        ).joinToString("\n")
        diagnosticsWriter.launch("slimbrowser-diagnostics.txt")
    }

    private fun showManualTestTools() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.manual_test_tools)
            .setItems(
                arrayOf(
                    getString(R.string.clear_site_data),
                    getString(R.string.simulate_offline_error),
                    getString(R.string.simulate_http_error),
                    getString(R.string.simulate_renderer_error),
                    getString(R.string.open_compatibility_test_page),
                ),
            ) { _, which ->
                when (which) {
                    0 -> lifecycleScope.launch { dataManager.clear(BrowsingDataSelection.ALL, web.webView) }
                    1 -> showSyntheticError(BrowserError.Offline)
                    2 -> showSyntheticError(BrowserError.Http(500))
                    3 -> web.recreateForRendererRecoveryTest()
                    4 -> openUrl(LOCAL_TEST_PAGE_URL, NavigationSource.MANUAL_INPUT)
                }
            }
            .show()
    }

    private fun showSyntheticError(error: BrowserError) {
        state.showBrowser(state.state.url)
        state.navigationFailed(state.state.navigationId, error)
        render()
    }

    private suspend fun persistFavicon(url: String, icon: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val directory = File(filesDir, "favicons").also(File::mkdirs)
            val target = File(directory, "$hash.png")
            val temporary = File.createTempFile("$hash-", ".tmp", directory)
            try {
                FileOutputStream(temporary).use { output ->
                    check(icon.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
                runCatching {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }.getOrElse {
                    Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
                Uri.fromFile(target).toString()
            } finally {
                temporary.delete()
            }
        }.getOrNull()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        Bundle().also { webState ->
            if (web.saveState(webState)) outState.putBundle(KEY_WEB_STATE, webState)
        }
        outState.putString(KEY_APP_SCENE, state.state.scene.name)
        outState.putString(KEY_PREVIOUS_SCENE, state.state.previousScene.name)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onPause() { web.onPause(); super.onPause() }
    override fun onStop() {
        if (PrivateSessionPolicy.shouldClearOnExit(settings, isFinishing) && !privateCleanupStarted) {
            privateCleanupStarted = true
            lifecycleScope.launch {
                dataManager.clear(
                    BrowsingDataSelection(
                        cache = true,
                        cookies = true,
                        webStorage = true,
                        formData = true,
                        session = true,
                    ),
                    web.webView,
                )
            }
        }
        super.onStop()
    }
    override fun onDestroy() { hideToolbarJob?.cancel(); fileCallback?.onReceiveValue(null); downloadsCoordinator.close(); externalActions.close(); web.destroy(); super.onDestroy() }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); applySettings(settings) }

    /** Handle deep link intents when the app is already running (singleTask launchMode). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val data = intent.data
        if (data != null) {
            handleDeepLink(data)
        }
    }

    /**
     * Process a deep link URI.
     *
     * Supports three deep link types:
     * 1. **URL Scheme** — e.g. `slimbrowser://open?url=https://example.com`
     * 2. **Android App Links** — HTTPS links from verified domains (e.g. `https://www.slimbrowser.com/...`)
     * 3. **Universal Links** — HTTPS links under `/u` path prefix (e.g. `https://www.slimbrowser.com/u/...`)
     *
     * For URL Scheme links, the `url` query parameter is extracted and navigated to.
     * For App Links / Universal Links the full HTTPS URL is navigated to directly.
     */
    private fun handleDeepLink(uri: Uri) {
        val urlString = when (uri.scheme) {
            SCHEME_SLIMBROWSER -> {
                // URL Scheme: slimbrowser://open?url=<encoded-url>
                // slimbrowser://navigate?url=<encoded-url>
                // Falls back to home if no url parameter is provided.
                uri.getQueryParameter(PARAM_URL).orEmpty()
            }
            SCHEME_HTTPS, SCHEME_HTTP -> {
                // App Links / Universal Links: the full HTTPS URL
                uri.toString()
            }
            else -> uri.toString()
        }
        if (urlString.isNotBlank()) {
            openUrl(urlString, NavigationSource.DEEP_LINK)
        } else {
            showHome()
        }
    }

    private companion object {
        const val KEY_WEB_STATE = "web_state"
        const val KEY_APP_SCENE = "app_scene"
        const val KEY_PREVIOUS_SCENE = "previous_scene"
        const val LOCAL_TEST_PAGE_URL = "file:///android_asset/slimbrowser-test.html"
        const val TOOLBAR_ANIMATION_MILLIS = 180L
        const val SCENE_FADE_MILLIS = 140L
        const val SCHEME_SLIMBROWSER = "slimbrowser"
        const val SCHEME_HTTPS = "https"
        const val SCHEME_HTTP = "http"
        const val PARAM_URL = "url"
    }
}

private inline fun <reified T : Enum<T>> Bundle?.enumValueOrNull(key: String): T? =
    this?.getString(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
