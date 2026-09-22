package com.example.slimbrowser.ui.browser

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.slimbrowser.R
import com.example.slimbrowser.data.download.DownloadEntity
import com.example.slimbrowser.data.download.DownloadRepository
import com.example.slimbrowser.data.download.DownloadStatus
import com.example.slimbrowser.domain.NavigationDecision
import com.example.slimbrowser.domain.NavigationPolicy
import com.example.slimbrowser.domain.NavigationSource
import com.example.slimbrowser.domain.UrlPolicy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware bridge between WebView downloads, [DownloadManager] and Room metadata.
 *
 * The coordinator deliberately uses app-specific external storage on Android 8/9 and public
 * Downloads through DownloadManager on Android 10+, so no broad storage permission is needed.
 * Call [close] from a non-lifecycle host; [ComponentActivity] hosts are closed automatically.
 */
class DownloadCoordinator(
    private val activity: ComponentActivity,
    private val repository: DownloadRepository,
    private val callback: Callback = Callback { },
) : DefaultLifecycleObserver, AutoCloseable {
    fun interface Callback {
        fun onEvent(event: Event)
    }

    data class Request(
        val url: String,
        val userAgent: String? = null,
        val contentDisposition: String? = null,
        val mimeType: String? = null,
        val contentLength: Long = -1,
        val allowLocalNetwork: Boolean = false,
    )

    sealed interface Event {
        data class ConfirmationCancelled(val fileName: String) : Event

        data class Queued(
            val recordId: Long,
            val downloadManagerId: Long,
            val fileName: String,
        ) : Event

        data class Retried(
            val recordId: Long,
            val downloadManagerId: Long,
        ) : Event

        data class StatusChanged(
            val recordId: Long,
            val downloadManagerId: Long,
            val status: DownloadStatus,
            val bytesDownloaded: Long,
            val totalBytes: Long,
            val localUri: String? = null,
            val failureReason: Int? = null,
        ) : Event

        data class Opened(val recordId: Long) : Event
        data class Deleted(val recordId: Long) : Event

        data class Failure(
            val operation: Operation,
            val cause: Throwable? = null,
        ) : Event
    }

    enum class Operation {
        VALIDATE,
        CONFIRM,
        ENQUEUE,
        TRACK,
        RETRY,
        OPEN,
        DELETE,
        RECEIVER,
    }

    private val downloadManager = requireNotNull(
        activity.getSystemService(DownloadManager::class.java),
    )
    private val coordinatorJob = SupervisorJob(activity.lifecycleScope.coroutineContext[Job])
    private val coordinatorScope = CoroutineScope(
        activity.lifecycleScope.coroutineContext + coordinatorJob,
    )
    private var confirmationDialog: AlertDialog? = null
    private var trackingJob: Job? = null
    private var receiverRegistered = false
    private var closed = false

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val managerId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, INVALID_ID)
            if (managerId < 0) return
            coordinatorScope.launchSafely(Operation.TRACK) {
                synchronize(managerId)
            }
        }
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    /** Shows a Material confirmation before enqueueing an approved HTTPS download. */
    fun requestDownload(request: Request) {
        val prepared = prepare(request)
        if (prepared == null) {
            showFailure(Operation.VALIDATE)
            return
        }
        if (!activityCanShowUi()) {
            callback.onEvent(Event.Failure(Operation.CONFIRM))
            return
        }

        confirmationDialog?.dismiss()
        confirmationDialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.download_confirmation_title)
            .setMessage(
                activity.getString(
                    R.string.download_confirmation_message,
                    prepared.fileName,
                    prepared.sourceHost,
                    prepared.displayMimeType,
                ),
            )
            .setPositiveButton(R.string.download_confirm) { _, _ ->
                coordinatorScope.launchSafely(Operation.ENQUEUE) {
                    enqueueInitial(prepared)
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                callback.onEvent(Event.ConfirmationCancelled(prepared.fileName))
            }
            .setOnCancelListener {
                callback.onEvent(Event.ConfirmationCancelled(prepared.fileName))
            }
            .create()
            .also(AlertDialog::show)
    }

    /** Convenience overload for [BrowserWebViewController.Listener.onDownloadRequested]. */
    fun requestDownload(
        request: WebDownloadRequest,
        allowLocalNetwork: Boolean = false,
    ) {
        requestDownload(
            Request(
                url = request.url,
                userAgent = request.userAgent,
                contentDisposition = request.contentDisposition,
                mimeType = request.mimeType,
                contentLength = request.contentLength,
                allowLocalNetwork = allowLocalNetwork,
            ),
        )
    }

    /** Explicit retry action; original request metadata and Cookie are reused. */
    fun retry(
        download: DownloadEntity,
        allowLocalNetwork: Boolean = false,
    ) {
        if (download.status != DownloadStatus.FAILED && download.status != DownloadStatus.CANCELLED) {
            callback.onEvent(Event.Failure(Operation.RETRY))
            return
        }
        val prepared = prepare(
            Request(
                url = download.sourceUrl,
                userAgent = download.userAgent,
                contentDisposition = download.contentDisposition,
                mimeType = download.mimeType,
                contentLength = download.totalBytes,
                allowLocalNetwork = allowLocalNetwork,
            ),
            cookieOverride = download.cookieHeader,
        ) ?: run {
            showFailure(Operation.RETRY)
            return
        }

        coordinatorScope.launchSafely(Operation.RETRY) {
            val enqueued = enqueuePlatform(prepared)
            val updated = try {
                repository.markRetried(download.id, enqueued.managerId)
            } catch (error: Throwable) {
                downloadManager.remove(enqueued.managerId)
                throw error
            }
            if (!updated) {
                downloadManager.remove(enqueued.managerId)
                throw IllegalStateException("Download record no longer exists")
            }
            Toast.makeText(activity, R.string.download_retry_started, Toast.LENGTH_SHORT).show()
            callback.onEvent(Event.Retried(download.id, enqueued.managerId))
        }
    }

    /** Opens a completed download using a grantable content URI from DownloadManager. */
    fun open(download: DownloadEntity) {
        coordinatorScope.launchSafely(Operation.OPEN) {
            if (download.status != DownloadStatus.SUCCESSFUL) {
                throw IllegalStateException("Only completed downloads can be opened")
            }
            val managerId = download.downloadManagerId
            val managerUri = managerId?.let { id ->
                runCatching { downloadManager.getUriForDownloadedFile(id) }.getOrNull()
            }
            val uri = managerUri ?: listOf(download.localUri, download.destinationUri)
                .firstNotNullOfOrNull(::grantableContentUri)
                ?: throw IllegalStateException("No grantable content URI is available")
            val mimeType = managerId?.let { id ->
                runCatching { downloadManager.getMimeTypeForDownloadedFile(id) }.getOrNull()
            }?.takeIf(String::isNotBlank)
                ?: download.mimeType?.substringBefore(';')?.takeIf(String::isNotBlank)
                ?: activity.contentResolver.getType(uri)
                ?: DEFAULT_MIME_TYPE
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                clipData = ClipData.newRawUri(download.fileName, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                activity.startActivity(intent)
            } catch (error: ActivityNotFoundException) {
                throw NoViewerException(error)
            } catch (error: SecurityException) {
                throw NoViewerException(error)
            }
            callback.onEvent(Event.Opened(download.id))
        }
    }

    /**
     * Deletes the Room record. Active platform work is cancelled first; a successful downloaded
     * file is retained unless [deleteDownloadedFile] is explicitly requested.
     */
    fun deleteRecord(
        download: DownloadEntity,
        deleteDownloadedFile: Boolean = false,
    ) {
        coordinatorScope.launchSafely(Operation.DELETE) {
            val managerId = download.downloadManagerId
            if (managerId != null && (download.status.isActive || deleteDownloadedFile)) {
                downloadManager.remove(managerId)
            }
            if (!repository.delete(download.id)) {
                throw IllegalStateException("Download record no longer exists")
            }
            Toast.makeText(activity, R.string.download_record_deleted, Toast.LENGTH_SHORT).show()
            callback.onEvent(Event.Deleted(download.id))
        }
    }

    fun refresh(downloadManagerId: Long) {
        if (downloadManagerId < 0) return
        coordinatorScope.launchSafely(Operation.TRACK) {
            synchronize(downloadManagerId)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (closed) return
        registerCompletionReceiver()
        startTracking()
    }

    override fun onStop(owner: LifecycleOwner) {
        trackingJob?.cancel()
        trackingJob = null
        unregisterCompletionReceiver()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        close()
    }

    override fun close() {
        if (closed) return
        closed = true
        confirmationDialog?.dismiss()
        confirmationDialog = null
        trackingJob?.cancel()
        trackingJob = null
        unregisterCompletionReceiver()
        activity.lifecycle.removeObserver(this)
        coordinatorScope.cancel()
    }

    private suspend fun enqueueInitial(prepared: PreparedRequest) {
        val enqueued = enqueuePlatform(prepared)
        val recordId = try {
            repository.recordEnqueued(
                downloadManagerId = enqueued.managerId,
                sourceUrl = prepared.url,
                fileName = prepared.fileName,
                mimeType = prepared.storedMimeType,
                contentDisposition = prepared.contentDisposition,
                userAgent = prepared.userAgent,
                cookieHeader = prepared.cookieHeader,
                destinationUri = enqueued.destinationUri,
            )
        } catch (error: Throwable) {
            downloadManager.remove(enqueued.managerId)
            throw error
        }
        if (prepared.contentLength > 0) {
            repository.updateProgress(
                downloadManagerId = enqueued.managerId,
                status = DownloadStatus.QUEUED,
                bytesDownloaded = 0,
                totalBytes = prepared.contentLength,
            )
        }
        Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show()
        callback.onEvent(Event.Queued(recordId, enqueued.managerId, prepared.fileName))
    }

    private fun enqueuePlatform(prepared: PreparedRequest): EnqueuedDownload {
        val destinationName = uniqueDestinationName(prepared.fileName)
        val request = DownloadManager.Request(Uri.parse(prepared.url))
            .setTitle(prepared.fileName)
            .setDescription(prepared.sourceHost)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        prepared.requestMimeType?.let(request::setMimeType)
        prepared.userAgent?.let { request.addRequestHeader(USER_AGENT_HEADER, it) }
        prepared.cookieHeader?.let { request.addRequestHeader(COOKIE_HEADER, it) }

        val destinationUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, destinationName)
            publicDownloadFile(destinationName).let(Uri::fromFile).toString()
        } else {
            request.setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                destinationName,
            )
            File(
                requireNotNull(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)),
                destinationName,
            ).let(Uri::fromFile).toString()
        }
        return EnqueuedDownload(downloadManager.enqueue(request), destinationUri)
    }

    @Suppress("DEPRECATION")
    private fun publicDownloadFile(fileName: String): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

    private fun startTracking() {
        if (trackingJob?.isActive == true) return
        trackingJob = coordinatorScope.launchSafely(Operation.TRACK) {
            repository.observeAll()
                .map { downloads ->
                    downloads.asSequence()
                        .filter { it.status.isActive }
                        .mapNotNull(DownloadEntity::downloadManagerId)
                        .distinct()
                        .sorted()
                        .toList()
                }
                .distinctUntilChanged()
                .collectLatest { managerIds ->
                    while (currentCoroutineContext().isActive && managerIds.isNotEmpty()) {
                        managerIds.forEach { synchronize(it) }
                        delay(PROGRESS_POLL_INTERVAL_MS)
                    }
                }
        }
    }

    private suspend fun synchronize(managerId: Long) {
        val current = repository.getByDownloadManagerId(managerId) ?: return
        val snapshot = query(managerId)
        if (snapshot == null) {
            if (current.status.isActive && repository.markFailed(managerId, DownloadManager.ERROR_UNKNOWN)) {
                callback.onEvent(
                    Event.StatusChanged(
                        recordId = current.id,
                        downloadManagerId = managerId,
                        status = DownloadStatus.FAILED,
                        bytesDownloaded = current.bytesDownloaded,
                        totalBytes = current.totalBytes,
                        failureReason = DownloadManager.ERROR_UNKNOWN,
                    ),
                )
            }
            return
        }

        when (snapshot.status) {
            DownloadStatus.SUCCESSFUL -> {
                val localUri = runCatching {
                    downloadManager.getUriForDownloadedFile(managerId)?.toString()
                }.getOrNull() ?: snapshot.localUri
                val changed = current.status != DownloadStatus.SUCCESSFUL ||
                    current.localUri != localUri ||
                    current.bytesDownloaded != snapshot.bytesDownloaded ||
                    current.totalBytes != snapshot.totalBytes
                if (changed) {
                    repository.markSuccessful(
                        managerId,
                        localUri,
                        snapshot.bytesDownloaded,
                        snapshot.totalBytes,
                    )
                    callback.onEvent(snapshot.toEvent(current.id, managerId, localUri))
                }
            }

            DownloadStatus.FAILED -> {
                val changed = current.status != DownloadStatus.FAILED ||
                    current.failureReason != snapshot.reason ||
                    current.bytesDownloaded != snapshot.bytesDownloaded ||
                    current.totalBytes != snapshot.totalBytes
                if (changed) {
                    repository.markFailed(
                        managerId,
                        snapshot.reason,
                        snapshot.bytesDownloaded,
                        snapshot.totalBytes,
                    )
                    callback.onEvent(snapshot.toEvent(current.id, managerId))
                }
            }

            else -> {
                val changed = current.status != snapshot.status ||
                    current.bytesDownloaded != snapshot.bytesDownloaded ||
                    current.totalBytes != snapshot.totalBytes
                if (changed) {
                    repository.updateProgress(
                        managerId,
                        snapshot.status,
                        snapshot.bytesDownloaded,
                        snapshot.totalBytes,
                    )
                    callback.onEvent(snapshot.toEvent(current.id, managerId))
                }
            }
        }
    }

    private fun query(managerId: Long): DownloadSnapshot? {
        val query = DownloadManager.Query().setFilterById(managerId)
        return downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val platformStatus = cursor.long(DownloadManager.COLUMN_STATUS).toInt()
            DownloadSnapshot(
                status = platformStatus.toDownloadStatus(),
                bytesDownloaded = cursor.long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    .coerceAtLeast(0),
                totalBytes = cursor.long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                localUri = cursor.string(DownloadManager.COLUMN_LOCAL_URI),
                reason = if (platformStatus == DownloadManager.STATUS_FAILED) {
                    cursor.long(DownloadManager.COLUMN_REASON).toInt()
                } else {
                    null
                },
            )
        }
    }

    private fun registerCompletionReceiver() {
        if (receiverRegistered) return
        try {
            ContextCompat.registerReceiver(
                activity,
                completionReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        } catch (error: RuntimeException) {
            callback.onEvent(Event.Failure(Operation.RECEIVER, error))
        }
    }

    private fun unregisterCompletionReceiver() {
        if (!receiverRegistered) return
        runCatching { activity.unregisterReceiver(completionReceiver) }
        receiverRegistered = false
    }

    private fun prepare(
        request: Request,
        cookieOverride: String? = null,
    ): PreparedRequest? {
        val decision = NavigationPolicy(request.allowLocalNetwork)
            .decide(request.url, NavigationSource.LINK_CLICK)
        val normalizedUrl = (decision as? NavigationDecision.Allow)?.url ?: return null
        val fileName = sanitizeFileName(
            runCatching {
                URLUtil.guessFileName(
                    normalizedUrl,
                    request.contentDisposition,
                    request.mimeType,
                )
            }.getOrDefault(DEFAULT_FILE_NAME),
        )
        val storedMimeType = cleanMetadata(request.mimeType, MAX_SHORT_METADATA_LENGTH)
        val requestMimeType = storedMimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf(MIME_TYPE_PATTERN::matches)
        val userAgent = cleanMetadata(request.userAgent, MAX_HEADER_LENGTH)
        val contentDisposition = cleanMetadata(
            request.contentDisposition,
            MAX_CONTENT_DISPOSITION_LENGTH,
        )
        val cookie = cleanMetadata(
            cookieOverride ?: runCatching {
                CookieManager.getInstance().getCookie(normalizedUrl)
            }.getOrNull(),
            MAX_COOKIE_LENGTH,
        )
        return PreparedRequest(
            url = normalizedUrl,
            sourceHost = UrlPolicy.hostOf(normalizedUrl) ?: normalizedUrl,
            fileName = fileName,
            storedMimeType = storedMimeType,
            requestMimeType = requestMimeType,
            displayMimeType = requestMimeType ?: DEFAULT_MIME_TYPE,
            contentDisposition = contentDisposition,
            userAgent = userAgent,
            cookieHeader = cookie,
            contentLength = request.contentLength.takeIf { it > 0 } ?: -1,
        )
    }

    private fun sanitizeFileName(value: String): String {
        val sanitized = value
            .replace(UNSAFE_FILE_NAME_CHARACTERS, "_")
            .trim(' ', '.')
            .take(MAX_FILE_NAME_LENGTH)
        return sanitized.ifBlank { DEFAULT_FILE_NAME }
    }

    private fun uniqueDestinationName(fileName: String): String {
        val dot = fileName.lastIndexOf('.').takeIf { it in 1 until fileName.lastIndex } ?: -1
        val base = if (dot >= 0) fileName.substring(0, dot) else fileName
        val extension = if (dot >= 0) fileName.substring(dot) else ""
        val suffix = "-${System.currentTimeMillis().toString(36)}"
        val availableBaseLength = (MAX_FILE_NAME_LENGTH - suffix.length - extension.length)
            .coerceAtLeast(1)
        return base.take(availableBaseLength) + suffix + extension
    }

    private fun cleanMetadata(value: String?, maxLength: Int): String? = value
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it.length <= maxLength && it.none(Char::isISOControl) }

    private fun grantableContentUri(value: String?): Uri? {
        val uri = value?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        return uri.takeIf { it.scheme.equals("content", ignoreCase = true) }
    }

    private fun activityCanShowUi(): Boolean = !activity.isFinishing && !activity.isDestroyed

    private fun showFailure(operation: Operation, cause: Throwable? = null) {
        val message = if (operation == Operation.OPEN) {
            R.string.download_open_failed
        } else {
            R.string.download_failed
        }
        if (activityCanShowUi()) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
        callback.onEvent(Event.Failure(operation, cause))
    }

    private fun CoroutineScope.launchSafely(
        operation: Operation,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            val reported = if (error is NoViewerException) error.cause else error
            showFailure(operation, reported)
        }
    }

    private data class PreparedRequest(
        val url: String,
        val sourceHost: String,
        val fileName: String,
        val storedMimeType: String?,
        val requestMimeType: String?,
        val displayMimeType: String,
        val contentDisposition: String?,
        val userAgent: String?,
        val cookieHeader: String?,
        val contentLength: Long,
    )

    private data class EnqueuedDownload(
        val managerId: Long,
        val destinationUri: String,
    )

    private data class DownloadSnapshot(
        val status: DownloadStatus,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val localUri: String?,
        val reason: Int?,
    ) {
        fun toEvent(
            recordId: Long,
            managerId: Long,
            successfulLocalUri: String? = localUri,
        ): Event.StatusChanged = Event.StatusChanged(
            recordId = recordId,
            downloadManagerId = managerId,
            status = status,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            localUri = successfulLocalUri,
            failureReason = reason,
        )
    }

    private class NoViewerException(cause: Throwable) : RuntimeException(cause)

    private companion object {
        const val INVALID_ID = -1L
        const val PROGRESS_POLL_INTERVAL_MS = 1_500L
        const val DEFAULT_FILE_NAME = "download"
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
        const val USER_AGENT_HEADER = "User-Agent"
        const val COOKIE_HEADER = "Cookie"
        const val MAX_FILE_NAME_LENGTH = 180
        const val MAX_SHORT_METADATA_LENGTH = 256
        const val MAX_CONTENT_DISPOSITION_LENGTH = 4_096
        const val MAX_HEADER_LENGTH = 4_096
        const val MAX_COOKIE_LENGTH = 65_536
        val MIME_TYPE_PATTERN = Regex("^[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+$")
        val UNSAFE_FILE_NAME_CHARACTERS = Regex("[\\\\/\\u0000-\\u001F\\u007F]")
    }
}

private val DownloadStatus.isActive: Boolean
    get() = this == DownloadStatus.QUEUED ||
        this == DownloadStatus.RUNNING ||
        this == DownloadStatus.PAUSED

private fun Int.toDownloadStatus(): DownloadStatus = when (this) {
    DownloadManager.STATUS_PENDING -> DownloadStatus.QUEUED
    DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
    DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
    else -> DownloadStatus.FAILED
}

private fun android.database.Cursor.long(columnName: String): Long =
    getLong(getColumnIndexOrThrow(columnName))

private fun android.database.Cursor.string(columnName: String): String? {
    val index = getColumnIndexOrThrow(columnName)
    return if (isNull(index)) null else getString(index)
}
