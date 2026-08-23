package com.example.slimbrowser.ui.settings

import android.app.Activity
import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.annotation.StringRes
import androidx.core.widget.doAfterTextChanged
import com.example.slimbrowser.BuildConfig
import com.example.slimbrowser.R
import com.example.slimbrowser.data.BrowserSettings
import com.example.slimbrowser.data.StartBehavior
import com.example.slimbrowser.data.ThemeMode
import com.example.slimbrowser.data.ToolbarPosition
import com.example.slimbrowser.databinding.ViewSettingsBinding
import com.example.slimbrowser.domain.SearchEngine
import com.example.slimbrowser.domain.UrlPolicy

/**
 * Owns the settings scene's view-only behavior.
 *
 * Persistence, confirmations, document pickers, and library navigation remain Activity concerns;
 * every user action is surfaced through [Callback]. Calling [render] is side-effect free and never
 * invokes [Callback.onSettingsChanged].
 */
class SettingsSceneController(
    activity: Activity,
    private val binding: ViewSettingsBinding,
    initialSettingsProvider: () -> BrowserSettings,
    private val callback: Callback,
) {
    enum class BrowsingDataTarget {
        CACHE,
        COOKIES,
        WEB_STORAGE,
        HISTORY,
        FORM_DATA,
        ALL,
    }

    enum class LibraryTarget {
        HISTORY,
        BOOKMARKS,
        DOWNLOADS,
    }

    enum class AboutTarget {
        PRIVACY_AND_TRUST,
        OPEN_SOURCE_LICENSES,
        EXPORT_DIAGNOSTICS,
        MANUAL_TEST_TOOLS,
    }

    interface Callback {
        fun onBack()

        /** Called only with a locally valid, complete settings snapshot. */
        fun onSettingsChanged(settings: BrowserSettings)

        fun onClearBrowsingData(target: BrowsingDataTarget)

        fun onChooseBackground()

        fun onClearBackground()

        fun onOpenLibrary(target: LibraryTarget)

        fun onOpenAbout(target: AboutTarget)
    }

    private data class SearchEngineChoice(
        val engine: SearchEngine,
        val label: String,
    )

    private val context: Context = activity
    private val searchEngineChoices = SearchEngine.entries.map { engine ->
        SearchEngineChoice(engine, context.getString(engine.labelResource))
    }
    private var rendering = false
    private var settings = initialSettingsProvider()

    init {
        configureSearchEngineDropdown()
        bindSettingsControls()
        bindActions()
        render(settings)
        renderVersionInformation()
    }

    /** Renders every persisted setting while suppressing all change callbacks. */
    fun render(settings: BrowserSettings) {
        this.settings = settings
        rendering = true
        try {
            binding.settingsHomeUrl.setText(settings.homeUrl)
            binding.settingsHomeUrl.setSelection(binding.settingsHomeUrl.text?.length ?: 0)
            binding.homeUrlLayout.error = null

            val searchEngine = SearchEngine.fromId(settings.searchEngineId)
            val searchLabel = searchEngineChoices
                .first { it.engine == searchEngine }
                .label
            binding.searchEngineInput.setText(searchLabel, false)

            binding.startBehaviorGroup.check(
                when (settings.startBehavior) {
                    StartBehavior.HOME -> R.id.startHomeRadio
                    StartBehavior.LAST_PAGE -> R.id.startLastPageRadio
                    StartBehavior.BLANK -> R.id.startBlankRadio
                },
            )
            binding.fullscreenSwitch.isChecked = settings.fullscreenEnabled
            binding.toolbarAutoHideSwitch.isChecked = settings.toolbarAutoHide
            binding.toolbarPositionGroup.check(
                when (settings.toolbarPosition) {
                    ToolbarPosition.BOTTOM -> R.id.toolbarBottomRadio
                    ToolbarPosition.TOP -> R.id.toolbarTopRadio
                },
            )
            binding.desktopModeSwitch.isChecked = settings.desktopModeDefault
            binding.textZoomSeekBar.max = TEXT_ZOOM_RANGE
            binding.textZoomSeekBar.progress = settings.textZoom
                .coerceIn(BrowserSettings.MIN_TEXT_ZOOM, BrowserSettings.MAX_TEXT_ZOOM) -
                BrowserSettings.MIN_TEXT_ZOOM
            renderTextZoom(settings.textZoom)
            binding.darkWebModeSwitch.isChecked = settings.darkWebMode

            binding.themeModeGroup.check(
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> R.id.themeSystemRadio
                    ThemeMode.LIGHT -> R.id.themeLightRadio
                    ThemeMode.DARK -> R.id.themeDarkRadio
                },
            )
            binding.reducedTransparencySwitch.isChecked = settings.reducedTransparency
            binding.reduceMotionSwitch.isChecked = settings.reduceMotion
            val hasCustomBackground = !settings.backgroundUri.isNullOrBlank()
            binding.backgroundStatus.setText(
                if (hasCustomBackground) R.string.custom_background else R.string.default_background,
            )
            binding.clearBackgroundButton.isEnabled = hasCustomBackground

            // Local addresses are part of the browser's user-directed compatibility contract;
            // keep the legacy preference visible but do not present it as a restriction switch.
            binding.allowLocalNetworkSwitch.isChecked = true
            binding.allowLocalNetworkSwitch.isEnabled = false
            binding.privateModeSwitch.isChecked = settings.privateMode
            binding.clearPrivateOnExitSwitch.isChecked = settings.clearPrivateOnExit
            binding.clearPrivateOnExitSwitch.isEnabled = settings.privateMode
        } finally {
            rendering = false
        }
    }

    /** Re-reads app/WebView versions, useful after the WebView provider changes. */
    fun renderVersionInformation() {
        val webViewVersion = runCatching {
            WebView.getCurrentWebViewPackage()?.versionName
        }.getOrNull().orEmpty().ifBlank {
            context.getString(R.string.version_unavailable)
        }
        binding.versionInfo.text = context.getString(
            R.string.version_info,
            BuildConfig.VERSION_NAME,
            webViewVersion,
        )
    }

    /**
     * Validates and commits the homepage field. Empty means the app's built-in Home scene.
     * Invalid input remains editable and is never sent to persistence.
     */
    fun commitHomeUrl(): Boolean {
        val rawValue = binding.settingsHomeUrl.text?.toString().orEmpty().trim()
        val normalized = if (rawValue.isEmpty()) "" else UrlPolicy.normalizeForNavigation(rawValue)
        if (normalized == null) {
            binding.homeUrlLayout.error = context.getString(R.string.invalid_url)
            return false
        }

        binding.homeUrlLayout.error = null
        if (binding.settingsHomeUrl.text?.toString() != normalized) {
            rendering = true
            try {
                binding.settingsHomeUrl.setText(normalized)
                binding.settingsHomeUrl.setSelection(normalized.length)
            } finally {
                rendering = false
            }
        }
        emitSettings(settings.copy(homeUrl = normalized))
        return true
    }

    private fun configureSearchEngineDropdown() {
        binding.searchEngineInput.setAdapter(
            ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                searchEngineChoices.map(SearchEngineChoice::label),
            ),
        )
        binding.searchEngineInput.setOnItemClickListener { _, _, position, _ ->
            if (!rendering) {
                val engine = searchEngineChoices.getOrNull(position)?.engine ?: return@setOnItemClickListener
                emitSettings(settings.copy(searchEngineId = engine.id))
            }
        }
    }

    private fun bindSettingsControls() {
        binding.settingsHomeUrl.doAfterTextChanged {
            if (!rendering) binding.homeUrlLayout.error = null
        }
        binding.settingsHomeUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !rendering) commitHomeUrl()
        }
        binding.settingsHomeUrl.setOnEditorActionListener { _, actionId, event ->
            val shouldCommit = actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            shouldCommit && commitHomeUrl()
        }

        binding.startBehaviorGroup.setOnCheckedChangeListener { _, checkedId ->
            if (rendering) return@setOnCheckedChangeListener
            val behavior = when (checkedId) {
                R.id.startHomeRadio -> StartBehavior.HOME
                R.id.startLastPageRadio -> StartBehavior.LAST_PAGE
                R.id.startBlankRadio -> StartBehavior.BLANK
                else -> return@setOnCheckedChangeListener
            }
            emitSettings(settings.copy(startBehavior = behavior))
        }
        binding.fullscreenSwitch.setOnCheckedChangeListener { _, checked ->
            emitIfUserChanged { copy(fullscreenEnabled = checked) }
        }
        binding.toolbarAutoHideSwitch.setOnCheckedChangeListener { _, checked ->
            emitIfUserChanged { copy(toolbarAutoHide = checked) }
        }
        binding.toolbarPositionGroup.setOnCheckedChangeListener { _, checkedId ->
            if (rendering) return@setOnCheckedChangeListener
            val position = when (checkedId) {
                R.id.toolbarBottomRadio -> ToolbarPosition.BOTTOM
                R.id.toolbarTopRadio -> ToolbarPosition.TOP
                else -> return@setOnCheckedChangeListener
            }
            emitSettings(settings.copy(toolbarPosition = position))
        }
        binding.desktopModeSwitch.setOnCheckedChangeListener { _, checked ->
            emitIfUserChanged { copy(desktopModeDefault = checked) }
        }
        binding.textZoomSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val zoom = (BrowserSettings.MIN_TEXT_ZOOM + progress)
                    .coerceIn(BrowserSettings.MIN_TEXT_ZOOM, BrowserSettings.MAX_TEXT_ZOOM)
                renderTextZoom(zoom)
                if (fromUser && !rendering) emitSettings(settings.copy(textZoom = zoom))
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        binding.darkWebModeSwitch.setOnCheckedChangeListener { _, checked ->
            emitIfUserChanged { copy(darkWebMode = checked) }
        }

        binding.themeModeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (rendering) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.themeSystemRadio -> ThemeMode.SYSTEM
                R.id.themeLightRadio -> ThemeMode.LIGHT
                R.id.themeDarkRadio -> ThemeMode.DARK
                else -> return@setOnCheckedChangeListener
            }
            emitSettings(
                settings.copy(
                    themeMode = mode,
                    // Kept in sync for old callers that still inspect the compatibility field.
                    darkThemeEnabled = when (mode) {
                        ThemeMode.SYSTEM -> settings.darkThemeEnabled
                        ThemeMode.LIGHT -> false
                        ThemeMode.DARK -> true
                    },
                ),
            )
        }
        binding.reducedTransparencySwitch.setOnCheckedChangeListener { _, checked ->
            emitIfUserChanged { copy(reducedTransparency = checked) }
        }
        binding.reduceMotionSwitch.setOnCheckedChangeListener { _, checked ->
            emitIfUserChanged { copy(reduceMotion = checked) }
        }

        binding.privateModeSwitch.setOnCheckedChangeListener { _, checked ->
            if (rendering) return@setOnCheckedChangeListener
            binding.clearPrivateOnExitSwitch.isEnabled = checked
            emitSettings(settings.copy(privateMode = checked))
        }
        binding.clearPrivateOnExitSwitch.setOnCheckedChangeListener { _, checked ->
            emitIfUserChanged { copy(clearPrivateOnExit = checked) }
        }
    }

    private fun bindActions() {
        binding.settingsBackButton.setOnClickListener { callback.onBack() }
        binding.chooseBackgroundButton.setOnClickListener { callback.onChooseBackground() }
        binding.clearBackgroundButton.setOnClickListener { callback.onClearBackground() }

        binding.clearCacheButton.setOnClickListener {
            callback.onClearBrowsingData(BrowsingDataTarget.CACHE)
        }
        binding.clearCookiesButton.setOnClickListener {
            callback.onClearBrowsingData(BrowsingDataTarget.COOKIES)
        }
        binding.clearWebStorageButton.setOnClickListener {
            callback.onClearBrowsingData(BrowsingDataTarget.WEB_STORAGE)
        }
        binding.clearHistoryButton.setOnClickListener {
            callback.onClearBrowsingData(BrowsingDataTarget.HISTORY)
        }
        binding.clearFormDataButton.setOnClickListener {
            callback.onClearBrowsingData(BrowsingDataTarget.FORM_DATA)
        }
        binding.clearAllDataButton.setOnClickListener {
            callback.onClearBrowsingData(BrowsingDataTarget.ALL)
        }

        binding.openHistoryButton.setOnClickListener {
            callback.onOpenLibrary(LibraryTarget.HISTORY)
        }
        binding.openBookmarksButton.setOnClickListener {
            callback.onOpenLibrary(LibraryTarget.BOOKMARKS)
        }
        binding.openDownloadsButton.setOnClickListener {
            callback.onOpenLibrary(LibraryTarget.DOWNLOADS)
        }

        binding.privacyStatementButton.setOnClickListener {
            callback.onOpenAbout(AboutTarget.PRIVACY_AND_TRUST)
        }
        binding.licensesButton.setOnClickListener {
            callback.onOpenAbout(AboutTarget.OPEN_SOURCE_LICENSES)
        }
        binding.exportDiagnosticsButton.setOnClickListener {
            callback.onOpenAbout(AboutTarget.EXPORT_DIAGNOSTICS)
        }
        binding.manualTestButton.setOnClickListener {
            callback.onOpenAbout(AboutTarget.MANUAL_TEST_TOOLS)
        }
    }

    private fun emitIfUserChanged(transform: BrowserSettings.() -> BrowserSettings) {
        if (!rendering) emitSettings(settings.transform())
    }

    private fun emitSettings(updated: BrowserSettings) {
        if (rendering || updated == settings) return
        settings = updated
        callback.onSettingsChanged(updated)
    }

    private fun renderTextZoom(zoom: Int) {
        binding.textZoomLabel.text = context.getString(
            R.string.text_zoom_value,
            zoom.coerceIn(BrowserSettings.MIN_TEXT_ZOOM, BrowserSettings.MAX_TEXT_ZOOM),
        )
    }

    @get:StringRes
    private val SearchEngine.labelResource: Int
        get() = when (this) {
            SearchEngine.BAIDU -> R.string.search_engine_baidu
            SearchEngine.BING -> R.string.search_engine_bing
            SearchEngine.DUCKDUCKGO -> R.string.search_engine_duckduckgo
        }

    private companion object {
        const val TEXT_ZOOM_RANGE = BrowserSettings.MAX_TEXT_ZOOM - BrowserSettings.MIN_TEXT_ZOOM
    }
}
