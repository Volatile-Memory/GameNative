# Comprehensive DI Refactoring Survey Report: Groups 3 & 4

**Project**: GameNative / Pluvia  
**Refactoring Initiative**: Eradicate Mid-Level Singletons & Service Locators  
**Scope**: Group 3 (User Library Managers) & Group 4 (Storefront Services / Hidden Singletons)  
**Survey Date**: 2026-09-02  
**Target Output**: `survey_report.md`

---

## 1. Executive Summary

This survey provides a comprehensive architectural analysis and implementation blueprint for refactoring:
- **Group 3 (User Library Managers)**: `FavoritesManager`, `FrontendSyncManager`, `CustomGameScanner`
- **Group 4 (Storefront Services / Hidden Singletons)**: `EpicService` / `EpicManager`, `GOGService` / `GOGManager`, `AmazonService` / `AmazonManager`, `SteamService` / `SteamManager`

### Core Problems Identified in Current Codebase
1. **Hidden Service Singletons**: `SteamService`, `EpicService`, `GOGService`, and `AmazonService` currently host massive static companion objects (`companion object { var instance: Service? ... }`) with stateful maps, sync locks, and business logic. Downstream consumers (ViewModels, UI Composables, background workers) invoke static methods directly on Android `Service` classes (e.g., `SteamService.isAppInstalled(appId)` or `EpicService.downloadGame(...)`).
2. **EntryPoint Escape Hatches**: Classes such as `FrontendSyncManager`, `AmazonService`, `SteamService`, `SteamWishlistService`, and `EpicManager` use `EntryPointAccessors.fromApplication(...)` or `PreferencesEntryPoint.get(...)` to pull dependencies out of Hilt dynamically rather than receiving them via constructor injection.
3. **Global Object Singletons**: `FrontendSyncManager`, `CustomGameScanner`, and `FavoritesManager` are declared as Kotlin `object` singletons holding mutable state or relying on late initialization from `PluviaApp.onCreate()`.
4. **Untestable Code**: Tests (e.g., `SteamAutoCloudTest`) rely on Java reflection (`getDeclaredField("instance")`) to force mock instances into static companion singletons.

### Target Architecture
1. Convert all targeted business components into `@Singleton class ... @Inject constructor(...)` components.
2. Separate Android foreground `Service` shells (which only handle OS foreground service notifications, lifecycle events, and process keep-alive) from the underlying storefront domain managers (`SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager`).
3. Inject domain preferences (`AuthPreferences`, `ContainerPreferences`, `DownloadPreferences`, `LibraryPreferences`, `GeneralPreferences`), Room DAOs, `AppStoragePaths`, and `StringResolver` directly via constructor injection, eliminating all `EntryPoint` accessors and `Context` prop-drilling.

---

## 2. Group 3: User Library Managers

### 2.1 `FavoritesManager`

#### A. File Location & Current Declaration
- **File**: `app/src/main/java/app/gamenative/data/FavoritesManager.kt`
- **Current Declaration**:
  ```kotlin
  object FavoritesManager : FavoritesRepository {
      @Volatile
      internal var delegate: FavoritesRepository = object : FavoritesRepository {
          private val _favorites = MutableStateFlow<Set<String>>(emptySet())
          override val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
          override val loaded: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
          override fun toggle(appId: String): Boolean? = null
      }
      override val favorites: StateFlow<Set<String>> get() = delegate.favorites
      override val loaded: StateFlow<Boolean> get() = delegate.loaded
      override fun toggle(appId: String): Boolean? = delegate.toggle(appId)
  }
  ```

#### B. State, Lifecycle & Coroutine Requirements
- Implements `FavoritesRepository` (interface defined in `app/src/main/java/app/gamenative/data/FavoritesRepository.kt`).
- The actual implementation `DefaultFavoritesRepository` (`app/src/main/java/app/gamenative/data/DefaultFavoritesRepository.kt`) is already an `@Singleton class` with `@Inject constructor(libraryPreferences: LibraryPreferences)`.
- In `PluviaApp.kt` (line 81), `FavoritesManager.delegate = favoritesRepository` is used to bridge static calls.

#### C. Escape Hatches & Service Locators
- Static singleton object bridging to `FavoritesRepository`.
- Composables (`LibraryScreen`, `BaseAppScreen`, `FavoriteActions`, `FavoriteCardIndicator`) access `FavoritesManager` statically instead of receiving state from ViewModel or injected repositories.

#### D. Required Hilt Dependencies
- `favoritesRepository: FavoritesRepository` (or `DefaultFavoritesRepository`)
- `libraryPreferences: LibraryPreferences`

#### E. Call-Site Inventory (7 files)
| # | File Path | Line(s) | Invocation Pattern |
|---|-----------|---------|--------------------|
| 1 | `app/src/main/java/app/gamenative/PluviaApp.kt` | 14, 81 | `FavoritesManager.delegate = favoritesRepository` |
| 2 | `app/src/main/java/app/gamenative/data/FavoritesManager.kt` | 18 | Object definition |
| 3 | `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | 14 | Unused import (ViewModel already injects `FavoritesRepository`) |
| 4 | `app/src/main/java/app/gamenative/ui/screen/library/LibraryScreen.kt` | 82, 371, 372 | `FavoritesManager.favorites.collectAsStateWithLifecycle()`, `FavoritesManager.loaded...` |
| 5 | `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt` | 33, 768 | `FavoritesManager.favorites.collectAsStateWithLifecycle()` |
| 6 | `app/src/main/java/app/gamenative/ui/screen/library/components/FavoriteActions.kt` | 5, 15 | `FavoritesManager.toggle(appId)` |
| 7 | `app/src/main/java/app/gamenative/ui/screen/library/components/FavoriteCardIndicator.kt` | 25, 38, 39 | `FavoritesManager.favorites...`, `FavoritesManager.loaded...` |

#### F. Proposed Target Signature & Refactoring Design
- Convert `FavoritesManager` to an injected `@Singleton class`:
  ```kotlin
  package app.gamenative.data

  import javax.inject.Inject
  import javax.inject.Singleton

  @Singleton
  class FavoritesManager @Inject constructor(
      private val repository: FavoritesRepository,
  ) : FavoritesRepository by repository
  ```
- Alternatively, eliminate `FavoritesManager` entirely by updating the 4 UI Composable files to consume `FavoritesRepository` (injected into ViewModels or provided via Composable parameters / CompositionLocal).
- Remove `FavoritesManager.delegate = favoritesRepository` in `PluviaApp.kt`.

---

### 2.2 `FrontendSyncManager`

#### A. File Location & Current Declaration
- **File**: `app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt`
- **Current Declaration**:
  ```kotlin
  object FrontendSyncManager {
      @EntryPoint
      @InstallIn(SingletonComponent::class)
      interface FrontendSyncEntryPoint {
          fun steamAppDao(): SteamAppDao
          fun epicGameDao(): EpicGameDao
          fun gogGameDao(): GOGGameDao
          fun amazonGameDao(): AmazonGameDao
      }
      ...
  }
  ```

#### B. State, Lifecycle & Coroutine Requirements
- **State**:
  - `_isSyncing = MutableStateFlow(false)`
  - `_anyConfigured = MutableStateFlow(false)`
  - `configuredDirs: MutableMap<GameSource, String>`
  - `resyncJob: Job?`
- **Coroutine Scope**: Currently creates an unconfined `CoroutineScope(Dispatchers.IO + SupervisorJob())`.
- **Event Listener**: Subscribes to `PluviaApp.events.on<AndroidEvent.LibraryInstallStatusChanged>` during initialization.

#### C. Escape Hatches & Service Locators
- `FrontendSyncEntryPoint` with `EntryPointAccessors.fromApplication(context, FrontendSyncEntryPoint::class.java)` (lines 89–93).
- `context.preferencesEntryPoint().downloadPreferences()` (line 94).
- `appContext.getString(R.string.frontend_sync_resync_complete)` (using `Context` as service locator for strings).
- `SteamService.isAppInstalled(appId)` (static call to `SteamService`).
- Eager `init(context: Context)` called from `PluviaApp.onCreate()` (line 104).

#### D. Required Hilt Dependencies
- `@ApplicationScope scope: CoroutineScope` (or `@IoDispatcher ioDispatcher: CoroutineDispatcher`)
- `downloadPreferences: DownloadPreferences`
- `stringResolver: StringResolver` (from `app.gamenative.core.appinfo.StringResolver`)
- `steamAppDao: SteamAppDao`
- `epicGameDao: EpicGameDao`
- `gogGameDao: GOGGameDao`
- `amazonGameDao: AmazonGameDao`
- `steamManager: Provider<SteamManager>` (or `SteamManager`)

#### E. Call-Site Inventory (5 files)
| # | File Path | Line(s) | Invocation Pattern |
|---|-----------|---------|--------------------|
| 1 | `app/src/main/java/app/gamenative/PluviaApp.kt` | 23, 104 | `FrontendSyncManager.init(this)` |
| 2 | `app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt` | 42 | Object definition |
| 3 | `app/src/main/java/app/gamenative/ui/screen/settings/FrontendSyncDialog.kt` | 31, 69, 72, 164 | `FrontendSyncManager.changeDirectory(...)`, `resyncAll()`, `extensionFor(...)` |
| 4 | `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInterface.kt` | 102, 106, 109, 112, 426 | `FrontendSyncManager.isSyncing`, `resyncAll()`, `anyConfigured` |
| 5 | `app/src/test/java/app/gamenative/sync/FrontendSyncManagerTest.kt` | 12, 30–117 | Unit tests calling `extensionFor` and `deleteAllFilesWithExtension` |

#### F. Proposed Target Signature & Refactoring Design
```kotlin
package app.gamenative.sync

