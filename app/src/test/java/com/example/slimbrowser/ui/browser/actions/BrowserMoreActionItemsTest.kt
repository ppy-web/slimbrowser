package com.example.slimbrowser.ui.browser.actions

import com.example.slimbrowser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserMoreActionItemsTest {
    @Test
    fun pageActionsAreDisabledWithoutCurrentPage() {
        val items = BrowserMoreActionItems.build(
            BrowserMoreActionsState(
                hasCurrentPage = false,
                isDesktopMode = false,
                isBookmarked = false,
            ),
        )

        items.take(6).forEach { assertFalse(it.isEnabled) }
        items.takeLast(2).forEach { assertTrue(it.isEnabled) }
        assertEquals(BrowserMoreAction.entries.toList(), items.map { it.action })
    }

    @Test
    fun togglesExposeTheirCurrentStateAndInverseActionLabel() {
        val items = BrowserMoreActionItems.build(
            BrowserMoreActionsState(
                hasCurrentPage = true,
                isDesktopMode = true,
                isBookmarked = true,
            ),
        ).associateBy(BrowserMoreActionItem::action)

        val desktop = checkNotNull(items[BrowserMoreAction.TOGGLE_DESKTOP_MODE])
        val bookmark = checkNotNull(items[BrowserMoreAction.TOGGLE_BOOKMARK])
        assertTrue(desktop.isSelected)
        assertEquals(R.string.disable_desktop_mode, desktop.labelRes)
        assertTrue(bookmark.isSelected)
        assertEquals(R.string.remove_current_bookmark, bookmark.labelRes)
    }
}
