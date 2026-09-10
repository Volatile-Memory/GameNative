# Group 4 Storefront Exploration Report: EpicService & GOGService

## Executive Summary
This report presents an architectural investigation of `EpicService` and `GOGService` and their relationship with `EpicManager` and `GOGManager` in GameNative. Both Android `Service` classes currently act as hidden mid-level singletons: their companion objects expose static instance accessors (`getInstance()`), maintain mutable static state (`syncInProgress`, `lastSyncTimestamp`, `backgroundSyncJob`, `hasPerformedInitialSync`), and access instance-level state (`activeDownloads: ConcurrentHashMap`). Furthermore, business logic, game installation status checks, download dispatching, cloud save synchronizations, and auth delegates are routed through static methods on the Android Services across over 50 files.

To achieve the architectural goals of Group 4 (Milestone M3), the business logic, state maps, and API client orchestrations will be extracted into `@Singleton class EpicManager @Inject constructor(...)` and `@Singleton class GOGManager @Inject constructor(...)`. The Android Services (`EpicService` and `GOGService`) will be reduced to thin shells managing only Android foreground service lifecycles and notifications.

---

## 1. File Locations & Component Overview

### 1.1 Epic Subsystem
- **`EpicService.kt`**: `app/src/main/java/app/gamenative/service/epic/EpicService.kt` (761 lines)
  - Annotated with `@AndroidEntryPoint`.
  - Holds `instance: EpicService?` companion singleton.
  - Holds instance fields: `notificationHelper`, `epicManager: EpicManager`, `epicDownloadManager: EpicDownloadManager`, `epicOverlayManager: EpicOverlayManager`, `scope: CoroutineScope`, `activeDownloads = ConcurrentHashMap<Int, DownloadInfo>()`.
- **`EpicManager.kt`**: `app/src/main/java/app/gamenative/service/epic/EpicManager.kt` (1141 lines)
  - Annotated with `@Singleton class EpicManager @Inject constructor(epicGameDao: EpicGameDao, downloadPreferences: DownloadPreferences? = null)`.
  - Handles Room DB operations (`EpicGameDao`), Epic Catalog API calls (`fetchCatalogItem`, `fetchGameInfo`), library pagination (`fetchLibrary`), manifest fetching (`fetchManifestFromEpic`), EOS deployment ID resolution (`fetchDeploymentId`), and additional command-line parameters (`fetchAdditionalCommandLine`).
  - Currently lacks: `activeDownloads` map, `downloadGame` dispatch, `deleteGame` container cleanup, installation detection, and sync lifecycle tracking.
- **Supporting Epic Classes**:
  - `EpicDownloadManager.kt`: `@Singleton class EpicDownloadManager @Inject constructor(epicManager: EpicManager, @ApplicationContext context: Context, downloadPreferences: DownloadPreferences? = null)` (Note: `epicManager` parameter is completely unused in this class).
  - `EpicOverlayManager.kt`: `@Singleton class EpicOverlayManager @Inject constructor(epicManager: EpicManager, epicDownloadManager: EpicDownloadManager)`.
  - `EpicAuthManager.kt`: `object EpicAuthManager` (handles OAuth tokens, credential files, ownership tokens).
  - `EpicCloudSavesManager.kt`: `object EpicCloudSavesManager` (handles manifest-based cloud saves).
  - `EpicGameLauncher.kt`: `object EpicGameLauncher` (Wine launch parameters and ownership token staging).
  - `EpicConstants.kt`: `object EpicConstants` (URLs, paths).

### 1.2 GOG Subsystem
- **`GOGService.kt`**: `app/src/main/java/app/gamenative/service/gog/GOGService.kt` (850 lines)
  - Annotated with `@AndroidEntryPoint`.
  - Holds `instance: GOGService?` companion singleton.
  - Holds instance fields: `notificationHelper`, `gogManager: GOGManager`, `gogDownloadManager: GOGDownloadManager`, `scope: CoroutineScope`, `activeDownloads = ConcurrentHashMap<String, DownloadInfo>()`.
  - Companion object defines complex operations: `syncCloudSaves(...)`, `detectCloudSaveConflict(...)`, `getPartialDownloads()`, `downloadGame(...)`, etc.
