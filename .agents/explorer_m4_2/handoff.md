# Handoff Report: Investigation of WorkshopManager.kt and All Usages

**Author**: explorer_m4_2  
**Target Milestone**: M4 (Group 5: Advanced Subsystems)  
**Date**: 2026-09-05  

---

## 1. Observation

### 1.1 Current Declaration and Internal State of `WorkshopManager.kt`
- **File**: `app/src/main/java/app/gamenative/workshop/WorkshopManager.kt`
- **Current Declaration** (line 74):
  ```kotlin
  object WorkshopManager {
  ```
- **Internal States & Properties** (lines 76–109):
  - `private const val TAG = "WorkshopManager"` (line 76)
  - `private val downloadPreferences: DownloadPreferences? get() = SteamService.currentManager?.downloadPreferences` (lines 77–78)
  - `private val containerPreferences: ContainerPreferences? get() = SteamService.currentManager?.containerPreferences` (lines 79–80)
  - App ID constants:
    - `private const val RAIN_WORLD_APP_ID = 312520` (line 81)
    - `private const val ONI_APP_ID = 457140` (line 84)
    - `private const val YOMI_HUSTLE_APP_ID = 2212330` (line 85)
  - Path constants:
    - `private const val RAIN_WORLD_MODS_PATH = "RainWorld_Data/StreamingAssets/mods"` (line 82)
    - `private const val RAIN_WORLD_ENABLED_MODS_PATH = "RainWorld_Data/StreamingAssets/enabledMods.txt"` (line 83)
  - RPC pagination constants:
    - `private const val MAX_PAGES = 50` (line 86)
    - `private const val PAGE_SIZE = 100` (line 87)
  - State flag:
    - `private var workshopTypesPatched = false` (line 88)
  - Mod compatibility definitions:
    - `private const val SLAY_THE_SPIRE_HEADLESS_LAUNCHER_JAR_BASE64 = "..."` (line 95)
    - `private val SKIP_ZIP_EXTRACTION_APP_IDS = setOf(...)` (lines 96–101)
    - `private val YOMI_HUSTLE_FLAT_FILE_EXTENSIONS = setOf("zip")` (line 103)
    - `private val ZIP_PAYLOAD_EXTENSIONS_BY_APP_ID = mapOf(...)` (lines 105–108)
  - Content validation markers:
    - `private const val COMPLETE_MARKER = ".workshop_complete"` (line 299)
    - `private const val MIN_SIZE_VALIDATION_BYTES = 8L * 1024L * 1024L` (line 300)
    - `private const val SUSPICIOUS_SIZE_RATIO_DIVISOR = 20L` (line 301)
    - `private const val WORKSHOP_UPDATE_THRESHOLD = 100L * 1024 * 1024` (line 4208)

### 1.2 Coroutine Scopes, Dispatchers, and Flows
- **Flows**:
  - Grep search for `Flow`, `StateFlow`, `SharedFlow` in `WorkshopManager.kt` returned **0 results**. `WorkshopManager` maintains no internal Flows.
- **Coroutine Scopes & Dispatchers**:
  - `startWorkshopDownload` (line 4244): Creates an ad-hoc unconfined `CoroutineScope(Dispatchers.IO).launch { ... }` which is returned via `DownloadInfo.setDownloadJob(job)`.
  - `downloadItems` (line 904): Declared as `suspend fun downloadItems(...) = coroutineScope { ... }` with concurrent download pipelines controlled by `Semaphore(concurrentLimit)`.
  - `decompressLzmaFiles` (lines 825, 845): Uses `withContext(Dispatchers.IO)` and `coroutineScope { ... }` with a bounded semaphore `Semaphore(concurrency)`.
  - `fetchSubscribedFilesViaRPC` (line 188): Uses `withContext(Dispatchers.IO)`.
  - `downloadPreviewImage` (line 1220): Uses `withContext(Dispatchers.IO)`.
  - `downloadViaHttp` (line 1253): Uses `withContext(Dispatchers.IO)`.
  - `NonCancellable` (line 4370): Uses `withContext(NonCancellable) { steamClient/dao update }` in cleanup block.

### 1.3 Service Locator, EntryPoint, and Context Prop-Drilling Usages
Direct investigation of `WorkshopManager.kt` identified the following escape hatches:
1. **`AppUtilsEntryPoint` Escape Hatch** (line 4104):
   ```kotlin
   var bionicSteam = AppUtilsEntryPoint.get(context).steamManager().containerPreferences.launchBionicSteam
   ```
   Uses `AppUtilsEntryPoint` to fetch `SteamManager` just to read `containerPreferences.launchBionicSteam`.
