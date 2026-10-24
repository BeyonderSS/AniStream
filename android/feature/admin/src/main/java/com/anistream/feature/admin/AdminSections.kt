package com.anistream.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anistream.core.account.ui.AvatarImage
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val COLORS = listOf(Accent.main, Accent.live, Color(0xFF9DB4FF), Accent.danger, Ink.high, Color(0xFFC79BF2))

@Composable
private fun Legend(items: List<Pair<String, Color>>) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        items.forEach { (l, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c)); Spacer(Modifier.width(Space.xs)); Text(l, style = AppType.caption)
            }
        }
    }
}

// ---- Overview -------------------------------------------------------------------------------------------------
fun LazyListScope.overview(d: JsonObject) {
    val t = d.o("totals")
    val kpis = listOf(
        "Watching now" to t.d("watchingNow"), "Online, 15 min" to t.d("onlineNow"), "Accounts" to t.d("users"), "Profiles" to t.d("profiles"),
        "Devices seen" to t.d("devices"), "History entries" to t.d("history"), "Open rooms" to t.d("activeRooms")
    )
    items(kpis.chunked(2)) { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            pair.forEach { (label, v) ->
                val live = label == "Watching now" && v > 0
                Column(Modifier.weight(1f).clip(Radius.lg).background(if (live) Accent.faint else Bg.raised).padding(Space.lg)) {
                    Text(label, style = AppType.caption)
                    Text(num(v), style = AppType.heading.copy(color = if (live) Accent.main else Ink.high))
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    val daily = d.list("daily")
    panel("Activity", "last 30 days") {
        Sparkline(listOf(daily.map { it.d("dau") } to Accent.main, daily.map { it.d("signups") } to Accent.live, daily.map { it.d("wau") } to Ink.faint), dashedFrom = 2)
        Legend(listOf("Daily active" to Accent.main, "Sign-ups" to Accent.live, "Weekly active" to Ink.faint))
        daily.lastOrNull()?.let { Text("Latest: ${it.i("dau")} active, ${it.i("signups")} signed up", style = AppType.caption, modifier = Modifier.padding(top = Space.sm)) }
    }
    panel("Top titles", "plays, last 30 days") {
        Ranked(d.list("topTitles").map { RankRow(it.s("title").orEmpty(), it.d("plays"), sub = "${it.i("viewers")} ${if (it.i("viewers") == 1) "viewer" else "viewers"}") }, "No plays reported yet.")
    }
    panel("Source health", "streams that started vs failed") { SourceRows(d.list("sources")) }
}

// ---- Live now -------------------------------------------------------------------------------------------------
fun LazyListScope.live(d: JsonObject) {
    val w = d.list("watching")
    panel("Watching", "${w.size} ${if (w.size == 1) "stream" else "streams"}, refreshes every 10 s") {
        if (w.isEmpty()) Empty("Nobody is watching right now.")
        Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            w.forEach { x ->
                val det = x.o("detail")
                val f = if (det.d("duration") > 0) det.d("position") / det.d("duration") else 0.0
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(x.s("avatar") ?: "grinning_face", 36.dp)
                        Column(Modifier.weight(1f).padding(horizontal = Space.sm)) {
                            Text(x.s("profile") ?: x.s("username").orEmpty(), style = AppType.bodyStrong)
                            Text("@${x.s("username")}", style = AppType.caption)
                        }
                        Text("${x.s("device").orEmpty()} ${x.s("app_version").orEmpty()}".trim(), style = AppType.caption)
                    }
                    Text(det.s("title") ?: "Untitled", style = AppType.captionStrong.copy(color = Ink.high), modifier = Modifier.padding(top = Space.sm))
                    Text(listOfNotNull(det.s("episode")?.let { "Episode $it" }, det.s("source"), det.s("quality")).joinToString(", "), style = AppType.caption)
                    Spacer(Modifier.height(Space.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Meter(f) }
                        Text("  ${clock(det.d("position"))} of ${clock(det.d("duration"))}", style = AppType.numeric)
                    }
                }
            }
        }
    }
    val rooms = d.list("rooms")
    panel("Watch together", "open rooms") {
        if (rooms.isEmpty()) Empty("No rooms are open.")
        Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
            rooms.forEach { r ->
                val m = r.list("members")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.s("code").orEmpty(), style = AppType.captionStrong.copy(color = Accent.main, fontFamily = FontFamily.Monospace))
                        Text(r.s("title") ?: "Choosing a title", style = AppType.captionStrong.copy(color = Ink.high))
                        Text(if (m.isEmpty()) "empty, ${r.ago("updated_at")}" else "${m.size} in room", style = AppType.caption)
                    }
                    m.forEach { AvatarImage(it.s("avatar")?.takeIf(String::isNotEmpty) ?: "grinning_face", 28.dp, Modifier.padding(start = 2.dp)) }
                }
            }
        }
    }
}