import app.gamenative.core.appinfo.StringResolver
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.data.GameSource
import app.gamenative.db.dao.AmazonGameDao
import app.gamenative.db.dao.EpicGameDao
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.db.dao.SteamAppDao
import app.gamenative.events.AndroidEvent
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.service.SteamManager
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.EnumMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class FrontendSyncManager @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val downloadPreferences: DownloadPreferences,
    private val stringResolver: StringResolver,
    private val steamAppDao: SteamAppDao,
    private val epicGameDao: EpicGameDao,
    private val gogGameDao: GOGGameDao,
    private val amazonGameDao: AmazonGameDao,
    private val steamManager: Provider<SteamManager>,
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _anyConfigured = MutableStateFlow(false)
    val anyConfigured: StateFlow<Boolean> = _anyConfigured.asStateFlow()

    private val configuredDirs: MutableMap<GameSource, String> =
        Collections.synchronizedMap(EnumMap(GameSource::class.java))

    private var resyncJob: Job? = null

    init {
        app.gamenative.PluviaApp.events.on<AndroidEvent.LibraryInstallStatusChanged, Unit> { event ->
            scope.launch {
                syncGame(event.appId, event.source)
            }
        }
        scope.launch {
            GameSource.entries.forEach { source ->
                val dir = downloadPreferences.getFrontendSyncDir(source)
                if (dir.isNotEmpty()) {
                    configuredDirs[source] = dir
                    syncAllInstalledGames(source, dir)
                }
            }
            _anyConfigured.value = configuredDirs.isNotEmpty()
        }
    }

    fun extensionFor(source: GameSource): String = when (source) {
        GameSource.STEAM -> ".steam"
        GameSource.EPIC -> ".epic"
        GameSource.GOG -> ".gog"
        GameSource.AMAZON -> ".amazon"
        GameSource.CUSTOM_GAME -> ".pcgame"
    }

    fun resyncAll() { ... }
    fun changeDirectory(source: GameSource, newPath: String, deleteOldFiles: Boolean) { ... }
    internal fun deleteAllFilesWithExtension(dir: String, extension: String) { ... }
}
```

---

### 2.3 `CustomGameScanner`

#### A. File Location & Current Declaration
- **File**: `app/src/main/java/app/gamenative/utils/CustomGameScanner.kt`
- **Current Declaration**:
  ```kotlin
  object CustomGameScanner {
      @Volatile
      var downloadPreferences: DownloadPreferences? = null

      @Volatile
      var libraryPreferences: LibraryPreferences? = null

      @Volatile
      var containerPreferences: ContainerPreferences? = null
      ...
  }
  ```

#### B. State, Lifecycle & Coroutine Requirements
- **State**:
  - `CustomGameCache` (in-memory map of `appId -> folderPath`)
  - Mutable preference properties populated after startup.
- **File scanning & generation**:
  - Computes `defaultRootPath`, `importRootPath`, `scanRootPaths`.
  - Scans directories, extracts icons via `ExeIconExtractor`, detects executable files via `findUniqueExeRelativeToFolder`, generates custom game numeric IDs and writes `.gamenative` files.
- **Framework Permissions**:
  - `hasStoragePermission(context, path)`: checks `Environment.isExternalStorageManager()` / `READ_EXTERNAL_STORAGE`.
  - `requestManageExternalStoragePermission(context)`: starts `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`.

#### C. Escape Hatches & Service Locators
- `@Volatile var downloadPreferences`, `libraryPreferences`, `containerPreferences` mutable fields.
- Direct static calls to `SteamService.instance`, `SteamService.findSteamAppWithInstallDir`, `SteamService.isAppLicensed`, `SteamService.getInstalledApp`, `SteamService.getMainAppDepots`.
- `DownloadService.baseExternalAppDirPath`, `DownloadService.baseDataDirPath`, `DownloadService.externalVolumePaths`.
- Instantiation of `ContainerManager(context)` inside helper functions.

#### D. Required Hilt Dependencies
- `@ApplicationContext context: Context` (for storage permission check, launching system settings intent, `ContainerManager`)
- `appStoragePaths: AppStoragePaths`
- `downloadPreferences: DownloadPreferences`
- `libraryPreferences: LibraryPreferences`
- `containerPreferences: ContainerPreferences`
- `steamManager: Provider<SteamManager>` (to query Steam app info & licenses for custom games mapped to Steam)

#### E. Call-Site Inventory (22 files)
| # | File Path | Line(s) | Invocation Pattern |
|---|-----------|---------|--------------------|
| 1 | `app/src/main/java/app/gamenative/PluviaApp.kt` | — | Initial preference binding (to be eliminated) |
| 2 | `app/src/main/java/app/gamenative/data/LibraryItem.kt` | 4, 61 | `CustomGameScanner.findIconFileForCustomGame(appId)` |
| 3 | `app/src/main/java/app/gamenative/data/gog/GogSeedCollector.kt` | 11, 74 | `CustomGameScanner.scanAsLibraryItems()` |
| 4 | `app/src/main/java/app/gamenative/service/SteamService.kt` | 196, 1440 | `CustomGameScanner.invalidateCache()` |
| 5 | `app/src/main/java/app/gamenative/ui/PluviaMain.kt` | 101, 201, 1714 | `CustomGameScanner.isGameInstalled(gameId)`, `CustomGameScanner.getLaunchExecutable(container)` |
| 6 | `app/src/main/java/app/gamenative/ui/component/dialog/ContainerConfigDialog.kt` | 96, 1015 | `CustomGameScanner.hasStoragePermission(context, path)` |
| 7 | `app/src/main/java/app/gamenative/ui/components/CustomGameFolderPicker.kt` | 16, 134 | `CustomGameScanner.requestManageExternalStoragePermission(context)` |
| 8 | `app/src/main/java/app/gamenative/ui/model/DownloadsViewModel.kt` | 28, 272 | `CustomGameScanner.scanAsLibraryItems(query = "")` |
| 9 | `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt` | 18 | Import |
| 10 | `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | 54, 609, 621, 812 | `CustomGameScanner.createLibraryItemFromFolder(...)`, `invalidateCache()`, `scanAsLibraryItems(...)` |
| 11 | `app/src/main/java/app/gamenative/ui/model/MainViewModel.kt` | 29, 542 | `CustomGameScanner.getFolderPathFromAppId(appId)` |
| 12 | `app/src/main/java/app/gamenative/ui/screen/library/LibraryScreen.kt` | 120, 512 | `CustomGameScanner.hasStoragePermission(context, path)` |
| 13 | `app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt` | 23, 69, 89, 100, 110, 225, 256, 313, 515, 518, 526 | `getFolderPathFromAppId`, `findHeroCoverInFolder`, `findCapsuleCoverInFolder`, `findIconFileForCustomGame`, `findUniqueExeRelativeToFolder`, `isManagedFolder`, `invalidateCache` |
| 14 | `app/src/main/java/app/gamenative/ui/screen/library/appscreen/SteamAppScreen.kt` | 90 | Import |
| 15 | `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryGridCard.kt` | 75, 674, 696, 701, 706, 707 | `getFolderPathFromAppId`, `findCapsuleCoverForCustomGame`, `findHeroCoverForCustomGame` |
| 16 | `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryListCard.kt` | 57, 337 | `CustomGameScanner.findIconFileForCustomGame(context, appInfo.appId)` |
| 17 | `app/src/main/java/app/gamenative/ui/screen/xserver/XAudioUtils.kt` | 12, 41 | `CustomGameScanner.getFolderPathFromAppId(appId)` |
| 18 | `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt` | 128, 4435 | `CustomGameScanner.findUniqueExeRelativeToFolder(gameFolderPath)` |
| 19 | `app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt` | 560, 562 | `CustomGameScanner.scanAsLibraryItems(...)`, `getFolderPathFromAppId(...)` |
| 20 | `app/src/main/java/app/gamenative/utils/ContainerUtils.kt` | 31, 313 | `CustomGameScanner.getLaunchExecutable(container)` |
| 21 | `app/src/main/java/app/gamenative/utils/CustomGameImporter.kt` | 15, 96, 172 | `CustomGameScanner.importRootPath`, `invalidateCache()` |
| 22 | `app/src/main/java/app/gamenative/utils/GameFeedbackUtils.kt` | 18, 142 | `CustomGameScanner.findUniqueExeRelativeToFolder(folder)` |

