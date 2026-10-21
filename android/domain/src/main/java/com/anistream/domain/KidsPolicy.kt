package com.anistream.domain

import com.anistream.core.common.Result
import com.anistream.domain.model.AnimeSearchResult

/** A blocked title; surfaces as an ordinary failed Result so every screen shows the message. */
class KidsBlockedException : Exception(KidsPolicy.BLOCKED)

/**
 * What a kids profile may see. [active] is set by the account layer when the active profile has `kids` on;
 * repositories and view models call the rules below, never the Composables.
 *
 * Missing rating data: movies lean safe (no genres and no certification = blocked, and at least one family genre is
 * required); anime is allowed when the rating is G/PG/PG-13, or when the rating is unknown and the genres are known
 * and none is blocked. Hianime list cards carry no genres and only sometimes a rating (a "18+" badge), so a card is
 * dropped only when badged; a title that slips through is stopped by [anime] when it is opened.
 */
object KidsPolicy {
    const val BLOCKED = "Not available on this profile"

    @Volatile var active = false

    private val ANIME_GENRES = setOf("hentai", "ecchi", "horror", "gore")
    private val MEDIA_GENRES = setOf("horror", "thriller", "crime", "war", "adult", "erotic")
    private val FAMILY_GENRES = setOf("animation", "family", "kids", "comedy", "adventure")
    private val CHANNEL_GROUPS = listOf("kids", "animation", "family", "education", "cartoon", "children")
    private val ADULT_CERTS = setOf("R", "NC-17", "NC17", "X", "TV-MA", "18", "18+", "R18", "R+", "RX")

    private fun norm(genres: List<String>) = genres.map { it.trim().lowercase() }

    /** MAL-style ("R - 17+ (violence & profanity)", "Rx - Hentai") or hianime badge ("18+"). */
    fun animeRatingBlocked(rating: String?): Boolean {
        val r = rating?.trim()?.uppercase().orEmpty()
        return r.startsWith("R") || r.startsWith("18") || r.startsWith("NC")
    }

    fun animeGenreBlocked(genre: String) = genre.trim().lowercase() in ANIME_GENRES

    /** List card: only a visible adult badge is known. */
    fun animeCard(item: AnimeSearchResult) = !animeRatingBlocked(item.rating)

    /** Full check, used on detail pages. */
    fun anime(rating: String?, genres: List<String>): Boolean {
        if (animeRatingBlocked(rating) || genres.any(::animeGenreBlocked)) return false
        return !rating.isNullOrBlank() || genres.isNotEmpty()
    }

    fun media(genres: List<String>, certification: String? = null): Boolean {
        if (certification?.trim()?.uppercase()?.let { it in ADULT_CERTS } == true) return false
        val g = norm(genres)
        return g.none { it in MEDIA_GENRES } && g.any { it in FAMILY_GENRES }
    }

    fun channel(group: String?): Boolean {
        val g = group?.lowercase() ?: return false
        return CHANNEL_GROUPS.any { it in g }
    }

    /**
     * Paged load with the kids filter applied per page. A page that filters down to nothing would otherwise read as
     * "end of list", so it keeps pulling pages (up to 5). Returns the kept items and the last page actually read.
     */
    suspend fun <T> loadKept(first: Int, keep: (T) -> Boolean, load: suspend (Int) -> Result<List<T>>): Pair<Result<List<T>>, Int> {
        var page = first
        while (true) {
            val r = load(page)
            if (!active || r !is Result.Success) return r to page
            val kept = r.data.filter(keep)
            if (kept.isNotEmpty() || r.data.isEmpty() || page >= first + 4) return Result.success(kept) to page
            page++
        }
    }
}
