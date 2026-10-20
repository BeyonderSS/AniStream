package com.anistream.feature.player

import com.anistream.core.ui.theme.amberGlow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anistream.core.ui.components.PrimaryButton
import com.anistream.core.ui.components.SecondaryButton
import com.anistream.core.ui.components.SegmentedControl
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import kotlinx.coroutines.delay

internal val Scrim = Bg.shadow.copy(alpha = 0.5f)
internal val Tabular = AppType.bodyStrong.copy(fontFeatureSettings = "tnum")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val (fullscreen, setFullscreen) = rememberFullscreen(viewModel::onStop)

    LaunchedEffect(ui.message) {
        val msg = ui.message ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.messageShown()
    }

    val video: @Composable (Modifier) -> Unit = { m ->
        VideoPlayer(
            player = viewModel.player,
            title = "${ui.animeTitle}, Ep ${ui.episodeNumber}",
            isLoading = ui.isLoading,
            error = ui.error,
            fullscreen = fullscreen,
            onBack = { if (fullscreen) setFullscreen(false) else onBack() },
            onToggleFullscreen = { setFullscreen(!fullscreen) },
            onSettings = { showSettings = true },
            onRetry = viewModel::retry,
            endOverlay = if (!ui.ended) null else {
                {
                    val next = ui.episodeNumbers.firstOrNull { it > ui.episodeNumber }
                    if (next != null) NextCountdown("Up next: episode $next", viewModel::nextEpisode, viewModel::dismissEnd)
                    else FinishedCard(ui.animeTitle, onBack)
                }
            },
            modifier = m
        )
    }

    Box(Modifier.fillMaxSize().background(if (fullscreen) Color.Black else Bg.base)) {
        if (fullscreen) {
            video(Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.background(Color.Black).statusBarsPadding()) {
                    video(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                }
                PortraitDetails(
                    ui = ui,
                    onLanguage = viewModel::setLanguage,
                    onQuality = { showSettings = true },
                    onDownload = viewModel::download,
                    onNext = viewModel::nextEpisode,
                    onEpisode = viewModel::playEpisode,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }, containerColor = Bg.raised, shape = SheetShape) {
            Column(Modifier.padding(horizontal = Space.lg).padding(bottom = Space.xl)) {
                Text("Quality", style = AppType.heading)
                Spacer(Modifier.height(Space.sm))
                ui.availableQualities.forEach { source ->
                    SheetOption(source.qualityLabel, selected = source.qualityLabel == ui.selectedQuality) {
                        viewModel.selectQuality(source.qualityLabel)
                        showSettings = false
                    }
                }
                if (ui.availableQualities.isEmpty()) Text("No qualities yet", style = AppType.caption)
                Spacer(Modifier.height(Space.xl))
                Text("Audio", style = AppType.heading)
                Spacer(Modifier.height(Space.md))
                SubDubToggle(ui.language, viewModel::setLanguage)
            }
        }
    }
}

/** A row in a player bottom sheet: label (mist when selected, haze row) + amber check. */
@Composable
internal fun SheetOption(label: String, selected: Boolean, caption: String? = null, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (caption == null) 48.dp else 56.dp)
            .clip(Radius.md)
            .background(if (selected) Bg.overlay else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Tabular.copy(color = if (selected) Ink.high else Ink.body), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = AppType.numeric, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Accent.main)
    }
}

/**
 * Landscape = fullscreen: hides system bars, back exits fullscreen first, gives orientation back when leaving.
 * [onStop] runs on a real ON_STOP (not a rotation). Returns (fullscreen, setFullscreen).
 */