// ---- Engagement -----------------------------------------------------------------------------------------------
fun LazyListScope.engagement(d: JsonObject) {
    panel("When people watch", "weekday by hour, UTC") { Heatmap(d.list("heatmap")) }
    val plat = d.list("platforms")
    val total = plat.sumOf { it.d("minutes") }.coerceAtLeast(1.0)
    panel("Platform split", "share of watch time") {
        if (plat.isEmpty()) Empty("No watch time yet.")
        Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
            plat.forEachIndexed { i, p -> Box(Modifier.weight((p.d("minutes") / total).toFloat().coerceAtLeast(0.001f)).height(8.dp).background(COLORS[i % 4])) }
        }
        plat.forEachIndexed { i, p ->
            Row(Modifier.padding(top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(COLORS[i % 4])); Spacer(Modifier.width(Space.sm))
                Text(p.s("platform").orEmpty(), style = AppType.captionStrong.copy(color = Ink.high), modifier = Modifier.weight(1f))
                Text("${pct(p.d("minutes") / total)}, ${hours(p.d("minutes"))}, ${p.i("users")} people", style = AppType.caption)
            }
        }
    }
    panel("Binge streaks", "consecutive days watched") {
        Ranked(d.list("binge").map { RankRow(it.s("username").orEmpty(), it.d("longest"), "${it.i("longest")} days", if (it.i("current") > 0) "on a ${it.i("current")}-day streak now" else "streak ended", if (it.i("current") > 0) Accent.main else Accent.live) }, "No streaks yet.")
    }
    panel("Watch time by account") { Ranked(d.list("perUser").map { RankRow(it.s("username").orEmpty(), it.d("minutes"), hours(it.d("minutes"))) }, "No watch time yet.") }
    panel("Watch time by profile") {
        Ranked(d.list("perProfile").map { RankRow(it.s("name").orEmpty() + if (it.b("kids")) " (Kids)" else "", it.d("minutes"), hours(it.d("minutes")), "@${it.s("username")}", avatar = it.s("avatar") ?: "grinning_face") }, "No watch time yet.")
    }
    panel("Completion by title", "finished, of everyone who started") {
        Ranked(d.list("completion").map {
            val f = it.d("completed") / it.d("starts").coerceAtLeast(1.0)
            RankRow(it.s("title").orEmpty(), f, pct(f), "${it.i("completed")} of ${it.i("starts")} finished", Accent.live)
        }, "No synced history yet.")
    }
}

