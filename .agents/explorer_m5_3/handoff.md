# Handoff Report: Call Sites, Lifecycle Hooks, and Unit Test Strategy (Milestone 5)

**Agent**: `explorer_m5_3` (Codebase Researcher / Explorer)  
**Parent**: `4e0c7245-24ab-4ad8-b0b1-6787f82b4eba`  
**Date**: 2026-09-05T05:48:00Z  
**Scope**: Group 6: PluviaApp Session Extraction (Milestone 5)  

---

## 1. Observation

### 1.1 Scope of Investigation
This investigation examined:
1. All call sites of `PluviaApp` static members across `app/src/main/java/app/gamenative/ui/`, `app/src/main/java/app/gamenative/service/`, and `app/src/main/java/com/winlator/`.
2. The runtime interaction model between `XServerView`, touch/input controls (`InputControlsView`, `TouchpadView`, `InputControlsManager`), `RadialMenuCoordinator`, suspend/resume state machines, and Android `Activity`/`View` lifecycle hooks (`MainActivity`, `ImmersiveXrActivity`, `XServerScreen`).
3. Existing unit test coverage in `app/src/test` and specification of new test requirements.
4. Step-by-step refactoring boundaries and risk mitigation strategy for Worker.

---

### 1.2 Call Site Inventory by Package & Class

#### A. UI Package (`app/src/main/java/app/gamenative/ui/`)

1. **`XServerScreen.kt`** (5,992 lines) — The core in-session Compose screen:
   - **`PluviaApp.touchpadView`** (36 usages):
     - Instantiation: Line 1912: `PluviaApp.touchpadView = TouchpadView(context, getxServer(), inputPrefs.capturePointerOnExternalMouse)`
     - View hierarchy attachment: Line 1913: `frameLayout.addView(PluviaApp.touchpadView)`
     - Configuration & listeners: Lines 1914 (`setMoveCursorToTouchpoint`), 1921 (`setShowKeyboardCallback`), 1937 (`setClickHighlightListener`), 2131, 2133, 2136 (`setGestureConfig`), 2449, 2463 (`setBackgroundColor`), 2541, 2793, 3154 (`setSensitivity`), 3177–3182 (`setEnabled`, pointer button toggles)
     - Touch & pointer event processing: Lines 922–923, 978–979, 1132, 1226, 1230, 1370–1371, 1443–1444 (`releasePointerCapture`), 1587 (`hasPointerCapture`), 1838 (`onTouchEvent`)
     - View binding into other views: Line 2331 (`setTouchpadView(PluviaApp.touchpadView)` inside `icView`), Line 2431 (`touchpadViewProvider = { PluviaApp.touchpadView }`)
   - **`PluviaApp.inputControlsView`** (42 usages):
     - Instantiation & binding: Line 2328 (`val icView = InputControlsView(context)`), Line 2396 (`PluviaApp.inputControlsView = icView`), Line 2404 (`xServerView.getxServer().winHandler.setInputControlsView(PluviaApp.inputControlsView)`)
     - Profile management: Lines 1179, 1193, 2666, 2921–2922, 3174, 3401, 3417, 3510
     - Input event routing: Lines 1530 (`onKeyEvent`), 1582 (`onGenericMotionEvent`), 1833 (`onTouchEvent`), 2375 (`triggerShowKeyboard`), 3113, 3172–3173, 3186, 3492, 3519, 3532
     - Layout element editor: Lines 1203–1204 (`setEditMode`), 2611–2636 (`addElement`, `removeElement`, `profile.save`, `invalidate`), 2653–2658, 2769, 2772, 3542, 3549
     - Container shooter mode: Lines 1284 (`setContainerShooterMode`), 1285, 2809 (`setShooterModeConfig`)
   - **`PluviaApp.inputControlsManager`** (12 usages):
     - Instantiation: Line 2308: `PluviaApp.inputControlsManager = InputControlsManager(context)`
     - Profile querying: Lines 958, 1108, 1146, 1246, 1270, 2334, 2664, 2849, 3073 (`getProfiles`), 3509, 3556
   - **`PluviaApp.radialMenuCoordinator`** (10 usages):
     - Lifecycle: Line 500 (`detach()`), Line 501 (`= null`), Line 2369, Line 2402 (`bindInputControlsView`)
     - Interaction & profile: Line 1180 (`setProfile`), Line 1298 (`showSettingsDialog`), Line 1821 (`onHostTouchEvent`), Line 2367, Line 2925, Line 3111
   - **`PluviaApp.xEnvironment`** (11 usages):
     - Session setup: Line 2233: `PluviaApp.xEnvironment = setupXEnvironment(...)`
     - Error teardown: Line 2285 (`stopEnvironmentComponents()`), Line 2289 (`= null`)
     - Pause/Resume: Lines 808 (`onPause`), 822, 828, 836 (`onResume`), 1331 (`resumeGameProcesses`), 2272 (`onPause`)
     - State checks: Line 1868, Line 2113 (`if (PluviaApp.xEnvironment == null)`)
   - **`PluviaApp.xServerView`** (5 usages):
     - Assignment: Line 2297: `PluviaApp.xServerView = xServerView`
     - View destruction teardown: Lines 2567–2568: `if (PluviaApp.xServerView === binding.xServerView) { PluviaApp.xServerView = null }`
     - Controller & renderer query: Line 3175 (`refreshControllerMappingsForHotplug`), Line 3183 (`setCursorVisible`)
   - **`PluviaApp.achievementWatcher`** (1 usage):
     - Instantiation: Line 4076: `PluviaApp.achievementWatcher = AchievementWatcher(...)`
   - **Suspend State & Teardown** (14 usages):
     - `PluviaApp.setActiveSuspendPolicy(suspendPolicy)`: Line 436
     - `PluviaApp.clearActiveSuspendState()`: Line 1460
     - `PluviaApp.isOverlayPaused`: Lines 800, 809, 813, 827, 834, 1330, 1462, 1480, 1587, 2275, 2763
     - `PluviaApp.isActivityInForeground`: Line 2271
     - `PluviaApp.shutdownEnvironment()`: Line 4583 (inside `exit()`)

