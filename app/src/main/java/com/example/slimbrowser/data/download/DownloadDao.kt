package com.example.slimbrowser.data.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY updatedAt DESC")
    fun observeByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun observeById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE downloadManagerId = :downloadManagerId")
    suspend fun getByDownloadManagerId(downloadManagerId: Long): DownloadEntity?

    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity): Int

    @Query(
        """
        UPDATE downloads
        SET status = :status,
            bytesDownloaded = :bytesDownloaded,
            totalBytes = :totalBytes,
            updatedAt = :updatedAt
        WHERE downloadManagerId = :downloadManagerId
        """,
    )
    suspend fun updateProgress(
        downloadManagerId: Long,
        status: DownloadStatus,
        bytesDownloaded: Long,
        totalBytes: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE downloads
        SET status = :status,
            localUri = :localUri,
            failureReason = :failureReason,
            bytesDownloaded = :bytesDownloaded,
            totalBytes = :totalBytes,
            updatedAt = :updatedAt
        WHERE downloadManagerId = :downloadManagerId
        """,
    )
    suspend fun finish(
        downloadManagerId: Long,
        status: DownloadStatus,
        localUri: String?,
        failureReason: Int?,
        bytesDownloaded: Long,
        totalBytes: Long,
        updatedAt: Long,
    ): Int

    @Query(
        """
        UPDATE downloads
        SET downloadManagerId = :newDownloadManagerId,
            status = :queuedStatus,
            failureReason = NULL,
            localUri = NULL,
            bytesDownloaded = 0,
            totalBytes = -1,
            retryCount = retryCount + 1,
            updatedAt = :updatedAt,
            lastAttemptAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markRetried(
        id: Long,
        newDownloadManagerId: Long,
        queuedStatus: DownloadStatus,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM downloads")
    suspend fun clear(): Int
}
