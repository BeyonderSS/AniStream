package com.anistream.core.media.dramachi

import com.anistream.core.media.MediaHttp
import com.anistream.core.media.StreamSource
import com.anistream.core.media.Stremio
import com.anistream.domain.model.MediaItem
import com.anistream.domain.model.MediaStream
import com.anistream.domain.model.MediaType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Dramachi (Asian dramas/movies) JSON API at api.nodeobjects.com. Ported from MovieBox-TUI (Apache-2.0). */
@Singleton
class DramachiSource @Inject constructor(private val http: MediaHttp) : StreamSource {

    override suspend fun streams(title: String, year: Int?, type: MediaType, season: Int, episode: Int): List<MediaStream> {
        val hits = DramachiParser.search(http.getJson("$BASE?interface=search&q=${enc(Stremio.searchQuery(title))}&filter=all&page=1"))
        val years = if (type == MediaType.MOVIE) year?.let { it..it } else null
        val match = Stremio.pickMatch(title, type, years, hits) ?: return emptyList()
        val rip = DramachiParser.rip(http.getJson("$BASE?interface=title_v2&id=${enc(match.id)}"), season) ?: return emptyList()
        val eps = DramachiParser.episodes(
            http.getJson("$BASE?interface=eplist&season=${enc(rip)}&id=${enc(match.id)}"), season, episode
        )
        return coroutineScope {
            eps.mapIndexed { i, ep ->
                async {
                    val url = runCatching {
                        DramachiParser.fileUrl(http.getJson("$BASE?interface=getFile&fid=${enc(ep.fid)}&findex=${enc(ep.disk)}"))
                    }.getOrNull() ?: return@async null
                    val q = if (ep.height > 0) "${ep.height}p" else "AUTO"
                    MediaStream(
                        url = url,
                        qualityLabel = if (eps.size > 1 && season == 0) "$q Part ${i + 1}" else q,
                        height = ep.height,
                        sizeBytes = ep.sizeBytes,
                        sourceName = "Dramachi"
                    )
                }
            }.awaitAll()
        }.filterNotNull().sortedByDescending { it.height }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private companion object {
        const val BASE = "https://api.nodeobjects.com/"
    }
}

internal object DramachiParser {
    data class Episode(val number: Int?, val fid: String, val disk: String, val height: Int, val sizeBytes: Long?)

    private val TRAILING_YEAR = Regex("""\s+(19|20)\d{2}$""")

    /** Search hits as MediaItems (id = Dramachi title id). Movie titles carry their year ("Parasite 2019"); it's stripped. */
    fun search(root: JsonObject): List<MediaItem> = (root["data"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val raw = o.str("title") ?: return@mapNotNull null
        val year = o.str("year")?.toIntOrNull() ?: TRAILING_YEAR.find(raw)?.value?.trim()?.toInt()
        MediaItem(
            id = o.str("id") ?: return@mapNotNull null,
            type = if (o.str("content")?.lowercase() in setOf("movie", "movies")) MediaType.MOVIE else MediaType.SERIES,
            title = raw.replace(TRAILING_YEAR, ""),
            year = year,
            posterUrl = o.str("thumb")?.let { "https://static.nodeobjects.com/thumbnail/$it" }
        )
    }

    /** Episode-list key: "Season NN"'s first (original-audio) version; movies use their only group ("hd Rip"). */
    fun rip(title: JsonObject, season: Int): String? {
        val seasons = title["seasons"] as? JsonObject ?: return null
        val group = (seasons["Season %02d".format(season)] ?: seasons.values.firstOrNull()?.takeIf { season <= 1 })
            as? JsonObject ?: return null
        return ((group["versions"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("rip")
    }

    /** The requested episode (or every part of a movie, in order). Falls back to list position when titles carry no number. */
    fun episodes(eplist: JsonObject, season: Int, episode: Int): List<Episode> {
        val all = (eplist["episode_list"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val fid = o.str("fid") ?: return@mapNotNull null
            val disk = o.str("disk") ?: return@mapNotNull null
            Episode(
                number = episodeNumber(o.str("f_title").orEmpty()),
                fid = fid,
                disk = disk,
                height = o.str("quality")?.let { Regex("(\\d{3,4})p").find(it)?.groupValues?.get(1)?.toInt() } ?: 0,
                sizeBytes = o.str("size")?.let(::sizeBytes)
            )
        }.sortedBy { it.number ?: Int.MAX_VALUE }
        if (season == 0 && episode == 0) return all
        return all.filter { it.number == episode }.ifEmpty { listOfNotNull(all.getOrNull(episode - 1)) }
    }

    /** "Queen of Tears S01E16" → 16, "Show EP 3" → 3, "Parasite 2019 002" → 2. */
    fun episodeNumber(fTitle: String): Int? {
        val t = fTitle.trim().replace(Regex("""(?i)\s+(\d{3,4}p|hi dub|dub|eng subbed full)$"""), "")
        Regex("""(?i)(?:^|[^a-z])(?:episode|ep\.?|e)[\s._-]*(\d+)""").find(t)?.let { return it.groupValues[1].toInt() }
        return t.split(Regex("\\s+")).lastOrNull()?.toIntOrNull()
    }

    /** getFile → https://<hostInfo.host>/cdn/<fileInfo[0].url> */
    fun fileUrl(root: JsonObject): String? {
        val host = (root["hostInfo"] as? JsonObject)?.str("host")?.trimEnd('/') ?: return null
        val path = ((root["fileInfo"] as? JsonArray)?.firstOrNull() as? JsonObject)?.str("url")?.trimStart('/') ?: return null
        return "https://$host/cdn/$path".toHttpUrlOrNull()?.toString()
    }

    /** "218.32 MB" → bytes (binary units, as the TUI). */
    fun sizeBytes(s: String): Long? {
        val m = Regex("""(?i)(\d+(?:\.\d+)?)\s*(KB|MB|GB)""").find(s) ?: return null
        val mult = when (m.groupValues[2].uppercase()) { "GB" -> 1L shl 30; "MB" -> 1L shl 20; else -> 1L shl 10 }
        return (m.groupValues[1].toDouble() * mult).toLong()
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
}
