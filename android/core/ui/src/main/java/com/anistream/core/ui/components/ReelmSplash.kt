package com.anistream.core.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anistream.core.ui.R
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Display
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Motion
import kotlinx.coroutines.delay

/** True when the user switched animations off (animator duration scale 0). Read once. */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

private const val WORD = "Reelm"
private const val REEL_DP = 88f
/** The system splash draws the 108-unit launcher icon at 288dp, so its 48-unit reel is 128dp, dead centre. */
private const val SYSTEM_REEL_DP = 128f
/** Reel centre sits this far above the screen centre once the wordmark is under it (88 reel + 20 gap + 56 line). */
private const val REEL_LIFT_DP = 38f
private val Beam = Color(0xFFFFE2B0)

/**
 * Launch animation that takes over from the system splash: the reel spins down from where the system icon sat,
 * the projector lamp warms up behind it, "Reelm" is revealed letter by letter and a beam of light passes over it,
 * then everything pushes in and fades into whatever is underneath (about 1.8 s).
 * [lite] (low-end TV): a 0.9 s alpha-only version with no rotation, gradients or offscreen layers.
 * With animations switched off the finished frame shows briefly and is removed without a fade.
 */
@Composable
fun ReelmSplash(lite: Boolean, onDone: () -> Unit) {
    val reduced = rememberReducedMotion()
    val done by rememberUpdatedState(onDone)
    val total = if (lite) 900f else 1800f
    val t = remember { Animatable(if (reduced) total * 0.7f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) delay(600) else t.animateTo(total, tween(total.toInt(), easing = LinearEasing))
        done()
    }
    // eased 0..1 progress of a time window [a, b] in ms
    fun win(a: Float, b: Float, easing: Easing = Motion.emphasized) = easing.transform(((t.value - a) / (b - a)).coerceIn(0f, 1f))
    fun exit() = if (reduced) 0f else if (lite) win(650f, 900f, LinearEasing) else win(1450f, 1800f)
    fun enterLite() = win(0f, 300f, LinearEasing)

    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = 1f - exit() }.background(Bg.base).semantics { contentDescription = "Reelm" },
        contentAlignment = Alignment.Center
    ) {
        // the lamp: one radial amber light behind the reel that blooms, then settles
        if (!lite) Box(
            Modifier.fillMaxSize().drawBehind {
                val bloom = win(150f, 700f) * 0.55f - win(800f, 1300f) * 0.2f
                val a = bloom
                if (a > 0f) {
                    val c = Offset(size.width / 2, size.height / 2 - REEL_LIFT_DP.dp.toPx())
                    drawCircle(
                        Brush.radialGradient(listOf(Accent.main.copy(alpha = a), Accent.main.copy(alpha = a * 0.25f), Color.Transparent), c, 220.dp.toPx()),
                        radius = 220.dp.toPx(), center = c
                    )
                }
            }
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                val e = exit()
                if (lite) alpha = enterLite()
                val s = 1f + 0.06f * e
                scaleX = s; scaleY = s
            }
        ) {
            Image(
                painterResource(R.drawable.reelm_reel), contentDescription = null,
                modifier = Modifier.size(REEL_DP.dp).graphicsLayer {
                    if (lite || reduced) return@graphicsLayer
                    val move = win(120f, 780f)
                    val s = SYSTEM_REEL_DP / REEL_DP + (1f - SYSTEM_REEL_DP / REEL_DP) * move
                    scaleX = s; scaleY = s
                    translationY = REEL_LIFT_DP.dp.toPx() * (1f - move)
                    rotationZ = -300f * (1f - win(0f, 950f))
                }
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.graphicsLayer { if (!lite) compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                    drawContent()
                    if (lite || reduced) return@drawWithContent
                    val p = win(720f, 1320f, LinearEasing)
                    if (p <= 0f || p >= 1f) return@drawWithContent
                    val x = -0.35f * size.width + p * 1.7f * size.width
                    drawRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Beam, Color.Transparent), startX = x - size.width * 0.3f, endX = x + size.width * 0.3f),
                        blendMode = BlendMode.SrcAtop
                    )
                }
            ) {
                WORD.forEachIndexed { i, ch ->
                    Text(
                        ch.toString(), style = Wordmark,
                        modifier = Modifier.graphicsLayer {
                            if (lite || reduced) return@graphicsLayer
                            val r = win(430f + i * 70f, 800f + i * 70f)
                            alpha = r
                            translationY = 18.dp.toPx() * (1f - r)
                        }
                    )
                }
            }
        }
    }
}

private val Wordmark = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp, color = Ink.high, letterSpacing = (-0.5).sp)
