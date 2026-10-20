package com.anistream.feature.tv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.feature.player.MediaPlayerViewModel
import com.anistream.feature.player.PlayerViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Panel { NONE, SETTINGS, STRIP }

/** One option group in the ↑ settings panel. */
internal class SettingGroup<T>(val title: String, val options: List<T>, val label: (T) -> String, val selected: (T) -> Boolean, val pick: (T) -> Unit)

/** ↓ episode strip: numbers, current, and a picker. */
internal class EpisodeStrip(val numbers: List<Int>, val current: Int, val watchedBelow: Int, val pick: (Int) -> Unit)

/**
 * D-pad player chrome shared by the anime and media players (both ViewModels own their ExoPlayer).
 * OK play/pause · ←/→ seek ±10 s, accelerating while held · ↑ settings · ↓ episodes · Back hides controls first.
 * Controls auto-hide after 4 s of no input while playing.
 */
@OptIn(UnstableApi::class)
@Composable
private fun TvPlayerChrome(
    player: Player,
    title: String,
    subtitle: String?,
    isLive: Boolean,
    loading: Boolean,
    error: String?,
    message: String?,
    onMessageShown: () -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    onExit: () -> Unit,
    settings: List<SettingGroup<*>>,
    strip: EpisodeStrip?,
    upNext: String?,
    ended: Boolean,
    onUpNext: () -> Unit,
    onCancelUpNext: () -> Unit,
    onWebPlayer: (() -> Unit)? = null
) {
    var visible by remember { mutableStateOf(true) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var poke by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val reduce = LocalReduceMotion.current

    fun show() { visible = true; poke++ }
    fun closePanel() { panel = Panel.NONE; poke++; runCatching { rootFocus.requestFocus() } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) onStop() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            playing = player.isPlaying
            delay(500)
        }
    }
    LaunchedEffect(poke, panel, playing) {
        if (panel == Panel.NONE && playing) { delay(4000); visible = false }
    }
    LaunchedEffect(panel) {
        if (panel != Panel.NONE) { withFrameNanos { }; runCatching { panelFocus.requestFocus() } }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(message) { if (message != null) { delay(3500); onMessageShown() } }
    LaunchedEffect(ended, upNext) { if (ended && upNext != null) { delay(5000); onUpNext() } }

    BackHandler(enabled = ended && upNext != null) { onCancelUpNext() }
    BackHandler(enabled = !(ended && upNext != null) && (panel != Panel.NONE || visible) && error == null) {
        if (panel != Panel.NONE) closePanel() else visible = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                if (panel != Panel.NONE || error != null) { poke++; return@onPreviewKeyEvent false }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // ponytail: linear ramp on key repeat (10 s → 60 s per step); tune here if it feels slow on long films
                val step = 10_000L * (1 + e.nativeKeyEvent.repeatCount / 5).coerceAtMost(6)
                fun seek(delta: Long) {
                    if (isLive) return
                    val max = if (duration > 0) duration else Long.MAX_VALUE
                    player.seekTo((player.currentPosition + delta).coerceIn(0L, max))
                    position = player.currentPosition
                }
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                        if (player.isPlaying) player.pause() else player.play()
                        playing = player.isPlaying
                    }
                    Key.MediaPlay -> player.play()
                    Key.MediaPause -> player.pause()
                    Key.DirectionLeft, Key.MediaRewind -> seek(-step)
                    Key.DirectionRight, Key.MediaFastForward -> seek(step)
                    Key.DirectionUp -> if (settings.any { it.options.isNotEmpty() }) panel = Panel.SETTINGS
                    Key.DirectionDown -> if (strip != null && strip.numbers.isNotEmpty()) panel = Panel.STRIP
                    else -> { show(); return@onPreviewKeyEvent false }
                }
                show()
                true
            }
            .focusRequester(rootFocus)
            .focusable()
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    isFocusable = false
                    this.player = player
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        if (loading && error == null) {
            Text("Loading…", style = TvType.title, modifier = Modifier.align(Alignment.Center).background(Bg.badge, Radius.pill).padding(horizontal = 24.dp, vertical = 10.dp))
        }

        AnimatedVisibility(
            visible = visible || panel != Panel.NONE || error != null,
            enter = if (reduce) EnterTransition.None else fadeIn(),
            exit = if (reduce) ExitTransition.None else fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Bg.base.copy(alpha = 0.85f), Color.Transparent)))
                        .padding(horizontal = Overscan, vertical = 32.dp)
                ) {
                    Text(title, style = TvType.headline, maxLines = 1)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = TvType.body)
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Bg.base.copy(alpha = 0.92f))))
                        .padding(horizontal = Overscan, vertical = 32.dp)
                ) {
                    when (panel) {
                        Panel.SETTINGS -> SettingsPanel(settings, panelFocus) { closePanel() }
                        Panel.STRIP -> if (strip != null) StripPanel(strip, panelFocus) { closePanel() }
                        Panel.NONE -> Unit
                    }
                    if (panel != Panel.NONE) Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Ink.high)
                        if (isLive) {
                            Text("Live", style = TvType.body.copy(color = Accent.onLive), modifier = Modifier.background(Accent.live, Radius.pill).padding(horizontal = 12.dp, vertical = 2.dp))
                        } else {
                            Text(formatTime(position), style = TvType.body.copy(fontFeatureSettings = "tnum"))
                            Box(Modifier.weight(1f).height(6.dp).background(Ink.faint, Radius.pill)) {
                                val f = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                                Box(Modifier.fillMaxWidth(f).height(6.dp).background(Accent.main, Radius.pill))
                            }
                            Text(formatTime(duration), style = TvType.body.copy(fontFeatureSettings = "tnum"))
                        }
                    }
                    if (panel == Panel.NONE) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            listOfNotNull("↑ Settings".takeIf { settings.any { it.options.isNotEmpty() } }, "↓ Episodes".takeIf { strip != null }).joinToString("    "),
                            style = TvType.body.copy(color = Ink.muted)
                        )
                    }
                }
            }
        }

        if (error != null) {
            val retryFocus = remember { FocusRequester() }
            LaunchedEffect(error) { withFrameNanos { }; runCatching { retryFocus.requestFocus() } }
            Column(
                Modifier.align(Alignment.Center).background(Bg.raised, Radius.xl).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error, style = TvType.title)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvPill("Retry", onRetry, modifier = Modifier.focusRequester(retryFocus))
                    if (settings.any { it.options.size > 1 }) TvPill("Change source", { panel = Panel.SETTINGS })
                    if (onWebPlayer != null) TvPill("Try web player", onWebPlayer)
                    TvPill("Back", onExit)
                }
            }
        }

        if (ended && upNext != null) {
            Text(
                "$upNext in 5 s. Back to cancel",
                style = TvType.title,
                modifier = Modifier.align(Alignment.TopEnd).padding(Overscan).background(Bg.raised, Radius.lg).padding(horizontal = 24.dp, vertical = 14.dp)
            )
        }
        if (message != null) {
            Text(
                message,
                style = TvType.body.copy(color = Ink.high),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp).background(Bg.overlay, Radius.pill).padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun SettingsPanel(groups: List<SettingGroup<*>>, firstFocus: FocusRequester, close: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.filter { it.options.isNotEmpty() }.forEachIndexed { gi, g -> SettingRow(g, if (gi == 0) firstFocus else null, close) }
    }
}

