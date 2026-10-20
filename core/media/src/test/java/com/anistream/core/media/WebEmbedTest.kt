package com.anistream.core.media

import com.anistream.domain.model.WebEmbed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebEmbedTest {
    @Test fun urls() {
        assertEquals("https://vsembed.ru/embed/movie?imdb=tt0816692", WebEmbed.url("vsembed.ru", "tt0816692", 0, 0))
        assertEquals("https://vsembed.ru/embed/tv?imdb=tt0903747&season=1&episode=2", WebEmbed.url("vsembed.ru", "tt0903747", 1, 2))
    }

    @Test fun navigationFilter() {
        assertTrue(WebEmbed.allows("https", "vsembed.ru", mainFrame = true))
        assertTrue(WebEmbed.allows("https", "www.vidsrc.sh", mainFrame = true))
        assertFalse(WebEmbed.allows("https", "ads.example.com", mainFrame = true))
        assertFalse(WebEmbed.allows("https", "evilvsembed.ru", mainFrame = true))
        assertTrue(WebEmbed.allows("https", "stellarconductornexus.com", mainFrame = false))
        assertFalse(WebEmbed.allows("intent", null, mainFrame = false))
        assertFalse(WebEmbed.allows("market", "details", mainFrame = true))
    }
}
