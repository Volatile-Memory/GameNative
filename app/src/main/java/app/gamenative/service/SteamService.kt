package app.gamenative.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import androidx.core.content.ContextCompat
import app.gamenative.BuildConfig
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.data.AppInfo
import app.gamenative.data.DepotInfo
import app.gamenative.data.DownloadInfo
import app.gamenative.data.DownloadingAppInfo
import app.gamenative.data.GameProcessInfo
import app.gamenative.data.LaunchInfo
import app.gamenative.data.PostSyncInfo
import app.gamenative.data.SteamApp
import app.gamenative.data.SteamFriend
import app.gamenative.data.SteamLicense
import app.gamenative.data.SteamUnlockedBranch
import app.gamenative.db.dao.AppInfoDao
import app.gamenative.db.dao.CachedLicenseDao
import app.gamenative.db.dao.ChangeNumbersDao
import app.gamenative.db.dao.DownloadingAppInfoDao
import app.gamenative.db.dao.FileChangeListsDao
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.db.dao.SteamFileHashCacheDao
import app.gamenative.db.dao.SteamLicenseDao
import app.gamenative.db.dao.SteamUnlockedBranchDao
import app.gamenative.di.AppUtilsEntryPoint
import app.gamenative.events.AndroidEvent
import app.gamenative.preferences.AuthPreferences
import app.gamenative.preferences.ContainerPreferences
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.preferences.GeneralPreferences
import app.gamenative.preferences.LibraryPreferences
import app.gamenative.statsgen.Achievement
import dagger.hilt.android.AndroidEntryPoint
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.types.SteamID
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import app.gamenative.enums.SaveLocation
import app.gamenative.enums.SyncResult
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@AndroidEntryPoint
class SteamService : Service() {

    @Inject
    lateinit var steamManager: SteamManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    val steamClient: SteamClient? get() = steamManager.steamClient
    val localPersona: StateFlow<SteamFriend> get() = steamManager.localPersona
    val appDao: SteamAppDao get() = steamManager.appDao
    val licenseDao: SteamLicenseDao get() = steamManager.licenseDao
    val changeNumbersDao: ChangeNumbersDao get() = steamManager.changeNumbersDao
    val appInfoDao: AppInfoDao get() = steamManager.appInfoDao
    val fileChangeListsDao: FileChangeListsDao get() = steamManager.fileChangeListsDao
    val steamFileHashCacheDao: SteamFileHashCacheDao get() = steamManager.steamFileHashCacheDao
    val cachedLicenseDao: CachedLicenseDao get() = steamManager.cachedLicenseDao
    val encryptedAppTicketDao: AppInfoDao? get() = null
    val downloadingAppInfoDao: DownloadingAppInfoDao get() = steamManager.downloadingAppInfoDao
    val steamUnlockedBranchDao: SteamUnlockedBranchDao get() = steamManager.steamUnlockedBranchDao
    val authPreferences: AuthPreferences get() = steamManager.authPreferences
    val containerPreferences: ContainerPreferences get() = steamManager.containerPreferences
    val downloadPreferences: DownloadPreferences get() = steamManager.downloadPreferences
    val generalPreferences: GeneralPreferences get() = steamManager.generalPreferences
    val libraryPreferences: LibraryPreferences get() = steamManager.libraryPreferences

    suspend fun getEncryptedAppTicket(appId: Int): ByteArray? = steamManager.getEncryptedAppTicket(appId)