// ---- Content --------------------------------------------------------------------------------------------------
fun LazyListScope.content(d: JsonObject) {
    panel("Most watched", "watch time, 30 days") { Ranked(d.list("watched").map { RankRow(it.s("title").orEmpty(), it.d("minutes"), hours(it.d("minutes")), "${it.i("viewers")} viewers") }, "No watch time yet.") }
    panel("Most abandoned", "started, under 20% watched") { Ranked(d.list("abandoned").map { RankRow(it.s("title").orEmpty(), it.d("abandoned"), "${it.i("abandoned")} profiles", tone = Accent.danger) }, "Nothing abandoned yet.") }
    val kinds = d.list("kinds")
    val total = kinds.sumOf { it.d("minutes") }.coerceAtLeast(1.0)
    panel("What people watch", "share of watch time") {
        if (kinds.isEmpty()) Empty("No watch time yet.")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            kinds.forEach { k ->
                Column(Modifier.weight(1f).clip(Radius.md).background(Bg.overlay).padding(Space.md)) {
                    Text(if (k.s("kind") == "live") "Live TV" else k.s("kind").orEmpty().replaceFirstChar { it.uppercase() }, style = AppType.caption)
                    Text(pct(k.d("minutes") / total), style = AppType.heading)
                    Text(hours(k.d("minutes")), style = AppType.caption)
                }
            }
        }
    }
    panel("Searches with no results", "worth adding a source for") {
        val s = d.list("searches")
        if (s.isEmpty()) Empty("Every search found something.")
        s.forEach { Row(Modifier.padding(vertical = 4.dp)) { Text(it.s("query").orEmpty(), style = AppType.captionStrong.copy(color = Ink.high), modifier = Modifier.weight(1f)); Text("${it.i("times")}", style = AppType.numeric) } }
    }
}

// ---- Sources --------------------------------------------------------------------------------------------------
fun LazyListScope.sources(d: JsonObject) {
    d.list("alerts").forEach { a ->
        item {
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(Accent.danger.copy(alpha = 0.16f)).padding(Space.lg)) {
                Text("${a.s("source")} dropped", style = AppType.bodyStrong.copy(color = Accent.danger))
                Text("Only ${a.i("ok")} of ${a.i("ok") + a.i("errors")} streams started in the last hour.", style = AppType.caption)
            }
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Column(Modifier.weight(1f).clip(Radius.lg).background(Bg.raised).padding(Space.lg)) {
                Text("HEVC failures, 14 days", style = AppType.caption); Text(num(d.d("hevcFailures")), style = AppType.heading)
            }
            Column(Modifier.weight(1f).clip(Radius.lg).background(Bg.raised).padding(Space.lg)) {
                Text("Dolby audio failures, 14 days", style = AppType.caption); Text(num(d.d("dolbyFailures")), style = AppType.heading)
            }
        }
    }
    val daily = d.list("daily")
    val names = daily.mapNotNull { it.s("source") }.distinct().take(6)
    val days = daily.mapNotNull { it.s("day") }.distinct().sorted()
    panel("Success rate over time", "% of streams that started, per day") {
        if (days.isEmpty()) Empty("No playback reported yet.")
        else {
            Sparkline(names.mapIndexed { i, n ->
                days.map { day -> daily.firstOrNull { it.s("source") == n && it.s("day") == day }?.let { rate(it.d("ok"), it.d("errors")) * 100 } ?: Double.NaN } to COLORS[i]
            }, max = 100.0)
            Legend(names.mapIndexed { i, n -> n to COLORS[i] })
        }
    }
    panel("By source and host") { SourceRows(d.list("bySource")) }
    panel("Failures by codec") { Ranked(d.list("codecs").map { RankRow(it.s("codec").orEmpty(), it.d("errors"), tone = Accent.danger) }, "No failures.") }
    panel("Recent playback errors") {
        val r = d.list("recent").take(25)
        if (r.isEmpty()) Empty("No playback errors. Nice.")
        r.forEach { EventLine(it) }
    }
}

// ---- App health -----------------------------------------------------------------------------------------------
@Composable
private fun CrashRow(c: JsonElement) {
    var open by remember { mutableStateOf(false) }
    val trace = c.o("detail").s("trace").orEmpty()
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = Space.sm)) {
        Text(trace.lineSequence().firstOrNull().orEmpty().ifBlank { "Crash" }, style = AppType.captionStrong.copy(color = Accent.danger), maxLines = if (open) 4 else 1, overflow = TextOverflow.Ellipsis)
        Text("${c.s("username") ?: "unknown"}, ${c.s("device").orEmpty()} ${c.s("app_version").orEmpty()}, ${c.ago("at")}", style = AppType.caption)
        if (open) Text(trace, style = AppType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Ink.body), modifier = Modifier.padding(top = Space.sm).clip(Radius.sm).background(Bg.overlay).horizontalScroll(rememberScrollState()).padding(Space.sm))
    }
}

