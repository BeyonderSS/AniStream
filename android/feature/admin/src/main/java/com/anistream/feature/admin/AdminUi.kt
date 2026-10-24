package com.anistream.feature.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anistream.core.account.ui.AvatarImage
import com.anistream.core.ui.components.LoadingShimmer
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import kotlinx.serialization.json.JsonElement

internal val Silver = Ink.body

@Composable
internal fun Panel(title: String?, hint: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(Radius.lg).background(Bg.raised).padding(Space.lg)) {
        if (title != null) {
            Text(title, style = AppType.bodyStrong)
            if (hint != null) Text(hint, style = AppType.caption)
            Spacer(Modifier.height(Space.md))
        }
        content()
    }
}

internal fun LazyListScope.panel(title: String?, hint: String? = null, content: @Composable ColumnScope.() -> Unit) =
    item { Panel(title, hint, content) }

@Composable
internal fun Empty(text: String) = Text(text, style = AppType.caption)

@Composable
internal fun Meter(v: Double, tone: Color = Accent.main, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Ink.hairline)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(v.coerceIn(0.02, 1.0).toFloat()).background(tone))
    }
}

@Composable
internal fun Chip(text: String, color: Color = Accent.main, textColor: Color = Accent.on) =
    Text(text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(color).padding(horizontal = 8.dp, vertical = 2.dp))

@Composable
internal fun Skeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        repeat(3) { LoadingShimmer(Modifier.fillMaxWidth().height(110.dp).clip(Radius.lg)) }
    }
}

class RankRow(val label: String, val value: Double, val shown: String? = null, val sub: String? = null, val tone: Color = Accent.main, val avatar: String? = null)

/** A ranked list with an inline bar: the shape most admin answers take. */
@Composable
internal fun Ranked(rows: List<RankRow>, empty: String, onClick: ((Int) -> Unit)? = null) {
    if (rows.isEmpty()) return Empty(empty)
    val max = rows.maxOf { it.value }.coerceAtLeast(1.0)
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        rows.forEachIndexed { i, r ->
            Column(if (onClick != null) Modifier.clickable { onClick(i) } else Modifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (r.avatar != null) { AvatarImage(r.avatar, 24.dp); Spacer(Modifier.width(Space.sm)) }
                    Column(Modifier.weight(1f)) {
                        Text(r.label, style = AppType.captionStrong.copy(color = Ink.high), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (r.sub != null) Text(r.sub, style = AppType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(Space.sm))
                    Text(r.shown ?: num(r.value), style = AppType.numeric)
                }
                Spacer(Modifier.height(Space.xs))
                Meter(r.value / max, r.tone)
            }
        }
    }
}

@Composable
internal fun SourceRows(rows: List<JsonElement>) {
    if (rows.isEmpty()) return Empty("No playback reported yet.")
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        rows.forEach { r ->
            val v = rate(r.d("ok"), r.d("errors"))
            val tone = if (v < 0.8) Accent.danger else if (v < 0.95) Accent.main else Accent.live
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.s("source").orEmpty() + (r.s("host")?.let { "  $it" } ?: ""), style = AppType.captionStrong.copy(color = Ink.high), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${pct(v)}  ${num(r.d("ok"))} ok, ${num(r.d("errors"))} failed", style = AppType.numeric.copy(color = tone))
            }
            Meter(v, tone)
        }
    }
}

private val KIND_LABEL = mapOf(
    "register" to "signed up", "login" to "signed in", "login_failed" to "failed sign-in", "logout" to "signed out", "active" to "came back today",
    "profile_create" to "created a profile", "profile_update" to "edited a profile", "profile_delete" to "deleted a profile", "history_push" to "synced history",
    "pair_start" to "TV pairing started", "pair_claim" to "paired a TV", "room_create" to "opened a watch-together room", "room_join" to "joined a room",
    "room_load" to "room switched title", "hub_connect" to "connected", "handoff" to "handed off to another device", "play" to "started",
    "play_error" to "playback failed", "source_used" to "streamed from", "heartbeat" to "is watching", "crash" to "app crashed",
    "search_empty" to "searched with no results", "update_offered" to "was offered an update", "update_installed" to "installed an update"
)
internal val KINDS = KIND_LABEL.keys.toList()
private val BAD = setOf("login_failed", "play_error", "crash")

