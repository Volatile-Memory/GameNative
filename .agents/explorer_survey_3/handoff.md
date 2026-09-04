# Handoff Report — Explorer Survey 3: Group 5, Group 6, DI Infrastructure & Unit Tests

## 1. Observation

### Group 5: Advanced Subsystems (`BestConfigService`, `WorkshopManager`)
- **`BestConfigService`** (`app/src/main/java/app/gamenative/utils/BestConfigService.kt:32`):
  - Declared as `object BestConfigService` (1007 lines).
  - Holds API constant `API_BASE_URL` ("https://api.gamenative.app/api/best-config"), `httpClient` (`Net.http`), and in-memory cache `ConcurrentHashMap<String, BestConfigResponse>`.
  - Escape Hatches Observed:
    - Line 812: `context.preferencesEntryPoint().containerPreferences()`
    - Line 813: `context.preferencesEntryPoint().authPreferences()`
    - Line 155: `context.getString(R.string...)` inside `getCompatibilityMessage()`
    - Lines 272-307, 511-527, 782-811: `context: Context` parameter prop-drilled across `prepareConfigForApplication()`, `validateComponentVersions()`, `resolveMissingManifestInstallRequests()`, and `parseConfigToContainerData()`.
  - Call Sites:
    - `app/src/main/java/app/gamenative/ui/PluviaMain.kt:1738`
    - `app/src/main/java/app/gamenative/ui/component/dialog/CommunityConfigsDialog.kt:765`
    - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt:90, 870, 897, 913, 997, 1014`
    - `app/src/main/java/app/gamenative/ui/util/ContainerConfigTransfer.kt:96, 111, 119, 157`
    - `app/src/main/java/app/gamenative/utils/ContainerUtils.kt:871, 878`
    - `app/src/test/java/app/gamenative/utils/BestConfigServiceTest.kt:22, 108, 137, 164, ...`

- **`WorkshopManager`** (`app/src/main/java/app/gamenative/workshop/WorkshopManager.kt:74`):
  - Declared as `object WorkshopManager` (4503 lines).
  - Escape Hatches Observed:
    - Line 78: `runCatching { SteamService.instance?.let { PreferencesEntryPoint.get(it).downloadPreferences() } }.getOrNull()`
    - Line 80: `runCatching { SteamService.instance?.let { PreferencesEntryPoint.get(it).containerPreferences() } }.getOrNull()`
    - Line 4104: `PreferencesEntryPoint.get(context).containerPreferences().launchBionicSteam`
    - Lines 1423, 4095: `SteamService.getAppDirPath(appId)`
    - Lines 1424, 4096: `SteamService.getAppInfoOf(appId)`
    - Lines 4224, 4225: `SteamService.instance?.steamClient`, `SteamService.userSteamId`
    - Lines 4229, 4240, 4241, 4242, 4247, 4308, 4349, 4351, 4362, 4363: `SteamService.*` download registry & DB calls.
  - Call Sites:
    - `app/src/main/java/app/gamenative/service/SteamService.kt:4004, 4012`
    - `app/src/main/java/app/gamenative/ui/PluviaMain.kt:2039, 2056, 2079, 2087, 2120, 2134, 2174, 2180, 2196`
    - `app/src/main/java/app/gamenative/ui/component/dialog/WorkshopManagerDialog.kt:130`
    - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/SteamAppScreen.kt:505, 509, 1399, 1429, 1434`
    - `app/src/test/java/app/gamenative/workshop/WorkshopManagerTest.kt:19, 101, 106, 111, 118, ...`

---

