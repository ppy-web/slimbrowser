package com.example.slimbrowser.ui.library.history

import com.example.slimbrowser.data.history.HistoryEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryListItemsTest {
    private val zoneId = ZoneId.of("UTC")
    private val now = Instant.parse("2026-08-23T12:00:00Z").toEpochMilli()

    @Test
    fun buildGroupsAndSortsEntriesByLocalVisitDate() {
        val earlier = entry(3, "Earlier", "2026-08-20T09:00:00Z")
        val todayOlder = entry(2, "Today older", "2026-08-23T08:00:00Z")
        val yesterday = entry(4, "Yesterday", "2026-08-22T11:00:00Z")
        val todayNewer = entry(1, "Today newer", "2026-08-23T10:00:00Z")

        val rows = HistoryListItems.build(
            listOf(earlier, todayOlder, yesterday, todayNewer),
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(
            listOf(
                HistoryPeriod.TODAY,
                HistoryPeriod.YESTERDAY,
                HistoryPeriod.EARLIER,
            ),
            rows.filterIsInstance<HistoryListItem.Header>().map(HistoryListItem.Header::period),
        )
        assertEquals(
            listOf(1L, 2L, 4L, 3L),
            rows.filterIsInstance<HistoryListItem.Entry>().map { it.history.id },
        )
    }

    @Test
    fun searchMatchesTitleUrlOrHostAndOmitsEmptySections() {
        val matchByHost = entry(
            id = 1,
            title = "Example",
            instant = "2026-08-20T09:00:00Z",
            url = "https://docs.kotlinlang.org/guide.html",
            host = "docs.kotlinlang.org",
        )
        val unrelated = entry(2, "Android", "2026-08-23T09:00:00Z")

        val rows = HistoryListItems.build(
            listOf(matchByHost, unrelated),
            query = "KOTLINLANG",
            nowMillis = now,
            zoneId = zoneId,
        )

        assertEquals(listOf(HistoryPeriod.EARLIER), rows.filterIsInstance<HistoryListItem.Header>().map { it.period })
        assertEquals(listOf(matchByHost), rows.filterIsInstance<HistoryListItem.Entry>().map { it.history })
        assertTrue(rows.all { it.stableId != 0L })
    }

    private fun entry(
        id: Long,
        title: String,
        instant: String,
        url: String = "https://example.com/$id",
        host: String = "example.com",
    ) = HistoryEntity(
        id = id,
        url = url,
        title = title,
        host = host,
        visitedAt = Instant.parse(instant).toEpochMilli(),
    )
}
