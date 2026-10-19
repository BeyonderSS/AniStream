package com.anistream.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anistream.core.ui.components.AnimeCarousel
import com.anistream.core.ui.components.ArtworkImage
import com.anistream.core.ui.components.ContinueWatchingCard
import com.anistream.core.ui.components.EmptyState
import com.anistream.core.ui.components.ErrorContent
import com.anistream.core.ui.components.LoadingShimmer
import com.anistream.core.ui.components.PosterRowSkeleton
import com.anistream.core.ui.components.PrimaryButton
import com.anistream.core.ui.components.SecondaryButton
import com.anistream.core.ui.components.SectionHeader
import com.anistream.core.ui.components.resumeEpisode
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.AnimeSearchResult
import kotlinx.coroutines.delay

private const val HERO_COUNT = 8
private const val HERO_INTERVAL_MS = 6_000L

private enum class HomeMode { Loading, Error, Empty, Content }

/**
 * @param onSeeAllClick receives a hianime category path ("top-airing", "recently-updated", ...)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAnimeClick: (String) -> Unit,
    onSeeAllClick: (String) -> Unit,
    onPlayEpisode: (String, Int) -> Unit,
    onGenresClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshContinueWatching()
        onPauseOrDispose { }
    }

    val c = uiState.content
    val hero = c.trending.distinctBy { it.slug }.take(HERO_COUNT)
    // (title, items, see-all path or null). Trending lives in the hero; leftovers get a row.
    val rows: List<Triple<String, List<AnimeSearchResult>, String?>> = listOf(
        Triple("Trending Now", c.trending.drop(HERO_COUNT), null),
        Triple("Latest Episodes", c.recentEpisodes, "recently-updated"),
        Triple("Top Airing", c.topAiring, "top-airing"),
        Triple("Most Popular", c.popular, "most-popular"),
        Triple("Most Favorite", c.favorites, "most-favorite"),
        Triple("Latest Completed", c.completed, "latest-completed")
    ).filter { it.second.isNotEmpty() }

    val hasContent = hero.isNotEmpty() || rows.isNotEmpty()
    val error = uiState.error
    val mode = when {
        hasContent -> HomeMode.Content
        uiState.isLoading -> HomeMode.Loading
        error != null -> HomeMode.Error
        else -> HomeMode.Empty
    }

    val listState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState()
    val refreshing = uiState.isLoading && hasContent

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg.base)
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.loadContent() },
            state = refreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = refreshing,
                    containerColor = Bg.overlay,
                    color = Accent.main,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 56.dp)
                )
            }
        ) {
            Crossfade(targetState = mode, animationSpec = tween(250), label = "homeMode") { m ->
                when (m) {
                    HomeMode.Loading -> HomeSkeleton()
                    HomeMode.Error -> ErrorContent(message = error ?: "", onRetry = { viewModel.loadContent() })
                    HomeMode.Empty -> EmptyState(
                        message = "Nothing to show yet",
                        icon = Icons.Rounded.LiveTv,
                        actionLabel = "Refresh",
                        onAction = { viewModel.loadContent() }
                    )
                    HomeMode.Content -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Space.xl)
                    ) {
                        if (hero.isNotEmpty()) {
                            item(key = "hero") {
                                HeroPager(
                                    items = hero,
                                    onWatch = { onPlayEpisode(it.slug, 1) },
                                    onDetails = { onAnimeClick(it.slug) }
                                )
                            }
                        } else {
                            item(key = "topSpace") { TopBarSpacer() }
                        }

                        if (uiState.continueWatching.isNotEmpty()) {
                            item(key = "cwHeader") {
                                Spacer(Modifier.height(Space.lg))
                                SectionHeader(title = "Continue Watching")
                            }
                            item(key = "cwRow") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = Space.lg),
                                    horizontalArrangement = Arrangement.spacedBy(Space.md)
                                ) {
                                    items(uiState.continueWatching, key = { it.animeSlug }) { history ->
                                        ContinueWatchingCard(
                                            history = history,
                                            onClick = { onPlayEpisode(history.animeSlug, history.resumeEpisode()) },
                                            onPlayClick = { onPlayEpisode(history.animeSlug, history.resumeEpisode()) }
                                        )
                                    }
                                }
                            }
                        }

                        rows.forEach { (title, list, path) ->
                            item(key = title) {
                                Spacer(Modifier.height(Space.xl))
                                SectionHeader(
                                    title = title,
                                    onSeeAll = path?.let { p -> { onSeeAllClick(p) } }
                                )
                                AnimeCarousel(items = list, onAnimeClick = { onAnimeClick(it.slug) })
                            }
                        }
                    }
                }
            }
        }

        // Transparent over the hero, turns solid once the hero scrolls away.
        val solid by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 120 }
        }
        HomeTopBar(
            solid = solid || mode != HomeMode.Content,
            onSearchClick = onSearchClick,
            onGenresClick = onGenresClick
        )
    }
}

@Composable
private fun HomeTopBar(solid: Boolean, onSearchClick: () -> Unit, onGenresClick: () -> Unit) {
    val bg by animateColorAsState(if (solid) Bg.bar else Color.Transparent, tween(250, easing = FastOutSlowInEasing), label = "topBarBg")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg.scrimTop)
            .background(bg)
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = Space.lg, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "ANISTREAM",
            style = AppType.heading.copy(letterSpacing = 2.sp),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSearchClick) {
            Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Ink.high)
        }
        IconButton(onClick = onGenresClick) {
            Icon(Icons.Outlined.Category, contentDescription = "Browse genres", tint = Ink.high)
        }
    }
}

/** Reserves space for the overlaid top bar when there is no hero. */
@Composable
private fun TopBarSpacer() {
    Column {
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(Modifier.height(56.dp))
    }
}