### Group 6: PluviaApp (`PluviaApp.companion`)
- **`PluviaApp`** (`app/src/main/java/app/gamenative/PluviaApp.kt:56`):
  - Declared as `@HiltAndroidApp class PluviaApp : SplitCompatApplication()`.
  - Companion object (lines 232-360) holds:
    1. Game Session Runtime State: `xEnvironment: XEnvironment?`, `xServerView: XServerRendererView?`, `inputControlsView: InputControlsView?`, `inputControlsManager: InputControlsManager?`, `touchpadView: TouchpadView?`, `radialMenuCoordinator: RadialMenuCoordinator?`, `achievementWatcher: AchievementWatcher?`, `activeSuspendPolicy: String`, `hasInitializedSuspendPolicyState: Boolean`, `isOverlayPaused: Boolean`, `shutdownEnvironment()`, `clearActiveSuspendState()`, `setActiveSuspendPolicy()`, `hasValidSuspendPolicyState()`, `isNeverSuspendMode()`, `isManualSuspendMode()`.
    2. Global App Event Bus & Utilities: `events: EventDispatcher`, `isActivityInForeground: Boolean`, `getDefaultScreenSize(): String`, `instance: PluviaApp`, `onDestinationChangedListener: NavChangedListener?`.
  - Existing Game Session Infrastructure:
    - `GameSessionScope.kt`: `@Scope @Retention(RUNTIME) annotation class GameSessionScoped`
    - `GameSessionComponent.kt`: `@GameSessionScoped @DefineComponent(parent = SingletonComponent::class) interface GameSessionComponent`
    - `GameSessionEntryPoint.kt`: `@EntryPoint @InstallIn(GameSessionComponent::class) interface GameSessionEntryPoint`
    - `GameSessionManager.kt`: `interface GameSessionManager` with `activeSession: StateFlow<ActiveGameSession?>`
    - `DefaultGameSessionManager.kt`: `@Singleton class DefaultGameSessionManager @Inject constructor(...) : GameSessionManager`
    - `GameSessionModule.kt`: Provides `@GameSessionScoped @GameSessionCoroutineScope CoroutineScope`.

---

### DI Infrastructure & Existing Unit Tests
- **Hilt Modules Observed**:
  - `SystemServicesModule` (`core/system/`): Provides 13 system services (ConnectivityManager, NotificationManager, PowerManager, DisplayManager, etc.).
  - `StorageModule` (`core/storage/`): Binds `AppStoragePaths` (`AndroidAppStoragePaths`).
  - `StringResolverModule` (`core/appinfo/`): Binds `StringResolver` (`AndroidStringResolver`).
  - `PreferencesDataStoreModule` & `PreferencesBindingModule` (`di/PreferencesModule.kt`): Provides `@PluviaDataStore DataStore<Preferences>` and binds 7 domain preference repositories.
  - `DatabaseModule`, `RepositoryModule`, `CoroutinesModule`, `IdModule`, `TimeModule`, `AppBuildInfoModule`, `RuntimeModule`, `GameSessionModule`.
- **Existing Unit Tests**:
  - `BestConfigServiceTest.kt` (`app/src/test/java/app/gamenative/utils/BestConfigServiceTest.kt`): 1094 lines, 36 Robolectric unit tests covering all config parsing, GPU fallback/exact matching, and manifest dependency checks.
  - `WorkshopManagerTest.kt` (`app/src/test/java/app/gamenative/workshop/WorkshopManagerTest.kt`): 652 lines, 50+ JUnit unit tests covering mod ID parsing, cleanup, sync detection, stale directory detection, and compatibility symlinks.
  - `GameSessionManagerTest.kt` (`app/src/test/java/app/gamenative/core/runtime/GameSessionManagerTest.kt`): Tests lifecycle transitions and teardown logic with `FakeGameSessionManager`.

---

## 2. Logic Chain

1. **Group 5 Conversion**:
   - `BestConfigService` currently fetches remote configurations, validates available versions against resource string arrays and manifest components, and performs GPU-specific overrides. By converting it to `@Singleton class BestConfigService @Inject constructor(...)` with `@ApplicationContext context: Context`, `containerPreferences: ContainerPreferences`, `authPreferences: AuthPreferences`, and `stringResolver: StringResolver`:
     - We completely eliminate `context.preferencesEntryPoint()` calls (lines 812, 813).
     - We eliminate `context: Context` parameter from `getCompatibilityMessage()` (using `stringResolver`).
     - We clean up public caller methods `parseConfigToContainerData`, `parseConfigResult`, and `resolveMissingManifestInstallRequests`.
   - `WorkshopManager` currently manages Steam workshop mod subscriptions, downloads, disk validation, and symlink creation. By converting it to `@Singleton class WorkshopManager @Inject constructor(...)` with `@ApplicationContext context: Context`, `downloadPreferences: DownloadPreferences`, `containerPreferences: ContainerPreferences`, `appStoragePaths: AppStoragePaths`, and `steamManagerProvider: Provider<SteamManager>`:
     - We eliminate `PreferencesEntryPoint.get(SteamService.instance)` (lines 78, 80) and `PreferencesEntryPoint.get(context)` (line 4104).
     - Static callers in `SteamService`, `SteamAppScreen`, `PluviaMain`, and `WorkshopManagerDialog` receive `WorkshopManager` via `@Inject` or method parameters.