fun LazyListScope.appHealth(d: JsonObject) {
    panel("Crash reports", "sent on the next app start, tap for the stack trace") {
        val c = d.list("crashes")
        if (c.isEmpty()) Empty("No crashes reported.")
        c.forEach { CrashRow(it) }
    }
    panel("Version adoption", "people per version, 30 days") { Ranked(d.list("versions").map { RankRow("${it.s("platform").orEmpty()} ${it.s("version").orEmpty()}", it.d("users"), "${it.i("users")} people") }, "No versions reported yet.") }
    panel("In-app updates", "installed after being offered") {
        Ranked(d.list("updates").map { val f = it.d("installed") / it.d("offered").coerceAtLeast(1.0); RankRow(it.s("version").orEmpty(), f, pct(f), "${it.i("installed")} of ${it.i("offered")} installed", Accent.live) }, "No update prompts yet.")
    }
    panel("Reelm Helper on web", "browser extension versions") {
        Ranked(d.list("helpers").map { RankRow(if (it.s("helper") == "none") "Not installed" else it.s("helper").orEmpty(), it.d("users"), "${it.i("users")} people") }, "No web playback yet.")
    }
}

// ---- Security -------------------------------------------------------------------------------------------------
fun LazyListScope.security(d: JsonObject, openUser: (String) -> Unit) {
    panel("Failed sign-ins", "latest 50") {
        val f = d.list("failed")
        if (f.isEmpty()) Empty("No failed sign-ins.")
        f.forEach { x ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text((x.s("username") ?: "?") + if (!x.b("known")) "  no such account" else "", style = AppType.captionStrong.copy(color = Ink.high), modifier = Modifier.weight(1f))
                Text("${x.s("ip_country") ?: "??"}, ${x.ago("at")}", style = AppType.caption)
            }
        }
    }
    panel("New devices", "first seen in the last 7 days") {
        val n = d.list("newDevices")
        if (n.isEmpty()) Empty("No new devices this week.")
        n.forEach { x ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${x.s("username")}  ${x.s("device").orEmpty()}", style = AppType.captionStrong.copy(color = Ink.high))
                    Text(x.s("ua").orEmpty(), style = AppType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${x.s("ip_country") ?: "??"}, ${x.ago("first_at")}", style = AppType.caption)
            }
        }
    }
    val s = d.list("sessions")
    panel("Sessions per account", "tap an account to sign out its sessions") {
        Ranked(s.map { RankRow("@${it.s("username")}", it.d("sessions"), "${it.i("sessions")} signed in", "last used ${it.ago("last_used_at")}") }, "No sessions.") { i -> s[i].s("user_id")?.let(openUser) }
    }
    panel("Kids profiles", "watch time, 30 days") {
        val k = d.list("kids")
        if (k.isEmpty()) Empty("No kids profiles.")
        k.forEach { x ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(x.s("avatar") ?: "grinning_face", 32.dp, kids = false)
                Column(Modifier.weight(1f).padding(horizontal = Space.sm)) {
                    Text("${x.s("name")}  @${x.s("username")}", style = AppType.captionStrong.copy(color = Ink.high))
                    Text(x.s("last_title")?.let { "Last watched $it, ${x.ago("last_at")}" } ?: "Nothing watched yet", style = AppType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(hours(x.d("minutes")), style = AppType.numeric)
            }
        }
    }
}

