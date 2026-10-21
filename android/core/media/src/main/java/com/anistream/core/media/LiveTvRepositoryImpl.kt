package com.anistream.core.media

import com.anistream.core.common.Result
import com.anistream.core.common.safeApiCall
import com.anistream.core.database.dao.PlaylistDao
import com.anistream.core.database.entity.PlaylistEntity
import com.anistream.domain.KidsPolicy
import com.anistream.domain.model.Channel
import com.anistream.domain.model.Playlist
import com.anistream.domain.repository.LiveTvRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTvRepositoryImpl @Inject constructor(
    private val http: MediaHttp,
    private val dao: PlaylistDao
) : LiveTvRepository {

    @Volatile private var cache: List<Channel>? = null

    override fun playlists(): Flow<List<Playlist>> =
        dao.observeAll().map { list -> list.map { Playlist(it.id, it.name, it.url) } }

    override suspend fun addPlaylist(name: String, url: String): Result<Playlist> = safeApiCall {
        val u = url.trim()
        require(u.startsWith("http://") || u.startsWith("https://")) { "Playlist URL must be http(s)" }
        val n = name.trim().ifEmpty { u.substringAfterLast('/').substringBefore('?') }
        val id = dao.insert(PlaylistEntity(name = n, url = u))
        cache = null
        Playlist(id, n, u)
    }

    override suspend fun removePlaylist(id: Long): Result<Unit> = safeApiCall {
        dao.delete(id)
        cache = null
    }

    override suspend fun channels(refresh: Boolean): Result<List<Channel>> =
        allChannels(refresh).map { list -> if (KidsPolicy.active) list.filter { KidsPolicy.channel(it.group) } else list }

    private suspend fun allChannels(refresh: Boolean): Result<List<Channel>> = safeApiCall {
        cache?.takeIf { !refresh }?.let { return@safeApiCall it }
        val playlists = dao.getAll()
        val results = coroutineScope {
            playlists.map { p ->
                async {
                    runCatching {
                        require(p.url.startsWith("http://") || p.url.startsWith("https://")) { "Playlist URL must be http(s)" }
                        M3u.parse(http.get(p.url, maxBytes = MAX_PLAYLIST_BYTES))
                    }
                }
            }.awaitAll()
        }
        if (results.isNotEmpty() && results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        results.flatMap { it.getOrNull().orEmpty() }.distinctBy { it.url }.also { cache = it }
    }

    private companion object {
        const val MAX_PLAYLIST_BYTES = 15L * 1024 * 1024
    }
}

/** Extended-M3U parser: #EXTINF attributes (tvg-id, tvg-logo, group-title), name after the last comma, #EXTVLCOPT headers. */
internal object M3u {
    private val ATTR = Regex("([\\w-]+)=\"([^\"]*)\"")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var attrs: Map<String, String> = emptyMap()
        var name: String? = null
        val headers = HashMap<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.isEmpty() -> continue
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    attrs = ATTR.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = line.substringAfterLast(',', "").trim()
                    headers.clear()
                    // iptv-org also puts headers on the #EXTINF line itself
                    attrs["http-user-agent"]?.ifBlank { null }?.let { headers["User-Agent"] = it }
                    (attrs["http-referrer"] ?: attrs["http-referer"])?.ifBlank { null }?.let { headers["Referer"] = it }
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val value = opt.substringAfter('=').trim()
                    when (opt.substringBefore('=').trim().lowercase()) {
                        "http-user-agent" -> headers["User-Agent"] = value
                        "http-referrer", "http-referer" -> headers["Referer"] = value
                    }
                }
                line.startsWith("#") -> continue
                else -> {
                    if (line.startsWith("http://") || line.startsWith("https://")) {
                        out += Channel(
                            id = line,
                            name = name?.ifEmpty { null } ?: attrs["tvg-name"] ?: line,
                            url = line,
                            logoUrl = attrs["tvg-logo"]?.ifBlank { null },
                            // "News;Public" → "News" so the UI groups cleanly
                            group = attrs["group-title"]?.substringBefore(';')?.trim()?.ifBlank { null },
                            headers = headers.toMap()
                        )
                    }
                    attrs = emptyMap(); name = null; headers.clear()
                }
            }
        }
        return out.distinctBy { it.url }
    }
}
