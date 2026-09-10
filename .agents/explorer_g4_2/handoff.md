# Handoff Report: EpicService & GOGService Storefront Refactoring

## 1. Observation
1. **EpicService & GOGService Definition**:
   - `EpicService.kt` (`app/src/main/java/app/gamenative/service/epic/EpicService.kt`): 761 lines, `@AndroidEntryPoint class EpicService : Service()`.
     - Lines 37–616: Companion object holding `private var instance: EpicService? = null`, mutable static sync state (`syncInProgress`, `backgroundSyncJob`, `lastSyncTimestamp`, `hasPerformedInitialSync`), and static facades for auth, download management, and game operations.
     - Lines 634: `private val activeDownloads = ConcurrentHashMap<Int, DownloadInfo>()` in service instance.
     - Lines 411–520: `downloadGame(...)` runs download job on `instance.scope`, tracks in `activeDownloads` and `notificationHelper`, coordinates post-install save sync and snackbars.
     - Lines 228–280: `deleteGame(context, appId)` deletes files, chunk cache, uninstalls DB record, deletes container `EPIC_${game.id}`, emits `LibraryInstallStatusChanged`.
   - `GOGService.kt` (`app/src/main/java/app/gamenative/service/gog/GOGService.kt`): 850 lines, `@AndroidEntryPoint class GOGService : Service()`.
     - Lines 45–710: Companion object holding `private var instance: GOGService? = null`, mutable static sync state (`syncInProgress`, `backgroundSyncJob`, `lastSyncTimestamp`, `hasPerformedInitialSync`).
     - Lines 725: `private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()` in service instance.
     - Lines 353–448: `downloadGame(...)` runs download job on `instance.scope`, tracks in `activeDownloads` and `notificationHelper`.
     - Lines 471–709: `syncCloudSaves(...)` and `detectCloudSaveConflict(...)` implemented directly inside Service companion object.
2. **Existing Managers**:
   - `EpicManager.kt` (`app/src/main/java/app/gamenative/service/epic/EpicManager.kt`): 1141 lines, `@Singleton class EpicManager @Inject constructor(private val epicGameDao: EpicGameDao, private val downloadPreferences: DownloadPreferences? = null)`.
     - Lines 44–46: Uses `PreferencesEntryPoint.get(PluviaApp.instance).downloadPreferences()` as fallback.
   - `GOGManager.kt` (`app/src/main/java/app/gamenative/service/gog/GOGManager.kt`): 1289 lines, `@Singleton class GOGManager @Inject constructor(private val gogGameDao: GOGGameDao, @ApplicationContext private val context: Context)`.
     - Contains `deleteGame`, `getInstalledExe`, `getGogWineStartCommand`, `getScriptInterpreterPartsForLaunch`, `getSaveDirectoryPath`.
3. **Circular Dependency Finding**:
   - `EpicDownloadManager.kt` line 68: constructor has `private val epicManager: EpicManager`, but grep search across the file shows `epicManager` is **never referenced** in the implementation.
   - `GOGDownloadManager.kt` line 83: constructor has `private val gogManager: GOGManager`, but grep search across the file shows `gogManager` is **never referenced** in the implementation.
   - `EpicOverlayManager.kt` lines 25–28: injects `EpicManager` and `EpicDownloadManager`.
4. **Escape Hatches & Preferences**:
   - `PreferencesEntryPoint` is used in:
     - `EpicManager.kt`: line 45 (`PreferencesEntryPoint.get(PluviaApp.instance).downloadPreferences()`)
     - `EpicService.kt`: line 201 (`PreferencesEntryPoint.get(context).downloadPreferences().externalStoragePath`)
     - `EpicConstants.kt`: lines 125, 138
     - `EpicDownloadManager.kt`: line 515
     - `GOGService.kt`: line 222 (`PreferencesEntryPoint.get(PluviaApp.instance).downloadPreferences().externalStoragePath`)
     - `GOGConstants.kt`: lines 131, 143
   - `GOGConstants.kt` line 116: uses static mutable `appContext: Context?`.
