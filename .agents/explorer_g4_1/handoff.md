# Handoff Report: Group 4 Storefront Refactoring (SteamService & AmazonService)

**Agent**: `explorer_g4_1`  
**Working Directory**: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_1`  
**Handoff Type**: Hard (Investigation Complete)  
**Target Milestone**: Group 4 Storefront Services Extraction (`M3`)

---

## 1. Observation

1. **SteamService Scope & Structure**:
   - Path: `app/src/main/java/app/gamenative/service/SteamService.kt`
   - Total lines: 4,750 lines.
   - Companion object spans lines 346–3512 (3,166 lines), holding static singleton state:
     - Line 367: `internal var instance: SteamService? = null`
     - Line 396: `private val downloadJobs = ConcurrentHashMap<Int, DownloadInfo>()`
     - Line 399: `val workshopPausedApps: MutableSet<Int> = ConcurrentHashMap.newKeySet()`
     - Line 485: `private val syncInProgressApps = ConcurrentHashMap<Int, AtomicBoolean>()`
     - Line 516: `@Volatile var keepAlive: Boolean = false`
     - Line 523–529: `isConnected`, `isRunning`, `isLoggingOut`, `isWaitingForQRAuth`
   - Class instance spans lines 200–345 and 3514–4750 (1,584 lines), holding JavaSteam client instances and handlers:
     - Line 275: `internal var steamClient: SteamClient? = null`
     - Line 274: `internal var callbackManager: CallbackManager? = null`
     - Line 278–285: `_unifiedFriends`, `_steamUser`, `_steamApps`, `_steamFriends`, `_steamCloud`, `_steamUserStats`, `_steamFamilyGroups`
     - Line 292–306: `appPicsChannel`, `packagePicsChannel`
     - Line 333–344: `_isPlayingBlocked`, `_localPersona`
   - Escape Hatches & Service Locators in `SteamService.kt`:
     - Line 204: `val languageCode = PreferencesEntryPoint.get(newBase).generalPreferences().appLanguage`
     - Line 339: `val auth = PreferencesEntryPoint.get(this).authPreferences()`
     - Line 2791: `val authPreferences = PreferencesEntryPoint.get(instance!!).authPreferences()`
     - Line 3028: `PreferencesEntryPoint.get(ctx).authPreferences().clearSteamSession()`
     - Line 3086: `PreferencesEntryPoint.get(it).authPreferences().username`
     - Line 3314: `PreferencesEntryPoint.get(context).authPreferences().steamUserAccountId`
     - Line 313: `File(applicationContext.filesDir, "pending_achievement_sync.txt")`
     - Line 1671: `File(instance!!.filesDir, fileName)`
     - Line 1790: `instance?.assets?.open(...)`
     - Lines 387, 435: `svc.getString(...)`

2. **AmazonService Scope & Structure**:
   - Path: `app/src/main/java/app/gamenative/service/amazon/AmazonService.kt` (925 lines).
   - Holds static state in companion object:
     - Line 74: `private var instance: AmazonService? = null`
     - Lines 65–68: `activeDownloads = ConcurrentHashMap<String, DownloadInfo>()`, `activeDownloadPaths = ConcurrentHashMap<String, String>()`
     - Lines 76–80: `lastSyncTimestamp`, `hasPerformedInitialSync`, `syncInProgress`, `backgroundSyncJob`
   - Escape Hatches in `AmazonService.kt`:
     - Lines 47–51: `@EntryPoint @InstallIn(SingletonComponent::class) interface AmazonDaoEntryPoint { fun amazonGameDao(): AmazonGameDao }`
     - Lines 158–160: `EntryPointAccessors.fromApplication(context.applicationContext, AmazonDaoEntryPoint::class.java).amazonGameDao()`
     - Line 379: `PreferencesEntryPoint.get(context).downloadPreferences().externalStoragePath`

3. **Existing AmazonManager**:
   - Path: `app/src/main/java/app/gamenative/service/amazon/AmazonManager.kt`
   - Declared as:
     ```kotlin
     @Singleton
     class AmazonManager @Inject constructor(
         private val amazonGameDao: AmazonGameDao,
         @ApplicationContext private val context: Context,
     )
     ```
   - Only 95 lines, containing 9 simple DAO/API bridge functions. Higher-level domain methods (install checks, path resolution, downloads, cancellation, uninstallation, verification) remain stuck in `AmazonService`.

4. **Call Site Census**:
   - `SteamService` is referenced in **67 files** across ViewModels (`DownloadsViewModel`, `LibraryViewModel`, `UserLoginViewModel`, `MainViewModel`, `GogRecommendationsViewModel`), UI screens (`SteamAppScreen`, `BaseAppScreen`, `LibraryAppScreen`), utilities (`SteamUtils`, `ContainerUtils`, `ContainerStorageManager`), launchers (`ImageFsInstaller`, `GlibcProgramLauncherComponent`), and subsystems (`WorkshopManager`).
   - `AmazonService` is referenced in **18 files** across ViewModels (`DownloadsViewModel`, `LibraryViewModel`, `MainViewModel`), UI screens (`AmazonAppScreen`, `BaseAppScreen`, `LibraryScreen`, `LibraryListPane`), auth handlers (`PlatformOAuthHandlers`, `PlatformAuthUiHelpers`, `PlatformAuthUtils`), and utilities (`ContainerUtils`, `ContainerStorageManager`, `GameFeedbackUtils`, `XServerScreen`, `XAudioUtils`).

---

## 2. Logic Chain

1. **Hidden Android Service Singletons Cause Lifecycle & Concurrency Bugs**:
   - Observations 1 and 2 reveal that `SteamService` and `AmazonService` store domain state (e.g., active download maps, sync state, and API connections) in static companion fields relying on nullable `instance` pointers.
   - When callers invoke static queries while the service is stopped, `instance` is null, causing silent failures or requiring escape hatches like `AmazonDaoEntryPoint` (`EntryPointAccessors.fromApplication(...)`) (Observation 2).

2. **Domain Logic vs Service Lifecycle Separation**:
   - In both services, Android framework responsibilities are minimal: handling `startForeground`, ongoing notifications (`NotificationHelper`), `onStartCommand` intent routing, `onTimeout`, and network connectivity listeners.
   - All remaining logic (Observation 1: JavaSteam client, DepotDownloader, AutoCloud, Goldberg sync, PICS metadata, and Observation 2: Amazon manifest parsing, file verification, uninstallation, download orchestration) represents pure domain business logic.

3. **Singleton Extraction Eliminates Escape Hatches & Service Locators**:
   - Extracting `SteamManager` as a `@Singleton class` allows direct constructor injection of DAOs, domain preferences (`AuthPreferences`, `ContainerPreferences`, `DownloadPreferences`, `GeneralPreferences`, `LibraryPreferences`), `AppStoragePaths`, and `StringResolver`. This directly eradicates all 6 `PreferencesEntryPoint` usages and Context filesystem locators (Observation 1).
   - Expanding `@Singleton class AmazonManager` to absorb `activeDownloads`, sync state, install detection, and verification allows direct injection of `AmazonGameDao`, `AmazonDownloadManager`, and `DownloadPreferences`. This directly eradicates `AmazonDaoEntryPoint`, `EntryPointAccessors.fromApplication`, and `PreferencesEntryPoint` (Observations 2 & 3).

4. **Circularity Mitigation**:
   - `SteamManager` and `WorkshopManager` share mutual dependencies: `SteamManager` invokes workshop resumption, while `WorkshopManager` invokes download info and app metadata queries. Injecting `Provider<WorkshopManager>` in `SteamManager` breaks the circular dependency safely.

---

## 3. Caveats

1. **Java Call Sites for SteamService**:
   - `ImageFsInstaller.java`, `GlibcProgramLauncherComponent.java`, and `BionicProgramLauncherComponent.java` call `SteamService.Companion.getAppDirPath(...)`. When refactoring, either `SteamManager` must be injected into those components, or temporary `@JvmStatic` delegation on `SteamService` can be used during transition before migrating Java components.
2. **EpicService & GOGService Scope**:
   - This investigation specifically analyzed `SteamService` and `AmazonService` per prompt instructions. `EpicService` and `GOGService` share analogous architectures and are covered by peer explorers.

---

## 4. Conclusion

- `SteamService` and `AmazonService` must be refactored into thin Android service shells that inject `@Singleton SteamManager` and `@Singleton AmazonManager`, respectively.
- `AmazonManager` already exists at `app/src/main/java/app/gamenative/service/amazon/AmazonManager.kt` but must be expanded to absorb all domain operations currently in `AmazonService`, eliminating `AmazonDaoEntryPoint` and `EntryPointAccessors`.
- `SteamManager` must be created at `app/src/main/java/app/gamenative/service/SteamManager.kt` as an `@Singleton` holding the JavaSteam client, DepotDownloader, PICS channels, and AutoCloud state, eliminating all `PreferencesEntryPoint` and `instance` dereferences.
- Detailed migration blueprints, constructor definitions, and caller migration tables have been documented in `report.md`.

---

## 5. Verification Method

To independently verify the findings in this report:

1. **Verify Static Singletons & Escape Hatches**:
   - Inspect `AmazonDaoEntryPoint` and `EntryPointAccessors`:
     ```pwsh
     rg -n "AmazonDaoEntryPoint|EntryPointAccessors" app/src/main/java/app/gamenative/service/amazon
     ```
   - Inspect `PreferencesEntryPoint` in `SteamService.kt`:
     ```pwsh
     rg -n "PreferencesEntryPoint" app/src/main/java/app/gamenative/service/SteamService.kt
     ```
2. **Verify Caller Counts**:
   - Enumerate all references to `AmazonService`:
     ```pwsh
     rg -l "AmazonService" app/src/main
     ```
     (Expected: 18 files)
   - Enumerate all references to `SteamService`:
     ```pwsh
     rg -l "SteamService" app/src/main
     ```
     (Expected: 67 files)
3. **Verify Existing AmazonManager Status**:
   - View `app/src/main/java/app/gamenative/service/amazon/AmazonManager.kt` to confirm current 95-line implementation.