- **`GOGManager.kt`**: `app/src/main/java/app/gamenative/service/gog/GOGManager.kt` (1289 lines)
  - Annotated with `@Singleton class GOGManager @Inject constructor(gogGameDao: GOGGameDao, @ApplicationContext context: Context)`.
  - Handles Room DB operations (`GOGGameDao`), library sync (`GOGApiClient`), installation detection (`detectAndUpdateExistingInstallations`, `detectGameFromDirectory`), uninstallation (`deleteGame`), executable discovery (`getInstalledExe`, `getLaunchExecutable`), Wine launch command generation (`getGogWineStartCommand`, `getScriptInterpreterPartsForLaunch`), and cloud save path resolution (`getSaveDirectoryPath`).
  - Currently lacks: `activeDownloads` map, `downloadGame` dispatch, cloud save sync orchestration (`syncCloudSaves`, `detectCloudSaveConflict`), and sync lifecycle tracking.
- **Supporting GOG Classes**:
  - `GOGDownloadManager.kt`: `@Singleton class GOGDownloadManager @Inject constructor(apiClient: GOGApiClient, parser: GOGManifestParser, gogManager: GOGManager, @ApplicationContext context: Context)` (Note: `gogManager` parameter is completely unused in this class).
  - `GOGAuthManager.kt`: `object GOGAuthManager` (OAuth tokens and credential storage).
  - `GOGApiClient.kt`: `object GOGApiClient` (HTTP API queries for library and game details).
  - `GOGCloudSavesManager.kt`: `class GOGCloudSavesManager(context: Context)` (per-operation cloud save client).
  - `GOGConstants.kt`: `object GOGConstants` (contains mutable static `appContext: Context?`).

---

## 2. Business Logic & State vs Android Service Lifecycle

### 2.1 State & Logic Trapped in Services and Companion Objects

| Subsystem | Component | Trapped Logic / State | Impact / Flaw |
|---|---|---|---|
| **Epic** | `EpicService.activeDownloads` | `ConcurrentHashMap<Int, DownloadInfo>` instance field | If Android kills the service, download tracking is severed from UI even if coroutines continue or chunks exist. |
| **Epic** | `EpicService.Companion` | `syncInProgress: Boolean`, `backgroundSyncJob: Job?`, `lastSyncTimestamp: Long`, `hasPerformedInitialSync: Boolean` | Static state surviving across service recreations but resetting on process death without repository persistence. |
| **Epic** | `EpicService.Companion.downloadGame` | Creates `DownloadInfo`, adds to `activeDownloads`, launches coroutine on `instance.scope`, runs post-install save sync via `EpicCloudSavesManager`, shows `SnackbarManager` | Business workflow tightly bound to `instance != null`. Fails if service is not started. |
| **Epic** | `EpicService.Companion.deleteGame` | Deletes files, clears chunk cache, uninstalls DB entry, calls `ContainerUtils.deleteContainer`, emits `LibraryInstallStatusChanged` | Crucial game uninstallation logic trapped in Service companion instead of `EpicManager`. |
| **Epic** | `EpicService.Companion.*` | Wrappers for Auth (`authenticateWithCode`, `getStoredCredentials`, `logout`), Overlay (`installOverlay`), Launch tokens (`getGameLaunchToken`, `buildLaunchParameters`) | Service acts as an indiscriminate service locator for disparate subsystems. |
| **GOG** | `GOGService.activeDownloads` | `ConcurrentHashMap<String, DownloadInfo>` instance field | Same download state fragility as Epic. |
| **GOG** | `GOGService.Companion` | `syncInProgress: Boolean`, `backgroundSyncJob: Job?`, `lastSyncTimestamp: Long`, `hasPerformedInitialSync: Boolean` | Static state preventing clean test isolation and DI scoping. |
| **GOG** | `GOGService.Companion.downloadGame` | Creates `DownloadInfo`, adds to `activeDownloads`, launches on `instance.scope`, post-install sync with `syncCloudSaves`, shows `SnackbarManager` | Service instance required for initiating game downloads. |
| **GOG** | `GOGService.Companion.syncCloudSaves` & `detectCloudSaveConflict` | Full cloud save sync and conflict detection loops (lines 471–709 of `GOGService.kt`) | Core domain logic located inside Android Service companion instead of `GOGManager` or a dedicated Cloud Save coordinator. |
| **GOG** | `GOGService.Companion.*` | Wrappers for `GOGAuthManager` (`authenticateWithCode`, `validateCredentials`, `logout`) | Static forwarding antipattern. |