#### F. Proposed Target Signature & Refactoring Design
```kotlin
package app.gamenative.utils

import android.content.Context
import app.gamenative.core.storage.AppStoragePaths
import app.gamenative.preferences.ContainerPreferences
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.preferences.LibraryPreferences
import app.gamenative.service.SteamManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class CustomGameScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appStoragePaths: AppStoragePaths,
    private val downloadPreferences: DownloadPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val containerPreferences: ContainerPreferences,
    private val steamManager: Provider<SteamManager>,
) {
    val defaultRootPath: String get() { ... }
    val importRootPath: String get() { ... }
    val scanRootPaths: List<String> get() { ... }
    
    fun ensureDefaultFolderExists() { ... }
    fun findIconFileForCustomGame(appId: String): String? { ... }
    fun findCapsuleCoverForCustomGame(appId: String): String? { ... }
    fun findHeroCoverForCustomGame(appId: String): String? { ... }
    fun findUniqueExeRelativeToFolder(folder: File): String? { ... }
    fun getLaunchExecutable(container: Container): String { ... }
    fun findAllValidExeFiles(folder: File): List<String> { ... }
    fun hasStoragePermission(path: String): Boolean { ... }
    fun requestManageExternalStoragePermission(): Boolean { ... }
    fun scanAsLibraryItems(query: String = "", indexOffsetStart: Int = 0): List<LibraryItem> { ... }
    fun isManagedFolder(folderPath: String): Boolean { ... }
    fun createLibraryItemFromFolder(folderPath: String): LibraryItem? { ... }
    fun invalidateCache() { ... }
    fun findCustomGameById(gameId: Int): String? { ... }
    fun isGameInstalled(appId: Int): Boolean { ... }
    fun getFolderPathFromAppId(appId: String): String? { ... }
}
```

---

## 3. Group 4: The Storefront Services (Hidden Singletons)

### 3.1 Architectural Separation Paradigm

```
┌────────────────────────────────────────────────────────────────────────┐
│                   Android System Layer (Thin Shell)                    │
│                                                                        │
│   EpicService / GOGService / AmazonService / SteamService              │
│   - @AndroidEntryPoint class ... : Service()                          │
│   - Manages Foreground Notification (NotificationHelper)              │
│   - Handles Service Intents (ACTION_SYNC, ACTION_EXIT)                 │
│   - Keeps process alive during long active downloads/syncs             │
│   - Holds start(context) / stop() static lifecycle helpers only        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Injects & delegates to
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   Domain Business Logic Layer                          │
│                                                                        │
│   EpicManager / GOGManager / AmazonManager / SteamManager              │
│   - @Singleton class ... @Inject constructor(...)                      │
│   - Manages Authentication State, OAuth & Token refreshes              │
│   - Manages Download State (activeDownloads: Map<Int, DownloadInfo>)   │
│   - Manages Library Syncing, PICS / Manifest parsing, DB operations   │
│   - Exposes StateFlows (connectionState, syncProgress, activeDownloads)│
│   - Injected into ViewModels, Composables, Workers, Dialogs            │
└────────────────────────────────────────────────────────────────────────┘
```

---

### 3.2 `EpicService` -> `EpicManager`

#### A. File Location & Current Declarations
- **`EpicService.kt`**: `app/src/main/java/app/gamenative/service/epic/EpicService.kt` (761 lines)
- **`EpicManager.kt`**: `app/src/main/java/app/gamenative/service/epic/EpicManager.kt` (1,141 lines)

#### B. Current Coupling & Problem Analysis
1. `EpicService` currently holds:
   - `instance: EpicService?`
   - `activeDownloads = ConcurrentHashMap<Int, DownloadInfo>()`
   - `syncInProgress: Boolean`, `backgroundSyncJob: Job?`, `lastSyncTimestamp: Long`
   - Over 30 static companion methods: `isGameInstalled`, `downloadGame`, `cancelDownload`, `deleteGame`, `getEpicGameOf`, `getDLCForGame`, `getInstalledExe`, `getLaunchExecutable`, `installOverlay`, `logout`, `authenticateWithCode`.
2. `EpicManager` currently has `@Singleton class EpicManager @Inject constructor(epicGameDao, downloadPreferences? = null)` and falls back to `PreferencesEntryPoint.get(PluviaApp.instance)` for `downloadPreferences`.

#### C. Extraction Blueprint: What Moves to `EpicManager`
- All download management logic and active download state (`activeDownloads: ConcurrentHashMap<Int, DownloadInfo>`).
- All library sync state (`isSyncInProgress: StateFlow<Boolean>`, `lastSyncTimestamp`).
- Game CRUD operations (`getGameById`, `getGameByAppName`, `getDLCForTitle`, `updateGame`, `isGameInstalled`, `getInstallPath`, `deleteGame`).
- Auth delegation (`authenticateWithCode`, `hasStoredCredentials`, `getStoredCredentials`, `logout`, `getAccountId`).
- Launch parameter building & EOS overlay management (`buildLaunchParameters`, `getGameLaunchToken`, `installOverlay`, `removeOverlay`).

#### D. Proposed Target Signatures

