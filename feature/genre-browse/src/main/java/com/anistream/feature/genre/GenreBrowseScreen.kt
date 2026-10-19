package com.anistream.feature.genre

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anistream.core.ui.components.AnimeCard
import com.anistream.core.ui.components.EmptyState
import com.anistream.core.ui.components.ErrorContent
import com.anistream.core.ui.components.LoadingShimmer
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.Genre

@Composable
fun GenreBrowseScreen(
    onAnimeClick: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: GenreBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // system back from a genre's list returns to the picker, same as the toolbar arrow
    BackHandler(enabled = uiState.path != null && viewModel.initialPath == null) { viewModel.clearSelection() }

    Column(Modifier.fillMaxSize().background(Bg.base)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Space.xs, end = Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!viewModel.clearSelection()) onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink.high)
            }
            Text(
                (uiState.title ?: "Genres").uppercase(),
                style = AppType.heading,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (uiState.path == null) {
            val error = uiState.error
            val state = when {
                uiState.isLoading -> 0
                error != null -> 1
                uiState.genres.isEmpty() -> 2
                else -> 3
            }
            Crossfade(targetState = state, animationSpec = tween(250), label = "genres_state") { s ->
                when (s) {
                    0 -> TileGrid { items(12) { LoadingShimmer(Modifier.fillMaxWidth().height(72.dp).clip(Radius.md)) } }
                    1 -> ErrorContent(message = error ?: "Failed to load genres", onRetry = viewModel::loadGenres)
                    2 -> EmptyState(message = "No genres found", icon = Icons.Rounded.Category)
                    else -> TileGrid {
                        items(uiState.genres) { genre ->
                            GenreTile(genre, onClick = { viewModel.onGenreClick(genre) })
                        }
                    }
                }
            }
        } else {
            val error = uiState.resultsError
            val state = when {
                uiState.isResultsLoading -> 0
                uiState.results.isEmpty() && error != null -> 1
                uiState.results.isEmpty() -> 2
                else -> 3
            }
            Crossfade(targetState = state, animationSpec = tween(250), label = "results_state") { s ->
                when (s) {
                    0 -> PosterGrid { items(12) { PosterSkeleton() } }
                    1 -> ErrorContent(message = error ?: "Failed to load", onRetry = viewModel::loadNextPage)
                    2 -> EmptyState(message = "No anime found here", icon = Icons.Rounded.Category)
                    else -> ResultsGrid(
                        uiState = uiState,
                        onAnimeClick = onAnimeClick,
                        onLoadMore = viewModel::loadNextPage
                    )
                }
            }
        }
    }
}

@Composable
private fun TileGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.sm, bottom = Space.xxxl),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        content = content
    )
}

/** Raised sharp tile with a hairline; gold 1dp border while pressed (one-accent rule, no hue tints). */
@Composable
private fun GenreTile(genre: Genre, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Bg.raised, Radius.md)
            .border(1.dp, if (pressed) Accent.main else Ink.hairline, Radius.md)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = Space.lg),
        verticalArrangement = Arrangement.Center
    ) {
        Text(genre.name.uppercase(), style = AppType.heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val count = genre.count
        if (count > 0) Text("$count titles", style = AppType.numeric)
    }
}

@Composable
private fun PosterGrid(
    state: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.sm, bottom = Space.xxxl),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
        content = content
    )
}

@Composable
private fun ResultsGrid(uiState: GenreBrowseUiState, onAnimeClick: (String) -> Unit, onLoadMore: () -> Unit) {
    val state = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = state.layoutInfo.totalItemsCount
            total > 0 && last >= total - 6
        }
    }
    LaunchedEffect(nearEnd, uiState.results.size) { if (nearEnd) onLoadMore() }

    PosterGrid(state) {
        items(uiState.results, key = { it.slug }) { anime ->
            AnimeCard(anime = anime, onClick = { onAnimeClick(anime.slug) }, modifier = Modifier.fillMaxWidth())
        }
        if (uiState.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(Space.lg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent.main, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun PosterSkeleton() {
    Column {
        LoadingShimmer(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(Radius.md))
        Spacer(Modifier.height(Space.sm))
        LoadingShimmer(Modifier.fillMaxWidth(0.8f).height(12.dp).clip(Radius.sm))
    }
}
