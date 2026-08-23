package com.example.slimbrowser.data.bookmark

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY sortOrder ASC, createdAt ASC")
    abstract fun observeAll(): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE pinnedToHome = 1
        ORDER BY sortOrder ASC, createdAt ASC
        LIMIT :limit
        """,
    )
    abstract fun observePinned(limit: Int): Flow<List<BookmarkEntity>>

    @Query(
        """
        SELECT * FROM bookmarks
        WHERE title LIKE '%' || :query || '%' COLLATE NOCASE
           OR url LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY sortOrder ASC, createdAt ASC
        """,
    )
    abstract fun search(query: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    abstract suspend fun getById(id: Long): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE url = :url")
    abstract suspend fun getByUrl(url: String): BookmarkEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM bookmarks")
    abstract suspend fun maxSortOrder(): Int

    @Insert
    abstract suspend fun insert(entity: BookmarkEntity): Long

    @Update
    abstract suspend fun update(entity: BookmarkEntity): Int

    @Query("DELETE FROM bookmarks WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM bookmarks")
    abstract suspend fun clear(): Int

    @Query("UPDATE bookmarks SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    abstract suspend fun updateSortOrder(id: Long, sortOrder: Int, updatedAt: Long): Int

    @Transaction
    open suspend fun save(
        id: Long?,
        title: String,
        url: String,
        pinnedToHome: Boolean,
        sortOrder: Int?,
        updatedAt: Long,
    ): Long {
        val sameUrl = getByUrl(url)
        val sameId = id?.let { getById(it) }

        if (sameUrl != null) {
            if (sameId != null && sameId.id != sameUrl.id) deleteById(sameId.id)
            check(
                update(
                    sameUrl.copy(
                        title = title,
                        pinnedToHome = pinnedToHome,
                        sortOrder = sortOrder ?: sameUrl.sortOrder,
                        updatedAt = updatedAt,
                    ),
                ) == 1,
            )
            return sameUrl.id
        }

        if (sameId != null) {
            check(
                update(
                    sameId.copy(
                        title = title,
                        url = url,
                        pinnedToHome = pinnedToHome,
                        sortOrder = sortOrder ?: sameId.sortOrder,
                        updatedAt = updatedAt,
                    ),
                ) == 1,
            )
            return sameId.id
        }

        return insert(
            BookmarkEntity(
                title = title,
                url = url,
                createdAt = updatedAt,
                updatedAt = updatedAt,
                sortOrder = sortOrder ?: (maxSortOrder() + 1),
                pinnedToHome = pinnedToHome,
            ),
        )
    }

    @Transaction
    open suspend fun reorder(idsInOrder: List<Long>, updatedAt: Long) {
        idsInOrder.forEachIndexed { index, id ->
            check(updateSortOrder(id, index, updatedAt) == 1)
        }
    }
}
