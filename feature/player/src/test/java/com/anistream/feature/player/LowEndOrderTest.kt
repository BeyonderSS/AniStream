package com.anistream.feature.player

import com.anistream.domain.model.MediaStream
import org.junit.Assert.assertEquals
import org.junit.Test

class LowEndOrderTest {
    private fun s(label: String, height: Int, codec: String? = null) = MediaStream(url = label, qualityLabel = label, height = height, codec = codec, sourceName = "x")

    @Test
    fun fireTvStick2Order() {
        val uhd = s("2160p", 2160, "HEVC")
        val hdr = s("1080p HDR 10bit", 1080, "x265")
        val hevc = s("1080p", 1080, "HEVC")
        val avc = s("720p", 720, "h264")
        // hw HEVC Main only, decoders up to 1088 lines
        assertEquals(listOf(hevc, avc, uhd, hdr), lowEndOrder(listOf(uhd, hdr, hevc, avc), hardwareHevc = true, main10 = false, maxHeight = 1088))
        // no hw HEVC at all: H.264 first, HEVC kept as last resort
        assertEquals(listOf(avc, hdr, hevc, uhd), lowEndOrder(listOf(uhd, hdr, hevc, avc), hardwareHevc = false, main10 = false, maxHeight = 1088))
    }
}
