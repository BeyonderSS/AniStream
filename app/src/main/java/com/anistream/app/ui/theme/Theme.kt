package com.anistream.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Accent.main,
    onPrimary = Accent.on,
    primaryContainer = Accent.soft,
    onPrimaryContainer = Accent.main,
    inversePrimary = Accent.main,

    // secondary* drives selected FilterChip / NavigationBar indicator / tonal buttons
    secondary = Accent.main,
    onSecondary = Accent.on,
    secondaryContainer = Accent.main,
    onSecondaryContainer = Accent.on,

    tertiary = Accent.main,
    onTertiary = Accent.on,
    tertiaryContainer = Accent.soft,
    onTertiaryContainer = Accent.main,

    background = Bg.base,
    onBackground = Ink.high,

    surface = Bg.raised,
    onSurface = Ink.high,
    surfaceVariant = Bg.overlay,
    onSurfaceVariant = Ink.muted,
    surfaceTint = Color.Transparent,
    surfaceBright = Bg.overlay,
    surfaceDim = Bg.base,
    surfaceContainerLowest = Bg.base,
    surfaceContainerLow = Bg.raised,
    surfaceContainer = Bg.raised,
    surfaceContainerHigh = Bg.overlay,
    surfaceContainerHighest = Bg.overlay,

    inverseSurface = Bg.overlay,
    inverseOnSurface = Ink.high,

    error = Accent.danger,
    onError = Bg.base,
    errorContainer = Accent.danger,
    onErrorContainer = Bg.base,

    outline = Ink.ghost,
    outlineVariant = Ink.hairline,
    scrim = Bg.base
)

// Radius hierarchy (DESIGN-REELM.md): small 8, poster 12, card 18, sheet/dialog 24.
private val ReelmShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = Radius.sm,
    medium = Radius.md,
    large = Radius.lg,
    extraLarge = Radius.xl
)

/** Reelm theme (function name kept so existing call sites compile). */
@Composable
fun AniStreamTheme(
    content: @Composable () -> Unit
) {
    // System bars are transparent/edge-to-edge (enableEdgeToEdge in MainActivity); don't paint them here.
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AniStreamTypography,
        shapes = ReelmShapes,
        content = content
    )
}
