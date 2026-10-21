package com.anistream.core.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anistream.core.account.FluentEmojis
import com.anistream.core.account.Profile
import com.anistream.core.ui.components.PrimaryButton
import com.anistream.core.ui.components.SecondaryButton
import com.anistream.core.ui.components.TextAction
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space

/** Round Fluent Emoji avatar. */
@Composable
fun AvatarImage(avatarId: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, ring: Boolean = false) {
    AsyncImage(
        model = FluentEmojis.urlFor(avatarId),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size).clip(CircleShape).background(Bg.overlay)
            .then(if (ring) Modifier.border(3.dp, Accent.main, CircleShape) else Modifier).padding(size / 8)
    )
}

/** Phone overlay: shown at launch when signed in with more than one profile. */
@Composable
fun ProfilePickerGate(content: @Composable () -> Unit) {
    val vm: AccountViewModel = hiltViewModel()
    val pending by vm.pickerPending.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        content()
        if (pending && profiles.size > 1) {
            Box(Modifier.fillMaxSize().background(Bg.base).statusBarsPadding().navigationBarsPadding()) {
                ProfilePicker(profiles, onPick = vm::select)
            }
        }
    }
}

@Composable
fun ProfilePicker(profiles: List<Profile>, onPick: (Profile) -> Unit) {
    Column(Modifier.fillMaxSize().padding(Space.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Who's watching?", style = AppType.title)
        Spacer(Modifier.height(Space.xxl))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.xl),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(profiles, key = { it.id }) { p ->
                Column(Modifier.clip(Radius.lg).clickable { onPick(p) }.padding(Space.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarImage(p.avatar, 104.dp)
                    Spacer(Modifier.height(Space.sm))
                    Text(p.name, style = AppType.bodyStrong, maxLines = 1)
                    if (p.kids) Text("Kids", style = AppType.caption)
                }
            }
        }
    }
}

@Composable
private fun field(value: String, onChange: (String) -> Unit, hint: String, password: Boolean = false, type: KeyboardType = KeyboardType.Text) {
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(hint, style = AppType.body.copy(color = Ink.faint)) },
        textStyle = AppType.body.copy(color = Ink.high),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else type),
        modifier = Modifier.fillMaxWidth().clip(Radius.md),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Bg.raised, unfocusedContainerColor = Bg.raised,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Accent.main, focusedTextColor = Ink.high, unfocusedTextColor = Ink.high
        )
    )
}

/**
 * Sign in / register (optional, never forced) plus, when signed in: profiles, "sign in a TV" and sign out.
 * [pairCode] comes from a deep link (reelm://pair/<code>) and is offered for approval.
 */