2. **Preference getters via static `SteamService.currentManager`** (lines 77–80):
   ```kotlin
   private val downloadPreferences: DownloadPreferences?
       get() = SteamService.currentManager?.downloadPreferences
   private val containerPreferences: ContainerPreferences?
       get() = SteamService.currentManager?.containerPreferences
   ```
3. **Static `SteamService` usages** (19 distinct occurrences across the file):
   - `File(SteamService.getAppDirPath(appId))` (lines 1423, 4095)
   - `SteamService.getAppInfoOf(appId)?.name ?: ""` (lines 1424, 4096)
   - `SteamService.instance?.steamClient ?: return null` (line 4224)
   - `SteamService.userSteamId ?: return null` (line 4225)
   - `SteamService.getAppDownloadInfo(appId)?.cancel(...)` (line 4229)
   - `SteamService.setAppDownloadInfo(appId, info)` (line 4240)
   - `SteamService.workshopPausedApps.remove(appId)` (line 4241)
   - `SteamService.notifyDownloadStarted(appId)` (line 4242)
   - `SteamService.instance?.appDao?.setWorkshopDownloadPending(appId, true)` (line 4247)
   - `SteamService.getLicensesFromDb()` (line 4308)
   - `SteamService.getAppDownloadInfo(appId) === info` (lines 4349, 4362)
   - `SteamService.workshopPausedApps.add(appId)` (line 4351)
   - `SteamService.removeDownloadJob(appId)` (line 4363)
   - `SteamService.workshopPausedApps.contains(appId)` (line 4368)
   - `SteamService.instance?.appDao?.setWorkshopDownloadPending(appId, false)` (line 4371)
   - `SteamService.instance?.steamClient` (line 4400)
   - `SteamService.userSteamId` (line 4401)
4. **`context: Context` Prop-Drilling** in function signatures:
   - `private fun getContainerWinePrefix(context: Context, appId: Int): String` (line 1373)
   - `fun deleteWorkshopMods(context: Context, containerId: String, ...)` (line 1391)
   - `fun cleanupDisabledWorkshopArtifactsForApp(context: Context, appId: Int)` (line 1420)
   - `suspend fun configureLocalWorkshopContentForEnabledIds(context: Context, appId: Int, enabledIds: Set<Long>): Boolean` (line 4018)
   - `fun configureSymlinksForApp(context: Context, appId: Int, ...)` (line 4089)
   - `fun startWorkshopDownload(appId: Int, enabledIds: Set<Long>, context: Context): DownloadInfo?` (line 4222)
   - `suspend fun checkForWorkshopUpdates(appId: Int, enabledIds: Set<Long>, context: Context): WorkshopUpdateCheck?` (line 4398)

### 1.4 All Usages of `WorkshopManager` Across Codebase
Grep search (`grep_search`) across the entire repository revealed exact call sites in **4 production files** and **1 test file**:

#### Call Site 1: `app/src/main/java/app/gamenative/ui/PluviaMain.kt`
- **Line 110**: `import app.gamenative.workshop.WorkshopManager`
- **Line 2039**: `val enabledWorkshopIds = WorkshopManager.parseEnabledIds(appDao?.getEnabledWorkshopItemIds(gameId))`
- **Line 2056**: `WorkshopManager.configureLocalWorkshopContentForEnabledIds(context = context, appId = gameId, enabledIds = enabledWorkshopIds)`
- **Line 2079**: `val updateCheck = WorkshopManager.checkForWorkshopUpdates(appId = gameId, enabledIds = enabledWorkshopIds, context = context)`
- **Line 2087**: `val thresholdBytes = WorkshopManager.getUpdateThresholdBytes()`
- **Line 2120**: `val spaceError = WorkshopManager.checkDiskSpace(updateCheck.workshopContentDir, totalBytes * 2)`
- **Line 2134**: `val successCount = WorkshopManager.downloadItems(items = updateCheck.itemsToSync, ...)`
- **Line 2174**: `WorkshopManager.runPostProcessing(...)`
- **Line 2180**: `WorkshopManager.configureSymlinksForApp(context, gameId, updateCheck.allItems, updateCheck.winePrefix, updateCheck.workshopContentDir)`
- **Line 2196**: `WorkshopManager.cleanupDisabledWorkshopArtifactsForApp(context, gameId)`