@Composable
internal fun rememberFullscreen(onStop: () -> Unit): Pair<Boolean, (Boolean) -> Unit> {
    val fullscreen = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current

    // rotation (incl. the fullscreen button) recreates the activity: don't pause for that
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) onStop()
    }

    // Rotation recreates the activity; only give orientation back when actually leaving the player.
    DisposableEffect(Unit) {
        onDispose {
            if (activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    DisposableEffect(fullscreen) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (fullscreen) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // ponytail: exiting fullscreen pins SENSOR_PORTRAIT until the next fullscreen tap or leaving the screen,
    // so rotate-to-fullscreen stops working after a manual exit (YouTube does the same).
    fun setFullscreen(on: Boolean) {
        activity?.requestedOrientation =
            if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
    BackHandler(enabled = fullscreen) { setFullscreen(false) }
    return fullscreen to ::setFullscreen
}

/**
 * Video surface + the Nocturne controls overlay, shared by the anime and media players.
 * [live]: no seek bar / ±10s, a LIVE badge instead. [endOverlay] non-null = playback ended, shown over the video.
 */
@Composable
internal fun VideoPlayer(
    player: Player,
    title: String,
    isLoading: Boolean,
    error: String?,
    fullscreen: Boolean,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier,
    live: Boolean = false,
    endOverlay: (@Composable () -> Unit)? = null
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var bufferedPos by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var interactions by remember { mutableIntStateOf(0) }
    var bubble by remember { mutableIntStateOf(0) } // -1 rewind, +1 forward, 0 hidden
    var bubbleTick by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    // poll only while something on screen shows the position
    LaunchedEffect(controlsVisible) {
        while (controlsVisible) {
            position = player.currentPosition
            bufferedPos = player.bufferedPosition
            duration = player.duration.coerceAtLeast(0L) // TIME_UNSET is negative
            delay(250)
        }
    }
    LaunchedEffect(controlsVisible, isPlaying, scrubMs != null, interactions) {
        if (controlsVisible && isPlaying && scrubMs == null) {
            delay(3000)
            controlsVisible = false
        }
    }
    LaunchedEffect(bubbleTick) {
        if (bubble != 0) {
            delay(650)
            bubble = 0
        }
    }

    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        position = target
        interactions++
    }

    val showSpinner = isLoading || buffering
    val blocked = error != null || endOverlay != null

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx -> LayoutInflater.from(ctx).inflate(R.layout.player_texture, null) as PlayerView },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(live) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = if (live) null else { offset ->
                            val dir = if (offset.x < size.width / 2) -1 else 1
                            seekBy(dir * 10_000L)
                            bubble = dir
                            bubbleTick++
                        }
                    )
                }
        )

        SeekBubble("−10s", visible = bubble == -1, Modifier.align(Alignment.CenterStart).padding(start = Space.xxl))
        SeekBubble("+10s", visible = bubble == 1, Modifier.align(Alignment.CenterEnd).padding(end = Space.xxl))

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize().background(Scrim)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.xs, vertical = if (fullscreen) Space.sm else 0.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink.high)
                    }
                    Text(
                        title,
                        style = AppType.bodyStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Ink.high)
                    }
                }

                if (!blocked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.xxl),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        if (!live) IconButton(onClick = { seekBy(-10_000L) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Replay10, contentDescription = "Back 10 seconds", tint = Ink.high, modifier = Modifier.size(32.dp))
                        }
                        if (showSpinner) {
                            Spacer(Modifier.size(72.dp))
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(72.dp)
                                    .amberGlow(Radius.round)
                                    .clip(Radius.round)
                                    .background(Accent.main)
                                    .clickable {
                                        when {
                                            player.isPlaying -> player.pause()
                                            player.playbackState == Player.STATE_ENDED -> { player.seekTo(0L); player.play() }
                                            else -> player.play()
                                        }
                                        interactions++
                                    }
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Accent.on,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        if (!live) IconButton(onClick = { seekBy(10_000L) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.Forward10, contentDescription = "Forward 10 seconds", tint = Ink.high, modifier = Modifier.size(32.dp))
                        }
                    }
                }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = Space.lg)
                        .padding(bottom = if (fullscreen) Space.md else 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (live) {
                            Text(
                                "Live",
                                style = AppType.label.copy(color = Accent.onLive),
                                modifier = Modifier.background(Accent.live, Radius.pill).padding(horizontal = Space.sm + 2.dp, vertical = 2.dp)
                            )
                        } else {
                            Text(
                                "${formatTime(scrubMs ?: position)} / ${formatTime(duration)}",
                                style = AppType.numeric.copy(color = Ink.high)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = if (fullscreen) "Exit fullscreen" else "Fullscreen",
                                tint = Ink.high
                            )
                        }
                    }
                    if (!live) SeekBar(
                        position = position,
                        buffered = bufferedPos,
                        duration = duration,
                        onScrub = { scrubMs = it },
                        onSeek = { target ->
                            player.seekTo(target)
                            position = target
                        }
                    )
                }
            }
        }

        if (showSpinner && error == null) {
            CircularProgressIndicator(color = Accent.main, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(48.dp))
        }

        if (error != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.Center).padding(Space.lg)
            ) {
                Text(error, style = AppType.body, textAlign = TextAlign.Center, maxLines = 3)
                Spacer(Modifier.height(Space.md))
                PrimaryButton(text = "Retry", onClick = onRetry)
            }
        }

        if (endOverlay != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Bg.base.copy(alpha = 0.85f))
                    .pointerInput(Unit) { detectTapGestures { } } // swallow taps meant for the video
            ) {
                endOverlay()
            }
        }
    }
}

