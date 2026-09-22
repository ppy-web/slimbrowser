package com.example.slimbrowser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserPreferencesModelTest {
    @Test
    fun `legacy favorite codec round trips unicode and delimiters in url`() {
        val favorite = Favorite("示例 站点", "https://example.com/path?q=a%20b")

        assertEquals(favorite, FavoriteCodec.decode(FavoriteCodec.encode(favorite)))
    }

    @Test
    fun `legacy favorite codec rejects malformed values`() {
        assertNull(FavoriteCodec.decode("not-base64"))
        assertNull(FavoriteCodec.decode(FavoriteCodec.encode(Favorite("title", ""))))
    }

    @Test
    fun `legacy positional settings constructor remains compatible`() {
        val settings = BrowserSettings("https://example.com/", true, true, "file:///background")

        assertEquals("https://example.com/", settings.homeUrl)
        assertEquals(true, settings.fullscreenEnabled)
        assertEquals(true, settings.darkThemeEnabled)
        assertEquals("file:///background", settings.backgroundUri)
        assertEquals(BrowserSettings.DEFAULT_SEARCH_ENGINE_ID, settings.searchEngineId)
        assertEquals(BrowserSettings.DEFAULT_TEXT_ZOOM, settings.textZoom)
    }
}
