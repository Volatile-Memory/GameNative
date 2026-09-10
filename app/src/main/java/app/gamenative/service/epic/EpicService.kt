package app.gamenative.service.epic

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.gamenative.PluviaApp
import app.gamenative.data.DownloadInfo
import app.gamenative.data.EpicCredentials
import app.gamenative.data.EpicGame
import app.gamenative.data.EpicGameToken
import app.gamenative.events.AndroidEvent
import app.gamenative.service.NotificationHelper
import com.winlator.container.Container
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Epic Games Service - thin foreground service shell that delegates to injected EpicManager.
 */
@AndroidEntryPoint
class EpicService : Service() {

    @Inject
    lateinit var epicManager: EpicManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backgroundSyncJob: Job? = null
    private val onEndProcess: (AndroidEvent.EndProcess) -> Unit = { stop() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.tag("Epic").i("[EpicService] Service created")

        epicManager.onSyncStatusChanged = { inProgress ->
            if (inProgress) {
                notificationHelper.showSyncing(NotificationHelper.NOTIFICATION_ID_EPIC)
            } else {
                notificationHelper.showIdle(NotificationHelper.NOTIFICATION_ID_EPIC)
            }
        }

        epicManager.onDownloadTracked = { downloadInfo, title ->
            notificationHelper.trackDownload(downloadInfo, title, NotificationHelper.NOTIFICATION_ID_EPIC)
        }

        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)
        PluviaApp.events.emit(AndroidEvent.ServiceReady)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.tag("EPIC").d("onStartCommand() - action: ${intent?.action}")

