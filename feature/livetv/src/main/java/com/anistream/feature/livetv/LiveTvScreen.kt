package com.anistream.feature.livetv

import androidx.compose.ui.draw.clip
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anistream.core.ui.components.ArtworkImage
import com.anistream.core.ui.components.EmptyState
import com.anistream.core.ui.components.ErrorContent
import com.anistream.core.ui.components.PillChip
import com.anistream.core.ui.components.PrimaryButton
import com.anistream.core.ui.components.RowSkeleton
import com.anistream.core.ui.components.TextAction
import com.anistream.core.ui.components.pressClickable
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.Channel
import com.anistream.domain.model.Playlist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(
    onPlay: () -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val ch by viewModel.channels.collectAsStateWithLifecycle()
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val group by viewModel.group.collectAsStateWithLifecycle()
    val add by viewModel.add.collectAsStateWithLifecycle()

    var managing by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(add.done) { if (add.done) { dialog = false; viewModel.resetAdd() } }
    BackHandler(enabled = managing) { managing = false }

    val list = playlists
    Column(Modifier.fillMaxSize().background(Bg.base)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = if (managing) 0.dp else Space.lg, end = Space.xs)
        ) {
            if (managing) {
                IconButton(onClick = { managing = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink.high) }
            }
            Text(if (managing) "Playlists" else "Live TV", style = AppType.title, modifier = Modifier.weight(1f))
            if (!managing && !list.isNullOrEmpty()) {
                IconButton(onClick = { managing = true }) {
                    Icon(Icons.Outlined.PlaylistPlay, contentDescription = "Manage playlists", tint = Ink.high)
                }
            }
        }

        when {
            list == null -> SkeletonList()
            managing -> PlaylistManager(list, onRemove = viewModel::removePlaylist, onAdd = { viewModel.resetAdd(); dialog = true })
            list.isEmpty() -> NoPlaylists(
                busy = add.busy,
                error = if (dialog) null else add.error,
                onAdd = { viewModel.resetAdd(); dialog = true },
                onSuggestion = { viewModel.addPlaylist("iptv-org", IPTV_ORG_URL) }
            )
            else -> {
                SearchField(query, onChange = { viewModel.query.value = it })
                if (ch.groups.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.lg),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        modifier = Modifier.padding(top = Space.sm)
                    ) {
                        item(key = "all") { PillChip(label = "All", selected = group == null, onClick = { viewModel.group.value = null }) }
                        items(ch.groups, key = { it }) { g ->
                            PillChip(label = g, selected = g == group, onClick = { viewModel.group.value = g })
                        }
                    }
                }
                val err = ch.error
                val refreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = ch.refreshing,
                    onRefresh = { viewModel.load(refresh = true) },
                    state = refreshState,
                    modifier = Modifier.fillMaxSize().padding(top = Space.sm),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = refreshState,
                            isRefreshing = ch.refreshing,
                            containerColor = Bg.overlay,
                            color = Accent.main,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    when {
                        ch.isLoading -> SkeletonList()
                        err != null && ch.all.isEmpty() -> ErrorContent(message = err, onRetry = { viewModel.load(refresh = true) })
                        visible.isEmpty() && ch.all.isEmpty() -> EmptyState(
                            message = "No channels",
                            subtitle = "Your playlists returned no playable channels",
                            icon = Icons.Outlined.LiveTv,
                            actionLabel = "Manage playlists",
                            onAction = { managing = true }
                        )
                        visible.isEmpty() -> EmptyState(message = "No matching channels", icon = Icons.Rounded.SearchOff)
                        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xl)) {
                            items(visible, key = { it.id }, contentType = { 0 }) { c ->
                                ChannelRow(c, onClick = { viewModel.play(c); onPlay() })
                            }
                        }
                    }
                }
            }
        }
    }

    if (dialog) AddPlaylistDialog(
        busy = add.busy,
        error = add.error,
        onDismiss = { dialog = false; viewModel.resetAdd() },
        onConfirm = viewModel::addPlaylist
    )
}

