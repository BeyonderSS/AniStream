package com.anistream.feature.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.tv.material3.Text
import com.anistream.domain.model.AnimeSearchResult
import com.anistream.domain.model.MediaItem
import com.anistream.domain.model.MediaType
import com.anistream.domain.model.WatchHistory
import com.anistream.feature.home.HomeViewModel
import com.anistream.feature.movies.MoviesHomeViewModel
import com.anistream.feature.movies.asMediaItem
import com.anistream.domain.model.forYouRows

enum class HomeFilter { ALL, MEDIA, ANIME }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvHomeScreen(filter: HomeFilter, homeVm: HomeViewModel, moviesVm: MoviesHomeViewModel, nav: NavController) {
    val anime by homeVm.uiState.collectAsStateWithLifecycle()
    val media by moviesVm.uiState.collectAsStateWithLifecycle()

    // re-entering this destination (back from detail/player) picks up fresh progress
    LaunchedEffect(Unit) {
        homeVm.refreshContinueWatching()
        moviesVm.refreshContinueWatching()
    }

    val lowEnd = LocalLowEnd.current
    // ponytail: rows rebuild only when the data changes, not on every D-pad move
    var removing by remember { mutableStateOf<WatchHistory?>(null) }
    val rows = remember(anime, media, filter, lowEnd) { buildRows(filter, anime, media, lowEnd, nav) { removing = it } }
    removing?.let { h ->
        TvConfirm("Remove ${h.animeTitle} from history?", "Remove", {
            (if (h.kind == "media") moviesVm::removeHistory else homeVm::removeHistory)(h.animeSlug); removing = null
        }, { removing = null })
    }

    // Focus is written here but read only by the backdrop/hero composables below, so a D-pad move
    // recomposes those two and the two cards whose focus changed, never the whole screen.
    val focused = remember { mutableStateOf<TvItem?>(null) }
    val hero = remember { mutableStateOf<TvItem?>(null) }
    LaunchedEffect(lowEnd) {
        // low-end: change hero/backdrop only once focus has rested 300 ms; further moves cancel the load
        snapshotFlow { focused.value }.collectLatest { if (lowEnd && hero.value != null) delay(300); hero.value = it }
    }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    val restore = remember { FocusRequester() }
    // read without observing: only matters when the screen is (re)built, and writing it on each move must not recompose this
    val last = Snapshot.withoutReadObservation { lastFocused }
    val restoreKey = last?.takeIf { k -> rows.any { r -> r.items.any { it.key == k } } } ?: rows.firstOrNull()?.items?.firstOrNull()?.key
    val hasRows = rows.isNotEmpty()
    LaunchedEffect(hasRows) {
        if (hasRows) {
            withFrameNanos { }
            runCatching { restore.requestFocus() }
        }
    }

    // The focused row snaps to the top of the rows area (never a half-cut row under the hero):
    // the column's own bring-into-view is turned off and we scroll by row index instead.
    val listState = rememberLazyListState()
    val focusedRow = remember { mutableIntStateOf(-1) }
    // everything but Continue watching
    val pool = remember(rows) { rows.filterNot { it.wide }.flatMap { it.items } }
    val lead = if (pool.isNotEmpty()) 1 else 0
    LaunchedEffect(lead) {
        snapshotFlow { focusedRow.intValue }.collectLatest { i ->
            if (i >= 0) listState.animateScrollToItem(if (i == 0) 0 else i + lead)
        }
    }
    val rowSpec = LocalBringIntoViewSpec.current

