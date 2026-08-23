package com.example.slimbrowser.ui.library.history

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slimbrowser.R
import com.example.slimbrowser.data.history.HistoryEntity
import com.example.slimbrowser.databinding.ItemHistoryEntryBinding
import com.example.slimbrowser.databinding.ItemHistorySectionBinding
import java.text.DateFormat
import java.time.ZoneId

class HistoryListAdapter(
    private val callbacks: Callbacks,
) : ListAdapter<HistoryListItem, RecyclerView.ViewHolder>(DiffCallback) {
    interface Callbacks {
        fun onOpenRequested(entry: HistoryEntity)

        fun onDeleteRequested(entry: HistoryEntity)
    }

    init {
        setHasStableIds(true)
    }

    fun submitEntries(
        entries: List<HistoryEntity>,
        query: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        commitCallback: (() -> Unit)? = null,
    ) {
        val items = HistoryListItems.build(entries, query, nowMillis, zoneId)
        if (commitCallback == null) {
            submitList(items)
        } else {
            submitList(items, Runnable { commitCallback() })
        }
    }

    override fun getItemId(position: Int): Long = getItem(position).stableId

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is HistoryListItem.Header -> VIEW_TYPE_HEADER
        is HistoryListItem.Entry -> VIEW_TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> HeaderViewHolder(
                ItemHistorySectionBinding.inflate(inflater, parent, false),
            )

            else -> EntryViewHolder(
                ItemHistoryEntryBinding.inflate(inflater, parent, false),
                callbacks,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is HistoryListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is HistoryListItem.Entry -> (holder as EntryViewHolder).bind(item)
        }
    }

    private class HeaderViewHolder(
        private val binding: ItemHistorySectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryListItem.Header) {
            binding.root.setText(
                when (item.period) {
                    HistoryPeriod.TODAY -> R.string.history_today
                    HistoryPeriod.YESTERDAY -> R.string.history_yesterday
                    HistoryPeriod.EARLIER -> R.string.history_earlier
                },
            )
        }
    }

    private class EntryViewHolder(
        private val binding: ItemHistoryEntryBinding,
        private val callbacks: Callbacks,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryListItem.Entry) {
            val entry = item.history
            val context = binding.root.context
            binding.historyTitle.text = entry.title
            binding.historyUrl.text = entry.url
            binding.historyInitial.text = entry.title.firstOrNull()?.uppercaseChar()?.toString()
                ?: context.getString(R.string.history_initial_fallback)
            val favicon = entry.faviconUri?.takeIf(String::isNotBlank)
            binding.historyFavicon.isVisible = favicon != null
            binding.historyInitial.isVisible = favicon == null
            if (favicon != null) {
                binding.historyFavicon.setImageURI(Uri.parse(favicon))
            } else {
                binding.historyFavicon.setImageDrawable(null)
            }

            val formattedTime = when (item.period) {
                HistoryPeriod.TODAY,
                HistoryPeriod.YESTERDAY,
                -> DateFormat.getTimeInstance(DateFormat.SHORT).format(entry.visitedAt)

                HistoryPeriod.EARLIER ->
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(entry.visitedAt)
            }
            binding.historyMetadata.text = context.getString(
                R.string.history_metadata,
                entry.host.ifBlank { context.getString(R.string.unknown_site) },
                formattedTime,
                entry.visitCount,
            )
            binding.deleteHistoryButton.contentDescription = context.getString(
                R.string.delete_history_item_named,
                entry.title,
            )
            binding.root.setOnClickListener { callbacks.onOpenRequested(entry) }
            binding.deleteHistoryButton.setOnClickListener { callbacks.onDeleteRequested(entry) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<HistoryListItem>() {
        override fun areItemsTheSame(
            oldItem: HistoryListItem,
            newItem: HistoryListItem,
        ): Boolean = oldItem::class == newItem::class && oldItem.stableId == newItem.stableId

        override fun areContentsTheSame(
            oldItem: HistoryListItem,
            newItem: HistoryListItem,
        ): Boolean = oldItem == newItem
    }

    private companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_ENTRY = 1
    }
}