    suspend fun getEncryptedAppTicketBase64(appId: Int): String? = steamManager.getEncryptedAppTicketBase64(appId)

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    private val onEndProcess: (AndroidEvent.EndProcess) -> Unit = {
        stop()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        PluviaApp.events.on<AndroidEvent.EndProcess, Unit>(onEndProcess)

        steamManager.onDownloadTracked = { _, _ -> }
        steamManager.onSyncStatusChanged = { syncing ->
            if (syncing) {
                notificationHelper.showSyncing(NotificationHelper.NOTIFICATION_ID_STEAM)
            } else {
                notificationHelper.showIdle(NotificationHelper.NOTIFICATION_ID_STEAM)
            }
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = checkAndPauseDownloads()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = checkAndPauseDownloads()

            private fun hasActiveWifiOrEthernet(): Boolean {
                val activeNet = connectivityManager.activeNetwork ?: return false
                val caps = connectivityManager.getNetworkCapabilities(activeNet) ?: return false
                return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            }

            private fun checkAndPauseDownloads() {
                if (steamManager.downloadPreferences.downloadOnWifiOnly && !hasActiveWifiOrEthernet()) {
                    for ((appId, info) in steamManager.getActiveDownloads()) {
                        Timber.d("Pausing download for $appId — WiFi/Ethernet lost")
                        info.cancel()
                        PluviaApp.events.emit(AndroidEvent.DownloadPausedDueToConnectivity(appId))
                        steamManager.removeDownloadJob(appId)
                    }
                    notificationHelper.notify(getString(R.string.download_paused_wifi))
                }
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = notificationHelper.createServiceNotification(NotificationHelper.NOTIFICATION_ID_STEAM, "Running...")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NotificationHelper.NOTIFICATION_ID_STEAM, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_STEAM, notification)
        }
        notificationHelper.markActive(NotificationHelper.NOTIFICATION_ID_STEAM)
        notificationHelper.showIdle(NotificationHelper.NOTIFICATION_ID_STEAM)

        when (intent?.action) {
            NotificationHelper.ACTION_EXIT -> {
                Timber.d("Exiting app via notification intent")
                PluviaApp.events.emit(AndroidEvent.EndProcess)
                return START_NOT_STICKY
            }
        }

        steamManager.start()

        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        super.onTimeout(startId, fgsType)
        Timber.w("Foreground service timeout reached, restarting...")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()

        steamManager.getActiveDownloads().values.forEach { downloadInfo ->
            downloadInfo.persistProgressSnapshot()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationHelper.cancel()

        runCatching {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }

        instance = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!steamManager.hasActiveOperations() && !(BuildConfig.XR_BUILD && steamManager.keepAlive)) {
            Timber.i("Task removed and no active work — stopping service")
            stopSelf()
        } else {
            Timber.i("Task removed but active work or keepAlive exists — keeping service alive")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val MAX_PICS_BUFFER = 256
        const val MAX_RETRY_ATTEMPTS = 20
        const val INVALID_APP_ID: Int = Int.MAX_VALUE
        const val INVALID_PKG_ID: Int = Int.MAX_VALUE
        private const val STEAM_CONTROLLER_CONFIG_FILENAME = "steam_controller_config.vdf"

        var requestTimeout: Duration
            get() = SteamManager.requestTimeout
            set(value) { SteamManager.requestTimeout = value }

        var responseTimeout: Duration
            get() = SteamManager.responseTimeout
            set(value) { SteamManager.responseTimeout = value }

        @Volatile
        var instance: SteamService? = null
            internal set

        val currentManager: SteamManager?
            get() = instance?.steamManager ?: PluviaApp.instance?.let {
                runCatching { AppUtilsEntryPoint.get(it).steamManager() }.getOrNull()
            }

        fun start(context: Context) {
            val intent = Intent(context, SteamService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        val hasWifiOrEthernet: Boolean
            get() = currentManager?.hasWifiOrEthernet ?: true

        val workshopPausedApps: MutableSet<Int>
            get() = currentManager?.workshopPausedApps ?: ConcurrentHashMap.newKeySet()

        @JvmStatic
        var keepAlive: Boolean
            get() = currentManager?.keepAlive ?: false
            set(value) { currentManager?.keepAlive = value }

        var isImporting: Boolean
            get() = currentManager?.isImporting ?: false
            set(value) { currentManager?.isImporting = value }

        val isStopping: Boolean
            get() = currentManager?.isStopping ?: false

        val isConnected: Boolean
            get() = currentManager?.isConnected ?: false

        val isRunning: Boolean
            get() = currentManager?.isRunning ?: false

        val isLoggingOut: Boolean
            get() = currentManager?.isLoggingOut ?: false

        val isLoggedIn: Boolean
            get() = currentManager?.isLoggedIn ?: false

        val isWaitingForQRAuth: Boolean
            get() = currentManager?.isWaitingForQRAuth ?: false

        val isPlayingBlocked: StateFlow<Boolean>
            get() = currentManager?.isPlayingBlocked ?: MutableStateFlow(false).asStateFlow()

        val localPersona: StateFlow<SteamFriend>
            get() = currentManager?.localPersona ?: MutableStateFlow(SteamFriend()).asStateFlow()

        val cachedAchievements: List<Achievement>?
            get() = currentManager?.cachedAchievements

        val cachedAchievementsAppId: Int?
            get() = currentManager?.cachedAchievementsAppId

        fun clearCachedAchievements() {
            currentManager?.clearCachedAchievements()
        }

        fun clearPlayingConflict() {
            currentManager?.clearPlayingConflict()
        }

        var autoStopWhenIdle: Boolean
            get() = currentManager?.autoStopWhenIdle ?: false
            set(value) {
                currentManager?.autoStopWhenIdle = value
            }

        val internalAppInstallPath: String
            get() = currentManager?.internalAppInstallPath ?: ""

        val externalAppInstallPath: String
            get() = currentManager?.externalAppInstallPath ?: ""

        val allInstallPaths: List<String>
            get() = currentManager?.allInstallPaths ?: emptyList()

        val defaultStoragePath: String
            get() = currentManager?.defaultStoragePath ?: ""

        val defaultAppInstallPath: String
            get() = currentManager?.defaultAppInstallPath ?: ""

        val defaultAppStagingPath: String
            get() = currentManager?.defaultAppStagingPath ?: ""

        val userSteamId: SteamID?
            get() = currentManager?.userSteamId

        val familyMembers: List<Int>
            get() = currentManager?.familyMembers ?: emptyList()

        val isLoginInProgress: Boolean
            get() = currentManager?.isLoginInProgress ?: false

        fun getAppDirPath(appId: Int): String =
            currentManager?.getAppDirPath(appId) ?: ""

        fun getAppDirName(appInfo: SteamApp?): String =
            currentManager?.getAppDirName(appInfo) ?: (appInfo?.name ?: "")

        fun getInstalledExe(appId: Int): String =
            currentManager?.getInstalledExe(appId) ?: ""

        fun getLaunchExecutable(appId: String, container: com.winlator.container.Container): String =
            currentManager?.getLaunchExecutable(appId, container) ?: ""

        fun buildLicensedDepotMap(apps: List<SteamApp>): Map<Int, Set<Int>> =
            currentManager?.buildLicensedDepotMap(apps) ?: emptyMap()

        fun getAppInfoOf(appId: Int): SteamApp? =
            currentManager?.getAppInfoOf(appId)

        fun getDownloadingAppInfoOf(appId: Int): DownloadingAppInfo? =
            currentManager?.getDownloadingAppInfoOf(appId)

        fun getDownloadableDlcAppsOf(appId: Int): List<SteamApp>? =
            currentManager?.getDownloadableDlcAppsOf(appId)

        @JvmStatic
        fun getOwnedDlcAppIdsOf(appId: Int): IntArray =
            currentManager?.getOwnedDlcAppIdsOf(appId) ?: IntArray(0)

        fun getHiddenDlcAppsOf(appId: Int): List<SteamApp>? =
            currentManager?.getHiddenDlcAppsOf(appId)

        fun getInstalledApp(appId: Int): AppInfo? =
            currentManager?.getInstalledApp(appId)

        fun getAllInstalledApps(): List<AppInfo>? =
            currentManager?.getAllInstalledApps()

        fun findSteamAppWithAppIds(appIds: List<Int>): List<SteamApp>? =
            currentManager?.findSteamAppWithAppIds(appIds)

        fun getImportedAppDirs(): List<String> =
            currentManager?.getImportedAppDirs() ?: emptyList()

        fun findSteamAppWithInstallDir(dirName: String): List<SteamApp>? =
            currentManager?.findSteamAppWithInstallDir(dirName)

        fun getInstalledDepotsOf(appId: Int): List<Int>? =
            currentManager?.getInstalledDepotsOf(appId)

        fun getInstalledDlcDepotsOf(appId: Int): List<Int>? =
            currentManager?.getInstalledDlcDepotsOf(appId)

        fun getAppDownloadInfo(appId: Int): DownloadInfo? =
            currentManager?.getAppDownloadInfo(appId)

        fun setAppDownloadInfo(appId: Int, info: DownloadInfo) {
            currentManager?.setAppDownloadInfo(appId, info)
        }

        fun getActiveDownloads(): Map<Int, DownloadInfo> =
            currentManager?.getActiveDownloads() ?: emptyMap()

        suspend fun getPartialDownloads(): List<Int> =
            currentManager?.getPartialDownloads() ?: emptyList()

        fun isAppInstalled(appId: Int): Boolean =
            currentManager?.isAppInstalled(appId) ?: false

        suspend fun deleteApp(appId: Int): Boolean =
            currentManager?.deleteApp(appId) ?: false

        fun hasPartialDownload(appId: Int): Boolean =
            currentManager?.hasPartialDownload(appId) ?: false

        fun getAppDlc(appId: Int): Map<Int, DepotInfo> =
            currentManager?.getAppDlc(appId) ?: emptyMap()

        suspend fun isAppInLibrary(appId: Int): Boolean =
            currentManager?.isAppInLibrary(appId) ?: false

        suspend fun requestFreeLicense(appId: Int): Boolean =
            currentManager?.requestFreeLicense(appId) ?: false

        suspend fun getOwnedAppDlc(appId: Int): Map<Int, DepotInfo> =
            currentManager?.getOwnedAppDlc(appId) ?: emptyMap()

        fun getMainAppDlcIdsWithoutProperDepotDlcIds(appId: Int): MutableList<Int> =
            currentManager?.getMainAppDlcIdsWithoutProperDepotDlcIds(appId) ?: mutableListOf()

        suspend fun refreshOwnedGamesFromServer(): Int =
            currentManager?.refreshOwnedGamesFromServer() ?: 0

        suspend fun checkDlcOwnershipViaPICSBatch(dlcAppIds: Collection<Int>): Set<Int> =
            currentManager?.checkDlcOwnershipViaPICSBatch(dlcAppIds.toSet()) ?: emptySet()

        suspend fun getOwnedGames(steamID: Long): List<app.gamenative.data.OwnedGames> =
            currentManager?.getOwnedGames(steamID) ?: emptyList()

        suspend fun fetchSteamCollections() =
            currentManager?.fetchSteamCollections()

        fun getWindowsLaunchInfos(appId: Int): List<LaunchInfo> =
            currentManager?.getWindowsLaunchInfos(appId) ?: emptyList()

        fun removeDownloadJob(appId: Int) {
            currentManager?.removeDownloadJob(appId)
        }

        fun downloadApp(appId: Int): DownloadInfo? =
            currentManager?.downloadApp(appId)

        fun downloadApp(
            appId: Int,
            dlcAppIds: List<Int>,
            branch: String = "public",
            isUpdateOrVerify: Boolean = false,
        ): DownloadInfo? =
            currentManager?.downloadApp(appId, dlcAppIds, branch, isUpdateOrVerify)

        fun downloadApp(
            appId: Int,
            downloadableDepots: Map<Int, DepotInfo>,
            userSelectedDlcAppIds: List<Int>,
            branch: String,
            containerLanguage: String,
            isUpdateOrVerify: Boolean,
        ): DownloadInfo? = currentManager?.downloadApp(
            appId,
            downloadableDepots,
            userSelectedDlcAppIds,
            branch,
            containerLanguage,
            isUpdateOrVerify,
        )

        fun downloadSteam(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
        ): Deferred<Unit> = currentManager?.downloadSteam(onDownloadProgress, parentScope, context)
            ?: parentScope.async { }

        fun downloadFile(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
            fileName: String,
        ): Deferred<Unit> = currentManager?.downloadFile(onDownloadProgress, parentScope, context, fileName)
            ?: parentScope.async { }

        fun resolveSteamControllerVdfText(appId: Int): String? =
            currentManager?.resolveSteamControllerVdfText(appId)

        fun beginLaunchApp(
            appId: Int,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            ignorePendingOperations: Boolean = false,
            preferredSave: SaveLocation = SaveLocation.None,
            prefixToPath: (String) -> String,
            isOffline: Boolean = false,
            onProgress: ((message: String, progress: Float) -> Unit)? = null,
        ): Deferred<PostSyncInfo> =
            currentManager?.beginLaunchApp(
                appId,
                parentScope,
                ignorePendingOperations,
                preferredSave,
                prefixToPath,
                isOffline,
                onProgress,
            ) ?: parentScope.async { PostSyncInfo(SyncResult.UpToDate) }

        fun beginLaunchApp(
            context: Context,
            appId: Int,
            isOffline: Boolean = false,
            prefixToPath: (String) -> String,
        ): Deferred<PostSyncInfo> =
            currentManager?.beginLaunchApp(
                appId = appId,
                isOffline = isOffline,
                prefixToPath = prefixToPath,
            ) ?: CoroutineScope(Dispatchers.IO).async { PostSyncInfo(SyncResult.UpToDate) }

        fun forceSyncUserFiles(
            appId: Int,
            prefixToPath: (String) -> String,
            preferredSave: SaveLocation = SaveLocation.None,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            overrideLocalChangeNumber: Long? = null,
        ): Deferred<PostSyncInfo> =
            currentManager?.forceSyncUserFiles(
                appId,
                prefixToPath,
                preferredSave,
                parentScope,
                overrideLocalChangeNumber,
            ) ?: parentScope.async { PostSyncInfo(SyncResult.UpToDate) }

        suspend fun closeApp(
            context: Context,
            appId: Int,
            isOffline: Boolean = false,
            prefixToPath: (String) -> String,
        ): Deferred<Unit> =
            currentManager?.closeApp(context, appId, isOffline, prefixToPath)
                ?: CoroutineScope(Dispatchers.IO).async { }

        suspend fun notifyRunningProcesses(vararg gameProcesses: GameProcessInfo) =
            currentManager?.notifyRunningProcesses(*gameProcesses)

        suspend fun startLoginWithQr() =
            currentManager?.startLoginWithQr()

        fun stopLoginWithQr() {
            currentManager?.stopLoginWithQr()
        }

        suspend fun startLoginWithCredentials(
            username: String,
            password: String,
            rememberSession: Boolean,
            authenticator: `in`.dragonbra.javasteam.steam.authentication.IAuthenticator,
        ) {
            currentManager?.startLoginWithCredentials(username, password, rememberSession, authenticator)
        }

        fun stop() {
            CoroutineScope(Dispatchers.IO).launch {
                currentManager?.stop()
            }
        }

        fun logOut() {
            currentManager?.logOut()
        }

        fun hasActiveOperations(): Boolean =
            currentManager?.hasActiveOperations() ?: false

        suspend fun generateAchievements(context: Context, appId: Int) {
            currentManager?.generateAchievements(context, appId)
        }

        suspend fun generateAchievements(appId: Int, configDirectory: String) {
            currentManager?.generateAchievements(appId, configDirectory)
        }

        fun findSteamSettingsDir(context: Context, appId: Int): String? =
            currentManager?.findSteamSettingsDir(context, appId)

        fun getGseSaveDirs(context: Context, appId: Int): List<File> =
            currentManager?.getGseSaveDirs(context, appId) ?: emptyList()

        fun isImageFsInstalled(context: Context): Boolean =
            currentManager?.isImageFsInstalled(context) ?: false

        fun isImageFsInstallable(context: Context, variant: String = ""): Boolean =
            currentManager?.isImageFsInstallable(context, variant) ?: false

        fun isSteamInstallable(context: Context): Boolean =
            currentManager?.isSteamInstallable(context) ?: false

        fun isFileInstallable(context: Context, filename: String): Boolean =
            currentManager?.isFileInstallable(context, filename) ?: false

        suspend fun fetchFile(
            url: String,
            dest: File,
            onProgress: (Float) -> Unit,
        ) {
            currentManager?.fetchFile(url, dest, onProgress)
        }

        fun downloadImageFs(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            variant: String = "",
            context: Context,
        ): Deferred<Unit> = currentManager?.downloadImageFs(onDownloadProgress, parentScope, variant, context)
            ?: parentScope.async { }

        fun downloadImageFsPatches(
            onDownloadProgress: (Float) -> Unit,
            parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
            context: Context,
        ): Deferred<Unit> = currentManager?.downloadImageFsPatches(onDownloadProgress, parentScope, context)
            ?: parentScope.async { }

        fun clearDatabase(clearCloudSyncState: Boolean = false) {
            currentManager?.clearDatabase(clearCloudSyncState)
        }

        fun syncPendingOfflineAchievements(context: Context) {
            currentManager?.syncPendingOfflineAchievements(context)
        }

        suspend fun isUpdatePending(appId: Int, branch: String = "public"): Boolean =
            currentManager?.isUpdatePending(appId, branch) ?: false

        suspend fun checkPrivateBranchPassword(appId: Int, password: String): Map<String, ByteArray> =
            currentManager?.checkPrivateBranchPassword(appId, password) ?: emptyMap()

        suspend fun getSteamUnlockedBranches(appId: Int): List<SteamUnlockedBranch> =
            currentManager?.getSteamUnlockedBranches(appId) ?: emptyList()

        fun filterForDownloadableDepots(
            depot: DepotInfo,
            prefer64Bit: Boolean,
            preferNonDeckWindows: Boolean,
            preferredLanguage: String,
            ownedDlc: Map<Int, DepotInfo>?,
            licensedDepotIds: Set<Int>? = null,
            hasSteamUnlockedBranch: Boolean = false,
            dlcAppIdsWithSingleDepots: Set<Int>? = null,
        ): Boolean = SteamManager.filterForDownloadableDepots(
            depot,
            prefer64Bit,
            preferNonDeckWindows,
            preferredLanguage,
            ownedDlc,
            licensedDepotIds,
            hasSteamUnlockedBranch,
            dlcAppIdsWithSingleDepots,
        )

        fun getDlcAppIdsWithSingleDepot(depots: Map<Int, DepotInfo>): Set<Int> =
            SteamManager.getDlcAppIdsWithSingleDepot(depots)

        fun eligibleDepots(
            depots: Map<Int, DepotInfo>,
            preferredLanguage: String,
            ownedDlc: Map<Int, DepotInfo>?,
            licensedDepotIds: Set<Int>?,
        ): Collection<DepotInfo> =
            SteamManager.eligibleDepots(depots, preferredLanguage, ownedDlc, licensedDepotIds)

        fun resolveDownloadableDepots(
            depots: Map<Int, DepotInfo>,
            preferredLanguage: String,
            ownedDlc: Map<Int, DepotInfo>?,
            licensedDepotIds: Set<Int>?,
            hasSteamUnlockedBranch: Boolean = false,
        ): Map<Int, DepotInfo> = SteamManager.resolveDownloadableDepots(
            depots,
            preferredLanguage,
            ownedDlc,
            licensedDepotIds,
            hasSteamUnlockedBranch,
        )

        fun getMainAppDepots(appId: Int, containerLanguage: String): Map<Int, DepotInfo> =
            currentManager?.getMainAppDepots(appId, containerLanguage) ?: emptyMap()

        fun getDownloadableDepots(appId: Int): Map<Int, DepotInfo> =
            currentManager?.getDownloadableDepots(appId) ?: emptyMap()

        fun getDownloadableDepots(appId: Int, preferredLanguage: String): Map<Int, DepotInfo> =
            currentManager?.getDownloadableDepots(appId, preferredLanguage) ?: emptyMap()

        suspend fun getLicensesFromDb(): List<License> =
            currentManager?.getLicensesFromDb() ?: emptyList()

        fun isAppLicensed(packageId: Int): Boolean =
            currentManager?.isAppLicensed(packageId) ?: false

        fun getPkgInfoOf(appId: Int): SteamLicense? =
            currentManager?.getPkgInfoOf(appId)

        fun getSharedPkg(): SteamLicense? =
            currentManager?.getSharedPkg()

        fun getLicensedDepotIds(appId: Int): Set<Int>? =
            currentManager?.getLicensedDepotIds(appId)

        suspend fun setPersonaState(state: EPersonaState) {
            currentManager?.setPersonaState(state)
        }

        suspend fun requestUserPersona() {
            currentManager?.requestUserPersona()
        }

        suspend fun getSelfCurrentlyPlayingAppId(): Int? =
            currentManager?.getSelfCurrentlyPlayingAppId()

        suspend fun kickPlayingSession(onlyGame: Boolean = true): Boolean =
            currentManager?.kickPlayingSession(onlyGame) ?: false

        fun notifyDownloadStarted(appId: Int) {
            currentManager?.notifyDownloadStarted(appId)
        }

        fun getLoginUsersVdfOauth(
            steamId64: String,
            account: String,
            refreshToken: String,
            accessToken: String? = null,
            personaName: String = account,
        ): String = currentManager?.getLoginUsersVdfOauth(steamId64, account, refreshToken, accessToken, personaName) ?: ""

        suspend fun fetchFileWithFallback(
            fileName: String,
            dest: File,
            context: Context,
            onProgress: (Float) -> Unit,
        ) {
            currentManager?.fetchFileWithFallback(fileName, dest, context, onProgress)
        }

        suspend fun getEncryptedAppTicket(appId: Int): ByteArray? =
            currentManager?.getEncryptedAppTicket(appId)

        suspend fun getEncryptedAppTicketBase64(appId: Int): String? =
            currentManager?.getEncryptedAppTicketBase64(appId)

        fun connectToSteam() {
            currentManager?.connectToSteam()
        }
    }
}