        val notification = notificationHelper.createServiceNotification(NotificationHelper.NOTIFICATION_ID_EPIC, "Connected")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.NOTIFICATION_ID_EPIC, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_EPIC, notification)
        }
        notificationHelper.markActive(NotificationHelper.NOTIFICATION_ID_EPIC)

        val shouldSync = when (intent?.action) {
            ACTION_MANUAL_SYNC -> {
                Timber.tag("EPIC").i("Manual sync requested - bypassing throttle")
                true
            }

            ACTION_SYNC_LIBRARY -> {
                Timber.tag("EPIC").i("Automatic sync requested")
                true
            }

            null -> {
                val now = System.currentTimeMillis()
                val timeSinceLastSync = now - lastSyncTimestamp
                val shouldResync = !hasPerformedInitialSync || timeSinceLastSync >= SYNC_THROTTLE_MILLIS
                if (shouldResync) {
                    Timber.tag("EPIC").i("Service restarted by Android - performing sync")
                    true
                } else {
                    Timber.tag("EPIC").d("Service restarted by Android - skipping sync (throttled)")
                    false
                }
            }

            else -> false
        }

        if (shouldSync && (backgroundSyncJob == null || backgroundSyncJob?.isActive != true)) {
            backgroundSyncJob?.cancel()
            backgroundSyncJob = serviceScope.launch {
                try {
                    epicManager.setSyncInProgress(true)
                    val syncResult = epicManager.startBackgroundSync(applicationContext)
                    if (syncResult.isFailure) {
                        Timber.w("Failed to start background sync: ${syncResult.exceptionOrNull()?.message}")
                    } else {
                        Timber.tag("EPIC").i("Background library sync completed successfully")
                        lastSyncTimestamp = System.currentTimeMillis()
                        hasPerformedInitialSync = true
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Exception starting background sync")
                } finally {
                    epicManager.setSyncInProgress(false)
                }
            }
        }

        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Timber.tag("EPIC").w("Foreground service timeout reached, restarting...")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag("Epic").i("[EpicService] Service destroyed")
        PluviaApp.events.off<AndroidEvent.EndProcess, Unit>(onEndProcess)

        backgroundSyncJob?.cancel()
        epicManager.setSyncInProgress(false)
        epicManager.onSyncStatusChanged = null
        epicManager.onDownloadTracked = null

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancel(NotificationHelper.NOTIFICATION_ID_EPIC)
        instance = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!epicManager.hasActiveOperations()) {
            Timber.tag("Epic").i("Task removed and no active work — stopping service")
            stopSelf()
        } else {
            Timber.tag("Epic").i("Task removed but active work exists — keeping service alive")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private var instance: EpicService? = null

        private const val ACTION_SYNC_LIBRARY = "app.gamenative.EPIC_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "app.gamenative.EPIC_MANUAL_SYNC"
        private const val SYNC_THROTTLE_MILLIS = 15 * 60 * 1000L // 15 minutes

        private var lastSyncTimestamp: Long = 0L
        private var hasPerformedInitialSync: Boolean = false

        val isRunning: Boolean
            get() = instance != null

        fun start(context: Context) {
            Timber.tag("EPIC").d("Starting service...")
            if (isRunning) {
                Timber.tag("EPIC").d("[EpicService] Service already running, skipping start")
                return
            }

            if (!hasPerformedInitialSync) {
                Timber.tag("EPIC").i("[EpicService] First-time start - starting service with initial sync")
                val intent = Intent(context, EpicService::class.java)
                intent.action = ACTION_SYNC_LIBRARY
                context.startForegroundService(intent)
                return
            }

            val now = System.currentTimeMillis()
            val timeSinceLastSync = now - lastSyncTimestamp

            val intent = Intent(context, EpicService::class.java)
            if (timeSinceLastSync >= SYNC_THROTTLE_MILLIS) {
                Timber.tag("EPIC").i("[EpicService] Starting service with automatic sync (throttle passed)")
                intent.action = ACTION_SYNC_LIBRARY
            } else {
                val remainingMinutes = (SYNC_THROTTLE_MILLIS - timeSinceLastSync) / 1000 / 60
                Timber.tag("EPIC").i("Starting service without sync - throttled (${remainingMinutes}min remaining)")
            }
            context.startForegroundService(intent)
        }

        fun triggerLibrarySync(context: Context) {
            Timber.tag("EPIC").i("Triggering manual library sync (bypasses throttle)")
            val intent = Intent(context, EpicService::class.java)
            intent.action = ACTION_MANUAL_SYNC
            context.startForegroundService(intent)
        }

        fun stop() {
            instance?.stopSelf()
        }

        fun getInstance(): EpicService? = instance

        // ── Forwarding helpers delegating to injected EpicManager ─────────────

        fun hasActiveOperations(): Boolean = instance?.epicManager?.hasActiveOperations() ?: false

        fun isSyncInProgress(): Boolean = instance?.epicManager?.isSyncInProgress() ?: false

        fun hasActiveDownload(): Boolean = instance?.epicManager?.hasActiveDownload() ?: false

        fun getCurrentlyDownloadingGame(): Int? = instance?.epicManager?.getCurrentlyDownloadingGame()

        fun getDownloadInfo(appId: Int): DownloadInfo? = instance?.epicManager?.getDownloadInfo(appId)

        fun getActiveDownloads(): Map<Int, DownloadInfo> = instance?.epicManager?.getActiveDownloads() ?: emptyMap()

        fun hasPartialDownload(context: Context, appId: Int): Boolean =
            instance?.epicManager?.hasPartialDownload(context, appId) ?: false

        suspend fun getPartialDownloads(): List<Int> =
            instance?.epicManager?.getPartialDownloads() ?: emptyList()

        suspend fun deleteGame(context: Context, appId: Int): Result<Unit> =
            instance?.epicManager?.deleteGame(context, appId) ?: Result.failure(Exception("EpicService not running"))

        suspend fun cleanupDownload(context: Context, appId: Int) {
            instance?.epicManager?.cleanupDownload(context, appId)
        }

        fun cancelDownload(appId: Int): Boolean =
            instance?.epicManager?.cancelDownload(appId) ?: false

        fun getEpicGameOf(appId: Int): EpicGame? =
            instance?.epicManager?.getEpicGameOf(appId)

        fun getEpicGameByAppName(appName: String): EpicGame? =
            instance?.epicManager?.getEpicGameByAppName(appName)

        fun getDLCForGame(appId: Int): List<EpicGame> =
            instance?.epicManager?.getDLCForGame(appId) ?: emptyList()

        suspend fun updateEpicGame(game: EpicGame) {
            instance?.epicManager?.updateEpicGame(game)
        }

        fun isGameInstalled(context: Context, appId: Int): Boolean =
            instance?.epicManager?.isGameInstalled(context, appId) ?: false

        fun getInstallPath(appId: Int): String? =
            instance?.epicManager?.getInstallPath(appId)

        fun updateInstallPath(appId: Int, path: String) {
            instance?.epicManager?.updateInstallPath(appId, path)
        }

        suspend fun getInstalledExe(appId: Int): String =
            instance?.epicManager?.getInstalledExe(appId) ?: ""

        suspend fun getLaunchExecutable(containerId: String): String =
            instance?.epicManager?.getLaunchExecutable(containerId) ?: ""

        suspend fun refreshLibrary(context: Context): Result<Int> =
            instance?.epicManager?.refreshLibrary(context) ?: Result.failure(Exception("EpicService not running"))

        suspend fun fetchManifestSizes(context: Context, appId: Int): EpicManager.ManifestSizes =
            instance?.epicManager?.fetchManifestSizes(context, appId) ?: EpicManager.ManifestSizes(0L, 0L)

        fun downloadGame(
            context: Context,
            appId: Int,
            dlcGameIds: List<Int>,
            installPath: String,
            containerLanguage: String,
        ): Result<DownloadInfo> =
            instance?.epicManager?.downloadGame(context, appId, dlcGameIds, installPath, containerLanguage)
                ?: Result.failure(Exception("EpicService not running"))

        suspend fun refreshSingleGame(appId: Int, context: Context): Result<EpicGame?> =
            instance?.epicManager?.refreshSingleGame(appId, context) ?: Result.failure(Exception("EpicService not running"))

        suspend fun getGameLaunchToken(
            context: Context,
            namespace: String? = null,
            catalogItemId: String? = null,
            requiresOwnershipToken: Boolean = false,
        ): Result<EpicGameToken> =
            instance?.epicManager?.getGameLaunchToken(namespace, catalogItemId, requiresOwnershipToken, context)
                ?: EpicAuthManager.getGameLaunchToken(context, namespace, catalogItemId, requiresOwnershipToken)

        suspend fun buildLaunchParameters(
            context: Context,
            container: Container,
            game: EpicGame,
            offline: Boolean = false,
            languageCode: String = "en-US",
        ): Result<List<String>> =
            instance?.epicManager?.buildLaunchParameters(container, game, offline, languageCode, context)
                ?: EpicGameLauncher.buildLaunchParameters(context, container, game, offline, languageCode)

        fun cleanupLaunchTokens(context: Context, container: Container? = null) {
            instance?.epicManager?.cleanupLaunchTokens(container, context) ?: EpicGameLauncher.cleanupOwnershipTokens(context, container)
        }

        suspend fun installOverlay(
            context: Context,
            container: Container,
            forceReinstall: Boolean = false,
            onProgress: ((Int, Int) -> Unit)? = null,
        ): Result<Unit> =
            instance?.epicManager?.installOverlay(container, forceReinstall, onProgress, context)
                ?: Result.failure(Exception("EpicService not running"))

        suspend fun removeOverlay(context: Context, container: Container): Result<Unit> =
            instance?.epicManager?.removeOverlay(container, context)
                ?: Result.failure(Exception("EpicService not running"))

        fun hasStoredCredentials(context: Context): Boolean =
            instance?.epicManager?.hasStoredCredentials(context) ?: EpicAuthManager.hasStoredCredentials(context)

        suspend fun getStoredCredentials(context: Context): Result<EpicCredentials> =
            instance?.epicManager?.getStoredCredentials(context) ?: EpicAuthManager.getStoredCredentials(context)

        suspend fun authenticateWithCode(context: Context, authorizationCode: String): Result<EpicCredentials> =
            instance?.epicManager?.authenticateWithCode(authorizationCode, context) ?: EpicAuthManager.authenticateWithCode(context, authorizationCode)

        suspend fun logout(context: Context): Result<Unit> {
            val result = instance?.epicManager?.logout(context)
                ?: run {
                    val cleared = EpicAuthManager.clearStoredCredentials(context)
                    if (cleared) Result.success(Unit) else Result.failure(Exception("Failed to clear credentials"))
                }
            stop()
            return result
        }

        fun getAccountId(): String? =
            instance?.epicManager?.getAccountId() ?: null
    }
}