/** [label] e.g. "Up next: episode 3"; runs [onNext] after 5s. */
@Composable
internal fun NextCountdown(label: String, onNext: () -> Unit, onCancel: () -> Unit) {
    var seconds by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds--
        }
        onNext()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = AppType.label.copy(color = Ink.muted))
        Spacer(Modifier.height(Space.xs))
        Text("Next episode in $seconds", style = AppType.heading)
        Spacer(Modifier.height(Space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            SecondaryButton(text = "Cancel", onClick = onCancel)
            PrimaryButton(text = "Play now", onClick = onNext)
        }
    }
}

@Composable
internal fun FinishedCard(title: String, onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Space.lg)) {
        Text("You finished", style = AppType.label.copy(color = Ink.muted))
        Spacer(Modifier.height(Space.xs))
        Text(title, style = AppType.heading, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Space.lg))
        PrimaryButton(text = "Back to details", onClick = onBack)
    }
}

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

@Composable
private fun SeekBubble(text: String, visible: Boolean, modifier: Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + scaleIn(tween(200), initialScale = 0.8f),
        exit = fadeOut(tween(250)),
        modifier = modifier
    ) {
        Box(
            Modifier
                .clip(Radius.pill)
                .background(Scrim)
                .padding(horizontal = Space.lg, vertical = Space.sm)
        ) {
            Text(text, style = AppType.label.copy(fontFeatureSettings = "tnum"))
        }
    }
}

/** Slim seek bar: 4dp rounded track (6dp while dragging) in mist, buffered brighter, played in amber, round amber thumb. */
@Composable
private fun SeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    onScrub: (Long?) -> Unit,
    onSeek: (Long) -> Unit
) {
    var scrub by remember { mutableStateOf<Float?>(null) }
    val currentDuration by rememberUpdatedState(duration)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val total = duration.coerceAtLeast(1L).toFloat()
    val played = scrub ?: (position / total).coerceIn(0f, 1f)
    val loaded = (buffered / total).coerceIn(0f, 1f)
    val dragging = scrub != null

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (currentDuration <= 0L) return@awaitEachGesture
                    down.consume()
                    fun update(x: Float) {
                        val f = (x / size.width).coerceIn(0f, 1f)
                        scrub = f
                        currentOnScrub((f * currentDuration).toLong())
                    }
                    update(down.position.x)
                    horizontalDrag(down.id) { change ->
                        change.consume()
                        update(change.position.x)
                    }
                    scrub?.let { currentOnSeek((it * currentDuration).toLong()) }
                    scrub = null
                    currentOnScrub(null)
                }
            }
    ) {
        val track: Dp = if (dragging) 6.dp else 4.dp
        val h = track.toPx()
        val top = (size.height - h) / 2
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(Ink.ghost, Offset(0f, top), Size(size.width, h), r)
        drawRoundRect(Ink.muted, Offset(0f, top), Size(size.width * loaded, h), r)
        drawRoundRect(Accent.main, Offset(0f, top), Size(size.width * played, h), r)
        drawCircle(Accent.main, radius = (if (dragging) 8.dp else 6.dp).toPx(), center = Offset(size.width * played, size.height / 2))
    }
}

