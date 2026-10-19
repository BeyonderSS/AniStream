package com.anistream.feature.movies

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anistream.core.ui.components.ArtworkImage
import com.anistream.core.ui.components.ErrorContent
import com.anistream.core.ui.components.LoadingShimmer
import com.anistream.core.ui.components.PillChip
import com.anistream.core.ui.components.PosterImage
import com.anistream.core.ui.components.PrimaryButton
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.MediaDetail
import com.anistream.domain.model.MediaType
import kotlinx.coroutines.launch

private const val TILES_PER_ROW = 5
private val BarHeight = 56.dp

@Composable
fun MovieDetailScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshHistory()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                DetailEvent.OpenPlayer -> onOpenPlayer()
                is DetailEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    val detail = uiState.detail
    val phase = when {
        uiState.isLoading -> 0
        detail == null -> 1
        else -> 2
    }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240 } }
    val barBg by animateColorAsState(if (scrolled) Bg.base else Color.Transparent, tween(200), label = "movieBar")

    Box(Modifier.fillMaxSize().background(Bg.base)) {
        Crossfade(targetState = phase, animationSpec = tween(250), label = "movieDetail") { p ->
            when (p) {
                0 -> DetailSkeleton()
                1 -> ErrorContent(message = uiState.error ?: "Failed to load", onRetry = viewModel::load, modifier = Modifier.statusBarsPadding())
                else -> if (detail != null) {
                    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomInset + BarHeight + Space.xxxl)
                    ) {
                        item(key = "hero") { Hero(detail) }
                        val desc = detail.description
                        if (!desc.isNullOrBlank()) item(key = "synopsis") { Synopsis(desc) }
                        if (detail.cast.isNotEmpty()) item(key = "cast") {
                            Text(
                                "Cast: " + detail.cast.take(6).joinToString(", "),
                                style = AppType.caption,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = Space.lg).padding(top = Space.md)
                            )
                        }
                        if (detail.audioVariants.size > 1) item(key = "audio") {
                            Column(Modifier.padding(top = Space.xl)) {
                                Heading("Audio language")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = Space.lg),
                                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                                ) {
                                    items(detail.audioVariants, key = { it.id }) { v ->
                                        PillChip(label = v.label, selected = v.isCurrent, onClick = { viewModel.selectVariant(v.id) })
                                    }
                                }
                            }
                        }
                        if (detail.type == MediaType.SERIES && detail.seasons.isNotEmpty()) {
                            item(key = "seasons") {
                                Column(Modifier.padding(top = Space.xxl)) {
                                    Heading("Episodes")
                                    if (detail.seasons.size > 1) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = Space.lg),
                                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                            modifier = Modifier.padding(bottom = Space.md)
                                        ) {
                                            items(detail.seasons, key = { it.number }) { s ->
                                                PillChip(
                                                    label = "Season ${s.number}",
                                                    selected = s.number == uiState.selectedSeason,
                                                    onClick = { viewModel.selectSeason(s.number) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            val season = uiState.selectedSeason
                            val count = detail.seasons.firstOrNull { it.number == season }?.episodeCount ?: 0
                            val resume = uiState.resume
                            val rows = (1..count).chunked(TILES_PER_ROW)
                            items(rows, key = { "s$season-${it.first()}" }) { row ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                    modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.sm)
                                ) {
                                    row.forEach { ep ->
                                        EpisodeTile(
                                            number = ep,
                                            next = resume != null && resume.season == season && resume.episode == ep,
                                            enabled = detail.playable,
                                            onClick = { viewModel.play(season, ep) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(barBg).statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(Space.sm).background(if (scrolled) Color.Transparent else Bg.badge)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink.high)
            }
            if (scrolled) {
                Text(detail?.title.orEmpty().uppercase(), style = AppType.heading, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(end = Space.lg))
            }
        }

        if (detail != null && phase == 2) {
            val resume = uiState.resume
            val isSeries = detail.type == MediaType.SERIES
            val firstSeason = detail.seasons.firstOrNull()?.number ?: 1
            val (label, target) = when {
                !detail.playable -> "Not available to stream" to null
                isSeries && resume != null -> "Continue S${resume.season} · E${resume.episode}" to resume
                isSeries -> "Play S$firstSeason · E1" to Resume(firstSeason, 1)
                resume != null -> "Continue" to Resume(0, 0)
                else -> "Play" to Resume(0, 0)
            }
            ActionBar(
                label = label,
                enabled = target != null,
                loading = uiState.resolving,
                isFavorite = uiState.isFavorite,
                onPlay = { if (target != null) viewModel.play(target.season, target.episode) },
                onFavorite = viewModel::toggleFavorite
            )
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BarHeight + Space.xl)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(d: MediaDetail) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(Modifier.fillMaxWidth()) {
        ArtworkImage(
            url = d.backdropUrl ?: d.posterUrl,
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .then(if (canBlur) Modifier.blur(24.dp) else Modifier)
                .graphicsLayer { alpha = if (canBlur) 0.7f else 0.35f }
        )
        Box(Modifier.matchParentSize().background(Bg.scrim))
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.statusBarsPadding().padding(top = BarHeight + Space.lg, start = Space.lg, end = Space.lg)
        ) {
            PosterImage(d.posterUrl, d.title, Modifier.width(120.dp))
            Spacer(Modifier.width(Space.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text(d.title.uppercase(), style = AppType.title, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val rating = d.rating
                if (rating != null && rating > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, contentDescription = null, tint = Accent.star, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text("%.1f".format(rating), style = AppType.numeric.copy(color = Ink.high))
                    }
                }
            }
        }
    }
    val meta = listOfNotNull(
        if (d.type == MediaType.SERIES) "Series" else "Movie",
        d.year?.toString(),
        d.runtime,
        d.country
    ).filter { it.isNotBlank() } + d.genres
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        modifier = Modifier.padding(horizontal = Space.lg).padding(top = Space.lg)
    ) {
        meta.forEach {
            Text(
                it.uppercase(),
                style = AppType.label.copy(color = Ink.body),
                maxLines = 1,
                modifier = Modifier.border(1.dp, Ink.hairline, Radius.sm).padding(horizontal = Space.sm, vertical = Space.xs)
            )
        }
    }
}

@Composable
private fun Synopsis(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = Space.lg)
            .padding(top = Space.xl)
            .animateContentSize(tween(250, easing = FastOutSlowInEasing))
    ) {
        Text(
            text,
            style = AppType.body,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow }
        )
        if (overflows || expanded) {
            Text(
                if (expanded) "LESS" else "MORE",
                style = AppType.label.copy(color = Ink.body),
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = Space.sm)
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), style = AppType.heading, modifier = Modifier.padding(horizontal = Space.lg).padding(bottom = Space.md))
}

