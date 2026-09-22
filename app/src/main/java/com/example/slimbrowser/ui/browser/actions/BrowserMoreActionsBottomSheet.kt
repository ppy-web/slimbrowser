package com.example.slimbrowser.ui.browser.actions

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.slimbrowser.databinding.BottomSheetBrowserActionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Displays browser actions but deliberately does not start intents or mutate browser/Room state.
 */
class BrowserMoreActionsBottomSheet(
    context: Context,
    private val callbacks: Callbacks,
) {
    fun interface Callbacks {
        fun onActionSelected(action: BrowserMoreAction)
    }

    private val binding = BottomSheetBrowserActionsBinding.inflate(LayoutInflater.from(context))
    private val dialog = BottomSheetDialog(context)
    private val adapter = BrowserMoreActionsAdapter { action ->
        dialog.dismiss()
        callbacks.onActionSelected(action)
    }

    init {
        ViewCompat.setAccessibilityHeading(binding.moreActionsTitle, true)
        binding.moreActionsList.layoutManager = GridLayoutManager(context, COLUMN_COUNT)
        binding.moreActionsList.adapter = adapter
        dialog.setContentView(binding.root)
    }

    fun show(state: BrowserMoreActionsState) {
        adapter.submitList(BrowserMoreActionItems.build(state))
        dialog.show()
    }

    fun dismiss() = dialog.dismiss()

    val isShowing: Boolean
        get() = dialog.isShowing

    private companion object {
        const val COLUMN_COUNT = 2
    }
}
