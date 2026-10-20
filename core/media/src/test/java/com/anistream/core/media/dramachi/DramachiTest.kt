package com.anistream.core.media.dramachi

import com.anistream.core.media.Stremio
import com.anistream.domain.model.MediaType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Fixtures captured from api.nodeobjects.com on 2026-10-20 (Queen of Tears, Parasite). */
class DramachiTest {

    private fun json(name: String) = Json.parseToJsonElement(javaClass.getResource("/dramachi/$name")!!.readText()) as JsonObject

    @Test
    fun search_matchesDramaAndMovieWithYearInTitle() {
        val drama = Stremio.pickMatch("Queen of Tears", MediaType.SERIES, null, DramachiParser.search(json("search.json")))
        assertEquals("61779", drama?.id)

        val movies = DramachiParser.search(json("search_movie.json"))
        val parasite = Stremio.pickMatch("Parasite", MediaType.MOVIE, 2019..2019, movies)
        assertEquals("40770", parasite?.id)
        assertEquals("Parasite", parasite?.title)
        assertNull(Stremio.pickMatch("Parasite", MediaType.MOVIE, 2010..2010, movies))
    }

    @Test
    fun rip_picksOriginalVersionOfSeason() {
        assertEquals("Season 01", DramachiParser.rip(json("title.json"), 1))
        assertNull(DramachiParser.rip(json("title.json"), 2))
    }

    @Test
    fun episodes_matchByNumber() {
        val eplist = json("eplist.json")
        val e16 = DramachiParser.episodes(eplist, 1, 16).single()
        assertEquals("628750", e16.fid)
        assertEquals("4", e16.disk)
        assertEquals(540, e16.height)
        assertEquals(228925112L, e16.sizeBytes)
        assertEquals(16, e16.number)
        assertEquals(2, DramachiParser.episodes(eplist, 1, 2).single().number)
        assertEquals((1..16).toList(), DramachiParser.episodes(eplist, 0, 0).map { it.number }) // movie: all parts, in order
        assertEquals(2, DramachiParser.episodeNumber("Parasite 2019 002"))
        assertEquals(7, DramachiParser.episodeNumber("Squid Game S02E07 DUB"))
    }

    @Test
    fun fileUrl_joinsHostAndPath() {
        assertEquals(
            "https://o4nbr1fc.pdkvzojivp.xyz/cdn/zone1/K_Drama/Queen_of_Tears/Season_01/Queen_of_Tears_S01E16_KimoiTV.com.mkv",
            DramachiParser.fileUrl(json("getfile.json"))
        )
    }
}