@Composable
private fun EpisodeTile(number: Int, next: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(Radius.md)
            .background(Bg.raised)
            .border(1.dp, if (next) Accent.main else Ink.hairline, Radius.md)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Text("$number", style = AppType.bodyStrong.copy(fontFeatureSettings = "tnum", color = if (next) Accent.text else Ink.body))
    }
}

@Composable
private fun BoxScope.ActionBar(
    label: String,
    enabled: Boolean,
    loading: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val heartScale = remember { Animatable(1f) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Bg.base, Bg.base)))
            .navigationBarsPadding()
            .padding(start = Space.lg, end = Space.lg, top = Space.xl, bottom = Space.md)
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            PrimaryButton(
                text = if (loading) "Finding sources…" else label,
                onClick = onPlay,
                enabled = enabled && !loading,
                icon = if (enabled && !loading) Icons.Rounded.PlayArrow else null,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
            if (loading) {
                CircularProgressIndicator(
                    color = Accent.on,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = Space.lg).size(20.dp)
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(Bg.base)
                .border(1.dp, if (isFavorite) Accent.main else Ink.ghost, Radius.pill)
                .clickable {
                    onFavorite()
                    scope.launch {
                        heartScale.animateTo(1.3f, tween(120))
                        heartScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                    }
                }
        ) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favourites" else "Add to favourites",
                tint = if (isFavorite) Accent.main else Ink.body,
                modifier = Modifier.scale(heartScale.value)
            )
        }
    }
}

@Composable
private fun DetailSkeleton() {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .background(Bg.raised.copy(alpha = 0.5f))
                .statusBarsPadding()
                .padding(top = BarHeight + Space.lg, start = Space.lg, end = Space.lg, bottom = Space.lg)
        ) {
            LoadingShimmer(Modifier.width(120.dp).aspectRatio(2f / 3f))
            Spacer(Modifier.width(Space.lg))
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                LoadingShimmer(Modifier.fillMaxWidth().height(28.dp))
                LoadingShimmer(Modifier.fillMaxWidth(0.6f).height(28.dp))
                LoadingShimmer(Modifier.width(80.dp).height(16.dp))
            }
        }
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            repeat(3) { LoadingShimmer(Modifier.fillMaxWidth(if (it == 2) 0.7f else 1f).height(16.dp)) }
        }
    }
}
