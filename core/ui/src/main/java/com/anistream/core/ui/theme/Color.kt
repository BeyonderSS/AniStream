package com.anistream.core.ui.theme

import androidx.compose.ui.graphics.Color

// Legacy names kept for source compatibility; they now resolve to the Design.kt tokens.
// New code should use Bg / Ink / Accent directly.
val DarkBackground = Bg.base
val DarkSurface = Bg.raised
val DarkSurfaceVariant = Bg.raised
val DarkSurfaceElevated = Bg.overlay
val DarkSurfaceContainer = Bg.raised

val DarkCard = Bg.raised
val DarkCardElevated = Bg.overlay
val DarkBottomNav = Bg.base

val Primary = Accent.main
val PrimaryVariant = Accent.main
val PrimaryLight = Accent.main
val PrimaryDark = Accent.main
val PrimaryContainer = Accent.soft

val Secondary = Accent.main
val SecondaryVariant = Accent.main
val SecondaryLight = Accent.main
val SecondaryContainer = Accent.soft

val Tertiary = Accent.main
val TertiaryContainer = Accent.soft

val Success = Accent.live
val Warning = Accent.star
val Error = Accent.danger
val Info = Ink.body

val ScoreHigh = Accent.star
val ScoreMid = Accent.star
val ScoreLow = Accent.star

val TextPrimary = Ink.high
val TextSecondary = Ink.muted
val TextTertiary = Ink.faint
val TextDisabled = Ink.faint

val ShimmerBase = Bg.raised
val ShimmerHighlight = Bg.overlay
val Overlay = Bg.badge
val Divider = Ink.hairline

/** No rainbow (DESIGN-REELM.md): every genre gets the same quiet accent. */
val GenreColors: List<Color> = listOf(Accent.main)
