package com.anistream.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anistream.core.account.AuthRules
import com.anistream.core.account.ui.AvatarImage
import com.anistream.core.ui.components.PillChip
import com.anistream.core.ui.components.SecondaryButton
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Space
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private class Confirm(val title: String, val body: String, val cta: String, val method: String, val path: String, val done: String, val danger: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun UserSheet(vm: AdminViewModel, id: String, say: (String) -> Unit, onClose: () -> Unit, onChanged: () -> Unit) {
    var data by remember(id) { mutableStateOf<JsonObject?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var sub by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    var busy by remember { mutableStateOf(false) }
    var temp by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    LaunchedEffect(id, tick) {
        try { data = vm.get("users/$id"); error = null } catch (e: CancellationException) { throw e } catch (e: Exception) { error = adminError(e) }
    }

    ModalBottomSheet(onDismissRequest = { if (!busy) onClose() }, containerColor = Bg.raised) {
        val d = data
        val u = d.o("user")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(bottom = Space.xl)) {
            if (d == null) {
                if (error != null) { Text(error.orEmpty(), style = AppType.body); SecondaryButton("Try again", { tick++ }) } else Skeleton()
                return@Column
            }
            val name = u.s("username").orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarImage(d.list("profiles").firstOrNull().s("avatar") ?: "grinning_face", 56.dp, ring = u.b("is_admin"))
                Column(Modifier.padding(start = Space.md)) {
                    Text("@$name", style = AppType.heading)
                    Text("Joined ${u.ago("created_at")}, last seen ${u.ago("last_seen_at")}", style = AppType.caption)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.xs), modifier = Modifier.padding(top = Space.xs)) {
                        if (u.b("is_admin")) Chip("ADMIN")
                        if (u.b("disabled")) Chip("DISABLED", Accent.danger, Bg.base)
                        Chip("${u.i("sessions")} ${if (u.i("sessions") == 1) "session" else "sessions"}", Bg.overlay, Ink.body)
                    }
                }
            }
            Spacer(Modifier.height(Space.md))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                val base = "users/$id"
                if (u.b("disabled")) SecondaryButton("Enable", { confirm = Confirm("Enable this account?", "@$name can sign in again.", "Enable", "POST", "$base/enable", "Account enabled") })
                else SecondaryButton("Disable", { confirm = Confirm("Disable this account?", "@$name is signed out everywhere and can't sign in until you enable the account again.", "Disable", "POST", "$base/disable", "Account disabled", true) })
                SecondaryButton("Sign out all", { confirm = Confirm("Sign out everywhere?", "Every device signed in as @$name is signed out.", "Sign out all", "POST", "$base/revoke", "Signed out of all devices") })
                SecondaryButton("Reset password", { confirm = Confirm("Reset the password?", "@$name gets a new temporary password and is signed out everywhere. You'll see the password once.", "Reset password", "POST", "$base/reset_password", "Password reset") })
                if (u.b("is_admin")) SecondaryButton("Remove admin", { confirm = Confirm("Remove admin?", "@$name loses access to this dashboard.", "Remove admin", "POST", "$base/remove_admin", "Admin removed") })
                else SecondaryButton("Make admin", { confirm = Confirm("Make admin?", "@$name can see everything here and act on every account.", "Make admin", "POST", "$base/make_admin", "Admin added") })
                SecondaryButton("Delete", { confirm = Confirm("Delete this account?", "@$name, all profiles and all watch history are deleted. This can't be undone.", "Delete account", "DELETE", base, "Account deleted", true) })
            }
            Spacer(Modifier.height(Space.md))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                items(listOf("Profiles", "Devices", "Sessions", "History", "Events").withIndex().toList()) { (i, t) -> PillChip(t, sub == i, { sub = i }) }
            }
            Spacer(Modifier.height(Space.md))
            val profiles = d.list("profiles")
            when (sub) {
                0 -> profiles.forEach { p ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(p.s("avatar") ?: "grinning_face", 56.dp, kids = p.b("kids"))
                        Text(p.s("name").orEmpty(), style = AppType.bodyStrong, modifier = Modifier.padding(start = Space.md))
                    }
                }
                1 -> d.list("devices").let { l ->
                    if (l.isEmpty()) Empty("No devices recorded.")
                    l.forEach { x ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${x.s("device") ?: "Unknown"}  ${x.s("country").orEmpty()}", style = AppType.captionStrong.copy(color = Ink.high))
                                Text(x.s("ua").orEmpty(), style = AppType.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text("${x.i("events")} events, ${x.ago("last_at")}", style = AppType.caption)
                        }
                    }
                }
                2 -> d.list("sessions").let { l ->
                    if (l.isEmpty()) Empty("Not signed in anywhere.")
                    l.forEach { x ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Signed in ${x.ago("created_at")}${x.s("device")?.let { ", $it" } ?: ""}", style = AppType.captionStrong.copy(color = Ink.high))
                                Text("Used ${x.ago("last_used_at")}, ${x.s("ua") ?: "unknown client"}", style = AppType.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            SecondaryButton("Sign out", { confirm = Confirm("Sign out this session?", "That device is signed out. Others stay signed in.", "Sign out", "DELETE", "sessions/${x.s("id")}", "Session signed out") })
                        }
                    }
                }
                3 -> d.list("history").let { l ->
                    if (l.isEmpty()) Empty("No watch history.")
                    l.forEach { h ->
                        val f = if (h.d("duration") > 0) h.d("position") / h.d("duration") else 0.0
                        val pl = h.o("payload")
                        val who = profiles.firstOrNull { it.s("id") == h.s("profile_id") }.s("name")
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(pl.s("title") ?: h.s("key").orEmpty(), style = AppType.captionStrong.copy(color = Ink.high), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(who, pl.s("ep")?.let { "Episode $it" }, pl.s("season")?.let { "S$it E${pl.s("episode")}" }, h.ago("updated_at")).joinToString(", "), style = AppType.caption)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Space.xs)) {
                                Column(Modifier.weight(1f)) { Meter(f) }
                                Text("  ${pct(f)}", style = AppType.numeric)
                            }
                        }
                    }
                }
                else -> d.list("events").let { l ->
                    if (l.isEmpty()) Empty("No events.")
                    l.forEach { EventLine(it, user = name) }
                }
            }
        }
    }

    confirm?.let { c ->
        AlertDialog(
            onDismissRequest = { if (!busy) confirm = null },
            containerColor = Bg.raised,
            title = { Text(c.title, style = AppType.heading) },
            text = { Text(c.body, style = AppType.body) },
            dismissButton = { TextButton({ confirm = null }, enabled = !busy) { Text("Cancel", color = Ink.body) } },
            confirmButton = {
                TextButton({
                    scope.launch {
                        busy = true
                        try {
                            val r = vm.act(c.method, c.path)
                            r.s("password")?.let { temp = it }
                            say(c.done); confirm = null
                            if (c.method == "DELETE" && c.path.startsWith("users/")) onClose() else tick++
                            onChanged()
                        } catch (e: CancellationException) { throw e } catch (e: Exception) { say(adminError(e)) } finally { busy = false }
                    }
                }, enabled = !busy) { Text(if (busy) "Working" else c.cta, color = if (c.danger) Accent.danger else Accent.main) }
            }
        )
    }
    if (temp.isNotEmpty()) AlertDialog(
        onDismissRequest = { temp = "" },
        containerColor = Bg.raised,
        title = { Text("Temporary password", style = AppType.heading) },
        text = {
            Column {
                Text("Share it with the person privately. It won't be shown again.", style = AppType.body)
                Text(temp, style = AppType.bodyStrong.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(top = Space.md))
            }
        },
        confirmButton = { TextButton({ clip.setText(AnnotatedString(temp)); say("Copied"); temp = "" }) { Text("Copy and close", color = Accent.main) } }
    )
}

