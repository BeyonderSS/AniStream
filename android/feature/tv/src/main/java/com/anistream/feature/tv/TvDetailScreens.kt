package com.anistream.feature.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.anistream.core.ui.theme.Accent
import com.anistream.domain.model.Eps
import com.anistream.domain.model.MediaType
import com.anistream.feature.anime.AnimeDetailViewModel
import com.anistream.feature.movies.DetailEvent
import com.anistream.feature.movies.MovieDetailViewModel
import com.anistream.feature.movies.Resume
import kotlinx.coroutines.delay

/**
 * Backdrop + left column (title, chips, synopsis ≤ 4 lines, actions) + focusable rows below, all in one
 * LazyColumn so D-pad focus scrolls it. Chips and actions wrap (FlowRow) instead of overflowing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailLayout(
    backdrop: String?,
    portrait: Boolean,
    title: String,
    meta: List<String>,
    synopsis: String?,
    note: String?,
    actions: @Composable () -> Unit,
    rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        ProjectorBackdrop(backdrop, portrait = portrait)
        LazyColumn(contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            item {
                Column(Modifier.padding(horizontal = Overscan).fillMaxWidth(0.7f)) {
                    Text(title, style = TvType.headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    MetaChips(meta)
                    if (!synopsis.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(synopsis, style = TvType.body, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                    if (note != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(note, style = TvType.body.copy(color = Accent.main))
                    }
                    Spacer(Modifier.height(24.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
                }
            }
            rows()
        }
    }
}

/** Horizontal row of pills with per-row focus restoration; [initial] is composed on entry so it can take focus. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun <T> PillRow(
    title: String?,
    items: List<T>,
    label: (T) -> String,
    selected: (T) -> Boolean,
    onClick: (T) -> Unit,
    initial: Int = 0,
    firstFocus: FocusRequester? = null,
    inset: androidx.compose.ui.unit.Dp = Overscan
) {
    Column {
        if (title != null) Text(title, style = TvType.title, modifier = Modifier.padding(start = inset, bottom = 8.dp))
        val state = rememberLazyListState(initialFirstVisibleItemIndex = initial.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        LazyRow(
            state = state,
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = inset, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items.size) { i ->
                val it = items[i]
                TvPill(
                    label(it), onClick = { onClick(it) }, selected = selected(it),
                    modifier = if (i == initial && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
                )
            }
        }
    }
}

@Composable
private fun RequestOnce(key: Any, requester: FocusRequester) {
    LaunchedEffect(key) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

@Composable
fun TvAnimeDetailScreen(onPlay: (slug: String, episode: Int) -> Unit, onOpen: (slug: String) -> Unit, vm: AnimeDetailViewModel = hiltViewModel()) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshHistory() }
    val a = s.anime
    if (a == null) {
        if (s.isLoading) TvMessage("Loading…") else TvMessage(s.error ?: "Couldn't load this title") { TvPill("Retry", vm::loadAnimeDetail) }
        return
    }
    val episodes = s.episodes
    val h = s.history
    val resumeEp = when {
        h == null || h.lastEpisodeWatched == 0 -> episodes.firstOrNull()?.number
        h.watchProgress >= 0.9f -> episodes.firstOrNull { it.number > h.lastEpisodeWatched }?.number ?: h.lastEpisodeWatched
        else -> h.lastEpisodeWatched
    }
    val playFocus = remember { FocusRequester() }
    RequestOnce(a.slug, playFocus)

    DetailLayout(
        backdrop = a.bannerUrl ?: a.posterUrl,
        portrait = a.bannerUrl == null,
        title = a.title,
        meta = listOfNotNull(a.type, a.year?.toString(), a.status, scoreLabel(a.score), (a.episodes ?: episodes.size.takeIf { it > 0 })?.let { "$it episodes" }),
        synopsis = a.synopsis,
        note = s.episodesError,
        actions = {
            TvPill(
                text = when {
                    resumeEp == null -> "No episodes"
                    h != null && h.lastEpisodeWatched > 0 -> "Resume episode $resumeEp"
                    else -> "Play episode $resumeEp"
                },
                onClick = { resumeEp?.let { onPlay(vm.slug, it) } },
                enabled = resumeEp != null,
                icon = Icons.Filled.PlayArrow, large = true,
                modifier = Modifier.focusRequester(playFocus)
            )
            TvPill(
                if (s.isFavorite) "In favourites" else "Favourite", vm::toggleFavorite,
                icon = if (s.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, large = true, selected = s.isFavorite
            )
        }
    ) {
        if (episodes.isNotEmpty()) item {
            val watched = h?.lastEpisodeWatched ?: 0
            PillRow(
                "Episodes", episodes,
                label = { e ->
                    val p = h?.eps?.get("e${e.number}")?.p ?: 0f
                    (if (e.filler) "${e.number} (filler)" else e.number.toString()) + if (p in 0.02f..<Eps.DONE) " - ${(p * 100).toInt()}%" else ""
                },
                selected = { e -> e.number <= watched || (h?.eps?.get("e${e.number}")?.p ?: 0f) >= Eps.DONE },
                onClick = { e -> onPlay(vm.slug, e.number) },
                initial = episodes.indexOfFirst { it.number == resumeEp }.coerceAtLeast(0)
            )
        }
        if (a.recommendations.isNotEmpty()) item {
            val row = TvRow("More like this", a.recommendations.distinctBy { it.slug }.map { r ->
                TvItem(key = r.slug, title = r.title, imageUrl = r.posterUrl, onClick = { onOpen(r.slug) })
            })
            TvCardRow(row, restoreKey = null, restoreRequester = remember { FocusRequester() }, onFocused = {})
        }
    }
}

@Composable
fun TvMovieDetailScreen(
    onPlayer: () -> Unit,
    /** (imdbId, season, episode) */
    onWebPlayer: (String, Int, Int) -> Unit,
    vm: MovieDetailViewModel = hiltViewModel()
) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.refreshHistory()
        vm.events.collect { e ->
            when (e) {
                DetailEvent.OpenPlayer -> onPlayer()
                is DetailEvent.OpenWebPlayer -> onWebPlayer(e.imdbId, e.season, e.episode)
                is DetailEvent.Message -> message = e.text
            }
        }
    }
    LaunchedEffect(message) { if (message != null) { delay(4000); message = null } }
    val d = s.detail
    if (d == null) {
        if (s.isLoading) TvMessage("Loading…") else TvMessage(s.error ?: "Couldn't load this title") { TvPill("Retry", vm::load) }
        return
    }
    val series = d.type == MediaType.SERIES
    val r = s.resume
    val firstSeason = d.seasons.firstOrNull()?.number ?: 1
    val playFocus = remember { FocusRequester() }
    RequestOnce(d.id, playFocus)

    DetailLayout(
        backdrop = d.backdropUrl ?: d.posterUrl,
        portrait = d.backdropUrl == null,
        title = d.title,
        meta = listOfNotNull(if (series) "Series" else "Movie", d.year?.toString(), d.runtime, scoreLabel(d.rating)) + d.genres.take(3),
        synopsis = d.description,
        note = message ?: if (!d.playable) "Not available to stream in the app" else null,
        actions = {
            val web = s.webFallback
            if (!d.playable && web != null) {
                TvPill("Watch in web player", { vm.openWebPlayer(web) }, large = true, modifier = Modifier.focusRequester(playFocus))
            } else TvPill(
                text = when {
                    s.resolving -> "Finding sources…"
                    r != null && r.episode > 0 -> "Resume S${r.season} E${r.episode}"
                    r != null -> "Resume"
                    series -> "Play S$firstSeason E1"
                    else -> "Play"
                },
                onClick = { if (r != null) vm.play(r.season, r.episode) else if (series) vm.play(firstSeason, 1) else vm.play(0, 0) },
                icon = Icons.Filled.PlayArrow, large = true,
                modifier = Modifier.focusRequester(playFocus)
            )
            // streams came back empty for a playable title: offer the web player next to Play
            if (d.playable && web != null) TvPill("Web player", { vm.openWebPlayer(web) }, large = true)
            TvPill(
                if (s.isFavorite) "In favourites" else "Favourite", vm::toggleFavorite,
                icon = if (s.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, large = true, selected = s.isFavorite
            )
        }
    ) {
        if (d.audioVariants.size > 1) item {
            PillRow("Audio", d.audioVariants, { it.label }, { it.isCurrent }, { vm.selectVariant(it.id) })
        }
        if (series && d.seasons.isNotEmpty()) {
            item {
                PillRow("Seasons", d.seasons, { "Season ${it.number}" }, { it.number == s.selectedSeason }, { vm.selectSeason(it.number) },
                    initial = d.seasons.indexOfFirst { it.number == s.selectedSeason }.coerceAtLeast(0))
            }
            val count = d.seasons.firstOrNull { it.number == s.selectedSeason }?.episodeCount ?: 0
            if (count > 0) item(key = "eps${s.selectedSeason}") {
                val resumeHere = r?.takeIf { it.season == s.selectedSeason }?.episode ?: 0
                PillRow(
                    "Episodes", (1..count).toList(),
                    { n -> val p = s.eps["s${s.selectedSeason}e$n"]?.p ?: 0f; "Episode $n" + if (p in 0.02f..<Eps.DONE) " - ${(p * 100).toInt()}%" else "" },
                    { it < resumeHere || (s.eps["s${s.selectedSeason}e$it"]?.p ?: 0f) >= Eps.DONE },
                    { if (d.playable) vm.play(s.selectedSeason, it) else vm.openWebPlayer(Resume(s.selectedSeason, it)) },
                    initial = (resumeHere - 1).coerceAtLeast(0)
                )
            }
        }
    }
}
