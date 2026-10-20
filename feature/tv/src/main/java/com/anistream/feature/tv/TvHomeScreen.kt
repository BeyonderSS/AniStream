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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
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

enum class HomeFilter { ALL, MEDIA, ANIME }

@Composable
fun TvHomeScreen(filter: HomeFilter, homeVm: HomeViewModel, moviesVm: MoviesHomeViewModel, nav: NavController) {
    val anime by homeVm.uiState.collectAsStateWithLifecycle()
    val media by moviesVm.uiState.collectAsStateWithLifecycle()

    // re-entering this destination (back from detail/player) picks up fresh progress
    LaunchedEffect(Unit) {
        homeVm.refreshContinueWatching()
        moviesVm.refreshContinueWatching()
    }

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
    }.sortedByDescending { it.lastWatchedTimestamp }.map(::historyItem)

    fun animeRow(title: String, list: List<AnimeSearchResult>) = TvRow(title, list.distinctBy { it.slug }.map { animeItem(title, it) })
    val mediaRows = media.rows.map { r -> TvRow(r.title, r.items.distinctBy { it.id }.map { mediaItem(r.title, it) }) }
    val animeRows = listOf(
        animeRow("Top airing anime", c.topAiring),
        animeRow("Most popular anime", c.popular),
        animeRow("New episodes", c.recentEpisodes),
        animeRow("Fan favourites", c.favorites),
        animeRow("Completed anime", c.completed)
    )
    val rows = buildList {
        add(TvRow("Continue watching", continueRow, wide = true))
        when (filter) {
            HomeFilter.ALL -> { add(animeRow("Trending anime", c.trending)); addAll(mediaRows); addAll(animeRows) }
            HomeFilter.MEDIA -> addAll(mediaRows)
            HomeFilter.ANIME -> { add(animeRow("Trending anime", c.trending)); addAll(animeRows) }
        }
    }.filter { it.items.isNotEmpty() }.distinctBy { it.title }

    var hero by remember { mutableStateOf<TvItem?>(null) }
    var lastFocused by rememberSaveable { mutableStateOf<String?>(null) }
    val restore = remember { FocusRequester() }
    val restoreKey = lastFocused?.takeIf { k -> rows.any { r -> r.items.any { it.key == k } } } ?: rows.firstOrNull()?.items?.firstOrNull()?.key
    val hasRows = rows.isNotEmpty()
    LaunchedEffect(hasRows) {
        if (hasRows) {
            withFrameNanos { }
            runCatching { restore.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ProjectorBackdrop(hero?.imageUrl)
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().padding(start = Overscan, end = Overscan, top = 36.dp).height(176.dp)) {
                val h = hero
                Text(
                    h?.title ?: when (filter) { HomeFilter.ALL -> "Reelm"; HomeFilter.MEDIA -> "Movies & TV"; HomeFilter.ANIME -> "Anime" },
                    style = TvType.display, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                if (h != null) MetaChips(h.meta.distinct())
            }
            when {
                rows.isEmpty() && (anime.isLoading || media.isLoading) -> TvMessage("Loading…")
                rows.isEmpty() -> TvMessage(anime.error ?: media.error ?: "Nothing here yet") {
                    TvPill("Retry", onClick = { homeVm.loadContent(); moviesVm.load() })
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(rows, key = { it.title }) { row ->
                        TvCardRow(row, restoreKey, restore, onFocused = { hero = it; lastFocused = it.key })
                    }
                }
            }
        }
    }
}
