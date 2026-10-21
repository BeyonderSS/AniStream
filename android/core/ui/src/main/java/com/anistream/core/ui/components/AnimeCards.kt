package com.anistream.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.core.ui.theme.Motion
import com.anistream.domain.model.AnimeSearchResult
import com.anistream.domain.model.WatchHistory

/** 2:3 poster, 12dp corners, faint mist edge, placeholder + crossfade. */
@Composable
fun PosterImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    ArtworkImage(
        url = url,
        contentDescription = contentDescription,
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(Radius.md)
            .border(1.dp, Ink.hairline, Radius.md)
    )
}

/** Poster card: 2:3 artwork, type pill, score, 2-line title. Width 140dp unless [modifier] overrides (e.g. fillMaxWidth in grids). */
@Composable
fun AnimeCard(
    anime: AnimeSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clip(Radius.md)
            .pressClickable(onLongClick = onLongClick, onClick = onClick)
    ) {
        Box {
            PosterImage(anime.posterUrl, anime.title, Modifier.fillMaxWidth())
            val type = anime.type
            if (!type.isNullOrBlank()) {
                TypeBadge(type, Modifier.align(Alignment.TopStart).padding(Space.sm))
            }
            val score = anime.score
            if (score != null && score > 0) {
                ScoreBadge(score, Modifier.align(Alignment.BottomStart).padding(Space.sm))
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            text = anime.title,
            style = AppType.captionStrong,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Full-width list card: small poster + title + spaced meta (type, score, status). */
@Composable
fun AnimeCardHorizontal(
    anime: AnimeSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.lg)
            .background(Bg.raised)
            .pressClickable(onClick = onClick)
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterImage(anime.posterUrl, anime.title, Modifier.width(64.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs)
        ) {
            Text(
                text = anime.title,
                style = AppType.bodyStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val type = anime.type
                if (!type.isNullOrBlank()) Text(type, style = AppType.caption)
                val score = anime.score
                if (score != null && score > 0) ScoreBadge(score, plain = true)
                val status = anime.status
                if (!status.isNullOrBlank()) {
                    Text(status, style = AppType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 16:9 resume card: artwork crop, play overlay, amber progress along the bottom edge, "Ep N" caption. */
@Composable
fun ContinueWatchingCard(
    history: WatchHistory,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = history.watchProgress.coerceIn(0f, 1f),
        animationSpec = Motion.standard(),
        label = "progress"
    )

    Column(
        modifier = modifier
            .width(240.dp)
            .clip(Radius.md)
            .pressClickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(Radius.md)
                .border(1.dp, Ink.hairline, Radius.md)
        ) {
            ArtworkImage(
                url = history.posterUrl,
                contentDescription = history.animeTitle,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.BottomCenter)
                    .background(Bg.scrim)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clip(Radius.pill)
                    .background(Bg.badge)
                    .pressClickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Ink.high, modifier = Modifier.size(28.dp))
            }
            Text(
                text = "Ep ${history.resumeEpisode()}",
                style = AppType.numeric.copy(color = Ink.high),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Space.sm, bottom = Space.sm + 3.dp)
            )
            // progress along the bottom edge
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Ink.ghost)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Accent.main)
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            text = history.animeTitle,
            style = AppType.captionStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Section title in AppType.heading with an optional quiet, non-wrapping "See all". */
@Composable
fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = Space.lg, end = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = AppType.heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            Row(
                modifier = Modifier
                    .clip(Radius.pill)
                    .pressClickable(role = Role.Button, onClick = onSeeAll)
                    .heightIn(min = 48.dp)
                    .padding(start = Space.md, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("See all", style = AppType.label.copy(color = Ink.body), maxLines = 1, softWrap = false)
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Ink.body,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AnimeCarousel(
    items: List<AnimeSearchResult>,
    onAnimeClick: (AnimeSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        items(items.distinctBy { it.slug }, key = { it.slug }) { anime ->
            AnimeCard(anime = anime, onClick = { onAnimeClick(anime) })
        }
    }
}

/** Episode to resume: the last one if it wasn't finished (<90%), otherwise the next. */
fun WatchHistory.resumeEpisode(): Int {
    if (lastEpisodeWatched <= 0) return 1
    if (watchProgress < 0.9f) return lastEpisodeWatched
    return if (totalEpisodes > 0) minOf(nextEpisode, totalEpisodes) else nextEpisode
}
