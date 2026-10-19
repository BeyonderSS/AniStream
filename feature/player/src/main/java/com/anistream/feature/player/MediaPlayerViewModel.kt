package com.anistream.feature.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.anistream.core.common.Result
import com.anistream.domain.model.MediaStream
import com.anistream.domain.model.PlaybackRequest
import com.anistream.domain.model.WatchHistory
import com.anistream.domain.repository.DownloadRepository
import com.anistream.domain.repository.MoviesRepository
import com.anistream.domain.repository.NowPlaying
import com.anistream.domain.repository.WatchHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

data class MediaPlayerUiState(
    val request: PlaybackRequest? = null,
    val streamIndex: Int = 0,
    /** index into the current stream's subtitles; -1 = off */
    val subtitleIndex: Int = -1,
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    /** played to the end; the screen shows the next-episode countdown or the finished card */
    val ended: Boolean = false,
    val downloadStarted: Boolean = false
) {
    val stream: MediaStream? get() = request?.streams?.getOrNull(streamIndex)
    val isLive: Boolean get() = request?.isLive == true
    val hasNextEpisode: Boolean
        get() = request?.let { it.mediaId != null && it.season > 0 && it.episode > 0 && it.seasonEpisodeCount > it.episode } == true
}

/** "1080p · HEVC · 2.1 GB" (no source name). */
internal fun MediaStream.detailLabel(): String = listOfNotNull(
    qualityLabel.takeIf { it.isNotBlank() },
    codec?.takeIf { it.isNotBlank() }?.uppercase(),
    sizeBytes?.takeIf { it > 0 }?.let { formatBytes(it) }
).joinToString(" · ")

/** "MovieBox · 1080p · HEVC · 2.1 GB" */
internal fun MediaStream.label(): String = listOf(sourceName, detailLabel()).filter { it.isNotBlank() }.joinToString(" · ")

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024) else "${mb.toLong()} MB"
}

/** Explicit MIME wins; otherwise sniff the URL path (.m3u8 → HLS, .mpd → DASH), else let ExoPlayer treat it as progressive. */
internal fun inferMimeType(stream: MediaStream): String? {
    stream.mimeType?.let { return it }
    val path = Uri.parse(stream.url).path.orEmpty().lowercase()
    return when {
        path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
        else -> null
    }
}

