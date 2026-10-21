package com.anistream.core.media.moviebox

import android.content.Context
import com.anistream.domain.model.AudioVariant
import com.anistream.domain.model.MediaDetail
import com.anistream.domain.model.MediaItem
import com.anistream.domain.model.MediaSeason
import com.anistream.domain.model.MediaStream
import com.anistream.domain.model.MediaType
import com.anistream.domain.model.SubtitleTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * MovieBox (oneroom) mobile API client. Port of MovieBox-TUI's provider (Apache-2.0), including the
 * PR #208 stream recovery: play-info now only returns an "upgrade" notice clip, so streams come from
 * resource/v2 + resource-position/v2, which hands back the signed MP4 for each resource.
 */
@Singleton
class MovieBoxClient @Inject constructor(
    @ApplicationContext context: Context
) : MovieBoxSource {

    private val prefs = context.getSharedPreferences("moviebox", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val sessionLock = Mutex()
    @Volatile private var hostIndex = 0

    // One device profile per install, like a real client; regenerated only if prefs are cleared.
    private val profile: Profile = Profile.load(prefs)

    override suspend fun search(query: String, page: Int): List<MediaItem> {
        val body = buildJsonObject {
            put("keyword", query); put("page", page); put("perPage", 15); put("subjectType", 0)
        }.toString()
        val data = call("POST", "/wefeed-mobile-bff/subject-api/search/v2", body)
        return MovieBoxParser.searchResults(data)
    }

    override suspend fun detail(subjectId: String): MediaDetail = coroutineScope {
        val detail = async { call("GET", "/wefeed-mobile-bff/subject-api/get?subjectId=$subjectId") }
        val seasons = async {
            runCatching { call("GET", "/wefeed-mobile-bff/subject-api/season-info?subjectId=$subjectId") }.getOrNull()
        }
        MovieBoxParser.detail(detail.await(), seasons.await())
    }

    override suspend fun streams(subjectId: String, season: Int, episode: Int): List<MediaStream> = coroutineScope {
        val list = call(
            "GET",
            "/wefeed-mobile-bff/subject-api/resource/v2?subjectId=$subjectId&page=1&perPage=20&all=0" +
                "&startPosition=1&endPosition=1&pagerMode=2&resolution=0&se=$season&epFrom=$episode&epTo=$episode&isVip=true"
        )
        val resources = MovieBoxParser.resources(list).filter { it.season == season && it.episode == episode }
        resources.map { res ->
            async {
                runCatching {
                    val q = listOf(
                        "subjectId" to subjectId, "resourceId" to res.resourceId, "failUrl" to res.link,
                        "failCode" to "404", "resourceNum" to "0", "isVip" to "true"
                    ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
                    val pos = call("GET", "/wefeed-mobile-bff/subject-api/resource-position/v2?$q")
                    // resource/v2's size is the notice clip's (~0.8 MB); the recovered entry carries the real one
                    val real = MovieBoxParser.recovered(pos, res) ?: return@runCatching null
                    val subs = runCatching {
                        MovieBoxParser.captions(
                            call("GET", "/wefeed-mobile-bff/subject-api/get-ext-captions?subjectId=$subjectId&resourceId=${res.resourceId}")
                        )
                    }.getOrDefault(emptyList())
                    MediaStream(
                        url = real.link,
                        qualityLabel = if (res.height > 0) "${res.height}p" else "AUTO",
                        height = res.height,
                        codec = res.codec,
                        sizeBytes = real.sizeBytes ?: res.sizeBytes,
                        // the MP4 CDN rejects a Referer with HTTP 429; User-Agent only
                        headers = mapOf("User-Agent" to profile.userAgent),
                        subtitles = subs,
                        sourceName = "MovieBox",
                        isDirectFile = true
                    )
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().sortedWith(
            // h264 before hevc at the same height: some phones can't decode HEVC
            compareByDescending<MediaStream> { it.height }.thenBy { if (it.codec.equals("h264", true)) 0 else 1 }
        )
    }

    /** Signed request with host failover and one re-login on auth failure. Returns the response's `data`. */
    private suspend fun call(method: String, pathAndQuery: String, body: String? = null): JsonElement {
        val token = token()
        return try {
            request(method, pathAndQuery, body, token)
        } catch (e: AuthException) {
            prefs.edit().remove(KEY_TOKEN).apply()
            request(method, pathAndQuery, body, token(forceFresh = true))
        }
    }

    private suspend fun token(forceFresh: Boolean = false): String = sessionLock.withLock {
        if (!forceFresh) prefs.getString(KEY_TOKEN, null)?.let { return it }
        val data = request("POST", "/wefeed-mobile-bff/user-api/visitor-login", "{}", null)
        val fresh = (data as? JsonObject)?.str("token") ?: throw IOException("MovieBox login returned no token")
        prefs.edit().putString(KEY_TOKEN, fresh).apply()
        fresh
    }

    private suspend fun request(method: String, pathAndQuery: String, body: String?, token: String?): JsonElement =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            for (attempt in HOSTS.indices) {
                val idx = (hostIndex + attempt) % HOSTS.size
                val url = HOSTS[idx] + pathAndQuery
                val ts = System.currentTimeMillis()
                val builder = Request.Builder().url(url)
                    .header("User-Agent", profile.userAgent)
                    .header("Accept", MovieBoxSigner.JSON)
                    .header("Content-Type", MovieBoxSigner.JSON)
                    .header("x-client-token", MovieBoxSigner.clientToken(ts))
                    .header("x-tr-signature", MovieBoxSigner.signature(method, url, body, ts))
                    .header("x-client-info", profile.clientInfo)
                    .header("x-client-status", "0")
                    .header("x-forwarded-for", profile.ip)
                if (token != null) builder.header("Authorization", "Bearer $token")
                // bytes, not String: String.toRequestBody appends "; charset=utf-8", which breaks the signed Content-Type
                if (method == "POST") builder.post((body ?: "{}").toByteArray().toRequestBody(MovieBoxSigner.JSON.toMediaType()))
                try {
                    http.newCall(builder.build()).execute().use { resp ->
                        resp.header("x-user")?.let(::absorbUserHeader)
                        if (resp.code == 401) throw AuthException()
                        if (resp.code in RETRY_CODES) {
                            lastError = IOException("MovieBox host returned HTTP ${resp.code}")
                            return@use null
                        }
                        if (!resp.isSuccessful) throw IOException("MovieBox returned HTTP ${resp.code}")
                        val root = JSON.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonObject
                            ?: throw IOException("MovieBox returned an unexpected response")
                        val code = (root["code"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                        if (code == 401 || code == 1001) throw AuthException()
                        if (code != 0) throw IOException("MovieBox error $code: ${root.str("message")}")
                        hostIndex = idx
                        root["data"] ?: JsonObject(emptyMap())
                    }?.let { return@withContext it }
                } catch (e: AuthException) {
                    throw e
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError ?: IOException("MovieBox unreachable")
        }

    private fun absorbUserHeader(raw: String) {
        val t = runCatching { (JSON.parseToJsonElement(raw) as? JsonObject)?.str("token") }.getOrNull()
        if (!t.isNullOrBlank()) prefs.edit().putString(KEY_TOKEN, t).apply()
    }

    private class AuthException : IOException("MovieBox session expired")

    /** Persisted per-install client identity sent with every request (mirrors MovieBox-TUI). */
    private class Profile(val userAgent: String, val clientInfo: String, val ip: String) {
        companion object {
            fun load(prefs: android.content.SharedPreferences): Profile {
                prefs.getString("ua", null)?.let { ua ->
                    return Profile(ua, prefs.getString("ci", "")!!, prefs.getString("ip", "")!!)
                }
                val (android, build) = listOf(
                    "10" to "QP1A.191005.007.A3", "11" to "RP1A.200720.011", "12" to "S1B.220414.015", "13" to "TQ2A.230405.003"
                ).random()
                val model = listOf("23078RKD5C", "2201117TY", "2201117TG", "22101316G", "M2012K11AG").random()
                val versionCode = listOf(50020117, 50020118, 50020119, 50020120, 50020121).random()
                val ua = "com.community.oneroom/$versionCode (Linux; U; Android $android; en_US; $model; Build/$build; Cronet/135.0.7012.3)"
                val ci = buildJsonObject {
                    put("package_name", "com.community.oneroom"); put("version_name", "4.0.01.0813.03")
                    put("version_code", versionCode); put("os", "android"); put("os_version", android)
                    put("install_ch", "ps"); put("device_id", (1..32).joinToString("") { "%x".format(Random.nextInt(16)) })
                    put("install_store", "ps"); put("gaid", UUID.randomUUID().toString()); put("brand", "Redmi")
                    put("model", model); put("system_language", "en"); put("net", "NETWORK_WIFI"); put("region", "US")
                    put("timezone", "Asia/Kolkata"); put("sp_code", "40401"); put("X-Play-Mode", "2")
                }.toString()
                val prefix = listOf("103.241", "49.36", "117.195", "106.198", "122.162", "157.32", "182.70", "103.58", "27.60", "59.90").random()
                val ip = "$prefix.${Random.nextInt(1, 254)}.${Random.nextInt(1, 254)}"
                prefs.edit().putString("ua", ua).putString("ci", ci).putString("ip", ip).apply()
                return Profile(ua, ci, ip)
            }
        }
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private val JSON = Json { ignoreUnknownKeys = true }
        private val RETRY_CODES = setOf(403, 406, 407, 429, 500, 502, 503, 504)
        private val HOSTS = listOf(
            "https://api6.aoneroom.com", "https://api5.aoneroom.com", "https://api4.aoneroom.com",
            "https://api4sg.aoneroom.com", "https://api3.aoneroom.com", "https://api6sg.aoneroom.com",
            "https://api.inmoviebox.com"
        )
    }
}

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content?.takeIf { it.isNotBlank() }

/** Pure JSON → domain mapping, unit-tested against real responses. */
object MovieBoxParser {
    data class Resource(
        val resourceId: String, val link: String, val season: Int, val episode: Int,
        val height: Int, val codec: String?, val sizeBytes: Long?
    )

    fun isNotice(url: String) = url.contains("macdn.aoneroom.com") && url.contains("/other/")

    fun searchResults(data: JsonElement): List<MediaItem> {
        val results = ((data as? JsonObject)?.get("results") as? JsonArray).orEmpty()
        return results.flatMap { ((it as? JsonObject)?.get("subjects") as? JsonArray).orEmpty() }
            .mapNotNull { (it as? JsonObject)?.let(::item) }
            .distinctBy { it.id }
    }

    private fun item(o: JsonObject): MediaItem? {
        val id = o.str("subjectId") ?: return null
        return MediaItem(
            id = "mb:$id",
            type = if (o.str("subjectType") == "2") MediaType.SERIES else MediaType.MOVIE,
            title = o.str("title") ?: return null,
            posterUrl = (o["cover"] as? JsonObject)?.str("url"),
            year = o.str("releaseDate")?.take(4)?.toIntOrNull(),
            rating = o.str("imdbRatingValue")?.toDoubleOrNull()?.takeIf { it > 0 },
            genres = o.str("genre")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        )
    }

    fun detail(data: JsonElement, seasonInfo: JsonElement?): MediaDetail {
        val o = data as? JsonObject ?: throw IOException("MovieBox detail missing")
        val base = item(o) ?: throw IOException("MovieBox detail missing id/title")
        val seasons = ((seasonInfo as? JsonObject)?.get("seasons") as? JsonArray).orEmpty().mapNotNull { s ->
            val so = s as? JsonObject ?: return@mapNotNull null
            val n = so.str("se")?.toIntOrNull() ?: return@mapNotNull null
            MediaSeason(n, so.str("maxEp")?.toIntOrNull() ?: 0).takeIf { n > 0 && it.episodeCount > 0 }
        }
        val dubs = (o["dubs"] as? JsonArray).orEmpty().mapNotNull { d ->
            val dobj = d as? JsonObject ?: return@mapNotNull null
            val sid = dobj.str("subjectId") ?: return@mapNotNull null
            AudioVariant("mb:$sid", dobj.str("lanName") ?: "Audio", dobj.str("lanCode").orEmpty(), "mb:$sid" == base.id)
        }
        val cast = (o["staffList"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") }.take(12)
        return MediaDetail(
            id = base.id,
            type = base.type,
            title = base.title,
            description = o.str("description")?.replace("&apos;", "'")?.replace("&quot;", "\"")?.replace("&amp;", "&"),
            posterUrl = base.posterUrl,
            backdropUrl = (o["stills"] as? JsonObject)?.str("url") ?: base.posterUrl,
            year = base.year,
            rating = base.rating,
            runtime = o.str("duration"),
            country = o.str("countryName"),
            genres = base.genres,
            cast = cast,
            seasons = seasons,
            audioVariants = dubs,
            playable = o.str("hasResource") != "false"
        )
    }

    fun resources(data: JsonElement): List<Resource> =
        ((data as? JsonObject)?.get("list") as? JsonArray).orEmpty().mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            Resource(
                resourceId = o.str("resourceId") ?: return@mapNotNull null,
                link = o.str("resourceLink").orEmpty(),
                season = o.str("se")?.toIntOrNull() ?: 0,
                episode = o.str("ep")?.toIntOrNull() ?: 0,
                height = o.str("resolution")?.toIntOrNull() ?: 0,
                codec = o.str("codecName"),
                sizeBytes = o.str("size")?.toLongOrNull()
            )
        }

    /** The signed MP4 for exactly this resource/season/episode, never the notice clip. Its `size` is the real file's. */
    fun recovered(data: JsonElement, original: Resource): Resource? =
        resources(data).firstOrNull {
            it.resourceId == original.resourceId && it.season == original.season && it.episode == original.episode &&
                it.link.startsWith("http") && !isNotice(it.link)
        }

    fun captions(data: JsonElement): List<SubtitleTrack> =
        ((data as? JsonObject)?.get("extCaptions") as? JsonArray).orEmpty().mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            val url = o.str("url") ?: return@mapNotNull null
            SubtitleTrack(
                url = url,
                languageCode = o.str("lan").orEmpty(),
                label = o.str("lanName") ?: o.str("lan").orEmpty(),
                mimeType = if (url.substringBefore('?').endsWith(".vtt")) "text/vtt" else "application/x-subrip"
            )
        }.sortedBy { if (it.languageCode.startsWith("en")) 0 else 1 }
}
