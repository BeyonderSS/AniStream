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
