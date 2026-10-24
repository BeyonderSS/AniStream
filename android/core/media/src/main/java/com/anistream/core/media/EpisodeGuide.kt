package com.anistream.core.media

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Episode art and names for the TV detail strip. [runtime] minutes. */
data class GuideEp(val n: Int, val title: String?, val overview: String?, val still: String?, val runtime: Int?)

/**
 * TMDB (series seasons) and Kitsu (anime) episode metadata, cached in memory. Every failure is an empty list
 * (not cached), so the screen falls back to its plain numbered tiles.
 * ponytail: memory cache only, no disk; a cold start refetches one small JSON per season.
 */
@Singleton
class EpisodeGuide @Inject constructor(private val http: MediaHttp) {
    private val cache = HashMap<String, List<GuideEp>>()
    private val tmdbIds = HashMap<String, Int?>()
    private val key = BuildConfig.TMDB_API_KEY

    /** [mediaId] "tt…" resolves by IMDb id; anything else by [title] + [year]. */
    suspend fun season(mediaId: String, title: String, year: Int?, season: Int): List<GuideEp> {
        val k = "$mediaId:$season"
        synchronized(cache) { cache[k] }?.let { return it }
        if (key.isBlank()) return emptyList()
        val list = runCatching {
            val id = synchronized(tmdbIds) { if (tmdbIds.containsKey(mediaId)) tmdbIds[mediaId] else null } ?: tmdbId(mediaId, title, year)
            synchronized(tmdbIds) { tmdbIds[mediaId] = id }
            if (id == null) return@runCatching emptyList()
            (tmdb("/tv/$id/season/$season?")["episodes"] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                GuideEp(
                    n = o.int("episode_number") ?: return@mapNotNull null, title = o.str("name"), overview = o.str("overview"),
                    still = o.str("still_path")?.let { "https://image.tmdb.org/t/p/w300$it" }, runtime = o.int("runtime")
                )
            }
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) synchronized(cache) { cache[k] = list }
        return list
    }

    private suspend fun tmdbId(mediaId: String, title: String, year: Int?): Int? =
        if (mediaId.startsWith("tt")) {
            ((tmdb("/find/$mediaId?external_source=imdb_id")["tv_results"] as? JsonArray)?.firstOrNull() as? JsonObject)?.int("id")
        } else {
            // "NCIS [Hindi]" -> "NCIS": dub tags and parentheses are not part of the TMDB title
            val q = enc(title.replace(Regex("\\s*[\\[(].*?[\\])]"), "").trim())
            suspend fun first(y: String) = ((tmdb("/search/tv?query=$q$y")["results"] as? JsonArray)?.firstOrNull() as? JsonObject)?.int("id")
            first(year?.let { "&first_air_date_year=$it" }.orEmpty()) ?: first("")
        }

    /** Kitsu episode names/thumbnails, same lookup as web `animeEpisodes`; [count] bounds the pages fetched (20 each, max 10). */
    suspend fun anime(title: String, japanese: String?, year: Int?, count: Int): List<GuideEp> {
        val k = "kitsu:$title:$year"
        synchronized(cache) { cache[k] }?.let { return it }
        val list = runCatching {
            var hit: JsonObject? = null
            for (q in listOfNotNull(title, japanese?.takeIf { it.isNotBlank() })) {
                val res = (http.getJson("https://kitsu.io/api/edge/anime?filter%5Btext%5D=${enc(q)}&page%5Blimit%5D=5&fields%5Banime%5D=startDate,canonicalTitle")["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                hit = res.firstOrNull { year != null && (it["attributes"] as? JsonObject)?.str("startDate")?.startsWith(year.toString()) == true } ?: res.firstOrNull()
                if (hit != null) break
            }
            val id = hit?.str("id") ?: return@runCatching emptyList()
            (0 until ((count + 19) / 20).coerceIn(1, 10)).flatMap { i ->
                runCatching {
                    (http.getJson("https://kitsu.io/api/edge/anime/$id/episodes?page%5Blimit%5D=20&page%5Boffset%5D=${i * 20}&sort=number")["data"] as? JsonArray).orEmpty().mapNotNull { e ->
                        val a = (e as? JsonObject)?.get("attributes") as? JsonObject ?: return@mapNotNull null
                        GuideEp(
                            n = a.int("number") ?: return@mapNotNull null, title = a.str("canonicalTitle"), overview = a.str("synopsis"),
                            still = ((a["thumbnail"] as? JsonObject)?.str("original")), runtime = a.int("length")
                        )
                    }
                }.getOrDefault(emptyList())
            }
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) synchronized(cache) { cache[k] = list }
        return list
    }

    private suspend fun tmdb(pathAndQuery: String): JsonObject {
        val sep = if (pathAndQuery.endsWith("?")) "" else "&"
        val url = "https://api.themoviedb.org/3$pathAndQuery${sep}api_key=$key"
        // TMDB drops the odd connection; one retry (as Recommendations does)
        return try { http.getJson(url) } catch (e: java.io.IOException) { kotlinx.coroutines.delay(1500); http.getJson(url) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.intOrNull
}
