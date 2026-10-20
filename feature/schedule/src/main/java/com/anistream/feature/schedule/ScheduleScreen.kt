package com.anistream.feature.schedule

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anistream.core.ui.components.EmptyState
import com.anistream.core.ui.components.ErrorContent
import com.anistream.core.ui.components.LoadingShimmer
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.ScheduleEntry
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

private val TimeColumn = 56.dp
private val RailWidth = 24.dp

private fun dayName(date: LocalDate) = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

/** "HH:mm" from the schedule payload; null when unparseable (then it's never dimmed). */
private fun ScheduleEntry.localTime(): LocalTime? = time?.let { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }

@Composable
fun ScheduleScreen(
    onAnimeClick: (String) -> Unit,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val days = remember { (0L..6L).map { LocalDate.now().plusDays(it) } }

    Column(Modifier.fillMaxSize().background(Bg.base)) {
        Text(
            "Schedule",
            style = AppType.title,
            modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.sm, bottom = Space.md)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            itemsIndexed(days) { i, date ->
                val name = dayName(date)
                DayPill(
                    label = if (i == 0) "Today" else name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                    date = date.dayOfMonth,
                    selected = name == uiState.selectedDay,
                    onClick = { viewModel.onDaySelect(name) }
                )
            }
        }
        Spacer(Modifier.height(Space.lg))

        val isToday = uiState.selectedDay == currentDayName()
        val state = when {
            uiState.isLoading -> 0
            uiState.error != null -> 1
            uiState.entriesForDay.isEmpty() -> 2
            else -> 3
        }
        Crossfade(targetState = state, animationSpec = tween(250), label = "schedule_state") { s ->
            when (s) {
                0 -> Column(
                    Modifier.padding(horizontal = Space.lg),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
                ) {
                    repeat(6) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingShimmer(Modifier.width(40.dp).height(12.dp).clip(Radius.sm))
                            Spacer(Modifier.width(TimeColumn - 40.dp + RailWidth))
                            LoadingShimmer(Modifier.weight(1f).height(72.dp).clip(Radius.lg))
                        }
                    }
                }

                1 -> ErrorContent(
                    message = uiState.error ?: "Failed to load schedule",
                    onRetry = viewModel::loadSchedule
                )

                2 -> EmptyState(
                    message = "Nothing airing on ${uiState.selectedDay}",
                    subtitle = "Pick another day to see what's coming",
                    icon = Icons.Rounded.EventBusy
                )

                else -> {
                    val entries = uiState.entriesForDay
                    val now = remember(entries) { LocalTime.now() }
                    val upNext = if (isToday) entries.firstOrNull { e -> e.localTime()?.let { !it.isBefore(now) } == true } else null
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Space.lg, end = Space.lg, bottom = Space.xxxl)
                    ) {
                        itemsIndexed(entries) { i, entry ->
                            val past = isToday && entry.localTime()?.isBefore(now) == true
                            TimelineRow(
                                entry = entry,
                                isFirst = i == 0,
                                isLast = i == entries.lastIndex,
                                past = past,
                                upNext = entry === upNext,
                                onClick = { onAnimeClick(entry.animeSlug) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPill(label: String, date: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .height(64.dp)
            .clip(Radius.lg)
            .background(if (selected) Ink.high else Bg.raised)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = AppType.label, color = if (selected) Bg.base.copy(alpha = 0.72f) else Ink.muted, maxLines = 1)
        Text(date.toString(), style = AppType.heading.copy(fontFeatureSettings = "tnum"), color = if (selected) Bg.base else Ink.body)
    }
}

@Composable
private fun TimelineRow(
    entry: ScheduleEntry,
    isFirst: Boolean,
    isLast: Boolean,
    past: Boolean,
    upNext: Boolean,
    onClick: () -> Unit
) {
    val dotColor = when {
        upNext -> Accent.main
        else -> Ink.faint
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .alpha(if (past) 0.5f else 1f)
    ) {
        Text(
            entry.time ?: "--:--",
            style = AppType.numeric,
            color = if (upNext) Accent.text else Ink.body,
            modifier = Modifier.width(TimeColumn).padding(top = Space.lg + 2.dp)
        )
        // dot + vertical rail; the rail runs through the row so consecutive rows join up
        Box(
            Modifier
                .width(RailWidth)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val dotY = (Space.lg + 8.dp).toPx()
                    drawLine(
                        Ink.hairline,
                        Offset(x, if (isFirst) dotY else 0f),
                        Offset(x, if (isLast) dotY else size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
        ) {
            Box(
                Modifier
                    .padding(top = Space.lg + 4.dp)
                    .align(Alignment.TopCenter)
                    .size(8.dp)
                    .background(dotColor, CircleShape)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Space.xs)
                .clip(Radius.lg)
                .background(Bg.raised)
                .then(if (upNext) Modifier.border(1.5.dp, Accent.main, Radius.lg) else Modifier)
                .clickable(onClick = onClick)
                .padding(Space.lg)
        ) {
            if (upNext) {
                Text(
                    "Up next",
                    style = AppType.label,
                    color = Accent.on,
                    modifier = Modifier
                        .padding(bottom = Space.sm)
                        .background(Accent.main, Radius.pill)
                        .padding(horizontal = Space.sm, vertical = 2.dp)
                )
            }
            Text(entry.animeTitle, style = AppType.bodyStrong, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val ep = entry.episodeNumber
            if (ep != null) {
                Text("Episode $ep", style = AppType.numeric, modifier = Modifier.padding(top = Space.xs))
            }
        }
    }
}
