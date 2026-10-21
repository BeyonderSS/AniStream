package com.anistream.feature.tv

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.anistream.core.account.AccountRepository
import com.anistream.core.account.HistorySync
import com.anistream.core.account.Handoff
import com.anistream.core.account.HubClient
import com.anistream.domain.model.WatchHistory
import com.anistream.core.account.FluentEmojis
import com.anistream.core.account.Profile
import com.anistream.core.account.qrBitmap
import com.anistream.core.ui.theme.Accent
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PairUi(val code: String? = null, val qr: Bitmap? = null, val error: String? = null)

/** TV sign-in: shows a QR for the pairing code and polls until a signed-in phone claims it. The TV never asks for a password. */
@HiltViewModel
class TvAccountViewModel @Inject constructor(val repo: AccountRepository, val hub: HubClient, val sync: HistorySync) : ViewModel() {
    val session = repo.session
    val profiles = repo.profiles
    val pickerPending = repo.pickerPending

    private val _pair = MutableStateFlow(PairUi())
    val pair: StateFlow<PairUi> = _pair.asStateFlow()

    /** Runs until signed in or the screen leaves (the caller's coroutine is cancelled). */
    suspend fun pairLoop() {
        while (true) {
            val p = try { repo.startPair() } catch (e: Exception) {
                _pair.value = PairUi(error = "Can't reach the server"); delay(5_000); continue
            }
            val qr = withContext(Dispatchers.Default) { qrBitmap("${repo.baseUrl}/pair/${p.code}", 360) }
            _pair.value = PairUi(p.code, qr)
            // poll every 3 s; a failed or expired code just gets replaced by a fresh one
            val deadline = if (p.expiresAt > 0) p.expiresAt else System.currentTimeMillis() + 9 * 60_000
            while (System.currentTimeMillis() < deadline) {
                delay(3_000)
                val done = try { repo.pollPair(p.code) } catch (e: Exception) { if ((e as? com.anistream.core.account.ApiException)?.code in 400..499) break else false }
                if (done) return
            }
        }
    }

    /** Continue on TV: store the phone's row (so the player resumes from its progress) and return it. */
    suspend fun applyHandoff(h: Handoff): WatchHistory? = sync.applyHandoff(h)

    fun signOut() { viewModelScope.launch { repo.logout() } }
}

/** D-pad profile cards: amber ring on focus, first card focused. */
@Composable
fun TvProfilePicker(profiles: List<Profile>, activeId: String?, title: String, onPick: (Profile) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(profiles.size) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, style = TvType.headline)
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            profiles.forEachIndexed { i, p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Card(
                        onClick = { onPick(p) },
                        modifier = (if (i == 0) Modifier.focusRequester(first) else Modifier).size(132.dp),
                        shape = CardDefaults.shape(CircleShape),
                        colors = CardDefaults.colors(containerColor = Bg.overlay),
                        scale = CardDefaults.scale(focusedScale = 1.08f),
                        border = CardDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Accent.main), shape = CircleShape))
                    ) {
                        AsyncImage(
                            model = FluentEmojis.urlFor(p.avatar), contentDescription = p.name, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(18.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(p.name, style = TvType.title.copy(color = if (p.id == activeId) Accent.main else Ink.high), maxLines = 1)
                }
            }
        }
    }
}

/** Launch gate: with a signed-in account of more than one profile the picker is shown first. */
@Composable
fun TvProfileGate(vm: TvAccountViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val pending by vm.pickerPending.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        content()
        if (pending && profiles.size > 1) {
            Box(Modifier.fillMaxSize().background(Bg.base)) {
                TvProfilePicker(profiles, null, "Who's watching?") { vm.repo.selectProfile(it.id) }
            }
        }
    }
}

/** Rail destination: QR sign-in when signed out; profile switcher and sign out when signed in. */
@Composable
fun TvAccountScreen(vm: TvAccountViewModel = hiltViewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val s = session
    if (s != null) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { TvProfilePicker(profiles, s.activeProfile?.id, "Signed in as @${s.user.username}") { vm.repo.selectProfile(it.id) } }
            Box(Modifier.fillMaxSize().weight(0.25f), contentAlignment = Alignment.TopCenter) { TvPill("Sign out", onClick = vm::signOut) }
        }
        return
    }
    val pair by vm.pair.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.pairLoop() }
    Row(Modifier.fillMaxSize().padding(horizontal = Overscan), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(300.dp).clip(Radius.md).background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
            pair.qr?.let { Image(it.asImageBitmap(), contentDescription = "QR code to sign in", filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(56.dp))
        Column(Modifier.width(420.dp)) {
            Text("Sign in with phone", style = TvType.headline)
            Spacer(Modifier.height(14.dp))
            Text("Open Reelm on your phone, sign in, then scan this code or enter it under Account > Sign in a TV.", style = TvType.body.copy(color = Ink.body))
            Spacer(Modifier.height(24.dp))
            Text(pair.code ?: pair.error ?: "…", style = TvType.headline.copy(color = Accent.main, letterSpacing = 6.sp))
        }
    }
}