@Composable
internal fun EventLine(e: JsonElement, user: String? = null) {
    val k = e.s("kind").orEmpty()
    val det = e.o("detail")
    val what = det.s("title") ?: det.s("query") ?: det.o("count")?.let { "${det.i("count")} ${if (det.i("count") == 1) "entry" else "entries"}" }
        ?: det.s("target") ?: det.s("name") ?: det.s("code") ?: det.s("username") ?: ""
    val who = user ?: e.s("username") ?: (if (k == "login_failed") det.s("username") else null) ?: "Someone"
    val dot = if (k in BAD) Accent.danger else if (k == "register") Accent.live else if (k in setOf("play", "source_used", "heartbeat")) Accent.main else Ink.faint
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Space.sm))
        Column(Modifier.weight(1f)) {
            Text("$who ${KIND_LABEL[k] ?: k.replace('_', ' ')} $what" + (det.s("source")?.takeIf { k != "heartbeat" }?.let { " on $it" } ?: ""), style = AppType.captionStrong)
            det.s("error")?.let { Text(it, style = AppType.caption.copy(color = Accent.danger), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.width(Space.sm))
        Text("${e.s("device")?.let { "$it, " } ?: ""}${e.ago("at")}", style = AppType.caption)
    }
}

/** Lines over a shared scale; NaN breaks nothing, it is skipped. [fill] shades under the first series. */
@Composable
internal fun Sparkline(series: List<Pair<List<Double>, Color>>, modifier: Modifier = Modifier, max: Double? = null, dashedFrom: Int = Int.MAX_VALUE) {
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        val top = (max ?: series.maxOfOrNull { s -> s.first.filter { !it.isNaN() }.maxOrNull() ?: 0.0 } ?: 0.0).coerceAtLeast(1.0)
        for (y in 0..3) drawLine(Ink.hairline, Offset(0f, size.height * y / 3), Offset(size.width, size.height * y / 3), 1f)
        series.forEachIndexed { si, (vals, color) ->
            val step = if (vals.size > 1) size.width / (vals.size - 1) else 0f
            val path = Path()
            var started = false
            vals.forEachIndexed { i, v ->
                if (v.isNaN()) return@forEachIndexed
                val p = Offset(i * step, (size.height * (1 - v / top)).toFloat())
                if (started) path.lineTo(p.x, p.y) else { path.moveTo(p.x, p.y); started = true }
            }
            val dash = if (si >= dashedFrom) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null
            drawPath(path, color, style = Stroke(width = if (si == 0) 4f else 3f, pathEffect = dash))
            if (si == 0 && vals.size > 1) {
                val area = Path().apply { addPath(path); lineTo((vals.size - 1) * step, size.height); lineTo(0f, size.height); close() }
                drawPath(area, color.copy(alpha = 0.14f))
            }
        }
    }
}

private val DOW = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/** Weekday by hour (UTC), amber intensity = watch minutes. */
@Composable
internal fun Heatmap(cells: List<JsonElement>) {
    val m = cells.associate { (it.i("dow") * 24 + it.i("hour")) to it.d("minutes") }
    val max = cells.maxOfOrNull { it.d("minutes") }?.coerceAtLeast(1.0) ?: 1.0
    Row {
        Column(Modifier.width(32.dp)) { DOW.forEach { Text(it, style = AppType.caption.copy(fontSize = 10.sp), modifier = Modifier.height(16.dp)) } }
        Canvas(Modifier.weight(1f).height(16.dp * 7)) {
            val cw = size.width / 24; val ch = size.height / 7
            for (d in 0 until 7) for (h in 0 until 24) {
                val v = m[d * 24 + h] ?: 0.0
                drawRect(
                    if (v > 0) Accent.main.copy(alpha = (0.1 + 0.9 * v / max).toFloat()) else Ink.hairline,
                    Offset(h * cw + 1, d * ch + 1), Size(cw - 2, ch - 2)
                )
            }
        }
    }
    Text("0h to 23h, UTC", style = AppType.caption, modifier = Modifier.padding(start = 32.dp, top = Space.xs))
}
