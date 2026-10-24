package com.anistream.feature.admin

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AdminDataTest {
    @Test fun formats() {
        assertEquals("just now", ago(1_000.0, now = 10_000))
        assertEquals("3 minutes ago", ago(1_000.0, now = 181_000))
        assertEquals("2 days ago", ago(1_000.0, now = 2 * 86_400_000L + 1_000))
        assertEquals("never", ago(0.0))
        assertEquals("45 min", hours(45.0))
        assertEquals("2.0 h", hours(120.0))
        assertEquals("93%", pct(0.934))
        assertEquals("1:05", clock(65.0))
    }

    @Test fun readers() {
        val j = Json.parseToJsonElement("""{"a":{"n":3,"s":"x","t":1,"z":null},"l":[1,2]}""")
        assertEquals(3, j.o("a").i("n")); assertEquals("x", j.o("a").s("s")); assertEquals(null, j.o("a").s("z"))
        assertEquals(true, j.o("a").b("t")); assertEquals(2, j.list("l").size); assertEquals(0, j.o("q").i("n"))
    }
}