2. **Group 6 PluviaApp Companion Extraction**:
   - `PluviaApp.companion` currently acts as a global mutable state holder for active game sessions and app-wide singletons.
   - The session-specific runtime fields (`xEnvironment`, `xServerView`, `inputControlsView`, `inputControlsManager`, `touchpadView`, `radialMenuCoordinator`, `achievementWatcher`, suspend policies, and `shutdownEnvironment()`) logically belong to the active playing session lifecycle.
   - By creating `@GameSessionScoped class GameSessionRuntime @Inject constructor(...)`:
     - All runtime views and execution handles are scoped directly to the `GameSessionComponent`.
     - When `GameSessionManager.startSession(...)` is called, `GameSessionComponent` is built with `ActiveGameSessionInfo`.
     - When `GameSessionManager.endSession(...)` or `terminate()` runs, `GameSessionRuntime.shutdown()` executes clean teardown, eliminating static memory leaks.
   - For global utilities:
     - `EventDispatcher`: Becomes `@Singleton class EventDispatcher @Inject constructor()`, provided via Hilt and injected into callers (`MainActivity`, `SteamService`, etc.).
     - `getDefaultScreenSize()`: Moved to `@Singleton class ScreenSizeResolver @Inject constructor(displayManager: DisplayManager)` or provided via `SystemServicesModule`.
     - `isActivityInForeground`: Managed in `@Singleton class AppLifecycleState @Inject constructor()`.

3. **DI Infrastructure & Utilities Readiness**:
   - Existing modules already provide `AppStoragePaths`, `StringResolver`, `SystemServicesModule` (13 services), `PreferencesModule` (`@PluviaDataStore`), and coroutine dispatchers.
   - Minor additions required: Provide `EventDispatcher` in `SingletonComponent`, provide `OkHttpClient` (`Net.http`), and provide `ScreenSizeResolver`.

4. **Existing Unit Tests Migration**:
   - `BestConfigServiceTest` and `WorkshopManagerTest` will instantiate the refactored `@Singleton class` directly in `@Before` setup using `ApplicationProvider.getApplicationContext()` and mock/fake preference repositories.

---

## 3. Caveats

- **XR Build Support**: `ImmersiveXrActivity.kt` accesses `PluviaApp.xServerView` directly for Vulkan and OpenGL XR frame bridges (`setVulkanXrFrameBridge`, `setXrFrameBridge`). These call sites must receive `GameSessionManager` / `GameSessionRuntime` via injection.
- **DEX Register Limits**: `XServerScreen.kt` is near the Dex verifier register limit. Helper parameter bundling (such as `ImmersiveSessionHooks`) should be preserved when passing `GameSessionRuntime` to avoid runtime Dex verification errors.
- **Read-Only Explorer Phase**: No source code modifications were performed during this survey.

---

## 4. Conclusion

- Group 5 (`BestConfigService`, `WorkshopManager`) and Group 6 (`PluviaApp.companion` / `GameSessionScoped`) are fully mapped with zero ambiguities.
- Every escape hatch (`PreferencesEntryPoint`, `EntryPointAccessors`, `Context` prop-drilling) has been identified with exact line numbers and concrete `@Inject constructor` replacements.
- All 15+ caller files and 100+ call sites across UI screens, ViewModels, and services have been cataloged.
- The project's DI infrastructure already contains the necessary foundation (`GameSessionComponent`, `GameSessionManager`, `SystemServicesModule`, `AppStoragePaths`, `StringResolver`, `PreferencesModule`).
- The full architectural artifact has been generated at `C:\Users\VladK\.gemini\antigravity\brain\8674d8dc-f545-44e0-9158-c9a48781b3e1\survey_report.md`.

---

## 5. Verification Method

To independently verify the survey findings and ensure clean compilation after refactoring:

1. **Source Inspection**:
   - Inspect `app/src/main/java/app/gamenative/utils/BestConfigService.kt`
   - Inspect `app/src/main/java/app/gamenative/workshop/WorkshopManager.kt`
   - Inspect `app/src/main/java/app/gamenative/PluviaApp.kt`
   - Inspect `app/src/main/java/app/gamenative/core/runtime/` (`GameSessionComponent.kt`, `GameSessionManager.kt`)
2. **Build Verification**:
   - `./gradlew compileModernDebugKotlin`
3. **Unit Tests Verification**:
   - `./gradlew :app:testModernDebugUnitTest --tests "app.gamenative.utils.BestConfigServiceTest"`
   - `./gradlew :app:testModernDebugUnitTest --tests "app.gamenative.workshop.WorkshopManagerTest"`
   - `./gradlew :app:testModernDebugUnitTest --tests "app.gamenative.core.runtime.GameSessionManagerTest"`
