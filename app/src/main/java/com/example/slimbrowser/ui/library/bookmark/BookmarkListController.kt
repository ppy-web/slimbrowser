package com.example.slimbrowser.ui.library.bookmark

import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slimbrowser.data.bookmark.BookmarkEntity
import com.example.slimbrowser.databinding.DialogBookmarkListBinding

/** UI-only bridge for BookmarkRepository.observeAll(). */
class BookmarkListController(
    private val binding: DialogBookmarkListBinding,
    callbacks: Callbacks,
) {
    interface Callbacks : BookmarkListAdapter.Callbacks {
        fun onDismissRequested()
    }

    private val adapter = BookmarkListAdapter(callbacks)

    init {
        binding.bookmarkList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.bookmarkList.adapter = adapter
        binding.closeBookmarkListButton.setOnClickListener { callbacks.onDismissRequested() }
        submitBookmarks(emptyList())
    }

    fun submitBookmarks(bookmarks: List<BookmarkEntity>) {
        adapter.submitList(bookmarks) {
            val isEmpty = bookmarks.isEmpty()
            binding.bookmarkList.isVisible = !isEmpty
            binding.bookmarkEmptyState.isVisible = isEmpty
        }
    }
}