@Composable
private fun HeroPager(
    items: List<AnimeSearchResult>,
    onWatch: (AnimeSearchResult) -> Unit,
    onDetails: (AnimeSearchResult) -> Unit
) {
    val pagerState = rememberPagerState { items.size }

    // Auto-advance every 6s; restarts whenever the user swipes or a page settles.
    LaunchedEffect(pagerState.settledPage, pagerState.isScrollInProgress, items.size) {
        if (items.size < 2 || pagerState.isScrollInProgress) return@LaunchedEffect
        delay(HERO_INTERVAL_MS)
        pagerState.animateScrollToPage(
            (pagerState.settledPage + 1) % items.size,
            animationSpec = tween(600, easing = FastOutSlowInEasing)
        )
    }

    Box {
        HorizontalPager(state = pagerState, key = { items[it].slug }) { page ->
            HeroPage(items[page], onWatch = { onWatch(items[page]) }, onDetails = { onDetails(items[page]) })
        }
        PageDots(
            pagerState = pagerState,
            count = items.size,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Space.sm)
        )
    }
}

@Composable
private fun HeroPage(anime: AnimeSearchResult, onWatch: () -> Unit, onDetails: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clickable(onClick = onDetails)
    ) {
        ArtworkImage(
            url = anime.posterUrl,
            contentDescription = anime.title,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Bg.scrimTop)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .align(Alignment.BottomCenter)
                .background(Bg.scrim)
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.lg, bottom = Space.xl + Space.sm)
        ) {
            val meta = listOfNotNull(
                anime.type?.takeIf { it.isNotBlank() },
                anime.year?.toString(),
                anime.status?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta.uppercase(), style = AppType.label.copy(color = Ink.body), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Space.xs))
            }
            Text(anime.title.uppercase(), style = AppType.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PrimaryButton(text = "Watch", onClick = onWatch, icon = Icons.Rounded.PlayArrow)
                SecondaryButton(text = "Details", onClick = onDetails, icon = Icons.Outlined.Info)
            }
        }
    }
}

@Composable
private fun PageDots(pagerState: PagerState, count: Int, modifier: Modifier = Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = pagerState.currentPage == i
            val width by animateDpAsState(if (selected) 24.dp else 12.dp, tween(250, easing = FastOutSlowInEasing), label = "dash")
            Box(
                Modifier
                    .height(2.dp)
                    .width(width)
                    .background(if (selected) Accent.main else Ink.faint)
            )
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize()) {
        LoadingShimmer(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
        )
        Spacer(Modifier.height(Space.xl))
        PosterRowSkeleton()
        Spacer(Modifier.height(Space.xl))
        PosterRowSkeleton()
    }
}
