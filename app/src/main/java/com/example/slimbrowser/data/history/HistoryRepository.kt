package com.example.slimbrowser.data.history

import java.net.URI
import kotlinx.coroutines.flow.Flow

class HistoryRepository(
    private val dao: HistoryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeAll(): Flow<List<HistoryEntity>> = dao.observeAll()

    fun observeRecent(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<HistoryEntity>> {
        require(limit > 0)
        return dao.observeRecent(limit)
    }

    fun search(query: String): Flow<List<HistoryEntity>> = dao.search(query.trim())

    suspend fun recordVisit(
        url: String,
        title: String,
        host: String = hostFrom(url),
        faviconUri: String? = null,
        visitedAt: Long = clock(),
        isPrivate: Boolean = false,
    ): Long? {
        if (isPrivate) return null
        require(url.isNotBlank())
        return dao.recordVisit(
            HistoryEntity(
                url = url,
                title = normalizeTitle(title, url),
                host = host,
                faviconUri = faviconUri,
                visitedAt = visitedAt,
            ),
        )
    }

    suspend fun getById(id: Long): HistoryEntity? = dao.getById(id)

    suspend fun updateFavicon(url: String, faviconUri: String?): Boolean =
        dao.updateFavicon(url, faviconUri) == 1

    suspend fun deleteById(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun clear(): Int = dao.clear()

    private companion object {
        const val DEFAULT_RECENT_LIMIT = 6

        fun hostFrom(url: String): String = runCatching { URI(url).host.orEmpty() }.getOrDefault("")

        fun normalizeTitle(title: String, fallback: String): String = title.trim()
            .replace(Regex("\\s+"), " ")
            .take(200)
            .ifBlank { fallback }
    }
}