@Composable
private fun <T> SettingRow(g: SettingGroup<T>, focus: FocusRequester?, close: () -> Unit) {
    PillRow(g.title, g.options, g.label, g.selected, { g.pick(it); close() },
        initial = g.options.indexOfFirst(g.selected).coerceAtLeast(0), firstFocus = focus, inset = 8.dp)
}

@Composable
private fun StripPanel(strip: EpisodeStrip, focus: FocusRequester, close: () -> Unit) {
    PillRow("Episodes", strip.numbers, { "Episode $it" }, { it == strip.current || it < strip.watchedBelow }, { strip.pick(it); close() },
        initial = strip.numbers.indexOf(strip.current).coerceAtLeast(0), firstFocus = focus, inset = 8.dp)
}

private fun formatTime(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, t % 3600 / 60, t % 60) else String.format(Locale.US, "%d:%02d", t / 60, t % 60)
}

@Composable
fun TvAnimePlayerScreen(onExit: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    val next = s.episodeNumbers.firstOrNull { it > s.episodeNumber }
    TvPlayerChrome(
        player = vm.player,
        title = s.animeTitle,
        subtitle = "Episode ${s.episodeNumber}  ${if (s.language == "dub") "Dub" else "Sub"}",
        isLive = false,
        loading = s.isLoading,
        error = s.error,
        message = s.message,
        onMessageShown = vm::messageShown,
        onRetry = vm::retry,
        onStop = vm::onStop,
        onExit = onExit,
        settings = listOf(
            SettingGroup("Audio", listOf("sub", "dub"), { if (it == "dub") "Dub" else "Sub" }, { it == s.language }, vm::setLanguage),
            SettingGroup("Quality", s.availableQualities.map { it.qualityLabel }, { it }, { it == s.selectedQuality }, vm::selectQuality)
        ),
        strip = EpisodeStrip(s.episodeNumbers, s.episodeNumber, s.watchedBelow, vm::playEpisode),
        upNext = next?.let { "Episode $it" }.takeIf { s.hasNext },
        ended = s.ended,
        onUpNext = vm::nextEpisode,
        onCancelUpNext = vm::dismissEnd
    )
}


