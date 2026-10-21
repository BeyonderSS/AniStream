package com.anistream.domain

import com.anistream.domain.model.AnimeSearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class KidsPolicyTest {
    @Test fun animeRatings() {
        assertEquals(true, KidsPolicy.anime("PG-13", listOf("Action", "Fantasy")))
        assertEquals(true, KidsPolicy.anime("G", listOf("Comedy")))
        assertEquals(false, KidsPolicy.anime("R - 17+ (violence & profanity)", listOf("Action")))
        assertEquals(false, KidsPolicy.anime("R+", listOf("Drama")))
        assertEquals(false, KidsPolicy.anime("Rx", emptyList()))
    }

    @Test fun animeGenresAndUnknownRating() {
        assertEquals(false, KidsPolicy.anime("PG-13", listOf("Comedy", "Ecchi")))
        assertEquals(false, KidsPolicy.anime(null, listOf("Horror")))
        assertEquals(true, KidsPolicy.anime(null, listOf("Slice of Life", "Comedy")))
        assertEquals(false, KidsPolicy.anime(null, emptyList()))
    }

    @Test fun animeCardsDropOnlyAdultBadges() {
        assertEquals(false, KidsPolicy.animeCard(AnimeSearchResult("a-1", "A", rating = "18+")))
        assertEquals(true, KidsPolicy.animeCard(AnimeSearchResult("b-2", "B")))
    }

    @Test fun movies() {
        assertEquals(true, KidsPolicy.media(listOf("Animation", "Family")))
        assertEquals(true, KidsPolicy.media(listOf("Action", "Adventure"), "PG"))
        assertEquals(false, KidsPolicy.media(listOf("Comedy", "Horror")))
        assertEquals(false, KidsPolicy.media(listOf("Crime", "Comedy")))
        assertEquals(false, KidsPolicy.media(listOf("Animation"), "TV-MA"))
        assertEquals(false, KidsPolicy.media(emptyList()))
        assertEquals(false, KidsPolicy.media(listOf("Drama")))
    }

    @Test fun channels() {
        assertEquals(true, KidsPolicy.channel("Kids"))
        assertEquals(true, KidsPolicy.channel("Animation;Family"))
        assertEquals(false, KidsPolicy.channel("News"))
        assertEquals(false, KidsPolicy.channel(null))
    }
}
