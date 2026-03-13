package app.gamenative.service

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import app.gamenative.service.amazon.AmazonService
import app.gamenative.service.epic.EpicService
import app.gamenative.service.gog.GOGService
import app.gamenative.data.DownloadInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveDownload(
    val gameId: String,
    val source: String, // "STEAM", "GOG", "EPIC", "AMAZON"
    val title: String,
    val downloadInfo: DownloadInfo
)

@Singleton
class DownloadNotificationManager @Inject constructor(
    private val notificationHelper: NotificationHelper
) {
    private val activeDownloads = ConcurrentHashMap<String, ActiveDownload>()
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun init() {
        val downloadStatusListener: (AndroidEvent.DownloadStatusChanged) -> Unit = {
            updateActiveDownloads()
        }

        PluviaApp.events.on<AndroidEvent.DownloadStatusChanged, Unit>(downloadStatusListener)

        startTracking()
    }

    private fun updateActiveDownloads() {
        val newActiveDownloads = mutableMapOf<String, ActiveDownload>()

        SteamService.getAllDownloadJobs().forEach { (gameId, info) ->
            if (info.isActive()) {
                val title = SteamService.getAppInfoOf(gameId)?.name ?: "Steam Game"
                newActiveDownloads["STEAM_${gameId}"] = ActiveDownload(gameId.toString(), "STEAM", title, info)
            }
        }

        GOGService.getAllActiveDownloads().forEach { (gameId, info) ->
            if (info.isActive()) {
                val title = GOGService.getGOGGameOf(gameId)?.title ?: "GOG Game"
                newActiveDownloads["GOG_${gameId}"] = ActiveDownload(gameId, "GOG", title, info)
            }
        }

        EpicService.getAllActiveDownloads().forEach { (gameId, info) ->
            if (info.isActive()) {
                val title = EpicService.getEpicGameOf(gameId)?.title ?: "Epic Game"
                newActiveDownloads["EPIC_${gameId}"] = ActiveDownload(gameId.toString(), "EPIC", title, info)
            }
        }

        AmazonService.getAllActiveDownloads().forEach { (gameId, info) ->
            if (info.isActive()) {
                val title = AmazonService.getAmazonGameOf(gameId)?.title ?: "Amazon Game"
                newActiveDownloads["AMAZON_${gameId}"] = ActiveDownload(gameId, "AMAZON", title, info)
            }
        }

        activeDownloads.clear()
        activeDownloads.putAll(newActiveDownloads)
    }

    private fun startTracking() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (true) {
                updateActiveDownloads()

                val downloads = activeDownloads.values.toList()
                if (downloads.isNotEmpty()) {
                    notificationHelper.updateDownloadNotifications(downloads)
                } else {
                    notificationHelper.cancelAllDownloadNotifications()
                }

                delay(1000) // Update once per second
            }
        }
    }
}
