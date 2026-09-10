# Group 4 Storefront Services & Managers: DI Architecture, Service Wrappers, and Call Site Investigation Report

**Author**: `explorer_g4_3`  
**Date**: 2026-09-04  
**Target Logical Domain**: Group 4 — Storefront Services (`SteamService`, `EpicService`, `GOGService`, `AmazonService`)  
**Authority Reference**: `ORIGINAL_REQUEST.md` (§R1 Group 4, §R2, §R3) & `PROJECT.md` (Milestone 3 / Group 4)

---

## Executive Summary

This investigation analyzes the architectural state, dependency injection graph, Android Service lifecycles, existing manager implementations, and downstream call sites for the four storefront services in GameNative: **Steam**, **Epic Games**, **GOG**, and **Amazon Games**.

### Core Discoveries:
1. **Steam Architecture Gap**: `SteamManager` does **not exist** anywhere in the codebase. All Steam client connection handling, authentication, PICS metadata caching, licenses, active download jobs, cloud sync, and launch parameter generation reside in a monolithic **4,750-line** class (`SteamService.kt`) with an extensive static companion object acting as a hidden singleton.
2. **Asymmetrical Implementations for Epic, GOG, Amazon**:
   - `EpicManager.kt` (1,141 lines) and `GOGManager.kt` (1,289 lines) exist as `@Singleton class ... @Inject constructor`, but their corresponding Services (`EpicService`, `GOGService`) still retain active download maps, background sync state tracking, and ~600-line static companion facades that forward calls to `getInstance()`.
   - `AmazonManager.kt` (95 lines) is merely a thin Room DAO wrapper; almost all business logic, active download maps, path resolution, and installation detection are trapped in `AmazonService.kt` (925 lines). Furthermore, `AmazonService` contains an explicit `@EntryPoint @InstallIn(SingletonComponent::class) interface AmazonDaoEntryPoint` escape hatch using `EntryPointAccessors.fromApplication`.
3. **The "Service-as-Singleton" Anti-Pattern**: All four Android services expose public static methods in their `companion object` that check `instance != null` or `getInstance()`. If the Android Service has not been started, has stopped, or was killed by Android's low-memory killer (OOM), downstream consumers (ViewModels, AppScreens, utilities) fail silently, return empty data, or throw null-pointer exceptions.
4. **DI Module Readiness**: Dagger Hilt modules for Room DAOs (`DatabaseModule`), Preferences (`PreferencesModule`), Storage (`StorageModule` / `AppStoragePaths`), System Services (`SystemServicesModule`), String resolution (`StringResolverModule`), and Coroutine Dispatchers/Scopes (`CoroutinesModule`) are already fully configured in `SingletonComponent`. Storefront managers can directly inject all needed dependencies into their constructors.
5. **Call Site Surface Area**: Over **100 files** directly invoke static methods on `SteamService`, `EpicService`, `GOGService`, and `AmazonService`. Downstream consumers include `DownloadsViewModel`, `MainViewModel`, `UserLoginViewModel`, `LibraryViewModel`, `CustomGameScanner`, `WorkshopManager`, Compose AppScreens (`BaseAppScreen`, `SteamAppScreen`, `EpicAppScreen`, `GOGAppScreen`, `AmazonAppScreen`), storage utilities (`ContainerStorageManager`, `ContainerUtils`), and dependency downloaders.

---

## 1. Android Services Analysis

### 1.1 Manifest & Annotations
All four storefront services are registered in `app/src/main/AndroidManifest.xml` with:
- `android:enabled="true"`
- `android:exported="false"`
- `android:foregroundServiceType="dataSync"`

Each service class is annotated with Dagger Hilt's `@AndroidEntryPoint`:
- `app.gamenative.service.SteamService` (`@AndroidEntryPoint class SteamService : Service(), IChallengeUrlChanged`)
- `app.gamenative.service.epic.EpicService` (`@AndroidEntryPoint class EpicService : Service()`)
- `app.gamenative.service.gog.GOGService` (`@AndroidEntryPoint class GOGService : Service()`)
- `app.gamenative.service.amazon.AmazonService` (`@AndroidEntryPoint class AmazonService : Service()`)

