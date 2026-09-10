# Comprehensive Storefront Refactoring Investigation Report: SteamService & AmazonService

**Agent**: `explorer_g4_1`  
**Date**: 2026-09-04  
**Working Directory**: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_1`  
**Milestone**: Group 4 Storefront Services Extraction (`M3`)

---

## 1. Executive Summary

In the GameNative codebase, `SteamService` and `AmazonService` represent "Hidden Android Service Singletons" — Android `Service` components that maintain extensive domain business logic, in-memory caches, API client handles, active download maps, and cloud synchronization state inside large companion objects and static/service-instance fields. 

Because Android `Service` lifecycles are dictated by the OS, using services as singletons causes major architectural flaws:
1. **Service Locator Leaks & Nullability Hazards**: Callers invoke static companion methods (e.g., `SteamService.getAppDirPath(...)`, `SteamService.getAppInfoOf(...)`, `AmazonService.getInstallPathByAppId(...)`), which dereference nullable `instance` references (`instance?.appDao`, `instance?.steamClient`). If the service is not actively running, these operations silently fail or return null/empty results.
2. **Dagger Hilt Escape Hatches**: Both services and their companion objects rely on `PreferencesEntryPoint` and `EntryPointAccessors.fromApplication(...)` to acquire DAOs and preferences whenever the service instance is unavailable or when operating from static utility methods.
3. **Pervasive Context Prop-Drilling & Service Locator Usages**: Framework methods (`context.getString()`, `context.filesDir`, `context.dataDir`, `context.getSystemService()`, `context.assets`) are scattered throughout domain logic instead of using domain abstractions (`StringResolver`, `AppStoragePaths`).

### Strategic Remedy:
- Extract all business logic, state, and client connections from `SteamService` into a new `@Singleton class SteamManager @Inject constructor(...)`.
- Expand the existing, underutilized `@Singleton class AmazonManager @Inject constructor(...)` to absorb all active download maps, sync state, installation queries, uninstallation routines, and verification logic currently residing in `AmazonService`.
- Reduce `SteamService` and `AmazonService` to thin Android foreground service shells that only manage foreground notification channels, OS service timeouts, network connectivity hooks, and lifecycle coordination.
- Eradicate `AmazonDaoEntryPoint` (`EntryPointAccessors`) and all `PreferencesEntryPoint` calls in both domains.
- Update callers across 67 files (`SteamService`) and 18 files (`AmazonService`) to inject `SteamManager` and `AmazonManager`.

---

## 2. Deep Dive: SteamService

### 2.1 File Location & Structural Anatomy
- **Path**: `app/src/main/java/app/gamenative/service/SteamService.kt`
- **Total Lines**: 4,750 lines
- **Companion Object**: Lines 346–3512 (**3,166 lines!**)
- **Service Body**: Lines 200–345 & 3514–4750 (**1,584 lines**)

### 2.2 Companion Object State & Static Fields
The companion object of `SteamService` maintains massive global state:
- `internal var instance: SteamService? = null`: Singleton instance leak used by static methods to access injected DAOs, preferences, and JavaSteam clients.
- `private val downloadJobs = ConcurrentHashMap<Int, DownloadInfo>()`: Global map of active Steam download jobs keyed by `appId`.
- `val workshopPausedApps: MutableSet<Int> = ConcurrentHashMap.newKeySet()`: Paused workshop download IDs.
- `private val depotKeyPrep = ConcurrentHashMap<Int, DepotKeyPrep>()`, `depotKeyOwner`, `depotKeyPrepLock`: Depot-key acquisition progress bookkeeping.
- `private val syncInProgressApps = ConcurrentHashMap<Int, AtomicBoolean>()`: Concurrency lock guards for cloud sync per app.
- `keepAlive: Boolean`: Prevents service termination while a game session is active.
- `isImporting: Boolean`, `isStopping: Boolean`, `isConnected: Boolean`, `isRunning: Boolean`, `isLoggingOut: Boolean`, `isWaitingForQRAuth: Boolean`: Connection & session flags.
- `cachedAchievements: List<Achievement>?`, `cachedAchievementsAppId: Int?`: In-memory achievement cache.
- `requestTimeout`, `responseTimeout`: Network configuration timeouts.

### 2.3 Service Instance State
The `SteamService` instance maintains all core JavaSteam communication infrastructure:
- **JavaSteam RPC & Client**: `steamClient: SteamClient?`, `callbackManager: CallbackManager?`, `callbackSubscriptions: ArrayList<Closeable>`.
- **Domain Handlers**:
  - `_steamUser: SteamUser?`
  - `_steamApps: SteamApps?`
  - `_steamFriends: SteamFriends?`
  - `_steamCloud: SteamCloud?`
  - `_steamUserStats: SteamUserStats?`
  - `_steamFamilyGroups: FamilyGroups?`
  - `_unifiedFriends: SteamUnifiedFriends?`
- **Reactive State & Channels**:
  - `_isPlayingBlocked: MutableStateFlow<Boolean>`
  - `_isHandlingConflict: AtomicBoolean`
  - `_localPersona: MutableStateFlow<SteamFriend>`
  - `appPicsChannel: Channel<List<PICSRequest>>` (capacity 1,000)
  - `packagePicsChannel: Channel<List<PICSRequest>>` (capacity 1,000)
- **Background Jobs & Tracking**:
  - `reconnectJob: Job?`
  - `offlineAchievementSyncJob: Job?`
  - `picsChangesCheckerJob: Job?`
  - `picsGetProductInfoJob: Job?`
  - `friendCheckerJob: Job?`
  - `steamCollectionsJob: Job?`
  - `pendingSyncAppIds: MutableSet<Int>` (persisted to `filesDir/pending_achievement_sync.txt`)
  - `appTokens: ConcurrentHashMap<Int, Long>`
  - `familyGroupMembers: ArrayList<Int>`

### 2.4 Domain Business Logic vs. Android Service Lifecycle

| Domain Area | Description & Key Methods | Belongs In |
|---|---|---|
| **Authentication & Session** | `startLoginWithCredentials`, `startLoginWithQr`, `stopLoginWithQr`, `logOut`, `clearUserData`, `connectToSteam`, `onConnected`, `onDisconnected`, `onLoggedOn`, `onLoggedOff`, `onPersonaStateReceived`, `setPersonaState`, `requestUserPersona`, `getLoginUsersVdfOauth` | **SteamManager** |
| **Download Engine (DepotDownloader)** | `downloadApp`, `downloadFile`, `downloadSteam`, `downloadImageFs`, `downloadImageFsPatches`, `filterForDownloadableDepots`, `eligibleDepots`, `resolveDownloadableDepots`, `getMainAppDepots`, `getDownloadableDepots`, `AppDownloadListener`, `depotKeyPrep`, `removeDownloadJob`, `hasPartialDownload`, `getActiveDownloads`, `getPartialDownloads` | **SteamManager** |
| **PICS & Catalog Metadata** | `continuousPICSChangesChecker`, `continuousPICSGetProductInfo`, `refreshOwnedGamesFromServer`, `checkDlcOwnershipViaPICSBatch`, `getLicensesFromDb`, `isAppLicensed`, `getPkgInfoOf`, `getSharedPkg`, `getLicensedDepotIds`, `buildLicensedDepotMap`, `getAppInfoOf`, `getDownloadingAppInfoOf`, `getDownloadableDlcAppsOf`, `getOwnedDlcAppIdsOf`, `getHiddenDlcAppsOf`, `getAllInstalledApps`, `findSteamAppWithAppIds`, `getImportedAppDirs`, `findSteamAppWithInstallDir`, `getInstalledDepotsOf`, `getInstalledDlcDepotsOf`, `getAppDlc`, `getOwnedAppDlc`, `isAppInLibrary`, `requestFreeLicense` | **SteamManager** |
| **Path & Executable Resolution** | `getAppDirName`, `resolveExistingAppDir`, `getAppDirPath`, `choosePrimaryExe`, `getInstalledExe`, `getLaunchExecutable`, `getWindowsLaunchInfos` | **SteamManager** |
| **AutoCloud & Session Lifecycle** | `beginLaunchApp`, `forceSyncUserFiles`, `closeApp`, `notifyRunningProcesses`, `kickPlayingSession`, `clearPlayingConflict` | **SteamManager** |
| **Achievements & Goldberg Sync** | `generateAchievements`, `seedGseSaveAchievements`, `getGseSaveDirs`, `collectGseUnlocksAndStats`, `syncAchievementsFromGoldberg`, `findSteamSettingsDir`, `storeAchievementUnlocks`, `syncPendingOfflineAchievements`, `clearCachedAchievements` | **SteamManager** |
| **Steam Collections & Tickets** | `fetchSteamCollections`, `getEncryptedAppTicket`, `getEncryptedAppTicketBase64` | **SteamManager** |
| **Steam Input Integration** | `selectSteamControllerConfig`, `resolveSteamInputManifestFile`, `loadConfigFromManifest`, `parseManifestForConfig`, `readBuiltInSteamInputTemplate`, `readDownloadedSteamInputTemplate` | **SteamManager** |
| **Foreground Service Lifecycle** | `startForeground(...)`, `stopForeground(...)`, `onStartCommand` intent routing (`ACTION_EXIT`), `onTimeout`, `onDestroy`, `onTaskRemoved`, `ConnectivityManager.NetworkCallback` wifi listener | **SteamService (Thin Shell)** |

### 2.5 Escape Hatches & Service Locator Usages in SteamService
The investigation identified multiple escape hatches:
1. **PreferencesEntryPoint**:
   - Line 204: `val languageCode = PreferencesEntryPoint.get(newBase).generalPreferences().appLanguage` in `attachBaseContext`.
   - Line 339: `val auth = PreferencesEntryPoint.get(this).authPreferences()` in `_localPersona`.
   - Line 2791: `val authPreferences = PreferencesEntryPoint.get(instance!!).authPreferences()` in `login(...)`.
   - Line 3028: `PreferencesEntryPoint.get(ctx).authPreferences().clearSteamSession()` in `clearUserData(...)`.
   - Line 3086: `PreferencesEntryPoint.get(it).authPreferences().username` in `performLogOffDuties(...)`.
   - Line 3314: `PreferencesEntryPoint.get(context).authPreferences().steamUserAccountId` in `getGseSaveDirs(...)`.
2. **Context Filesystem Service Locators**:
   - Line 313: `File(applicationContext.filesDir, "pending_achievement_sync.txt")`
   - Line 1671: `File(instance!!.filesDir, fileName)` in `downloadFile(...)`
   - Line 1682: `File(instance!!.filesDir, "steam.tzst")` in `downloadSteam(...)`
   - Line 1790: `instance?.assets?.open(...)` in `readBuiltInSteamInputTemplate(...)`
3. **Context String Locators**:
   - Lines 387, 435, 3574: `svc.getString(R.string.download_no_wifi)`, `svc.getString(R.string.download_preparing_depots, ...)`, `notificationHelper.notify(getString(R.string.download_paused_wifi))`

---

## 3. Deep Dive: AmazonService & AmazonManager

### 3.1 File Location & Structural Anatomy
- **AmazonService**: `app/src/main/java/app/gamenative/service/amazon/AmazonService.kt` (925 lines)
- **AmazonManager**: `app/src/main/java/app/gamenative/service/amazon/AmazonManager.kt` (95 lines)
- **AmazonDownloadManager**: `app/src/main/java/app/gamenative/service/amazon/AmazonDownloadManager.kt` (350 lines, `@Singleton`)
- **AmazonAuthManager**: `app/src/main/java/app/gamenative/service/amazon/AmazonAuthManager.kt` (235 lines, `object`)

### 3.2 State in AmazonService
`AmazonService` contains all runtime state that should be in `AmazonManager`:
- `private var instance: AmazonService? = null`: Singleton instance pointer.
- `private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()`: Active downloads by Amazon `productId`.
- `private val activeDownloadPaths = ConcurrentHashMap<String, String>()`: Active download directory paths.
- `private var lastSyncTimestamp: Long = 0L`: Throttle timestamp for library sync.
- `private var hasPerformedInitialSync: Boolean = false`
- `private var syncInProgress: Boolean = false`
- `private var backgroundSyncJob: Job? = null`

### 3.3 The Underutilized AmazonManager
Currently, `AmazonManager` is already annotated with `@Singleton` and `@Inject constructor(private val amazonGameDao: AmazonGameDao, @ApplicationContext private val context: Context)`. However, it only exposes 9 simple methods (basic DB delegation and API entitlement fetch).

All higher-level domain operations are implemented in `AmazonService`:
- `fetchDownloadSize(productId)`
- `isGameInstalled(context, productId)` & `isGameInstalledByAppId(context, appId)`
- `getExpectedInstallPathByAppId(context, appId)`
- `hasPartialDownloadByAppId(context, appId)`
- `getInstallPath(productId)` & `getInstallPathByAppId(appId)`
- `updateInstallPath(appId, path)`
- `getProductIdByAppId(appId)`
- `getLaunchExecutable(containerId)`
- `isUpdatePending(productId)` & `isUpdatePendingByAppId(appId)`
- `getDownloadInfo(productId)` & `getActiveDownloads()`
- `getPartialInstallPaths(context)` & `getPartialDownloads(context)`
- `downloadGame(context, productId, installPath)`
- `cancelDownload(productId)` & `cancelDownloadByAppId(appId)`
- `deleteGame(context, productId)` (Manifest-based uninstall, directory tree pruning, marker removal)
- `verifyGame(context, productId)` (SHA-256 and size verification against cached proto manifest)
- `cleanupFailedInstall(...)`

### 3.4 Escape Hatches in AmazonService
1. **AmazonDaoEntryPoint**:
   - Lines 47–51:
     ```kotlin
     @EntryPoint
     @InstallIn(SingletonComponent::class)
     interface AmazonDaoEntryPoint {
         fun amazonGameDao(): AmazonGameDao
     }
     ```
   - Lines 158–160: In `AmazonService.logout(...)`, if the service is not running, it uses:
     ```kotlin
     val dao = EntryPointAccessors
         .fromApplication(context.applicationContext, AmazonDaoEntryPoint::class.java)
         .amazonGameDao()
     ```
     This escape hatch exists *solely* because logout was placed in the Android Service companion instead of the singleton `AmazonManager`.
2. **PreferencesEntryPoint**:
   - Line 379: `PreferencesEntryPoint.get(context).downloadPreferences().externalStoragePath` in `getPartialInstallPaths(...)`.

---

## 4. Call Sites & Usage Enumeration

### 4.1 SteamService Usages (67 Files)

| Category | Files | Static Calls Replaced by Injected SteamManager |
|---|---|---|
| **ViewModels** | `DownloadsViewModel.kt`, `LibraryViewModel.kt`, `UserLoginViewModel.kt`, `MainViewModel.kt`, `GogRecommendationsViewModel.kt` | `getActiveDownloads`, `getPartialDownloads`, `getAppDownloadInfo`, `removeDownloadJob`, `isLoggedIn`, `userSteamId`, `localPersona`, `getAllInstalledApps`, `refreshOwnedGamesFromServer`, `startLoginWithCredentials`, `startLoginWithQr`, `stopLoginWithQr`, `logOut` |
| **Screens & UI** | `SteamAppScreen.kt`, `BaseAppScreen.kt`, `LibraryAppScreen.kt`, `FeaturedCtaButton.kt`, `LibraryListCard.kt`, `LibraryScreen.kt`, `SystemMenu.kt`, `PluviaMain.kt` | `getAppInfoOf`, `getAppDownloadInfo`, `downloadApp`, `isAppInstalled`, `getOwnedDlcAppIdsOf`, `isUpdatePending`, `hasPartialDownload`, `userSteamId` |
| **Launch & Runtime** | `SteamUtils.kt`, `ContainerUtils.kt`, `ContainerStorageManager.kt`, `SteamSaveTransfer.kt`, `GameFixesRegistry.kt`, `PlatformAuthUtils.kt`, `KeyValueUtils.kt`, `GameFeedbackUtils.kt`, `ImageFsInstaller.java`, `GlibcProgramLauncherComponent.java`, `BionicProgramLauncherComponent.java` | `getAppDirPath`, `getAppDirName`, `getLaunchExecutable`, `getInstalledExe`, `getWindowsLaunchInfos`, `getLoginUsersVdfOauth`, `getEncryptedAppTicketBase64`, `beginLaunchApp`, `closeApp`, `notifyRunningProcesses` |
| **Downloaders & Dependencies** | `WinComponentDownloader.kt`, `GraphicsDriverDownloader.kt`, `DXWrapperDownloader.kt`, `CoreDriverDownloader.kt`, `ContainerFilesDownloader.kt`, `UpdateInstaller.kt`, `ManifestInstaller.kt`, `BionicSteamAssetsDependency.kt`, `BionicDefaultProtonDependency.kt` | `fetchFileWithFallback`, `downloadSteam`, `downloadFile`, `downloadImageFs`, `downloadImageFsPatches` |
| **Settings Dialogs** | `WineProtonManagerDialog.kt`, `SettingsGroupDebug.kt`, `DriverManagerDialog.kt`, `ContentsManagerDialog.kt`, `LsfgVkManager.kt` | `allInstallPaths`, `defaultAppInstallPath`, `isAppInstalled` |
| **Subsystems** | `WorkshopManager.kt`, `CustomGameScanner.kt` | `getAppDirPath`, `getAppInfoOf`, `userSteamId`, `getLicensesFromDb`, `setAppDownloadInfo`, `getAppDownloadInfo`, `workshopPausedApps`, `notifyDownloadStarted`, `removeDownloadJob` |

### 4.2 AmazonService Usages (18 Files)

| File | Usages / Invocations | Replaced By |
|---|---|---|
| `PlatformAuthUiHelpers.kt` | `AmazonService.logout(context)` | Injected `amazonManager.logout()` |
| `PlatformOAuthHandlers.kt` | `AmazonService.authenticateWithCode(context, authCode)`, `AmazonService.start(context)`, `AmazonService.triggerLibrarySync(context)` | Injected `amazonManager.authenticateWithCode(...)`, `amazonManager.syncLibrary()` |
| `PlatformAuthUtils.kt` | `AmazonService.hasStoredCredentials(context)` | Injected `amazonManager.hasStoredCredentials()` |
| `LibraryScreen.kt` | `AmazonService.hasStoredCredentials(context)` | Injected `amazonManager.hasStoredCredentials()` |
| `LibraryListPane.kt` | `AmazonService.hasStoredCredentials(context)` | Injected `amazonManager.hasStoredCredentials()` |
| `AmazonAppScreen.kt` | `AmazonService.getProductIdByAppId`, `getAmazonGameOf`, `fetchDownloadSize`, `isGameInstalledByAppId`, `getDownloadInfoByAppId`, `hasPartialDownloadByAppId`, `downloadGame`, `cancelDownloadByAppId`, `deleteGame`, `verifyGame`, `isUpdatePendingByAppId` | Injected `amazonManager` instance methods |
| `BaseAppScreen.kt` | `AmazonService.getDownloadInfoByAppId(libraryItem.gameId)` | Injected `amazonManager.getDownloadInfoByAppId(...)` |
| `XServerScreen.kt` | `AmazonService.getProductIdByAppId`, `getInstallPathByAppId`, `getAmazonGameOf`, `getInstance()` | Injected `amazonManager` methods |
| `XAudioUtils.kt` | `AmazonService.getInstallPath(appId)` | Injected `amazonManager.getInstallPath(...)` |
| `ContainerUtils.kt` | `AmazonService.getInstallPathByAppId`, `updateInstallPath`, `getAmazonGameByAppId` | Injected `amazonManager` methods |
| `ContainerStorageManager.kt` | `AmazonService.getProductIdByAppId`, `deleteGame(context, productId)`, `getAmazonGameOf` | Injected `amazonManager` methods |
| `GameFeedbackUtils.kt` | `AmazonService.getProductIdByAppId`, `getAmazonGameOf` | Injected `amazonManager` methods |
| `PluviaMain.kt` | `AmazonService.isGameInstalledByAppId`, `isRunning`, `hasStoredCredentials`, `start(context)`, `getLaunchExecutable` | Injected `amazonManager` methods |
| `MainViewModel.kt` | `AmazonService.getAmazonGameByAppId(gameId)` | Injected `amazonManager.getGameByAppId(...)` |
| `LibraryViewModel.kt` | `AmazonService.hasStoredCredentials`, `triggerLibrarySync`, `isGameInstalledByAppId`, `hasPartialDownloadByAppId` | Injected `amazonManager` methods |
| `DownloadsViewModel.kt` | `AmazonService.getActiveDownloads()`, `getPartialDownloads(appContext)`, `cancelDownload(appId)`, `downloadGame(...)`, `deleteGame(...)` | Injected `amazonManager` methods |
| `AmazonService.kt` | Definition | Reduced to thin service shell delegating to `amazonManager` |
| `AndroidManifest.xml` | Service declaration | Unchanged |

---

## 5. Concrete Architecture & Extraction Plan

### 5.1 SteamManager Architecture (`@Singleton`)

```kotlin
package app.gamenative.service

