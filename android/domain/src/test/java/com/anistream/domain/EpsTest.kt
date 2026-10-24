package com.anistream.domain

import com.anistream.domain.model.EpProgress
import com.anistream.domain.model.Eps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpsTest {
    @Test fun keysMatchWeb() {
        assertEquals("e12", Eps.key("anime", 0, 12))
        assertEquals("s5e1", Eps.key("media", 5, 1))
        assertNull(Eps.key("media", 0, 0)) // movie
    }

    @Test fun mergeKeepsNewestPerEpisode() {
        val a = mapOf("e1" to EpProgress(0.97f, 200), "e2" to EpProgress(0.3f, 100))
        val b = mapOf("e1" to EpProgress(0.5f, 100), "e3" to EpProgress(0.1f, 50))
        val m = Eps.merge(a, b)
        assertEquals(0.97f, m["e1"]!!.p, 0f) // newer at wins even when its row is older
        assertEquals(0.3f, m["e2"]!!.p, 0f)
        assertEquals(0.1f, m["e3"]!!.p, 0f)
        assertEquals(m, Eps.merge(b, a))
    }

    @Test fun roomTextRoundTrips() {
        val m = mapOf("s1e2" to EpProgress(0.5f, 7), "e9" to EpProgress(1f, 8))
        assertEquals(m, Eps.parse(Eps.format(m)))
        assertEquals(emptyMap<String, EpProgress>(), Eps.parse(""))
    }

    @Test fun permilleConversion() {
        assertEquals(970L, Eps.toPermille(0.97f))
        assertEquals(1000L, Eps.toPermille(1.4f))
        assertEquals(0.5f, Eps.fromPermille(500, 1000), 0f)
        assertEquals(0.25f, Eps.fromPermille(30, 120), 0f) // web-style seconds
        assertEquals(0f, Eps.fromPermille(5, 0), 0f)
    }
}