**`EpicManager.kt`**:
```kotlin
package app.gamenative.service.epic

import android.content.Context
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.data.DownloadInfo
import app.gamenative.data.EpicCredentials
import app.gamenative.data.EpicGame
import app.gamenative.data.EpicGameToken
import app.gamenative.db.dao.EpicGameDao
import app.gamenative.preferences.AuthPreferences
import app.gamenative.preferences.ContainerPreferences
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.service.NotificationHelper
import com.winlator.container.Container
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class EpicManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val epicGameDao: EpicGameDao,
    private val authPreferences: AuthPreferences,
    private val containerPreferences: ContainerPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val epicDownloadManager: EpicDownloadManager,
    private val epicOverlayManager: EpicOverlayManager,
    private val notificationHelper: Provider<NotificationHelper>,
) {
    private val activeDownloads = ConcurrentHashMap<Int, DownloadInfo>()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun isSyncInProgress(): Boolean = _isSyncing.value
    fun hasActiveOperations(): Boolean = _isSyncing.value || hasActiveDownload()
    fun hasActiveDownload(): Boolean = activeDownloads.isNotEmpty()
    fun getCurrentlyDownloadingGame(): Int? = activeDownloads.keys.firstOrNull()
    fun getDownloadInfo(appId: Int): DownloadInfo? = activeDownloads[appId]
    fun getActiveDownloads(): Map<Int, DownloadInfo> = HashMap(activeDownloads)

    suspend fun authenticateWithCode(authorizationCode: String): Result<EpicCredentials>
    fun hasStoredCredentials(): Boolean
    suspend fun getStoredCredentials(): Result<EpicCredentials>
    suspend fun logout(): Result<Unit>
    fun getAccountId(): String?

    suspend fun startBackgroundSync(): Result<Int>
    suspend fun refreshLibrary(): Result<Int>
    suspend fun fetchManifestSizes(appId: Int): ManifestSizes

    fun downloadGame(appId: Int, dlcGameIds: List<Int>, installPath: String, containerLanguage: String): Result<DownloadInfo>
    fun cancelDownload(appId: Int): Boolean
    suspend fun cleanupDownload(appId: Int)
    suspend fun deleteGame(appId: Int): Result<Unit>

    suspend fun getGameById(appId: Int): EpicGame?
    suspend fun getGameByAppName(appName: String): EpicGame?
    suspend fun getDLCForGame(appId: Int): List<EpicGame>
    suspend fun updateGame(game: EpicGame)
    fun isGameInstalled(appId: Int): Boolean
    fun getInstallPath(appId: Int): String?
    suspend fun getInstalledExe(appId: Int): String
    suspend fun getLaunchExecutable(containerId: String): String
    fun hasPartialDownload(appId: Int): Boolean
    suspend fun getPartialDownloads(): List<Int>

    suspend fun getGameLaunchToken(namespace: String?, catalogItemId: String?, requiresOwnershipToken: Boolean): Result<EpicGameToken>
    suspend fun buildLaunchParameters(container: Container, game: EpicGame, offline: Boolean, languageCode: String): Result<List<String>>
    fun cleanupLaunchTokens(container: Container?)

    suspend fun installOverlay(container: Container, forceReinstall: Boolean, onProgress: ((Int, Int) -> Unit)?): Result<Unit>
    suspend fun removeOverlay(container: Container): Result<Unit>
}
```

**`EpicService.kt` (Thin Android Service Shell)**:
```kotlin
package app.gamenative.service.epic

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.gamenative.service.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class EpicService : Service() {

    @Inject
    lateinit var epicManager: EpicManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val ACTION_SYNC_LIBRARY = "app.gamenative.EPIC_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "app.gamenative.EPIC_MANUAL_SYNC"

        fun start(context: Context) {
            val intent = Intent(context, EpicService::class.java).apply { action = ACTION_SYNC_LIBRARY }
            context.startForegroundService(intent)
        }

        fun triggerLibrarySync(context: Context) {
            val intent = Intent(context, EpicService::class.java).apply { action = ACTION_MANUAL_SYNC }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EpicService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { ... }
    override fun onBind(intent: Intent?): IBinder? = null
}
```

#### E. Call-Site Inventory for Epic (24 files)
| # | File Path | Usage Category | Current Call Syntax |
|---|-----------|----------------|---------------------|
| 1 | `AndroidManifest.xml` | Service Manifest | `<service android:name=".service.epic.EpicService" ... />` |
| 2 | `MainActivity.kt` | Service Start | `EpicService.start(this)` |
| 3 | `gamefixes/GameFixesRegistry.kt` | Install State / Info | `EpicService.getEpicGameOf(appId)` |
| 4 | `service/epic/EpicCloudSavesManager.kt` | Game Info / Credentials | `EpicService.getEpicGameOf(appId)` |
| 5 | `service/epic/EpicGameLauncher.kt` | Token / Auth | `EpicService.getGameLaunchToken(...)` |
| 6 | `service/epic/EpicOverlayManager.kt` | Overlay State | `EpicService.getInstance()` |
| 7 | `ui/PluviaMain.kt` | Install / Exe Lookup | `EpicService.isGameInstalled(context, gameId)`, `getLaunchExecutable` |
| 8 | `ui/component/dialog/EpicGameManagerDialog.kt` | Game Details / DLC | `EpicService.getEpicGameOf(appId)`, `getDLCForGame(appId)` |
| 9 | `ui/model/DownloadsViewModel.kt` | Download & Progress | `EpicService.getActiveDownloads()`, `getDownloadInfo(appId)`, `downloadGame` |
| 10 | `ui/model/LibraryViewModel.kt` | Library Sync & Install State | `EpicService.getEpicGameOf(appId)`, `refreshLibrary()`, `isGameInstalled` |
| 11 | `ui/model/MainViewModel.kt` | Launch Executable | `EpicService.getLaunchExecutable(containerId)` |
| 12 | `ui/screen/library/LibraryScreen.kt` | Background Sync Status | `EpicService.isSyncInProgress()` |
| 13 | `ui/screen/library/appscreen/BaseAppScreen.kt` | Download / Install State | `EpicService.getDownloadInfo(appId)`, `isGameInstalled` |
| 14 | `ui/screen/library/appscreen/EpicAppScreen.kt` | Game Screen Operations | `EpicService.downloadGame(...)`, `deleteGame(...)`, `getDLCForGame(...)` |
| 15 | `ui/screen/library/components/LibraryListPane.kt` | Active Downloads | `EpicService.getActiveDownloads()` |
| 16 | `ui/screen/xserver/XAudioUtils.kt` | Install Path | `EpicService.getInstallPath(appId)` |
| 17 | `ui/screen/xserver/XServerScreen.kt` | Launch Executable | `EpicService.getInstalledExe(appId)` |
| 18 | `ui/util/PlatformAuthUiHelpers.kt` | Auth / Logout | `EpicService.logout(context)` |
| 19 | `utils/ContainerStorageManager.kt` | Storage & Path | `EpicService.getInstallPath(appId)` |
| 20 | `utils/ContainerUtils.kt` | Container & Exe Resolution | `EpicService.getLaunchExecutable(containerId)` |
| 21 | `utils/GameFeedbackUtils.kt` | Executable Name | `EpicService.getInstalledExe(appId)` |
| 22 | `utils/PlatformAuthUtils.kt` | Stored Credentials Check | `EpicService.hasStoredCredentials(context)` |
| 23 | `utils/PlatformOAuthHandlers.kt` | OAuth Callback Login | `EpicService.authenticateWithCode(context, code)` |
| 24 | `utils/launchdependencies/EpicOverlayDependency.kt` | Overlay Installation | `EpicService.installOverlay(context, container)` |

---

### 3.3 `GOGService` -> `GOGManager`

#### A. File Location & Current Declarations
- **`GOGService.kt`**: `app/src/main/java/app/gamenative/service/gog/GOGService.kt` (850 lines)
- **`GOGManager.kt`**: `app/src/main/java/app/gamenative/service/gog/GOGManager.kt` (1,289 lines)

#### B. Current Coupling & Problem Analysis
1. `GOGService` holds:
   - `instance: GOGService?`
   - `activeDownloads = ConcurrentHashMap<String, DownloadInfo>()`
   - `syncInProgress: Boolean`, `backgroundSyncJob: Job?`, `lastSyncTimestamp: Long`
   - Static companion methods for `authenticateWithCode`, `logout`, `downloadGame`, `cancelDownload`, `deleteGame`, `isGameInstalled`, `getGOGGameOf`, `getDLCForGame`, `getInstalledExe`, `getLaunchExecutable`, `syncCloudSaves`.
