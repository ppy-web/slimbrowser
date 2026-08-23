package com.example.slimbrowser.data

import com.example.slimbrowser.data.bookmark.BookmarkDao
import com.example.slimbrowser.data.bookmark.BookmarkEntity
import com.example.slimbrowser.data.bookmark.BookmarkRepository
import com.example.slimbrowser.data.history.HistoryDao
import com.example.slimbrowser.data.history.HistoryEntity
import com.example.slimbrowser.data.history.HistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryBehaviorTest {
    @Test
    fun `history visits merge by url and recent defaults to six`() = runBlocking {
        val dao = FakeHistoryDao()
        var now = 100L
        val repository = HistoryRepository(dao) { now++ }

        val firstId = repository.recordVisit("https://example.com/", "First")
        val secondId = repository.recordVisit("https://example.com/", "Updated")
        repeat(7) { index ->
            repository.recordVisit("https://example$index.com/", "Site $index")
        }

        assertEquals(firstId, secondId)
        assertEquals(2, dao.getByUrl("https://example.com/")?.visitCount)
        assertEquals("Updated", dao.getByUrl("https://example.com/")?.title)
        assertTrue(repository.updateFavicon("https://example.com/", "file:///favicon.png"))
        assertEquals("file:///favicon.png", dao.getByUrl("https://example.com/")?.faviconUri)
        assertEquals(6, repository.observeRecent().first().size)
    }

    @Test
    fun `private history is not recorded`() = runBlocking {
        val dao = FakeHistoryDao()
        val repository = HistoryRepository(dao)

        assertEquals(null, repository.recordVisit("https://example.com/", "Private", isPrivate = true))
        assertTrue(repository.observeAll().first().isEmpty())
    }

    @Test
    fun `bookmark duplicate url updates existing row and reorder is stable`() = runBlocking {
        val dao = FakeBookmarkDao()
        var now = 10L
        val repository = BookmarkRepository(dao) { now++ }

        val firstId = repository.addOrUpdate("https://example.com/", "Old")
        val secondId = repository.addOrUpdate(
            "https://example.com/",
            "New",
            pinnedToHome = true,
        )
        val otherId = repository.addOrUpdate("https://other.example/", "Other")
        repository.reorder(listOf(otherId, firstId))

        assertEquals(firstId, secondId)
        assertEquals("New", dao.getById(firstId)?.title)
        assertTrue(dao.getById(firstId)?.pinnedToHome == true)
        assertEquals(listOf(otherId, firstId), repository.observeAll().first().map { it.id })
        assertFalse(repository.delete(Long.MAX_VALUE))
    }
}

private class FakeHistoryDao : HistoryDao() {
    private val rows = linkedMapOf<Long, HistoryEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<HistoryEntity>> =
        flowOf(rows.values.sortedByDescending { it.visitedAt })

    override fun observeRecent(limit: Int): Flow<List<HistoryEntity>> =
        flowOf(rows.values.sortedByDescending { it.visitedAt }.take(limit))

    override fun search(query: String): Flow<List<HistoryEntity>> = flowOf(
        rows.values.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.url.contains(query, ignoreCase = true) ||
                it.host.contains(query, ignoreCase = true)
        },
    )

    override suspend fun getById(id: Long): HistoryEntity? = rows[id]

    override suspend fun getByUrl(url: String): HistoryEntity? = rows.values.firstOrNull { it.url == url }

    override suspend fun insertIgnore(entity: HistoryEntity): Long {
        if (rows.values.any { it.url == entity.url }) return -1L
        val id = nextId++
        rows[id] = entity.copy(id = id)
        return id
    }

    override suspend fun mergeVisit(
        url: String,
        title: String,
        host: String,
        faviconUri: String?,
        visitedAt: Long,
    ): Int {
        val existing = rows.values.firstOrNull { it.url == url } ?: return 0
        rows[existing.id] = existing.copy(
            title = title,
            host = host,
            faviconUri = faviconUri,
            visitedAt = visitedAt,
            visitCount = existing.visitCount + 1,
        )
        return 1
    }

    override suspend fun updateFavicon(url: String, faviconUri: String?): Int {
        val existing = rows.values.firstOrNull { it.url == url } ?: return 0
        rows[existing.id] = existing.copy(faviconUri = faviconUri)
        return 1
    }

    override suspend fun deleteById(id: Long): Int = if (rows.remove(id) != null) 1 else 0

    override suspend fun clear(): Int = rows.size.also { rows.clear() }
}

private class FakeBookmarkDao : BookmarkDao() {
    private val rows = linkedMapOf<Long, BookmarkEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<BookmarkEntity>> =
        flowOf(rows.values.sortedWith(compareBy<BookmarkEntity> { it.sortOrder }.thenBy { it.createdAt }))

    override fun observePinned(limit: Int): Flow<List<BookmarkEntity>> = flowOf(
        rows.values.filter { it.pinnedToHome }
            .sortedWith(compareBy<BookmarkEntity> { it.sortOrder }.thenBy { it.createdAt })
            .take(limit),
    )

    override fun search(query: String): Flow<List<BookmarkEntity>> = flowOf(
        rows.values.filter {
            it.title.contains(query, ignoreCase = true) || it.url.contains(query, ignoreCase = true)
        },
    )

    override suspend fun getById(id: Long): BookmarkEntity? = rows[id]

    override suspend fun getByUrl(url: String): BookmarkEntity? = rows.values.firstOrNull { it.url == url }

    override suspend fun maxSortOrder(): Int = rows.values.maxOfOrNull(BookmarkEntity::sortOrder) ?: -1

    override suspend fun insert(entity: BookmarkEntity): Long {
        require(rows.values.none { it.url == entity.url })
        val id = nextId++
        rows[id] = entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: BookmarkEntity): Int {
        if (entity.id !in rows) return 0
        require(rows.values.none { it.id != entity.id && it.url == entity.url })
        rows[entity.id] = entity
        return 1
    }

    override suspend fun deleteById(id: Long): Int = if (rows.remove(id) != null) 1 else 0

    override suspend fun clear(): Int = rows.size.also { rows.clear() }

    override suspend fun updateSortOrder(id: Long, sortOrder: Int, updatedAt: Long): Int {
        val existing = rows[id] ?: return 0
        rows[id] = existing.copy(sortOrder = sortOrder, updatedAt = updatedAt)
        return 1
    }
}
