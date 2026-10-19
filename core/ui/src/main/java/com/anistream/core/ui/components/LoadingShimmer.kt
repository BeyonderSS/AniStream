package com.anistream.core.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space

/** A shimmering block. Give it a size and (optionally) a clip shape. */
@Composable
fun LoadingShimmer(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        colors = listOf(Bg.raised, Bg.overlay, Bg.raised),
        start = Offset(x, 0f),
        end = Offset(x + 600f, 300f)
    )
    Box(modifier = modifier.background(brush))
}

@Composable
private fun Bone(modifier: Modifier, shape: Shape = Radius.sm) = LoadingShimmer(modifier.clip(shape))

/** Skeleton of an [AnimeCard]: 2:3 poster + two caption lines. Width comes from [modifier] (default 140dp like AnimeCard). */
@Composable
fun PosterSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.width(140.dp)) {
        Bone(Modifier.fillMaxWidth().aspectRatio(2f / 3f), Radius.md)
        Spacer(Modifier.height(Space.sm))
        Bone(Modifier.fillMaxWidth(0.9f).height(12.dp))
        Spacer(Modifier.height(Space.xs))
        Bone(Modifier.fillMaxWidth(0.6f).height(12.dp))
    }
}

/** Skeleton of a section: header bar + a row of posters. */
@Composable
fun PosterRowSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Bone(Modifier.padding(horizontal = Space.lg).width(160.dp).height(20.dp))
        Spacer(Modifier.height(Space.md))
        Row(
            modifier = Modifier.padding(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            repeat(4) { PosterSkeleton() }
        }
    }
}

/** Skeleton of a list row (thumbnail + two text lines), e.g. downloads, schedule, horizontal cards. */
@Composable
fun RowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.md)
    ) {
        Bone(Modifier.size(width = 64.dp, height = 96.dp), Radius.md)
        Column(Modifier.weight(1f).padding(top = Space.sm)) {
            Bone(Modifier.fillMaxWidth(0.8f).height(14.dp))
            Spacer(Modifier.height(Space.sm))
            Bone(Modifier.fillMaxWidth(0.5f).height(12.dp))
        }
    }
}

/** Skeleton of an [AnimeGrid]: a 3-column poster grid. */
@Composable
fun PosterGridSkeleton(modifier: Modifier = Modifier, count: Int = 12) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridMinCell),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md)
    ) {
        items(count) { PosterSkeleton(Modifier.fillMaxWidth()) }
    }
}