2. `GOGManager` is already `@Singleton class GOGManager @Inject constructor(gogGameDao, @ApplicationContext context)`, but its responsibilities are fragmented between `GOGService.Companion` and `GOGManager`.

#### C. Extraction Blueprint: What Moves to `GOGManager`
- All download tracking (`activeDownloads: ConcurrentHashMap<String, DownloadInfo>`).
- Sync state (`isSyncInProgress: StateFlow<Boolean>`, `lastSyncTimestamp`).
- Complete game management API (`getGameById`, `getDLCForGame`, `deleteGame`, `isGameInstalled`, `getInstallPath`, `updateInstallPath`, `getInstalledExe`, `getLaunchExecutable`, `getGameSize`).
- Auth delegation to `GOGAuthManager`.
- Cloud saves delegation to `GOGCloudSavesManager`.

#### D. Proposed Target Signatures

**`GOGManager.kt`**:
```kotlin
package app.gamenative.service.gog

import android.content.Context
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.data.DownloadInfo
import app.gamenative.data.GOGCredentials
import app.gamenative.data.GOGGame
import app.gamenative.db.dao.GOGGameDao
import app.gamenative.preferences.AuthPreferences
import app.gamenative.preferences.ContainerPreferences
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.service.NotificationHelper
import app.gamenative.service.gog.api.GOGApiClient
import com.winlator.container.Container
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class GOGManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val gogGameDao: GOGGameDao,
    private val authPreferences: AuthPreferences,
    private val containerPreferences: ContainerPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val gogApiClient: GOGApiClient,
    private val gogDownloadManager: GOGDownloadManager,
    private val notificationHelper: Provider<NotificationHelper>,
) {
    private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun isSyncInProgress(): Boolean = _isSyncing.value
    fun hasActiveOperations(): Boolean = _isSyncing.value || hasActiveDownload()
    fun hasActiveDownload(): Boolean = activeDownloads.isNotEmpty()
    fun getCurrentlyDownloadingGame(): String? = activeDownloads.keys.firstOrNull()
    fun getDownloadInfo(gameId: String): DownloadInfo? = activeDownloads[gameId]
    fun getActiveDownloads(): Map<String, DownloadInfo> = HashMap(activeDownloads)

    suspend fun authenticateWithCode(authorizationCode: String): Result<GOGCredentials>
    fun hasStoredCredentials(): Boolean
    suspend fun getStoredCredentials(): Result<GOGCredentials>
    suspend fun validateCredentials(): Result<Boolean>
    fun clearStoredCredentials(): Boolean
    suspend fun logout(): Result<Unit>

    suspend fun syncLibrary(): Result<Int>
    suspend fun refreshLibrary(): Result<Int>
    suspend fun getGameSize(gameId: String): GameSizeInfo?

    fun downloadGame(gameId: String, installPath: String, selectedLanguage: String, selectedDlcIds: List<String>): Result<DownloadInfo>
    fun cancelDownload(gameId: String): Boolean
    suspend fun cleanupDownload(gameId: String)
    suspend fun deleteGame(gameId: String): Result<Unit>

    suspend fun getGOGGameOf(gameId: String): GOGGame?
    suspend fun getDLCForGame(gameId: String): List<GOGGame>
    suspend fun updateGame(game: GOGGame)
    fun isGameInstalled(gameId: String): Boolean
    fun getInstallPath(gameId: String): String?
    suspend fun getInstalledExe(gameId: String): String
    suspend fun getLaunchExecutable(containerId: String): String
    fun hasPartialDownload(gameId: String): Boolean
    suspend fun getPartialDownloads(): List<String>
}
```

**`GOGService.kt` (Thin Android Service Shell)**:
```kotlin
package app.gamenative.service.gog

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.gamenative.service.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GOGService : Service() {

    @Inject
    lateinit var gogManager: GOGManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val ACTION_SYNC_LIBRARY = "app.gamenative.GOG_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "app.gamenative.GOG_MANUAL_SYNC"

        fun start(context: Context) {
            val intent = Intent(context, GOGService::class.java).apply { action = ACTION_SYNC_LIBRARY }
            context.startForegroundService(intent)
        }

        fun triggerLibrarySync(context: Context) {
            val intent = Intent(context, GOGService::class.java).apply { action = ACTION_MANUAL_SYNC }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GOGService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { ... }
    override fun onBind(intent: Intent?): IBinder? = null
}
```

#### E. Call-Site Inventory for GOG (27 files)
| # | File Path | Usage Category | Current Call Syntax |
|---|-----------|----------------|---------------------|
| 1 | `AndroidManifest.xml` | Manifest | `<service android:name=".service.gog.GOGService" ... />` |
| 2 | `MainActivity.kt` | Service Start | `GOGService.start(this)` |
| 3 | `gamefixes/GameFixesRegistry.kt` | Game Info | `GOGService.getGOGGameOf(appId)` |
| 4 | `gamefixes/types/GOGDependencyFix.kt` | Dependency Fix | `GOGService.getInstallPath(appId)` |
| 5 | `ui/PluviaMain.kt` | Install / Exe Lookup | `GOGService.isGameInstalled(gameId)`, `getLaunchExecutable` |
| 6 | `ui/model/DownloadsViewModel.kt` | Downloads Tracking | `GOGService.getActiveDownloads()`, `getDownloadInfo`, `downloadGame` |
| 7 | `ui/model/LibraryViewModel.kt` | Library Sync / Game Info | `GOGService.getGOGGameOf(appId)`, `refreshLibrary()`, `isGameInstalled` |
| 8 | `ui/model/MainViewModel.kt` | Launch Executable | `GOGService.getLaunchExecutable(containerId)` |
| 9 | `ui/screen/library/LibraryScreen.kt` | Background Sync Status | `GOGService.isSyncInProgress()` |
| 10 | `ui/screen/library/appscreen/BaseAppScreen.kt` | Download / Install State | `GOGService.getDownloadInfo(appId)`, `isGameInstalled` |
| 11 | `ui/screen/library/appscreen/GOGAppScreen.kt` | Game Screen Operations | `GOGService.downloadGame(...)`, `deleteGame(...)`, `getDLCForGame(...)` |
| 12 | `ui/screen/library/components/LibraryListPane.kt` | Active Downloads | `GOGService.getActiveDownloads()` |
| 13 | `ui/screen/xserver/XAudioUtils.kt` | Install Path | `GOGService.getInstallPath(appId)` |
| 14 | `ui/screen/xserver/XServerScreen.kt` | Launch Executable | `GOGService.getInstalledExe(appId)` |
| 15 | `ui/util/PlatformAuthUiHelpers.kt` | Auth / Logout | `GOGService.logout(context)` |
| 16 | `utils/ContainerStorageManager.kt` | Storage & Path | `GOGService.getInstallPath(appId)` |
| 17 | `utils/ContainerUtils.kt` | Container & Exe Resolution | `GOGService.getLaunchExecutable(containerId)` |
| 18 | `utils/GameFeedbackUtils.kt` | Executable Name | `GOGService.getInstalledExe(appId)` |
| 19 | `utils/PlatformAuthUtils.kt` | Stored Credentials Check | `GOGService.hasStoredCredentials(context)` |
| 20 | `utils/PlatformOAuthHandlers.kt` | OAuth Callback Login | `GOGService.authenticateWithCode(context, code)` |
| 21 | `utils/launchdependencies/GogScriptInterpreterDependency.kt` | Launcher Script | `GOGService.getInstallPath(appId)` |
| 22 | `utils/preInstallSteps/GogScriptInterpreterStep.kt` | Pre-install Script | `GOGService.getInstallPath(appId)` |
| 23 | `test/java/.../GameFixesRegistryTest.kt` | Test | Mock / Invocation |
| 24 | `test/java/.../GOGDependencyFixTest.kt` | Test | Mock / Invocation |
| 25 | `test/java/.../GogScriptInterpreterDependencyTest.kt` | Test | Mock / Invocation |
| 26 | `test/java/.../GogScriptInterpreterStepTest.kt` | Test | Mock / Invocation |