### 1.2 Foreground Service & Notification Management
- **Notification Helper**: Foreground notifications are created and updated via `app.gamenative.service.NotificationHelper`.
  - Notification IDs:
    - Steam: `NotificationHelper.NOTIFICATION_ID_STEAM = 1`
    - GOG: `NotificationHelper.NOTIFICATION_ID_GOG = 2`
    - Epic: `NotificationHelper.NOTIFICATION_ID_EPIC = 3`
    - Amazon: `NotificationHelper.NOTIFICATION_ID_AMAZON = 4`
  - Channel: `CHANNEL_ID = "pluvia_foreground_service"`, name: `"GameNative Foreground Service"`, importance: `IMPORTANCE_LOW`.
  - Content: Manages active download tracking, percentage progress bars, determinate/indeterminate progress, and summary notifications.
- **Service Lifecycle Execution**:
  - Services are started using `startForegroundService(Intent(context, ...Service::class.java))`.
  - In `onStartCommand(intent, flags, startId)`:
    ```kotlin
    val notification = notificationHelper.createServiceNotification(NOTIFICATION_ID, "Connected")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        startForeground(NOTIFICATION_ID, notification)
    }
    ```
    Returns `START_STICKY`.
  - In `onDestroy()`:
    ```kotlin
    stopForeground(STOP_FOREGROUND_REMOVE)
    notificationHelper.cancel(NOTIFICATION_ID)
    instance = null
    ```
  - In `onTimeout(startId, fgsType)` (Android 14+ / API 34+ timeout):
    Logs warning and calls `stopSelf()`.
  - In `onTaskRemoved(rootIntent)`:
    Checks `hasActiveOperations()` (sync in progress or active download in flight). If no active work, calls `stopSelf()`; otherwise keeps service alive.
- **Teardown Action Handling**:
  - Notification Exit action broadcasts `NotificationHelper.ACTION_EXIT`.
  - `NotificationActionReceiver : BroadcastReceiver` receives this and emits `AndroidEvent.EndProcess`.
  - Services subscribe in `onCreate()` to `AndroidEvent.EndProcess` and invoke `stopSelf()` or `Companion.stop()`.

### 1.3 Critical Architectural Flaws in Current Service Design
1. **Service Instance as Singleton Escape Hatch**:
   - `SteamService.Companion.instance`
   - `EpicService.Companion.instance`
   - `GOGService.Companion.instance`
   - `AmazonService.Companion.instance`
   Whenever callers need state or logic, they call static helper methods in the companion object which access `instance`. If `instance == null`, the call fails or returns dummy/fallback data.
2. **State Inversion & Lifecycle Destruction**:
   - In `EpicService`, `GOGService`, and `AmazonService`, `activeDownloads` is a `ConcurrentHashMap` stored **inside the Android Service instance**.
   - If the Android OS terminates the service process or destroys the service when downloads finish, any cached download state or active progress tracking is destroyed with it.
   - Conversely, querying `getActiveDownloads()` when the service is stopped returns an empty map, misleading the UI into thinking no downloads exist.
3. **Circular Reliance between Service and Companion**:
   - Methods like `EpicService.downloadGame()` create a `DownloadInfo`, insert it into `instance.activeDownloads`, and launch a coroutine on `instance.scope`. The static companion is acting as an un-injected singleton controller that steals the Android Service's coroutine scope and lifecycle.
4. **Explicit EntryPoint Escape Hatch in `AmazonService`**:
   - Lines 47-51 & 158-161 of `AmazonService.kt`:
     ```kotlin
     @EntryPoint
     @InstallIn(SingletonComponent::class)
     interface AmazonDaoEntryPoint {
         fun amazonGameDao(): AmazonGameDao
     }
     ...
     val dao = EntryPointAccessors
         .fromApplication(context.applicationContext, AmazonDaoEntryPoint::class.java)
         .amazonGameDao()
     ```
     This escape hatch was written specifically because the developers realized `instance` might be null when `logout()` is called!

---

## 2. Storefront Managers Status & Inventory