import android.content.Context
import app.gamenative.core.appinfo.StringResolver
import app.gamenative.core.storage.AppStoragePaths
import app.gamenative.data.*
import app.gamenative.db.PluviaDatabase
import app.gamenative.db.dao.*
import app.gamenative.preferences.*
import app.gamenative.workshop.WorkshopManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Singleton
class SteamManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appStoragePaths: AppStoragePaths,
    private val stringResolver: StringResolver,
    private val authPreferences: AuthPreferences,
    private val containerPreferences: ContainerPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val generalPreferences: GeneralPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val db: PluviaDatabase,
    private val appDao: SteamAppDao,
    private val licenseDao: SteamLicenseDao,
    private val changeNumbersDao: ChangeNumbersDao,
    private val appInfoDao: AppInfoDao,
    private val fileChangeListsDao: FileChangeListsDao,
    private val steamFileHashCacheDao: SteamFileHashCacheDao,
    private val cachedLicenseDao: CachedLicenseDao,
    private val encryptedAppTicketDao: EncryptedAppTicketDao,
    private val downloadingAppInfoDao: DownloadingAppInfoDao,
    private val steamUnlockedBranchDao: SteamUnlockedBranchDao,
    private val workshopManagerProvider: Provider<WorkshopManager>,
) {
    // 1. In-memory State & Coroutine Scopes
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadJobs = ConcurrentHashMap<Int, DownloadInfo>()
    val workshopPausedApps: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val depotKeyPrep = ConcurrentHashMap<Int, DepotKeyPrep>()
    private val depotKeyOwner = ConcurrentHashMap<Int, Int>()
    private val depotKeyPrepLock = Any()
    private val syncInProgressApps = ConcurrentHashMap<Int, AtomicBoolean>()
    
    // 2. Client & Handlers
    var steamClient: SteamClient? = null
        private set
    var callbackManager: CallbackManager? = null
        private set
    private var _steamUser: SteamUser? = null
    private var _steamApps: SteamApps? = null
    private var _steamFriends: SteamFriends? = null
    private var _steamCloud: SteamCloud? = null
    private var _steamUserStats: SteamUserStats? = null
    private var _steamFamilyGroups: FamilyGroups? = null
    private var _unifiedFriends: SteamUnifiedFriends? = null

    // 3. Reactive Flows
    private val _isPlayingBlocked = MutableStateFlow(false)
    val isPlayingBlocked = _isPlayingBlocked.asStateFlow()
    private val _localPersona = MutableStateFlow(
        SteamFriend(name = authPreferences.steamUserName, avatarHash = authPreferences.steamUserAvatarHash)
    )
    val localPersona = _localPersona.asStateFlow()

    // 4. Client Lifecycle
    fun initializeClient() { ... }
    fun connect() { ... }
    suspend fun disconnect() { ... }

    // 5. Download Operations (DepotDownloader)
    fun downloadApp(appId: Int, dlcAppIds: List<Int>, branch: String, isUpdateOrVerify: Boolean): DownloadInfo? { ... }
    fun getAppDownloadInfo(appId: Int): DownloadInfo? = downloadJobs[appId]
    fun getActiveDownloads(): Map<Int, DownloadInfo> = HashMap(downloadJobs)
    fun removeDownloadJob(appId: Int) { ... }
    fun hasPartialDownload(appId: Int): Boolean { ... }

    // 6. Metadata, Licenses, & Catalog
    fun getAppInfoOf(appId: Int): SteamApp? = runBlocking(Dispatchers.IO) { appDao.findApp(appId) }
    suspend fun getLicensesFromDb(): List<License> { ... }
    fun getOwnedDlcAppIdsOf(appId: Int): IntArray { ... }
    suspend fun refreshOwnedGamesFromServer(): Int { ... }

    // 7. Cloud Sync & Achievements
    suspend fun beginLaunchApp(...): Deferred<PostSyncInfo> { ... }
    suspend fun closeApp(...): Unit { ... }
    suspend fun syncAchievementsFromGoldberg(appId: Int) { ... }
}
```

*Note on Java Interop*: For calls from Java classes (`ImageFsInstaller.java`, `GlibcProgramLauncherComponent.java`), `SteamManager` can either be injected or an `@JvmStatic` bridge / singleton accessor provider can be maintained, or those Java call sites can inject/access `SteamManager` directly.

### 5.2 AmazonManager Architecture (`@Singleton`)

```kotlin
package app.gamenative.service.amazon

