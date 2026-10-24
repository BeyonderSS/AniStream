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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.DisplayHeavy
import com.anistream.core.ui.theme.Ink
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
                                val onF: (TvItem) -> Unit = {
                                    focused.value = it; lastFocused = it.key; focusedRow.intValue = i
                                }
                            if (row.wide) TvStripRow(row, restoreKey, restore, onF) else TvCardRow(row, restoreKey, restore, onF)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Radial glow behind the hero, tinted with the focused poster's colour (static dusk violet on low-end). */
@Composable
private fun HomeBackdrop(hero: State<TvItem?>) {
    val lowEnd = LocalLowEnd.current
    val c = if (lowEnd) null else rememberPosterColor(hero.value?.imageUrl).value
    val glow = lerp(Bg.base, c ?: DuskViolet, 0.5f)
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.radialGradient(listOf(glow, Bg.base), center = Offset(size.width * 0.75f, size.height * 0.25f), radius = size.width * 0.7f))
    })
}

/** Marquee hero: huge title (solid lead, outlined remainder), tilted whole poster, chips and actions. */
@Composable
private fun HomeHero(hero: State<TvItem?>, filter: HomeFilter) {
    val s = uiScale()
    fun d(n: Float) = (n * s).dp
    val h = hero.value
    Box(Modifier.fillMaxWidth().height(d(250f))) {
        if (h?.imageUrl != null) AsyncImage(
            h.imageUrl, null, contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = d(30f), end = Overscan + d(16f)).width(d(134f)).aspectRatio(2f / 3f)
                .graphicsLayer { rotationZ = -3f }.background(Bg.raised, RoundedCornerShape(d(8f))).clip(RoundedCornerShape(d(8f)))
        )
        Column(Modifier.padding(start = Overscan, end = d(240f), top = d(28f))) {
            val title = h?.title ?: when (filter) { HomeFilter.ALL -> "Reelm"; HomeFilter.MEDIA -> "Movies & TV"; HomeFilter.ANIME -> "Anime" }
            val cut = listOf(":", " - ").map { title.indexOf(it) }.filter { it > 0 }.minOrNull() ?: title.length
            val big = title.length <= 30
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Ink.high)) { append(title.substring(0, cut)) }
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.5f), drawStyle = Stroke(2.5f))) { append(title.substring(cut)) }
                },
                maxLines = if (big) 2 else 3, overflow = TextOverflow.Ellipsis,
                style = TvType.display.copy(fontFamily = DisplayHeavy, fontWeight = FontWeight.ExtraBold, fontSize = if (big) (52*s).sp else (38*s).sp, lineHeight = if (big) (54*s).sp else (40*s).sp),
            )
        }
        if (h != null) Column(Modifier.align(Alignment.BottomStart).padding(start = Overscan, bottom = d(6f)), verticalArrangement = Arrangement.spacedBy(d(8f))) {
            MetaChips(h.meta.distinct(), maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(d(14f)), verticalAlignment = Alignment.CenterVertically) {
                TvPill(h.resumeLabel ?: "Play", h.onClick, icon = Icons.Filled.PlayArrow)
                h.onDetails?.let { TvPill("Details", it) }
            }
        }
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
        resumeLabel = h.lastEpisodeWatched.takeIf { it > 0 }?.let { "Resume episode ${if (h.watchProgress >= 0.9f && !h.isComplete) h.nextEpisode else it}" } ?: "Resume",
        onDetails = { nav.navigate(TvRoutes.detail(if (h.kind == "media") h.mediaId ?: h.animeSlug.substringBefore(":s") else h.animeSlug)) },
        episodeLine = when {
            h.kind == "media" && h.lastEpisodeWatched == 0 -> "Movie"
            h.kind == "media" -> "Season ${h.season.coerceAtLeast(1)}, episode ${h.lastEpisodeWatched}"
            h.totalEpisodes > 0 -> "Episode ${h.lastEpisodeWatched} of ${h.totalEpisodes}"
            else -> "Episode ${h.lastEpisodeWatched}"
        },
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
