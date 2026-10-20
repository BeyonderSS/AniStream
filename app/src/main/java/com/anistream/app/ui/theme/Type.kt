package com.anistream.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.anistream.core.ui.theme.AppType

// Colour is left unspecified so Material components (buttons, nav, chips) can tint text via LocalContentColor.
private fun TextStyle.noColor() = copy(color = Color.Unspecified)

/** Every Material3 style lands on one of the four AppType sizes (30 / 22 / 16 / 13). */
val AniStreamTypography = Typography(
    displayLarge = AppType.title.noColor(),
    displayMedium = AppType.title.noColor(),
    displaySmall = AppType.title.noColor(),
    headlineLarge = AppType.title.noColor(),
    headlineMedium = AppType.heading.noColor(),
    headlineSmall = AppType.heading.noColor(),
    titleLarge = AppType.heading.noColor(),
    titleMedium = AppType.bodyStrong.noColor(),
    titleSmall = AppType.bodyStrong.noColor(),
    bodyLarge = AppType.body.noColor(),
    bodyMedium = AppType.body.noColor(),
    bodySmall = AppType.caption.noColor(),
    labelLarge = AppType.button.noColor(),
    labelMedium = AppType.label.noColor(),
    labelSmall = AppType.caption.noColor()
)
