package com.anistream.core.media.fourkhdhub

import com.anistream.core.media.Stremio
import com.anistream.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures captured from 4khdhub.one and its mirror chain on 2026-10-20 (series page trimmed to a few episode items). */
class FourKHdHubTest {

    private fun html(name: String) = javaClass.getResource("/fourkhdhub/$name")!!.readText()

    @Test
    fun search_matchesMovieAndSeries() {
        val movies = FourKHdHubParser.search(html("search_movie.html"))
        val interstellar = Stremio.pickMatch("Interstellar", MediaType.MOVIE, 2014..2014, movies)
        assertEquals("/interstellar-movie-458/", interstellar?.id)
        assertEquals(2014, interstellar?.year)

        val series = FourKHdHubParser.search(html("search_series.html"))
        assertEquals(MediaType.MOVIE, series.first { it.title.startsWith("El Camino") }.type)
        assertEquals("/breaking-bad-series-1385/", Stremio.pickMatch("Breaking Bad", MediaType.SERIES, null, series)?.id)
        assertNull(Stremio.pickMatch("Breaking Bad", MediaType.MOVIE, null, series))
    }

    @Test
    fun movieReleases_bestQualityFirstWithSizesAndMirrors() {
        val releases = FourKHdHubParser.releases(html("movie_page.html"), 0, 0)
        assertEquals(9, releases.size)
        assertEquals(listOf(2160, 2160, 2160), releases.take(3).map { it.height })
        assertTrue(releases.drop(3).all { it.height == 1080 })
        val remux = releases.first { "FraMeSToR" in it.title }
        assertEquals((66.68 * 1024 * 1024 * 1024).toLong(), remux.sizeBytes)
        assertEquals(2, remux.links.size)
        assertTrue(remux.links.all { it.startsWith("https://greenmotors.club/?id=") })
        assertEquals("HEVC", FourKHdHubParser.codec(remux.title))
        // within a quality, the smaller (more streamable) file comes first
        assertTrue(releases.take(3).zipWithNext().all { (a, b) -> a.sizeBytes!! <= b.sizeBytes!! })
    }

    @Test
    fun seriesReleases_onlyTheRequestedEpisode() {
        val page = html("series_page.html")
        val e2 = FourKHdHubParser.releases(page, 1, 2)
        assertTrue(e2.isNotEmpty())
        assertTrue(e2.all { FourKHdHubParser.seasonEpisode(it.title) == (1 to 2) })
        assertTrue(e2.all { it.sizeBytes != null && it.links.isNotEmpty() })
        assertTrue(FourKHdHubParser.releases(page, 5, 1).all { "S05E01" in it.title })
        assertTrue(FourKHdHubParser.releases(page, 3, 9).isEmpty())
    }

    @Test
    fun mirrorChain_greenMotorsToHubDriveToHubCloudToFiles() {
        assertEquals("https://hubdrive.bar/file/38988373616", FourKHdHubParser.greenMotorsTarget(html("greenmotors.html")))
        assertEquals("https://hubcloud.ist/drive/8yy88y6om99d928", FourKHdHubParser.hubDriveTarget(html("hubdrive.html")))
        val resolver = FourKHdHubParser.hubCloudResolver(html("hubcloud_drive.html"))!!
        assertTrue(resolver, resolver.startsWith("https://gamerxyt.com/hubcloud.php?host=hubcloud&id=8yy88y6om99d928&token="))

        val files = FourKHdHubParser.candidates(html("hubcloud_resolver.html"))
        assertEquals(2, files.size) // login/VPN/telegram/ads links dropped
        assertTrue(files[0].first.startsWith("https://gpdl.hubcloud.ist/?id="))
        assertEquals("Download [Server : 10Gbps]", files[0].second)
        val worker = files[1].first
        assertTrue(worker.contains(".workers.dev/") && worker.endsWith("(FraMeSToR-LUMiX).mkv"))
        assertTrue("spaces are percent-encoded", " " !in worker)
    }

    @Test
    fun playbackUrl_rejectsInsecureLocalAndPages() {
        assertNull(FourKHdHubParser.playbackUrl("http://cdn.example.com/a.mkv"))
        assertNull(FourKHdHubParser.playbackUrl("https://192.168.1.10/a.mkv"))
        assertNull(FourKHdHubParser.playbackUrl("https://localhost/a.mkv"))
        assertNull(FourKHdHubParser.playbackUrl("https://x.com/pack.zip"))
        assertNull(FourKHdHubParser.playbackUrl("https://hubcloud.ist/drive/admin"))
        assertEquals("https://cdn.example.com/0:/A%20B.mkv", FourKHdHubParser.playbackUrl("https://cdn.example.com/0:/A B.mkv"))
        assertTrue(FourKHdHubParser.isMedia("video/x-matroska"))
        assertTrue(!FourKHdHubParser.isMedia("text/html; charset=utf-8"))
    }
}