| Manager | Status | Current Code Location | Lines | Constructor / Annotations | Current Scope & Deficiencies |
|---|---|---|---|---|---|
| **SteamManager** | **DOES NOT EXIST** | N/A | 0 | None | All Steam client connection, credentials, PICS, license, download, cloud sync, and launch logic is inside `SteamService.kt` (4,750 lines). |
| **EpicManager** | **PARTIAL** | `service/epic/EpicManager.kt` | 1,141 | `@Singleton class ... @Inject constructor(epicGameDao: EpicGameDao, downloadPreferences: DownloadPreferences? = null)` | Contains Room DAO calls, asset metadata parsing, manifest calculation, and library syncing. **Deficiencies**: Constructor has nullable `DownloadPreferences` with fallback to `PreferencesEntryPoint.get(PluviaApp.instance)`; methods accept `Context`; does not own `activeDownloads` or sync state. |
| **GOGManager** | **PARTIAL** | `service/gog/GOGManager.kt` | 1,289 | `@Singleton class ... @Inject constructor(gogGameDao: GOGGameDao, @ApplicationContext context: Context)` | Contains Room DAO operations, gogdl Python bridge commands, executable discovery, Wine launch arguments. **Deficiencies**: Prop-drills `Context` for filesystem paths instead of `AppStoragePaths`; does not own `activeDownloads` or sync state. |
| **AmazonManager** | **STUB / MINIMAL** | `service/amazon/AmazonManager.kt` | 95 | `@Singleton class ... @Inject constructor(amazonGameDao: AmazonGameDao, @ApplicationContext context: Context)` | Only exposes basic Room DAO queries and `refreshLibrary()`. **Deficiencies**: Almost all Amazon business logic (925 lines) is trapped in `AmazonService.kt`, including download scheduling, installation checks, and path formatting. Uses `AmazonAuthManager` directly with `context`. |

### Sub-Managers and Helper Objects in Storefront Domains:
- **Steam**:
  - `SteamAutoCloud` (`object` singleton, 1,454 lines) — cloud save sync and file hashing.
  - `SteamUnifiedFriends` (class, 66 lines) — Steam player API and owned games.
  - `SteamWishlistService` (`object` singleton, 245 lines) — wishlist addition and WebView auth.
  - `DownloadService` (`object` singleton, 122 lines) — path resolution and folder sizes.
- **Epic**:
  - `EpicDownloadManager` (`@Singleton class ... @Inject constructor`, 1,439 lines) — chunk downloading and CDN streaming.
  - `EpicAuthManager` (`object` singleton, 323 lines) — OAuth and credentials persistence.
  - `EpicOverlayManager` (`@Singleton class ... @Inject constructor`, 276 lines) — Wine EOS overlay injection.
  - `EpicCloudSavesManager` (`object` singleton, 381 lines) — cloud save synchronization.
- **GOG**:
  - `GOGDownloadManager` (`@Singleton class ... @Inject constructor`, 1,861 lines) — manifest chunk downloading and assembly.
  - `GOGAuthManager` (`object` singleton, 443 lines) — OAuth and token refreshing.
  - `GOGCloudSavesManager` (`object` singleton, 219 lines) — cloud save synchronization.
- **Amazon**:
  - `AmazonDownloadManager` (`@Singleton class ... @Inject constructor`, 350 lines) — parallel file downloads.
  - `AmazonAuthManager` (`object` singleton, 235 lines) — PKCE auth and credential storage.
  - `AmazonSdkManager` (`object` singleton, 212 lines) — Wine SDK installation.

---

## 3. Dependency Injection Architecture

### 3.1 Existing DI Modules & Bindings Available in `SingletonComponent`
1. **`DatabaseModule`** (`app/src/main/java/app/gamenative/di/DatabaseModule.kt`):
   Provides Room database `PluviaDatabase` and all needed DAOs:
   - `SteamAppDao`, `SteamLicenseDao`, `SteamFileHashCacheDao`, `AppChangeNumbersDao`, `AppFileChangeListsDao`, `AppInfoDao`, `CachedLicenseDao`, `EncryptedAppTicketDao`, `DownloadingAppInfoDao`, `SteamUnlockedBranchDao`
   - `EpicGameDao`
   - `GOGGameDao`
   - `AmazonGameDao`
2. **`PreferencesModule`** (`app/src/main/java/app/gamenative/di/PreferencesModule.kt`):
   Provides `PluviaDataStore` and binds all domain preference repositories:
   - `AuthPreferences`
   - `ContainerPreferences`
   - `DownloadPreferences`
   - `GeneralPreferences`
   - `LibraryPreferences`
   - `InputPreferences`
   - `HudPreferences`
