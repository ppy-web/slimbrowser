package com.example.slimbrowser.ui.state

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolbarVisibilityControllerTest {
    @Test
    fun autoHideFollowsVisibleCollapsedHiddenSequence() {
        val controller = ToolbarVisibilityController()
        assertEquals(
            ToolbarVisibility.COLLAPSED,
            controller.onEvent(ToolbarEvent.IDLE_TIMEOUT, autoHideEnabled = true),
        )
        assertEquals(
            ToolbarVisibility.HIDDEN,
            controller.onEvent(ToolbarEvent.IDLE_TIMEOUT, autoHideEnabled = true),
        )
        assertEquals(
            ToolbarVisibility.VISIBLE,
            controller.onEvent(ToolbarEvent.EDGE_REVEAL, autoHideEnabled = true),
        )
    }

    @Test
    fun editingAndDisabledAutoHideKeepToolbarVisible() {
        val controller = ToolbarVisibilityController()
        controller.onEvent(ToolbarEvent.EDIT_STARTED, autoHideEnabled = true)
        assertEquals(
            ToolbarVisibility.VISIBLE,
            controller.onEvent(ToolbarEvent.SCROLL_DOWN, autoHideEnabled = true),
        )
        controller.onEvent(ToolbarEvent.EDIT_FINISHED, autoHideEnabled = true)
        assertEquals(
            ToolbarVisibility.VISIBLE,
            controller.onEvent(ToolbarEvent.SCROLL_DOWN, autoHideEnabled = false),
        )
    }
}
