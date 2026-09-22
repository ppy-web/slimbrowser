package com.example.slimbrowser.data.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC")
    abstract fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC LIMIT :limit")
    abstract fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query(
        """
        SELECT * FROM history_entries
        WHERE title LIKE '%' || :query || '%' COLLATE NOCASE
           OR url LIKE '%' || :query || '%' COLLATE NOCASE
           OR host LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY visitedAt DESC
        """,
    )
    abstract fun search(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_entries WHERE id = :id")
    abstract suspend fun getById(id: Long): HistoryEntity?

    @Query("SELECT * FROM history_entries WHERE url = :url")
    abstract suspend fun getByUrl(url: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: HistoryEntity): Long

    @Query(
        """
        UPDATE history_entries
        SET title = :title,
            host = :host,
            faviconUri = COALESCE(:faviconUri, faviconUri),
            visitedAt = :visitedAt,
            visitCount = visitCount + 1
        WHERE url = :url
        """,
    )
    abstract suspend fun mergeVisit(
        url: String,
        title: String,
        host: String,
        faviconUri: String?,
        visitedAt: Long,
    ): Int

    @Query("UPDATE history_entries SET faviconUri = :faviconUri WHERE url = :url")
    abstract suspend fun updateFavicon(url: String, faviconUri: String?): Int

    @Query("DELETE FROM history_entries WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM history_entries")
    abstract suspend fun clear(): Int

    @Transaction
    open suspend fun recordVisit(entity: HistoryEntity): Long {
        val insertedId = insertIgnore(entity.copy(id = 0, visitCount = 1))
        if (insertedId != INSERT_CONFLICT) return insertedId

        check(
            mergeVisit(
                url = entity.url,
                title = entity.title,
                host = entity.host,
                faviconUri = entity.faviconUri,
                visitedAt = entity.visitedAt,
            ) == 1,
        )
        return checkNotNull(getByUrl(entity.url)).id
    }

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}