@Composable
fun TvMediaPlayerScreen(
    onExit: () -> Unit,
    /** (imdbId, season, episode) */
    onWebPlayer: (String, Int, Int) -> Unit,
    vm: MediaPlayerViewModel = hiltViewModel()
) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    val r = s.request
    LaunchedEffect(s.webImdbId) {
        val tt = s.webImdbId ?: return@LaunchedEffect
        vm.webPlayerOpened()
        onWebPlayer(tt, r?.season ?: 0, r?.episode ?: 0)
    }
    val streams = r?.streams.orEmpty()
    val subs = s.stream?.subtitles.orEmpty()
    TvPlayerChrome(
        player = vm.player,
        title = r?.title.orEmpty(),
        subtitle = r?.subtitle,
        isLive = s.isLive,
        loading = s.isLoading,
        error = s.error,
        message = s.message,
        onMessageShown = vm::messageShown,
        onRetry = vm::retry,
        onStop = vm::onStop,
        onExit = onExit,
        settings = listOf(
            // WEB_SOURCE = last-resort web player, after the real sources
            SettingGroup(
                "Source", streams.indices.toList() + listOfNotNull(WEB_SOURCE.takeIf { s.canWebPlay }),
                { i -> if (i == WEB_SOURCE) "Web player" else streams[i].let { listOf(it.sourceName, it.qualityLabel).filter(String::isNotBlank).joinToString(" ") } },
                { it == s.streamIndex },
                { i -> if (i == WEB_SOURCE) vm.openWebPlayer() else vm.selectStream(i) }
            ),
            SettingGroup("Subtitles", if (subs.isEmpty()) emptyList() else listOf(-1) + subs.indices, { i -> if (i < 0) "Off" else subs[i].label }, { it == s.subtitleIndex }, vm::selectSubtitle)
        ),
        // MediaPlayerViewModel can only advance one episode, so the strip offers exactly that
        strip = if (s.hasNextEpisode && r != null) EpisodeStrip(listOf(r.episode + 1), -1, 0) { vm.nextEpisode() } else null,
        upNext = if (s.hasNextEpisode && r != null) "Episode ${r.episode + 1}" else null,
        ended = s.ended,
        onUpNext = vm::nextEpisode,
        onCancelUpNext = vm::dismissEnd,
        onWebPlayer = if (s.canWebPlay) vm::openWebPlayer else null
    )
}

private const val WEB_SOURCE = -1
