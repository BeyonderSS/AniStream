package com.anistream.core.network

import com.anistream.core.common.ScraperConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScraperConfigUpdaterTest {
    private val shippedHianime = ScraperConfig.hianimeBase
    private val shippedHosts = ScraperConfig.movieBoxHosts
    private val shippedSecret = ScraperConfig.movieBoxSecret.toList()

    @After fun restore() {
        ScraperConfig.hianimeBase = shippedHianime
        ScraperConfig.movieBoxHosts = shippedHosts
        ScraperConfig.movieBoxSecret = shippedSecret.toByteArray()
        ScraperConfig.embedKey = "otaku-embed-v1"
        ScraperConfig.dramachiBase = "https://api.nodeobjects.com/"
    }

    private fun apply(json: String) = ScraperConfigUpdater.applyConfig(Json.parseToJsonElement(json).jsonObject)

    @Test fun changedValuesAreApplied() {
        apply(
            """{"hianime":{"config":{"base":"https://new.example/","key":"rotated"}},
                "moviebox":{"config":{"hosts":["https://a.example","https://b.example"],"secret":[1,2,3]}},
                "dramachi":{"config":{"base":"https://d.example"}}}"""
        )
        assertEquals("https://new.example", ScraperConfig.hianimeBase) // trailing slash trimmed
        assertEquals("rotated", ScraperConfig.embedKey)
        assertEquals(listOf("https://a.example", "https://b.example"), ScraperConfig.movieBoxHosts)
        assertEquals(listOf<Byte>(1, 2, 3), ScraperConfig.movieBoxSecret.toList())
        assertEquals("https://d.example/", ScraperConfig.dramachiBase) // slash added
    }

    @Test fun malformedValuesNeverReplaceGoodOnes() {
        apply(
            """{"hianime":{"config":{"base":42,"key":""}},
                "moviebox":{"config":{"hosts":["http://insecure.example"],"secret":[1,999],"retryStatus":"x"}},
                "dramachi":{"config":{"base":"http://plain.example"}},
                "unknownSource":{"config":{"x":1}}}"""
        )
        assertEquals(shippedHianime, ScraperConfig.hianimeBase)
        assertEquals("otaku-embed-v1", ScraperConfig.embedKey)
        assertEquals(shippedHosts, ScraperConfig.movieBoxHosts)
        assertEquals(shippedSecret, ScraperConfig.movieBoxSecret.toList())
        assertEquals("https://api.nodeobjects.com/", ScraperConfig.dramachiBase)
        assertTrue(ScraperConfig.movieBoxRetry.contains(429))
    }

    @Test fun missingSectionsLeaveEverythingAlone() {
        apply("{}")
        assertEquals(shippedHianime, ScraperConfig.hianimeBase)
    }
}
