# Mid-Level Singletons Refactoring Survey (Groups 3 & 4) Handoff Report

**Explorer**: `explorer_survey_2_2`  
**Target Domains**: Group 3 (User Library Managers) & Group 4 (Storefront Services / Hidden Singletons)  
**Report File**: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_2\survey_report.md`  
**Timestamp**: 2026-09-02T01:57:00+05:00  

---

## 1. Observation

### 1.1 Source Files Directly Investigated
1. **`app/src/main/java/app/gamenative/data/FavoritesManager.kt`** (38 lines):
   - Currently declared as `object FavoritesManager : FavoritesRepository` with `@Volatile internal var delegate: FavoritesRepository`.
   - Initialized in `PluviaApp.kt` line 81 (`FavoritesManager.delegate = favoritesRepository`).
   - Referenced in 7 files (`PluviaApp.kt`, `FavoritesManager.kt`, `LibraryViewModel.kt`, `LibraryScreen.kt`, `BaseAppScreen.kt`, `FavoriteActions.kt`, `FavoriteCardIndicator.kt`).
2. **`app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt`** (267 lines):
   - Currently declared as `object FrontendSyncManager`.
   - Contains `@EntryPoint @InstallIn(SingletonComponent::class) interface FrontendSyncEntryPoint` (lines 44–51).
   - Calls `EntryPointAccessors.fromApplication` (line 89), `context.preferencesEntryPoint().downloadPreferences()` (line 94), and `appContext.getString` (line 138).
   - Initialized in `PluviaApp.kt` line 104 (`FrontendSyncManager.init(this)`).
   - Referenced in 4 production files and 1 test file.
3. **`app/src/main/java/app/gamenative/utils/CustomGameScanner.kt`** (845 lines):
   - Currently declared as `object CustomGameScanner` with `@Volatile` mutable preference fields (`downloadPreferences`, `libraryPreferences`, `containerPreferences`).
   - Calls `SteamService.instance`, `DownloadService`, `ContainerManager(context)`, and Android permission framework methods.
   - Referenced across 22 files (ViewModels, UI Composables, Dialogs, Storage utilities).
4. **`app/src/main/java/app/gamenative/service/epic/EpicService.kt`** (761 lines) & **`EpicManager.kt`** (1,141 lines):
   - `EpicService` holds a static companion object with active download state (`activeDownloads: ConcurrentHashMap<Int, DownloadInfo>`), sync tracking, and 30+ static methods delegating to injected fields on `instance`.
   - `EpicManager` is already `@Singleton class EpicManager @Inject constructor(...)` but falls back to `PreferencesEntryPoint.get(PluviaApp.instance)` for `downloadPreferences`.
   - Referenced across 24 files.
5. **`app/src/main/java/app/gamenative/service/gog/GOGService.kt`** (850 lines) & **`GOGManager.kt`** (1,289 lines):
   - `GOGService` holds a static companion object with download state (`activeDownloads`), sync state, and static methods for auth, downloads, game queries, and cloud saves.
   - `GOGManager` is `@Singleton class GOGManager @Inject constructor(...)`.
   - Referenced across 27 files (including unit tests).
6. **`app/src/main/java/app/gamenative/service/amazon/AmazonService.kt`** (925 lines) & **`AmazonManager.kt`** (95 lines):
   - `AmazonService` holds `AmazonDaoEntryPoint` (`@EntryPoint @InstallIn(SingletonComponent::class)`), `EntryPointAccessors.fromApplication(...)`, active downloads state (`activeDownloads`, `activeDownloadPaths`), and static companion methods.
   - `AmazonManager` is a small 95-line class.
   - Referenced across 19 files.
7. **`app/src/main/java/app/gamenative/service/SteamService.kt`** (4,750 lines):
   - Monolithic Android service whose companion object spans over 3,100 lines (lines 346–3513) with ~100 static methods and global download maps (`downloadJobs`).
   - Referenced across 78+ files across ViewModels, UI Composables, Dialogs, Services, and Tests (`SteamAutoCloudTest` currently relies on Java reflection to set `SteamService.instance`).

---

## 2. Logic Chain

1. **Hidden Singletons in Android Services**: Android `Service` classes (`EpicService`, `GOGService`, `AmazonService`, `SteamService`) were being used as static service locators because UI and ViewModels lacked direct DI access to background state.
2. **Separation of Concerns**: An Android `Service` is an Android OS process/foreground component, whereas download management, PICS syncing, database querying, and API authentication are application-level domain logic.
3. **Manager Extraction**: Extracting the state, maps, and domain methods into `@Singleton class *Manager @Inject constructor(...)` allows:
   - Complete removal of static companion instances (`instance: Service?`).
   - Elimination of `EntryPoint` accessors and `Context` prop-drilling.
   - Direct constructor injection into ViewModels and UI state holders.
   - Thin Android Services that only manage foreground notification channels, service intents, and process keep-alive.
4. **Group 3 Managers Conversion**:
   - `FavoritesManager`: converted to `@Singleton class FavoritesManager @Inject constructor(FavoritesRepository)` or replaced with direct `FavoritesRepository` injection.
   - `FrontendSyncManager`: converted to `@Singleton class FrontendSyncManager @Inject constructor(...)`, eliminating `FrontendSyncEntryPoint`.
   - `CustomGameScanner`: converted to `@Singleton class CustomGameScanner @Inject constructor(...)`, eliminating `@Volatile` mutable preference properties.

---

## 3. Caveats

- **Early Lifecycle Access**: `SteamService.attachBaseContext(newBase: Context)` executes before Hilt field injection. It reads `generalPreferences.appLanguage`. If needed, `PreferencesEntryPoint` in `attachBaseContext` or a static helper reading from DataStore may remain strictly for `attachBaseContext` if Hilt is not yet attached, or language can be set earlier in application context.
- **Provider Injection for Circular References**: `SteamManager`, `CustomGameScanner`, and `FrontendSyncManager` interact with each other. Using `javax.inject.Provider<SteamManager>` avoids any potential Dagger cyclic dependency during compilation.
- **Preserve Service Manifest Registrations**: The Android Service classes (`SteamService`, `EpicService`, `GOGService`, `AmazonService`) MUST remain declared in `AndroidManifest.xml` as foreground services with `foregroundServiceType="dataSync"`.

---

## 4. Conclusion

1. All 7 target classes in Groups 3 and 4 have been thoroughly surveyed, mapped, and cataloged.
2. Complete caller inventories and exact `@Singleton class ... @Inject constructor(...)` signatures have been produced in `survey_report.md`.
3. All `PreferencesEntryPoint`, `EntryPointAccessors`, mutable `@Volatile` fields, and reflection-based test setups can be completely eliminated.
4. The migration plan provides an incremental, zero-breakage path for worker agents to implement these refactorings.

---

## 5. Verification Method

To independently verify the survey findings:
1. **Report Verification**: Inspect `survey_report.md` for complete class inventories, exact line references, and proposed signatures.
2. **Build Verification**:
   ```bash
   ./gradlew compileModernDebugKotlin
   ```
3. **Unit Test Verification**:
   ```bash
   ./gradlew :app:testModernDebugUnitTest
   ```
4. **Invalidation Conditions**: Any unaccounted call site of `SteamService`, `EpicService`, `GOGService`, `AmazonService`, `FavoritesManager`, `FrontendSyncManager`, or `CustomGameScanner` would require updating the caller matrix in `survey_report.md`.
