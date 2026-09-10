package app.gamenative.service.gog

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.gamenative.PluviaApp
import app.gamenative.data.DownloadInfo
import app.gamenative.data.GOGCredentials
import app.gamenative.data.GOGGame
import app.gamenative.data.LaunchInfo
import app.gamenative.data.LibraryItem
import app.gamenative.events.AndroidEvent
import app.gamenative.service.NotificationHelper
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
 * GOG Service - thin foreground service shell that delegates to injected GOGManager.
 */
@AndroidEntryPoint
class GOGService : Service() {

    @Inject
    lateinit var gogManager: GOGManager

    val gogDownloadManager get() = gogManager.gogDownloadManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backgroundSyncJob: Job? = null
    private val onEndProcess: (AndroidEvent.EndProcess) -> Unit = { stop() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Timber.tag("GOG").i("[GOGService] Service created")

        gogManager.onSyncStatusChanged = { inProgress ->
            if (inProgress) {
                notificationHelper.showSyncing(NotificationHelper.NOTIFICATION_ID_GOG)
            } else {
                notificationHelper.showIdle(NotificationHelper.NOTIFICATION_ID_GOG)
            }
        }

        gogManager.onDownloadTracked = { downloadInfo, title ->
            notificationHelper.trackDownload(downloadInfo, title, NotificationHelper.NOTIFICATION_ID_GOG)
        }

        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)
        PluviaApp.events.emit(AndroidEvent.ServiceReady)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("[GOGService] onStartCommand() - action: ${intent?.action}")

