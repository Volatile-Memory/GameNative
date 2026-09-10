# Group 4 Storefront Services & Managers: Investigation Handoff Report

**Agent**: `explorer_g4_3`  
**Working Directory**: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_3`  
**Handoff Type**: Hard (Task Complete)

---

## 1. Observation

Direct observations from inspecting code, manifests, and DI modules:
- **Android Manifest & Services**:
  - `app/src/main/AndroidManifest.xml:135-157` defines `SteamService`, `GOGService`, `EpicService`, and `AmazonService` with `android:foregroundServiceType="dataSync"`.
  - All four services are annotated with `@AndroidEntryPoint`.
  - Services use `NotificationHelper` to manage foreground notifications (`NOTIFICATION_ID_STEAM=1`, `NOTIFICATION_ID_GOG=2`, `NOTIFICATION_ID_EPIC=3`, `NOTIFICATION_ID_AMAZON=4`).
  - All four services maintain static `instance: Service?` fields in their `companion object` and provide static facades that check `instance != null` or `getInstance()`.
- **Managers Inventory**:
  - `SteamManager`: **Does not exist** anywhere in `app/src` (0 results for `SteamManager`). `SteamService.kt` contains 4,750 lines containing all Steam connection, authentication, PICS, license, download, and cloud sync logic.
  - `EpicManager.kt:28-32`: Annotated `@Singleton class EpicManager @Inject constructor(epicGameDao: EpicGameDao, downloadPreferences: DownloadPreferences? = null)`. Contains lines 44-46 with `PreferencesEntryPoint.get(PluviaApp.instance).downloadPreferences()` escape hatch. `activeDownloads` is stored in `EpicService.kt:634`.
  - `GOGManager.kt:57-60`: Annotated `@Singleton class GOGManager @Inject constructor(gogGameDao: GOGGameDao, @ApplicationContext context: Context)`. `activeDownloads` is stored in `GOGService.kt`.
  - `AmazonManager.kt:14-18`: Only 95 lines wrapping `AmazonGameDao`. Almost all logic is in `AmazonService.kt:47-51` which declares an explicit `@EntryPoint @InstallIn(SingletonComponent::class) interface AmazonDaoEntryPoint` escape hatch using `EntryPointAccessors.fromApplication`.
- **DI Modules**:
  - `DatabaseModule.kt:25-103`: Provides Room DAOs for all storefronts (`SteamAppDao`, `SteamLicenseDao`, `AppInfoDao`, `EpicGameDao`, `GOGGameDao`, `AmazonGameDao`, `DownloadingAppInfoDao`, etc.).
  - `PreferencesModule.kt:60-105`: Binds all domain preferences (`AuthPreferences`, `ContainerPreferences`, `DownloadPreferences`, `GeneralPreferences`, `LibraryPreferences`, `InputPreferences`, `HudPreferences`).
  - `StorageModule.kt:9-19`: Binds `AppStoragePaths` (`AndroidAppStoragePaths`).
  - `SystemServicesModule.kt`: Provides `ConnectivityManager`, `NotificationManager`, `StorageManager`, etc.
  - `CoroutinesModule.kt`: Provides `@IoDispatcher`, `@DefaultDispatcher`, `@ApplicationScope CoroutineScope`.
  - `AppUtilsEntryPoint.kt`: Provides entry point for UI trees (currently has Groups 1, 2, 3; needs Group 4 managers added).
- **Downstream Call Sites**:
  - Over 100 files invoke static methods on `SteamService`, `EpicService`, `GOGService`, and `AmazonService`.
  - ViewModels (`DownloadsViewModel`, `MainViewModel`, `UserLoginViewModel`, `LibraryViewModel`) call `SteamService.getActiveDownloads()`, `EpicService.cancelDownload()`, etc.
  - UI screens (`BaseAppScreen`, `SteamAppScreen`, `EpicAppScreen`, `GOGAppScreen`, `AmazonAppScreen`) invoke static service methods for download info, installation verification, and manifest queries.
  - `CustomGameScanner.kt:601-615` checks `SteamService.instance != null` and queries `SteamService.instance?.appInfoDao`.
  - `WorkshopManager.kt:78-80, 4224-4401` directly accesses `SteamService.instance?.steamClient` and `SteamService.userSteamId`.

---

## 2. Logic Chain

1. **Premise**: In Android architecture, an `android.app.Service` is an OS-managed application component subject to destruction by Android's memory killer (OOM), task swipe-away, or foreground service timeouts.
2. **Observation**: `SteamService`, `EpicService`, `GOGService`, and `AmazonService` store active download collections in service instances and expose static companion methods relying on `instance != null`.
3. **Inference**: Calling static methods like `EpicService.getActiveDownloads()` or `SteamService.isAppInstalled(appId)` when the service is stopped causes silent empty returns, failed operations, or null dereferences. To circumvent this, developers introduced escape hatches like `AmazonDaoEntryPoint` (`EntryPointAccessors.fromApplication`).
4. **Resolution**: All in-memory state (active downloads map, sync jobs, login/connection state) and business operations must be extracted into injected `@Singleton` classes (`SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager`).
5. **Separation**: The Android Service classes must be reduced to thin shells (~100–150 lines) that only manage the `NotificationHelper` foreground notification and forward Android intents to the Managers.
6. **DI Graph Safety**: Interdependent classes (`CustomGameScanner` and `WorkshopManager` requiring `SteamManager`; `EpicManager` requiring `EpicDownloadManager`) must inject `Provider<...>` to avoid Dagger dependency cycles during `SingletonComponent` initialization.
7. **UI Access**: Because Compose trees and utility classes cannot easily use constructor injection, adding `fun steamManager(): SteamManager`, `fun epicManager(): EpicManager`, `fun gogManager(): GOGManager`, and `fun amazonManager(): AmazonManager` to `AppUtilsEntryPoint` guarantees seamless, escape-hatch-free access.

---

## 3. Caveats

- **Active Milestone 2 Work**: Milestone 2 (Group 3) is currently in progress; a missing import `import app.gamenative.di.appUtilsEntryPoint` in `LibraryScreen.kt` currently prevents clean compilation. Once the Milestone 2 worker adds that import, the baseline builds cleanly.
- **SteamService Refactoring Breadth**: Due to `SteamService.kt` being 4,750 lines long and referenced in ~64 files, extracting `SteamManager` requires methodical care to ensure all Steam callbacks, PICS channels, and Auto Cloud sync events are preserved.
- **No Source Modifications Made**: As an explorer, no modifications were made to any files outside `.agents/explorer_g4_3/`.

---

## 4. Conclusion

1. Group 4 requires creating `SteamManager.kt` as a new `@Singleton` class and refactoring `SteamService.kt` into a thin foreground service shell.
2. `EpicManager`, `GOGManager`, and `AmazonManager` must be updated to absorb the active download tracking, background sync state, and download operations currently trapped in their respective Android Services' companion objects.
3. Escape hatches (`PreferencesEntryPoint` in `EpicManager`, `AmazonDaoEntryPoint` in `AmazonService`) must be eradicated; all dependencies (Room DAOs, Preferences, AppStoragePaths, Coroutines) can be injected cleanly via `@Inject constructor`.
4. Storefront manager accessors should be added to `AppUtilsEntryPoint` for UI tree access.
5. `Provider<SteamManager>` must be used in `CustomGameScanner` and `WorkshopManager` to break circular dependency cycles.
6. A detailed analysis and step-by-step implementation blueprint are documented in `report.md`.

---

## 5. Verification Method

To verify these findings independently:
1. **Inspect Android Manifest**:
   Check service declarations in `app/src/main/AndroidManifest.xml:135-164`.
2. **Inspect Services & Escape Hatches**:
   View `AmazonService.kt:47-51, 158-161` to verify `AmazonDaoEntryPoint` escape hatch.
   View `EpicManager.kt:44-46` to verify `PreferencesEntryPoint` fallback.
   View `SteamService.kt:346-400` to verify static state in companion object.
3. **Inspect DI Modules**:
   View `app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt` to observe existing Group 1-3 bindings and see where Group 4 managers fit.
4. **Verify Downstream Consumers**:
   Run grep for `SteamService.` across `app/src/main/java` to verify call sites in `DownloadsViewModel`, `WorkshopManager`, `CustomGameScanner`, etc.
5. **Report Location**:
   Examine full report at `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_3\report.md`.
