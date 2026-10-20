package com.anistream.feature.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Message
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.anistream.core.ui.theme.AppType
import com.anistream.core.ui.theme.Bg
import com.anistream.core.ui.theme.Ink
import com.anistream.core.ui.theme.Radius
import com.anistream.core.ui.theme.Space
import com.anistream.domain.model.WebEmbed
import kotlinx.coroutines.delay

/** Focuses the player on load so D-pad OK reaches it (the video lives in a cross-origin iframe). */
private const val FOCUS_JS =
    "(function(){var e=document.querySelector('video,#player_iframe,iframe,button,[role=button]');" +
        "if(e){e.setAttribute('tabindex','0');e.focus();}})()"

/**
 * Last-resort web player: the VidSrc-family embed page in a full-screen WebView.
 * No history, resume or downloads. [tv]: D-pad mode (focus on load, Menu switches mirror).
 * Pop-ups and top-level navigations off the mirror hosts are blocked (see [WebEmbed.allows]).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebEmbedPlayer(imdbId: String, season: Int, episode: Int, onBack: () -> Unit, tv: Boolean = false) {
    var mirror by rememberSaveable { mutableIntStateOf(0) }
    val url = WebEmbed.url(WebEmbed.MIRRORS[mirror], imdbId, season, episode)
    var barVisible by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    var firstShow by rememberSaveable { mutableStateOf(true) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current

    fun nextMirror() { mirror = (mirror + 1) % WebEmbed.MIRRORS.size; barVisible = true; poke++ }
    fun hideCustom() { customCallback?.onCustomViewHidden(); customView = null; customCallback = null }

    // Full screen like the native player: landscape (phone) + no system bars.
    DisposableEffect(Unit) {
        if (!tv) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (!tv && activity != null && !activity.isChangingConfigurations) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { webView?.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { webView?.onResume() }
    LaunchedEffect(poke) { delay(if (firstShow) 6000 else 4000); barVisible = false; firstShow = false }

    BackHandler(enabled = customView != null) { hideCustom() }
    BackHandler(enabled = customView == null) { onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean =
                            !WebEmbed.allows(req.url.scheme, req.url.host, req.isForMainFrame)

                        override fun onPageFinished(v: WebView, u: String?) {
                            if (tv) { v.requestFocus(); v.evaluateJavascript(FOCUS_JS, null) }
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(v: View, cb: CustomViewCallback) { customView = v; customCallback = cb }
                        override fun onHideCustomView() { customView = null; customCallback = null }
                        override fun onCreateWindow(v: WebView?, dialog: Boolean, userGesture: Boolean, resultMsg: Message?) = false
                    }
                    if (tv) {
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setOnKeyListener { _, code, e ->
                            if (code == KeyEvent.KEYCODE_MENU && e.action == KeyEvent.ACTION_UP) { nextMirror(); true } else false
                        }
                    }
                    webView = this
                }
            },
            update = { wv -> if (wv.tag != url) { wv.tag = url; wv.loadUrl(url); if (tv) wv.requestFocus() } },
            onRelease = { wv -> wv.stopLoading(); wv.destroy(); webView = null },
            modifier = Modifier.fillMaxSize()
        )

        customView?.let { cv ->
            key(cv) {
                AndroidView(factory = { cv.apply { if (tv) requestFocus() } }, modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }

        // Thin hot zone that brings the bar back (the WebView eats every other touch).
        if (!tv && !barVisible && customView == null) {
            Box(Modifier.fillMaxWidth().height(28.dp).clickable { barVisible = true; poke++ })
        }

        AnimatedVisibility(visible = barVisible && customView == null, enter = fadeIn(), exit = fadeOut()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Bg.base.copy(alpha = 0.9f), Color.Transparent)))
                    .padding(horizontal = if (tv) 48.dp else Space.sm, vertical = if (tv) 24.dp else Space.xs)
            ) {
                if (!tv) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Ink.high)
                    }
                }
                Column(Modifier.weight(1f).padding(start = Space.sm)) {
                    Text("Web player", style = AppType.heading)
                    if (firstShow) Text("Progress isn't saved in the web player", style = AppType.caption)
                }
                if (WebEmbed.MIRRORS.size > 1) {
                    val label = "Mirror ${mirror + 1} of ${WebEmbed.MIRRORS.size}"
                    if (tv) {
                        Text("$label · Menu to switch", style = AppType.caption)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(Radius.pill)
                                .background(Bg.raised)
                                .clickable { nextMirror() }
                                .padding(horizontal = Space.md, vertical = Space.sm)
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Ink.body, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Space.xs))
                            Text(label, style = AppType.label.copy(color = Ink.body))
                        }
                    }
                }
            }
        }
    }
}