2. **`RadialMenuCoordinator.kt`** (8 usages):
   - Lines 55, 68: `PluviaApp.radialMenuCoordinator?.detach()`, `PluviaApp.radialMenuCoordinator = coordinator`
   - Line 67: `coordinator.bindTouchpadView(PluviaApp.touchpadView)`
   - Lines 320, 362: `PluviaApp.inputControlsManager`
   - Lines 564, 609: `val winHandler = xServer.winHandler ?: PluviaApp.xServerView?.getxServer()?.winHandler`

3. **`MainActivity.kt`** (15 usages):
   - Lines 209–211: Stale keepAlive guard: `if (SteamService.keepAlive && PluviaApp.xEnvironment == null) { PluviaApp.shutdownEnvironment() }`
   - Line 383: Cleanup on activity destroy/recreate: `PluviaApp.shutdownEnvironment()`
   - Lines 424, 428: Guard in `hasReadyGameLifecycleState()`: `!PluviaApp.hasValidSuspendPolicyState()`, `PluviaApp.xEnvironment == null`
   - Lines 438, 487: Activity foreground tracking: `PluviaApp.isActivityInForeground = true/false`
   - Lines 452, 490: `PluviaApp.isNeverSuspendMode()`
   - Lines 455, 496: `PluviaApp.isOverlayPaused`
   - Lines 456, 495: `PluviaApp.isManualSuspendMode()`
   - Lines 461, 494: `PluviaApp.xEnvironment?.onResume()`, `PluviaApp.xEnvironment?.onPause()`

4. **`ImmersiveXrActivity.kt`** (19 usages):
   - Lines 251, 419, 431: `PluviaApp.isActivityInForeground = true/false`
   - Lines 294, 316, 499, 1050, 1120, 1124, 1125, 1136, 1143, 1265: `PluviaApp.xServerView` (used to bridge rendering frames to Vulkan/GL OpenXR compositor surfaces)
   - Lines 420, 439: `PluviaApp.hasValidSuspendPolicyState()`
   - Lines 420, 424, 440, 442: `PluviaApp.xEnvironment` lifecycle inspection, `onResume()`, `onPause()`
   - Lines 422, 440: `PluviaApp.isNeverSuspendMode()`
   - Line 423: `PluviaApp.isManualSuspendMode()`
   - Lines 423, 524, 525, 546, 931: `PluviaApp.isOverlayPaused`
   - Lines 438, 449: `PluviaApp.shutdownEnvironment()`

