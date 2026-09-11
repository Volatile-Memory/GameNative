package app.gamenative.service
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Base64
import androidx.room.withTransaction
import app.gamenative.BuildConfig
import app.gamenative.NetworkMonitor
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.data.AppInfo
import app.gamenative.data.CachedLicense
import app.gamenative.data.DepotInfo
import app.gamenative.data.DownloadInfo
import app.gamenative.data.DownloadingAppInfo
import app.gamenative.data.EncryptedAppTicket
import app.gamenative.data.LaunchInfo
import app.gamenative.data.OwnedGames
import app.gamenative.data.SteamApp
import app.gamenative.data.SteamCollectionRepository
import app.gamenative.data.SteamFriend
import app.gamenative.data.SteamLicense
import app.gamenative.data.SteamUnlockedBranch
import app.gamenative.db.PluviaDatabase
import app.gamenative.db.dao.AppInfoDao
import app.gamenative.db.dao.CachedLicenseDao
import app.gamenative.db.dao.ChangeNumbersDao
import app.gamenative.db.dao.DownloadingAppInfoDao
import app.gamenative.db.dao.EncryptedAppTicketDao
import app.gamenative.db.dao.FileChangeListsDao
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.db.dao.SteamFileHashCacheDao
import app.gamenative.db.dao.SteamLicenseDao
import app.gamenative.db.dao.SteamUnlockedBranchDao
import app.gamenative.enums.LoginResult
import app.gamenative.enums.Marker
import app.gamenative.events.AndroidEvent
import app.gamenative.events.SteamEvent
import app.gamenative.preferences.AuthPreferences
import app.gamenative.preferences.ContainerPreferences
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.preferences.GeneralPreferences
import app.gamenative.preferences.LibraryPreferences
import app.gamenative.service.callback.GameInviteCallback
import app.gamenative.service.handler.GameInviteHandler
import app.gamenative.statsgen.Achievement
import app.gamenative.ui.util.GameInviteNotificationManager
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.CustomGameCache
import app.gamenative.utils.LicenseSerializer
import app.gamenative.utils.MarkerUtils
import app.gamenative.utils.SteamUtils
import com.winlator.container.Container
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.enums.ELicenseFlags
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.enums.EPersonaState
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesFamilygroupsSteamclient
import `in`.dragonbra.javasteam.rpc.service.FamilyGroups
import `in`.dragonbra.javasteam.steam.authentication.AuthPollResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.AuthenticationException
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.authentication.QrAuthSession
import `in`.dragonbra.javasteam.steam.discovery.FileServerListProvider
import `in`.dragonbra.javasteam.steam.discovery.ServerQuality
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.DepotKeyCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.SteamFriends
import `in`.dragonbra.javasteam.steam.handlers.steamfriends.callback.PersonaStateCallback
import `in`.dragonbra.javasteam.steam.handlers.steamgameserver.SteamGameServer
import `in`.dragonbra.javasteam.steam.handlers.steammasterserver.SteamMasterServer
import `in`.dragonbra.javasteam.steam.handlers.steamscreenshots.SteamScreenshots
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.handlers.steamuser.ChatMode
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOffCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.PlayingSessionStateCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.SteamUserStats
import `in`.dragonbra.javasteam.steam.handlers.steamworkshop.SteamWorkshop
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.FileData
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.log.LogListener
import `in`.dragonbra.javasteam.util.log.LogManager
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.EnumSet
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Provider
import okhttp3.OkHttpClient
import timber.log.Timber

/**
 * Singleton manager holding all Steam in-memory state, client connections,
 * active download tracking, callbacks, and core business logic.
 */