3. **`StorageModule`** (`app/src/main/java/app/gamenative/core/storage/StorageModule.kt`):
   Binds `AppStoragePaths` (`AndroidAppStoragePaths`).
   Exposes `filesDir`, `dataDir`, `cacheDir`, `externalFilesDir`, `containersDir`, `imageFsDir`, `getGameInstallBaseDir(sourceName)`.
4. **`SystemServicesModule`** (`app/src/main/java/app/gamenative/core/system/SystemServicesModule.kt`):
   Provides `@Singleton` system services:
   - `ConnectivityManager`
   - `NotificationManager`
   - `StorageManager`
   - `PowerManager`, `ActivityManager`, `DisplayManager`, `BatteryManager`, `Vibrator`, `WindowManager`, `InputManager`, `InputMethodManager`, `SensorManager`, `ShortcutManager`
5. **`CoroutinesModule`** (`app/src/main/java/app/gamenative/core/coroutines/CoroutinesModule.kt`):
   Provides dispatchers and scopes:
   - `@IoDispatcher CoroutineDispatcher`
   - `@DefaultDispatcher CoroutineDispatcher`
   - `@MainDispatcher CoroutineDispatcher`
   - `@MainImmediateDispatcher CoroutineDispatcher`
   - `@UnconfinedDispatcher CoroutineDispatcher`
   - `@ApplicationScope CoroutineScope` (SupervisorJob + DefaultDispatcher + named)
6. **`StringResolverModule`** (`app/src/main/java/app/gamenative/core/appinfo/StringResolverModule.kt`):
   Binds `StringResolver` (`AndroidStringResolver`).

### 3.2 Additions & Enhancements Needed for Group 4
1. **`AppUtilsEntryPoint.kt`**:
   Currently exposes singletons for Groups 1, 2, 3 (`hltbService`, `deviceGameStatsCache`, `favoritesManager`, `customGameScanner`, etc.).
   Must add accessors for the four storefront managers:
   ```kotlin
   fun steamManager(): SteamManager
   fun epicManager(): EpicManager
   fun gogManager(): GOGManager
   fun amazonManager(): AmazonManager
   ```
   This allows UI Composables (`SteamAppScreen`, `EpicAppScreen`, `GOGAppScreen`, `AmazonAppScreen`, `BaseAppScreen`) and utility classes to cleanly retrieve the managers via `context.appUtilsEntryPoint().steamManager()`.
2. **`NotificationHelper.kt` Constructor Clean-up**:
   `NotificationHelper` currently defaults `generalPreferences` to `PreferencesEntryPoint.get(context).generalPreferences()`. Remove the default argument so `GeneralPreferences` is injected cleanly via `@Inject constructor`.

---

## 4. Downstream Call Site Ecosystem

Over 100 files across the codebase make direct calls to storefront services. Here is the categorized breakdown:

### 4.1 ViewModels
- **`DownloadsViewModel.kt`**:
  - Queries active downloads: `SteamService.getActiveDownloads()`, `EpicService.getActiveDownloads()`, `GOGService.getActiveDownloads()`, `AmazonService.getActiveDownloads()`.
  - Queries partial/resumable downloads: `SteamService.getPartialDownloads()`, `EpicService.getPartialDownloads()`, `GOGService.getPartialDownloads()`, `AmazonService.getPartialDownloads(context)`.
  - Controls download state: `SteamService.getAppDownloadInfo(id)?.cancel()`, `EpicService.cancelDownload(id)`, `GOGService.cancelDownload(id)`, `AmazonService.cancelDownload(id)`.
  - Resumes downloads: `SteamService.downloadApp(id)`, `EpicService.downloadGame(...)`, `GOGService.downloadGame(...)`, `AmazonService.downloadGame(...)`.
  - Deletes games: `SteamService.deleteApp(id)`, `EpicService.deleteGame(...)`, `GOGService.deleteGame(...)`, `AmazonService.deleteGame(...)`.
- **`MainViewModel.kt`**:
  - Connection & Session: `SteamService.isConnected`, `SteamService.keepAlive`, `SteamService.notifyRunningProcesses()`.
  - Game launch & cloud sync: `SteamService.closeApp(context, gameId, ...)`, `GOGService.syncCloudSaves(...)`, `EpicService.getEpicGameOf(id)`, `GOGService.getGOGGameOf(id)`, `AmazonService.getAmazonGameByAppId(id)`.
