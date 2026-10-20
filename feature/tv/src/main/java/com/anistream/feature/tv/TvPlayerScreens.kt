package com.anistream.feature.tv

import com.anistream.core.common.LowEnd
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.text.format.DateFormat
import java.util.Date
import com.anistream.feature.player.INTRO_SKIP_MS
import com.anistream.feature.player.INTRO_WINDOW_MS
import com.anistream.feature.player.CountdownRing
import com.anistream.feature.player.activeSkip
import com.anistream.feature.player.skipLabel
import com.anistream.domain.model.SkipInterval
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.anistream.feature.player.PlaybackSpeeds
import com.anistream.feature.player.Resize
import com.anistream.feature.player.SleepOptions
import com.anistream.feature.player.SubtitleDelays
import com.anistream.feature.player.SubtitleScales
import com.anistream.feature.player.VideoSurface
import com.anistream.feature.player.audioOptions
import com.anistream.feature.player.delayLabel
import com.anistream.feature.player.hasSubtitles
import com.anistream.feature.player.rememberPlayerTweaks
import com.anistream.feature.player.rememberTracks
import com.anistream.feature.player.selectAudio
import com.anistream.feature.player.sleepLabel
import com.anistream.feature.player.speedLabel
import com.anistream.feature.player.subtitleScaleLabel
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
    /** per-title key for remembered speed / picture / subtitle size */
    tweaksKey: String,
    /** episodes get a "Skip intro" chip in their first 5 minutes */
    episodic: Boolean,
    onWebPlayer: (() -> Unit)? = null,
    /** art for the up-next card (poster) */
    upNextImage: String? = null,
    /** AniSkip intervals; non-empty replaces the fixed +85 s chip */
    skips: List<SkipInterval> = emptyList(),
    /** offer the "Auto-skip intro and recap" setting (anime) */
    skipSetting: Boolean = false
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
    val tweaks = rememberPlayerTweaks(tweaksKey)
    val tracks = rememberTracks(player)
    val introFocus = remember { FocusRequester() }
    var introFocused by remember { mutableStateOf(false) }
    var introSkippedFor by remember { mutableStateOf<String?>(null) }
    var skipToast by remember { mutableStateOf<String?>(null) }
    val autoSkipped = remember(title, subtitle) { mutableSetOf<SkipInterval>() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val context = LocalContext.current
    var lowEnd by remember { mutableStateOf(LowEnd.isOn(context)) }

    val audio = audioOptions(tracks)
    val subs = hasSubtitles(tracks)
    val allSettings = settings + listOf(
        SettingGroup("Speed", if (isLive) emptyList() else PlaybackSpeeds, ::speedLabel, { it == tweaks.speed }) { tweaks.updateSpeed(player, it) },
        SettingGroup("Picture", Resize.entries, { it.label }, { it == tweaks.resize }, tweaks::updateResize),
        SettingGroup("Audio track", if (audio.size > 1) audio else emptyList(), { it.label }, { it.selected }) { selectAudio(player, it) },
        SettingGroup("Subtitle size", if (subs) SubtitleScales else emptyList(), ::subtitleScaleLabel, { it == tweaks.subScale }, tweaks::updateSubScale),
        SettingGroup("Subtitle delay", if (subs) SubtitleDelays else emptyList(), ::delayLabel, { it == tweaks.subDelayMs }) { tweaks.subDelayMs = it },
        SettingGroup("Sleep timer", SleepOptions, ::sleepLabel, { it == tweaks.sleep }, tweaks::updateSleep),
        SettingGroup("Auto-skip intro and recap", if (skipSetting) listOf(true, false) else emptyList(), { if (it) "On" else "Off" }, { it == tweaks.autoSkip }, tweaks::updateAutoSkip),
        SettingGroup("Low-end mode (applies on restart)", listOf(true, false), { if (it) "On" else "Off" }, { it == lowEnd }) { lowEnd = it; LowEnd.set(context, it) }
    )
    val hasSettings = allSettings.any { it.options.isNotEmpty() }
    val skipNow = if (episodic && !isLive && error == null && !ended) activeSkip(skips, position) else null
    val showIntro = panel == Panel.NONE && (
        skipNow != null ||
            (skips.isEmpty() && episodic && !isLive && error == null && !ended && introSkippedFor != title &&
                duration > INTRO_WINDOW_MS && position < INTRO_WINDOW_MS)
        )
    val skipText = skipNow?.let { skipLabel(it.type) } ?: "Skip intro"
    val upNextShown = ended && upNext != null

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
            now = System.currentTimeMillis()
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
    LaunchedEffect(showIntro) { if (!showIntro && introFocused) runCatching { rootFocus.requestFocus() } }
    LaunchedEffect(message) { if (message != null) { delay(3500); onMessageShown() } }
    // AniSkip auto-skip: intro/recap once per interval, with a short toast
    LaunchedEffect(skipNow, tweaks.autoSkip) {
        val s = skipNow ?: return@LaunchedEffect
        if (tweaks.autoSkip && s.isIntroOrRecap && autoSkipped.add(s)) {
            player.seekTo(s.endMs)
            position = s.endMs
            skipToast = if (s.type == "recap") "Skipped recap" else "Skipped intro"
        }
    }
    LaunchedEffect(skipToast) { if (skipToast != null) { delay(2000); skipToast = null } }

    BackHandler(enabled = upNextShown) { onCancelUpNext() }
    BackHandler(enabled = !upNextShown && (panel != Panel.NONE || visible) && error == null) {
        if (panel != Panel.NONE) closePanel() else visible = false
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                // the up-next card's buttons take the D-pad
                if (panel != Panel.NONE || error != null || upNextShown) { poke++; return@onPreviewKeyEvent false }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // the skip-intro chip (reached with ↓) takes OK; ↑ backs out; anything else acts as usual
                val fromIntro = introFocused
                if (fromIntro) {
                    when (e.key) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> return@onPreviewKeyEvent false
                        Key.DirectionUp -> { runCatching { rootFocus.requestFocus() }; show(); return@onPreviewKeyEvent true }
                        else -> runCatching { rootFocus.requestFocus() }
                    }
                }
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
                    Key.DirectionUp -> if (hasSettings) panel = Panel.SETTINGS
                    Key.DirectionDown -> when {
                        showIntro && !fromIntro -> runCatching { introFocus.requestFocus() }
                        strip != null && strip.numbers.isNotEmpty() -> panel = Panel.STRIP
                    }
                    else -> { show(); return@onPreviewKeyEvent false }
                }
                show()
                true
            }
            .focusRequester(rootFocus)
            .focusable()
    ) {
        VideoSurface(
            player = player,
            tweaks = tweaks,
            create = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    keepScreenOn = true
                    isFocusable = false
                }
            },
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
                        Panel.SETTINGS -> SettingsPanel(allSettings, panelFocus) { closePanel() }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Text(
                                listOfNotNull(
                                    "↑ Settings".takeIf { hasSettings },
                                    if (showIntro) "↓ $skipText" else "↓ Episodes".takeIf { strip != null }
                                ).joinToString("    "),
                                style = TvType.body.copy(color = Ink.muted),
                                modifier = Modifier.weight(1f)
                            )
                            val fmt = remember(context) { DateFormat.getTimeFormat(context) }
                            val left = duration - position
                            Text(
                                listOfNotNull(
                                    fmt.format(Date(now)),
                                    if (!isLive && duration > 0) "Ends at ${fmt.format(Date(now + (left / tweaks.speed).toLong()))}" else null
                                ).joinToString("     "),
                                style = TvType.body.copy(color = Ink.body, fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
            }
        }

        if (showIntro) {
            TvPill(
                skipText,
                onClick = {
                    val s = skipNow
                    if (s != null) player.seekTo(s.endMs)
                    else player.seekTo((player.currentPosition + INTRO_SKIP_MS).coerceAtMost(duration))
                    position = player.currentPosition
                    if (s == null) introSkippedFor = title
                    show()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Overscan, bottom = if (visible) 150.dp else 40.dp)
                    .focusRequester(introFocus)
                    .onFocusChanged { introFocused = it.isFocused }
            )
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

        if (upNextShown) {
            UpNextCard(upNextImage, upNext.orEmpty(), title, onUpNext, onCancelUpNext, Modifier.align(Alignment.BottomEnd).padding(Overscan))
        }
        val note = message ?: skipToast
        if (note != null) {
            Text(
                note,
                style = TvType.body.copy(color = Ink.high),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp).background(Bg.overlay, Radius.pill).padding(horizontal = 22.dp, vertical = 10.dp)
            )
        }
    }
}

