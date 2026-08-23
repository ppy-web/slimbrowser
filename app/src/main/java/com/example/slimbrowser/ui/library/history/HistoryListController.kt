package com.example.slimbrowser.ui.library.history

import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slimbrowser.R
import com.example.slimbrowser.data.history.HistoryEntity
import com.example.slimbrowser.databinding.DialogHistoryListBinding
import java.time.ZoneId

/**
 * Owns only list presentation. The host observes [HistoryRepository][com.example.slimbrowser.data.history.HistoryRepository]
 * and handles all navigation and database mutations exposed by [Callbacks].
 */
class HistoryListController(
    private val binding: DialogHistoryListBinding,
    private val callbacks: Callbacks,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    interface Callbacks {
        fun onOpenRequested(entry: HistoryEntity)

        fun onDeleteRequested(entry: HistoryEntity)

        fun onClearAllRequested()

        fun onDismissRequested()

        /** Optional hook for hosts that prefer switching between observeAll() and repository.search(). */
        fun onSearchQueryChanged(query: String) = Unit
    }

    private var entries: List<HistoryEntity> = emptyList()
    private var query: String = ""

    private val adapter = HistoryListAdapter(
        object : HistoryListAdapter.Callbacks {
            override fun onOpenRequested(entry: HistoryEntity) = callbacks.onOpenRequested(entry)

            override fun onDeleteRequested(entry: HistoryEntity) = callbacks.onDeleteRequested(entry)
        },
    )

    init {
        binding.historyList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.historyList.adapter = adapter
        binding.historyList.setHasFixedSize(false)
        binding.historySearchInput.doAfterTextChanged {
            query = it?.toString().orEmpty()
            callbacks.onSearchQueryChanged(query.trim())
            render()
        }
        binding.clearHistoryButton.setOnClickListener { callbacks.onClearAllRequested() }
        binding.closeHistoryButton.setOnClickListener { callbacks.onDismissRequested() }
        render()
    }

    fun submitEntries(entries: List<HistoryEntity>) {
        this.entries = entries
        render()
    }

    fun setSearchQuery(query: String) {
        if (binding.historySearchInput.text?.toString() == query) return
        binding.historySearchInput.setText(query)
        binding.historySearchInput.setSelection(query.length)
    }

    private fun render() {
        val filteredCount = entries.count { entry ->
            query.isBlank() ||
                entry.title.contains(query.trim(), ignoreCase = true) ||
                entry.url.contains(query.trim(), ignoreCase = true) ||
                entry.host.contains(query.trim(), ignoreCase = true)
        }
        adapter.submitEntries(entries, query, clock(), zoneId) {
            binding.historyList.isVisible = filteredCount > 0
            binding.historyEmptyState.isVisible = filteredCount == 0
            binding.historyEmptyState.setText(
                if (entries.isEmpty()) R.string.history_empty else R.string.history_search_empty,
            )
        }
        binding.clearHistoryButton.isEnabled = entries.isNotEmpty()
    }
}