- **`UserLoginViewModel.kt`**:
  - Authentication: `SteamService.isLoggedIn`, `SteamService.isConnected`, `SteamService.startLoginWithCredentials(...)`, `SteamService.startLoginWithQr()`, `SteamService.stopLoginWithQr()`.
- **`LibraryViewModel.kt`**:
  - Library item queries, installed status checks, and sync triggers.

### 4.2 UI Screens & Components
- **`BaseAppScreen.kt`**:
  - Obtains `DownloadInfo` for display via `SteamService.getAppDownloadInfo(...)`, `EpicService.getDownloadInfo(...)`, `GOGService.getDownloadInfo(...)`, `AmazonService.getDownloadInfoByAppId(...)`.
- **`SteamAppScreen.kt`**:
  - Invokes `SteamService.isAppInstalled(...)`, `SteamService.getDownloadInfo(...)`, `SteamService.downloadApp(...)`, `SteamService.deleteApp(...)`, `SteamService.getAppDirPath(...)`, `SteamService.getAppInfoOf(...)`.
- **`EpicAppScreen.kt`**:
  - Invokes `EpicService.getEpicGameOf(...)`, `EpicService.getDLCForGame(...)`, `EpicService.fetchManifestSizes(...)`, `EpicService.downloadGame(...)`, `EpicService.deleteGame(...)`, `EpicService.cleanupDownload(...)`.
- **`GOGAppScreen.kt`**:
  - Invokes `GOGService.getGOGGameOf(...)`, `GOGService.downloadGame(...)`, `GOGService.deleteGame(...)`, `GOGService.isGameInstalled(...)`.
- **`AmazonAppScreen.kt`**:
  - Invokes `AmazonService.getAmazonGameOf(...)`, `AmazonService.getProductIdByAppId(...)`, `AmazonService.fetchDownloadSize(...)`, `AmazonService.downloadGame(...)`, `AmazonService.deleteGame(...)`.
- **`PluviaMain.kt`**:
  - Session startup checks and game launch executable resolution.

### 4.3 Domain Subsystems & Utilities
- **`CustomGameScanner.kt`**:
  - Lines 601-615: Checks `SteamService.instance != null`, calls `SteamService.findSteamAppWithInstallDir()`, `SteamService.isAppLicensed()`, `SteamService.getInstalledApp()`, and accesses `SteamService.instance?.appInfoDao`.
  - *Note: `PROJECT.md` mandates that `CustomGameScanner` injects `steamManagerProvider: Provider<SteamManager>`.*
- **`WorkshopManager.kt`**:
  - Lines 78-80, 1423, 4224-4401: Accesses `SteamService.instance?.steamClient`, `SteamService.userSteamId`, `SteamService.getAppDownloadInfo()`, `SteamService.setAppDownloadInfo()`, `SteamService.workshopPausedApps`, `SteamService.removeDownloadJob()`.
  - *Note: `PROJECT.md` mandates that `WorkshopManager` injects `steamManagerProvider: Provider<SteamManager>`.*
- **`ContainerStorageManager.kt` & `ContainerUtils.kt`**:
  - Calls `SteamService.getAppDirPath()`, `SteamService.internalAppInstallPath`, `SteamService.externalAppInstallPath`, `SteamService.deleteApp()`.
- **`SteamUtils.kt`**:
  - Heavily coupled to `SteamService` for `isLoggedIn`, `userSteamId`, `getAppDirPath`, `getAppInfoOf`, `localPersona`, `getLoginUsersVdfOauth`.
- **Downloader Dependencies**:
  - `CoreDriverDownloader`, `WinComponentDownloader`, `ContainerFilesDownloader`, `DXWrapperDownloader`, `GraphicsDriverDownloader`, `BionicSteamAssetsDependency`, `BionicDefaultProtonDependency` call `SteamService.Companion.fetchFileWithFallback()` and `SteamService.downloadFile()`.

---

## 5. Architectural Recommendations

### 5.1 Manager Architecture & Separation of Concerns

