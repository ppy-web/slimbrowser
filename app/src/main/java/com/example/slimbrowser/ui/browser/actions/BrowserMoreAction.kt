package com.example.slimbrowser.ui.browser.actions

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.slimbrowser.R

enum class BrowserMoreAction {
    SHARE,
    COPY_URL,
    OPEN_EXTERNAL,
    FIND_IN_PAGE,
    TOGGLE_DESKTOP_MODE,
    TOGGLE_BOOKMARK,
    OPEN_SETTINGS,
    GO_HOME,
}

data class BrowserMoreActionsState(
    val hasCurrentPage: Boolean,
    val isDesktopMode: Boolean,
    val isBookmarked: Boolean,
)

data class BrowserMoreActionItem(
    val action: BrowserMoreAction,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val isEnabled: Boolean,
    val isSelected: Boolean = false,
)

object BrowserMoreActionItems {
    fun build(state: BrowserMoreActionsState): List<BrowserMoreActionItem> = listOf(
        BrowserMoreActionItem(
            BrowserMoreAction.SHARE,
            R.string.share_page,
            R.drawable.ic_share,
            state.hasCurrentPage,
        ),
        BrowserMoreActionItem(
            BrowserMoreAction.COPY_URL,
            R.string.copy_url,
            R.drawable.ic_copy,
            state.hasCurrentPage,
        ),
        BrowserMoreActionItem(
            BrowserMoreAction.OPEN_EXTERNAL,
            R.string.open_in_external_browser,
            R.drawable.ic_open_external,
            state.hasCurrentPage,
        ),
        BrowserMoreActionItem(
            BrowserMoreAction.FIND_IN_PAGE,
            R.string.find_in_page,
            R.drawable.ic_find_in_page,
            state.hasCurrentPage,
        ),
        BrowserMoreActionItem(
            BrowserMoreAction.TOGGLE_DESKTOP_MODE,
            if (state.isDesktopMode) R.string.disable_desktop_mode else R.string.enable_desktop_mode,
            R.drawable.ic_desktop,
            state.hasCurrentPage,
            state.isDesktopMode,
        ),
        BrowserMoreActionItem(
            BrowserMoreAction.TOGGLE_BOOKMARK,
            if (state.isBookmarked) R.string.remove_current_bookmark else R.string.favorite_current_page,
            if (state.isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
            state.hasCurrentPage,
            state.isBookmarked,
        ),
        BrowserMoreActionItem(
            BrowserMoreAction.OPEN_SETTINGS,
            R.string.settings,
            R.drawable.ic_settings,
            true,
        ),
        BrowserMoreActionItem(
            BrowserMoreAction.GO_HOME,
            R.string.go_to_home,
            R.drawable.ic_home,
            true,
        ),
    )
}
