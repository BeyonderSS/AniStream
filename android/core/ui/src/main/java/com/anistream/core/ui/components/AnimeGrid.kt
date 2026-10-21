package com.anistream.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.AnimeSearchResult

/** ~3 columns on a phone. */
internal val GridMinCell = 104.dp

/** Poster grid that calls [onLoadMore] when the user scrolls within a few items of the end. */
@Composable
fun AnimeGrid(
    items: List<AnimeSearchResult>,
    onAnimeClick: (AnimeSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    val state = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = state.layoutInfo.totalItemsCount
            total > 0 && last >= total - 6
        }
    }
    if (onLoadMore != null) {
        LaunchedEffect(nearEnd, items.size) {
            if (nearEnd) onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridMinCell),
        state = state,
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.md, bottom = Space.xl)
    ) {
        items(items.distinctBy { it.slug }, key = { it.slug }) { anime ->
            AnimeCard(anime = anime, onClick = { onAnimeClick(anime) }, modifier = Modifier.fillMaxWidth())
        }
        if (isLoadingMore) {
            items(3, key = { "skeleton-$it" }) { PosterSkeleton(Modifier.fillMaxWidth()) }
        }
    }
}