---

### 3.4 `AmazonService` -> `AmazonManager`

#### A. File Location & Current Declarations
- **`AmazonService.kt`**: `app/src/main/java/app/gamenative/service/amazon/AmazonService.kt` (925 lines)
- **`AmazonManager.kt`**: `app/src/main/java/app/gamenative/service/amazon/AmazonManager.kt` (95 lines)

#### B. Current Coupling & Problem Analysis
1. `AmazonService` holds:
   - `AmazonDaoEntryPoint` (`@EntryPoint @InstallIn(SingletonComponent::class) interface AmazonDaoEntryPoint`) with `EntryPointAccessors.fromApplication(...)` (lines 48–51, 162).
   - `activeDownloads = ConcurrentHashMap<String, DownloadInfo>()`
   - `activeDownloadPaths = ConcurrentHashMap<String, String>()`
   - `syncInProgress: Boolean`, `backgroundSyncJob: Job?`, `lastSyncTimestamp: Long`
   - Static companion methods for `authenticateWithCode`, `logout`, `downloadGame`, `cancelDownload`, `deleteGame`, `isGameInstalled`, `getInstallPath`, `getInstalledExe`, `getLaunchExecutable`, `refreshLibrary`.
2. `AmazonManager` currently only has 95 lines with bare DAO calls.

#### C. Extraction Blueprint: What Moves to `AmazonManager`
- Move all download tracking (`activeDownloads`, `activeDownloadPaths`).
- Move sync state (`isSyncInProgress: StateFlow<Boolean>`, `lastSyncTimestamp`).
- Move install detection (`isGameInstalled`, `hasPartialDownload`, `getPartialDownloads`, `deleteGame`, `getInstallPath`, `updateInstallPath`).
- Move launch resolution (`getInstalledExe`, `getLaunchExecutable`).
- Move auth delegation (`authenticateWithCode`, `hasStoredCredentials`, `logout`).
- Eliminate `AmazonDaoEntryPoint` completely.

#### D. Proposed Target Signatures

**`AmazonManager.kt`**:
```kotlin
package app.gamenative.service.amazon

import android.content.Context
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.data.AmazonCredentials
import app.gamenative.data.AmazonGame
import app.gamenative.data.DownloadInfo
import app.gamenative.db.dao.AmazonGameDao
import app.gamenative.preferences.AuthPreferences
import app.gamenative.preferences.ContainerPreferences
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.service.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AmazonManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val amazonGameDao: AmazonGameDao,
    private val authPreferences: AuthPreferences,
    private val containerPreferences: ContainerPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val amazonDownloadManager: AmazonDownloadManager,
    private val notificationHelper: Provider<NotificationHelper>,
) {
    private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()
    private val activeDownloadPaths = ConcurrentHashMap<String, String>()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun isSyncInProgress(): Boolean = _isSyncing.value
    fun hasActiveOperations(): Boolean = _isSyncing.value || hasActiveDownload()
    fun hasActiveDownload(): Boolean = activeDownloads.isNotEmpty()
    fun getCurrentlyDownloadingGame(): String? = activeDownloads.keys.firstOrNull()
    fun getDownloadInfo(productId: String): DownloadInfo? = activeDownloads[productId]
    fun getActiveDownloads(): Map<String, DownloadInfo> = HashMap(activeDownloads)

    suspend fun authenticateWithCode(authCode: String): Result<AmazonCredentials>
    fun hasStoredCredentials(): Boolean
    suspend fun logout(): Result<Unit>

    suspend fun refreshLibrary()
    suspend fun getGameById(productId: String): AmazonGame?
    suspend fun getGameByAppId(appId: Int): AmazonGame?
    suspend fun getAllGames(): List<AmazonGame>
    suspend fun getNonInstalledGames(): List<AmazonGame>

    fun downloadGame(productId: String, installPath: String): Result<DownloadInfo>
    fun cancelDownload(productId: String): Boolean
    suspend fun cleanupDownload(productId: String)
    suspend fun deleteGame(appId: Int): Result<Unit>

    fun isGameInstalled(appId: Int): Boolean
    fun getInstallPath(appId: Int): String?
    fun updateInstallPath(appId: Int, path: String)
    suspend fun getInstalledExe(appId: Int): String
    suspend fun getLaunchExecutable(appId: Int): String
    fun hasPartialDownload(appId: Int): Boolean
    suspend fun getPartialDownloads(): List<Int>
}
```

**`AmazonService.kt` (Thin Android Service Shell)**:
```kotlin
package app.gamenative.service.amazon

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import app.gamenative.service.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AmazonService : Service() {

    @Inject
    lateinit var amazonManager: AmazonManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    companion object {
        private const val ACTION_SYNC_LIBRARY = "app.gamenative.AMAZON_SYNC_LIBRARY"
        private const val ACTION_MANUAL_SYNC = "app.gamenative.AMAZON_MANUAL_SYNC"

        fun start(context: Context) {
            val intent = Intent(context, AmazonService::class.java).apply { action = ACTION_SYNC_LIBRARY }
            context.startForegroundService(intent)
        }

        fun triggerLibrarySync(context: Context) {
            val intent = Intent(context, AmazonService::class.java).apply { action = ACTION_MANUAL_SYNC }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AmazonService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { ... }
    override fun onBind(intent: Intent?): IBinder? = null
}
```

#### E. Call-Site Inventory for Amazon (19 files)
| # | File Path | Usage Category | Current Call Syntax |
|---|-----------|----------------|---------------------|
| 1 | `AndroidManifest.xml` | Manifest | `<service android:name=".service.amazon.AmazonService" ... />` |
| 2 | `MainActivity.kt` | Service Start | `AmazonService.start(this)` |
| 3 | `ui/PluviaMain.kt` | Install / Exe Lookup | `AmazonService.isGameInstalled(gameId)`, `getLaunchExecutable` |
| 4 | `ui/model/DownloadsViewModel.kt` | Downloads Tracking | `AmazonService.getActiveDownloads()`, `getDownloadInfo`, `downloadGame` |
| 5 | `ui/model/LibraryViewModel.kt` | Library Sync / Game Info | `AmazonService.getGameByAppId(appId)`, `refreshLibrary()`, `isGameInstalled` |
| 6 | `ui/model/MainViewModel.kt` | Launch Executable | `AmazonService.getLaunchExecutable(appId)` |
| 7 | `ui/screen/library/LibraryScreen.kt` | Background Sync Status | `AmazonService.isSyncInProgress()` |
| 8 | `ui/screen/library/appscreen/AmazonAppScreen.kt` | Game Screen Operations | `AmazonService.downloadGame(...)`, `deleteGame(...)`, `getGameByAppId` |
| 9 | `ui/screen/library/appscreen/BaseAppScreen.kt` | Download / Install State | `AmazonService.getDownloadInfo(appId)`, `isGameInstalled` |
| 10 | `ui/screen/library/components/LibraryListPane.kt` | Active Downloads | `AmazonService.getActiveDownloads()` |
| 11 | `ui/screen/xserver/XAudioUtils.kt` | Install Path | `AmazonService.getInstallPath(appId)` |
| 12 | `ui/screen/xserver/XServerScreen.kt` | Launch Executable | `AmazonService.getInstalledExe(appId)` |
| 13 | `ui/util/PlatformAuthUiHelpers.kt` | Auth / Logout | `AmazonService.logout(context)` |
| 14 | `utils/ContainerStorageManager.kt` | Storage & Path | `AmazonService.getInstallPath(appId)` |
| 15 | `utils/ContainerUtils.kt` | Container & Exe Resolution | `AmazonService.getLaunchExecutable(appId)` |
| 16 | `utils/GameFeedbackUtils.kt` | Executable Name | `AmazonService.getInstalledExe(appId)` |
| 17 | `utils/PlatformAuthUtils.kt` | Stored Credentials Check | `AmazonService.hasStoredCredentials(context)` |
| 18 | `utils/PlatformOAuthHandlers.kt` | OAuth Callback Login | `AmazonService.authenticateWithCode(context, code)` |