internal fun subtitleMime(mime: String): String = when {
    mime.contains("vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    mime.contains("ass", ignoreCase = true) || mime.contains("ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}

/**
 * Generic player for Movies & TV, Stremio addon streams and Live TV. Reads [NowPlaying.request] once.
 * Owns the ExoPlayer so playback survives rotation.
 */
@OptIn(UnstableApi::class)
@HiltViewModel
class MediaPlayerViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val movies: MoviesRepository,
    private val history: WatchHistoryRepository,
    private val downloads: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaPlayerUiState(request = NowPlaying.request))
    val uiState: StateFlow<MediaPlayerUiState> = _uiState.asStateFlow()

    private var pendingResume: Float? = null
    /** stream index that already auto-switched away after an error, so a failure chain only walks forward */
    private var fellBackFrom = -1

    val player: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        _uiState.update { it.copy(isLoading = false) }
                        applyResume()
                    }
                    Player.STATE_ENDED -> if (!_uiState.value.isLive) {
                        persist(1f)
                        _uiState.update { it.copy(ended = true) }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    init {
        val request = _uiState.value.request
        if (request == null || request.streams.isEmpty()) {
            _uiState.update { it.copy(isLoading = false, error = "Nothing to play") }
        } else {
            viewModelScope.launch {
                recordStart(request)
                play(0, null)
            }
            viewModelScope.launch {
                while (true) {
                    delay(10_000)
                    if (player.isPlaying) saveProgress()
                }
            }
        }
    }

    private fun play(index: Int, startMs: Long?) {
        val request = _uiState.value.request ?: return
        val stream = request.streams.getOrNull(index) ?: return
        val userAgent = stream.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: DEFAULT_USER_AGENT
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(stream.headers.filterKeys { !it.equals("User-Agent", true) })
        val subtitleIndex = stream.subtitles.indexOfFirst { it.languageCode.startsWith("en", true) || it.label.startsWith("English", true) }
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .apply { inferMimeType(stream)?.let { setMimeType(it) } }
            .setSubtitleConfigurations(stream.subtitles.mapIndexed { i, t ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(t.url))
                    .setId("sub$i")
                    .setMimeType(subtitleMime(t.mimeType))
                    .setLanguage(t.languageCode)
                    .setLabel(t.label)
                    .build()
            })
            .apply { if (request.isLive) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build()) }
            .build()
        val source = DefaultMediaSourceFactory(http).createMediaSource(item)
        // live: no position = start at the live edge
        if (request.isLive || startMs == null) player.setMediaSource(source) else player.setMediaSource(source, startMs)
        _uiState.update { it.copy(streamIndex = index, isLoading = true, error = null, ended = false) }
        applySubtitle(stream, subtitleIndex)
        player.prepare()
        player.playWhenReady = true
    }

    private fun handleError(error: PlaybackException) {
        val s = _uiState.value
        val request = s.request ?: return
        if (s.isLive && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        val next = s.streamIndex + 1
        if (fellBackFrom != s.streamIndex && next < request.streams.size) {
            fellBackFrom = s.streamIndex
            play(next, if (s.isLive) null else player.currentPosition)
            _uiState.update { it.copy(message = "Switched to ${request.streams[next].label()}") }
            return
        }
        _uiState.update { it.copy(isLoading = false, error = "Playback failed (${error.errorCodeName})") }
    }

    /** Source sheet pick. VOD keeps its position. */
    fun selectStream(index: Int) {
        if (index == _uiState.value.streamIndex && _uiState.value.error == null) return
        play(index, if (_uiState.value.isLive) null else player.currentPosition)
    }

    fun retry() = play(_uiState.value.streamIndex, if (_uiState.value.isLive) null else player.currentPosition)

    /** -1 = off */
    fun selectSubtitle(index: Int) {
        val stream = _uiState.value.stream ?: return
        applySubtitle(stream, index)
    }

    // ponytail: picks by language, so two tracks with the same language code select the first; use a TrackSelectionOverride if that bites
    private fun applySubtitle(stream: MediaStream, index: Int) {
        val track = stream.subtitles.getOrNull(index)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, track == null)
            .setPreferredTextLanguage(track?.languageCode)
            .build()
        _uiState.update { it.copy(subtitleIndex = if (track == null) -1 else index) }
    }

    /** Upsert the history row so Continue Watching sees it; resume when it's the same season/episode. */
    private suspend fun recordStart(request: PlaybackRequest) {
        val key = request.historyKey
        if (request.isLive || key == null) return
        val existing = history.getBySlug(key).getOrNull()
        val progress = existing?.watchProgress ?: 0f
        pendingResume = if (
            existing != null && existing.season == request.season &&
            existing.lastEpisodeWatched == request.episode && progress in 0.02f..0.95f
        ) progress else null
        history.save(historyRow(request, pendingResume ?: 0f))
    }

    private fun historyRow(r: PlaybackRequest, fraction: Float) = WatchHistory(
        animeSlug = r.historyKey ?: "",
        animeTitle = r.title,
        posterUrl = r.posterUrl,
        totalEpisodes = r.seasonEpisodeCount,
        lastEpisodeWatched = r.episode,
        watchProgress = fraction,
        kind = "media",
        season = r.season,
        mediaId = r.mediaId,
        subtitle = r.subtitle
    )

    private fun applyResume() {
        val fraction = pendingResume ?: return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        pendingResume = null
        player.seekTo((fraction * duration).toLong())
    }

    /** Countdown finished / "Play now": resolve the next episode's streams and play them. */
    fun nextEpisode() {
        val s = _uiState.value
        val r = s.request ?: return
        val mediaId = r.mediaId ?: return
        if (!s.hasNextEpisode) return
        saveProgress()
        val episode = r.episode + 1
        _uiState.update { it.copy(ended = false, isLoading = true, error = null) }
        viewModelScope.launch {
            when (val res = movies.streams(mediaId, r.season, episode)) {
                is Result.Success -> {
                    if (res.data.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false, error = "No streams for episode $episode") }
                        return@launch
                    }
                    val next = r.copy(streams = res.data, episode = episode, subtitle = "S${r.season} · E$episode")
                    NowPlaying.request = next
                    fellBackFrom = -1
                    _uiState.update { it.copy(request = next, downloadStarted = false) }
                    recordStart(next)
                    play(0, null)
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = res.message ?: "Couldn't load episode $episode") }
                is Result.Loading -> {}
            }
        }
    }

    fun dismissEnd() = _uiState.update { it.copy(ended = false) }

    fun download() {
        val s = _uiState.value
        val r = s.request ?: return
        val key = r.historyKey ?: r.mediaId ?: r.title
        val stream = s.stream?.takeIf { it.isDirectFile } ?: r.streams.firstOrNull { it.isDirectFile }
        if (stream == null) {
            _uiState.update { it.copy(message = "This source can't be downloaded") }
            return
        }
        viewModelScope.launch {
            // keyed per episode so S1E1 and S1E2 don't overwrite each other
            val downloadKey = if (r.episode > 0) "$key:s${r.season}e${r.episode}" else key
            val res = downloads.startMediaDownload(stream, downloadKey, r.title, r.subtitle)
            val msg = if (res is Result.Error) "Download failed: ${res.message}" else "Downloading ${stream.label()}"
            _uiState.update { it.copy(message = msg, downloadStarted = res !is Result.Error) }
        }
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    fun onStop() {
        player.pause()
        saveProgress()
    }

    private fun saveProgress() {
        val duration = player.duration
        if (_uiState.value.isLoading || duration == C.TIME_UNSET || duration <= 0) return
        persist((player.currentPosition.toFloat() / duration).coerceIn(0f, 1f))
    }

    // outlives viewModelScope so the final save in onCleared still lands
    private fun persist(fraction: Float) {
        val r = _uiState.value.request ?: return
        if (r.isLive || r.historyKey == null) return
        CoroutineScope(Dispatchers.IO).launch { history.save(historyRow(r, fraction)) }
    }

    override fun onCleared() {
        saveProgress()
        player.release()
    }
}