5. **`PluviaMain.kt`** (5 usages):
   - Line 580: `pending == null && PluviaApp.xEnvironment == null` (prevent unwanted route pop when game is actively running)
   - Line 1387: `shouldShowDialogs && !state.annoyingDialogShown && PluviaApp.xEnvironment == null && !SteamService.keepAlive` (suppress dialogs when game is running)
   - Lines 678, 685, 690: `PluviaApp.onDestinationChangedListener` (local navigation listener hook)

6. **UI Data & Preference Holders**:
   - `XServerState.kt`: Line 14: `val screenSize: String = PluviaApp.getDefaultScreenSize()`
   - `SteamSaveTransfer.kt`: Line 368: `PluviaApp.instance?.let { PreferencesEntryPoint.get(it).authPreferences() }`
   - `LibraryTab.kt`: Line 106: `PluviaApp.instance?.let { PreferencesEntryPoint.get(it).libraryPreferences().showRecommendations }`

#### B. Service Package (`app/src/main/java/app/gamenative/service/`)

1. **`SteamManager.kt`**:
   - Line 1518: `if (PluviaApp.xEnvironment != null)` (guards conflict resolution on `EResult.LoggedInElsewhere`)
2. **`SteamService.kt`**:
   - Line 227: `PluviaApp.instance?.let { runCatching { AppUtilsEntryPoint.get(it).steamManager() }.getOrNull() }` (fallback when service instance is null)
3. **`SteamWishlistService.kt`**:
   - Line 33: `PreferencesEntryPoint.get(PluviaApp.instance).authPreferences()`
4. **`NotificationActionReceiver.kt`**:
   - Line 29: `PluviaApp.events.emit(AndroidEvent.EndProcess)`
5. **Storefront Constants Fallbacks**:
   - `GOGConstants.kt`: Lines 129, 141: `val context = appContext ?: PluviaApp.instance`
   - `AmazonConstants.kt`: Line 62: `val targetContext = context ?: PluviaApp.instance`
   - `EpicConstants.kt`: Line 123: `val targetContext = context ?: PluviaApp.instance`

#### C. Winlator Package (`app/src/main/java/com/winlator/`)

1. **`ContainerData.kt`**:
   - Line 4: `import app.gamenative.PluviaApp`
   - Line 15: `val screenSize: String = PluviaApp.getDefaultScreenSize()`
2. **`GlibcProgramLauncherComponent.java`**:
   - Line 36: Unused import `import app.gamenative.PluviaApp;`
3. **`BionicProgramLauncherComponent.java`**:
   - Line 54: Unused import `import app.gamenative.PluviaApp;`

---

### 1.3 Interaction Analysis: Views, Controls, Suspension, & Activity Hooks

Tracing the lifecycle of an active game session reveals three distinct stages:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. BOOT / INITIALIZATION (XServerScreen.kt)                                │
├─────────────────────────────────────────────────────────────────────────────┤
│ • Container & wine configuration assembled.                                 │
│ • XServerView created -> assigned to PluviaApp.xServerView                  │
│ • TouchpadView created -> assigned to PluviaApp.touchpadView                │
│ • InputControlsManager created -> assigned to PluviaApp.inputControlsManager│
│ • RadialMenuCoordinator installed -> assigned to PluviaApp.radialMenuCoord  │
│ • InputControlsView created -> assigned to PluviaApp.inputControlsView      │
│ • XEnvironment initialized -> assigned to PluviaApp.xEnvironment           │
│ • Suspend policy set: PluviaApp.setActiveSuspendPolicy(...)                 │
│ • AchievementWatcher started -> assigned to PluviaApp.achievementWatcher   │
│ • PowerManager.autoStart(...) and process CPU pinning triggered             │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. RUNNING & SUSPENSION HOOKS (Activity + Compose Quick Menu)               │
├─────────────────────────────────────────────────────────────────────────────┤
│ • Activity onPause():                                                       │
│     isActivityInForeground = false                                          │
│     If !neverSuspend: xEnvironment?.onPause()                               │
│     If manualSuspend: isOverlayPaused = true                                │
│ • Activity onResume():                                                      │
│     isActivityInForeground = true                                           │
│     If !neverSuspend && !isOverlayPaused: xEnvironment?.onResume()          │
│ • Compose Quick Menu Toggle (XServerScreen.kt):                             │
│     pauseGameIfAllowed() -> xEnvironment?.onPause(); isOverlayPaused = true │
│     resumeIfAllowedAfterOverlay() -> isOverlayPaused = false; onResume()    │
│ • XR Frame Bridging (ImmersiveXrActivity.kt):                               │
│     Reads xServerView.renderer and hooks frame bridge into OpenXR runtime   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. TEARDOWN / CLEANUP (exit(), onDestroy(), or crash recovery)             │
├─────────────────────────────────────────────────────────────────────────────┤
│ • PluviaApp.shutdownEnvironment():                                          │
│     achievementWatcher?.stop()                                              │
│     SteamService.clearCachedAchievements()                                  │
│     touchpadView?.releasePointerCapture()                                   │
│     radialMenuCoordinator?.detach()                                         │
│     xEnvironment?.stopEnvironmentComponents()                              │
│     PowerManager.stop()                                                     │
│     SteamService.keepAlive = false; clearPlayingConflict()                  │
│     Null out: xEnvironment, xServerView, touchpadView, icView, radialMenu  │
│     ActiveGameRegistry.clear(); clearActiveSuspendState()                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key Observation on Leaks**:
Notice that `touchpadView`, `inputControlsView`, and `xServerView` hold references to the host `Activity` Context. In the current design, storing them in `PluviaApp.companion` causes them to live for the entire process lifetime unless `shutdownEnvironment()` is explicitly executed. Scoping them inside `@GameSessionScoped class GameSessionRuntime` guarantees that when `GameSessionManager.endSession()` finishes, the entire subcomponent instance is dereferenced and eligible for garbage collection.

