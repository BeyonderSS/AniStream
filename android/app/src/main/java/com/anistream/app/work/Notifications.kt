package com.anistream.app.work

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.anistream.app.MainActivity
import com.anistream.app.R

/** A nav route handed over from a notification tap; the NavGraph navigates to it and clears it. */
object PendingDeepLink {
    var route by mutableStateOf<String?>(null)
}

const val EXTRA_ROUTE = "reelm.route"
const val CH_DOWNLOADS = "downloads"
const val CH_EPISODES = "new_episodes"
const val CH_REMINDERS = "reminders"

fun createNotificationChannels(context: Context) {
    NotificationManagerCompat.from(context).createNotificationChannelsCompat(
        listOf(
            NotificationChannelCompat.Builder(CH_DOWNLOADS, NotificationManagerCompat.IMPORTANCE_LOW).setName("Downloads").build(),
            NotificationChannelCompat.Builder(CH_EPISODES, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("New episodes").build(),
            NotificationChannelCompat.Builder(CH_REMINDERS, NotificationManagerCompat.IMPORTANCE_LOW).setName("Reminders").build()
        )
    )
}

fun notificationBuilder(context: Context, channel: String): NotificationCompat.Builder =
    NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_reelm)

/** Opens MainActivity, which hands [route] to [PendingDeepLink]. */
fun openAppIntent(context: Context, route: String, requestCode: Int): PendingIntent = PendingIntent.getActivity(
    context,
    requestCode,
    Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_ROUTE, route)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
)

/** Posts if allowed; silently does nothing when the user denied notifications. */
fun postNotification(context: Context, id: Int, notification: Notification) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    NotificationManagerCompat.from(context).notify(id, notification)
}
