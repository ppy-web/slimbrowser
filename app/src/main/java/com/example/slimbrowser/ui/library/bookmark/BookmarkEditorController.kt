package com.example.slimbrowser.ui.library.bookmark

import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import com.example.slimbrowser.R
import com.example.slimbrowser.data.bookmark.BookmarkEntity
import com.example.slimbrowser.databinding.DialogEditBookmarkBinding

data class BookmarkEditDraft(
    val id: Long?,
    val title: String,
    val url: String,
    val pinnedToHome: Boolean,
    val sortOrder: Int?,
)

/** Validates input and returns a draft; the host decides whether to call addOrUpdate or update. */
class BookmarkEditorController(
    private val binding: DialogEditBookmarkBinding,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onSaveRequested(draft: BookmarkEditDraft)

        fun onCancelRequested()
    }

    private var original: BookmarkEntity? = null

    init {
        ViewCompat.setAccessibilityHeading(binding.bookmarkEditorTitle, true)
        binding.bookmarkTitleInput.doAfterTextChanged { binding.bookmarkTitleLayout.error = null }
        binding.bookmarkUrlInput.doAfterTextChanged { binding.bookmarkUrlLayout.error = null }
        binding.cancelBookmarkEditButton.setOnClickListener { callbacks.onCancelRequested() }
        binding.saveBookmarkEditButton.setOnClickListener { submitIfValid() }
    }

    fun bind(bookmark: BookmarkEntity?) {
        original = bookmark
        binding.bookmarkEditorTitle.setText(
            if (bookmark == null) R.string.add_bookmark else R.string.edit_bookmark,
        )
        binding.bookmarkTitleInput.setText(bookmark?.title.orEmpty())
        binding.bookmarkUrlInput.setText(bookmark?.url.orEmpty())
        binding.pinBookmarkSwitch.isChecked = bookmark?.pinnedToHome ?: false
        binding.bookmarkTitleLayout.error = null
        binding.bookmarkUrlLayout.error = null
    }

    private fun submitIfValid() {
        val title = binding.bookmarkTitleInput.text?.toString()?.trim().orEmpty()
        val url = binding.bookmarkUrlInput.text?.toString()?.trim().orEmpty()
        var valid = true
        if (title.isEmpty()) {
            binding.bookmarkTitleLayout.error = binding.root.context.getString(R.string.bookmark_title_required)
            valid = false
        }
        if (url.isEmpty()) {
            binding.bookmarkUrlLayout.error = binding.root.context.getString(R.string.bookmark_url_required)
            valid = false
        }
        if (!valid) return

        callbacks.onSaveRequested(
            BookmarkEditDraft(
                id = original?.id,
                title = title,
                url = url,
                pinnedToHome = binding.pinBookmarkSwitch.isChecked,
                sortOrder = original?.sortOrder,
            ),
        )
    }
}