/** New account form; the server enforces the same username/password rules as sign-up. */
@Composable
internal fun CreateUserDialog(vm: AdminViewModel, say: (String) -> Unit, onDone: () -> Unit, onDismiss: () -> Unit) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var admin by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val name = user.trim().lowercase()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Bg.raised,
        title = { Text("Create an account", style = AppType.heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("Share the username and password with the person. They can change their profiles after signing in.", style = AppType.caption)
                OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Username") }, isError = AuthRules.usernameError(name) != null, supportingText = { AuthRules.usernameError(name)?.let { Text(it) } })
                OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Password") }, isError = AuthRules.passwordError(pass) != null, supportingText = { AuthRules.passwordError(pass)?.let { Text(it) } })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(admin, { admin = it })
                    Column { Text("Make this account an admin", style = AppType.captionStrong); Text("Admins see this dashboard and can manage every account.", style = AppType.caption) }
                }
                error?.let { Text(it, style = AppType.caption.copy(color = Accent.danger)) }
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancel", color = Ink.body) } },
        confirmButton = {
            TextButton({
                scope.launch {
                    busy = true; error = null
                    try {
                        val r = vm.act("POST", "users", buildJsonObject { put("username", name); put("password", pass); put("admin", admin) })
                        say("${if (r.o("user").b("is_admin")) "Admin" else "User"} @$name created"); onDone()
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { error = AuthRules.friendly(e, signingUp = true) } finally { busy = false }
                }
            }, enabled = !busy && AuthRules.canSubmit(name, pass)) { Text(if (busy) "Creating" else if (admin) "Create admin" else "Create user", color = Accent.main) }
        }
    )
}