    Box(Modifier.fillMaxSize()) {
        HomeBackdrop(hero)
        Column(Modifier.fillMaxSize()) {
            HomeHero(hero, filter)
            when {
                rows.isEmpty() && (anime.isLoading || media.isLoading) -> TvMessage("Loading…")
                rows.isEmpty() -> TvMessage(anime.error ?: media.error ?: "Nothing here yet") {
                    TvPill("Retry", onClick = { homeVm.loadContent(); moviesVm.load() })
                }
                else -> CompositionLocalProvider(LocalBringIntoViewSpec provides NoVerticalBringIntoView) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.clipToBounds(),
                        contentPadding = PaddingValues(bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (pool.isNotEmpty()) item(key = "surprise") {
                            TvPill("Surprise me", onClick = { pool.random().onClick() }, modifier = Modifier.padding(start = Overscan))
                        }
                        itemsIndexed(rows, key = { _, it -> it.title }) { i, row ->
                            // rows keep the default horizontal scrolling
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                                TvCardRow(row, restoreKey, restore, onFocused = {
                                    focused.value = it; lastFocused = it.key; focusedRow.intValue = i
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeBackdrop(hero: State<TvItem?>) = ProjectorBackdrop(hero.value?.imageUrl, portrait = true) // list models carry posters only

@Composable
private fun HomeHero(hero: State<TvItem?>, filter: HomeFilter) {
    Column(Modifier.fillMaxWidth().padding(start = Overscan, end = Overscan, top = 36.dp).height(176.dp)) {
        val h = hero.value
        Text(
            h?.title ?: when (filter) { HomeFilter.ALL -> "Reelm"; HomeFilter.MEDIA -> "Movies & TV"; HomeFilter.ANIME -> "Anime" },
            style = TvType.display, maxLines = 2, overflow = TextOverflow.Ellipsis,
            // keep clear of the right-aligned hero poster (230dp tall at 2:3 ≈ 153dp wide, plus a gap)
            modifier = Modifier.padding(end = 190.dp)
        )
        Spacer(Modifier.height(12.dp))
        if (h != null) MetaChips(h.meta.distinct(), maxLines = 1)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val NoVerticalBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

private fun buildRows(
    filter: HomeFilter,
    anime: com.anistream.feature.home.HomeUiState,
    media: com.anistream.feature.movies.MoviesHomeUiState,
    lowEnd: Boolean,
    nav: NavController,
    onRemove: (WatchHistory) -> Unit
): List<TvRow> {
    fun animeItem(row: String, a: AnimeSearchResult) = TvItem(
        key = "$row|${a.slug}", title = a.title, imageUrl = a.posterUrl,
        meta = listOfNotNull(a.type, a.year?.toString(), scoreLabel(a.score)),
        onClick = { nav.navigate(TvRoutes.anime(a.slug)) }
    )

    fun mediaItem(row: String, m: MediaItem) = TvItem(
        key = "$row|${m.id}", title = m.title, imageUrl = m.posterUrl,
        meta = listOfNotNull(if (m.type == MediaType.SERIES) "Series" else "Movie", m.year?.toString(), scoreLabel(m.rating)) + m.genres.take(2),
        onClick = { nav.navigate(TvRoutes.movie(m.id)) }
    )

    fun historyItem(h: WatchHistory) = TvItem(
        key = "cw|${h.animeSlug}", title = h.animeTitle, imageUrl = h.posterUrl,
        subtitle = h.subtitle ?: h.lastEpisodeWatched.takeIf { it > 0 }?.let { "Episode $it" },
        meta = listOfNotNull(if (h.kind == "media") "Movies & TV" else "Anime", h.subtitle),
        progress = h.watchProgress,
        onLongClick = { onRemove(h) },
        // anime resumes straight into the player; media goes through detail, where Resume is the first focus
        onClick = {
            if (h.kind == "media") nav.navigate(TvRoutes.movie(h.mediaId ?: h.animeSlug.substringBefore(":s")))
            else nav.navigate(TvRoutes.player(h.animeSlug, if (h.watchProgress >= 0.9f && !h.isComplete) h.nextEpisode else h.lastEpisodeWatched.coerceAtLeast(1)))
        }
    )

    val c = anime.content
    val continueRow = when (filter) {
        HomeFilter.ALL -> anime.continueWatching + media.continueWatching
        HomeFilter.MEDIA -> media.continueWatching
        HomeFilter.ANIME -> anime.continueWatching
    }.sortedByDescending { it.lastWatchedTimestamp }.distinctBy { it.animeSlug }.map(::historyItem) // anime list already holds media rows too

    fun animeRow(title: String, list: List<AnimeSearchResult>) = TvRow(title, list.distinctBy { it.slug }.map { animeItem(title, it) })
    val mediaRows = media.rows.map { r -> TvRow(r.title, r.items.distinctBy { it.id }.map { mediaItem(r.title, it) }) }
    val animeRows = listOf(
        animeRow("Top airing anime", c.topAiring),
        animeRow("Most popular anime", c.popular),
        animeRow("New episodes", c.recentEpisodes),
        animeRow("Fan favourites", c.favorites),
        animeRow("Completed anime", c.completed)
    )
    // "For you" rows exist only once there is history; TMDB rows carry the attribution TMDB's terms ask for
    val mediaPicks = media.picks.forYouRows().mapIndexed { i, (t, l) ->
        TvRow(t, l.map { mediaItem(t, it.asMediaItem()) }, caption = "Data from TMDB".takeIf { i == 0 })
    }
    val animePicks = anime.picks.forYouRows().map { (t, l) ->
        val title = if (filter == HomeFilter.ALL && t == "Top picks for you") "Top anime picks for you" else t
        animeRow(title, l.map { AnimeSearchResult(slug = it.id, title = it.title, posterUrl = it.posterUrl, score = it.rating, year = it.year) })
    }
    return buildList {
        add(TvRow("Continue watching", continueRow, wide = true))
        when (filter) {
            HomeFilter.ALL -> { addAll(mediaPicks); addAll(animePicks); add(animeRow("Trending anime", c.trending)); addAll(mediaRows); addAll(animeRows) }
            HomeFilter.MEDIA -> { addAll(mediaPicks); addAll(mediaRows) }
            HomeFilter.ANIME -> { addAll(animePicks); add(animeRow("Trending anime", c.trending)); addAll(animeRows) }
        }
    }.filter { it.items.isNotEmpty() }.distinctBy { it.title }
        // low-end: fewer rows keeps composition and image memory down
        .let { if (lowEnd) it.take(LOW_END_ROWS) else it }
}

private const val LOW_END_ROWS = 8