#### Call Site 2: `app/src/main/java/app/gamenative/ui/screen/library/appscreen/SteamAppScreen.kt`
- **Line 64**: `import app.gamenative.workshop.WorkshopManager`
- **Line 504**: `val enabledIds = WorkshopManager.parseEnabledIds(appDao?.getEnabledWorkshopItemIds(gameId))` (inside `resumeWorkshopDownload`)
- **Line 508**: `WorkshopManager.startWorkshopDownload(gameId, enabledIds, context)` (inside `resumeWorkshopDownload`)
- **Line 1398**: `currentEnabledIds = WorkshopManager.parseEnabledIds(idsString)` (inside `LaunchedEffect(gameId)`)
- **Line 1428**: `WorkshopManager.startWorkshopDownload(gameId, enabledIds, context)` (inside `WorkshopManagerDialog`'s `onSave` callback)
- **Line 1433**: `WorkshopManager.deleteWorkshopMods(context = context, containerId = gameId.toString(), gameRootDir = gameRootDir, gameName = gameName)`

#### Call Site 3: `app/src/main/java/app/gamenative/service/SteamManagerDownloads.kt`
- **Line 26**: `import app.gamenative.workshop.WorkshopManager`
- **Line 981**: `val enabledIds = WorkshopManager.parseEnabledIds(appDao.getEnabledWorkshopItemIds(appId))` (inside `SteamManager.resumePendingWorkshopDownloads()`)
- **Line 989**: `WorkshopManager.startWorkshopDownload(appId, enabledIds, context)` (inside `SteamManager.resumePendingWorkshopDownloads()`)

#### Call Site 4: `app/src/main/java/app/gamenative/ui/component/dialog/WorkshopManagerDialog.kt`
- **Line 81**: `import app.gamenative.workshop.WorkshopManager`
- **Line 130**: `val result = withContext(Dispatchers.IO) { WorkshopManager.getSubscribedItems(gameId, steamClient, steamId) }` (inside `LaunchedEffect(visible)`)

#### Call Site 5: `app/src/test/java/app/gamenative/workshop/WorkshopManagerTest.kt`
- **43 call sites** invoking static methods on `WorkshopManager`:
  - `WorkshopManager.parseEnabledIds(...)` (lines 101, 106, 111, 118, 126, 134)
  - `WorkshopManager.cleanupUnsubscribedItems(...)` (lines 147, 161, 173, 185)
  - `WorkshopManager.getItemsNeedingSync(...)` (lines 195, 206, 217, 227, 236, 250, 261, 271)
  - `WorkshopManager.updateMarkerTimestamps(...)` (lines 284, 296, 308, 319, 332)
  - `WorkshopManager.configureModSymlinks(...)` (lines 351, 368, 383, 398, 425, 450, 473)
  - `WorkshopManager.getWorkshopContentDir(...)` (line 487)
  - `WorkshopManager.fixItemFileNames(...)` (lines 503, 515, 526, 539, 554, 568, 582)
  - `WorkshopManager.fixFileExtensions(...)` (line 595)
  - `WorkshopManager.runPostProcessing(...)` (lines 609, 624)
  - `WorkshopManager.extractZipMods(...)` (line 644)

---

## 2. Logic Chain

### 2.1 Refactoring `WorkshopManager` to `@Singleton class ... @Inject constructor`
1. **Conversion from `object` to `class`**:
   - `WorkshopManager` must be declared as:
     ```kotlin
     @Singleton
     class WorkshopManager @Inject constructor(
         @ApplicationContext private val context: Context,
         private val downloadPreferences: DownloadPreferences,
         private val containerPreferences: ContainerPreferences,
         private val appStoragePaths: AppStoragePaths,
         private val steamManagerProvider: Provider<SteamManager>,
     )
     ```
2. **Eliminating Preferences & EntryPoint Escape Hatches**:
   - Constructor-injected `downloadPreferences` replaces `SteamService.currentManager?.downloadPreferences`. Lines 911 and 1196 can read `downloadPreferences.downloadSpeed` directly without null-checks.
   - Constructor-injected `containerPreferences` replaces `SteamService.currentManager?.containerPreferences` (lines 79–80) and eliminates `AppUtilsEntryPoint.get(context).steamManager().containerPreferences.launchBionicSteam` at line 4104, replacing it with `containerPreferences.launchBionicSteam`.
   - All `AppUtilsEntryPoint` references inside `WorkshopManager.kt` are removed.
3. **Decoupling File Storage with `AppStoragePaths`**:
   - In `getContainerWinePrefix`, `ImageFs.find(context)` can utilize `appStoragePaths.imageFsDir.toFile()` for resolving `home/${ImageFs.USER}-STEAM_$appId/.wine`.
4. **Eliminating Static `SteamService` via `steamManagerProvider`**:
   - All 19 calls previously routed to `SteamService` (`getAppDirPath`, `getAppInfoOf`, `steamClient`, `userSteamId`, `getAppDownloadInfo`, `setAppDownloadInfo`, `workshopPausedApps`, `notifyDownloadStarted`, `removeDownloadJob`, `appDao`, `getLicensesFromDb`) are replaced with instance calls on `steamManagerProvider.get()`.
5. **Eradicating `context: Context` from Function Signatures**:
   - Because `@ApplicationContext private val context: Context` is injected into the class, `context` can be removed from public function signatures:
     - `deleteWorkshopMods(containerId: String, gameRootDir: File?, gameName: String)`
     - `cleanupDisabledWorkshopArtifactsForApp(appId: Int)`
     - `configureLocalWorkshopContentForEnabledIds(appId: Int, enabledIds: Set<Long>): Boolean`
     - `configureSymlinksForApp(appId: Int, items: List<WorkshopItem>, winePrefix: String, workshopContentDir: File)`
     - `startWorkshopDownload(appId: Int, enabledIds: Set<Long>): DownloadInfo?`
     - `checkForWorkshopUpdates(appId: Int, enabledIds: Set<Long>): WorkshopUpdateCheck?`

### 2.2 Why `Provider<SteamManager>` is Required
1. **Circular Dependency Breakdown**:
   - `SteamManager` (via `SteamManagerDownloads.kt`: `resumePendingWorkshopDownloads()`) calls `WorkshopManager.startWorkshopDownload(...)`.
   - `WorkshopManager` requires `SteamManager` for `steamClient`, `userSteamId`, `getLicensesFromDb()`, `appDao`, download job tracking, and pausing.
   - If `SteamManager` injects `WorkshopManager` and `WorkshopManager` directly injects `SteamManager`, Dagger Hilt's dependency graph has a cycle: `SteamManager -> WorkshopManager -> SteamManager`.
   - Injecting `Provider<SteamManager>` into `WorkshopManager` (and `Provider<WorkshopManager>` into `SteamManager`) breaks the cycle. Dagger instantiates `WorkshopManager` without immediately instantiating `SteamManager`.
2. **Deferred Runtime Initialization**:
   - `WorkshopManager` does not need `SteamManager` during startup or static configuration—only when active download or update operations are executed. `steamManagerProvider.get()` defers resolution until call time.
3. **Strict Singleton Identity**:
   - Calling `steamManagerProvider.get()` guarantees retrieval of the exact `@Singleton` instance managed by Hilt without relying on mutable globals like `SteamService.instance`.

### 2.3 Wiring Up Downstream Callers
1. **`AppUtilsEntryPoint.kt`**:
   - Must add accessor:
     ```kotlin
     fun workshopManager(): app.gamenative.workshop.WorkshopManager
     ```
2. **`PluviaMain.kt` (Composable Tree)**:
   - Resolves `workshopManager`:
     ```kotlin
     val workshopManager = context.appUtilsEntryPoint().workshopManager()
     ```
   - Replaces all static `WorkshopManager.<method>` calls with `workshopManager.<method>`.
3. **`SteamAppScreen.kt` (Composable Tree / `BaseAppScreen`)**:
   - Resolves `workshopManager` via `context.appUtilsEntryPoint().workshopManager()`.
   - In `resumeWorkshopDownload`, `LaunchedEffect`, and dialog callbacks (`onSave`), uses `workshopManager.startWorkshopDownload(...)` and `workshopManager.deleteWorkshopMods(...)`.
4. **`SteamManager.kt` & `SteamManagerDownloads.kt`**:
   - In `SteamManager.kt`, inject:
     ```kotlin
     internal val workshopManagerProvider: Provider<WorkshopManager>,
     ```
   - In `SteamManagerDownloads.kt`:
     ```kotlin
     internal suspend fun SteamManager.resumePendingWorkshopDownloads() {
         ...
         val workshopManager = workshopManagerProvider.get()
         val enabledIds = workshopManager.parseEnabledIds(appDao.getEnabledWorkshopItemIds(appId))
         ...
         workshopManager.startWorkshopDownload(appId, enabledIds)
     }
     ```
5. **`WorkshopManagerDialog.kt` (Composable Dialog)**:
   - In `LaunchedEffect(visible)`:
     ```kotlin
     val workshopManager = remember(context) { context.appUtilsEntryPoint().workshopManager() }
     val result = withContext(Dispatchers.IO) {
         workshopManager.getSubscribedItems(gameId, steamClient, steamId)
     }
     ```
6. **`WorkshopManagerTest.kt` (Unit Tests)**:
   - In `@Before fun setUp()`:
     ```kotlin
     private lateinit var workshopManager: WorkshopManager

     @Before
     fun setUp() {
         tempDir = createTempDirectory("workshop_test").toFile()
         workshopContentDir = File(tempDir, "content")
         workshopContentDir.mkdirs()
         workshopManager = WorkshopManager(
             context = mockk(relaxed = true),
             downloadPreferences = mockk(relaxed = true),
             containerPreferences = mockk(relaxed = true),
             appStoragePaths = mockk(relaxed = true),
             steamManagerProvider = mockk(relaxed = true),
         )
     }
     ```
   - Update all 43 static calls from `WorkshopManager.<method>` to `workshopManager.<method>`.
7. **`AppUtilsEntryPointTest.kt` (Unit Tests)**:
   - Mock `val workshopManager = mockk<WorkshopManager>(relaxed = true)`
   - Add `override fun workshopManager(): WorkshopManager = workshopManager`
   - Add `assertNotNull(mockEntryPoint.workshopManager())`

---

## 3. Caveats
1. **Utility Methods Placement**:
   - Methods like `parseEnabledIds` and `getWorkshopContentDir` do not depend on any injected instance state. They can either remain instance methods on `WorkshopManager` (called via `workshopManager.*`) or be placed in a `companion object`. Making them instance methods on `WorkshopManager` aligns with Hilt encapsulation and makes mocking in tests straightforward.
2. **`context: Context` in Public Method Signatures**:
   - When eradicating `context: Context` from `startWorkshopDownload`, `checkForWorkshopUpdates`, and other signatures, downstream callers that pass `context` must be updated concurrently to prevent compile breaks.
3. **`SteamManagerDownloads.kt` Scope**:
   - `SteamManagerDownloads.kt` contains extension functions on `SteamManager`. If `SteamManager` injects `workshopManagerProvider: Provider<WorkshopManager>`, it has `internal` visibility and is directly accessible to `SteamManagerDownloads.kt` because both reside in `app.gamenative.service` in the same module.

---

## 4. Conclusion
1. `WorkshopManager.kt` is currently a monolithic singleton `object` with 4,503 lines of code. It contains no reactive Flows, but performs extensive background coroutine downloading and file processing.
2. It relies on Dagger Hilt escape hatches (`AppUtilsEntryPoint.get(context)`), static preference getters (`SteamService.currentManager`), static `SteamService` singleton calls (19 sites), and prop-drilled `context: Context` parameters (7 methods).
3. It can be converted cleanly into:
   ```kotlin
   @Singleton
   class WorkshopManager @Inject constructor(
       @ApplicationContext private val context: Context,
       private val downloadPreferences: DownloadPreferences,
       private val containerPreferences: ContainerPreferences,
       private val appStoragePaths: AppStoragePaths,
       private val steamManagerProvider: Provider<SteamManager>,
   )
   ```
4. `Provider<SteamManager>` breaks the circular dependency between `SteamManager` (which calls `WorkshopManager` to resume downloads) and `WorkshopManager` (which calls `SteamManager` to access client connections, licenses, and downloads).
5. Downstream callers are limited and well-bounded:
   - 1 EntryPoint interface: `AppUtilsEntryPoint.kt`
   - 3 UI Composable files: `PluviaMain.kt`, `SteamAppScreen.kt`, `WorkshopManagerDialog.kt`
   - 2 Service files: `SteamManager.kt`, `SteamManagerDownloads.kt`
   - 2 Unit test files: `WorkshopManagerTest.kt`, `AppUtilsEntryPointTest.kt`

---

## 5. Verification Method

### 5.1 Verification Commands
Once the implementation is applied:
1. **Compile Kotlin debug sources**:
   ```pwsh
   ./gradlew compileModernDebugKotlin
   ```
   *Expected result*: BUILD SUCCESSFUL with 0 errors.
2. **Run Unit Tests**:
   ```pwsh
   ./gradlew :app:testModernDebugUnitTest --tests "app.gamenative.workshop.WorkshopManagerTest"
   ./gradlew :app:testModernDebugUnitTest --tests "app.gamenative.di.AppUtilsEntryPointTest"
   ./gradlew :app:testModernDebugUnitTest
   ```
   *Expected result*: All unit tests PASS.

### 5.2 Forensic Checks
1. Ensure `object WorkshopManager` is replaced with `@Singleton class WorkshopManager @Inject constructor(...)`.
2. Ensure `app/src/main/java/app/gamenative/workshop/WorkshopManager.kt` contains **0** occurrences of `AppUtilsEntryPoint` and `PreferencesEntryPoint`.
3. Ensure `WorkshopManager.kt` contains **0** occurrences of `SteamService`.
4. Ensure `AppUtilsEntryPoint.kt` exposes `fun workshopManager(): WorkshopManager`.