---

### 3.5 `SteamService` -> `SteamManager`

#### A. File Location & Current Declaration
- **`SteamService.kt`**: `app/src/main/java/app/gamenative/service/SteamService.kt` (4,750 lines)
- **Current Declaration**: `@AndroidEntryPoint class SteamService : Service(), IChallengeUrlChanged`

#### B. Current Coupling & Problem Analysis
1. `SteamService` is a 4,750-line monolith that mixes:
   - Android `Service` lifecycle (`onCreate`, `onStartCommand`, `onDestroy`, `NotificationHelper`, `NetworkCallback`, `startForeground`)
   - JavaSteam client connection (`SteamClient`, `CallbackManager`, `SteamUser`, `SteamApps`, `SteamFriends`, `SteamCloud`, `SteamUserStats`, `FamilyGroups`)
   - PICS change cache synchronization (`appPicsChannel`, `packagePicsChannel`, Room DB transactions)
   - Depot download orchestration (`DepotDownloader`, `downloadJobs: ConcurrentHashMap<Int, DownloadInfo>`, `depotKeyPrep`, `workshopPausedApps`)
   - AutoCloud save synchronization (`SteamAutoCloud`, save conflict resolution)
   - Static companion object of over **3,100 lines (lines 346–3513)** containing ~100 static methods accessed throughout the application.
2. Downstream callers (~78 files) call `SteamService.<method>` statically, preventing modular testing and creating brittle hidden global state.

#### C. Extraction Blueprint: What Moves to `SteamManager`
- `SteamManager` becomes the central `@Singleton` managing:
  - Connection & Session: `connect()`, `disconnect()`, `logOn()`, `logOff()`, `loginResult: StateFlow<LoginResult>`, `connectionState: StateFlow<Boolean>`, `localPersona: StateFlow<SteamFriend>`.
  - Downloads: `downloadJobs: ConcurrentHashMap<Int, DownloadInfo>`, `downloadApp`, `cancelDownload`, `removeDownloadJob`, `getActiveDownloads`, `getAppDownloadInfo`, `hasPartialDownload`, `workshopPausedApps`.
  - Game / Depot Info: `isAppInstalled`, `getInstalledApp`, `getAllInstalledApps`, `getInstalledDepotsOf`, `getInstalledDlcDepotsOf`, `getAppDirPath`, `getInstalledExe`, `getLaunchExecutable`, `getMainAppDepots`, `getDownloadableDepots`, `eligibleDepots`, `resolveDownloadableDepots`, `getAppDlc`.
  - Licenses & Packaging: `isAppLicensed`, `getPkgInfoOf`, `getSharedPkg`, `getLicensedDepotIds`, `buildLicensedDepotMap`, `getAppInfoOf`, `getDownloadingAppInfoOf`, `getDownloadableDlcAppsOf`, `getOwnedDlcAppIdsOf`.
  - ImageFs & Runtime Asset Downloads: `isImageFsInstalled`, `isImageFsInstallable`, `isSteamInstallable`, `downloadImageFs`, `downloadFile`.
  - Cloud Sync: `syncSaves`, `clearPlayingConflict`, `hasPendingSync`.
  - JavaSteam event handlers & callbacks.

#### D. Proposed Target Signatures

**`SteamManager.kt`**:
```kotlin
package app.gamenative.service

import android.content.Context
import app.gamenative.core.appinfo.StringResolver
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.core.coroutines.IoDispatcher
import app.gamenative.core.storage.AppStoragePaths
import app.gamenative.data.*
import app.gamenative.db.PluviaDatabase
import app.gamenative.db.dao.*
import app.gamenative.enums.LoginResult
import app.gamenative.preferences.*
import com.winlator.container.Container
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SteamManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val authPreferences: AuthPreferences,
    private val containerPreferences: ContainerPreferences,
    private val downloadPreferences: DownloadPreferences,
    private val generalPreferences: GeneralPreferences,
    private val libraryPreferences: LibraryPreferences,
    private val appStoragePaths: AppStoragePaths,
    private val stringResolver: StringResolver,
    private val db: PluviaDatabase,
    private val licenseDao: SteamLicenseDao,
    private val appDao: SteamAppDao,
    private val changeNumbersDao: ChangeNumbersDao,
    private val appInfoDao: AppInfoDao,
    private val fileChangeListsDao: FileChangeListsDao,
    private val steamFileHashCacheDao: SteamFileHashCacheDao,
    private val cachedLicenseDao: CachedLicenseDao,
    private val encryptedAppTicketDao: EncryptedAppTicketDao,
    private val downloadingAppInfoDao: DownloadingAppInfoDao,
    private val steamUnlockedBranchDao: SteamUnlockedBranchDao,
    private val steamCollectionRepository: SteamCollectionRepository,
    private val notificationHelper: Provider<NotificationHelper>,
) {
    val localPersona: StateFlow<SteamFriend>
    val loginResultFlow: StateFlow<LoginResult>
    val isConnectedFlow: StateFlow<Boolean>

    val workshopPausedApps: MutableSet<Int>

    fun isAppInstalled(appId: Int): Boolean
    fun getInstalledApp(appId: Int): AppInfo?
    fun getAllInstalledApps(): List<AppInfo>?
    fun getAppDirPath(gameId: Int): String
    fun getInstalledExe(appId: Int): String
    fun getLaunchExecutable(appId: String, container: Container): String
    fun hasPartialDownload(appId: Int): Boolean

    fun getAppDownloadInfo(appId: Int): DownloadInfo?
    fun getActiveDownloads(): Map<Int, DownloadInfo>
    fun downloadApp(appId: Int): DownloadInfo?
    fun downloadApp(appId: Int, dlcAppIds: List<Int>, branch: String = "public", isUpdateOrVerify: Boolean = false): DownloadInfo?
    fun cancelDownload(appId: Int): Boolean
    fun removeDownloadJob(appId: Int)

    fun isAppLicensed(packageId: Int): Boolean
    fun getPkgInfoOf(appId: Int): SteamLicense?
    fun getSharedPkg(): SteamLicense?
    fun getLicensedDepotIds(appId: Int): Set<Int>?
    fun buildLicensedDepotMap(apps: List<SteamApp>): Map<Int, Set<Int>>
    fun getAppInfoOf(appId: Int): SteamApp?
    fun getDownloadingAppInfoOf(appId: Int): DownloadingAppInfo?
    fun getDownloadableDlcAppsOf(appId: Int): List<SteamApp>?
    fun getOwnedDlcAppIdsOf(appId: Int): IntArray
    fun getMainAppDepots(appId: Int, containerLanguage: String): Map<Int, DepotInfo>
    fun getDownloadableDepots(appId: Int, preferredLanguage: String = "english"): Map<Int, DepotInfo>

    fun isImageFsInstalled(): Boolean
    fun isImageFsInstallable(variant: String): Boolean
    fun isSteamInstallable(): Boolean
    fun downloadImageFs(containerVariant: String, progress: (Float) -> Unit): Boolean

    suspend fun syncSaves(appId: Int): PostSyncInfo?
    fun clearPlayingConflict()
}
```

