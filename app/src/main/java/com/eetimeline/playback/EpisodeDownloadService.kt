package com.eetimeline.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler

/** Media3 foreground service which executes and persists episode download requests. */
@UnstableApi
class EpisodeDownloadService : DownloadService(
    NOTIFICATION_ID,
    NOTIFICATION_UPDATE_INTERVAL_MS,
    null,
    0,
    0,
) {
    override fun onCreate() {
        ensureNotificationChannel(this)
        super.onCreate()
    }

    override fun getDownloadManager(): DownloadManager =
        PlaybackDownloadRuntime.downloadManager(this)

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val activeCount = downloads.count { it.state == Download.STATE_DOWNLOADING }
        val title = applicationInfo.loadLabel(packageManager)
        val message = if (activeCount > 0) {
            "Downloading $activeCount ${if (activeCount == 1) "episode" else "episodes"}"
        } else {
            "Preparing episode downloads"
        }
        return NotificationCompat.Builder(this, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(message)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "episode_downloads"
        private const val NOTIFICATION_ID = 4102
        private const val NOTIFICATION_UPDATE_INTERVAL_MS = 1_000L

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(DOWNLOAD_CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    DOWNLOAD_CHANNEL_ID,
                    "Episode downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Offline episode download status"
                },
            )
        }
    }
}