import android.content.Context
import app.gamenative.data.AmazonGame
import app.gamenative.data.DownloadInfo
import app.gamenative.db.dao.AmazonGameDao
import app.gamenative.preferences.DownloadPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import timber.log.Timber

@Singleton
class AmazonManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val amazonGameDao: AmazonGameDao,
    private val amazonDownloadManager: AmazonDownloadManager,
    private val downloadPreferences: DownloadPreferences,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Active downloads state (migrated from AmazonService)
    private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()
    private val activeDownloadPaths = ConcurrentHashMap<String, String>()

    // Sync tracking state (migrated from AmazonService)
    private var lastSyncTimestamp: Long = 0L
    private var hasPerformedInitialSync: Boolean = false
    private var syncInProgress: Boolean = false

    // Authentication & credentials
    fun hasStoredCredentials(): Boolean = AmazonAuthManager.hasStoredCredentials(context)
    suspend fun authenticateWithCode(authCode: String) = AmazonAuthManager.authenticateWithCode(context, authCode)
    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        AmazonAuthManager.logout(context)
        amazonGameDao.deleteAllNonInstalledGames() // Injected directly — NO EntryPointAccessors!
        Result.success(Unit)
    }

    // Sync operations
    suspend fun syncLibrary(force: Boolean = false) { ... }

    // Install queries & resolution
    fun isGameInstalled(productId: String): Boolean { ... }
    fun isGameInstalledByAppId(appId: Int): Boolean { ... }
    fun getInstallPath(productId: String): String? { ... }
    fun getInstallPathByAppId(appId: Int): String? { ... }
    fun getExpectedInstallPathByAppId(appId: Int): String? { ... }
    fun hasPartialDownloadByAppId(appId: Int): Boolean { ... }
    fun getLaunchExecutable(containerId: String): String { ... }

    // Downloads
    fun getActiveDownloads(): Map<String, DownloadInfo> = HashMap(activeDownloads)
    fun getDownloadInfo(productId: String): DownloadInfo? = activeDownloads[productId]
    fun getDownloadInfoByAppId(appId: Int): DownloadInfo? { ... }
    suspend fun getPartialDownloads(): List<String> { ... }
    suspend fun downloadGame(productId: String, installPath: String): Result<DownloadInfo> { ... }
    fun cancelDownload(productId: String): Boolean { ... }

    // Lifecycle & file management
    suspend fun deleteGame(productId: String): Result<Unit> { ... }
    suspend fun verifyGame(productId: String): Result<VerificationResult> { ... }
}
```

### 5.3 Thin Shell Android Services

#### SteamService:
```kotlin
@AndroidEntryPoint
class SteamService : Service() {
    @Inject lateinit var steamManager: SteamManager
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var downloadPreferences: DownloadPreferences

