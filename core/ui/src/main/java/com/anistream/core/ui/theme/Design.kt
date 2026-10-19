package com.anistream.core.ui.theme

import android.graphics.Typeface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AniStream "Nocturne" design system (Lamborghini-inspired). See DESIGN.md at the project root.
 * True black canvas, one gold accent, zero radius, uppercase condensed display type.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp   // screen gutter
    val xl = 24.dp   // gap between related groups
    val xxl = 32.dp  // gap between sections
    val xxxl = 48.dp
}

/** Every surface is sharp. Names kept so call sites read by intent. */
object Radius {
    val sm = RoundedCornerShape(0.dp)
    val md = RoundedCornerShape(0.dp)
    val lg = RoundedCornerShape(0.dp)
    val xl = RoundedCornerShape(0.dp)
    val pill = RoundedCornerShape(0.dp)
    /** Only for the seek thumb, progress dots, toggle tracks and timeline dots. */
    val round = CircleShape
}

/** Text on black: white headings, then Mist / Steel / Ash. */
object Ink {
    val high = Color(0xFFFFFFFF)
    val body = Color(0xFFE6E6E6)
    val muted = Color(0xFF969696)
    val faint = Color(0xFF7D7D7D)
    val hairline = Color(0x1AFFFFFF) // 10% white: dividers, card borders
    val glass = Color(0x14FFFFFF)    // 8% white: pressed ghost fill
    val ghost = Color(0x80FFFFFF)    // 50% white: ghost button border
}

object Accent {
    val main = Color(0xFFFFC000)     // Lamborghini gold, the only chromatic colour
    val on = Color.Black             // text/icons drawn on gold
    val pressed = Color(0xFF917300)
    val text = Color(0xFFFFCE3E)     // inline highlighted text
    val soft = Color(0x24FFC000)     // ~14%
    val faint = Color(0x0FFFC000)    // 6%
    val glow = Color.Transparent     // no glows in Nocturne
    val star = Color(0xFFFFC000)
    val live = Color(0xFF34D399)     // only for "airing / done" states
    val danger = Color(0xFFEF4444)   // only for destructive / failed
}

object Bg {
    val base = Color(0xFF000000)
    val raised = Color(0xFF181818)
    val overlay = Color(0xFF202020)
    val badge = Color(0xB3000000)     // black 70%
    val bar = Color(0xF2000000)       // black 95%
    val scrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000), Color(0xFF000000)))
    val scrimTop = Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))
}

private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

/** The only four sizes in the app (28 / 20 / 15 / 12). Display styles are meant to be uppercased at the call site. */
object AppType {
    val title = TextStyle(fontFamily = Condensed, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, color = Ink.high)
    val heading = TextStyle(fontFamily = Condensed, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = Ink.high, letterSpacing = 0.5.sp)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, color = Ink.body)
    val bodyStrong = body.copy(fontWeight = FontWeight.Medium, color = Ink.high)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, color = Ink.muted)
    val captionStrong = caption.copy(fontWeight = FontWeight.Medium, color = Ink.body)
    /** Uppercase buttons, badges, tabs, nav labels (uppercase the string at the call site). */
    val label = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = Ink.high, letterSpacing = 0.96.sp)
    /** tabular digits for times, episode numbers, sizes */
    val numeric = caption.copy(fontFeatureSettings = "tnum")
}
