package com.example.slimbrowser.ui.library.history

import com.example.slimbrowser.data.history.HistoryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryPeriod {
    TODAY,
    YESTERDAY,
    EARLIER,
}

sealed interface HistoryListItem {
    val stableId: Long

    data class Header(
        val period: HistoryPeriod,
    ) : HistoryListItem {
        override val stableId: Long = -(period.ordinal + 1L)
    }

    data class Entry(
        val history: HistoryEntity,
        val period: HistoryPeriod,
    ) : HistoryListItem {
        override val stableId: Long = history.id
    }
}

object HistoryListItems {
    fun build(
        entries: List<HistoryEntity>,
        query: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<HistoryListItem> {
        val normalizedQuery = query.trim()
        val filtered = if (normalizedQuery.isEmpty()) {
            entries
        } else {
            entries.filter { entry ->
                entry.title.contains(normalizedQuery, ignoreCase = true) ||
                    entry.url.contains(normalizedQuery, ignoreCase = true) ||
                    entry.host.contains(normalizedQuery, ignoreCase = true)
            }
        }

        val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val grouped = filtered
            .sortedByDescending(HistoryEntity::visitedAt)
            .groupBy { entry -> periodFor(entry.visitedAt, today, zoneId) }

        return buildList {
            HistoryPeriod.entries.forEach { period ->
                val periodEntries = grouped[period].orEmpty()
                if (periodEntries.isNotEmpty()) {
                    add(HistoryListItem.Header(period))
                    periodEntries.forEach { add(HistoryListItem.Entry(it, period)) }
                }
            }
        }
    }

    private fun periodFor(
        visitedAt: Long,
        today: LocalDate,
        zoneId: ZoneId,
    ): HistoryPeriod {
        val visitDate = Instant.ofEpochMilli(visitedAt).atZone(zoneId).toLocalDate()
        return when (visitDate) {
            today -> HistoryPeriod.TODAY
            today.minusDays(1) -> HistoryPeriod.YESTERDAY
            else -> HistoryPeriod.EARLIER
        }
    }
}
