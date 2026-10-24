package com.anistream.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anistream.core.ui.R

/**
 * Reelm "Dusk Projector" design system. See DESIGN-REELM.md at the project root.
 * Soft blue-slate dusk (never black), one warm projector-amber accent, Bricolage + Figtree,
 * radius hierarchy (poster 12 / card 18 / sheet 24 / pill).
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

/** Radius hierarchy: small inputs 8, posters/tiles 12, cards 18, sheets/dialogs 24, buttons/chips pill. */
object Radius {
    val sm = RoundedCornerShape(8.dp)
    val md = RoundedCornerShape(12.dp)   // posters
    val lg = RoundedCornerShape(18.dp)   // cards
    val xl = RoundedCornerShape(24.dp)   // sheets, dialogs
    val pill = RoundedCornerShape(percent = 50)
    val round = CircleShape
}

private const val MIST = 0xE9EAF0L

private fun mist(alphaPercent: Int) = Color((((alphaPercent * 255 / 100).toLong()) shl 24) or MIST)

/** Mist ink, hierarchy by opacity 100 / 72 / 52 / 32. */
object Ink {
    val high = mist(100)
    val body = mist(72)
    val muted = mist(52)
    val faint = mist(32)
    val hairline = mist(8)   // dividers, card edges
    val glass = mist(8)      // pressed ghost fill
    val ghost = mist(24)     // ghost button / outline border
}

object Accent {
    val main = Color(0xFFF2B45A)     // projector amber: primary action, focus, progress only
    val on = Color(0xFF1A1408)       // text/icons drawn on amber
    val pressed = Color(0xFFD99B42)
    val text = Color(0xFFF2B45A)
    val soft = Color(0x29F2B45A)     // 16%
    val faint = Color(0x12F2B45A)    // 7%
    val glow = Color(0x52F2B45A)     // ~32%: the projector light under the primary CTA
    val star = Ink.body              // ratings stay quiet; amber is reserved for actions
    val live = Color(0xFF7FD1B9)     // signal: downloaded / live / new episode
    val onLive = Color(0xFF0E201A)
    val danger = Color(0xFFF07C6C)   // failure / destructive
}

object Bg {
    val base = Color(0xFF15171F)      // dusk
    val raised = Color(0xFF1D2029)    // slate
    val overlay = Color(0xFF272B37)   // haze
    val badge = Color(0xB815171F)     // dusk 72%: pills over artwork
    val bar = Color(0xF21D2029)       // slate 95%: bottom bar
    val shadow = Color(0xFF0A0C12)    // shadow tint, never grey
    val scrim = Brush.verticalGradient(listOf(Color.Transparent, Color(0xB315171F), Color(0xFF15171F)))
    val scrimTop = Brush.verticalGradient(listOf(Color(0x8015171F), Color.Transparent))
}

/** Emphasized easing, 180–260 ms for actions; 400 ms for the "projector" backdrop crossfade. */
object Motion {
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val SHORT = 180
    const val MEDIUM = 260
    const val PROJECTOR = 400
    fun <T> quick() = tween<T>(SHORT, easing = emphasized)
    fun <T> standard() = tween<T>(MEDIUM, easing = emphasized)
}

/** Soft dusk-tinted elevation (ignored below API 28, where shadows stay neutral). */
fun Modifier.duskShadow(elevation: Dp = 12.dp, shape: Shape = Radius.lg): Modifier =
    shadow(elevation, shape, clip = false, ambientColor = Bg.shadow, spotColor = Bg.shadow)

/** The projector light: a blurred amber glow, only for the primary CTA / focused element. */
fun Modifier.amberGlow(shape: Shape = Radius.pill, elevation: Dp = 16.dp): Modifier =
    shadow(elevation, shape, clip = false, ambientColor = Accent.glow, spotColor = Accent.main)

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, width: Float = 92f) = Font(
    R.font.bricolage_grotesque, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width))
)

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    R.font.figtree, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val Display = FontFamily(bricolage(600), bricolage(700))
/** Marquee titles (TV hero, ticket stubs). */
val DisplayHeavy = FontFamily(bricolage(800, width = 100f))
val TextFamily = FontFamily(figtree(400), figtree(500), figtree(600))

/** Phone scale 30 / 22 / 16 / 13. Sentence/title case; never uppercased, never tracked. */
object AppType {
    val title = TextStyle(fontFamily = Display, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = Ink.high, letterSpacing = (-0.3).sp)
    val heading = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink.high)
    val body = TextStyle(fontFamily = TextFamily, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal, color = Ink.body)
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold, color = Ink.high)
    val caption = TextStyle(fontFamily = TextFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal, color = Ink.muted)
    val captionStrong = caption.copy(fontWeight = FontWeight.Medium, color = Ink.body)
    /** Chips, badges, nav labels, small buttons. */
    val label = TextStyle(fontFamily = TextFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink.high)
    /** Primary/secondary button text. */
    val button = TextStyle(fontFamily = TextFamily, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink.high)
    /** tabular digits for times, episode numbers, sizes */
    val numeric = caption.copy(fontFeatureSettings = "tnum")
}
