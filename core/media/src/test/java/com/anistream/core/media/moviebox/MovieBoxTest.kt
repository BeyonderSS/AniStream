package com.anistream.core.media.moviebox

import com.anistream.domain.model.MediaType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expected signatures come from the Python port that authenticates against the live API. */
class MovieBoxTest {

    private fun data(name: String): JsonElement {
        val text = javaClass.getResource("/moviebox/$name")!!.readText()
        return (Json.parseToJsonElement(text) as JsonObject)["data"]!!
    }

    private val ts = 1760000000000L

    @Test
    fun clientToken_matchesReference() {
        assertEquals("1760000000000,72fb86583d2470283955495668cf30f5", MovieBoxSigner.clientToken(ts))
    }

    @Test
    fun postSignature_matchesReference() {
        val body = """{"keyword":"interstellar","page":1,"perPage":15,"subjectType":0}"""
        assertEquals(
            "1760000000000|2|TJsqdeqkkUmRHBlKKvgyPQ==",
            MovieBoxSigner.signature("POST", "https://api6.aoneroom.com/wefeed-mobile-bff/subject-api/search/v2", body, ts)
        )
    }

    @Test
    fun getSignature_sortsAndDecodesQuery() {
        val url = "https://api6.aoneroom.com/wefeed-mobile-bff/subject-api/resource-position/v2" +
            "?subjectId=1&resourceId=2&failUrl=https%3A%2F%2Fmacdn.aoneroom.com%2Fother%2Fa.mp4&failCode=404"
        assertEquals("1760000000000|2|N2hjI1R4okOIV+3OO1Fyzg==", MovieBoxSigner.signature("GET", url, null, ts))
    }

    @Test
    fun search_mapsMoviesAndSeries() {
        val movies = MovieBoxParser.searchResults(data("search_movie.json"))
        val first = movies.first()
        assertTrue(first.id.startsWith("mb:"))
        assertTrue(first.title.contains("Interstellar"))
        assertEquals(2014, first.year)
        assertEquals(MediaType.MOVIE, first.type)
        assertTrue(first.posterUrl!!.startsWith("https://"))
        assertTrue(MovieBoxParser.searchResults(data("search_series.json")).any { it.type == MediaType.SERIES })
    }

    @Test
    fun seriesDetail_hasSeasonsDubsAndCast() {
        val d = MovieBoxParser.detail(data("detail_series.json"), data("season_info.json"))
        assertEquals(MediaType.SERIES, d.type)
        assertEquals(listOf(1 to 7, 2 to 13, 3 to 13, 4 to 13, 5 to 16), d.seasons.map { it.number to it.episodeCount })
        assertTrue(d.audioVariants.any { it.label == "Original Audio" })
        assertEquals(1, d.audioVariants.count { it.isCurrent })
        assertTrue(d.cast.contains("Bryan Cranston"))
        assertTrue(d.backdropUrl!!.startsWith("https://"))
    }

    @Test
    fun movieDetail_decodesEntities() {
        val d = MovieBoxParser.detail(data("detail_movie.json"), null)
        assertEquals(MediaType.MOVIE, d.type)
        assertFalse(d.description!!.contains("&apos;"))
        assertTrue(d.seasons.isEmpty())
    }

    @Test
    fun resourcePosition_recoversSignedMp4_notNotice() {
        val listed = MovieBoxParser.resources(data("resource_v2_s1e2.json")).first()
        assertTrue(MovieBoxParser.isNotice(listed.link))
        assertEquals(1, listed.season)
        assertEquals(2, listed.episode)
        val url = MovieBoxParser.recoveredLink(data("resource_position_s1e2.json"), listed)!!
        assertTrue(url.startsWith("https://bcdn.hakunaymatata.com/"))
        assertFalse(MovieBoxParser.isNotice(url))
        // a different resource id or episode must never be accepted
        assertNull(MovieBoxParser.recoveredLink(data("resource_position_s1e2.json"), listed.copy(resourceId = "other")))
        assertNull(MovieBoxParser.recoveredLink(data("resource_position_s1e2.json"), listed.copy(episode = 3)))
    }

    @Test
    fun movieResources_recoverForEachQuality() {
        val listed = MovieBoxParser.resources(data("resource_v2_movie.json"))
        assertTrue(listed.isNotEmpty())
        assertTrue(listed.all { it.season == 0 && it.episode == 0 })
        assertTrue(MovieBoxParser.recoveredLink(data("resource_position_movie.json"), listed.first()) != null)
    }

    @Test
    fun captions_areSrtWithEnglishFirst() {
        val subs = MovieBoxParser.captions(data("captions_s1e2.json"))
        assertEquals("en", subs.first().languageCode)
        assertEquals("application/x-subrip", subs.first().mimeType)
    }
}
