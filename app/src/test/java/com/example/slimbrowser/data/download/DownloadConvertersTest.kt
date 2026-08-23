package com.example.slimbrowser.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadConvertersTest {
    private val converters = DownloadConverters()

    @Test
    fun `download status round trips`() {
        DownloadStatus.entries.forEach { status ->
            assertEquals(status, converters.toStatus(converters.fromStatus(status)))
        }
    }

    @Test
    fun `unknown persisted status fails closed`() {
        assertEquals(DownloadStatus.FAILED, converters.toStatus("FUTURE_STATUS"))
    }
}