@Composable
private fun ChannelRow(c: Channel, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressClickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm)
    ) {
        ArtworkImage(
            url = c.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(48.dp).clip(Radius.sm).background(Bg.raised).padding(Space.xs)
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = AppType.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val g = c.group
            if (!g.isNullOrBlank()) Text(g, style = AppType.caption.copy(color = Ink.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.padding(horizontal = Space.lg).fillMaxWidth().height(52.dp),
        placeholder = { Text("Search channels", style = AppType.body, color = Ink.faint) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Ink.muted) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = Ink.muted)
            }
        },
        singleLine = true,
        shape = Radius.pill,
        textStyle = AppType.body.copy(color = Ink.high),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Bg.raised,
            unfocusedContainerColor = Bg.raised,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Accent.main
        )
    )
}

@Composable
private fun NoPlaylists(busy: Boolean, error: String?, onAdd: () -> Unit, onSuggestion: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            EmptyState(
                message = "No playlists yet",
                subtitle = "Add an M3U playlist URL to watch live channels",
                icon = Icons.Outlined.LiveTv,
                actionLabel = "Add playlist",
                onAction = onAdd
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(Space.lg)
                .fillMaxWidth()
                .clip(Radius.lg)
                .background(Bg.raised)
                .pressClickable(enabled = !busy, onClick = onSuggestion)
                .padding(Space.lg)
        ) {
            Column(Modifier.weight(1f)) {
                Text("iptv-org", style = AppType.heading)
                Text(error ?: "Free public channels", style = AppType.caption.copy(color = if (error != null) Accent.danger else Ink.muted))
            }
            Text(if (busy) "Adding…" else "Add", style = AppType.label.copy(color = Accent.text))
        }
    }
}

@Composable
private fun PlaylistManager(list: List<Playlist>, onRemove: (Long) -> Unit, onAdd: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            items(list, key = { it.id }) { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(Radius.lg)
                        .background(Bg.raised)
                        .padding(start = Space.lg, top = Space.md, bottom = Space.md, end = Space.xs)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = AppType.bodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p.url, style = AppType.caption.copy(color = Ink.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { onRemove(p.id) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove ${p.name}", tint = Ink.muted)
                    }
                }
            }
        }
        PrimaryButton(
            text = "Add playlist",
            icon = Icons.Rounded.Add,
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().padding(Space.lg)
        )
    }
}

@Composable
private fun AddPlaylistDialog(busy: Boolean, error: String?, onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Accent.main,
        unfocusedBorderColor = Ink.hairline,
        cursorColor = Accent.main,
        focusedContainerColor = Bg.raised,
        unfocusedContainerColor = Bg.raised
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Radius.xl,
        containerColor = Bg.overlay,
        icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = Ink.body) },
        title = { Text("Add playlist", style = AppType.heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true, shape = Radius.sm,
                    placeholder = { Text("Name (optional)", style = AppType.body, color = Ink.faint) },
                    textStyle = AppType.body.copy(color = Ink.high), colors = colors, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true, shape = Radius.sm,
                    placeholder = { Text("https://…/playlist.m3u", style = AppType.body, color = Ink.faint) },
                    textStyle = AppType.body.copy(color = Ink.high), colors = colors, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { clipboard.getText()?.text?.let { url = it.trim() } }) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = "Paste", tint = Ink.muted)
                        }
                    }
                )
                if (error != null) Text(error, style = AppType.caption.copy(color = Accent.danger))
            }
        },
        confirmButton = { TextAction(text = if (busy) "Adding…" else "Add", onClick = { if (!busy) onConfirm(name, url) }, color = Accent.text) },
        dismissButton = { TextAction(text = "Cancel", onClick = onDismiss) }
    )
}

@Composable
private fun SkeletonList() {
    Column(Modifier.fillMaxSize().padding(top = Space.sm)) { repeat(8) { RowSkeleton() } }
}