        val notification = notificationHelper.createServiceNotification(NotificationHelper.NOTIFICATION_ID_GOG, "Connected")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.NOTIFICATION_ID_GOG, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_GOG, notification)
        }
        notificationHelper.markActive(NotificationHelper.NOTIFICATION_ID_GOG)

        val shouldSync = when (intent?.action) {
            ACTION_MANUAL_SYNC -> {
                Timber.i("[GOGService] Manual sync requested - bypassing throttle")
                true
            }

            ACTION_SYNC_LIBRARY -> {
                Timber.i("[GOGService] Automatic sync requested")
                true
            }

            null -> {
                val now = System.currentTimeMillis()
                val timeSinceLastSync = now - lastSyncTimestamp
                val shouldResync = !hasPerformedInitialSync || timeSinceLastSync >= SYNC_THROTTLE_MILLIS
                if (shouldResync) {
                    Timber.i("[GOGService] Service restarted by Android - performing sync")
                    true
                } else {
                    Timber.d("[GOGService] Service restarted by Android - skipping sync (throttled)")
                    false
                }
            }

            else -> false
        }

        if (shouldSync && (backgroundSyncJob == null || backgroundSyncJob?.isActive != true)) {
            backgroundSyncJob?.cancel()
            backgroundSyncJob = serviceScope.launch {
                try {
                    gogManager.setSyncInProgress(true)
                    val syncResult = gogManager.startBackgroundSync(applicationContext)
                    if (syncResult.isFailure) {
                        Timber.w("Failed to start background sync: ${syncResult.exceptionOrNull()?.message}")
                    } else {
                        Timber.i("[GOGService] Background library sync completed successfully")
                        lastSyncTimestamp = System.currentTimeMillis()
                        hasPerformedInitialSync = true
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Exception starting background sync")
                } finally {
                    gogManager.setSyncInProgress(false)
                }
            }
        }

        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Timber.w("Foreground service timeout reached, restarting...")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.tag("GOG").i("[GOGService] Service destroyed")
        PluviaApp.events.off<AndroidEvent.EndProcess, Unit>(onEndProcess)

        backgroundSyncJob?.cancel()
        gogManager.setSyncInProgress(false)
        gogManager.onSyncStatusChanged = null
        gogManager.onDownloadTracked = null

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancel(NotificationHelper.NOTIFICATION_ID_GOG)
        instance = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!gogManager.hasActiveOperations()) {
            Timber.i("Task removed and no active work — stopping service")
            stopSelf()
        } else {
            Timber.i("Task removed but active work exists — keeping service alive")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_SYNC_LIBRARY = "app.gamenative.GOG_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "app.gamenative.GOG_MANUAL_SYNC"
        private const val SYNC_THROTTLE_MILLIS = 15 * 60 * 1000L // 15 minutes

        private var instance: GOGService? = null
        private var lastSyncTimestamp: Long = 0L
        private var hasPerformedInitialSync: Boolean = false

        val isRunning: Boolean
            get() = instance != null

        fun start(context: Context) {
            if (isRunning) {
                Timber.d("[GOGService] Service already running, skipping start")
                return
            }

            if (!hasPerformedInitialSync) {
                Timber.i("[GOGService] First-time start - starting service with initial sync")
                val intent = Intent(context, GOGService::class.java)
                intent.action = ACTION_SYNC_LIBRARY
                context.startForegroundService(intent)
                return
            }

            val now = System.currentTimeMillis()
            val timeSinceLastSync = now - lastSyncTimestamp

            val intent = Intent(context, GOGService::class.java)
            if (timeSinceLastSync >= SYNC_THROTTLE_MILLIS) {
                Timber.i("[GOGService] Starting service with automatic sync (throttle passed)")
                intent.action = ACTION_SYNC_LIBRARY
            } else {
                val remainingMinutes = (SYNC_THROTTLE_MILLIS - timeSinceLastSync) / 1000 / 60
                Timber.d("[GOGService] Starting service without sync - throttled (${remainingMinutes}min remaining)")
            }
            context.startForegroundService(intent)
        }

        fun triggerLibrarySync(context: Context) {
            Timber.i("[GOGService] Triggering manual library sync (bypasses throttle)")
            val intent = Intent(context, GOGService::class.java)
            intent.action = ACTION_MANUAL_SYNC
            context.startForegroundService(intent)
        }

        fun stop() {
            instance?.stopSelf()
        }

        fun getInstance(): GOGService? = instance

        // ── Forwarding helpers delegating to injected GOGManager ─────────────

        fun hasActiveOperations(): Boolean = instance?.gogManager?.hasActiveOperations() ?: false

        fun isSyncInProgress(): Boolean = instance?.gogManager?.isSyncInProgress() ?: false

        fun hasActiveDownload(): Boolean = instance?.gogManager?.hasActiveDownload() ?: false

        fun getCurrentlyDownloadingGame(): String? = instance?.gogManager?.getCurrentlyDownloadingGame()

        fun getDownloadInfo(gameId: String): DownloadInfo? = instance?.gogManager?.getDownloadInfo(gameId)

        fun getActiveDownloads(): Map<String, DownloadInfo> = instance?.gogManager?.getActiveDownloads() ?: emptyMap()

        fun hasPartialDownload(gameId: String, fallbackTitle: String? = null): Boolean =
            instance?.gogManager?.hasPartialDownload(gameId, fallbackTitle) ?: false

        suspend fun getPartialDownloads(): List<String> =
            instance?.gogManager?.getPartialDownloads() ?: emptyList()

        fun cleanupDownload(gameId: String) {
            instance?.gogManager?.cleanupDownload(gameId)
        }

        fun cancelDownload(gameId: String): Boolean =
            instance?.gogManager?.cancelDownload(gameId) ?: false

        fun getGOGGameOf(gameId: String): GOGGame? =
            instance?.gogManager?.getGOGGameOf(gameId)

        suspend fun updateGOGGame(game: GOGGame) {
            instance?.gogManager?.updateGOGGame(game)
        }

        fun isGameInstalled(gameId: String): Boolean =
            instance?.gogManager?.isGameInstalled(gameId) ?: false

        fun getInstallPath(gameId: String): String? =
            instance?.gogManager?.getInstallPath(gameId)

        fun updateInstallPath(gameId: String, path: String) {
            instance?.gogManager?.updateInstallPath(gameId, path)
        }

        fun verifyInstallation(gameId: String): Pair<Boolean, String?> =
            instance?.gogManager?.verifyInstallation(gameId) ?: Pair(false, "Service not available")

        suspend fun getInstalledExe(libraryItem: LibraryItem): String =
            instance?.gogManager?.getInstalledExe(libraryItem) ?: ""

        suspend fun getLaunchExecutable(appId: String, container: com.winlator.container.Container): String =
            instance?.gogManager?.getLaunchExecutable(appId, container) ?: ""

        fun getGogWineStartCommand(
            libraryItem: LibraryItem,
            container: com.winlator.container.Container,
            bootToContainer: Boolean,
            appLaunchInfo: LaunchInfo?,
            envVars: com.winlator.core.envvars.EnvVars,
            guestProgramLauncherComponent: com.winlator.xenvironment.components.GuestProgramLauncherComponent,
            gameId: Int,
        ): String =
            instance?.gogManager?.getGogWineStartCommand(
                libraryItem, container, bootToContainer, appLaunchInfo, envVars, guestProgramLauncherComponent, gameId,
            ) ?: "\"explorer.exe\""

        suspend fun refreshLibrary(context: Context): Result<Int> =
            instance?.gogManager?.refreshLibrary(context) ?: Result.failure(Exception("Service not available"))

        fun downloadGame(
            context: Context,
            gameId: String,
            installPath: String,
            containerLanguage: String,
        ): Result<DownloadInfo?> =
            instance?.gogManager?.downloadGame(context, gameId, installPath, containerLanguage)
                ?: Result.failure(Exception("Service not available"))

        suspend fun refreshSingleGame(gameId: String, context: Context): Result<GOGGame?> =
            instance?.gogManager?.refreshSingleGame(gameId, context) ?: Result.failure(Exception("Service not available"))

        suspend fun deleteGame(context: Context, libraryItem: LibraryItem): Result<Unit> =
            instance?.gogManager?.deleteGame(context, libraryItem) ?: Result.failure(Exception("Service not available"))

        suspend fun syncCloudSaves(
            context: Context,
            appId: String,
            preferredAction: String = "none",
        ): Boolean =
            instance?.gogManager?.syncCloudSaves(context, appId, preferredAction) ?: false

        suspend fun detectCloudSaveConflict(
            context: Context,
            appId: String,
        ): GOGManager.GogConflict? =
            instance?.gogManager?.detectCloudSaveConflict(context, appId)

        fun hasStoredCredentials(context: Context): Boolean =
            instance?.gogManager?.hasStoredCredentials(context) ?: GOGAuthManager.hasStoredCredentials(context)

        suspend fun getStoredCredentials(context: Context): Result<GOGCredentials> =
            instance?.gogManager?.getStoredCredentials(context) ?: GOGAuthManager.getStoredCredentials(context)

        suspend fun authenticateWithCode(context: Context, authorizationCode: String): Result<GOGCredentials> =
            instance?.gogManager?.authenticateWithCode(authorizationCode, context) ?: GOGAuthManager.authenticateWithCode(context, authorizationCode)

        suspend fun validateCredentials(context: Context): Result<Boolean> =
            instance?.gogManager?.validateCredentials(context) ?: GOGAuthManager.validateCredentials(context)

        fun clearStoredCredentials(context: Context): Boolean =
            instance?.gogManager?.clearStoredCredentials(context) ?: GOGAuthManager.clearStoredCredentials(context)

        suspend fun logout(context: Context): Result<Unit> {
            val r = instance?.gogManager?.logout(context) ?: run {
                clearStoredCredentials(context)
                Result.success(Unit)
            }
            stop()
            return r
        }
    }
}
