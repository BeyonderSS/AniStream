package com.anistream.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space

/** Scales the element to 0.97 while [interactionSource] is pressed (DESIGN.md press feedback). */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "pressScale"
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** clickable (+ optional long click) with ripple and press-scale feedback. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.pressClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    return pressScale(source).combinedClickable(
        interactionSource = source,
        indication = LocalIndication.current,
        enabled = enabled,
        role = role,
        onLongClick = onLongClick,
        onClick = onClick
    )
}

/** Remote artwork with a Bg.raised placeholder/error and a crossfade. */
@Composable
fun ArtworkImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center
) {
    val context = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).crossfade(true).build() }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        alignment = alignment,
        placeholder = ColorPainter(Bg.raised),
        error = ColorPainter(Bg.raised),
        fallback = ColorPainter(Bg.raised),
        modifier = modifier.background(Bg.raised)
    )
}

/** Gold rectangle CTA, black uppercase label, darker gold while pressed. No glow. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SharpButton(text, onClick, modifier, icon, enabled, primary = true)
}

/** Transparent ghost: 1dp white@50% border, white uppercase label, 8% white fill while pressed. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SharpButton(text, onClick, modifier, icon, enabled, primary = false)
}

@Composable
private fun SharpButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    icon: ImageVector?,
    enabled: Boolean,
    primary: Boolean
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = when {
        primary && pressed -> Accent.pressed
        primary -> Accent.main
        pressed -> Ink.glass
        else -> Color.Transparent
    }
    val fg = if (primary) Accent.on else Ink.high
    Row(
        modifier = modifier
            .pressScale(source)
            .alpha(if (enabled) 1f else 0.38f)
            .background(bg, Radius.pill)
            .then(if (primary) Modifier else Modifier.border(1.dp, Ink.ghost, Radius.pill))
            .clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = Space.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(text.uppercase(), style = AppType.label, color = fg, maxLines = 1, softWrap = false)
    }
}

/** Uppercase tertiary text button in Ink.body. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ink.body
) {
    Box(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = Space.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), style = AppType.label, color = color, maxLines = 1, softWrap = false)
    }
}

/** Sharp chip with a hairline border; selected = gold fill + black label. */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null
) {
    val fg by animateColorAsState(if (selected) Accent.on else Ink.body, tween(200), label = "chipFg")
    val bg by animateColorAsState(if (selected) Accent.main else Color.Transparent, tween(200), label = "chipBg")
    Row(
        modifier = modifier
            .background(bg, Radius.pill)
            .border(1.dp, if (selected) Accent.main else Ink.hairline, Radius.pill)
            .then(if (onClick != null) Modifier.pressClickable(onClick = onClick) else Modifier)
            .heightIn(min = 36.dp)
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Space.xs))
        }
        Text(label.uppercase(), style = AppType.label, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Sharp segmented control (e.g. Sub | Dub); selected segment gold with black label. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .border(1.dp, Ink.hairline, Radius.pill)
            .padding(2.dp)
    ) {
        options.forEachIndexed { i, option ->
            val selected = i == selectedIndex
            val bg by animateColorAsState(if (selected) Accent.main else Color.Transparent, tween(250), label = "segBg")
            Box(
                modifier = Modifier
                    .background(bg)
                    .pressClickable(role = Role.Tab) { onSelect(i) }
                    .heightIn(min = 36.dp)
                    .padding(horizontal = Space.lg),
                contentAlignment = Alignment.Center
            ) {
                Text(option.uppercase(), style = AppType.label, color = if (selected) Accent.on else Ink.muted, maxLines = 1)
            }
        }
    }
}

/** Icon in a sharp hairline square; used by empty/error states. */
@Composable
internal fun SoftCircleIcon(icon: ImageVector, tint: Color, background: Color) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .background(background, Radius.md)
            .border(1.dp, Ink.hairline, Radius.md),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
    }
}
