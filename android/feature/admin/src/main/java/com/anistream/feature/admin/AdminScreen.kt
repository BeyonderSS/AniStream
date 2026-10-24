package com.anistream.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anistream.core.ui.components.PillChip
import com.anistream.core.ui.components.PrimaryButton
import com.anistream.core.ui.components.SecondaryButton
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val SECTIONS = listOf("Overview", "Live now", "Engagement", "Content", "Sources", "App health", "Security", "Ops", "Users", "Events")

/** The admin dashboard: ten sections in a tab row, one shared [AdminViewModel], pull to refresh on every one. Entry is gated by Account (admin + Admin mode). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit, vm: AdminViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var userId by rememberSaveable { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val say: (String) -> Unit = { m -> scope.launch { snack.showSnackbar(m) } }

    Box(Modifier.fillMaxSize().background(Bg.base)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(horizontal = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Ink.high) }
                Text("Admin", style = AppType.heading, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.width(Space.sm))
                Chip("ADMIN")
            }
            ScrollableTabRow(tab, containerColor = Bg.base, edgePadding = Space.lg, divider = {}) {
                SECTIONS.forEachIndexed { i, t ->
                    Tab(tab == i, { tab = i }, text = { Text(t, style = AppType.label, color = if (tab == i) Accent.main else Ink.muted) })
                }
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> SectionFrame(vm, "overview", body = LazyListScope::overview)
                    1 -> SectionFrame(vm, "live", every = 10_000, body = LazyListScope::live)
                    2 -> SectionFrame(vm, "engagement", body = LazyListScope::engagement)
                    3 -> SectionFrame(vm, "content", body = LazyListScope::content)
                    4 -> SectionFrame(vm, "sources", body = LazyListScope::sources)
                    5 -> SectionFrame(vm, "app", body = LazyListScope::appHealth)
                    6 -> SectionFrame(vm, "security", body = { d -> security(d) { userId = it } })
                    7 -> SectionFrame(vm, "ops", body = LazyListScope::ops)
                    8 -> UsersSection(vm, say) { userId = it }
                    else -> EventsSection(vm)
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(Space.lg))
    }
    userId?.let { id -> UserSheet(vm, id, say, onClose = { userId = null }, onChanged = { vm.load(vm.ui.value.path) }) }
}

/** Loads [path] (and re-polls every [every] ms when set), then draws header + skeleton / error / [body] in one pull-to-refresh list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionFrame(
    vm: AdminViewModel, path: String, every: Long = 0,
    header: LazyListScope.() -> Unit = {}, body: LazyListScope.(JsonObject) -> Unit
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(path) {
        vm.load(path)
        while (every > 0) { delay(every); vm.load(path, silent = true) }
    }
    val d = ui.data.takeIf { ui.path == path }
    PullToRefreshBox(isRefreshing = ui.loading && d != null && ui.path == path, onRefresh = { vm.load(path) }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            header()
            when {
                d != null -> body(d)
                ui.error != null && ui.path == path -> item {
                    Column(Modifier.fillMaxWidth().clip(Radius.lg).background(Bg.raised).padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load this page", style = AppType.bodyStrong)
                        Text(ui.error.orEmpty(), style = AppType.caption)
                        Spacer(Modifier.height(Space.md))
                        SecondaryButton("Try again", { vm.load(path) })
                    }
                }
                else -> item { Skeleton() }
            }
        }
    }
}

// ---- Users ----------------------------------------------------------------------------------------------------
private const val PER = 25

@Composable
private fun UsersSection(vm: AdminViewModel, say: (String) -> Unit, open: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var term by rememberSaveable { mutableStateOf("") }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(q) { delay(250); if (q.trim() != term) { term = q.trim(); page = 0 } }
    SectionFrame(vm, "users?q=${java.net.URLEncoder.encode(term, "UTF-8")}&limit=$PER&offset=${page * PER}",
        header = {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(q, { q = it }, Modifier.weight(1f), singleLine = true, placeholder = { Text("Search usernames") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
                    Spacer(Modifier.width(Space.sm))
                    PrimaryButton("New user", { creating = true })
                }
            }
        }
    ) { d ->
        val users = d.list("users")
        val total = d.i("total")
        item { Text("$total ${if (total == 1) "account" else "accounts"}", style = AppType.caption) }
        if (users.isEmpty()) item { Empty("No accounts match \"$term\".") }
        items(users) { u ->
            Column(Modifier.fillMaxWidth().clip(Radius.lg).background(Bg.raised).clickable { open(u.s("id").orEmpty()) }.padding(Space.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("@${u.s("username")}", style = AppType.bodyStrong)
                    if (u.b("is_admin")) { Spacer(Modifier.width(Space.sm)); Chip("ADMIN") }
                    if (u.b("disabled")) { Spacer(Modifier.width(Space.sm)); Chip("DISABLED", Accent.danger, Bg.base) }
                }
                Text("${u.i("profiles")} profiles, ${u.i("sessions")} sessions, joined ${u.ago("created_at")}, last seen ${u.ago("last_seen_at")}", style = AppType.caption)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Previous", { page-- }, enabled = page > 0)
                Text("Page ${page + 1} of ${maxOf(1, (total + PER - 1) / PER)}", style = AppType.caption)
                SecondaryButton("Next", { page++ }, enabled = (page + 1) * PER < total)
            }
        }
    }
    if (creating) CreateUserDialog(vm, say, onDone = { creating = false; vm.load(vm.ui.value.path) }, onDismiss = { creating = false })
}

// ---- Events ---------------------------------------------------------------------------------------------------
/** Newest first; the first page is the section load, older pages come by the `next` cursor as the last row scrolls into view. */
@Composable
private fun EventsSection(vm: AdminViewModel) {
    var kind by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var who by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(user) { delay(300); who = user.trim() }
    val qs = "kind=$kind&user=${java.net.URLEncoder.encode(who, "UTF-8")}"
    var older by remember(qs) { mutableStateOf<List<JsonElement>>(emptyList()) }
    var next by remember(qs) { mutableStateOf<Int?>(null) } // null = use the first page's cursor, -1 = reached the end
    var failed by remember(qs) { mutableStateOf(false) }
    SectionFrame(vm, "events?$qs",
        header = {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Username or user id") })
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        item { PillChip("All events", kind == "", { kind = "" }) }
                        items(KINDS) { k -> PillChip(k.replace('_', ' '), kind == k, { kind = k }) }
                    }
                }
            }
        }
    ) { d ->
        val first = d.list("events")
        val cursor = (next ?: d.i("next")).takeIf { it > 0 }
        items(first + older) { EventLine(it) }
        if (first.isEmpty()) item { Empty("No events match these filters.") }
        if (cursor != null && !failed) item(key = "more$cursor") {
            LaunchedEffect(cursor) {
                try {
                    val r = vm.get("events?$qs&before=$cursor")
                    older = older + r.list("events"); next = r.i("next").takeIf { it > 0 } ?: -1
                } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failed = true }
            }
            Text("Loading older events", style = AppType.caption, modifier = Modifier.padding(Space.md))
        }
        if (failed) item { SecondaryButton("Couldn't load more. Retry", { failed = false }) }
    }
}