5. **Call Sites**:
   - 25 files reference `EpicService` (133 occurrences).
   - 27 files reference `GOGService` (including 4 unit test files: `GogScriptInterpreterStepTest.kt`, `GogScriptInterpreterDependencyTest.kt`, `GOGDependencyFixTest.kt`, `GameFixesRegistryTest.kt`).
   - Callers include `DownloadsViewModel`, `LibraryViewModel`, `MainViewModel`, `EpicAppScreen`, `GOGAppScreen`, `BaseAppScreen`, `ContainerUtils`, `ContainerStorageManager`, `EpicOverlayDependency`, `GogScriptInterpreterDependency`, `GogScriptInterpreterStep`, `MainActivity`.

## 2. Logic Chain
1. From Observation 1, `EpicService` and `GOGService` expose static methods and access instance state (`activeDownloads`) through `instance`. This creates a hidden singleton anti-pattern where callers fail or return null whenever the Android Service is not actively running.
2. From Observation 1 and 2, `EpicManager` and `GOGManager` already exist as `@Singleton class` components containing Room DAO interactions and API integrations, but currently lack download tracking, download initiation, and sync state tracking.
3. Moving `activeDownloads`, `downloadGame`, `cancelDownload`, `cleanupDownload`, `deleteGame`, `syncCloudSaves`, and sync state from the Services into `EpicManager` and `GOGManager` consolidates all storefront domain logic into injectable `@Singleton` classes independent of the Android UI/Service lifecycle.
4. From Observation 3, `EpicDownloadManager` and `GOGDownloadManager` declare unused constructor parameters for `EpicManager` and `GOGManager`. Removing these unused parameters cleanly eliminates any risk of circular dependency when injecting the download managers into `EpicManager` and `GOGManager`.
5. From Observation 4, `PreferencesEntryPoint` in `EpicManager` and `GOGManager` can be completely eliminated by injecting `DownloadPreferences` directly into their `@Inject constructor`s.
6. From Observation 5, all 50+ call sites across ViewModels, UI screens, launch steps, and tests can transition from static `Service.method()` calls to instance `manager.method()` calls via `@Inject` or `StorefrontEntryPoint` / `AppUtilsEntryPoint`.

## 3. Caveats
- Android `Service` foreground notifications require tracking download progress (`NotificationHelper.trackDownload`) and sync status (`NotificationHelper.showSyncing` / `showIdle`). The managers must expose callbacks (`onSyncStatusChanged`, `onDownloadTracked`) or Kotlin SharedFlows so the thin Services can update notifications without the managers knowing about Android `Service` internals.
- Composable trees (e.g. `BaseAppScreen`, `EpicAppScreen`, `GOGAppScreen`) cannot use constructor injection; they must receive `EpicManager` / `GOGManager` from ViewModels or via an `@EntryPoint` (`AppUtilsEntryPoint` or `StorefrontEntryPoint`).
- Unit tests (`GogScriptInterpreterStepTest`, `GogScriptInterpreterDependencyTest`) currently mock `GOGService.Companion` and `GOGService.getInstance()`. These tests must be updated to inject or mock `GOGManager` directly.

## 4. Conclusion
`EpicService` and `GOGService` should be refactored into thin Android foreground service shells. All domain state (`activeDownloads`, sync flags/timestamps), operations (`downloadGame`, `cancelDownload`, `deleteGame`, `syncCloudSaves`), and API orchestrations should reside in `@Singleton class EpicManager @Inject constructor(...)` and `@Singleton class GOGManager @Inject constructor(...)`. All `PreferencesEntryPoint` usages in the managers will be eliminated via direct `DownloadPreferences` injection. Unused parameters in `EpicDownloadManager` and `GOGDownloadManager` should be deleted to prevent circularity.

## 5. Verification Method
1. **Source Inspection**:
   - Verify `EpicManager` and `GOGManager` are `@Singleton class` with `@Inject constructor`.
   - Verify `PreferencesEntryPoint` is completely absent from `EpicManager.kt` and `GOGManager.kt`.
   - Verify `activeDownloads` is managed inside `EpicManager` and `GOGManager`.
   - Verify `EpicService.kt` and `GOGService.kt` contain 0 static business methods in their companion objects.
2. **Build Verification**:
   - Run `./gradlew compileModernDebugKotlin` to ensure clean compilation across all refactored call sites.
3. **Unit Test Verification**:
   - Run `./gradlew :app:testModernDebugUnitTest` to verify test suite passing.
4. **Invalidation Conditions**:
   - If circular dependencies arise between `EpicManager` <-> `EpicDownloadManager`, use `dagger.Lazy` or `javax.inject.Provider`.