---

### 1.4 Unit Test Inventory & Analysis in `app/src/test`

1. **Grep Search for `PluviaApp` in `app/src/test`**:
   - Matches: **0 files**.
   - Verbatim result: `No results found`.
   - Explanation: `PluviaApp` extends Android's `SplitCompatApplication`. Because of static state and framework dependencies, no unit tests could reference `PluviaApp` without failing with `Method not mocked` runtime exceptions.

2. **Existing Session Lifecycle Tests**:
   - `app/src/test/java/app/gamenative/core/runtime/GameSessionManagerTest.kt`:
     Tests `FakeGameSessionManager` lifecycle operations:
     - `fakeGameSessionManager_managesLifecycleCorrectly`: Verifies `startSession`, `getActiveSessionInfo`, `endSession`, session closed flags, and teardown callbacks.
     - `fakeGameSessionManager_startingNewSessionEndsPrevious`: Verifies starting a new session terminates previous active session.
   - `app/src/test/java/app/gamenative/testutil/FakeGameSessionManager.kt`:
     Implements in-memory fake with `startSessionCallCount`, `endSessionCallCount`, and `StateFlow<ActiveGameSession?>`.

3. **Existing DI EntryPoint Tests**:
   - `app/src/test/java/app/gamenative/di/AppUtilsEntryPointTest.kt`:
     Tests dynamic resolution of converted singletons from `AppUtilsEntryPoint`.

---

## 2. Logic Chain

### 2.1 Separation of Concerns: In-Session vs Out-of-Session

- **Observation**: Callers of `PluviaApp` fall into three distinct architectural categories:
  1. **In-Session Callers (`XServerScreen`, `RadialMenuCoordinator`)**: Operate directly on the running game's views, touchpads, input overlay, and wine processes.
  2. **Out-of-Session Callers (`MainActivity`, `ImmersiveXrActivity`, `SteamManager`, `PluviaMain`)**: Never need to touch the views directly; they only need to query if a session is running (`isSessionRunning`), signal pause/resume to the active session (`pauseSession()`, `resumeSession()`), or terminate the session (`endSession()`).
  3. **Global Utility Callers (`ContainerData`, `IntentLaunchManager`, `XServerState`, `DefaultContainerPreferences`)**: Query device screen aspect ratio (`getDefaultScreenSize()`) or dispatch app events (`events`).
- **Logic**:
  - The in-session state belongs in `@GameSessionScoped class GameSessionRuntime`.
  - The out-of-session lifecycle management belongs in `@Singleton class DefaultGameSessionManager : GameSessionManager`.
  - The global utility methods belong in `@Singleton class ScreenSizeResolver` and `@Singleton class EventDispatcher`.

### 2.2 Preserving Game Execution Reliability via Dagger Subcomponents