Each storefront should have a dedicated `@Singleton` Manager class containing all business logic, in-memory state, download tracking, and API communications.

```
┌───────────────────────────────────────────────────────────┐
│                      UI / ViewModels                      │
│   DownloadsViewModel, LibraryViewModel, AppScreens, etc.   │
└─────────────┬───────────────────────────────┬─────────────┘
              │ Direct @Inject / EntryPoint   │
              ▼                               │
┌───────────────────────────────┐             │ (Android Framework only:
│      Storefront Managers      │             │  startForegroundService)
│  (SteamManager, EpicManager,  │             │
│   GOGManager, AmazonManager)  │             ▼
│                               │      ┌──────────────┐
│  - @Singleton class           │◄─────┤ Android      │
│  - Owns Active Downloads Map  │Inject│ Services     │
│  - Owns Sync & Login State    │      │ (Thin Shells)│
│  - Coroutines (@AppScope/IO)  │      └──────┬───────┘
│  - Injects DAOs & Preferences │             │
└─────────────┬─────────────────┘             ▼
              │                     ┌──────────────────┐
              ▼                     │NotificationHelper│
┌───────────────────────────────┐   │ (Foreground FGS) │
│ Room DAOs, Preferences,       │   └──────────────────┘
│ AppStoragePaths, Network      │
└───────────────────────────────┘
```

#### Detailed Manager Responsibilities:
1. **`SteamManager`** (`app/src/main/java/app/gamenative/service/SteamManager.kt`):
   - **Constructor Dependencies**:
     ```kotlin
     @Singleton
     class SteamManager @Inject constructor(
         @ApplicationContext private val context: Context,
         private val appStoragePaths: AppStoragePaths,
         private val authPreferences: AuthPreferences,
         private val containerPreferences: ContainerPreferences,
         private val downloadPreferences: DownloadPreferences,
         private val generalPreferences: GeneralPreferences,
         private val libraryPreferences: LibraryPreferences,
         private val db: PluviaDatabase,
         private val appDao: SteamAppDao,
         private val licenseDao: SteamLicenseDao,
         private val appInfoDao: AppInfoDao,
         private val changeNumbersDao: ChangeNumbersDao,
         private val fileChangeListsDao: FileChangeListsDao,
         private val steamFileHashCacheDao: SteamFileHashCacheDao,
         private val cachedLicenseDao: CachedLicenseDao,
         private val encryptedAppTicketDao: EncryptedAppTicketDao,
         private val downloadingAppInfoDao: DownloadingAppInfoDao,
         private val steamUnlockedBranchDao: SteamUnlockedBranchDao,
         private val connectivityManager: ConnectivityManager,
         private val stringResolver: StringResolver,
         @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
         @ApplicationScope private val appScope: CoroutineScope,
     )
     ```
   - **State Owned**:
     - `steamClient: SteamClient`, `callbackManager: CallbackManager`
     - `localPersona: StateFlow<SteamFriend>`
     - `isLoggedIn: StateFlow<Boolean>`, `isConnected: StateFlow<Boolean>`, `userSteamId: SteamID?`
     - `downloadJobs: ConcurrentHashMap<Int, DownloadInfo>`
     - `workshopPausedApps: MutableSet<Int>`
     - `activeDownloadsFlow: StateFlow<Map<Int, DownloadInfo>>`
     - `isPlayingBlocked: StateFlow<Boolean>`
   - **Operations**:
     - Login with credentials, login with QR, logout.
     - PICS requests, app metadata caching, depot resolution.
     - App downloads, cancellation, pause/resume, partial download scanning.
     - App launch configuration, VDF generation, controller config setup.
     - Steam Auto Cloud synchronization.

2. **`EpicManager`** (`app/src/main/java/app/gamenative/service/epic/EpicManager.kt`):
   - **Constructor Dependencies**:
     ```kotlin
     @Singleton
     class EpicManager @Inject constructor(
         @ApplicationContext private val context: Context,
         private val appStoragePaths: AppStoragePaths,
         private val epicGameDao: EpicGameDao,
         private val downloadPreferences: DownloadPreferences,
         private val epicDownloadManagerProvider: Provider<EpicDownloadManager>,
         private val epicOverlayManagerProvider: Provider<EpicOverlayManager>,
         @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
         @ApplicationScope private val appScope: CoroutineScope,
     )
     ```
   - **State Absorbed from `EpicService`**:
     - `activeDownloads: ConcurrentHashMap<Int, DownloadInfo>`
     - `syncInProgress: StateFlow<Boolean>`, `lastSyncTimestamp: Long`
   - **Operations Absorbed**:
     - `downloadGame()`, `cancelDownload()`, `getActiveDownloads()`, `getPartialDownloads()`, `deleteGame()`, `isGameInstalled()`, `getInstallPath()`, `updateInstallPath()`, `getLaunchExecutable()`, `installOverlay()`.

