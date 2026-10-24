package com.anistream.core.account

import kotlinx.serialization.json.JsonObject
import com.anistream.domain.model.mediaSlug
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMergeTest {
    private fun e(key: String, at: Long, deleted: Boolean = false) = SyncEntry(key, JsonObject(emptyMap()), 0, 0, at, deleted)

    @Test fun lastWriteWins() {
        val local = mapOf("anime:a" to 100L, "anime:b" to 100L, "anime:c" to 100L)
        val remote = listOf(
            e("anime:a", 200),                   // newer remote wins
            e("anime:b", 50),                    // older remote loses
            e("anime:c", 100),                   // tie keeps local
            e("anime:d", 10),                    // unknown locally: take it
            e("anime:a2", 5, deleted = true)     // tombstones merge the same way
        )
        val applied = HistoryMerge.toApply(remote) { local[it] }.map { it.key }
        assertEquals(listOf("anime:a", "anime:d", "anime:a2"), applied)
    }
}

class MediaSlugTest {
    @Test fun sameVectorsAsWeb() {
        assertEquals("series:mentalist", mediaSlug(true, "The Mentalist"))
        assertEquals("series:mentalist", mediaSlug(true, "Mentalist"))
        assertEquals("movie:spidermanbrandnewday", mediaSlug(false, "Spider-Man: Brand New Day"))
        assertEquals("movie:fastandfurious", mediaSlug(false, "Fast & Furious"))
    }
}

class HistoryEpsPayloadTest {
    @Test fun epsRoundTripThroughPayload() {
        val h = com.anistream.domain.model.WatchHistory("a", "A", eps = mapOf("e1" to com.anistream.domain.model.EpProgress(0.97f, 5)))
        assertEquals(h.eps, HistoryPayload.decode(HistoryPayload.encode(h), 1).eps)
    }

    @Test fun webShapedEpsDecode() {
        val p = kotlinx.serialization.json.Json.parseToJsonElement("""{"slug":"a","kind":"anime","eps":{"e2":{"p":0.4,"at":9},"bad":1}}""") as JsonObject
        assertEquals(mapOf("e2" to com.anistream.domain.model.EpProgress(0.4f, 9)), HistoryPayload.decodeEps(p))
    }
}

class HistoryDurationTest {
    private fun wh(slug: String, kind: String = "media", mediaId: String? = null, p: Float = 0f, dur: Long = 0) =
        com.anistream.domain.model.WatchHistory(slug, "T", kind = kind, mediaId = mediaId, watchProgress = p, durationMs = dur)

    @Test fun durationRoundTripsInSecondsAndMinutesLeftRoundUp() {
        val h = wh("series:x", p = 0.64f, dur = 1_500_000) // 25 min, 36 % left = 9 min
        assertEquals(1_500_000L, HistoryPayload.decode(HistoryPayload.encode(h), 1).durationMs)
        assertEquals(9, h.minutesLeft)
        assertEquals(null, wh("series:x", p = 0.5f).minutesLeft)
    }

    @Test fun detailIdIsTheCatalogIdNeverTheHistoryKey() {
        assertEquals("mb:1", wh("series:breakingbad", mediaId = "mb:1").detailId)
        assertEquals("mb:tv:1396", wh("mb:tv:1396:s5e2").detailId)
        assertEquals("frieren", wh("frieren", kind = "anime").detailId)
    }
}
