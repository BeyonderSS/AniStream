package com.anistream.feature.downloads

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anistream.core.ui.components.EmptyState
import com.anistream.core.ui.components.LoadingShimmer
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.DownloadItem
import com.anistream.domain.model.DownloadState
import java.util.Locale

/** "157 MB" / "1.2 GB" (binary units, matching the old MB math). */
private fun formatSize(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1024) String.format(Locale.US, "%.1f GB", mb / 1024) else "${mb.toLong()} MB"
}

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var playing by remember { mutableStateOf<String?>(null) }
    playing?.let { LocalPlayerDialog(filePath = it, onDismiss = { playing = null }) }

    val downloads = uiState.downloads
    Column(Modifier.fillMaxSize().background(Bg.base)) {
        Text(
            "DOWNLOADS",
            style = AppType.title,
            modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.sm)
        )
        if (downloads.isNotEmpty()) {
            val n = downloads.size
            Text(
                "$n download${if (n == 1) "" else "s"} · ${formatSize(downloads.sumOf { it.fileSizeBytes })}",
                style = AppType.numeric,
                modifier = Modifier.padding(start = Space.lg, top = Space.xs)
            )
        }
        Spacer(Modifier.height(Space.md))

        val state = when {
            uiState.isLoading -> 0
            uiState.error != null -> 1
            downloads.isEmpty() -> 2
            else -> 3
        }
        Crossfade(targetState = state, animationSpec = tween(250), label = "downloads_state") { s ->
            when (s) {
                0 -> Column(Modifier.padding(horizontal = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    LoadingShimmer(Modifier.width(160.dp).height(16.dp).clip(Radius.sm))
                    repeat(4) { LoadingShimmer(Modifier.fillMaxWidth().height(72.dp).clip(Radius.lg)) }
                }

                1 -> EmptyState(
                    message = "Couldn't load downloads",
                    subtitle = uiState.error ?: "",
                    icon = Icons.Rounded.ErrorOutline
                )

                2 -> EmptyState(
                    message = "No downloads yet",
                    subtitle = "Download episodes from the player to watch offline",
                    icon = Icons.Rounded.Download
                )

                else -> {
                    // groups keep first-seen order; episodes sorted within a group
                    val groups = remember(downloads) {
                        downloads.groupBy { it.animeTitle }.mapValues { (_, v) -> v.sortedBy { it.episodeNumber } }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = Space.xxxl),
                        verticalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        groups.forEach { (title, eps) ->
                            item(key = "h:$title") {
                                Row(
                                    Modifier.padding(top = Space.md, bottom = Space.xs).animateItem(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        title,
                                        style = AppType.bodyStrong,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Text("  ${eps.size} ep", style = AppType.numeric)
                                }
                            }
                            items(eps, key = { it.episodeId }) { d ->
                                SwipeToDelete(
                                    onDelete = { viewModel.deleteDownload(d.episodeId) },
                                    modifier = Modifier.animateItem()
                                ) {
                                    DownloadRow(
                                        download = d,
                                        onCancel = { viewModel.cancelDownload(d.episodeId) },
                                        onDelete = { viewModel.deleteDownload(d.episodeId) },
                                        onPlay = { playing = d.filePath }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            val delete = it == SwipeToDismissBoxValue.EndToStart
            if (delete) onDelete()
            delete
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier.clip(Radius.lg),
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Accent.danger else Bg.raised,
                tween(250),
                label = "swipe_color"
            )
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = Space.xl),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Ink.high)
            }
        }
    ) { content() }
}

@Composable
private fun DownloadRow(
    download: DownloadItem,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    val state = download.state
    val active = state == DownloadState.DOWNLOADING || state == DownloadState.PENDING
    val playable = state == DownloadState.COMPLETED && download.filePath != null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg.raised)
            .border(1.dp, Ink.hairline, Radius.lg)
            .clickable(enabled = playable, onClick = onPlay)
            .padding(start = Space.md, end = Space.xs, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(Bg.overlay, Radius.md).border(1.dp, if (playable) Ink.ghost else Ink.hairline, Radius.md),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (state) {
                    DownloadState.COMPLETED -> Icons.Rounded.PlayArrow
                    DownloadState.FAILED -> Icons.Rounded.ErrorOutline
                    DownloadState.PAUSED -> Icons.Rounded.Pause
                    else -> Icons.Rounded.Download
                },
                contentDescription = null,
                tint = when (state) {
                    DownloadState.COMPLETED -> Ink.high
                    DownloadState.FAILED -> Accent.danger
                    else -> Ink.muted
                },
                modifier = Modifier.size(22.dp)
            )
        }

        Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
            // media downloads (movies & TV) have no anime episode number
            Text(
                if (download.episodeNumber > 0) "Episode ${download.episodeNumber}" else download.subtitle ?: "Movie",
                style = AppType.bodyStrong
            )
            val size = download.fileSizeBytes
            Text(
                if (size > 0) "${download.quality} · ${formatSize(size)}" else download.quality,
                style = AppType.numeric
            )
            if (active) {
                Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier.weight(1f).height(4.dp).clip(Radius.pill),
                        color = Accent.main,
                        trackColor = Color.White.copy(alpha = 0.25f),
                        strokeCap = StrokeCap.Butt,
                        drawStopIndicator = {}
                    )
                    Text(
                        if (state == DownloadState.PENDING) "QUEUED" else "${(download.progress * 100).toInt()}%",
                        style = AppType.numeric,
                        color = Ink.body,
                        modifier = Modifier.padding(start = Space.sm).width(44.dp)
                    )
                }
            }
        }

        when (state) {
            DownloadState.COMPLETED -> Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Downloaded",
                tint = Accent.live,
                modifier = Modifier.padding(end = Space.md).size(20.dp)
            )
            DownloadState.FAILED -> TextButton(onClick = onDelete) {
                Text("FAILED · REMOVE", style = AppType.label, color = Accent.danger)
            }
            DownloadState.PAUSED -> IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete download", tint = Ink.muted)
            }
            else -> IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = "Cancel download", tint = Ink.muted)
            }
        }
    }
}
