package com.anistream.core.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.anistream.core.account.FluentEmojis
import com.anistream.core.ui.components.PrimaryButton
import com.anistream.core.ui.components.SecondaryButton
import com.anistream.core.ui.components.TextAction
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space

/** Round Fluent Emoji avatar; [contentDescription] null when a parent already labels it. */
@Composable
fun AvatarImage(avatarId: String, size: Dp, modifier: Modifier = Modifier, ring: Boolean = false, contentDescription: String? = null) {
    AsyncImage(
        model = FluentEmojis.urlFor(avatarId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size).clip(CircleShape).background(Bg.overlay)
            .then(if (ring) Modifier.border(2.dp, Accent.main, CircleShape) else Modifier).padding(size / 8)
    )
}

/**
 * Account settings: who is watching (with a switch back to "Who's watching?"), approving a TV and sign out.
 * Guests get a way into sign in / sign up. [pairCode] comes from a deep link (reelm://pair/<code>) and is ready to approve.
 */
@Composable
fun AccountScreen(onBack: () -> Unit, pairCode: String? = null, vm: AccountViewModel = hiltViewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf(pairCode.orEmpty()) }
    LaunchedEffect(session?.token) { if (session != null) vm.refresh() }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg, vertical = Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink.high) }
            Text("Account", style = AppType.heading, modifier = Modifier.semantics { heading() })
        }
        Spacer(Modifier.height(Space.lg))

        val s = session
        if (s == null) {
            Text("You're watching as a guest", style = AppType.heading)
            Spacer(Modifier.height(Space.sm))
            Text("Your history stays on this phone. Sign in to sync it with your TV and keep separate profiles.", style = AppType.body)
            Spacer(Modifier.height(Space.xl))
            PrimaryButton("Sign in or create account", onClick = vm.repo::openGate, modifier = Modifier.fillMaxWidth())
            return@Column
        }

        val p = s.activeProfile
        Row(
            Modifier.fillMaxWidth().clip(Radius.lg).background(Bg.raised).padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (p != null) AvatarImage(p.avatar, 56.dp, ring = true)
            Column(Modifier.weight(1f).padding(horizontal = Space.md)) {
                Text(p?.name ?: "No profile picked", style = AppType.bodyStrong)
                Text("@${s.user.username}", style = AppType.caption)
            }
            SecondaryButton("Switch", onClick = vm.repo::openGate, icon = Icons.Rounded.SwapHoriz)
        }

        Spacer(Modifier.height(Space.xxl))
        Text("Sign in a TV", style = AppType.heading, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(Space.sm))
        Text("On the TV, choose Sign in with phone. Scan its QR code with your camera, or type the code shown under it.", style = AppType.body)
        Spacer(Modifier.height(Space.lg))
        AuthField(
            code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(8) }, "TV code",
            imeAction = ImeAction.Done, onIme = { if (code.length >= 6) { vm.claim(code); code = "" } }
        )
        Spacer(Modifier.height(Space.sm))
        PrimaryButton("Approve TV", onClick = { vm.claim(code); code = "" }, enabled = code.length >= 6, loading = ui.busy, modifier = Modifier.fillMaxWidth())
        ui.error?.let { Spacer(Modifier.height(Space.md)); ErrorNote(it) }
        ui.message?.let {
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Accent.live, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Space.sm))
                Text(it, style = AppType.body.copy(color = Accent.live))
            }
        }

        Spacer(Modifier.height(Space.xxl))
        TextAction("Sign out", onClick = vm::signOut, color = Accent.danger)
        Spacer(Modifier.height(Space.xl))
    }
}