3. **`GOGManager`** (`app/src/main/java/app/gamenative/service/gog/GOGManager.kt`):
   - **Constructor Dependencies**:
     ```kotlin
     @Singleton
     class GOGManager @Inject constructor(
         @ApplicationContext private val context: Context,
         private val appStoragePaths: AppStoragePaths,
         private val gogGameDao: GOGGameDao,
         private val downloadPreferences: DownloadPreferences,
         private val gogDownloadManagerProvider: Provider<GOGDownloadManager>,
         @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
         @ApplicationScope private val appScope: CoroutineScope,
     )
     ```
   - **State Absorbed from `GOGService`**:
     - `activeDownloads: ConcurrentHashMap<String, DownloadInfo>`
     - `syncInProgress: StateFlow<Boolean>`, `lastSyncTimestamp: Long`
   - **Operations Absorbed**:
     - `downloadGame()`, `cancelDownload()`, `getActiveDownloads()`, `getPartialDownloads()`, `deleteGame()`, `isGameInstalled()`, `syncCloudSaves()`.

4. **`AmazonManager`** (`app/src/main/java/app/gamenative/service/amazon/AmazonManager.kt`):
   - **Constructor Dependencies**:
     ```kotlin
     @Singleton
     class AmazonManager @Inject constructor(
         @ApplicationContext private val context: Context,
         private val appStoragePaths: AppStoragePaths,
         private val amazonGameDao: AmazonGameDao,
         private val downloadPreferences: DownloadPreferences,
         private val amazonDownloadManagerProvider: Provider<AmazonDownloadManager>,
         @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
         @ApplicationScope private val appScope: CoroutineScope,
     )
     ```
   - **State Absorbed from `AmazonService`**:
     - `activeDownloads: ConcurrentHashMap<String, DownloadInfo>`
     - `activeDownloadPaths: ConcurrentHashMap<String, String>`
     - `syncInProgress: StateFlow<Boolean>`, `lastSyncTimestamp: Long`
   - **Operations Absorbed**:
     - `downloadGame()`, `cancelDownload()`, `getActiveDownloads()`, `getPartialDownloads()`, `deleteGame()`, `isGameInstalled()`, `fetchDownloadSize()`, `getLaunchExecutable()`.

### 5.2 Android Services as Thin Shells

The four Android Services (`SteamService`, `EpicService`, `GOGService`, `AmazonService`) become **thin lifecycle wrappers** (~100–150 lines each) whose only jobs are:
1. Being declared in `AndroidManifest.xml` as `dataSync` foreground services.
2. Holding `@Inject lateinit var manager: ...Manager` and `@Inject lateinit var notificationHelper: NotificationHelper`.
3. Starting the foreground notification in `onStartCommand()`.
4. Forwarding intents (`ACTION_SYNC_LIBRARY`, `ACTION_MANUAL_SYNC`) to `manager.triggerSync()`.
5. Listening to `manager.activeOperationsFlow`: when active downloads and sync operations reach zero and no `keepAlive` is active, calling `stopSelf()`.
6. Tearing down foreground notifications in `onDestroy()`.
7. Providing static `start(context)` and `stop(context)` intent factory methods in `companion object`.
8. **Eradicating** static `instance` / `getInstance()` and all domain methods from the Service classes.

### 5.3 Circular Dependency Prevention

To prevent Dagger dependency cycles:
1. **`CustomGameScanner` ↔ `SteamManager`**:
   `CustomGameScanner` must inject `Provider<SteamManager>`.
2. **`WorkshopManager` ↔ `SteamManager`**:
   `WorkshopManager` must inject `Provider<SteamManager>`.