@Composable
private fun PortraitDetails(
    ui: PlayerUiState,
    onLanguage: (String) -> Unit,
    onQuality: () -> Unit,
    onDownload: () -> Unit,
    onNext: () -> Unit,
    onEpisode: (Int) -> Unit,
    modifier: Modifier
) {
    val next = ui.episodeNumbers.firstOrNull { it > ui.episodeNumber }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(vertical = Space.lg)
    ) {
        Column(Modifier.padding(horizontal = Space.lg)) {
            Text(ui.animeTitle, style = AppType.heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.xs))
            val total = ui.episodeNumbers.size
            Text(
                if (total > 0) "Episode ${ui.episodeNumber} of $total" else "Episode ${ui.episodeNumber}",
                style = AppType.numeric
            )
        }
        Spacer(Modifier.height(Space.lg))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            modifier = Modifier.padding(horizontal = Space.lg)
        ) {
            SubDubToggle(ui.language, onLanguage)
            val qualityEnabled = ui.availableQualities.isNotEmpty()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(48.dp)
                    .clip(Radius.pill)
                    .background(Bg.raised)
                    .clickable(enabled = qualityEnabled, onClick = onQuality)
                    .padding(start = Space.md, end = Space.sm)
            ) {
                Text(
                    ui.streamSource?.qualityLabel ?: "Auto",
                    style = AppType.label.copy(color = if (qualityEnabled) Ink.high else Ink.faint, fontFeatureSettings = "tnum")
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Change quality", tint = Ink.body, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onDownload,
                enabled = ui.streamSource != null && !ui.isLoading && !ui.downloadStarted,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    if (ui.downloadStarted) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                    contentDescription = if (ui.downloadStarted) "Downloading" else "Download episode",
                    tint = if (ui.downloadStarted) Accent.live else Ink.body
                )
            }
        }

        if (next != null) {
            Spacer(Modifier.height(Space.xl))
            UpNextCard(ui.posterUrl, next, onNext)
        }

        if (ui.episodeNumbers.size > 1) {
            Spacer(Modifier.height(Space.xl))
            Text("Episodes", style = AppType.heading, modifier = Modifier.padding(horizontal = Space.lg))
            Spacer(Modifier.height(Space.md))
            EpisodeStrip(ui.episodeNumbers, ui.episodeNumber, ui.watchedBelow, onEpisode)
        }
    }
}

@Composable
private fun SubDubToggle(language: String, onSelect: (String) -> Unit) {
    SegmentedControl(
        options = listOf("Sub", "Dub"),
        selectedIndex = if (language == "dub") 1 else 0,
        onSelect = { onSelect(if (it == 1) "dub" else "sub") }
    )
}

@Composable
private fun UpNextCard(posterUrl: String?, next: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = Space.lg)
            .fillMaxWidth()
            .clip(Radius.lg)
            .background(Bg.raised)
            .clickable(onClick = onClick)
            .padding(Space.md)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(posterUrl).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(Radius.md)
                .background(Bg.overlay)
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text("Up next", style = AppType.label.copy(color = Ink.muted))
            Text("Episode $next", style = AppType.bodyStrong)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).background(Bg.overlay, Radius.round)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play episode $next", tint = Ink.high)
        }
    }
}

@Composable
private fun EpisodeStrip(numbers: List<Int>, current: Int, watchedBelow: Int, onEpisode: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(current, numbers.size) {
        val i = numbers.indexOf(current)
        if (i >= 0) state.animateScrollToItem((i - 2).coerceAtLeast(0))
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        items(numbers, key = { it }) { n ->
            val isCurrent = n == current
            val watched = !isCurrent && n < watchedBelow
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(Radius.md)
                    .background(if (isCurrent) Accent.main else Bg.raised)
                    .clickable { onEpisode(n) }
            ) {
                Text(
                    "$n",
                    style = Tabular.copy(color = if (isCurrent) Accent.on else if (watched) Ink.faint else Ink.body)
                )
            }
        }
    }
}

internal fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