### 2.2 Legitimate Android Service Lifecycle Duties (Retained in Thin Services)
The only responsibilities that belong in `EpicService` and `GOGService` are:
1. **Foreground Service Lifecycle**: Calling `startForeground(...)` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC` and `stopForeground(...)`.
2. **Notification Channel Management**: Instantiating and managing `NotificationHelper` to display persistent notifications while syncing or downloading.
3. **Intent Dispatching**: Receiving intents from the system or alarms (`ACTION_SYNC_LIBRARY`, `ACTION_MANUAL_SYNC`, null intents for `START_STICKY` restarts) and delegating execution to injected managers.
4. **Lifecycle Hooks**:
   - `onTimeout(startId, fgsType)`: Handling Android 14+ foreground service timeouts.
   - `onTaskRemoved(rootIntent)`: Calling `manager.hasActiveOperations()` to decide whether to `stopSelf()`.
   - `onDestroy()`: Releasing notification channels and unsubscribing from event listeners.

---

## 3. Escape Hatches & Service-Locator Patterns

### 3.1 PreferencesEntryPoint & EntryPointAccessors Usages
- **`EpicManager.kt`**:
  - Line 6: `import app.gamenative.preferences.PreferencesEntryPoint`
  - Lines 44–46:
    ```kotlin
    val prefs = downloadPreferences ?: runCatching {
        PreferencesEntryPoint.get(PluviaApp.instance).downloadPreferences()
    }.getOrNull()
    ```
  - Constructor: `downloadPreferences: DownloadPreferences? = null` allows bypassing Hilt injection with an escape hatch.
- **`EpicService.kt`**:
  - Line 20: `import app.gamenative.preferences.PreferencesEntryPoint`
  - Line 201: `if (PreferencesEntryPoint.get(context).downloadPreferences().externalStoragePath.isNotBlank())` in `getPartialInstallPaths()`.
- **`EpicConstants.kt`**:
  - Lines 125, 138: `PreferencesEntryPoint.get(targetContext).downloadPreferences().externalStoragePath` in `externalEpicGamesPath()` and `defaultEpicGamesPath()`.
- **`EpicDownloadManager.kt`**:
  - Line 515: `val prefs = downloadPreferences ?: PreferencesEntryPoint.get(context).downloadPreferences()`.
- **`GOGService.kt`**:
  - Line 17: `import app.gamenative.preferences.PreferencesEntryPoint`
  - Line 222: `PreferencesEntryPoint.get(PluviaApp.instance).downloadPreferences().externalStoragePath` in `getPartialInstallPaths()`.
- **`GOGConstants.kt`**:
  - Lines 131, 143: `PreferencesEntryPoint.get(context).downloadPreferences().externalStoragePath` and `downloadPreferences()`.

### 3.2 Context as a Service-Locator
- **`EpicManager.kt`**:
  - `Context` is prop-drilled into `refreshLibrary(context)`, `fetchLibrary(context)`, `fetchGameInfo(context, ...)`, `fetchManifestFromEpic(context, ...)`, `fetchDeploymentId(context, ...)`, `fetchAdditionalCommandLine(context, ...)`, `fetchManifestSizes(context, ...)`.
  - In reality, `context` is used only for:
    - Calling `EpicAuthManager.getStoredCredentials(context)`
    - Accessing `context.filesDir` (e.g. `File(context.filesDir, "epic/deployment_ids")`, `File(context.filesDir, "epic/additional_cmdline")`)
- **`GOGManager.kt`**:
  - Injects `@ApplicationContext private val context: Context`.
  - Uses `context.filesDir` for `timestampFile = File(context.filesDir, "gog_sync_timestamps.json")` and `manifests/$gameId`.
  - Uses `context.cacheDir` for `gog_chunks/$gameId`.
  - Passes `context` to static `GOGApiClient` and `GOGAuthManager`.
- **`GOGConstants.kt`**:
  - Stores a mutable static `var appContext: Context? = null` with fallback to `PluviaApp.instance`.

---

## 4. Call Sites & Codebase Usage Inventory

A comprehensive grep across `app/src` identified **25 files** referencing `EpicService` and **27 files** referencing `GOGService`.

### 4.1 Categorized Call Sites

| Category | File | Usages of `EpicService` / `GOGService` |
|---|---|---|
| **ViewModels** | `DownloadsViewModel.kt` | Calls `getActiveDownloads()`, `getPartialDownloads()`, `cancelDownload()`, `downloadGame()`, `deleteGame()` on both services. |
| | `LibraryViewModel.kt` | Calls `hasStoredCredentials()`, `triggerLibrarySync()` for conditional tab counts and manual sync. |
| | `MainViewModel.kt` | Calls `getEpicGameOf(gameId)`, `getGOGGameOf(gameId)`, `GOGService.syncCloudSaves(...)`. |
| **UI App Screens** | `EpicAppScreen.kt` | 32 invocations: `getEpicGameOf`, `getDLCForGame`, `fetchManifestSizes`, `updateEpicGame`, `isGameInstalled`, `getDownloadInfo`, `hasPartialDownload`, `cleanupDownload`, `downloadGame`, `deleteGame`, `getInstallPath`. |
| | `GOGAppScreen.kt` | 35 invocations: `syncCloudSaves`, `getGOGGameOf`, `isGameInstalled`, `getDownloadInfo`, `hasPartialDownload`, `cleanupDownload`, `downloadGame`, `deleteGame`, `getInstallPath`. |
| | `BaseAppScreen.kt` | Calls `EpicService.getDownloadInfo(...)` and `GOGService.getDownloadInfo(...)` for download progress bar display. |
| **UI Components** | `LibraryScreen.kt` | Checks `!EpicService.hasStoredCredentials(context)` and `!GOGService.hasStoredCredentials(context)` to show login banners. |
| | `LibraryListPane.kt` | Checks credentials to compute storefront badge counts. |
| | `EpicGameManagerDialog.kt` | Calls `EpicService.deleteGame(...)` and queries installed status. |
| | `PluviaMain.kt` | Queries install paths and executables before container launch. |
| | `XServerScreen.kt` | Resolves game info, executables, and launch parameters via `EpicService.getInstance()?.epicManager` and `GOGService.getInstance()?.gogManager`. |
| **Utils & Domain** | `ContainerUtils.kt` | Resolves game titles, install paths, and updates install paths for `GameSource.EPIC` and `GameSource.GOG`. |
| | `ContainerStorageManager.kt` | Deletes games and queries titles. |
| | `GameFeedbackUtils.kt` | Retrieves `EpicGame` / `GOGGame` for crash and feedback reports. |
| | `XAudioUtils.kt` | Queries game install path to check for native audio configurations. |
| | `PlatformOAuthHandlers.kt` | Handles login callbacks: `EpicService.authenticateWithCode()`, `start()`, `triggerLibrarySync()`; same for GOG. |
| | `PlatformAuthUtils.kt` | Checks `EpicService.hasStoredCredentials(context)` / GOG credentials. |
| | `PlatformAuthUiHelpers.kt` | Handles user-initiated logout: `EpicService.logout(context)` / `GOGService.logout(context)`. |
| **Launch Pre-requisites** | `EpicOverlayDependency.kt` | Calls `EpicService.installOverlay(...)`. |
| | `GogScriptInterpreterDependency.kt` | Calls `GOGService.getInstallPath(...)` and `GOGService.getInstance()?.gogDownloadManager`. |
| | `GogScriptInterpreterStep.kt` | Calls `GOGService.getInstance()?.gogManager?.getScriptInterpreterPartsForLaunch(...)`. |
| | `GameFixesRegistry.kt` & `GOGDependencyFix.kt` | Resolves install paths for registry patches. |
| **Lifecycle & App** | `MainActivity.kt` | Manages service lifecycle based on credentials and active operations (`isRunning`, `start`, `stop`, `hasActiveOperations`). |
| | `AndroidManifest.xml` | Registers `EpicService` and `GOGService` foreground service declarations. |
| **Unit Tests** | `GogScriptInterpreterStepTest.kt` | Mocks `GOGService.Companion` and `GOGService.getInstance()`. |
| | `GogScriptInterpreterDependencyTest.kt` | Mocks `GOGService.Companion`. |
| | `GOGDependencyFixTest.kt` | Tests GOG dependency fixes with mock paths. |
| | `GameFixesRegistryTest.kt` | Tests fix registry with mock services. |

---

## 5. Architectural Design: EpicManager & GOGManager

### 5.1 Circular Dependency Elimination
A key finding during this investigation:
- `EpicDownloadManager` currently has `private val epicManager: EpicManager` in its constructor, but **does not use it anywhere in the class**.
- `GOGDownloadManager` currently has `private val gogManager: GOGManager` in its constructor, but **does not use it anywhere in the class**.
- `EpicOverlayManager` uses `epicManager.fetchManifestFromEpic(...)` and `epicDownloadManager.downloadDirectChunks(...)`.

When moving `downloadGame(...)` and overlay coordination into `EpicManager` and `GOGManager`:
1. Remove the unused `epicManager` from `EpicDownloadManager`'s constructor.
2. Remove the unused `gogManager` from `GOGDownloadManager`'s constructor.
3. Inject `EpicDownloadManager` into `EpicManager` (or wrap with `Provider<EpicDownloadManager>` / `Lazy<EpicDownloadManager>` to ensure zero circularity risk).
4. Inject `GOGDownloadManager` into `GOGManager` (or wrap with `Provider<GOGDownloadManager>`).

### 5.2 Target Manager Contracts

#### `EpicManager`
```kotlin
@Singleton
class EpicManager @Inject constructor(
    private val epicGameDao: EpicGameDao,
    private val downloadPreferences: DownloadPreferences,
    @ApplicationContext private val context: Context,
    private val epicDownloadManager: Provider<EpicDownloadManager>,
    private val epicOverlayManager: Provider<EpicOverlayManager>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    // Active download map (state moved from EpicService)
    private val activeDownloads = ConcurrentHashMap<Int, DownloadInfo>()

    // Sync state (state moved from EpicService.Companion)
    private var syncInProgress: Boolean = false
    private var backgroundSyncJob: Job? = null
    private var lastSyncTimestamp: Long = 0L
    private var hasPerformedInitialSync: Boolean = false

    // State observers for EpicService foreground notifications
    var onSyncStatusChanged: ((Boolean) -> Unit)? = null
    var onDownloadTracked: ((DownloadInfo, String) -> Unit)? = null

    // Game Database & Library Operations
    suspend fun getGameById(appId: Int): EpicGame?
    suspend fun getGameByAppName(appName: String): EpicGame?
    suspend fun getDLCForTitle(appId: Int): List<EpicGame>
    suspend fun updateGame(game: EpicGame)
    suspend fun isGameInstalled(appId: Int): Boolean
    suspend fun getInstallPath(appId: Int): String?
    suspend fun updateInstallPath(appId: Int, path: String)
    suspend fun getInstalledExe(appId: Int): String
    suspend fun getLaunchExecutable(containerId: String): String
    suspend fun refreshLibrary(): Result<Int>
    suspend fun startBackgroundSync(): Result<Unit>

    // Download Operations (moved from EpicService)
    fun getActiveDownloads(): Map<Int, DownloadInfo>
    fun getDownloadInfo(appId: Int): DownloadInfo?
    fun hasActiveDownload(): Boolean
    fun getCurrentlyDownloadingGame(): Int?
    fun hasPartialDownload(appId: Int): Boolean
    suspend fun getPartialDownloads(): List<Int>
    fun downloadGame(appId: Int, dlcGameIds: List<Int>, installPath: String, containerLanguage: String): Result<DownloadInfo>
    fun cancelDownload(appId: Int): Boolean
    suspend fun cleanupDownload(appId: Int)

    // Maintenance & Game Uninstallation (moved from EpicService)
    suspend fun deleteGame(appId: Int): Result<Unit>
    suspend fun deleteAllNonInstalledGames()
    fun hasActiveOperations(): Boolean

    // Overlay & Launcher Helpers
    suspend fun installOverlay(container: Container, forceReinstall: Boolean = false, onProgress: ((Int, Int) -> Unit)? = null): Result<Unit>
    suspend fun removeOverlay(container: Container): Result<Unit>
    suspend fun getGameLaunchToken(namespace: String? = null, catalogItemId: String? = null, requiresOwnershipToken: Boolean = false): Result<EpicGameToken>
    suspend fun buildLaunchParameters(container: Container, game: EpicGame, offline: Boolean = false, languageCode: String = "en-US"): Result<List<String>>

    // Auth Helpers
    fun hasStoredCredentials(): Boolean
    suspend fun getStoredCredentials(): Result<EpicCredentials>
    suspend fun authenticateWithCode(authorizationCode: String): Result<EpicCredentials>
    suspend fun logout(): Result<Unit>
}
```

#### `GOGManager`
```kotlin
@Singleton
class GOGManager @Inject constructor(
    private val gogGameDao: GOGGameDao,
    private val downloadPreferences: DownloadPreferences,
    @ApplicationContext private val context: Context,
    private val gogDownloadManager: Provider<GOGDownloadManager>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    // Active download map (state moved from GOGService)
    private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()

    // Sync state (state moved from GOGService.Companion)
    private var syncInProgress: Boolean = false
    private var backgroundSyncJob: Job? = null
    private var lastSyncTimestamp: Long = 0L
    private var hasPerformedInitialSync: Boolean = false

    // State observers for GOGService foreground notifications
    var onSyncStatusChanged: ((Boolean) -> Unit)? = null
    var onDownloadTracked: ((DownloadInfo, String) -> Unit)? = null

    // Game Database & Library Operations
    suspend fun getGameFromDbById(gameId: String): GOGGame?
    suspend fun getNonInstalledGames(): List<GOGGame>
    suspend fun updateGame(game: GOGGame)
    fun isGameInstalled(gameId: String): Boolean
    fun getInstallPath(gameId: String): String?
    fun updateInstallPath(gameId: String, path: String)
    fun verifyInstallation(gameId: String): Pair<Boolean, String?>
    suspend fun getInstalledExe(libraryItem: LibraryItem): String
    suspend fun getLaunchExecutable(appId: String, container: Container): String
    suspend fun refreshLibrary(): Result<Int>
    suspend fun startBackgroundSync(): Result<Unit>

    // Wine & Script Interpreter Helpers
    fun getGogWineStartCommand(libraryItem: LibraryItem, container: Container, bootToContainer: Boolean, appLaunchInfo: LaunchInfo?, envVars: EnvVars, guestProgramLauncherComponent: GuestProgramLauncherComponent, gameId: Int): String
    fun getScriptInterpreterPartsForLaunch(appId: String): List<String>

    // Download Operations (moved from GOGService)
    fun getActiveDownloads(): Map<String, DownloadInfo>
    fun getDownloadInfo(gameId: String): DownloadInfo?
    fun hasActiveDownload(): Boolean
    fun getCurrentlyDownloadingGame(): String?
    fun hasPartialDownload(gameId: String, fallbackTitle: String? = null): Boolean
    suspend fun getPartialDownloads(): List<String>
    fun downloadGame(gameId: String, installPath: String, containerLanguage: String): Result<DownloadInfo?>
    fun cancelDownload(gameId: String): Boolean
    fun cleanupDownload(gameId: String)

    // Maintenance & Game Uninstallation
    suspend fun deleteGame(libraryItem: LibraryItem): Result<Unit>
    suspend fun deleteAllNonInstalledGames()
    fun hasActiveOperations(): Boolean

    // Cloud Saves & Sync (moved from GOGService.Companion)
    suspend fun syncCloudSaves(appId: String, preferredAction: String = "none"): Boolean
    suspend fun detectCloudSaveConflict(appId: String): GOGService.GogConflict?

    // Auth Helpers
    fun hasStoredCredentials(): Boolean
    suspend fun getStoredCredentials(): Result<GOGCredentials>
    suspend fun authenticateWithCode(authorizationCode: String): Result<GOGCredentials>
    suspend fun validateCredentials(): Result<Boolean>
    suspend fun logout(): Result<Unit>
}
```

### 5.3 Thin Shell Android Services
`EpicService` and `GOGService` become lightweight foreground coordinator shells:
```kotlin
@AndroidEntryPoint
class EpicService : Service() {
    companion object {
        private const val ACTION_SYNC_LIBRARY = "app.gamenative.EPIC_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "app.gamenative.EPIC_MANUAL_SYNC"
        var isRunning: Boolean = false
            private set

        fun start(context: Context) { ... }
        fun triggerLibrarySync(context: Context) { ... }
        fun stop(context: Context) { ... }
    }

