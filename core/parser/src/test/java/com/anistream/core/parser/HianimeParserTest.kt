package com.anistream.core.parser

import com.anistream.core.common.Result
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are real hianime.at / zokoanime responses saved 2026-10-19. */
class HianimeParserTest {

    private fun fixture(name: String) = javaClass.getResource("/hianime/$name")!!.readText()

    private fun <T> Result<T>.ok(): T = when (this) {
        is Result.Success -> data
        is Result.Error -> throw AssertionError("parse failed", exception)
        Result.Loading -> throw AssertionError("loading")
    }

    @Test fun search() {
        val cards = HianimeParser.parseCards(fixture("search.html")).ok()
        val frieren = cards.first()
        assertEquals("frieren-beyond-journeys-end-481", frieren.slug)
        assertEquals("Frieren: Beyond Journey's End", frieren.title)
        assertEquals("https://cdn.anipixcdn.co/thumbnail/bb6d2babd7797d94d8f4a8600bc9b44e.jpg", frieren.posterUrl)
        assertEquals("TV", frieren.type)
        assertTrue(cards.any { it.slug == "frieren-beyond-journeys-end-season-2-808" })
        assertEquals(cards.size, cards.distinctBy { it.slug }.size)
        // sidebar top-10 cards must not leak in
        assertEquals(4, cards.size)
    }

    @Test fun home() {
        val home = HianimeParser.parseHome(fixture("home.html")).ok()
        assertTrue(home.trending.isNotEmpty())
        assertEquals("one-piece-1", home.trending.first().slug)
        assertTrue(home.topAiring.isNotEmpty())
        assertTrue(home.popular.isNotEmpty())
        assertTrue(home.favorites.isNotEmpty())
        assertTrue(home.completed.isNotEmpty())
        assertTrue(home.recentEpisodes.isNotEmpty())
        assertTrue(home.topAiring.all { it.slug.isNotBlank() && it.title.isNotBlank() && it.posterUrl != null })

        val genres = HianimeParser.parseGenres(fixture("home.html")).ok()
        assertEquals("action", genres.first().slug)
        assertEquals("Action", genres.first().name)
    }

    @Test fun detail() {
        val d = HianimeParser.parseDetail(fixture("detail.html"), "frieren-beyond-journeys-end-481").ok()
        assertEquals("Frieren: Beyond Journey's End", d.title)
        assertEquals("Sousou no Frieren", d.titleJapanese)
        assertTrue(d.synopsis!!.startsWith("During their decade-long quest"))
        assertEquals("https://hianime.at/storage/media/2026/08/frieren-beyond-journeys-end.webp", d.posterUrl)
        assertEquals(listOf("adventure", "fantasy", "shounen", "drama", "award-winning"), d.genres.map { it.slug })
        assertEquals("Award Winning", d.genres.last().name)
        assertEquals(listOf("frieren-beyond-journeys-end-481", "frieren-beyond-journeys-end-season-2-808"), d.seasons.map { it.slug })
        assertEquals("Season 2", d.seasons[1].title)
        assertTrue(d.recommendations.isNotEmpty())
        assertEquals(28, d.subEpisodes)
        assertEquals(28, d.dubEpisodes)
        assertEquals(28, d.episodes)
        assertEquals(9.36, d.score!!, 0.001)
        assertEquals("PG-13", d.rating)
        assertEquals("TV", d.type)
        assertEquals("25m", d.duration)
        assertEquals("Finished Airing", d.status)
        assertEquals(2023, d.year)
        assertEquals(listOf("Madhouse"), d.studios)
    }

    @Test fun episodes() {
        val eps = HianimeParser.parseEpisodes(fixture("eps.json"), "frieren-beyond-journeys-end-481").ok()
        assertEquals(28, eps.size)
        assertEquals(9227L, eps[0].id)
        assertEquals(1, eps[0].number)
        assertEquals("The Journey's End", eps[0].title)
        assertTrue(HianimeParser.parseEpisodes(fixture("eps.json"), "other-1").ok().isEmpty())
        assertEquals(481L, HianimeParser.numericId("frieren-beyond-journeys-end-481"))
    }

    @Test fun servers() {
        val srv = fixture("srv.json")
        assertEquals("https://zokoanime.video/stream/mal/52991/1/sub", HianimeParser.parseEmbedUrl(srv, "sub").ok())
        assertEquals("https://zokoanime.video/stream/mal/52991/1/dub", HianimeParser.parseEmbedUrl(srv, "eng").ok())
    }

    @Test fun embed() {
        val expected = Json.parseToJsonElement(fixture("blob.json")).jsonObject
        val cfg = HianimeParser.parseEmbed(fixture("embed.html")).ok()
        assertEquals(expected["src"]!!.jsonPrimitive.content, cfg.masterUrl)
        assertEquals(
            "https://hls.dramahot.top/v/scxqicy/55ejtphmn9/e38a115ezb/dnfinmtr3wdbax/subs/nzo9j4s07a98qkl1.vtt",
            cfg.subtitleUrl
        )
    }

    @Test fun masterPlaylist() {
        val master = "https://hls.dramahot.top/p/scxqicy/55ejtphmn9/e38a115ezb/dnfinmtr3wdbax/master.m3u8"
        val srcs = HianimeParser.parseMasterPlaylist(fixture("master.m3u8"), master, "https://zokoanime.video/", "sub.vtt").ok()
        assertEquals(listOf("1080p", "720p", "360p"), srcs.map { it.qualityLabel })
        assertEquals("https://hls.dramahot.top/p/scxqicy/55ejtphmn9/e38a115ezb/dnfinmtr3wdbax/720/index.m3u8", srcs[1].m3u8Url)
        assertEquals(1280, srcs[1].width)
        assertEquals(3_000_000L, srcs[1].bandwidth)
        assertTrue(srcs.all { it.referer == "https://zokoanime.video/" && it.subtitleUrl == "sub.vtt" })
    }

    @Test fun scheduleDay() {
        val entries = HianimeParser.parseScheduleDay(fixture("day.json"), "Monday").ok()
        assertEquals(6, entries.size)
        val first = entries.first()
        assertEquals("renegade-immortal-8", first.animeSlug)
        assertEquals("Renegade Immortal", first.animeTitle)
        assertEquals(163, first.episodeNumber)
        assertEquals("02:00", first.time)
        assertEquals("Monday", first.dayOfWeek)
    }
}