// ---- Ops ------------------------------------------------------------------------------------------------------
fun LazyListScope.ops(d: JsonObject) {
    val cf = d.o("cloudflare")
    if (!d.b("connected")) {
        panel("Connect Cloudflare analytics") {
            Text("Worker errors, CPU and D1 usage come from the Cloudflare GraphQL Analytics API. Create an API token with Account Analytics: Read, then from server/ run:", style = AppType.caption)
            Text("npx wrangler secret put CF_ANALYTICS_TOKEN\n# and in wrangler.toml [vars]:\nCF_ACCOUNT_ID = \"<your account id>\"", style = AppType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Ink.body), modifier = Modifier.padding(top = Space.sm).clip(Radius.sm).background(Bg.overlay).horizontalScroll(rememberScrollState()).padding(Space.sm))
        }
    } else if (cf.s("error") != null) {
        panel("Cloudflare analytics") { Empty("Cloudflare answered: ${cf.s("error")}") }
    } else {
        val byScript = cf.list("workers").groupBy { it.o("dimensions").s("scriptName").orEmpty() }
        panel("Workers", "last 7 days") {
            if (byScript.isEmpty()) Empty("No worker traffic yet.")
            byScript.forEach { (name, ws) ->
                val exceeded = ws.filter { it.o("dimensions").s("status") == "exceededResources" }.sumOf { it.o("sum").d("requests") }
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(name, style = AppType.captionStrong.copy(color = Ink.high))
                    Text("${num(ws.sumOf { it.o("sum").d("requests") })} requests, ${num(ws.sumOf { it.o("sum").d("errors") })} errors, ${num(exceeded)} CPU limit hits, CPU p50 / p99 " +
                        "%.1f / %.1f ms".format(ws.maxOf { it.o("quantiles").d("cpuTimeP50") } / 1000, ws.maxOf { it.o("quantiles").d("cpuTimeP99") } / 1000), style = AppType.caption)
                }
            }
        }
        val d1 = cf.list("d1").groupBy { it.o("dimensions").s("date").orEmpty() }.toSortedMap()
            .map { (day, r) -> Triple(day, r.sumOf { it.o("sum").d("rowsRead") }, r.sumOf { it.o("sum").d("rowsWritten") }) }
        val today = d1.lastOrNull()
        val lim = d.o("limits")
        val storage = cf.list("storage").maxOfOrNull { it.o("max").d("databaseSizeBytes") } ?: 0.0
        panel("D1 today", "against the free plan") {
            listOf(Triple("Rows read", today?.second ?: 0.0, lim.d("rowsReadPerDay")), Triple("Rows written", today?.third ?: 0.0, lim.d("rowsWrittenPerDay")), Triple("Storage", storage, lim.d("storageBytes"))).forEach { (l, v, max) ->
                val f = v / max.coerceAtLeast(1.0)
                Row { Text(l, style = AppType.caption, modifier = Modifier.weight(1f)); Text(if (l == "Storage") "%.1f MB of 5 GB".format(v / 1e6) else "${num(v)} of ${num(max)}", style = AppType.numeric) }
                Meter(f, if (f > 0.8) Accent.danger else Accent.main, Modifier.padding(bottom = Space.sm))
            }
        }
        panel("D1 rows per day", "last 7 days") { Ranked(d1.reversed().map { RankRow(it.first, it.third, "${num(it.third)} written", "${num(it.second)} read") }, "No D1 data yet.") }
    }
    val dep = d.o("deploys")
    panel("Deploys", "GitHub Actions") {
        val r = dep.list("runs")
        if (r.isEmpty()) Empty("GitHub returned no runs.")
        r.forEach { x -> Row(Modifier.padding(vertical = 4.dp)) {
            Column(Modifier.weight(1f)) { Text(x.s("title").orEmpty(), style = AppType.captionStrong.copy(color = Ink.high), maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${x.s("name")}, ${x.s("branch")}", style = AppType.caption) }
            Text(x.ago("at"), style = AppType.caption)
        } }
    }
    panel("Releases", "APK downloads") { Ranked(dep.list("releases").map { RankRow(it.s("tag").orEmpty(), it.d("downloads"), "${num(it.d("downloads"))} downloads", it.ago("at")) }, "No releases yet.") }
}
