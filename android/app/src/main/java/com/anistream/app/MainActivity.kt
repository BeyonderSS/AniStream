package com.anistream.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.anistream.app.work.EXTRA_ROUTE
import com.anistream.app.work.PendingDeepLink
import com.anistream.app.navigation.AniStreamNavGraph
import com.anistream.app.ui.theme.AniStreamTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) consumeDeepLink(intent)
        requestNotificationsOnce()
        // Transparent bars with light icons; the app is always dark.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            AniStreamTheme {
                AniStreamNavGraph()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?) {
        intent?.getStringExtra(EXTRA_ROUTE)?.let { PendingDeepLink.route = it }
        // reelm://pair/<code> or https://<api host>/pair/<code> -> the account screen with the code ready to approve
        intent?.data?.let { uri ->
            val code = if (uri.scheme == "reelm") uri.lastPathSegment else uri.pathSegments.getOrNull(1)?.takeIf { uri.pathSegments.firstOrNull() == "pair" }
            code?.takeIf { it.matches(Regex("[A-Za-z0-9]{4,12}")) }?.let { PendingDeepLink.route = "account?code=${it.uppercase()}" }
        }
    }

    /** Android 13+: ask once on first launch; a denial just means no notifications. */
    private fun requestNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
