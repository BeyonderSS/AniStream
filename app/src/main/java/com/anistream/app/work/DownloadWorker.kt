package com.anistream.app.work

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.anistream.app.navigation.Routes
import com.anistream.core.database.dao.DownloadDao
import com.anistream.core.network.repository.DOWNLOAD_WORKER
import com.anistream.core.network.repository.DownloadRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Named
import kotlin.coroutines.cancellation.CancellationException

private const val MAX_ATTEMPTS = 5

/** Runs one download (HLS or direct file) as a dataSync foreground service; retried with backoff on IOException. */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: DownloadRepositoryImpl,
    private val dao: DownloadDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(DownloadRepositoryImpl.K_ID)?.toLongOrNull() ?: return Result.failure()
        val row = dao.getById(id) ?: return Result.success() // deleted while queued
        val label = row.subtitle ?: row.episodeNumber.takeIf { it > 0 }?.let { "Episode $it" }
        val nid = id.hashCode()
        val input = inputData.keyValueMap.mapValues { it.value.toString() }

        suspend fun showProgress(p: Float) {
            val n = notificationBuilder(applicationContext, CH_DOWNLOADS)
                .setContentTitle(row.animeTitle)
                .setContentText(label)
                .setProgress(100, (p * 100).toInt(), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(0, "Cancel", actionIntent(applicationContext, ACTION_CANCEL, input, nid))
                .build()
            try {
                setForeground(ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC))
            } catch (e: IllegalStateException) {
                // Android 12+ refuses foreground starts from the background; the transfer still runs as plain work.
            }
        }

        showProgress(row.progress)
        return try {
            repo.transfer(inputData) { showProgress(it) }
            postNotification(
                applicationContext, nid + 1,
                notificationBuilder(applicationContext, CH_DOWNLOADS)
                    .setContentTitle("Download finished")
                    .setContentText(listOfNotNull(row.animeTitle, label).joinToString(" · "))
                    .setContentIntent(openAppIntent(applicationContext, Routes.DOWNLOADS, nid))
                    .setAutoCancel(true)
                    .build()
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else failed(id, nid, row.animeTitle, label, input)
        } catch (e: Exception) {
            failed(id, nid, row.animeTitle, label, input)
        }
    }

    private suspend fun failed(id: Long, nid: Int, title: String, label: String?, input: Map<String, String>): Result {
        repo.markFailed(id)
        postNotification(
            applicationContext, nid + 1,
            notificationBuilder(applicationContext, CH_DOWNLOADS)
                .setContentTitle("Download failed")
                .setContentText(listOfNotNull(title, label).joinToString(" · "))
                .setContentIntent(openAppIntent(applicationContext, Routes.DOWNLOADS, nid))
                .addAction(0, "Retry", actionIntent(applicationContext, ACTION_RETRY, input, nid))
                .setAutoCancel(true)
                .build()
        )
        return Result.failure()
    }
}

private const val ACTION_CANCEL = "reelm.download.CANCEL"
private const val ACTION_RETRY = "reelm.download.RETRY"
private const val EXTRA_INPUT = "input"

private fun actionIntent(context: Context, action: String, input: Map<String, String>, requestCode: Int): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, DownloadActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_INPUT, bundleOf(*input.toList().toTypedArray())),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

/** Cancel / Retry buttons on download notifications. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bundle = intent.getBundleExtra(EXTRA_INPUT) ?: return
        val input = bundle.keySet().associateWith { bundle.getString(it) }
        val id = input[DownloadRepositoryImpl.K_ID]?.toLongOrNull() ?: return
        NotificationManagerCompat.from(context).cancel(id.hashCode() + 1)
        val repo = EntryPointAccessors.fromApplication(context, WorkEntryPoint::class.java).downloads()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_CANCEL) repo.cancelDownload(id) else repo.retry(input)
            } finally {
                pending.finish()
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkEntryPoint {
    fun downloads(): DownloadRepositoryImpl
}

@Module
@InstallIn(SingletonComponent::class)
object WorkModule {
    @Provides
    @Named(DOWNLOAD_WORKER)
    fun downloadWorker(): Class<out ListenableWorker> = DownloadWorker::class.java
}
