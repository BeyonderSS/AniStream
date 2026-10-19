package com.anistream.core.network.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistUrisTest {
    @Test
    fun resolvesAbsoluteAndRelativeSegments() {
        val playlist = """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            https://cdn.example/a/seg-1.ts.jpg
            #EXTINF:10.0,
            seg-2.ts.jpg

            #EXT-X-ENDLIST
        """.trimIndent()
        assertEquals(
            listOf("https://cdn.example/a/seg-1.ts.jpg", "https://cdn.example/hls/seg-2.ts.jpg"),
            playlistUris(playlist, "https://cdn.example/hls/index-720.m3u8")
        )
    }

    @Test
    fun detectsMaster() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=1280x720\n720/index.m3u8\n"
        assertTrue(isMasterPlaylist(master))
        assertEquals(listOf("https://x.y/m/720/index.m3u8"), playlistUris(master, "https://x.y/m/master.m3u8"))
    }
}
