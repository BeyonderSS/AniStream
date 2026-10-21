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
import com.anistream.feature.party.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** A nav route handed over from a notification tap; the NavGraph navigates to it and clears it. */
object PendingDeepLink {
    var route by mutableStateOf<String?>(null)
}

const val EXTRA_ROUTE = "reelm.route"
const val CH_DOWNLOADS = "downloads"
const val CH_EPISODES = "new_episodes"
const val CH_REMINDERS = "reminders"
const val CH_UPDATES = "updates"

fun createNotificationChannels(context: Context) {
    NotificationManagerCompat.from(context).createNotificationChannelsCompat(
        listOf(
            NotificationChannelCompat.Builder(CH_DOWNLOADS, NotificationManagerCompat.IMPORTANCE_LOW).setName("Downloads").build(),
            NotificationChannelCompat.Builder(CH_EPISODES, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("New episodes").build(),
            NotificationChannelCompat.Builder(CH_REMINDERS, NotificationManagerCompat.IMPORTANCE_LOW).setName("Reminders").build(),
            NotificationChannelCompat.Builder(CH_UPDATES, NotificationManagerCompat.IMPORTANCE_LOW).setName("App updates").build()
        )
    )
}

fun notificationBuilder(context: Context, channel: String): NotificationCompat.Builder =
    NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_reelm)

/** Opens MainActivity, which hands [route] to [PendingDeepLink]; null just brings the app forward. */
fun openAppIntent(context: Context, route: String?, requestCode: Int): PendingIntent = PendingIntent.getActivity(
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

private const val UPDATE_ID = 0x0FDA7E

/** Mirrors [UpdateManager]'s APK download as a notification: progress opens the app (the update dialog), done reopens the installer. */
fun notifyUpdates(context: Context, updates: UpdateManager, scope: CoroutineScope) {
    scope.launch {
        updates.ui.map { ui -> ui.progress?.let { (it * 100).toInt() } to ui.installIntent }
            .distinctUntilChanged()
            .collect { (percent, install) ->
                val builder = notificationBuilder(context, CH_UPDATES)
                when {
                    percent != null -> builder
                        .setContentTitle("Downloading update")
                        .setProgress(100, percent, false)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setContentIntent(openAppIntent(context, null, UPDATE_ID))
                    install != null -> builder
                        .setContentTitle("Update ready")
                        .setContentText("Tap to install")
                        .setAutoCancel(true)
                        .setContentIntent(
                            PendingIntent.getActivity(
                                context, UPDATE_ID, Intent(install).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                            )
                        )
                    else -> return@collect NotificationManagerCompat.from(context).cancel(UPDATE_ID)
                }
                postNotification(context, UPDATE_ID, builder.build())
            }
    }
}
