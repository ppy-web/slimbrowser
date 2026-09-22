package com.example.slimbrowser.ui.library.download

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.slimbrowser.R
import com.example.slimbrowser.data.download.DownloadEntity
import com.example.slimbrowser.data.download.DownloadStatus
import com.example.slimbrowser.databinding.ItemDownloadBinding
import java.text.DateFormat

private val DownloadStatus.labelRes: Int
    get() = when (this) {
        DownloadStatus.QUEUED -> R.string.download_status_queued
        DownloadStatus.RUNNING -> R.string.download_status_running
        DownloadStatus.PAUSED -> R.string.download_status_paused
        DownloadStatus.SUCCESSFUL -> R.string.download_status_successful
        DownloadStatus.FAILED -> R.string.download_status_failed
        DownloadStatus.CANCELLED -> R.string.download_status_cancelled
    }

class DownloadListAdapter(
    private val callbacks: Callbacks,
) : ListAdapter<DownloadEntity, DownloadListAdapter.DownloadViewHolder>(DiffCallback) {
    interface Callbacks {
        /** The host resolves the local/destination URI and starts the appropriate viewer. */
        fun onOpenRequested(download: DownloadEntity)

        /** The host re-enqueues the original request, then calls DownloadRepository.markRetried. */
        fun onRetryRequested(download: DownloadEntity)

        fun onDeleteRequested(download: DownloadEntity)
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder =
        DownloadViewHolder(
            ItemDownloadBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
            callbacks,
        )

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DownloadViewHolder(
        private val binding: ItemDownloadBinding,
        private val callbacks: Callbacks,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(download: DownloadEntity) {
            val context = binding.root.context
            binding.downloadFileName.text = download.fileName
            binding.downloadSource.text = download.sourceUrl
            binding.downloadStatus.text = context.getString(download.status.labelRes)
            binding.downloadUpdatedAt.text = context.getString(
                R.string.download_updated_at,
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(download.updatedAt),
            )

            val showsProgress = download.status in setOf(
                DownloadStatus.QUEUED,
                DownloadStatus.RUNNING,
                DownloadStatus.PAUSED,
            )
            val hasKnownTotal = download.totalBytes > 0
            binding.downloadProgress.isVisible = false
            binding.downloadProgress.isIndeterminate = showsProgress && !hasKnownTotal
            if (hasKnownTotal) {
                val percentage = ((download.bytesDownloaded.toDouble() / download.totalBytes) * 100)
                    .toInt()
                    .coerceIn(0, 100)
                binding.downloadProgress.progress = percentage
                binding.downloadProgress.contentDescription = context.getString(
                    R.string.download_progress_percent,
                    percentage,
                )
                binding.downloadSize.text = context.getString(
                    R.string.download_size_progress,
                    Formatter.formatShortFileSize(context, download.bytesDownloaded.coerceAtLeast(0)),
                    Formatter.formatShortFileSize(context, download.totalBytes),
                )
            } else {
                binding.downloadProgress.contentDescription = context.getString(
                    R.string.download_progress_description,
                )
                binding.downloadSize.text = context.getString(
                    R.string.download_size_unknown_total,
                    Formatter.formatShortFileSize(context, download.bytesDownloaded.coerceAtLeast(0)),
                )
            }
            binding.downloadProgress.isVisible = showsProgress

            val canRetry = download.status == DownloadStatus.FAILED ||
                download.status == DownloadStatus.CANCELLED
            val canOpen = download.status == DownloadStatus.SUCCESSFUL &&
                (!download.localUri.isNullOrBlank() || !download.destinationUri.isNullOrBlank())
            binding.retryDownloadButton.isVisible = canRetry
            binding.openDownloadButton.isVisible = canOpen
            binding.retryDownloadButton.contentDescription = context.getString(
                R.string.retry_download_named,
                download.fileName,
            )
            binding.openDownloadButton.contentDescription = context.getString(
                R.string.open_download_named,
                download.fileName,
            )
            binding.deleteDownloadButton.contentDescription = context.getString(
                R.string.delete_download_named,
                download.fileName,
            )
            binding.retryDownloadButton.setOnClickListener { callbacks.onRetryRequested(download) }
            binding.openDownloadButton.setOnClickListener { callbacks.onOpenRequested(download) }
            binding.deleteDownloadButton.setOnClickListener { callbacks.onDeleteRequested(download) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<DownloadEntity>() {
        override fun areItemsTheSame(
            oldItem: DownloadEntity,
            newItem: DownloadEntity,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: DownloadEntity,
            newItem: DownloadEntity,
        ): Boolean = oldItem == newItem
    }
}
