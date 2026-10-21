package com.anistream.core.account.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anistream.core.account.AccountRepository
import com.anistream.core.account.Device
import com.anistream.core.account.HubClient
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.WatchHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HandoffViewModel @Inject constructor(private val account: AccountRepository, private val hub: HubClient) : ViewModel() {
    val enabled get() = account.enabled
    val session = account.session
    val devices = hub.devices
    fun tvs(all: List<Device>) = hub.tvs(all)
    fun send(tv: Device, h: WatchHistory, positionMs: Long): Boolean =
        account.session.value?.activeProfile?.id?.let { hub.sendHandoff(tv, it, h, positionMs) } ?: false
}

/**
 * Player settings-sheet section: "Continue on TV" lists the account's TVs that are online in the hub and
 * sends the current item and position. Renders nothing when accounts are off or nobody is signed in.
 */
@Composable
fun ContinueOnTvSection(history: () -> WatchHistory?, positionMs: () -> Long, onSent: () -> Unit = {}) {
    val vm: HandoffViewModel = hiltViewModel()
    val session by vm.session.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    if (!vm.enabled || session?.activeProfile == null) return
    var failed by remember { mutableStateOf(false) }
    val tvs = vm.tvs(devices)
    Spacer(Modifier.height(Space.xl))
    Text("Continue on TV", style = AppType.heading)
    Spacer(Modifier.height(Space.sm))
    if (tvs.isEmpty()) Text("No TV signed in to your account is online. Open Reelm on the TV first.", style = AppType.caption)
    tvs.forEach { tv ->
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(Radius.md)
                .clickable { failed = !(history()?.let { vm.send(tv, it, positionMs()) } ?: false); if (!failed) onSent() }
                .padding(horizontal = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = Ink.body)
            Text(tv.name.ifBlank { "TV" }, style = AppType.body, modifier = Modifier.padding(start = Space.md))
        }
    }
    if (failed) Text("Couldn't reach the TV", style = AppType.caption)
}