- **Observation**: A game session is started inside `XServerScreen` via Compose and ended either by the user exiting (`XServerScreen.exit()`), the Activity pausing/destroying (`MainActivity.onDestroy()`), or an external event (`AndroidEvent.EndProcess`).
- **Logic**:
  - By using Dagger Hilt's `@DefineComponent(parent = SingletonComponent::class)`, `GameSessionComponent` is an explicit child of `SingletonComponent`.
  - It receives access to all application singletons (`SteamManager`, `ContainerPreferences`, `AuthPreferences`, etc.) without passing them through UI composables.
  - `DefaultGameSessionManager` protects session state transitions using a `Mutex`. If a new game is launched while a prior game is still terminating, `DefaultGameSessionManager.startSession` guarantees the prior session is terminated before the new one is initialized.

### 2.3 Safe Incremental Migration Strategy

- **Observation**: `XServerScreen.kt` has over 100 usages of `PluviaApp.<member>`. Rewriting all of `XServerScreen.kt` in one massive commit risks runtime regressions in on-screen controls, virtual gamepad layout, and touch event handling.
- **Logic**:
  - Provide delegating bridge properties in `PluviaApp.companion` during the transition phase.
  - The bridge forwards reads/writes to `AppUtilsEntryPoint.get(instance).gameSessionManager().currentRuntime`.
  - This allows `GameSessionRuntime`, `DefaultGameSessionManager`, `ScreenSizeResolver`, and new unit tests to be fully compiled and tested *first*, before refactoring call sites in `XServerScreen.kt` and `MainActivity.kt`.

---

## 3. Caveats

1. **Android Main Thread vs Background Coroutines for Views**:
   `touchpadView`, `inputControlsView`, and `xServerView` must be instantiated, attached, and detached on the Android Main (UI) thread. `GameSessionRuntime.shutdownEnvironment()` can be called from `DefaultGameSessionManager.endSession()` in a coroutine, so view cleanup that interacts with UI views (`releasePointerCapture`, `detach`) must be resilient to thread context or dispatched to `Dispatchers.Main` if required.
2. **Activity Configuration Changes (Orientation & Multi-Window)**:
   When the device rotates, `MainActivity` is re-created unless `configChanges` is handled. `MainActivity.onPause()` and `onDestroy()` check `isChangingConfigurations`. The refactoring must preserve this check so rotating the device does not inadvertently terminate an active game session.
3. **XR OpenXR Renderer Surface References**:
   `ImmersiveXrActivity.kt` accesses `xServerView?.renderer` (lines 294, 316, 1050, 1120–1143) to pass the native Vulkan/GL swapchain bridge to OpenXR. `gameSessionManager.currentRuntime?.xServerView` provides identical access without leaking after the XR session terminates.
4. **Unused Imports in Java Components**:
   `GlibcProgramLauncherComponent.java` (line 36) and `BionicProgramLauncherComponent.java` (line 54) have unused imports of `app.gamenative.PluviaApp` that should be deleted for code cleanliness.
5. **No other caveats.**

---

## 4. Conclusion & Actionable Execution Plan

### 4.1 Target Architecture Summary

| Component | Scope | File Path | Responsibilities |
|---|---|---|---|
| `GameSessionRuntime` | `@GameSessionScoped` | `app/gamenative/core/runtime/GameSessionRuntime.kt` | Holds `xEnvironment`, `xServerView`, `touchpadView`, `inputControlsView`, `inputControlsManager`, `radialMenuCoordinator`, `achievementWatcher`, suspend/overlay states, and executes `shutdownEnvironment()`. Injects `Provider<SteamManager>`. |
| `GameSessionComponent` | Subcomponent | `app/gamenative/core/runtime/GameSessionComponent.kt` | `@DefineComponent(parent = SingletonComponent::class)`. Binds `ActiveGameSessionInfo`. |
| `GameSessionEntryPoint` | `@EntryPoint` | `app/gamenative/core/runtime/GameSessionEntryPoint.kt` | Exposes `sessionInfo()`, `sessionScope()`, `gameSessionRuntime()`. |
| `ActiveGameSession` | Handle | `app/gamenative/core/runtime/ActiveGameSession.kt` | Holds `info`, `component`, `sessionScope`, `runtime`, and executes `terminate()`. |
| `GameSessionManager` | Interface | `app/gamenative/core/runtime/GameSessionManager.kt` | Defines `activeSession: StateFlow<ActiveGameSession?>`, `isSessionRunning`, `currentRuntime`, `startSession`, `endSession`. |
| `DefaultGameSessionManager` | `@Singleton` | `app/gamenative/core/runtime/DefaultGameSessionManager.kt` | Implements thread-safe session transitions using `Mutex`. |
| `ScreenSizeResolver` | `@Singleton` | `app/gamenative/utils/ScreenSizeResolver.kt` | Resolves device screen aspect ratio (`DEFAULT_SCREEN_SIZE_4_3`, `16_10`, `16_9`). |
| `EventsModule` | `@Module` | `app/gamenative/di/EventsModule.kt` | Provides `@Singleton fun provideEventDispatcher(): EventDispatcher`. |
| `AppUtilsEntryPoint` | `@EntryPoint` | `app/gamenative/di/AppUtilsEntryPoint.kt` | Exposes `gameSessionManager()`, `screenSizeResolver()`, `eventDispatcher()`. |

