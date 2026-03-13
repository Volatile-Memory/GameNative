package app.gamenative.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import app.gamenative.MainActivity
import app.gamenative.PrefManager
import app.gamenative.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val DOWNLOAD_NOTIFICATION_ID_BASE = 1000
        private const val COALESCED_DOWNLOAD_NOTIFICATION_ID = 2000

        private const val CHANNEL_ID = "pluvia_foreground_service"
        private const val CHANNEL_NAME = "GameNative Foreground Service"
        private const val NOTIFICATION_ID = 1

        const val ACTION_EXIT = "com.oxgames.pluvia.EXIT"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Allows to display GameNative foreground notifications"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun notify(content: String) {
        val notification = createForegroundNotification(content)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun createForegroundNotification(content: String): Notification {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "pluvia://home".toUri(),
            context,
            MainActivity::class.java,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(context, SteamService::class.java).apply {
            action = ACTION_EXIT
        }
        val stopPendingIntent = PendingIntent.getForegroundService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val smallIconRes = if (PrefManager.useAltNotificationIcon) {
            R.drawable.ic_notification_alt
        } else {
            R.drawable.ic_notification
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(smallIconRes)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Exit", stopPendingIntent) // 0 = no icon
            .build()
    }

    fun updateDownloadNotifications(downloads: List<ActiveDownload>) {
        if (downloads.isEmpty()) {
            cancelAllDownloadNotifications()
            return
        }

        val smallIconRes = if (PrefManager.useAltNotificationIcon) {
            R.drawable.ic_notification_alt
        } else {
            R.drawable.ic_notification
        }

        // Coalesce if more than 5 downloads
        if (downloads.size > 5) {
            cancelAllDownloadNotifications() // clear individual ones

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("Downloading ${downloads.size} games")
                .setSmallIcon(smallIconRes)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)

            val inboxStyle = NotificationCompat.InboxStyle()
            var totalProgress = 0f
            downloads.forEach { download ->
                val progressPercent = (download.downloadInfo.getProgress() * 100).toInt()
                inboxStyle.addLine("${download.title}: $progressPercent%")
                totalProgress += download.downloadInfo.getProgress()
            }

            val averageProgress = ((totalProgress / downloads.size) * 100).toInt()
            builder.setContentText("$averageProgress% overall")
            builder.setStyle(inboxStyle)
            builder.setProgress(100, averageProgress, false)

            val intent = Intent(
                Intent.ACTION_VIEW,
                "pluvia://home".toUri(),
                context,
                MainActivity::class.java,
            ).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setContentIntent(pendingIntent)

            notificationManager.notify(COALESCED_DOWNLOAD_NOTIFICATION_ID, builder.build())
        } else {
            // Cancel coalesced notification if it exists
            notificationManager.cancel(COALESCED_DOWNLOAD_NOTIFICATION_ID)

            // Collect active download IDs
            val activeIds = mutableSetOf<Int>()

            downloads.forEachIndexed { index, download ->
                val notificationId = DOWNLOAD_NOTIFICATION_ID_BASE + index
                activeIds.add(notificationId)

                val progressPercent = (download.downloadInfo.getProgress() * 100).toInt()
                val statusText = download.downloadInfo.getStatusMessageFlow().value ?: "Downloading..."

                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setContentTitle(download.title)
                    .setContentText("$statusText ($progressPercent%)")
                    .setSmallIcon(smallIconRes)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setProgress(100, progressPercent, false)

                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "pluvia://home".toUri(),
                    context,
                    MainActivity::class.java,
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.setContentIntent(pendingIntent)

                notificationManager.notify(notificationId, builder.build())
            }

            // Cancel old notifications if we dropped below previous count
            for (id in DOWNLOAD_NOTIFICATION_ID_BASE until (DOWNLOAD_NOTIFICATION_ID_BASE + 5)) {
                if (!activeIds.contains(id)) {
                    notificationManager.cancel(id)
                }
            }
        }
    }

    fun cancelAllDownloadNotifications() {
        notificationManager.cancel(COALESCED_DOWNLOAD_NOTIFICATION_ID)
        for (id in DOWNLOAD_NOTIFICATION_ID_BASE until (DOWNLOAD_NOTIFICATION_ID_BASE + 5)) {
            notificationManager.cancel(id)
        }
    }
}
