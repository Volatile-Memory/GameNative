package app.gamenative.service.amazon

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.gamenative.PluviaApp
import app.gamenative.data.AmazonCredentials
import app.gamenative.data.AmazonGame
import app.gamenative.data.DownloadInfo
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
 * Amazon Games foreground service - thin shell delegating to injected AmazonManager.
 */
@AndroidEntryPoint
class AmazonService : Service() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var amazonManager: AmazonManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var backgroundSyncJob: Job? = null

    private val onEndProcess: (AndroidEvent.EndProcess) -> Unit = {
        stop()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        amazonManager.onSyncStatusChanged = { inProgress ->
            if (inProgress) {
                notificationHelper.showSyncing(NotificationHelper.NOTIFICATION_ID_AMAZON)
            } else {
                notificationHelper.showIdle(NotificationHelper.NOTIFICATION_ID_AMAZON)
            }
        }

        amazonManager.onDownloadTracked = { downloadInfo, title ->
            notificationHelper.trackDownload(downloadInfo, title, NotificationHelper.NOTIFICATION_ID_AMAZON)
        }

        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)
        PluviaApp.events.emit(AndroidEvent.ServiceReady)
        Timber.i("[Amazon] Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.createServiceNotification(NotificationHelper.NOTIFICATION_ID_AMAZON, "Connected")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.NOTIFICATION_ID_AMAZON, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_AMAZON, notification)
        }
        notificationHelper.markActive(NotificationHelper.NOTIFICATION_ID_AMAZON)

        val shouldSync = when (intent?.action) {
            ACTION_MANUAL_SYNC -> {
                Timber.i("[Amazon] Manual sync requested — bypassing throttle")
                true
            }
            ACTION_SYNC_LIBRARY -> {
                Timber.i("[Amazon] Automatic sync requested")
                true
            }
            null -> {
                val now = System.currentTimeMillis()
                val timeSinceLastSync = now - lastSyncTimestamp
                val shouldResync = !hasPerformedInitialSync || timeSinceLastSync >= SYNC_THROTTLE_MILLIS
                if (shouldResync) {
                    Timber.i("[Amazon] Service restarted by Android — performing sync")
                } else {
                    Timber.d("[Amazon] Service restarted by Android — skipping sync (throttled)")
                }
                shouldResync
            }
            else -> false
        }

        if (shouldSync && (backgroundSyncJob == null || backgroundSyncJob?.isActive != true)) {
            backgroundSyncJob?.cancel()
            backgroundSyncJob = serviceScope.launch {
                amazonManager.syncLibrary(force = intent?.action == ACTION_MANUAL_SYNC)
                lastSyncTimestamp = System.currentTimeMillis()
                hasPerformedInitialSync = true
            }
        }

        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Timber.w("[Amazon] Foreground service timeout reached, restarting...")
        stopSelf()
    }

    override fun onDestroy() {
        PluviaApp.events.off<AndroidEvent.EndProcess, Unit>(onEndProcess)
        backgroundSyncJob?.cancel()
        amazonManager.setSyncInProgress(false)
        amazonManager.onSyncStatusChanged = null
        amazonManager.onDownloadTracked = null

        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancel(NotificationHelper.NOTIFICATION_ID_AMAZON)
        instance = null
        super.onDestroy()
        Timber.i("[Amazon] Service destroyed")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!amazonManager.hasActiveOperations()) {
            Timber.i("[Amazon] Task removed and no active work — stopping service")
            stopSelf()
        } else {
            Timber.i("[Amazon] Task removed but active work exists — keeping service alive")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val ACTION_SYNC_LIBRARY = "app.gamenative.AMAZON_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "app.gamenative.AMAZON_MANUAL_SYNC"
        private const val SYNC_THROTTLE_MILLIS = 15 * 60 * 1000L // 15 minutes
        private var instance: AmazonService? = null

        private var lastSyncTimestamp: Long = 0L
        private var hasPerformedInitialSync: Boolean = false

        val isRunning: Boolean
            get() = instance != null

        fun isSyncInProgress(): Boolean = instance?.amazonManager?.isSyncInProgress() ?: false

        fun hasActiveOperations(): Boolean = instance?.amazonManager?.hasActiveOperations() ?: false

        fun start(context: Context) {
            if (isRunning) {
                Timber.d("[Amazon] Service already running")
                return
            }

            val intent = Intent(context, AmazonService::class.java)

            if (!hasPerformedInitialSync) {
                Timber.i("[Amazon] First-time start — starting service with initial sync")
                intent.action = ACTION_SYNC_LIBRARY
                context.startForegroundService(intent)
                return
            }

            val now = System.currentTimeMillis()
            val timeSinceLastSync = now - lastSyncTimestamp

            if (timeSinceLastSync >= SYNC_THROTTLE_MILLIS) {
                Timber.i("[Amazon] Starting service with automatic sync (throttle passed)")
                intent.action = ACTION_SYNC_LIBRARY
            } else {
                val remainingMinutes = (SYNC_THROTTLE_MILLIS - timeSinceLastSync) / 1000 / 60
                Timber.i("[Amazon] Starting service without sync — throttled (${remainingMinutes}min remaining)")
            }
            context.startForegroundService(intent)
        }

        fun stop() {
            instance?.stopSelf()
        }

        fun getInstance(): AmazonService? = instance

        fun triggerLibrarySync(context: Context) {
            Timber.i("[Amazon] Manual sync requested — bypassing throttle")
            val intent = Intent(context, AmazonService::class.java)
            intent.action = ACTION_MANUAL_SYNC
            context.startForegroundService(intent)
        }

        // ── Forwarding delegates to injected AmazonManager ────────────────────

        fun hasStoredCredentials(context: Context): Boolean =
            instance?.amazonManager?.hasStoredCredentials(context) ?: AmazonAuthManager.hasStoredCredentials(context)

        suspend fun authenticateWithCode(
            context: Context,
            authCode: String,
        ): Result<AmazonCredentials> =
            instance?.amazonManager?.authenticateWithCode(authCode, context)
                ?: AmazonAuthManager.authenticateWithCode(context, authCode)

        suspend fun logout(context: Context): Result<Unit> {
            val r = instance?.amazonManager?.logout(context) ?: run {
                AmazonAuthManager.logout(context)
                Result.success(Unit)
            }
            stop()
            return r
        }

        suspend fun fetchDownloadSize(productId: String): Long? =
            instance?.amazonManager?.fetchDownloadSize(productId)

        fun isGameInstalled(context: Context, productId: String): Boolean =
            instance?.amazonManager?.isGameInstalled(context, productId) ?: false

        fun isGameInstalledByAppId(context: Context, appId: Int): Boolean =
            instance?.amazonManager?.isGameInstalledByAppId(context, appId) ?: false

        fun getExpectedInstallPathByAppId(context: Context, appId: Int): String? =
            instance?.amazonManager?.getExpectedInstallPathByAppId(context, appId)

        fun hasPartialDownloadByAppId(context: Context, appId: Int): Boolean =
            instance?.amazonManager?.hasPartialDownloadByAppId(context, appId) ?: false

        fun getAmazonGameOf(productId: String): AmazonGame? =
            instance?.amazonManager?.getAmazonGameOf(productId)

        fun getAmazonGameByAppId(appId: Int): AmazonGame? =
            instance?.amazonManager?.getAmazonGameByAppId(appId)

        fun getInstallPath(productId: String): String? =
            instance?.amazonManager?.getInstallPath(productId)

        fun getInstallPathByAppId(appId: Int): String? =
            instance?.amazonManager?.getInstallPathByAppId(appId)

        fun updateInstallPath(appId: Int, path: String) {
            instance?.amazonManager?.updateInstallPath(appId, path)
        }

        fun getProductIdByAppId(appId: Int): String? =
            instance?.amazonManager?.getProductIdByAppId(appId)

        fun getLaunchExecutable(containerId: String): String =
            instance?.amazonManager?.getLaunchExecutable(containerId) ?: ""

        fun getInstalledGamePath(gameId: String): String? =
            instance?.amazonManager?.getInstalledGamePath(gameId)

        suspend fun isUpdatePending(productId: String): Boolean =
            instance?.amazonManager?.isUpdatePending(productId) ?: false

        suspend fun isUpdatePendingByAppId(appId: Int): Boolean =
            instance?.amazonManager?.isUpdatePendingByAppId(appId) ?: false

        fun getDownloadInfo(productId: String): DownloadInfo? =
            instance?.amazonManager?.getDownloadInfo(productId)

        fun getActiveDownloads(): Map<String, DownloadInfo> =
            instance?.amazonManager?.getActiveDownloads() ?: emptyMap()

        suspend fun getPartialDownloads(context: Context): List<String> =
            instance?.amazonManager?.getPartialDownloads(context) ?: emptyList()

        fun getDownloadInfoByAppId(appId: Int): DownloadInfo? =
            instance?.amazonManager?.getDownloadInfoByAppId(appId)

        fun cancelDownloadByAppId(appId: Int): Boolean =
            instance?.amazonManager?.cancelDownloadByAppId(appId) ?: false

        fun hasActiveDownload(): Boolean =
            instance?.amazonManager?.hasActiveDownload() ?: false

        suspend fun downloadGame(
            context: Context,
            productId: String,
            installPath: String,
        ): Result<DownloadInfo> =
            instance?.amazonManager?.downloadGame(context, productId, installPath)
                ?: Result.failure(Exception("Amazon service is not running"))

        fun cancelDownload(productId: String): Boolean =
            instance?.amazonManager?.cancelDownload(productId) ?: false

        suspend fun deleteGame(context: Context, productId: String): Result<Unit> =
            instance?.amazonManager?.deleteGame(context, productId)
                ?: Result.failure(Exception("Amazon service is not running"))

        suspend fun verifyGame(context: Context, productId: String): Result<AmazonManager.VerificationResult> =
            instance?.amazonManager?.verifyGame(context, productId)
                ?: Result.failure(Exception("Amazon service is not running"))
    }
}