**`SteamService.kt` (Thin Android Service Shell)**:
```kotlin
package app.gamenative.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SteamService : Service() {

    @Inject
    lateinit var steamManager: SteamManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    companion object {
        const val INVALID_APP_ID: Int = Int.MAX_VALUE
        const val INVALID_PKG_ID: Int = Int.MAX_VALUE

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SteamService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SteamService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize foreground notification & register connectivity manager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Run foreground notification & keep service alive
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

#### E. Call-Site Inventory for Steam (78+ files)
| # | Domain / Module | File Paths | Usage Categories |
|---|-----------------|------------|------------------|
| 1 | **Application Lifecycle & Core** | `MainActivity.kt`, `PluviaApp.kt`, `AndroidManifest.xml` | Service start/stop, startup checks |
| 2 | **Data Models & DAOs** | `data/DepotInfo.kt`, `data/SteamApp.kt`, `data/gog/GogSeedCollector.kt`, `db/dao/SteamAppDao.kt`, `enums/PathType.kt` | Constants (`INVALID_APP_ID`, `INVALID_PKG_ID`), path resolvers, app queries |
| 3 | **Services & Helpers** | `service/AchievementWatcher.kt`, `service/DownloadService.kt`, `service/NotificationActionReceiver.kt`, `service/NotificationHelper.kt`, `service/SteamAutoCloud.kt`, `service/SteamUnifiedFriends.kt`, `service/SteamWishlistService.kt`, `sync/FrontendSyncManager.kt` | App installation checks, cloud sync, friend avatars, active download queries |
| 4 | **UI - ViewModels** | `ui/model/DownloadsViewModel.kt`, `ui/model/GogRecommendationsViewModel.kt`, `ui/model/LibraryViewModel.kt`, `ui/model/MainViewModel.kt`, `ui/model/UserLoginViewModel.kt` | Full Steam game management, login session, active downloads, library scanning |
| 5 | **UI - Screens & Cards** | `ui/PluviaMain.kt`, `ui/screen/library/FeaturedCtaButton.kt`, `ui/screen/library/LibraryAppScreen.kt`, `ui/screen/library/LibraryScreen.kt`, `ui/screen/library/appscreen/BaseAppScreen.kt`, `ui/screen/library/appscreen/SteamAppScreen.kt`, `ui/screen/library/components/LibraryListCard.kt`, `ui/screen/library/components/SystemMenu.kt` | Game cards, hero art, download button state, installed game checks, Steam collections |
| 6 | **UI - Dialogs** | `ui/component/dialog/ContainerConfigDialog.kt`, `ui/component/dialog/GameManagerDialog.kt`, `ui/component/dialog/ProfileDialog.kt`, `ui/component/dialog/WorkshopManagerDialog.kt`, `ui/component/topbar/AccountButton.kt`, `ui/screen/settings/ContentsManagerDialog.kt`, `ui/screen/settings/DriverManagerDialog.kt`, `ui/screen/settings/SettingsGroupDebug.kt`, `ui/screen/settings/WineProtonManagerDialog.kt` | Account status, cloud conflict dialog, game depots manager, workshop downloads, ImageFs manager |
| 7 | **XR & XServer** | `ui/screen/xr/ImmersiveXrActivity.kt`, `ui/screen/xserver/XAudioUtils.kt`, `ui/screen/xserver/XServerScreen.kt` | Launch executable resolution, container drive mounting, game process monitoring |
| 8 | **Utilities** | `ui/util/SteamSaveTransfer.kt`, `utils/ContainerStorageManager.kt`, `utils/ContainerUtils.kt`, `utils/CustomGameScanner.kt`, `utils/GameFeedbackUtils.kt`, `utils/KeyValueUtils.kt`, `utils/SteamControllerVdfUtils.kt`, `utils/SteamTokenHelper.kt`, `utils/SteamTokenLogin.kt`, `utils/SteamUtils.kt`, `utils/launchdependencies/BionicSteamAssetsDependency.kt`, `workshop/WorkshopManager.kt` | VDF parsing, launch args, token login, steam assets |
| 9 | **Unit Tests** | `test/.../SteamAppDaoTest.kt`, `test/.../DepotFilteringTest.kt`, `test/.../SdCardDetectionTest.kt`, `test/.../SteamAutoCloudTest.kt` | Direct testing of depot filters, path resolution, auto cloud sync |

---

## 4. Dagger Hilt Dependency Injection Architecture & Modules

```kotlin
package app.gamenative.di

import app.gamenative.data.DefaultFavoritesRepository
import app.gamenative.data.FavoritesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(
        impl: DefaultFavoritesRepository,
    ): FavoritesRepository
}
```

All other managers (`FrontendSyncManager`, `CustomGameScanner`, `SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager`) are declared directly as `@Singleton class ... @Inject constructor(...)` and do not require extra `@Provides` boilerplate.

---

## 5. Step-by-Step Implementation & Migration Plan

### Step 1: Group 3 User Library Managers Migration
1. **`FavoritesManager`**:
   - Refactor `FavoritesManager.kt` into an `@Singleton class FavoritesManager @Inject constructor(repository: FavoritesRepository) : FavoritesRepository by repository`.
   - Update call sites in `LibraryScreen.kt`, `BaseAppScreen.kt`, `FavoriteActions.kt`, `FavoriteCardIndicator.kt` to inject or consume `FavoritesRepository`.
   - Remove static assignment in `PluviaApp.kt`.
2. **`FrontendSyncManager`**:
   - Convert `FrontendSyncManager.kt` to `@Singleton class FrontendSyncManager @Inject constructor(...)`.
   - Remove `FrontendSyncEntryPoint` and `EntryPointAccessors.fromApplication`.
   - Update `SettingsGroupInterface.kt` and `FrontendSyncDialog.kt` to receive `FrontendSyncManager` via ViewModel or DI.
   - Update `FrontendSyncManagerTest.kt` to instantiate `FrontendSyncManager` directly.
3. **`CustomGameScanner`**:
   - Convert `CustomGameScanner.kt` to `@Singleton class CustomGameScanner @Inject constructor(...)`.
   - Remove `@Volatile` preference fields and static instance setters.
   - Inject `CustomGameScanner` into `LibraryViewModel`, `DownloadsViewModel`, `MainViewModel`, `ContainerStorageManager`, `CustomGameImporter`, etc.

### Step 2: Group 4 Storefront Managers Extraction
1. **`EpicManager` & `EpicService`**:
   - Move active download tracking and companion business logic from `EpicService` into `EpicManager`.
   - Convert `EpicService` into a thin foreground `Service` shell.
   - Update all 24 caller files to inject `EpicManager` instead of calling `EpicService` statically.
2. **`GOGManager` & `GOGService`**:
   - Move active download tracking and companion business logic from `GOGService` into `GOGManager`.
   - Convert `GOGService` into a thin foreground `Service` shell.
   - Update all 27 caller files to inject `GOGManager` instead of calling `GOGService` statically.
3. **`AmazonManager` & `AmazonService`**:
   - Move active download tracking and companion business logic from `AmazonService` into `AmazonManager`.
   - Delete `AmazonDaoEntryPoint` and all `EntryPointAccessors`.
   - Convert `AmazonService` into a thin foreground `Service` shell.
   - Update all 19 caller files to inject `AmazonManager` instead of calling `AmazonService` statically.
4. **`SteamManager` & `SteamService`**:
   - Extract `SteamManager` with all JavaSteam, PICS, Depot download, AutoCloud, and AppInfo queries.
   - Convert `SteamService` into a thin foreground `Service` shell with `start(context)` / `stop(context)`.
   - Update ViewModels (`LibraryViewModel`, `DownloadsViewModel`, `MainViewModel`, `UserLoginViewModel`), UI screens, dialogs, and helper services to receive `SteamManager` via `@Inject`.
   - Update `SteamAutoCloudTest` to inject mock `SteamManager` without reflection hacks.

### Step 3: Verification
- Execute compilation:
  ```bash
  ./gradlew compileModernDebugKotlin
  ```
- Execute unit tests:
  ```bash
  ./gradlew :app:testModernDebugUnitTest
  ```
- Verify zero occurrences of `PreferencesEntryPoint` or `EntryPointAccessors.fromApplication` remain in Groups 3 & 4.