/** Next-episode preview: art under the countdown ring, "Episode N", Play now (focused) / Cancel. The ring runs the countdown. */
@Composable
private fun UpNextCard(imageUrl: String?, episodeLabel: String, title: String, onPlay: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { playFocus.requestFocus() } }
    Row(
        modifier.background(Bg.raised, Radius.xl).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(240.dp).aspectRatio(16f / 9f).clip(Radius.lg).background(Bg.overlay)
            )
            Box(Modifier.matchParentSize().clip(Radius.lg).background(Bg.base.copy(alpha = 0.45f)))
            CountdownRing(onDone = onPlay, size = 64.dp, textStyle = TvType.title)
        }
        Column {
            Text("Up next", style = TvType.body.copy(color = Ink.muted))
            Text(episodeLabel, style = TvType.headline, maxLines = 1)
            Text(title, style = TvType.body, maxLines = 1, modifier = Modifier.widthIn(max = 360.dp))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvPill("Play now", onPlay, modifier = Modifier.focusRequester(playFocus))
                TvPill("Cancel", onCancel)
            }
        }
    }
}

@Composable
private fun SettingsPanel(groups: List<SettingGroup<*>>, firstFocus: FocusRequester, close: () -> Unit) {
    // scrolls: focus brings the active row into view
    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        onCancelUpNext = vm::dismissEnd,
        tweaksKey = s.animeSlug,
        episodic = true,
        upNextImage = s.posterUrl,
        skips = s.skips,
        skipSetting = true
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
        tweaksKey = r?.historyKey ?: r?.title ?: "media",
        episodic = (r?.episode ?: 0) > 0,
        onWebPlayer = if (s.canWebPlay) vm::openWebPlayer else null,
        upNextImage = r?.posterUrl
    )
}

private const val WEB_SOURCE = -1