@Composable
fun AccountScreen(onBack: () -> Unit, pairCode: String? = null, vm: AccountViewModel = hiltViewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf(pairCode.orEmpty()) }
    LaunchedEffect(session?.token) { if (session != null) vm.refresh() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg, vertical = Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Ink.high) }
            Text(if (session == null) "Sign in" else "Account", style = AppType.heading)
        }
        Spacer(Modifier.height(Space.lg))

        val s = session
        if (s == null) {
            SignInForm(ui.busy, vm::signIn)
        } else {
            Text("Signed in as @${s.user.username}", style = AppType.body)
            Spacer(Modifier.height(Space.xl))
            Text("Profiles", style = AppType.heading)
            Spacer(Modifier.height(Space.md))
            profiles.forEach { p ->
                val active = p.id == s.activeProfile?.id
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.xs).clip(Radius.lg).background(if (active) Accent.faint else Bg.raised)
                        .clickable { vm.select(p) }.padding(Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarImage(p.avatar, 48.dp, ring = active)
                    Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                        Text(p.name, style = AppType.bodyStrong)
                        Text(if (active) "Watching now" else if (p.kids) "Kids" else "Tap to switch", style = AppType.caption)
                    }
                    IconButton(onClick = { editing = p }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${p.name}", tint = Ink.body) }
                }
            }
            if (profiles.size < 5) {
                Spacer(Modifier.height(Space.sm))
                SecondaryButton("Add profile", onClick = { creating = true }, icon = Icons.Rounded.Add)
            }

            Spacer(Modifier.height(Space.xxl))
            Text("Sign in a TV", style = AppType.heading)
            Text("Open Reelm on the TV, choose Sign in with phone, then scan its QR code or type the code here.", style = AppType.caption, modifier = Modifier.padding(vertical = Space.sm))
            field(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) }, "TV code")
            Spacer(Modifier.height(Space.sm))
            PrimaryButton("Approve TV", onClick = { vm.claim(code); code = "" }, enabled = code.length >= 6 && !ui.busy)

            Spacer(Modifier.height(Space.xxl))
            TextAction("Sign out", onClick = vm::signOut)
        }

        ui.error?.let { Text(it, style = AppType.body.copy(color = Accent.danger), modifier = Modifier.padding(top = Space.lg)) }
        ui.message?.let { Text(it, style = AppType.body.copy(color = Accent.live), modifier = Modifier.padding(top = Space.lg)) }
    }

    if (creating || editing != null) {
        ProfileEditor(
            initial = editing, canDelete = profiles.size > 1,
            onDismiss = { creating = false; editing = null },
            onSave = { name, avatar, kids -> vm.save(editing?.id, name, avatar, kids); creating = false; editing = null },
            onDelete = { editing?.let { vm.delete(it.id) }; editing = null }
        )
    }
}

@Composable
private fun SignInForm(busy: Boolean, submit: (String, String, Boolean) -> Unit) {
    var register by rememberSaveable { mutableStateOf(false) }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    Text("An account keeps your watch history in sync across your phone and TV. It is optional.", style = AppType.body)
    Spacer(Modifier.height(Space.lg))
    field(user, { user = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' } }, "Username (a-z, 0-9, _)")
    Spacer(Modifier.height(Space.sm))
    field(pass, { pass = it }, if (register) "Password (8+ characters)" else "Password", password = true)
    Spacer(Modifier.height(Space.lg))
    PrimaryButton(if (register) "Create account" else "Sign in", onClick = { submit(user, pass, register) }, enabled = !busy && user.length >= 3 && pass.length >= 8)
    Spacer(Modifier.height(Space.sm))
    TextAction(if (register) "I already have an account" else "Create an account", onClick = { register = !register })
}

/** Add / edit a profile: name, kids switch and the Fluent Emoji avatar grid. */
@Composable
private fun ProfileEditor(initial: Profile?, canDelete: Boolean, onDismiss: () -> Unit, onSave: (String, String, Boolean) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var avatar by remember { mutableStateOf(initial?.avatar ?: FluentEmojis.all.first().id) }
    var kids by remember { mutableStateOf(initial?.kids ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Bg.raised,
        title = { Text(if (initial == null) "New profile" else "Edit profile", style = AppType.heading) },
        text = {
            Column {
                field(name, { name = it.take(20) }, "Name")
                Spacer(Modifier.height(Space.md))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kids profile", style = AppType.body, modifier = Modifier.weight(1f))
                    Switch(checked = kids, onCheckedChange = { kids = it })
                }
                Spacer(Modifier.height(Space.md))
                LazyVerticalGrid(columns = GridCells.Fixed(4), horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.height(260.dp)) {
                    items(FluentEmojis.all, key = { it.id }) { e ->
                        AvatarImage(e.id, 56.dp, ring = e.id == avatar, modifier = Modifier.clickable { avatar = e.id })
                    }
                }
            }
        },
        confirmButton = { TextAction("Save", onClick = { if (name.isNotBlank()) onSave(name, avatar, kids) }) },
        dismissButton = {
            Row {
                if (initial != null && canDelete) TextAction("Delete", onClick = onDelete)
                TextAction("Cancel", onClick = onDismiss)
            }
        }
    )
}
