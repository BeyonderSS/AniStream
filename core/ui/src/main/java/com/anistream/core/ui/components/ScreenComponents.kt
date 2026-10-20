package com.anistream.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.Episode

/** Error state: icon in a soft circle, one-line headline, message, Retry CTA. */
@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SoftCircleIcon(Icons.Rounded.CloudOff, tint = Ink.muted, background = Bg.raised)
        Spacer(Modifier.height(Space.xl))
        Text("Something went wrong", style = AppType.heading, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.sm))
        Text(
            text = message,
            style = AppType.body.copy(color = Ink.muted),
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Space.xl))
        PrimaryButton(text = "Retry", onClick = onRetry, icon = Icons.Rounded.Refresh)
    }
}

/** Empty state: icon in a soft slate circle, one line (+ optional subtitle), optional CTA. */
@Composable
fun EmptyState(
    message: String,
    subtitle: String? = null,
    icon: ImageVector = Icons.Outlined.FavoriteBorder,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        SoftCircleIcon(icon, tint = Ink.body, background = Bg.raised)
        Spacer(Modifier.height(Space.xl))
        Text(message, style = AppType.heading, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(Space.sm))
            Text(subtitle, style = AppType.body.copy(color = Ink.muted), textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun EpisodeListItem(
    episode: Episode,
    lastWatched: Int,
    watchProgress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWatched = episode.number <= lastWatched
    val isCurrent = episode.number == lastWatched + 1

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.md)
            .background(Color.Transparent)
            .pressClickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(Radius.round)
                .background(if (isCurrent) Accent.main else Bg.raised),
            contentAlignment = Alignment.Center
        ) {
            if (isWatched) {
                Icon(Icons.Rounded.Check, contentDescription = "Watched", tint = Ink.faint, modifier = Modifier.size(18.dp))
            } else {
                Text(
                    text = "${episode.number}",
                    style = AppType.numeric.copy(color = if (isCurrent) Accent.on else Ink.body)
                )
            }
        }

        Spacer(Modifier.width(Space.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = episode.title ?: "Episode ${episode.number}",
                    style = AppType.body.copy(color = if (isWatched) Ink.faint else Ink.high),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (episode.filler) {
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        text = "Filler",
                        style = AppType.label.copy(color = Ink.muted),
                        modifier = Modifier
                            .background(Bg.overlay, Radius.pill)
                            .padding(horizontal = Space.sm, vertical = 2.dp)
                    )
                }
            }

            if (isWatched && watchProgress > 0f) {
                Spacer(Modifier.height(Space.sm))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(Radius.pill)
                        .background(Ink.ghost)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(watchProgress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Accent.main)
                    )
                }
            }
        }
    }
}
