package com.anistream.feature.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.domain.model.DownloadState
import com.anistream.feature.downloads.DownloadsViewModel
import com.anistream.feature.downloads.LocalPlayerDialog
import com.anistream.feature.favorites.FavoritesViewModel
import com.anistream.feature.livetv.IPTV_ORG_URL
import com.anistream.feature.livetv.LiveTvViewModel
import com.anistream.feature.search.SearchViewModel

internal val keys = ('a'..'z').map { it.toString() } + ('1'..'9').map { it.toString() } + "0"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvSearchScreen(onOpen: (id: String) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    // Fire TV and most Android TVs ship a recognizer; hide the mic where none resolves
    val canVoice = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::onQueryChange)
    }

    Row(Modifier.fillMaxSize().padding(start = Overscan, top = 40.dp)) {
        Column(Modifier.width(360.dp)) {
            Text("Search", style = TvType.headline)
            Spacer(Modifier.height(14.dp))
            Text(
                s.query.ifEmpty { "Type a title" },
                style = TvType.title.copy(color = if (s.query.isEmpty()) Ink.faint else Ink.high),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().background(Bg.raised, Radius.md).padding(horizontal = 18.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvPill("Anime", { vm.onScopeChange(0) }, selected = s.scope == 0)
                TvPill("Movies & TV", { vm.onScopeChange(1) }, selected = s.scope == 1)
            }
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(keys) { k -> TvPill(k, { vm.onQueryChange(s.query + k) }) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvPill("Space", { vm.onQueryChange(s.query + " ") })
                TvPill("", { vm.onQueryChange(s.query.dropLast(1)) }, icon = Icons.AutoMirrored.Outlined.Backspace)
                TvPill("Clear", vm::onClearQuery)
                if (canVoice) TvPill("", {
                    try { voice.launch(voiceIntent) } catch (_: ActivityNotFoundException) { }
                }, icon = Icons.Outlined.Mic)
            }
        }
        Spacer(Modifier.width(32.dp))
        val list = if (s.query.isBlank()) (if (s.scope == 1) s.mediaPopular else s.popular) else s.visibleResults
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    s.query.isBlank() -> "Popular"
                    s.isLoading -> "Searching…"
                    s.error != null -> s.error!!
                    list.isEmpty() -> "No results"
                    else -> "Results"
                },
                style = TvType.title
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(132.dp),
                modifier = Modifier.focusRestorer(),
                contentPadding = PaddingValues(top = 14.dp, end = Overscan, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(list, key = { it.slug }) { r ->
                    TvCard(TvItem(r.slug, r.title, r.posterUrl, onClick = { onOpen(r.slug) }), wide = false, onFocused = {
                        if (r == list.lastOrNull() && s.query.isNotBlank()) vm.loadNextPage()
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvLiveScreen(onPlay: () -> Unit, vm: LiveTvViewModel = hiltViewModel()) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val ch by vm.channels.collectAsStateWithLifecycle()
    val add by vm.add.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()

    when {
        playlists == null || ch.isLoading -> return TvMessage("Loading channels…")
        playlists!!.isEmpty() -> return TvMessage(add.error ?: "No playlists yet") {
            TvPill(if (add.busy) "Adding…" else "Add the free iptv-org channel list", { vm.addPlaylist("iptv-org", IPTV_ORG_URL) }, large = true)
        }
        ch.error != null && ch.all.isEmpty() -> return TvMessage(ch.error!!) { TvPill("Retry", { vm.load(refresh = true) }) }
    }
    Row(Modifier.fillMaxSize().padding(start = Overscan, top = 40.dp)) {
        Column(Modifier.width(260.dp)) {
            Text("Live TV", style = TvType.headline)
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                modifier = Modifier.focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
            ) {
                item { TvPill("All channels", { vm.group.value = null }, selected = group == null) }
                items(ch.groups) { g -> TvPill(g, { vm.group.value = g }, selected = group == g) }
            }
        }
        Spacer(Modifier.width(28.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(200.dp),
            modifier = Modifier.weight(1f).focusRestorer(),
            contentPadding = PaddingValues(top = 56.dp, end = Overscan, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(visible, key = { it.id }) { c ->
                TvCard(TvItem(c.id, c.name, c.logoUrl, subtitle = c.group, onClick = { vm.play(c); onPlay() }), wide = true, onFocused = {})
            }
        }
    }
}

@Composable
fun TvLibraryScreen(onOpen: (id: String) -> Unit, favVm: FavoritesViewModel = hiltViewModel(), dlVm: DownloadsViewModel = hiltViewModel()) {
    val fav by favVm.uiState.collectAsStateWithLifecycle()
    val dl by dlVm.uiState.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { favVm.loadFavorites() }
    val restore = remember { androidx.compose.ui.focus.FocusRequester() }

    val favRow = TvRow("Favourites", fav.sortedFavorites.map { f ->
        TvItem("fav|${f.animeSlug}", f.animeTitle, f.posterUrl, onClick = { onOpen(f.animeSlug) })
    })
    val dlRow = TvRow("Downloads", dl.downloads.map { d ->
        val path = d.filePath
        TvItem(
            "dl|${d.episodeId}", d.animeTitle, null,
            subtitle = when (d.state) {
                DownloadState.COMPLETED -> d.subtitle ?: "Episode ${d.episodeNumber}"
                DownloadState.DOWNLOADING -> "Downloading ${(d.progress * 100).toInt()}%"
                DownloadState.FAILED -> "Failed"
                DownloadState.PAUSED -> "Paused"
                DownloadState.PENDING -> "Waiting"
            },
            progress = d.progress.takeIf { d.state == DownloadState.DOWNLOADING },
            onClick = { if (d.state == DownloadState.COMPLETED && path != null) playing = path }
        )
    }, wide = true)
    val rows = listOf(favRow, dlRow).filter { it.items.isNotEmpty() }

    Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
        Text("Library", style = TvType.headline, modifier = Modifier.padding(start = Overscan, bottom = 18.dp))
        if (rows.isEmpty()) {
            TvMessage(if (fav.isLoading || dl.isLoading) "Loading…" else "Favourites and downloads show up here")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                items(rows, key = { it.title }) { TvCardRow(it, null, restore, onFocused = {}) }
            }
        }
    }
    playing?.let { LocalPlayerDialog(filePath = it, onDismiss = { playing = null }) }
}