    override fun onCreate() {
        super.onCreate()
        steamManager.initializeClient()
        // Register network callback for wifi download pausing
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Notification & foreground service registration
        startForeground(NotificationHelper.NOTIFICATION_ID_STEAM, notification)
        if (intent?.action == NotificationHelper.ACTION_EXIT) {
            PluviaApp.events.emit(AndroidEvent.EndProcess)
            return START_NOT_STICKY
        }
        steamManager.connect()
        return START_STICKY
    }

    override fun onDestroy() {
        steamManager.persistActiveSnapshots()
        super.onDestroy()
    }
}
```

#### AmazonService:
```kotlin
@AndroidEntryPoint
class AmazonService : Service() {
    @Inject lateinit var amazonManager: AmazonManager
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationHelper.NOTIFICATION_ID_AMAZON, notification)
        if (intent?.action == ACTION_SYNC_LIBRARY || intent?.action == ACTION_MANUAL_SYNC) {
            serviceScope.launch { amazonManager.syncLibrary(force = intent.action == ACTION_MANUAL_SYNC) }
        }
        return START_STICKY
    }
}
```

---

## 6. Implementation & Refactoring Plan

### Phase 1: AmazonManager Expansion & AmazonService Thinning (Lower Risk, 18 Files)
1. Expand `AmazonManager.kt`:
   - Inject `AmazonDownloadManager`, `DownloadPreferences`, `@ApplicationContext context: Context`.
   - Move `activeDownloads`, `activeDownloadPaths`, `syncInProgress`, `lastSyncTimestamp` from `AmazonService` to `AmazonManager`.
   - Move install detection (`isGameInstalled`, `isGameInstalledByAppId`, `hasPartialDownloadByAppId`), path resolution (`getInstallPath`, `getExpectedInstallPathByAppId`, `getLaunchExecutable`), download operations (`downloadGame`, `cancelDownload`, `fetchDownloadSize`), uninstallation (`deleteGame`), and verification (`verifyGame`) into `AmazonManager`.
   - Update `logout()` in `AmazonManager` to directly call `amazonGameDao.deleteAllNonInstalledGames()`.
2. Delete `AmazonDaoEntryPoint` from `AmazonService.kt` and eliminate `EntryPointAccessors.fromApplication`.
3. Eliminate `PreferencesEntryPoint` in `getPartialInstallPaths`.
4. Make `AmazonService` inject `AmazonManager` and delegate incoming intents.
5. Refactor callers in `AmazonAppScreen`, `DownloadsViewModel`, `LibraryViewModel`, `MainViewModel`, `ContainerUtils`, `ContainerStorageManager`, `PlatformOAuthHandlers`, and `PlatformAuthUiHelpers` to inject and call `AmazonManager`.

### Phase 2: Create SteamManager & Extract Business Logic (High Impact, 67 Files)
1. Create `app/src/main/java/app/gamenative/service/SteamManager.kt`:
   - Annotate with `@Singleton` and `@Inject constructor`.
   - Inject domain DAOs, Preferences (`AuthPreferences`, `ContainerPreferences`, `DownloadPreferences`, `GeneralPreferences`, `LibraryPreferences`), `AppStoragePaths`, `StringResolver`, `@ApplicationContext context: Context`, and `Provider<WorkshopManager>`.
   - Migrate JavaSteam client initialization, handler registrations, callback manager, channels, and persona state.
   - Migrate DepotDownloader engine, download jobs map, depot key acquisition, and partial download detection.
   - Migrate AutoCloud launch & exit synchronization, Goldberg achievement synchronization, and PICS metadata checkers.
2. Replace all `PreferencesEntryPoint` usages inside Steam domain with directly injected preferences.
3. Replace `context.filesDir` / `instance!!.filesDir` with `appStoragePaths.filesDir`.
4. Replace `instance?.getString(...)` with `stringResolver.getString(...)`.
5. Thin out `SteamService.kt`:
   - Inject `SteamManager`.
   - Retain foreground notification lifecycle, `NotificationHelper`, `ConnectivityManager.NetworkCallback`, and OS service timeout handling.
6. Refactor upstream call sites in ViewModels (`DownloadsViewModel`, `LibraryViewModel`, `UserLoginViewModel`, `MainViewModel`), UI composables (`SteamAppScreen`, `BaseAppScreen`), utilities (`SteamUtils`, `ContainerUtils`, `ContainerStorageManager`), and advanced subsystems (`WorkshopManager`).

---

## 7. Verification Strategy

1. **Static Analysis & Compilation**:
   - Verify 0 occurrences of `AmazonDaoEntryPoint` and `EntryPointAccessors.fromApplication` in `AmazonService` and `AmazonManager`.
   - Verify 0 occurrences of `PreferencesEntryPoint` in `SteamService` and `SteamManager`.
   - Compile target: `./gradlew compileModernDebugKotlin`.
2. **Unit Tests**:
   - Run existing unit test suite: `./gradlew :app:testModernDebugUnitTest`.
   - Add new unit tests for `AmazonManagerTest` and `SteamManagerTest` validating:
     - Install status resolution and partial download detection.
     - License and depot mapping.
     - Download cancellation and progress tracking.
     - Logout and DB pruning without Android Service dependency.
