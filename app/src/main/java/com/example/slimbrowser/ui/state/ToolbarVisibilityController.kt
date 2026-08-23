package com.example.slimbrowser.ui.state

enum class ToolbarEvent {
    PAGE_STARTED,
    PAGE_FINISHED,
    SCROLL_UP,
    SCROLL_DOWN,
    EDGE_REVEAL,
    PAGE_TAP,
    EDIT_STARTED,
    EDIT_FINISHED,
    IDLE_TIMEOUT,
    ERROR_SHOWN,
}

/**
 * Pure toolbar visibility reducer. Pixel thresholds and event throttling stay
 * in the Android adapter; this class defines the observable state transitions.
 */
class ToolbarVisibilityController(
    initialVisibility: ToolbarVisibility = ToolbarVisibility.VISIBLE,
) {
    var visibility: ToolbarVisibility = initialVisibility
        private set

    private var editing = false

    fun onEvent(
        event: ToolbarEvent,
        autoHideEnabled: Boolean,
    ): ToolbarVisibility {
        visibility = when (event) {
            ToolbarEvent.EDIT_STARTED -> {
                editing = true
                ToolbarVisibility.VISIBLE
            }
            ToolbarEvent.EDIT_FINISHED -> {
                editing = false
                ToolbarVisibility.VISIBLE
            }
            ToolbarEvent.PAGE_STARTED,
            ToolbarEvent.SCROLL_UP,
            ToolbarEvent.EDGE_REVEAL,
            ToolbarEvent.PAGE_TAP,
            ToolbarEvent.ERROR_SHOWN,
            -> ToolbarVisibility.VISIBLE
            ToolbarEvent.PAGE_FINISHED -> if (autoHideEnabled && !editing) {
                ToolbarVisibility.COLLAPSED
            } else {
                ToolbarVisibility.VISIBLE
            }
            ToolbarEvent.SCROLL_DOWN -> if (autoHideEnabled && !editing) {
                ToolbarVisibility.HIDDEN
            } else {
                ToolbarVisibility.VISIBLE
            }
            ToolbarEvent.IDLE_TIMEOUT -> when {
                !autoHideEnabled || editing -> ToolbarVisibility.VISIBLE
                visibility == ToolbarVisibility.VISIBLE -> ToolbarVisibility.COLLAPSED
                else -> ToolbarVisibility.HIDDEN
            }
        }
        return visibility
    }
}
