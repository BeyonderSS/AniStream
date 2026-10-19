package com.anistream.feature.downloads

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Space
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/** Full-screen offline playback of a downloaded .ts (+ sibling .vtt). Local files need no Referer. */
@Composable
fun LocalPlayerDialog(filePath: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(filePath) {
        val subs = File(filePath.removeSuffix(".ts") + ".vtt")
        val item = MediaItem.Builder()
            .setUri(Uri.fromFile(File(filePath)))
            .setSubtitleConfigurations(
                if (subs.exists()) listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subs))
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                ) else emptyList()
            )
            .build()
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(item)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player; setShowSubtitleButton(true) } },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.statusBarsPadding().padding(Space.sm).background(Color.Black.copy(alpha = 0.7f))
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close player", tint = Ink.high)
            }
        }
    }
}
