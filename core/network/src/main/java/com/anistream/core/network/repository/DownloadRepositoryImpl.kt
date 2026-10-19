package com.anistream.core.network.repository

import android.content.Context
import android.os.Environment
import com.anistream.core.common.Result
import com.anistream.core.database.dao.DownloadDao
import com.anistream.core.database.entity.DownloadEntity
import com.anistream.domain.model.DownloadItem
import com.anistream.domain.model.DownloadState
import com.anistream.domain.model.MediaStream
import com.anistream.domain.model.StreamSource
import com.anistream.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/** Media URIs of an HLS media playlist, resolved against [baseUrl]. Master playlists yield their variant URIs. */
internal fun playlistUris(playlist: String, baseUrl: String): List<String> {
    val base = URI(baseUrl)
    return playlist.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { base.resolve(it).toString() }
        .toList()
}

internal fun isMasterPlaylist(playlist: String) = playlist.contains("#EXT-X-STREAM-INF")

/** Stable key for (anime, episode); hianime episode ids aren't known to the downloads screen. */
fun downloadId(animeSlug: String, episodeNumber: Int): Long =
    (animeSlug.hashCode().toLong() shl 32) or (episodeNumber.toLong() and 0xFFFFFFFFL)

/** "/x/a.ts" -> "/x/a": subtitle sidecars sit next to the video with a .vtt/.srt extension. */
internal fun sidecarBase(videoPath: String) = videoPath.substringBeforeLast('.')

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val dao: DownloadDao,
    @ApplicationContext private val context: Context
) : DownloadRepository {

    // ponytail: app-scoped coroutines die with the process; move to WorkManager / a foreground service if long downloads get killed
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    init {
        scope.launch { dao.failInterrupted() }
    }

    private val dir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, "downloads")
            .apply { mkdirs() }

    override suspend fun getAll(): Result<List<DownloadItem>> {
        return try {
            Result.success(dao.getAll().first().map { it.toDomain() })
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun startDownload(
        stream: StreamSource,
        animeSlug: String,
        title: String,
        episodeNumber: Int
    ): Result<Unit> {
        return try {
            val id = downloadId(animeSlug, episodeNumber)
            if (jobs[id]?.isActive == true) return Result.success(Unit)
            val video = File(dir, "${animeSlug.replace('/', '_')}-ep$episodeNumber.ts")
            val subs = File(video.path.removeSuffix(".ts") + ".vtt")
            val entity = DownloadEntity(
                episodeId = id,
                animeTitle = title,
                episodeNumber = episodeNumber,
                quality = stream.qualityLabel,
                filePath = video.path,
                state = DownloadState.PENDING.name
            )
            dao.upsert(entity)
            jobs[id] = scope.launch {
                try {
                    download(stream, entity, video, subs)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    video.delete()
                    dao.updateProgress(id, 0f, DownloadState.FAILED.name)
                } finally {
                    jobs.remove(id)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    private suspend fun download(stream: StreamSource, entity: DownloadEntity, video: File, subs: File) {
        val referer = stream.referer
        var playlistUrl = stream.m3u8Url
        var playlist = fetchText(playlistUrl, referer)
        if (isMasterPlaylist(playlist)) {
            playlistUrl = playlistUris(playlist, playlistUrl).first()
            playlist = fetchText(playlistUrl, referer)
        }
        val segments = playlistUris(playlist, playlistUrl)
        if (segments.isEmpty()) throw IOException("Empty playlist")

        dao.updateProgress(entity.episodeId, 0f, DownloadState.DOWNLOADING.name)
        var lastWrite = 0L
        video.outputStream().buffered().use { out ->
            segments.forEachIndexed { i, url ->
                kotlin.coroutines.coroutineContext.ensureActive()
                client.newCall(request(url, referer)).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Segment ${i + 1}/${segments.size}: HTTP ${resp.code}")
                    resp.body?.byteStream()?.copyTo(out) ?: throw IOException("Empty segment")
                }
                val now = System.currentTimeMillis()
                if (now - lastWrite > 1000) {
                    lastWrite = now
                    dao.updateProgress(entity.episodeId, (i + 1f) / segments.size, DownloadState.DOWNLOADING.name)
                }
            }
        }

        stream.subtitleUrl?.let { url ->
            // subtitles are optional; a failure here shouldn't sink the video
            runCatching { subs.writeText(fetchText(url, referer)) }
        }

        dao.upsert(
            entity.copy(
                progress = 1f,
                state = DownloadState.COMPLETED.name,
                fileSizeBytes = video.length()
            )
        )
    }

    override suspend fun startMediaDownload(stream: MediaStream, key: String, title: String, subtitle: String?): Result<Unit> {
        return try {
            if (!stream.isDirectFile) return Result.error(IOException("Only direct files can be downloaded"))
            val id = downloadId(key, 0)
            if (jobs[id]?.isActive == true) return Result.success(Unit)
            val name = listOfNotNull(title, subtitle).joinToString(" ").replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').take(120)
            val video = File(dir, "${name.ifEmpty { "video" }}.mp4")
            val entity = DownloadEntity(
                episodeId = id,
                animeTitle = title,
                episodeNumber = 0,
                quality = stream.qualityLabel,
                filePath = video.path,
                state = DownloadState.PENDING.name,
                subtitle = subtitle
            )
            dao.upsert(entity)
            jobs[id] = scope.launch {
                try {
                    downloadFile(stream, entity, video)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // keep the partial file: the next attempt resumes it with a Range request
                    dao.updateProgress(id, 0f, DownloadState.FAILED.name)
                } finally {
                    jobs.remove(id)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    private suspend fun downloadFile(stream: MediaStream, entity: DownloadEntity, video: File) {
        val existing = if (video.exists()) video.length() else 0L
        val req = Request.Builder().url(stream.url)
            .header("User-Agent", USER_AGENT)
            .apply {
                stream.headers.forEach { (k, v) -> header(k, v) }
                if (existing > 0) header("Range", "bytes=$existing-")
            }
            .build()
        dao.updateProgress(entity.episodeId, 0f, DownloadState.DOWNLOADING.name)
        client.newCall(req).execute().use { resp ->
            if (resp.code == 416 && existing > 0) return@use // partial file is already whole
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("Empty body")
            val resumed = resp.code == 206
            val start = if (resumed) existing else 0L
            val total = body.contentLength().takeIf { it > 0 }?.plus(start) ?: stream.sizeBytes ?: -1L
            var done = start
            var lastWrite = 0L
            java.io.FileOutputStream(video, resumed).buffered().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (total > 0 && now - lastWrite > 1000) {
                            lastWrite = now
                            dao.updateProgress(entity.episodeId, done.toFloat() / total, DownloadState.DOWNLOADING.name)
                        }
                    }
                }
            }
            if (total > 0 && done < total) throw IOException("Connection closed at $done/$total bytes")
        }

        stream.subtitles.firstOrNull()?.let { track ->
            val ext = if (track.mimeType.contains("vtt")) "vtt" else "srt"
            // subtitles are optional; a failure here shouldn't sink the video
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(track.url).header("User-Agent", USER_AGENT).build()).execute().use {
                        if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
                        it.body?.string() ?: ""
                    }
                }
                File(sidecarBase(video.path) + ".$ext").writeText(text)
            }
        }

        dao.upsert(entity.copy(progress = 1f, state = DownloadState.COMPLETED.name, fileSizeBytes = video.length()))
    }

    private fun request(url: String, referer: String?): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .apply { if (referer != null) header("Referer", referer) }
        .build()

    private suspend fun fetchText(url: String, referer: String?): String = withContext(Dispatchers.IO) {
        client.newCall(request(url, referer)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for $url")
            resp.body?.string() ?: throw IOException("Empty body for $url")
        }
    }

    override suspend fun cancelDownload(episodeId: Long): Result<Unit> {
        return try {
            jobs.remove(episodeId)?.cancel()
            deleteFiles(episodeId)
            dao.updateProgress(episodeId, 0f, DownloadState.FAILED.name)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    override suspend fun deleteDownload(episodeId: Long): Result<Unit> {
        return try {
            jobs.remove(episodeId)?.cancel()
            deleteFiles(episodeId)
            dao.delete(episodeId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(e)
        }
    }

    private suspend fun deleteFiles(episodeId: Long) {
        val path = dao.getById(episodeId)?.filePath ?: return
        File(path).delete()
        listOf("vtt", "srt").forEach { File(sidecarBase(path) + ".$it").delete() }
    }

    private fun DownloadEntity.toDomain() = DownloadItem(
        episodeId = episodeId,
        animeTitle = animeTitle,
        episodeNumber = episodeNumber,
        quality = quality,
        filePath = filePath,
        fileSizeBytes = fileSizeBytes,
        progress = progress,
        state = runCatching { DownloadState.valueOf(state) }.getOrDefault(DownloadState.PENDING),
        createdAt = createdAt,
        subtitle = subtitle
    )
}