@Singleton
class SteamManager @Inject constructor(
    @ApplicationContext internal val context: Context,
    internal val authPreferences: AuthPreferences,
    internal val containerPreferences: ContainerPreferences,
    internal val downloadPreferences: DownloadPreferences,
    internal val generalPreferences: GeneralPreferences,
    internal val libraryPreferences: LibraryPreferences,
    internal val db: PluviaDatabase,
    internal val licenseDao: SteamLicenseDao,
    internal val appDao: SteamAppDao,
    internal val changeNumbersDao: ChangeNumbersDao,
    internal val appInfoDao: AppInfoDao,
    internal val fileChangeListsDao: FileChangeListsDao,
    internal val steamFileHashCacheDao: SteamFileHashCacheDao,
    internal val cachedLicenseDao: CachedLicenseDao,
    internal val encryptedAppTicketDao: EncryptedAppTicketDao,
    internal val downloadingAppInfoDao: DownloadingAppInfoDao,
    internal val steamUnlockedBranchDao: SteamUnlockedBranchDao,
    internal val notificationHelper: NotificationHelper,
    internal val workshopManagerProvider: Provider<app.gamenative.workshop.WorkshopManager>,
    internal val gameSessionManagerProvider: Provider<app.gamenative.core.runtime.GameSessionManager>,
) : IChallengeUrlChanged {

    companion object {
        const val MAX_PICS_BUFFER = 256
        const val MAX_RETRY_ATTEMPTS = 20
        const val INVALID_APP_ID: Int = Int.MAX_VALUE
        const val INVALID_PKG_ID: Int = Int.MAX_VALUE
        internal const val STEAM_CONTROLLER_CONFIG_FILENAME = "steam_controller_config.vdf"

        var requestTimeout = 30.seconds
        var responseTimeout = 120.seconds

        internal val PROTOCOL_TYPES = EnumSet.of(ProtocolTypes.WEB_SOCKET)
    }

    // Callbacks for thin Android Service foreground notification tracking
    var onDownloadTracked: ((DownloadInfo, String) -> Unit)? = null
    var onSyncStatusChanged: ((Boolean) -> Unit)? = null

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // To view log messages in android logcat properly
    internal val logger = object : LogListener {
        override fun onLog(clazz: Class<*>, message: String?, throwable: Throwable?) {
            val logMessage = message ?: "No message given"
            Timber.i(throwable, "[${clazz.simpleName}] -> $logMessage")
        }

        override fun onError(clazz: Class<*>, message: String?, throwable: Throwable?) {
            val logMessage = message ?: "No message given"
            Timber.e(throwable, "[${clazz.simpleName}] -> $logMessage")
        }
    }

    internal var callbackManager: CallbackManager? = null
    internal var steamClient: SteamClient? = null
    internal val callbackSubscriptions: ArrayList<Closeable> = ArrayList()

    internal var _unifiedFriends: SteamUnifiedFriends? = null
    internal var _steamUser: SteamUser? = null
    internal var _steamApps: SteamApps? = null
    internal var _steamFriends: SteamFriends? = null
    internal var _steamCloud: SteamCloud? = null
    internal var _steamUserStats: SteamUserStats? = null
    internal var _steamFamilyGroups: FamilyGroups? = null

    internal var _loginResult: LoginResult = LoginResult.Failed
    internal var licenses: List<License> = emptyList()
    internal var retryAttempt = 0

    internal val appPicsChannel = Channel<List<PICSRequest>>(
        capacity = 1_000,
        onBufferOverflow = BufferOverflow.SUSPEND,
        onUndeliveredElement = { droppedApps ->
            Timber.w("App PICS Channel dropped: ${droppedApps.size} apps")
        },
    )

    internal val packagePicsChannel = Channel<List<PICSRequest>>(
        capacity = 1_000,
        onBufferOverflow = BufferOverflow.SUSPEND,
        onUndeliveredElement = { droppedPackages ->
            Timber.w("Package PICS Channel dropped: ${droppedPackages.size} packages")
        },
    )

    internal var reconnectJob: Job? = null
    internal var offlineAchievementSyncJob: Job? = null
    internal val pendingSyncAppIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    internal val pendingSyncFileLock = Any()
    internal val pendingSyncFile by lazy { File(context.filesDir, "pending_achievement_sync.txt") }

    // The current shared family group the logged in user is joined to.
    internal var familyGroupMembers: ArrayList<Int> = arrayListOf()
    internal val appTokens: ConcurrentHashMap<Int, Long> = ConcurrentHashMap()

    internal var picsGetProductInfoJob: Job? = null
    internal var picsChangesCheckerJob: Job? = null
    internal var friendCheckerJob: Job? = null
    internal var steamCollectionsJob: Job? = null

    internal val _isPlayingBlocked = MutableStateFlow(false)
    val isPlayingBlocked: StateFlow<Boolean> = _isPlayingBlocked.asStateFlow()
    internal val _isHandlingConflict = AtomicBoolean(false)

    // In-memory local persona state
    internal val _localPersona = MutableStateFlow(
        SteamFriend(name = authPreferences.steamUserName, avatarHash = authPreferences.steamUserAvatarHash),
    )
    val localPersona: StateFlow<SteamFriend> get() = _localPersona.asStateFlow()

    internal val downloadJobs = ConcurrentHashMap<Int, DownloadInfo>()

    /** Apps with a workshop download that was paused (cancelled) by the user. */
    val workshopPausedApps: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    // Depot-key acquisition progress, keyed by appId
    internal class DepotKeyPrep(val owner: DownloadInfo, val total: Int) {
        val resolved = AtomicInteger(0)
    }

    internal val depotKeyPrep = ConcurrentHashMap<Int, DepotKeyPrep>()
    internal val depotKeyOwner = ConcurrentHashMap<Int, Int>()
    internal val depotKeyPrepLock = Any()

    internal val syncInProgressApps = ConcurrentHashMap<Int, AtomicBoolean>()

    @Volatile
    var keepAlive: Boolean = false

    @Volatile
    var isImporting: Boolean = false

    var isStopping: Boolean = false
        internal set

    var isConnected: Boolean = false
        internal set

    var isRunning: Boolean = false
        internal set

    var isLoggingOut: Boolean = false
        internal set

    val isLoggedIn: Boolean
        get() = steamClient?.steamID?.isValid == true

    var isWaitingForQRAuth: Boolean = false
        internal set

    var cachedAchievements: List<Achievement>? = null
        internal set

    var cachedAchievementsAppId: Int? = null
        internal set

    var autoStopWhenIdle: Boolean = false

    init {
        // Restore any app IDs that were pending achievement sync before process kill
        pendingSyncAppIds.addAll(
            runCatching {
                pendingSyncFile.readLines().mapNotNull { it.trim().toIntOrNull() }
            }.getOrDefault(emptyList()),
        )

        // JavaSteam logger CME hot-fix
        runCatching {
            val clazz = Class.forName("in.dragonbra.javasteam.util.log.LogManager")
            val field = clazz.getDeclaredField("LOGGERS").apply { isAccessible = true }
            field.set(null, ConcurrentHashMap<Any, Any>())
        }

        // Clear stale download records (completed games) but keep interrupted ones
        scope.launch {
            for (record in downloadingAppInfoDao.getAll()) {
                if (isAppInstalled(record.appId)) {
                    downloadingAppInfoDao.deleteApp(record.appId)
                }
            }
        }

        LogManager.addListener(logger)
    }

    fun clearCachedAchievements() {
        cachedAchievements = null
        cachedAchievementsAppId = null
    }

    val hasWifiOrEthernet: Boolean get() = NetworkMonitor.hasWifiOrEthernet.value

    /** @return true if download may proceed; false if blocked (notifies user) */
    internal fun checkWifiOrNotify(): Boolean {
        if (downloadPreferences.downloadOnWifiOnly && !hasWifiOrEthernet) {
            notificationHelper.notify(context.getString(R.string.download_no_wifi))
            return false
        }
        return true
    }

    internal fun beginDepotKeyPrep(appId: Int, depotIds: Set<Int>, downloadInfo: DownloadInfo) {
        synchronized(depotKeyPrepLock) {
            depotKeyPrep[appId] = DepotKeyPrep(downloadInfo, depotIds.size)
            depotIds.forEach { depotId -> depotKeyOwner[depotId] = appId }
            downloadInfo.updateStatusMessage(context.getString(R.string.download_preparing))
        }
    }

    internal fun noteDepotKeyResolved(depotId: Int) {
        val appId = depotKeyOwner[depotId] ?: return
        synchronized(depotKeyPrepLock) {
            val prep = depotKeyPrep[appId] ?: return
            val done = prep.resolved.incrementAndGet().coerceAtMost(prep.total)
            prep.owner.updateStatusMessage(
                context.getString(R.string.download_preparing_depots, done, prep.total),
            )
        }
    }

    internal fun clearDepotKeyPrep(appId: Int, owner: DownloadInfo? = null) {
        if (!depotKeyPrep.containsKey(appId)) return
        synchronized(depotKeyPrepLock) {
            val prep = depotKeyPrep[appId] ?: return
            if (owner != null && prep.owner !== owner) return
            depotKeyOwner.entries.removeIf { it.value == appId }
            depotKeyPrep.remove(appId)
            prep.owner.updateStatusMessage(null)
        }
    }

    internal fun notifyDownloadStarted(appId: Int) {
        PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, true))
    }

    internal fun notifyDownloadStopped(appId: Int) {
        PluviaApp.events.emit(AndroidEvent.DownloadStatusChanged(appId, false))
    }

    fun removeDownloadJob(appId: Int) {
        clearDepotKeyPrep(appId)
        val removed = downloadJobs.remove(appId)
        if (removed != null) {
            notifyDownloadStopped(appId)
        }
    }

    /** Returns true if there is an incomplete download on disk (no complete marker). */
    fun hasPartialDownload(appId: Int): Boolean {
        if (workshopPausedApps.contains(appId)) return true

        val downloadingApp = getDownloadingAppInfoOf(appId)
        if (downloadingApp != null) {
            return true
        }

        val dirPath = getAppDirPath(appId)
        return MarkerUtils.hasPartialInstall(dirPath)
    }

    private fun getSyncFlag(appId: Int): AtomicBoolean {
        val existing = syncInProgressApps[appId]
        if (existing != null) {
            return existing
        }
        val created = AtomicBoolean(false)
        val prior = syncInProgressApps.putIfAbsent(appId, created)
        return prior ?: created
    }

    internal fun tryAcquireSync(appId: Int): Boolean {
        val flag = getSyncFlag(appId)
        val acquired = flag.compareAndSet(false, true)
        if (acquired) {
            notificationHelper.showSyncing(NotificationHelper.NOTIFICATION_ID_STEAM)
            onSyncStatusChanged?.invoke(true)
        }
        return acquired
    }

    internal fun releaseSync(appId: Int) {
        val flag = syncInProgressApps[appId]
        flag?.set(false)
        if (flag != null && !flag.get()) {
            syncInProgressApps.remove(appId, flag)
        }
        notificationHelper.showIdle(NotificationHelper.NOTIFICATION_ID_STEAM)
        onSyncStatusChanged?.invoke(false)
    }

    fun clearPlayingConflict() {
        _isPlayingBlocked.value = false
        _isHandlingConflict.set(false)
    }

    internal val serverListPath: String
        get() = Paths.get(DownloadService.baseCacheDirPath, "server_list.bin").pathString

    val internalAppInstallPath: String
        get() = Paths.get(DownloadService.baseDataDirPath, "Steam", "steamapps", "common").pathString

    internal val externalAppInstallRoot: String
        get() = downloadPreferences.externalStoragePath.orEmpty()

    val externalAppInstallPath: String
        get() = Paths.get(externalAppInstallRoot, "Steam", "steamapps", "common").pathString

    val allInstallPaths: List<String>
        get() {
            val paths = mutableListOf(internalAppInstallPath)
            val extPath = downloadPreferences.externalStoragePath.orEmpty()
            if (extPath.isNotBlank()) {
                paths += externalAppInstallPath
            }
            for (volPath in DownloadService.externalVolumePaths) {
                if (volPath.isNotBlank()) {
                    paths += Paths.get(volPath, "Steam", "steamapps", "common").pathString
                }
            }
            return paths.distinct()
        }

    internal val internalAppStagingPath: String
        get() = Paths.get(DownloadService.baseDataDirPath, "Steam", "steamapps", "staging").pathString

    internal val externalAppStagingPath: String
        get() = Paths.get(externalAppInstallRoot, "Steam", "steamapps", "staging").pathString

    internal val externalStorageReady: Boolean
        get() = downloadPreferences.useExternalStorage && File(externalAppInstallRoot).let {
            it.path.isNotBlank() && it.exists()
        }

    val defaultStoragePath: String
        get() {
            return if (externalStorageReady) {
                Timber.i("External storage path is $externalAppInstallRoot")
                externalAppInstallRoot
            } else {
                DownloadService.baseDataDirPath
            }
        }

    val defaultAppInstallPath: String
        get() {
            return if (externalStorageReady) {
                Timber.i("Using external storage")
                Timber.i("install path for external storage is $externalAppInstallPath")
                externalAppInstallPath
            } else {
                Timber.i("Using internal storage")
                internalAppInstallPath
            }
        }

    val defaultAppStagingPath: String
        get() {
            return if (downloadPreferences.useExternalStorage) {
                externalAppStagingPath
            } else {
                internalAppStagingPath
            }
        }

    val userSteamId: SteamID?
        get() = steamClient?.steamID

    val familyMembers: List<Int>
        get() = familyGroupMembers

    val isLoginInProgress: Boolean
        get() = _loginResult == LoginResult.InProgress

    suspend fun setPersonaState(state: EPersonaState) = withContext(Dispatchers.IO) {
        authPreferences.personaState = state
        _steamFriends?.setPersonaState(state)
    }

    suspend fun requestUserPersona() = withContext(Dispatchers.IO) {
        userSteamId?.let { _steamFriends?.requestFriendInfo(it) }
    }

    suspend fun getSelfCurrentlyPlayingAppId(): Int? = withContext(Dispatchers.IO) {
        val self = localPersona.value
        if (self.isPlayingGame) self.gameAppID else null
    }

    suspend fun getLicensesFromDb(): List<License> = withContext(Dispatchers.IO) {
        val cached = cachedLicenseDao.getAll()
        cached.mapNotNull { cachedLicense ->
            LicenseSerializer.deserializeLicense(cachedLicense.licenseJson)
        }
    }

    fun isAppLicensed(packageId: Int): Boolean {
        return runBlocking(Dispatchers.IO) {
            licenseDao.findLicense(packageId) != null
        }
    }

    fun getPkgInfoOf(appId: Int): SteamLicense? {
        return runBlocking(Dispatchers.IO) {
            licenseDao.findLicense(
                appDao.findApp(appId)?.packageId ?: INVALID_PKG_ID,
            )
        }
    }

    fun getSharedPkg(): SteamLicense? {
        return runBlocking(Dispatchers.IO) {
            licenseDao.findLicense(0)
        }
    }

    fun getLicensedDepotIds(appId: Int): Set<Int>? {
        val ids = getPkgInfoOf(appId)?.depotIds ?: return null
        val directDepotIds = ids.takeIf { it.isNotEmpty() }?.toSet() ?: emptySet()
        val sharedDepotIds = getSharedPkg()?.depotIds?.takeIf { it.isNotEmpty() }?.toSet() ?: emptySet()
        return (directDepotIds + sharedDepotIds).takeIf { it.isNotEmpty() }
    }

    fun buildLicensedDepotMap(apps: List<SteamApp>): Map<Int, Set<Int>> {
        val pkgIds = apps.map { it.packageId }.filter { it != INVALID_PKG_ID }.distinct()
        val licenses = runBlocking(Dispatchers.IO) {
            licenseDao.findLicenses(pkgIds)
        }
        val pkgToDepots = licenses.associate { it.packageId to it.depotIds.toSet() }
        return apps.mapNotNull { app ->
            val depots = pkgToDepots[app.packageId]?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            app.id to depots
        }.toMap()
    }

    fun getAppInfoOf(appId: Int): SteamApp? {
        return runBlocking(Dispatchers.IO) { appDao.findApp(appId) }
    }

    fun getDownloadingAppInfoOf(appId: Int): DownloadingAppInfo? {
        return runBlocking(Dispatchers.IO) { downloadingAppInfoDao.getDownloadingApp(appId) }
    }

    fun getDownloadableDlcAppsOf(appId: Int): List<SteamApp>? {
        return runBlocking(Dispatchers.IO) { appDao.findDownloadableDLCApps(appId) }
    }

    fun getOwnedDlcAppIdsOf(appId: Int): IntArray {
        val visible = getDownloadableDlcAppsOf(appId).orEmpty()
        val hidden = getHiddenDlcAppsOf(appId).orEmpty()
        if (visible.isEmpty() && hidden.isEmpty()) return IntArray(0)
        val ids = LinkedHashSet<Int>(visible.size + hidden.size)
        visible.forEach { ids.add(it.id) }
        hidden.forEach { ids.add(it.id) }
        ids.remove(appId)
        return ids.toIntArray()
    }

    fun getHiddenDlcAppsOf(appId: Int): List<SteamApp>? {
        return runBlocking(Dispatchers.IO) { appDao.findHiddenDLCApps(appId) }
    }

    fun getInstalledApp(appId: Int): AppInfo? {
        return runBlocking(Dispatchers.IO) { appInfoDao.getInstalledApp(appId) }
    }

    fun getAllInstalledApps(): List<AppInfo>? {
        return runBlocking(Dispatchers.IO) { appInfoDao.getAll() }
    }

    fun findSteamAppWithAppIds(appIds: List<Int>): List<SteamApp>? {
        return runBlocking(Dispatchers.IO) { appDao.findSteamAppWithAppIds(appIds) }
    }

    fun getImportedAppDirs(): List<String> {
        val dirs = mutableSetOf<String>()
        val installedApps = getAllInstalledApps()
        val importedAppIds = installedApps?.filter { it.isImported }?.map { it.id }
        if (importedAppIds != null) {
            val steamApps = importedAppIds
                .chunked(900)
                .flatMap { ids -> findSteamAppWithAppIds(ids).orEmpty() }
            steamApps.forEach { steamApp ->
                dirs += getAppDirName(steamApp)
            }
        }
        return dirs.toList()
    }

    fun findSteamAppWithInstallDir(dirName: String): List<SteamApp>? {
        return runBlocking(Dispatchers.IO) { appDao.findSteamAppWithInstallDir(dirName) }
    }

    fun getInstalledDepotsOf(appId: Int): List<Int>? {
        return getInstalledApp(appId)?.downloadedDepots
    }

    fun getInstalledDlcDepotsOf(appId: Int): List<Int>? {
        return getInstalledApp(appId)?.dlcDepots
    }

    fun getAppDownloadInfo(appId: Int): DownloadInfo? {
        return downloadJobs[appId]
    }

    fun setAppDownloadInfo(appId: Int, info: DownloadInfo) {
        downloadJobs[appId] = info
    }

    fun getActiveDownloads(): Map<Int, DownloadInfo> = HashMap(downloadJobs)

    suspend fun getPartialDownloads(): List<Int> {
        return downloadingAppInfoDao.getAll()
            .map { it.appId }
            .filter { appId -> !downloadJobs.containsKey(appId) }
    }

    fun isAppInstalled(appId: Int): Boolean {
        return MarkerUtils.hasMarker(getAppDirPath(appId), Marker.DOWNLOAD_COMPLETE_MARKER)
    }

    fun getAppDlc(appId: Int): Map<Int, DepotInfo> {
        return getAppInfoOf(appId)?.let {
            it.depots.filter { it.value.dlcAppId != INVALID_APP_ID }
        }.orEmpty()
    }

    suspend fun isAppInLibrary(appId: Int): Boolean =
        licenseDao.getAllLicenses().any { appId in it.appIds }

    suspend fun requestFreeLicense(appId: Int): Boolean = withContext(Dispatchers.IO) {
        val steamApps = _steamApps ?: return@withContext false
        try {
            val callback = steamApps.requestFreeLicense(appId).toFuture().await()
            Timber.i("requestFreeLicense($appId) -> ${callback.result}, apps=${callback.grantedApps}, packages=${callback.grantedPackages}")
            callback.result == EResult.OK && appId in callback.grantedApps
        } catch (e: Exception) {
            Timber.e(e, "requestFreeLicense($appId) failed")
            false
        }
    }

    suspend fun getOwnedAppDlc(appId: Int): Map<Int, DepotInfo> {
        val client = steamClient ?: return emptyMap()
        val accountId = client.steamID?.accountID?.toInt() ?: return emptyMap()
        val ownedGameIds = getOwnedGames(userSteamId!!.convertToUInt64()).map { it.appId }.toHashSet()

        return getAppDlc(appId).filter { (_, depot) ->
            when {
                depot.dlcAppId == INVALID_APP_ID -> true
                licenseDao.findLicense(depot.dlcAppId) != null -> true
                appDao.findApp(depot.dlcAppId) != null -> true
                depot.dlcAppId in ownedGameIds -> true
                else -> false
            }
        }.toMap()
    }

    fun getMainAppDlcIdsWithoutProperDepotDlcIds(appId: Int): MutableList<Int> {
        val mainAppDlcIds = mutableListOf<Int>()
        val hiddenDlcAppIds = getHiddenDlcAppsOf(appId).orEmpty().map { it.id }

        val appInfo = getAppInfoOf(appId)
        if (appInfo != null) {
            val checkingAppDlcIds = appInfo.depots.filter { it.value.dlcAppId != INVALID_APP_ID }.map { it.value.dlcAppId }.distinct()
            checkingAppDlcIds.forEach { checkingDlcId ->
                val checkMap = appInfo.depots.filter { it.value.dlcAppId == checkingDlcId }
                if (checkMap.size == 1) {
                    val depotInfo = checkMap[checkMap.keys.first()]!!
                    if (depotInfo.osList.contains(app.gamenative.enums.OS.none) &&
                        depotInfo.manifests.isEmpty() &&
                        hiddenDlcAppIds.isNotEmpty() && hiddenDlcAppIds.contains(checkingDlcId)
                    ) {
                        mainAppDlcIds.add(checkingDlcId)
                    }
                }
            }
        }

        return mainAppDlcIds
    }

    fun getAppDirName(app: SteamApp?): String {
        var appName = app?.config?.installDir.orEmpty()
        if (appName.isEmpty()) {
            appName = app?.name.orEmpty()
        }
        return appName
    }

    object SteamDirResolver {
        fun resolveExistingAppDir(installPaths: List<String>, names: List<String>): String? {
            var firstExisting: String? = null
            for (basePath in installPaths) {
                for (name in names) {
                    if (name.isEmpty()) continue
                    val path = Paths.get(basePath, name)
                    if (Files.isDirectory(path)) {
                        if (MarkerUtils.hasMarker(path.pathString, Marker.DOWNLOAD_COMPLETE_MARKER)) {
                            return path.pathString
                        }
                        if (firstExisting == null) firstExisting = path.pathString
                    }
                }
            }
            return firstExisting
        }
    }

    fun getAppDirPath(gameId: Int): String {
        val info = getAppInfoOf(gameId)

        val appInfo = getInstalledApp(gameId)
        if (appInfo != null && appInfo.isImported) {
            return appInfo.customInstallPath
        }

        val appName = getAppDirName(info)
        val oldName = info?.name.orEmpty()
        val names = if (oldName.isNotEmpty() && oldName != appName) listOf(appName, oldName) else listOf(appName)

        val resolved = SteamDirResolver.resolveExistingAppDir(allInstallPaths, names)
        if (resolved != null) return resolved

        if (downloadPreferences.useExternalStorage) {
            return Paths.get(externalAppInstallPath, appName).pathString
        }
        return Paths.get(internalAppInstallPath, appName).pathString
    }

    internal fun isExecutable(flags: Any): Boolean = when (flags) {
        is EnumSet<*> -> {
            flags.contains(EDepotFileFlag.Executable) || flags.contains(EDepotFileFlag.CustomExecutable)
        }
        is Int -> (flags and 0x20) != 0 || (flags and 0x80) != 0
        is Long -> ((flags and 0x20L) != 0L) || ((flags and 0x80L) != 0L)
        else -> false
    }

    private val UE_SHIPPING = Regex(""".*-win(32|64)(-shipping)?\.exe$""", RegexOption.IGNORE_CASE)
    private val UE_BINARIES = Regex(""".*/binaries/win(32|64)/.*\.exe$""", RegexOption.IGNORE_CASE)
    private val NEGATIVE_KEYWORDS = listOf(
        "crash", "handler", "viewer", "compiler", "tool",
        "setup", "unins", "eac", "launcher", "steam",
    )
    private val GENERIC_NAME = Regex("^[a-z]\\d{1,3}\\.exe$", RegexOption.IGNORE_CASE)

    private fun fuzzyMatch(a: String, b: String): Boolean {
        val cleanA = a.replace(Regex("[^a-z]"), "")
        val cleanB = b.replace(Regex("[^a-z]"), "")
        return cleanA.take(5) == cleanB.take(5)
    }

    private fun scoreExe(file: FileData, gameName: String, hasExeFlag: Boolean): Int {
        var s = 0
        val path = file.fileName.lowercase()

        if (UE_SHIPPING.matches(path)) s += 300
        if (UE_BINARIES.containsMatchIn(path)) s += 250
        if (!path.contains('/')) s += 200
        if (path.contains(gameName) || fuzzyMatch(path, gameName)) s += 100
        if (NEGATIVE_KEYWORDS.any { it in path }) s -= 150
        if (GENERIC_NAME.matches(file.fileName)) s -= 200
        if (hasExeFlag) s += 50

        Timber.i("Score for $path: $s")
        return s
    }

    internal fun FileData.isStub(): Boolean {
        val generic = Regex("^[a-z]\\d{1,3}\\.exe$", RegexOption.IGNORE_CASE)
        val bad = listOf("launcher", "steam", "crash", "handler", "setup", "unins", "eac")
        val n = fileName.lowercase()
        val stub = generic.matches(n) || bad.any { it in n } || totalSize < 1_000_000
        if (stub) Timber.d("Stub filtered: $fileName size=$totalSize")
        return stub
    }

    fun choosePrimaryExe(files: List<FileData>?, gameName: String): FileData? = files?.maxWithOrNull { a, b ->
        val sa = scoreExe(a, gameName, isExecutable(a.flags))
        val sb = scoreExe(b, gameName, isExecutable(b.flags))
        when {
            sa != sb -> sa - sb
            else -> (a.totalSize - b.totalSize).toInt()
        }
    }

    fun getInstalledExe(appId: Int): String {
        val appInfo = getAppInfoOf(appId) ?: return ""
        val installDir = appInfo.config.installDir.ifEmpty { appInfo.name }
        val depots = appInfo.depots.values.filter { d -> !d.sharedInstall && d.isWindowsCompatible }

        val launchTargets = appInfo.config.launch
            .mapNotNull { it.executable.lowercase() }.toSet()

        val flagged = mutableListOf<Pair<FileData, Long>>()
        var largestDepotSize = 0L

        val steamClientRef = steamClient
        val licenses = runBlocking { getLicensesFromDb() }
        if (steamClientRef == null || licenses.isEmpty()) {
            return getAppInfoOf(appId)?.let {
                getWindowsLaunchInfos(appId).firstOrNull()
            }?.executable ?: ""
        }

        val installedBranch = getInstalledApp(appId)?.branch ?: "public"
        for (depot in depots) {
            val mi = depot.manifests[installedBranch]
                ?: depot.encryptedManifests[installedBranch]
                ?: depot.manifests["public"]
                ?: continue
            if (mi.size > largestDepotSize) largestDepotSize = mi.size

            val man = DepotManifest.loadFromFile("${getAppDirPath(appId)}/.DepotDownloader/${depot.depotId}_${mi.gid}.manifest")
            man?.files?.firstOrNull { f ->
                f.fileName.lowercase() in launchTargets && !f.isStub()
            }?.let {
                return it.fileName.replace('\\', '/').toString()
            }

            man?.files?.filter { isExecutable(it.flags) || it.fileName.endsWith(".exe", true) }
                ?.forEach { flagged += it to mi.size }
        }

        choosePrimaryExe(
            flagged.map { it.first }.let { pool ->
                val noStubs = pool.filterNot { it.isStub() }
                if (noStubs.isNotEmpty()) noStubs else pool
            },
            installDir.lowercase(),
        )?.let {
            return it.fileName.replace('\\', '/')
        }

        flagged.filter { it.second == largestDepotSize }
            .maxByOrNull { it.first.totalSize }
            ?.let {
                return it.first.fileName.replace('\\', '/').toString()
            }

        return getAppInfoOf(appId)?.let {
            getWindowsLaunchInfos(appId).firstOrNull()
        }?.executable ?: ""
    }

    fun getLaunchExecutable(appId: String, container: Container): String {
        if (container.isLaunchRealSteam || container.isLaunchBionicSteam) return "steam"
        val gameId = ContainerUtils.extractGameIdFromContainerId(appId)
        return container.executablePath.ifEmpty { getInstalledExe(gameId) }
    }

    suspend fun deleteApp(appId: Int): Boolean = withContext(Dispatchers.IO) {
        val appInfo = getInstalledApp(appId)
        val result = if (appInfo?.isImported == true) {
            val folderPath = appInfo.customInstallPath
            val manualFolders = libraryPreferences.customGameManualFolders.toMutableSet()
            manualFolders.remove(folderPath)
            libraryPreferences.customGameManualFolders = manualFolders
            CustomGameCache.invalidate()
            MarkerUtils.removeMarker(folderPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            true
        } else {
            val appDirPath = getAppDirPath(appId)
            val appDir = File(appDirPath)
            if (appDir.exists()) {
                MarkerUtils.removeMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            }
            File(appDirPath).deleteRecursively()
        }

        workshopPausedApps.remove(appId)
        db.withTransaction {
            appInfoDao.deleteApp(appId)
            changeNumbersDao.deleteByAppId(appId)
            fileChangeListsDao.deleteByAppId(appId)
            steamFileHashCacheDao.deleteByAppId(appId)
            downloadingAppInfoDao.deleteApp(appId)
            appDao.clearWorkshopState(appId)

            val indirectDlcAppIds = getDownloadableDlcAppsOf(appId).orEmpty().map { it.id }
            indirectDlcAppIds.forEach { dlcAppId ->
                appInfoDao.deleteApp(dlcAppId)
                changeNumbersDao.deleteByAppId(dlcAppId)
                fileChangeListsDao.deleteByAppId(dlcAppId)
                steamFileHashCacheDao.deleteByAppId(dlcAppId)
            }
        }

        result
    }

    fun getWindowsLaunchInfos(appId: Int): List<LaunchInfo> {
        return getAppInfoOf(appId)?.let { appInfo ->
            appInfo.config.launch.filter { launchInfo ->
                launchInfo.executable.endsWith(".exe", ignoreCase = true)
            }
        }.orEmpty()
    }

    internal fun getLoginUsersVdfOauth(
        steamId64: String,
        account: String,
        refreshToken: String,
        accessToken: String? = null,
        personaName: String = account,
    ): String {
        val epoch = System.currentTimeMillis() / 1_000
        return buildString {
            appendLine("\"users\"")
            appendLine("{")
            appendLine("    \"$steamId64\"")
            appendLine("    {")
            appendLine("        \"AccountName\"          \"$account\"")
            appendLine("        \"PersonaName\"          \"$personaName\"")
            appendLine("        \"RememberPassword\"     \"1\"")
            appendLine("        \"WantsOfflineMode\"     \"0\"")
            appendLine("        \"SkipOfflineModeWarning\"     \"0\"")
            appendLine("        \"AllowAutoLogin\"       \"1\"")
            appendLine("        \"MostRecent\"           \"1\"")
            appendLine("        \"Timestamp\"            \"$epoch\"")
            appendLine("    }")
            appendLine("}")
        }
    }

    internal fun login(
        username: String,
        accessToken: String? = null,
        refreshToken: String? = null,
        password: String? = null,
        rememberSession: Boolean = true,
        twoFactorAuth: String? = null,
        emailAuth: String? = null,
        clientId: Long? = null,
    ) {
        val user = _steamUser ?: return

        authPreferences.username = username

        if ((password != null && rememberSession) || refreshToken != null) {
            if (accessToken != null) {
                authPreferences.accessToken = accessToken
            }
            if (refreshToken != null) {
                authPreferences.refreshToken = refreshToken
            }
            if (clientId != null) {
                authPreferences.clientId = clientId
            }
        }

        val event = SteamEvent.LogonStarted(username)
        PluviaApp.events.emit(event)

        user.logOn(
            LogOnDetails(
                username = SteamUtils.removeSpecialChars(username).trim(),
                password = password?.let { SteamUtils.removeSpecialChars(it).trim() },
                shouldRememberPassword = rememberSession,
                twoFactorCode = twoFactorAuth,
                authCode = emailAuth,
                accessToken = refreshToken,
                loginID = SteamUtils.getUniqueDeviceId(context),
                machineName = SteamUtils.getMachineName(context),
                chatMode = ChatMode.NEW_STEAM_CHAT,
            ),
        )
    }

    suspend fun startLoginWithCredentials(
        username: String,
        password: String,
        rememberSession: Boolean,
        authenticator: IAuthenticator,
    ) = withContext(Dispatchers.IO) {
        try {
            Timber.i("Logging in via credentials.")
            _loginResult = LoginResult.InProgress
            steamClient?.let { client ->
                val authDetails = AuthSessionDetails().apply {
                    this.username = username.trim()
                    this.password = password
                    this.persistentSession = rememberSession
                    this.authenticator = authenticator
                    this.deviceFriendlyName = SteamUtils.getMachineName(context)
                    this.clientOSType = EOSType.WinUnknown
                }

                val event = SteamEvent.LogonStarted(username)
                PluviaApp.events.emit(event)

                val authSession = client.authentication.beginAuthSessionViaCredentials(authDetails).await()
                val pollResult = authSession.pollingWaitForResult().await()

                if (pollResult.accountName.isEmpty() && pollResult.refreshToken.isEmpty()) {
                    throw Exception("No account name or refresh token received.")
                }

                login(
                    clientId = authSession.clientID,
                    username = pollResult.accountName,
                    accessToken = pollResult.accessToken,
                    refreshToken = pollResult.refreshToken,
                    rememberSession = rememberSession,
                )
            } ?: run {
                Timber.e("Could not logon: Failed to connect to Steam")
                val event = SteamEvent.LogonEnded(username, LoginResult.Failed, "No connection to Steam")
                PluviaApp.events.emit(event)
            }
        } catch (e: Exception) {
            Timber.e(if (e is CancellationException) "Login cancelled or timed out" else "Login failed")
            val message = when (e) {
                is CancellationException -> null
                is AuthenticationException -> e.result?.name ?: e.message
                else -> e.message ?: e.javaClass.name
            }
            val event = SteamEvent.LogonEnded(username, LoginResult.Failed, message)
            PluviaApp.events.emit(event)
        }
    }

    suspend fun startLoginWithQr() = withContext(Dispatchers.IO) {
        try {
            Timber.i("Logging in via QR.")
            val client = steamClient
            if (client == null) {
                Timber.e("Could not start QR logon: Client not initialized")
                val event = SteamEvent.QrAuthEnded(success = false, message = "Client not initialized")
                PluviaApp.events.emit(event)
                return@withContext
            }

            isWaitingForQRAuth = true
            val authDetails = AuthSessionDetails().apply {
                this.deviceFriendlyName = SteamUtils.getMachineName(context)
                this.clientOSType = EOSType.WinUnknown
                this.persistentSession = true
            }

            val authSession = client.authentication.beginAuthSessionViaQR(authDetails).await()
            authSession.challengeUrlChanged = this@SteamManager

            val qrEvent = SteamEvent.QrChallengeReceived(authSession.challengeUrl)
            PluviaApp.events.emit(qrEvent)

            var authPollResult: AuthPollResult? = null
            while (isWaitingForQRAuth && authPollResult == null) {
                try {
                    authPollResult = authSession.pollAuthSessionStatus().await()
                } catch (e: Exception) {
                    Timber.e(e, "Poll auth session status error")
                    throw e
                }
                delay(authSession.pollingInterval.toLong())
            }

            isWaitingForQRAuth = false
            val event = SteamEvent.QrAuthEnded(authPollResult != null)
            PluviaApp.events.emit(event)

            if (authPollResult == null) {
                Timber.e("Got no auth poll result")
                throw Exception("Got no auth poll result")
            }

            login(
                clientId = authSession.clientID,
                username = authPollResult.accountName,
                accessToken = authPollResult.accessToken,
                refreshToken = authPollResult.refreshToken,
            )
        } catch (e: Exception) {
            Timber.e(e, "QR failed")
            val message = when (e) {
                is CancellationException -> "QR Session timed out"
                is AuthenticationException -> e.result?.name ?: e.message
                else -> e.message ?: e.javaClass.name
            }
            val event = SteamEvent.QrAuthEnded(success = false, message = message)
            PluviaApp.events.emit(event)
        }
    }

    fun stopLoginWithQr() {
        Timber.i("Stopping QR polling")
        isWaitingForQRAuth = false
    }

    suspend fun stop() {
        Timber.i("Stopping SteamManager client")
        if (steamClient != null && steamClient!!.isConnected) {
            isStopping = true
            steamClient!!.disconnect()
            while (isStopping) {
                delay(200L)
            }
        } else {
            clearValues()
        }
    }

    fun logOut() {
        CoroutineScope(Dispatchers.Default).launch {
            isLoggingOut = true
            performLogOffDuties(clearCloudSyncState = true)
            _steamUser?.logOff()
        }
    }

    internal fun clearUserData(clearCloudSyncState: Boolean = false) {
        scope.launch {
            authPreferences.clearSteamSession()
        }
        clearPendingSync()
        clearDatabase(clearCloudSyncState = clearCloudSyncState)
        SteamCollectionRepository.clear()
    }

    internal fun shouldClearUserDataForLoggedOnFailure(result: EResult): Boolean = when (result) {
        EResult.InvalidPassword,
        EResult.IllegalPassword,
        EResult.PasswordUnset,
        EResult.AccountLogonDenied,
        EResult.AccountLogonDeniedNoMail,
        EResult.AccountLogonDeniedVerifiedEmailRequired,
        EResult.AccountLoginDeniedNeedTwoFactor,
        EResult.InvalidLoginAuthCode,
        EResult.ExpiredLoginAuthCode,
        EResult.RequirePasswordReEntry,
        EResult.ParentalControlRestricted,
        EResult.CachedCredentialInvalid,
        EResult.AccessDenied,
        EResult.Expired,
        EResult.Revoked -> true
        else -> false
    }

    fun clearDatabase(clearCloudSyncState: Boolean = false) {
        scope.launch {
            db.withTransaction {
                appDao.deleteAll()
                if (clearCloudSyncState) {
                    changeNumbersDao.deleteAll()
                    fileChangeListsDao.deleteAll()
                }
                licenseDao.deleteAll()
                encryptedAppTicketDao.deleteAll()
                downloadingAppInfoDao.deleteAll()
                steamUnlockedBranchDao.deleteAll()
            }
        }
    }

    internal fun cancelLongLivedSteamJobs() {
        picsGetProductInfoJob?.cancel()
        picsChangesCheckerJob?.cancel()
        friendCheckerJob?.cancel()
        steamCollectionsJob?.cancel()
    }

    internal fun performLogOffDuties(clearCloudSyncState: Boolean = false) {
        val username = authPreferences.username
        clearUserData(clearCloudSyncState = clearCloudSyncState)
        _localPersona.value = SteamFriend()

        val event = SteamEvent.LoggedOut(username)
        PluviaApp.events.emit(event)

        cancelLongLivedSteamJobs()
    }

    suspend fun getOwnedGames(friendID: Long): List<OwnedGames> = withContext(Dispatchers.IO) {
        _unifiedFriends?.getOwnedGames(friendID) ?: emptyList()
    }

    fun hasActiveOperations(): Boolean {
        val anySyncInProgress = syncInProgressApps.values.any { it.get() }
        return anySyncInProgress || downloadJobs.values.any { it.getProgress() < 1f }
    }

    fun hasActiveDownload(): Boolean = downloadJobs.isNotEmpty()

    fun isSyncInProgress(): Boolean = syncInProgressApps.values.any { it.get() }

    suspend fun checkPrivateBranchPassword(appId: Int, password: String): Map<String, ByteArray> = withContext(Dispatchers.IO) {
        val steamApps = _steamApps ?: return@withContext emptyMap()
        try {
            val callback = steamApps.checkAppBetaPassword(appId, password).await()
            if (callback.result == EResult.OK) {
                for ((branchName, _) in callback.betaPasswords) {
                    steamUnlockedBranchDao.insert(SteamUnlockedBranch(appId, branchName, password))
                }
                callback.betaPasswords
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Timber.e(e, "checkPrivateBranchPassword failed for app $appId")
            emptyMap()
        }
    }

    suspend fun getSteamUnlockedBranches(appId: Int): List<SteamUnlockedBranch> = withContext(Dispatchers.IO) {
        steamUnlockedBranchDao.getSteamUnlockedBranches(appId)
    }

    fun start() {
        initializeClient()
        connectToSteam()
    }

    fun initializeClient() {
        if (isRunning) return
        Timber.i("Using server list path: $serverListPath")

        val configuration = SteamConfiguration.create {
            it.withProtocolTypes(PROTOCOL_TYPES)
            it.withCellID(authPreferences.cellId)
            it.withServerListProvider(FileServerListProvider(File(serverListPath)))
            it.withConnectionTimeout(60000L)
            it.withHttpClient(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .pingInterval(15, TimeUnit.SECONDS)
                    .build(),
            )
        }

        steamClient = SteamClient(configuration).apply {
            removeHandler(SteamGameServer::class.java)
            removeHandler(SteamMasterServer::class.java)
            removeHandler(SteamWorkshop::class.java)
            removeHandler(SteamScreenshots::class.java)
            addHandler(GameInviteHandler())
        }

        callbackManager = CallbackManager(steamClient!!)

        _steamUser = steamClient!!.getHandler(SteamUser::class.java)
        _steamApps = steamClient!!.getHandler(SteamApps::class.java)
        _steamFriends = steamClient!!.getHandler(SteamFriends::class.java)
        _steamCloud = steamClient!!.getHandler(SteamCloud::class.java)
        _steamUserStats = steamClient!!.getHandler(SteamUserStats::class.java)
        _unifiedFriends = SteamUnifiedFriends(this)
        _steamFamilyGroups = steamClient!!.getHandler<SteamUnifiedMessages>()!!.createService<FamilyGroups>()

        with(callbackSubscriptions) {
            with(callbackManager!!) {
                add(subscribe(ConnectedCallback::class.java, ::onConnected))
                add(subscribe(DisconnectedCallback::class.java, ::onDisconnected))
                add(subscribe(LoggedOnCallback::class.java, ::onLoggedOn))
                add(subscribe(LoggedOffCallback::class.java, ::onLoggedOff))
                add(subscribe(PersonaStateCallback::class.java, ::onPersonaStateReceived))
                add(subscribe(LicenseListCallback::class.java, ::onLicenseList))
                add(subscribe(PlayingSessionStateCallback::class.java, ::onPlayingSessionState))
                add(subscribe(DepotKeyCallback::class.java) { noteDepotKeyResolved(it.depotID) })
                add(subscribe(GameInviteCallback::class.java, ::onGameInvite))
            }
        }

        isRunning = true

        scope.launch {
            while (isRunning) {
                try {
                    callbackManager!!.runWaitCallbacks(1000L)
                } catch (e: Exception) {
                    Timber.e("runWaitCallbacks failed: $e")
                }
            }
        }

        connectToSteam()
    }

    internal fun connectToSteam() {
        CoroutineScope(Dispatchers.Default).launch {
            steamClient?.connect()
            delay(5000)

            if (!isConnected) {
                Timber.w("Failed to connect to Steam, marking endpoint bad and force disconnecting")
                try {
                    steamClient?.servers?.tryMark(steamClient!!.currentEndpoint, PROTOCOL_TYPES, ServerQuality.BAD)
                } catch (_: Exception) {}
                try {
                    steamClient?.disconnect()
                } catch (e: Exception) {
                    Timber.e(e, "There was an issue when disconnecting:")
                }
            }
        }
    }

    internal fun clearValues() {
        _loginResult = LoginResult.Failed
        isRunning = false
        isConnected = false
        isLoggingOut = false
        isWaitingForQRAuth = false

        steamClient = null
        _steamUser = null
        _steamApps = null
        _steamFriends = null
        _steamCloud = null

        callbackSubscriptions.forEach { it.close() }
        callbackSubscriptions.clear()
        callbackManager = null

        _unifiedFriends?.close()
        _unifiedFriends = null

        reconnectJob?.cancel()
        offlineAchievementSyncJob?.cancel()
        offlineAchievementSyncJob = null
        pendingSyncAppIds.clear()
        isStopping = false
        retryAttempt = 0

        PluviaApp.events.clearAllListenersOf<SteamEvent<Any>>()
    }

    internal fun reconnect() {
        notificationHelper.notify("Retrying...")
        isConnected = false

        if (!_isHandlingConflict.get()) {
            val event = SteamEvent.Disconnected(isTerminal = false)
            PluviaApp.events.emit(event)
        }

        steamClient?.disconnect()
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun onConnected(callback: ConnectedCallback) {
        Timber.i("Connected to Steam")
        reconnectJob?.cancel()
        retryAttempt = 0
        isConnected = true

        var isAutoLoggingIn = false
        if (SteamUtils.hasStoredCredentials()) {
            isAutoLoggingIn = true
            login(
                username = authPreferences.username,
                refreshToken = authPreferences.refreshToken,
                rememberSession = true,
            )
        }

        val event = SteamEvent.Connected(isAutoLoggingIn)
        PluviaApp.events.emit(event)
    }

    internal fun onDisconnected(callback: DisconnectedCallback) {
        Timber.i("Disconnected from Steam. User initiated: ${callback.isUserInitiated}")
        isConnected = false
        offlineAchievementSyncJob?.cancel()
        offlineAchievementSyncJob = null

        if (!isStopping && retryAttempt < MAX_RETRY_ATTEMPTS) {
            retryAttempt++
            val backoffMs = (1000L * minOf(1 shl (retryAttempt - 1), 60)).coerceAtMost(60_000L)
            Timber.w("Attempting to reconnect (retry $retryAttempt) after ${backoffMs}ms")

            if (!_isHandlingConflict.get()) {
                val event = SteamEvent.RemotelyDisconnected
                PluviaApp.events.emit(event)
            }

            reconnectJob = scope.launch {
                delay(backoffMs)
                if (isRunning && !isStopping) connectToSteam()
            }
        } else {
            val event = SteamEvent.Disconnected(isTerminal = !isStopping)
            PluviaApp.events.emit(event)
            clearValues()
        }
    }

    internal fun onLoggedOn(callback: LoggedOnCallback) {
        Timber.i("Logged onto Steam: ${callback.result}")

        if (userSteamId?.isValid == true) {
            if (authPreferences.steamUserAccountId != userSteamId!!.accountID.toInt()) {
                authPreferences.steamUserAccountId = userSteamId!!.accountID.toInt()
                Timber.d("Saving logged in Steam accountID ${userSteamId!!.accountID.toInt()}")
            }
            val steamId64 = userSteamId!!.convertToUInt64()
            if (authPreferences.steamUserSteamId64 != steamId64) {
                authPreferences.steamUserSteamId64 = steamId64
                Timber.d("Saving logged in Steam ID64 $steamId64")
            }
        }

        when (callback.result) {
            EResult.TryAnotherCM -> {
                _loginResult = LoginResult.Failed
                reconnect()
            }
            EResult.OK -> {
                if (!authPreferences.cellIdManuallySet) {
                    authPreferences.cellId = callback.cellID
                }

                scope.launch { requestUserPersona() }
                steamCollectionsJob = scope.launch { fetchSteamCollections() }

                if (callback.familyGroupId != 0L) {
                    scope.launch {
                        val request = SteammessagesFamilygroupsSteamclient.CFamilyGroups_GetFamilyGroup_Request.newBuilder().apply {
                            familyGroupid = callback.familyGroupId
                        }.build()

                        _steamFamilyGroups?.getFamilyGroup(request)?.await()?.let {
                            if (it.result == EResult.OK) {
                                val response = it.body
                                Timber.i("Found family share: ${response.name}, with ${response.membersCount} members.")
                                response.membersList.forEach { member ->
                                    val accountID = SteamID(member.steamid).accountID.toInt()
                                    familyGroupMembers.add(accountID)
                                }
                            }
                        }
                    }
                }

                picsChangesCheckerJob = continuousPICSChangesChecker()
                picsGetProductInfoJob = continuousPICSGetProductInfo()

                _steamFriends?.setPersonaState(authPreferences.personaState)

                val activeGame = ActiveGameRegistry.get()
                if (activeGame != null) {
                    Timber.i("Re-sending active game session for appId=%d after Steam reconnect", activeGame.appId)
                    scope.launch {
                        notifyRunningProcesses(activeGame)
                    }
                }

                notificationHelper.notify("Connected")
                _loginResult = LoginResult.Success

                scope.launch {
                    resumePendingWorkshopDownloads()
                }

                syncPendingOfflineAchievements()
            }
            else -> {
                if (shouldClearUserDataForLoggedOnFailure(callback.result)) {
                    scope.launch { authPreferences.clearSteamSession() }
                }
                _loginResult = LoginResult.Failed
                reconnect()
            }
        }

        val event = SteamEvent.LogonEnded(authPreferences.username, _loginResult)
        PluviaApp.events.emit(event)
    }

    internal fun onLoggedOff(callback: LoggedOffCallback) {
        Timber.i("Logged off of Steam: ${callback.result}")
        notificationHelper.notify("Disconnected...")

        if (isLoggingOut) {
            performLogOffDuties(clearCloudSyncState = true)
            scope.launch { stop() }
        } else if (callback.result == EResult.LogonSessionReplaced) {
            cancelLongLivedSteamJobs()
            scope.launch { stop() }
        } else if (callback.result == EResult.LoggedInElsewhere) {
            if (gameSessionManagerProvider.get().isSessionRunning) {
                if (!_isHandlingConflict.getAndSet(true)) {
                    _isPlayingBlocked.value = true
                    PluviaApp.events.emit(SteamEvent.PlayingBlocked(remoteAppName = null))
                }
                reconnect()
            } else {
                PluviaApp.events.emit(SteamEvent.ForceCloseApp)
                reconnect()
            }
        } else {
            reconnect()
        }
    }

    internal fun onPlayingSessionState(callback: PlayingSessionStateCallback) {
        Timber.d("onPlayingSessionState: blocked=${callback.isPlayingBlocked} remoteAppId=${callback.playingAppID}")
        _isPlayingBlocked.value = callback.isPlayingBlocked
        if (!callback.isPlayingBlocked) return

        val knownApp = callback.playingAppID
            .takeIf { it != 0 }
            ?.let { getAppInfoOf(it) }
            ?: return

        if (_isHandlingConflict.compareAndSet(false, true)) {
            PluviaApp.events.emit(SteamEvent.PlayingBlocked(remoteAppName = knownApp.name))
        }
    }

    internal fun onGameInvite(callback: GameInviteCallback) {
        Timber.i("onGameInvite: from=${callback.inviterSteamId} connect=${callback.connectString}")
        if (callback.connectString.isEmpty()) return
        GameInviteNotificationManager.show(callback.inviterSteamId, callback.connectString)
    }

    @OptIn(ExperimentalStdlibApi::class)
    internal fun onPersonaStateReceived(callback: PersonaStateCallback) {
        if (!callback.friendId.isIndividualAccount || callback.playerName.isEmpty()) return

        scope.launch {
            db.withTransaction {
                val currentUserId = steamClient?.steamID ?: return@withTransaction
                if (callback.friendId != currentUserId) return@withTransaction

                val avatarHash = callback.avatarHash.toHexString()
                val playerName = callback.playerName
                val state = if (callback.personaState == EPersonaState.Offline && isConnected) {
                    authPreferences.personaState
                } else {
                    callback.personaState
                }

                _localPersona.update {
                    it.copy(
                        avatarHash = avatarHash,
                        name = playerName,
                        state = state,
                        gameAppID = callback.gamePlayedAppId,
                        gameName = appDao.findApp(callback.gamePlayedAppId)?.name ?: callback.gameName,
                    )
                }

                authPreferences.steamUserAvatarHash = avatarHash
                authPreferences.steamUserName = playerName

                val event = SteamEvent.PersonaStateReceived(localPersona.value)
                PluviaApp.events.emit(event)
            }
        }
    }

    internal fun onLicenseList(callback: LicenseListCallback) {
        if (callback.result != EResult.OK) {
            Timber.w("Failed to get License list")
            return
        }
        Timber.i("Received License List ${callback.result}, size: ${callback.licenseList.size}")

        scope.launch {
            db.withTransaction {
                licenses = callback.licenseList
                cachedLicenseDao.deleteAll()
                callback.licenseList.chunked(500).forEach { chunk ->
                    cachedLicenseDao.insertAll(
                        chunk.map { license ->
                            CachedLicense(licenseJson = LicenseSerializer.serializeLicense(license))
                        },
                    )
                }
                val licensesToAdd = callback.licenseList
                    .groupBy { it.packageID }
                    .map { licensesEntry ->
                        val preferred = licensesEntry.value.firstOrNull {
                            it.ownerAccountID == userSteamId?.accountID?.toInt()
                        } ?: licensesEntry.value.first()
                        SteamLicense(
                            packageId = licensesEntry.key,
                            lastChangeNumber = preferred.lastChangeNumber,
                            timeCreated = preferred.timeCreated,
                            timeNextProcess = preferred.timeNextProcess,
                            minuteLimit = preferred.minuteLimit,
                            minutesUsed = preferred.minutesUsed,
                            paymentMethod = preferred.paymentMethod,
                            licenseFlags = licensesEntry.value
                                .map { it.licenseFlags }
                                .reduceOrNull { first, second ->
                                    val combined = EnumSet.copyOf(first)
                                    combined.addAll(second)
                                    combined
                                } ?: EnumSet.noneOf(ELicenseFlags::class.java),
                            purchaseCode = preferred.purchaseCode,
                            licenseType = preferred.licenseType,
                            territoryCode = preferred.territoryCode,
                            accessToken = preferred.accessToken,
                            ownerAccountId = licensesEntry.value.map { it.ownerAccountID },
                            masterPackageID = preferred.masterPackageID,
                        )
                    }

                if (licensesToAdd.isNotEmpty()) {
                    Timber.i("Adding ${licensesToAdd.size} licenses")
                    licensesToAdd.chunked(500).forEach { chunk ->
                        licenseDao.insertAll(chunk)
                    }
                }

                val licensesToRemove = licenseDao.findStaleLicences(
                    packageIds = callback.licenseList.map { it.packageID },
                )
                if (licensesToRemove.isNotEmpty()) {
                    Timber.i("Removing ${licensesToRemove.size} (stale) licenses")
                    val packageIds = licensesToRemove.map { it.packageId }
                    licenseDao.deleteStaleLicenses(packageIds)
                }

                licenseDao.getAllLicenses()
                    .map { PICSRequest(it.packageId, it.accessToken) }
                    .chunked(MAX_PICS_BUFFER)
                    .forEach { chunk ->
                        Timber.d("onLicenseList: Queueing ${chunk.size} package(s) for PICS")
                        packagePicsChannel.send(chunk)
                    }
            }
        }
    }

    override fun onChanged(qrAuthSession: QrAuthSession?) {
        qrAuthSession?.let { qr ->
            if (!BuildConfig.DEBUG) {
                Timber.d("QR code changed -> ${qr.challengeUrl}")
            }
            val event = SteamEvent.QrChallengeReceived(qr.challengeUrl)
            PluviaApp.events.emit(event)
        } ?: run { Timber.w("QR challenge url was null") }
    }

    internal fun addPendingSyncApp(appId: Int) {
        synchronized(pendingSyncFileLock) {
            pendingSyncAppIds.add(appId)
            runCatching { pendingSyncFile.writeText(pendingSyncAppIds.joinToString("\n")) }
        }
        Timber.tag("achievements").d("Recording appId=$appId for offline achievement sync on reconnect")
    }

    internal fun removePendingSyncApp(appId: Int) {
        synchronized(pendingSyncFileLock) {
            pendingSyncAppIds.remove(appId)
            runCatching {
                if (pendingSyncAppIds.isEmpty()) pendingSyncFile.delete()
                else pendingSyncFile.writeText(pendingSyncAppIds.joinToString("\n"))
            }
        }
    }

    internal fun clearPendingSync() {
        synchronized(pendingSyncFileLock) {
            pendingSyncAppIds.clear()
            runCatching { pendingSyncFile.delete() }
        }
    }

    suspend fun getEncryptedAppTicket(appId: Int): ByteArray? {
        return try {
            val cachedTicket = encryptedAppTicketDao.getByAppId(appId)
            val now = System.currentTimeMillis()
            val thirtyMinutes = 30 * 60 * 1000L

            if (cachedTicket != null && (now - cachedTicket.timestamp) < thirtyMinutes) {
                Timber.d("Using cached encrypted app ticket protobuf for app $appId")
                return cachedTicket.encryptedTicket
            }

            val apps = _steamApps
            val response = try {
                withTimeout(5_000) {
                    apps?.requestEncryptedAppTicket(appId)?.await()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to request encrypted app ticket for app $appId")
                return null
            }

            if (response?.result != EResult.OK || response.encryptedAppTicket == null) {
                Timber.w("Failed to get encrypted app ticket for app $appId: ${response?.result}")
                return null
            }

            val ticketProto = response.encryptedAppTicket
            val ticket = EncryptedAppTicket(
                appId = appId,
                result = response.result.code(),
                ticketVersionNo = ticketProto!!.ticketVersionNo.toInt(),
                crcEncryptedTicket = ticketProto.crcEncryptedticket.toInt(),
                cbEncryptedUserData = ticketProto.cbEncrypteduserdata.toInt(),
                cbEncryptedAppOwnershipTicket = ticketProto.cbEncryptedAppownershipticket.toInt(),
                encryptedTicket = ticketProto.toByteArray(),
                timestamp = now,
            )

            encryptedAppTicketDao.insert(ticket)
            Timber.d("Stored new encrypted app ticket protobuf for app $appId")

            ticket.encryptedTicket
        } catch (e: Exception) {
            Timber.e(e, "Error getting encrypted app ticket for app $appId")
            null
        }
    }

    suspend fun getEncryptedAppTicketBase64(appId: Int): String? {
        val ticket = getEncryptedAppTicket(appId) ?: return null
        return Base64.encodeToString(ticket, Base64.NO_WRAP)
    }
}
