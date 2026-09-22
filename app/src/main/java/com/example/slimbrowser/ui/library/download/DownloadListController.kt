package com.example.slimbrowser.ui.library.download

import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.slimbrowser.data.download.DownloadEntity
import com.example.slimbrowser.databinding.DialogDownloadListBinding

/** UI-only bridge for DownloadRepository.observeAll(). */
class DownloadListController(
    private val binding: DialogDownloadListBinding,
    callbacks: Callbacks,
) {
    interface Callbacks : DownloadListAdapter.Callbacks {
        fun onClearAllRequested()

        fun onDismissRequested()
    }

    private val adapter = DownloadListAdapter(callbacks)

    init {
        binding.downloadList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.downloadList.adapter = adapter
        binding.clearDownloadsButton.setOnClickListener { callbacks.onClearAllRequested() }
        binding.closeDownloadListButton.setOnClickListener { callbacks.onDismissRequested() }
        submitDownloads(emptyList())
    }

    fun submitDownloads(downloads: List<DownloadEntity>) {
        adapter.submitList(downloads) {
            val isEmpty = downloads.isEmpty()
            binding.downloadList.isVisible = !isEmpty
            binding.downloadEmptyState.isVisible = isEmpty
        }
        binding.clearDownloadsButton.isEnabled = downloads.isNotEmpty()
    }
}