3. **Manager ↔ DownloadManager** (`EpicManager` ↔ `EpicDownloadManager`, `GOGManager` ↔ `GOGDownloadManager`, `AmazonManager` ↔ `AmazonDownloadManager`):
   Because the DownloadManager needs the Manager (e.g. `EpicDownloadManager` injects `EpicManager`), the Manager must inject the DownloadManager via `Provider<EpicDownloadManager>` or `Lazy<EpicDownloadManager>`.
4. **Manager ↔ Service**:
   Services inject Managers. Managers **NEVER** inject Services. Managers only initiate foreground services via `context.startForegroundService(Intent(context, Service::class.java))` using `@ApplicationContext context: Context`.

### 5.4 Lifecycle Leak Prevention
- Managers are `@Singleton` and live for the entire application process lifetime.
- Managers must **never** reference an Android `Activity`, `Fragment`, or UI Composable context.
- Where a framework method is strictly needed (such as `startForegroundService` or `registerReceiver`), inject `@ApplicationContext context: Context`. ApplicationContext is process-scoped and does not leak activities.
- Android Services observe Managers, not the other way around. In `onDestroy()`, the Service cleans up its own notification and subscriptions without cancelling the Manager's long-running background tasks.

---

## 6. Implementation Blueprint for Group 4

To implement Group 4 cleanly without regressions, follow this staged rollout:

### Phase 1: DI & Infrastructure Foundation
1. Add `fun steamManager(): SteamManager`, `fun epicManager(): EpicManager`, `fun gogManager(): GOGManager`, and `fun amazonManager(): AmazonManager` to `AppUtilsEntryPoint`.
2. Clean up `NotificationHelper` to inject `GeneralPreferences` without fallback to `PreferencesEntryPoint`.

### Phase 2: Create `SteamManager` & Refactor `SteamService`
1. Create `app/src/main/java/app/gamenative/service/SteamManager.kt`:
   - Move client connection, authentication, PICS caching, licenses, active download jobs, cloud sync, and launch configuration from `SteamService.kt` into `SteamManager`.
   - Inject all DAOs, preferences, dispatchers, and system services directly into `@Inject constructor`.
2. Shrink `SteamService.kt` into a thin foreground service shell delegating to `SteamManager`.
3. Migrate `CustomGameScanner` and `WorkshopManager` to receive `Provider<SteamManager>`.

### Phase 3: Enhance `EpicManager`, `GOGManager`, `AmazonManager` & Thin their Services
1. **Epic**: Move `activeDownloads`, sync state, and download operations from `EpicService` companion into `EpicManager`. Fix constructor to inject non-null `DownloadPreferences`.
2. **GOG**: Move `activeDownloads`, sync state, and download operations from `GOGService` companion into `GOGManager`. Replace `Context` path calls with `AppStoragePaths`.
3. **Amazon**: Move `activeDownloads`, `activeDownloadPaths`, installation checks, download handling, and logout DB cleanup from `AmazonService` into `AmazonManager`. Eliminate `AmazonDaoEntryPoint`.
4. Turn `EpicService`, `GOGService`, and `AmazonService` into thin foreground shells.

### Phase 4: Downstream Call Site Migration
1. Inject `SteamManager`, `EpicManager`, `GOGManager`, and `AmazonManager` into `DownloadsViewModel`, `MainViewModel`, `UserLoginViewModel`, and `LibraryViewModel`.
2. Update `BaseAppScreen`, `SteamAppScreen`, `EpicAppScreen`, `GOGAppScreen`, and `AmazonAppScreen` to obtain managers via `context.appUtilsEntryPoint()`.
3. Update `ContainerStorageManager`, `ContainerUtils`, `SteamUtils`, and downloader utilities.
4. Verify compile with `./gradlew compileModernDebugKotlin` and unit tests with `./gradlew :app:testModernDebugUnitTest`.

---

## 7. Conclusion

Group 4 represents the most critical architectural transition in the "Eradicate Mid-Level Singletons" project. By decoupling the business logic and active state from Android Service lifecycles into `@Singleton` Managers (`SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager`), GameNative will eliminate silent `instance == null` crashes, remove remaining `PreferencesEntryPoint` and `EntryPointAccessors` escape hatches, stop `Context` prop-drilling, and establish a resilient, testable, and reactive storefront subsystem.