---

### 4.2 Step-by-Step Refactoring Plan for Worker

#### Phase 1: Global Utilities & Core Runtime Implementation
1. **Create `ScreenSizeResolver.kt`**:
   - Extract `PluviaApp.getDefaultScreenSize()` logic into `@Singleton class ScreenSizeResolver @Inject constructor(@ApplicationContext private val context: Context)`.
2. **Create `EventsModule.kt`**:
   - Provide `@Provides @Singleton fun provideEventDispatcher(): EventDispatcher = EventDispatcher()`.
3. **Update `AppUtilsEntryPoint.kt`**:
   - Add accessors:
     ```kotlin
     fun gameSessionManager(): GameSessionManager
     fun screenSizeResolver(): ScreenSizeResolver
     fun eventDispatcher(): EventDispatcher
     ```
4. **Implement `GameSessionRuntime.kt`**:
   - Annotated with `@GameSessionScoped`.
   - Inject `ActiveGameSessionInfo`, `Provider<SteamManager>`, and `@GameSessionCoroutineScope CoroutineScope`.
   - Hold `xEnvironment`, `xServerView`, `touchpadView`, `inputControlsView`, `inputControlsManager`, `radialMenuCoordinator`, `achievementWatcher`.
   - Implement `isOverlayPaused`, `isActivityInForeground`, `activeSuspendPolicy`.
   - Implement `startSession`, `pauseSession`, `resumeSession`, `onActivityResume`, `onActivityPause`, `shutdownEnvironment`.
5. **Update `GameSessionEntryPoint.kt` & `ActiveGameSession.kt`**:
   - Add `fun gameSessionRuntime(): GameSessionRuntime` to `GameSessionEntryPoint`.
   - Add `val runtime: GameSessionRuntime` to `ActiveGameSession`, invoking `runtime.shutdownEnvironment()` on termination.
6. **Update `DefaultGameSessionManager.kt`**:
   - In `startSession`, extract `runtime = entryPoint.gameSessionRuntime()`, call `runtime.startSession(info)`, pass to `ActiveGameSession`.
   - Provide `override val currentRuntime: GameSessionRuntime? get() = activeSession.value?.runtime`.

#### Phase 2: Unit Test Suite Implementation
1. **Create `GameSessionRuntimeTest.kt`** (`app/src/test/java/app/gamenative/core/runtime/GameSessionRuntimeTest.kt`):
   - Test initial default states.
   - Test suspend policy configuration (`setActiveSuspendPolicy`, `isNeverSuspendMode`, `isManualSuspendMode`).
   - Test pause/resume state machine.
   - Test activity resume/pause dispatch.
   - Test `shutdownEnvironment()` invocation and teardown resilience (verifying one failing step does not abort the rest).
2. **Create `DefaultGameSessionManagerTest.kt`** (`app/src/test/java/app/gamenative/core/runtime/DefaultGameSessionManagerTest.kt`):
   - Test session start and end flow using test component builder.
   - Test auto-termination of previous session when starting a new session.
3. **Create `ScreenSizeResolverTest.kt`** (`app/src/test/java/app/gamenative/utils/ScreenSizeResolverTest.kt`):
   - Test aspect ratio classification logic (4:3, 16:10, 16:9).
4. **Create `EventDispatcherTest.kt`** (`app/src/test/java/app/gamenative/events/EventDispatcherTest.kt`):
   - Test `on`, `once`, `off`, `emit`, `emitJava`, and thread-safe listener iteration.
5. **Update `AppUtilsEntryPointTest.kt`**:
   - Assert `gameSessionManager()`, `screenSizeResolver()`, and `eventDispatcher()` resolve non-null.