    @Inject lateinit var epicManager: EpicManager
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        notificationHelper = NotificationHelper(applicationContext)
        epicManager.onSyncStatusChanged = { syncing ->
            if (syncing) notificationHelper.showSyncing(NotificationHelper.NOTIFICATION_ID_EPIC)
            else notificationHelper.showIdle(NotificationHelper.NOTIFICATION_ID_EPIC)
        }
        epicManager.onDownloadTracked = { info, title ->
            notificationHelper.trackDownload(info, title, NotificationHelper.NOTIFICATION_ID_EPIC)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground notification
        val notification = notificationHelper.createServiceNotification(...)
        startForeground(NotificationHelper.NOTIFICATION_ID_EPIC, notification, ...)
        
        // Delegate sync logic to epicManager
        ...
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!epicManager.hasActiveOperations()) stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        epicManager.onSyncStatusChanged = null
        epicManager.onDownloadTracked = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
```

### 5.4 Composable / UI Access Strategy
For ViewModels, inject `EpicManager` and `GOGManager` directly in `@HiltViewModel` constructors:
- `DownloadsViewModel @Inject constructor(epicManager: EpicManager, gogManager: GOGManager, ...)`
- `LibraryViewModel @Inject constructor(epicManager: EpicManager, gogManager: GOGManager, ...)`
- `MainViewModel @Inject constructor(epicManager: EpicManager, gogManager: GOGManager, ...)`

For Composable trees (`EpicAppScreen`, `GOGAppScreen`, `BaseAppScreen`):
- Expose methods/flows through the corresponding ViewModel (preferred pattern in Compose architecture).
- For direct Composable tree dependencies (such as in `BaseAppScreen` or utility functions), provide an EntryPoint:
```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface StorefrontEntryPoint {
    fun epicManager(): EpicManager
    fun gogManager(): GOGManager
    fun steamManager(): SteamManager
    fun amazonManager(): AmazonManager
}
```
`AppUtilsEntryPoint` can also be extended with these accessors to match the pattern used for `HltbService` and `SteamGridDB` in Milestone M1.

---

## 6. Actionable Implementation Plan (Milestone M3)

### Phase 1: Dependency Cleanup & Constructor Preparation
1. Remove the unused `epicManager` constructor parameter from `EpicDownloadManager.kt`.
2. Remove the unused `gogManager` constructor parameter from `GOGDownloadManager.kt`.
3. In `EpicManager.kt`:
   - Change `downloadPreferences: DownloadPreferences? = null` to non-null `downloadPreferences: DownloadPreferences`.
   - Remove `PreferencesEntryPoint.get(PluviaApp.instance).downloadPreferences()`.
   - Inject `@ApplicationContext context: Context`, `Provider<EpicDownloadManager>`, `Provider<EpicOverlayManager>`, and `@IoDispatcher ioDispatcher: CoroutineDispatcher`.
4. In `GOGManager.kt`:
   - Inject `downloadPreferences: DownloadPreferences`, `Provider<GOGDownloadManager>`, and `@IoDispatcher ioDispatcher: CoroutineDispatcher`.
   - Remove `PreferencesEntryPoint` from path calculation fallbacks.

### Phase 2: Business Logic & State Migration into Managers
1. Move `activeDownloads: ConcurrentHashMap` from `EpicService` into `EpicManager`.
2. Move `activeDownloads: ConcurrentHashMap` from `GOGService` into `GOGManager`.
3. Move `syncInProgress`, `backgroundSyncJob`, `lastSyncTimestamp`, and `hasPerformedInitialSync` from Service companion objects into their respective managers.
4. Move `downloadGame`, `cancelDownload`, `cleanupDownload`, `getDownloadInfo`, `hasActiveDownload`, `getCurrentlyDownloadingGame`, `getActiveDownloads`, `hasPartialDownload`, and `getPartialDownloads` into `EpicManager` and `GOGManager`.
5. Move `deleteGame` from `EpicService` into `EpicManager`.
6. Move `syncCloudSaves` and `detectCloudSaveConflict` from `GOGService.Companion` into `GOGManager`.
7. Add listener callbacks (`onSyncStatusChanged`, `onDownloadTracked`) or Kotlin SharedFlows in the Managers for foreground service notifications.

### Phase 3: Android Services Refactoring into Thin Shells
1. Remove all static game operations, download methods, and auth forwarding from `EpicService.Companion` and `GOGService.Companion`.
2. Inject `EpicManager` into `EpicService` and `GOGManager` into `GOGService`.
3. Retain only foreground notifications, intent parsing (`ACTION_SYNC_LIBRARY`, `ACTION_MANUAL_SYNC`), and lifecycle coordination in the Android Services.

### Phase 4: Call Site Migration
1. Update `DownloadsViewModel`, `LibraryViewModel`, and `MainViewModel` to inject `EpicManager` and `GOGManager`.
2. Update `EpicAppScreen.kt` and `GOGAppScreen.kt` to call manager instance methods (via ViewModel or EntryPoint).
3. Update `ContainerUtils.kt`, `ContainerStorageManager.kt`, `GameFeedbackUtils.kt`, and `XAudioUtils.kt`.
4. Update launch pre-requisites: `EpicOverlayDependency.kt`, `GogScriptInterpreterDependency.kt`, and `GogScriptInterpreterStep.kt` to use injected managers or EntryPoint instead of `Service.getInstance()`.
5. Update unit tests (`GogScriptInterpreterStepTest.kt`, `GogScriptInterpreterDependencyTest.kt`, `GOGDependencyFixTest.kt`, `GameFixesRegistryTest.kt`) to mock `GOGManager` directly instead of mocking static Service companion objects.

---

## Conclusion
`EpicService` and `GOGService` are classic Android "God Service" hidden singletons that improperly tie database access, network synchronization, file deletions, and active download state to an Android `Service` instance. Because `EpicManager` and `GOGManager` already exist as `@Singleton class` components with Room DAOs, the path forward is clean and direct: transfer the active download state maps, download dispatching, cloud saves, and business operations into the Managers, eliminate the unused circular constructor dependencies in `EpicDownloadManager` and `GOGDownloadManager`, remove all `PreferencesEntryPoint` escape hatches, and reduce the Android Services to thin foreground notification handlers.
