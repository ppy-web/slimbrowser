package com.example.slimbrowser.data.bookmark

import kotlinx.coroutines.flow.Flow

class BookmarkRepository(
    private val dao: BookmarkDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<BookmarkEntity>> = dao.observeAll()

    fun observePinned(limit: Int = DEFAULT_PINNED_LIMIT): Flow<List<BookmarkEntity>> {
        require(limit > 0)
        return dao.observePinned(limit)
    }

    fun search(query: String): Flow<List<BookmarkEntity>> = dao.search(query.trim())

    suspend fun getById(id: Long): BookmarkEntity? = dao.getById(id)

    suspend fun getByUrl(url: String): BookmarkEntity? = dao.getByUrl(url)

    suspend fun addOrUpdate(
        url: String,
        title: String,
        pinnedToHome: Boolean = false,
        sortOrder: Int? = null,
    ): Long = save(null, url, title, pinnedToHome, sortOrder)

    suspend fun update(
        id: Long,
        url: String,
        title: String,
        pinnedToHome: Boolean,
        sortOrder: Int? = null,
    ): Long = save(id, url, title, pinnedToHome, sortOrder)

    suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun clear(): Int = dao.clear()

    suspend fun reorder(idsInOrder: List<Long>) = dao.reorder(idsInOrder, clock())

    private suspend fun save(
        id: Long?,
        url: String,
        title: String,
        pinnedToHome: Boolean,
        sortOrder: Int?,
    ): Long {
        require(url.isNotBlank())
        return dao.save(
            id = id,
            title = title.trim().replace(Regex("\\s+"), " ").take(200).ifBlank { url },
            url = url,
            pinnedToHome = pinnedToHome,
            sortOrder = sortOrder,
            updatedAt = clock(),
        )
    }

    private companion object {
        const val DEFAULT_PINNED_LIMIT = 8
    }
}