#### Phase 3: PluviaApp Companion Delegation & Call Site Refactoring
1. **Update `PluviaApp.companion`**:
   - Redirect static properties to `AppUtilsEntryPoint.get(instance).gameSessionManager().currentRuntime`.
   - Redirect `getDefaultScreenSize()` to `AppUtilsEntryPoint.get(instance).screenSizeResolver().getDefaultScreenSize()`.
   - Redirect `shutdownEnvironment()` to `AppUtilsEntryPoint.get(instance).gameSessionManager().endSession()`.
2. **Refactor Out-of-Session Callers**:
   - `MainActivity.kt`: Inject `GameSessionManager` or resolve via `appUtilsEntryPoint()`. Replace `PluviaApp.xEnvironment == null` with `!gameSessionManager.isSessionRunning`, `PluviaApp.shutdownEnvironment()` with `gameSessionManager.endSession()`, `PluviaApp.isNeverSuspendMode()` with `gameSessionManager.currentRuntime?.isNeverSuspendMode() == true`.
   - `ImmersiveXrActivity.kt`: Inject/resolve `GameSessionManager`. Replace `PluviaApp.xServerView` with `gameSessionManager.currentRuntime?.xServerView`.
   - `SteamManager.kt`: Inject `GameSessionManager` (or `Provider<GameSessionManager>`). Replace `PluviaApp.xEnvironment != null` with `gameSessionManager.isSessionRunning`.
   - `PluviaMain.kt`: Replace `PluviaApp.xEnvironment == null` with `gameSessionManager.isSessionRunning == false`.
3. **Refactor In-Session Callers (`XServerScreen.kt` & `RadialMenuCoordinator.kt`)**:
   - At container launch, call `val session = gameSessionManager.startSession(info)`.
   - Bind views directly to `session.runtime`: `runtime.xServerView = xServerView`, `runtime.touchpadView = touchpadView`, `runtime.inputControlsView = icView`, `runtime.inputControlsManager = manager`, `runtime.xEnvironment = env`.
   - Replace in-screen accesses from `PluviaApp.<view>` to `runtime.<view>`.
4. **Refactor Utility Callers**:
   - `ContainerData.kt`, `IntentLaunchManager.kt`, `XServerState.kt`, `DefaultContainerPreferences.kt`: Replace `PluviaApp.getDefaultScreenSize()` with `ScreenSizeResolver`.
5. **Remove Unused Imports**:
   - Delete `import app.gamenative.PluviaApp;` from `GlibcProgramLauncherComponent.java` and `BionicProgramLauncherComponent.java`.

---

## 5. Verification Method

### 5.1 Verification Commands
1. **Compilation Verification**:
   ```bash
   ./gradlew compileModernDebugKotlin
   ```
   Must exit with code 0 without any unresolved reference or Dagger graph errors.

2. **Unit Test Verification**:
   ```bash
   ./gradlew :app:testModernDebugUnitTest
   ```
   Must pass 100% of tests including all new tests in `GameSessionRuntimeTest`, `DefaultGameSessionManagerTest`, `ScreenSizeResolverTest`, `EventDispatcherTest`, and updated `AppUtilsEntryPointTest`.

### 5.2 Forensic Code Inspection
1. **Zero Stateful Companion Leaks**:
   Verify `PluviaApp.companion` no longer holds mutable active session state (`xEnvironment`, `xServerView`, `inputControlsView`, `touchpadView`, etc.) as raw static variables.
2. **Zero `EntryPointAccessors.fromApplication` Escape Hatches in Target Classes**:
   Verify `GameSessionRuntime`, `DefaultGameSessionManager`, and `ScreenSizeResolver` do NOT use `EntryPointAccessors` or `PreferencesEntryPoint`.
3. **Dependency Injection Integrity**:
   Verify `GameSessionRuntime` is `@GameSessionScoped class GameSessionRuntime @Inject constructor(...)` and `DefaultGameSessionManager` is `@Singleton class DefaultGameSessionManager @Inject constructor(...)`.

### 5.3 Invalidation Conditions
- Any occurrence where `GameSessionRuntime` is retained after `endSession()`, preventing garbage collection of native views.
- Dagger circular dependency between `SteamManager` and `DefaultGameSessionManager`. (Prevented by using `Provider<SteamManager>` inside `GameSessionRuntime`).
- Any regression breaking on-screen touch controls, virtual gamepad editor, or radial menu gesture routing during `XServerScreen` execution.
