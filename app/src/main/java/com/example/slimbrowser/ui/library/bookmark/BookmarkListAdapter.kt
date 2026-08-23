package com.example.slimbrowser.ui.library.bookmark

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slimbrowser.R
import com.example.slimbrowser.data.bookmark.BookmarkEntity
import com.example.slimbrowser.databinding.ItemBookmarkBinding

class BookmarkListAdapter(
    private val callbacks: Callbacks,
) : ListAdapter<BookmarkEntity, BookmarkListAdapter.BookmarkViewHolder>(DiffCallback) {
    interface Callbacks {
        fun onOpenRequested(bookmark: BookmarkEntity)

        fun onEditRequested(bookmark: BookmarkEntity)

        fun onDeleteRequested(bookmark: BookmarkEntity)

        fun onPinnedChangeRequested(bookmark: BookmarkEntity, pinned: Boolean)

        /** The host can pass this list directly to BookmarkRepository.reorder. */
        fun onReorderRequested(idsInOrder: List<Long>)
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder =
        BookmarkViewHolder(
            binding = ItemBookmarkBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            callbacks = callbacks,
            onMoveRequested = ::requestMove,
        )

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount)
    }

    private fun requestMove(bookmark: BookmarkEntity, offset: Int) {
        val fromIndex = currentList.indexOfFirst { it.id == bookmark.id }
        val toIndex = fromIndex + offset
        if (fromIndex < 0 || toIndex !in currentList.indices) return

        val reordered = currentList.toMutableList()
        val moved = reordered.removeAt(fromIndex)
        reordered.add(toIndex, moved)
        callbacks.onReorderRequested(reordered.map(BookmarkEntity::id))
    }

    class BookmarkViewHolder(
        private val binding: ItemBookmarkBinding,
        private val callbacks: Callbacks,
        private val onMoveRequested: (BookmarkEntity, Int) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(bookmark: BookmarkEntity, position: Int, itemCount: Int) {
            val context = binding.root.context
            binding.bookmarkTitle.text = bookmark.title
            binding.bookmarkUrl.text = bookmark.url
            binding.pinnedBadge.isVisible = bookmark.pinnedToHome

            binding.editBookmarkButton.contentDescription = context.getString(
                R.string.edit_bookmark_named,
                bookmark.title,
            )
            binding.deleteBookmarkButton.contentDescription = context.getString(
                R.string.delete_bookmark_named,
                bookmark.title,
            )
            binding.pinBookmarkButton.contentDescription = context.getString(
                if (bookmark.pinnedToHome) {
                    R.string.unpin_bookmark_named
                } else {
                    R.string.pin_bookmark_named
                },
                bookmark.title,
            )
            binding.pinBookmarkButton.setImageResource(
                if (bookmark.pinnedToHome) R.drawable.ic_pin_filled else R.drawable.ic_pin,
            )
            binding.moveBookmarkUpButton.isEnabled = position > 0
            binding.moveBookmarkDownButton.isEnabled = position < itemCount - 1
            binding.moveBookmarkUpButton.contentDescription = context.getString(
                R.string.move_bookmark_up_named,
                bookmark.title,
            )
            binding.moveBookmarkDownButton.contentDescription = context.getString(
                R.string.move_bookmark_down_named,
                bookmark.title,
            )

            binding.root.setOnClickListener { callbacks.onOpenRequested(bookmark) }
            binding.editBookmarkButton.setOnClickListener { callbacks.onEditRequested(bookmark) }
            binding.deleteBookmarkButton.setOnClickListener { callbacks.onDeleteRequested(bookmark) }
            binding.pinBookmarkButton.setOnClickListener {
                callbacks.onPinnedChangeRequested(bookmark, !bookmark.pinnedToHome)
            }
            binding.moveBookmarkUpButton.setOnClickListener { onMoveRequested(bookmark, -1) }
            binding.moveBookmarkDownButton.setOnClickListener { onMoveRequested(bookmark, 1) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<BookmarkEntity>() {
        override fun areItemsTheSame(
            oldItem: BookmarkEntity,
            newItem: BookmarkEntity,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: BookmarkEntity,
            newItem: BookmarkEntity,
        ): Boolean = oldItem == newItem
    }
}
