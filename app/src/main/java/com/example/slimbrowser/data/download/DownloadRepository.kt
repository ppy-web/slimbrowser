package com.example.slimbrowser.data.download

import kotlinx.coroutines.flow.Flow

class DownloadRepository(
    private val dao: DownloadDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<DownloadEntity>> = dao.observeAll()

    fun observeByStatus(status: DownloadStatus): Flow<List<DownloadEntity>> =
        dao.observeByStatus(status)

    fun observeById(id: Long): Flow<DownloadEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): DownloadEntity? = dao.getById(id)

    suspend fun getByDownloadManagerId(downloadManagerId: Long): DownloadEntity? =
        dao.getByDownloadManagerId(downloadManagerId)

    suspend fun recordEnqueued(
        downloadManagerId: Long,
        sourceUrl: String,
        fileName: String,
        mimeType: String? = null,
        contentDisposition: String? = null,
        userAgent: String? = null,
        cookieHeader: String? = null,
        destinationUri: String? = null,
    ): Long {
        require(downloadManagerId >= 0)
        require(sourceUrl.isNotBlank())
        val now = clock()
        return dao.insert(
            DownloadEntity(
                downloadManagerId = downloadManagerId,
                sourceUrl = sourceUrl,
                fileName = fileName.ifBlank { "download" },
                mimeType = mimeType,
                contentDisposition = contentDisposition,
                userAgent = userAgent,
                cookieHeader = cookieHeader,
                destinationUri = destinationUri,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateProgress(
        downloadManagerId: Long,
        status: DownloadStatus,
        bytesDownloaded: Long,
        totalBytes: Long,
    ): Boolean = dao.updateProgress(
        downloadManagerId,
        status,
        bytesDownloaded,
        totalBytes,
        clock(),
    ) > 0

    suspend fun markSuccessful(
        downloadManagerId: Long,
        localUri: String?,
        bytesDownloaded: Long,
        totalBytes: Long,
    ): Boolean = dao.finish(
        downloadManagerId = downloadManagerId,
        status = DownloadStatus.SUCCESSFUL,
        localUri = localUri,
        failureReason = null,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        updatedAt = clock(),
    ) > 0

    suspend fun markFailed(
        downloadManagerId: Long,
        failureReason: Int?,
        bytesDownloaded: Long? = null,
        totalBytes: Long? = null,
    ): Boolean {
        val current = dao.getByDownloadManagerId(downloadManagerId)
        return dao.finish(
            downloadManagerId = downloadManagerId,
            status = DownloadStatus.FAILED,
            localUri = null,
            failureReason = failureReason,
            bytesDownloaded = bytesDownloaded ?: current?.bytesDownloaded ?: 0,
            totalBytes = totalBytes ?: current?.totalBytes ?: -1,
            updatedAt = clock(),
        ) > 0
    }

    /** Keeps all original request metadata and binds a retry to its new DownloadManager id. */
    suspend fun markRetried(id: Long, newDownloadManagerId: Long): Boolean {
        require(newDownloadManagerId >= 0)
        return dao.markRetried(id, newDownloadManagerId, DownloadStatus.QUEUED, clock()) > 0
    }

    suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun clear(): Int = dao.clear()
}
