package com.anistream.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search anime..."
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(Radius.pill)
            .background(Bg.raised)
            .border(1.dp, Ink.hairline, Radius.pill),
        textStyle = AppType.body.copy(color = Ink.high),
        placeholder = { Text(placeholder, style = AppType.body.copy(color = Ink.faint)) },
        leadingIcon = {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Ink.muted, modifier = Modifier.size(24.dp))
        },
        trailingIcon = {
            AnimatedVisibility(visible = query.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Ink.muted, modifier = Modifier.size(20.dp))
                }
            }
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Accent.main,
            focusedTextColor = Ink.high,
            unfocusedTextColor = Ink.high
        )
    )
}

/** Neutral outline chip (no rainbow). [index] is kept for source compatibility and ignored. */
@Composable
fun GenreChip(
    name: String,
    index: Int,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    PillChip(label = name, selected = selected, onClick = onClick, modifier = modifier)
}

/**
 * Star in Accent.star + the score. Default is a small pill readable over artwork;
 * [plain] drops the pill for use on plain backgrounds.
 */
@Composable
fun ScoreBadge(
    score: Double,
    modifier: Modifier = Modifier,
    plain: Boolean = false
) {
    Row(
        modifier = modifier
            .then(if (plain) Modifier else Modifier.background(Bg.badge, Radius.pill).padding(horizontal = Space.sm, vertical = 2.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Star, contentDescription = null, tint = Accent.star, modifier = Modifier.size(12.dp))
        Text(
            text = String.format("%.1f", score),
            style = AppType.numeric.copy(color = if (plain) Ink.body else Ink.high)
        )
    }
}

/** Small caption pill (base at 70% + white text) for TV / Movie / OVA over artwork. */
@Composable
fun TypeBadge(
    type: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = type.uppercase(),
        style = AppType.label.copy(color = Ink.high),
        maxLines = 1,
        modifier = modifier
            .background(Bg.badge, Radius.pill)
            .padding(horizontal = Space.sm, vertical = 2.dp)
    )
}

/** Scores are always shown with the star colour; no traffic-light colours (DESIGN.md). */
@Suppress("UNUSED_PARAMETER")
fun scoreColor(score: Double): Color = Accent.star
