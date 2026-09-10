# Handoff Report: GameSession Architecture Design for Milestone 5 (Group 6: PluviaApp Session Extraction)

**Author**: `explorer_m5_2`  
**Date**: 2026-09-05  
**Mission**: Investigate and design the GameSession architecture (`@GameSessionScoped`, `GameSessionRuntime`, `GameSessionComponent`, `GameSessionManager`) to eradicate static companion state and escape hatches from `PluviaApp`.

---

## Executive Summary

Currently, `PluviaApp.companion` serves as a mutable dumping ground holding game-playing runtime state (`xEnvironment`, `xServerView`, `touchpadView`, `inputControlsView`, `inputControlsManager`, `radialMenuCoordinator`, `achievementWatcher`, `isOverlayPaused`, `activeSuspendPolicy`, `isActivityInForeground`). This leaks memory between game sessions (noted directly in `PluviaApp.kt:232`: `// TODO: find a way to make this saveable, this is terrible (leak that memory baby)`), creates hidden coupling, and forces callers to communicate via static references instead of dependency injection.

This report establishes the complete architecture for **Milestone 5**:
1. **Dagger Hilt Subcomponent `@DefineComponent`**: `@GameSessionScoped` subcomponent child of `SingletonComponent` whose lifetime matches the active game session.
2. **`GameSessionRuntime`**: The central `@GameSessionScoped` class holding all in-game session state, lifecycle methods (`startSession`, `pauseSession`, `resumeSession`, `stopSession`, `shutdownEnvironment`), UI view references, and suspend policy control.
3. **`GameSessionManager` / `DefaultGameSessionManager`**: The thread-safe `@Singleton` lifecycle coordinator managing session creation, reactive observation via `StateFlow<ActiveGameSession?>`, and clean teardown.
4. **Global Utility Extraction**: Migration of `PluviaApp.getDefaultScreenSize()` to an injectable `@Singleton class ScreenSizeResolver` and Hilt provision of `EventDispatcher`.
5. **Clean Escape Hatch Eradication**: Direct injection of `Provider<SteamManager>` into `GameSessionRuntime`, eliminating all static `SteamService.*` calls from teardown routines.

---

## 1. Observation

### 1.1 Existing Files in `app/src/main/java/app/gamenative/core/runtime/`
A preliminary skeleton of the runtime package was initiated in earlier refactoring phases:
- `GameSessionScope.kt` (lines 1–12):
  Declares `@Scope @Retention(AnnotationRetention.RUNTIME) annotation class GameSessionScoped`.
- `ActiveGameSessionInfo.kt` (lines 1–15):
  Defines metadata: `appId: String`, `title: String`, `source: GameSource`, `containerId: String`, `startTimeMillis: Long`.
- `GameSessionComponent.kt` (lines 1–21):
  Defines Hilt subcomponent:
  ```kotlin
  @GameSessionScoped
  @DefineComponent(parent = SingletonComponent::class)
  interface GameSessionComponent {
      @DefineComponent.Builder
      interface Builder {
          fun setSessionInfo(@BindsInstance sessionInfo: ActiveGameSessionInfo): Builder
          fun build(): GameSessionComponent
      }
  }
  ```
- `ActiveGameSession.kt` (lines 10–27):
  Encapsulates `info: ActiveGameSessionInfo`, `component: GameSessionComponent`, `sessionScope: CoroutineScope`, and `onTeardown: suspend () -> Unit`.
  *Observation*: Currently lacks a reference to `GameSessionRuntime`.
- `GameSessionEntryPoint.kt` (lines 12–19):
  ```kotlin
  @EntryPoint
  @InstallIn(GameSessionComponent::class)
  interface GameSessionEntryPoint {
      fun sessionInfo(): ActiveGameSessionInfo
      @GameSessionCoroutineScope
      fun sessionScope(): CoroutineScope
  }
  ```
  *Observation*: Lacks `fun gameSessionRuntime(): GameSessionRuntime`.
- `GameSessionModule.kt` (lines 13–24):
  Provides `@GameSessionScoped @GameSessionCoroutineScope fun provideGameSessionCoroutineScope(...)`.
- `GameSessionManager.kt` (lines 8–24):
  Defines interface with `val activeSession: StateFlow<ActiveGameSession?>`, `isSessionRunning`, `startSession(info, onTeardown)`, `endSession()`, `getActiveSessionInfo()`.
- `DefaultGameSessionManager.kt` (lines 16–84):
  `@Singleton` implementing `GameSessionManager` using `sessionMutex = Mutex()`, injecting `Provider<GameSessionComponent.Builder>`.
- `RuntimeModule.kt` (lines 9–19):
  Binds `DefaultGameSessionManager` to `GameSessionManager` in `SingletonComponent`.

### 1.2 State & Methods in `PluviaApp.companion` (`PluviaApp.kt:224-352`)
Direct observation of `PluviaApp.kt` companion object reveals:
1. **Global Utilities (Non-Session Scoped)**:
   - Line 226: `val events: EventDispatcher = EventDispatcher()` (used in ~200 files).
   - Line 227: `internal var onDestinationChangedListener: NavChangedListener? = null`.
   - Line 229: `lateinit var instance: PluviaApp`.
   - Lines 302–351: `fun getDefaultScreenSize(): String` (computes aspect ratio via `DisplayManager` / `DisplayMetrics` / `Display.mode`).
2. **Active Game Session State (Session Scoped)**:
   - Line 233: `internal var xEnvironment: XEnvironment? = null`
   - Line 234: `internal var xServerView: XServerRendererView? = null`
   - Line 235: `var inputControlsView: InputControlsView? = null`
   - Line 236: `var inputControlsManager: InputControlsManager? = null`
   - Line 237: `var touchpadView: TouchpadView? = null`
   - Line 238: `var radialMenuCoordinator: RadialMenuCoordinator? = null`
   - Line 239: `var achievementWatcher: app.gamenative.service.AchievementWatcher? = null`
   - Line 241: `var isOverlayPaused by mutableStateOf(false)`
   - Line 243: `@Volatile var isActivityInForeground: Boolean = true`
   - Line 246: `var activeSuspendPolicy: String = Container.SUSPEND_POLICY_MANUAL; private set`
   - Line 248: `private var hasInitializedSuspendPolicyState: Boolean = false`
3. **Session Lifecycle & Teardown Methods**:
   - Lines 250–253: `fun setActiveSuspendPolicy(policy: String)`
   - Lines 259–288: `fun shutdownEnvironment()`:
     ```kotlin
     runCatching { achievementWatcher?.stop() }
     runCatching { SteamService.clearCachedAchievements() }
     runCatching { touchpadView?.releasePointerCapture() }
     runCatching { radialMenuCoordinator?.detach() }
     runCatching { env?.stopEnvironmentComponents() }
     PowerManager.stop()
     xEnvironment = null
     inputControlsView = null
     inputControlsManager = null
     touchpadView = null
     radialMenuCoordinator = null
     achievementWatcher = null
     ActiveGameRegistry.clear()
     SteamService.keepAlive = false
     SteamService.clearPlayingConflict()
     clearActiveSuspendState()
     ```
   - Lines 290–300: `clearActiveSuspendState()`, `hasValidSuspendPolicyState()`, `isNeverSuspendMode()`, `isManualSuspendMode()`.

### 1.3 Call Sites of Session State Across the Codebase
- **`XServerScreen.kt`**:
  - Sets up and stores session state:
    - Line 1912: `PluviaApp.touchpadView = TouchpadView(...)`
    - Line 2233: `PluviaApp.xEnvironment = setupXEnvironment(...)`
    - Line 2297: `PluviaApp.xServerView = xServerView`
    - Line 2308: `PluviaApp.inputControlsManager = InputControlsManager(context)`
    - Line 2396: `PluviaApp.inputControlsView = icView`
    - Line 4076: `PluviaApp.achievementWatcher = AchievementWatcher(...)`
  - Controls pause & resume:
    - Lines 808, 822, 828, 836, 1331, 2272: `PluviaApp.xEnvironment?.onPause()`, `onResume()`, `resumeGameProcesses()`
    - Lines 800, 809, 813, 827, 1330, 1462, 1480, 1587, 2275, 2763: reads/writes `PluviaApp.isOverlayPaused`
  - Exiting and Teardown:
    - Line 4583: `PluviaApp.shutdownEnvironment()`
- **`MainActivity.kt`**:
  - Lines 209–211: If `SteamService.keepAlive && PluviaApp.xEnvironment == null`, calls `PluviaApp.shutdownEnvironment()`.
  - Line 383: On activity recreation/intent cleanup, calls `PluviaApp.shutdownEnvironment()`.
  - Lines 428–461 (`onResume`): Sets `PluviaApp.isActivityInForeground = true`; checks `PluviaApp.isNeverSuspendMode()` and `PluviaApp.isOverlayPaused`, calling `PluviaApp.xEnvironment?.onResume()`.
  - Lines 487–496 (`onPause`): Sets `PluviaApp.isActivityInForeground = false`; pauses `PluviaApp.xEnvironment`.
- **`ImmersiveXrActivity.kt`**:
  - Lines 251, 419, 431: Reads/writes `PluviaApp.isActivityInForeground`.
  - Lines 294, 316, 499, 1050, 1120–1143: Reads `PluviaApp.xServerView`.
  - Lines 420–449: Inspects `PluviaApp.xEnvironment`, `isNeverSuspendMode()`, calls `onResume()`, `onPause()`, and `PluviaApp.shutdownEnvironment()`.
  - Lines 524, 546, 931: Inspects `PluviaApp.isOverlayPaused`.
- **`SteamManager.kt`**:
  - Line 1518: `if (PluviaApp.xEnvironment != null)` to check whether a game is active when dealing with `EResult.LoggedInElsewhere`.
- **`RadialMenuCoordinator.kt`**:
  - Lines 55, 68: Reads and assigns `PluviaApp.radialMenuCoordinator`.
  - Lines 67, 564, 609: Reads `PluviaApp.touchpadView` and `PluviaApp.xServerView`.

### 1.4 Existing Tests and Test Utilities
- `app/src/test/java/app/gamenative/core/runtime/GameSessionManagerTest.kt`: Tests session creation and termination transitions using `FakeGameSessionManager`.
- `app/src/test/java/app/gamenative/testutil/FakeGameSessionManager.kt`: Implements fake session lifecycle without Hilt.
- `app/src/test/java/app/gamenative/di/AppUtilsEntryPointTest.kt`: Tests polymorphic access to singletons via `AppUtilsEntryPoint`.

---

## 2. Logic Chain

### 2.1 Why Dagger Hilt `@DefineComponent` Fits Best
1. **Subcomponent Lifecycle Guarantee**:
   In Android, game sessions are ephemeral. Using a subcomponent with `@DefineComponent(parent = SingletonComponent::class)` guarantees that all `@GameSessionScoped` dependencies (including `GameSessionRuntime`, session coroutine scopes, and attached views) have their lifecycles strictly bounded to the game session.
2. **Access to Parent Singleton Graph**:
   Because `parent = SingletonComponent::class`, all singletons (`SteamManager`, `ContainerManager`, `AuthPreferences`, `DownloadPreferences`, `EventDispatcher`, `CoroutineDispatcher`, etc.) are seamlessly injectable into `@GameSessionScoped` classes without manually forwarding constructor parameters.
3. **Deterministic Memory Reclamation (Fixing the Leak)**:
   When `GameSessionManager.endSession()` sets `_activeSession.value = null` and clears internal references, the entire subcomponent instance is dereferenced. The GC can reclaim all large native buffers, X11 renderer surfaces, and UI references. This directly resolves `PluviaApp.kt:232`.
4. **Contrasted with Pure Manual Manager**:
   A pure manual manager without `@DefineComponent` would require writing assisted factories for `GameSessionRuntime` and manually wiring every future session-scoped class. `@DefineComponent` provides declarative Hilt injection with minimal boilerplate.

### 2.2 Lifecycle State Transitions
The game session lifecycle is governed by a clear, thread-safe state machine:
```
               [IDLE (activeSession == null)]
                             │
            startSession(ActiveGameSessionInfo)
                             │
                             ▼
                 [BUILD SUBCOMPONENT]
            (GameSessionComponent created)
            (GameSessionRuntime instantiated)
            (GameSessionScope launched)
                             │
                             ▼
                 [RUNNING / ACTIVE]
        (Views attached: xEnvironment, touchpadView, etc.)
               │                         ▲
         pauseSession()            resumeSession()
               ▼                         │
                 [PAUSED / SUSPENDED]
            (Overlay visible / App backgrounded)
                             │
         endSession() / shutdownEnvironment()
                             │
                             ▼
                  [TEARDOWN & CLEANUP]
         (AchievementWatcher stopped)
         (SteamManager cache/conflicts cleared)
         (Pointer capture released, views detached)
         (xEnvironment components stopped)
         (PowerManager stopped, Registry cleared)
         (sessionScope.cancel())
                             │
                             ▼
               [IDLE (activeSession == null)]
               (Subcomponent garbage collected)
```

### 2.3 Thread Safety and Concurrency Model
- **`DefaultGameSessionManager`**:
  - Uses `sessionMutex = Mutex()` for atomic session creation and termination.
  - Exposes `_activeSession` via `StateFlow<ActiveGameSession?>` for lock-free, reactive observation.
  - If a new session starts while an old one is still active, it atomically terminates the old session before building the new subcomponent.
- **`ActiveGameSession`**:
  - Uses `@Volatile var isClosed: Boolean` to guarantee idempotent termination (`if (isClosed) return`).
- **`GameSessionRuntime`**:
  - UI view attachments (`touchpadView`, `inputControlsView`, etc.) are called from the Android Main thread.
  - `isActivityInForeground` is `@Volatile`.
  - `isOverlayPaused` is backed by `mutableStateOf(false)`, allowing Compose to trigger recomposition automatically while remaining readable/writeable by non-Compose threads.
  - `shutdownEnvironment()` wraps each individual cleanup step in `runCatching { ... }` so a failure in one component does not abort subsequent teardown steps.

### 2.4 Eradication of Escape Hatches & Service Locators
- **Static `SteamService` calls removed**:
  `shutdownEnvironment()` previously called `SteamService.clearCachedAchievements()`, `SteamService.keepAlive = false`, and `SteamService.clearPlayingConflict()`.
  In `GameSessionRuntime`, we inject `Provider<SteamManager>` directly. All three operations are called on the injected `SteamManager` instance.
  Using `Provider<SteamManager>` prevents circular dependency between `SteamManager` and `GameSessionManager` / `GameSessionRuntime`.
- **`Context` eliminated from `GameSessionRuntime` constructor**:
  `GameSessionRuntime` does not require `Context`. All required metadata comes from `ActiveGameSessionInfo`, and all singletons come from Hilt.
- **`PluviaApp.getDefaultScreenSize()` extracted**:
  Moved into `@Singleton class ScreenSizeResolver @Inject constructor(@ApplicationContext private val context: Context)`. Call sites can inject `ScreenSizeResolver` directly.
- **`AppUtilsEntryPoint` integration**:
  Composable functions (such as `XServerScreen`) can access `gameSessionManager()` through `context.appUtilsEntryPoint().gameSessionManager()`.

---

## 3. Architecture Specification & Implementation Contracts

### 3.1 Scope & Component (`GameSessionScope.kt` & `GameSessionComponent.kt`)
Existing declarations in `app/src/main/java/app/gamenative/core/runtime/`:
```kotlin
package app.gamenative.core.runtime

import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class GameSessionScoped
```

```kotlin
package app.gamenative.core.runtime

import dagger.BindsInstance
import dagger.hilt.DefineComponent
import dagger.hilt.components.SingletonComponent

@GameSessionScoped
@DefineComponent(parent = SingletonComponent::class)
interface GameSessionComponent {

    @DefineComponent.Builder
    interface Builder {
        fun setSessionInfo(@BindsInstance sessionInfo: ActiveGameSessionInfo): Builder
        fun build(): GameSessionComponent
    }
}
```

### 3.2 Component Entry Point (`GameSessionEntryPoint.kt`)
Updated to expose `gameSessionRuntime`:
```kotlin
package app.gamenative.core.runtime

import app.gamenative.core.coroutines.GameSessionCoroutineScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import kotlinx.coroutines.CoroutineScope

@EntryPoint
@InstallIn(GameSessionComponent::class)
interface GameSessionEntryPoint {
    fun sessionInfo(): ActiveGameSessionInfo
    
    @GameSessionCoroutineScope
    fun sessionScope(): CoroutineScope

    fun gameSessionRuntime(): GameSessionRuntime
}
```

### 3.3 Active Session Handle (`ActiveGameSession.kt`)
Holds reference to `GameSessionRuntime`:
```kotlin
package app.gamenative.core.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

class ActiveGameSession(
    val info: ActiveGameSessionInfo,
    val component: GameSessionComponent,
    val sessionScope: CoroutineScope,
    val runtime: GameSessionRuntime,
    private val onTeardown: suspend () -> Unit = {},
) {

    @Volatile
    var isClosed: Boolean = false
        private set

    suspend fun terminate() {
        if (isClosed) return
        isClosed = true
        sessionScope.cancel()
        runtime.shutdownEnvironment()
        onTeardown()
    }
}
```

### 3.4 Active Game Session Runtime (`GameSessionRuntime.kt`)
New file in `app/src/main/java/app/gamenative/core/runtime/GameSessionRuntime.kt`:
```kotlin
package app.gamenative.core.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gamenative.core.coroutines.GameSessionCoroutineScope
import app.gamenative.data.GameSource
import app.gamenative.powercontrol.PowerManager
import app.gamenative.service.AchievementWatcher
import app.gamenative.service.ActiveGameRegistry
import app.gamenative.service.SteamManager
import app.gamenative.ui.screen.xserver.RadialMenuCoordinator
import com.winlator.container.Container
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.InputControlsView
import com.winlator.widget.TouchpadView
import com.winlator.widget.XServerRendererView
import com.winlator.xenvironment.XEnvironment
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

/**
 * Scoped runtime holder for the active game playing session.
 * Replaces static fields in [app.gamenative.PluviaApp.companion].
 */
@GameSessionScoped
class GameSessionRuntime @Inject constructor(
    val sessionInfo: ActiveGameSessionInfo,
    private val steamManagerProvider: Provider<SteamManager>,
    @GameSessionCoroutineScope private val sessionScope: CoroutineScope,
) {

    // --- Metadata Accessors ---
    val appId: String get() = sessionInfo.appId
    val title: String get() = sessionInfo.title
    val source: GameSource get() = sessionInfo.source
    val containerId: String get() = sessionInfo.containerId
    val startTimeMillis: Long get() = sessionInfo.startTimeMillis

    // --- Environment & View References ---
    var xEnvironment: XEnvironment? = null
    var xServerView: XServerRendererView? = null
    var inputControlsView: InputControlsView? = null
    var inputControlsManager: InputControlsManager? = null
    var touchpadView: TouchpadView? = null
    var radialMenuCoordinator: RadialMenuCoordinator? = null
    var achievementWatcher: AchievementWatcher? = null

    // --- Suspend & Overlay State ---
    var isOverlayPaused: Boolean by mutableStateOf(false)
    @Volatile
    var isActivityInForeground: Boolean = true

    var activeSuspendPolicy: String = Container.SUSPEND_POLICY_MANUAL
        private set

    private var hasInitializedSuspendPolicyState: Boolean = false

    fun setActiveSuspendPolicy(policy: String) {
        activeSuspendPolicy = Container.normalizeSuspendPolicy(policy)
        hasInitializedSuspendPolicyState = true
    }

    fun clearActiveSuspendState() {
        activeSuspendPolicy = Container.SUSPEND_POLICY_MANUAL
        isOverlayPaused = false
        hasInitializedSuspendPolicyState = false
    }

    fun hasValidSuspendPolicyState(): Boolean = hasInitializedSuspendPolicyState
    fun isNeverSuspendMode(): Boolean = activeSuspendPolicy.equals(Container.SUSPEND_POLICY_NEVER, ignoreCase = true)
    fun isManualSuspendMode(): Boolean = activeSuspendPolicy.equals(Container.SUSPEND_POLICY_MANUAL, ignoreCase = true)

    // --- Lifecycle Methods ---

    fun startSession(info: ActiveGameSessionInfo = sessionInfo) {
        Timber.i("GameSessionRuntime: Starting session for ${info.title} (${info.appId})")
        clearActiveSuspendState()
    }

    fun pauseSession() {
        if (isNeverSuspendMode()) {
            Timber.d("GameSessionRuntime: Skipping overlay suspend due to suspend policy=never")
            return
        }
        xEnvironment?.onPause()
        isOverlayPaused = true
        Timber.d("GameSessionRuntime: Session paused")
    }

    fun resumeSession() {
        if (!isOverlayPaused) return
        if (!isNeverSuspendMode()) {
            xEnvironment?.onResume()
        }
        isOverlayPaused = false
        Timber.d("GameSessionRuntime: Session resumed")
    }

    fun onActivityResume() {
        isActivityInForeground = true
        if (hasValidSuspendPolicyState() && !isNeverSuspendMode() && !isOverlayPaused) {
            xEnvironment?.onResume()
        }
    }

    fun onActivityPause() {
        isActivityInForeground = false
        if (!isNeverSuspendMode()) {
            xEnvironment?.onPause()
        }
    }

    fun stopSession() {
        shutdownEnvironment()
    }

    /**
     * Complete environment teardown — stops background watchers, frees views,
     * resets Steam session conflicts, and clears hardware power profiles.
     */
    fun shutdownEnvironment() {
        val env = xEnvironment
        Timber.i("GameSessionRuntime: shutdownEnvironment (env=${env != null})")

        // Per-step catch so failure in one teardown step doesn't abort the rest
        runCatching { achievementWatcher?.stop() }
            .onFailure { Timber.e(it, "shutdownEnvironment: achievementWatcher.stop") }

        runCatching { steamManagerProvider.get().clearCachedAchievements() }
            .onFailure { Timber.e(it, "shutdownEnvironment: clearCachedAchievements") }

        runCatching { touchpadView?.releasePointerCapture() }
            .onFailure { Timber.e(it, "shutdownEnvironment: releasePointerCapture") }

        runCatching { radialMenuCoordinator?.detach() }
            .onFailure { Timber.e(it, "shutdownEnvironment: radialMenuCoordinator.detach") }

        runCatching { env?.stopEnvironmentComponents() }
            .onFailure { Timber.e(it, "shutdownEnvironment: stopEnvironmentComponents") }

        // Stop performance driver
        PowerManager.stop()

        // Release references to allow GC
        xEnvironment = null
        xServerView = null
        inputControlsView = null
        inputControlsManager = null
        touchpadView = null
        radialMenuCoordinator = null
        achievementWatcher = null

        ActiveGameRegistry.clear()

        runCatching {
            val steamManager = steamManagerProvider.get()
            steamManager.keepAlive = false
            steamManager.clearPlayingConflict()
        }.onFailure { Timber.e(it, "shutdownEnvironment: steamManager reset") }

        clearActiveSuspendState()
    }
}
```

### 3.5 GameSessionManager Contracts (`GameSessionManager.kt` & `DefaultGameSessionManager.kt`)
Updated `GameSessionManager.kt`:
```kotlin
package app.gamenative.core.runtime

import kotlinx.coroutines.flow.StateFlow

/**
 * Controller managing the lifecycle of game playing sessions.
 */
interface GameSessionManager {
    /** Reactive flow emitting the current active session, or null if no session is running. */
    val activeSession: StateFlow<ActiveGameSession?>

    /** True if a game session is currently active. */
    val isSessionRunning: Boolean
        get() = activeSession.value != null

    /** Current active session runtime, or null if no session is running. */
    val currentRuntime: GameSessionRuntime?
        get() = activeSession.value?.runtime

    /** Starts a new game session, ending any existing active session first. */
    suspend fun startSession(
        info: ActiveGameSessionInfo,
        onTeardown: suspend () -> Unit = {},
    ): ActiveGameSession

    /** Terminates and cleans up the currently active session. */
    suspend fun endSession()

    /** Convenience accessor for current active session info. */
    fun getActiveSessionInfo(): ActiveGameSessionInfo? = activeSession.value?.info
}
```

Updated `DefaultGameSessionManager.kt`:
```kotlin
package app.gamenative.core.runtime

import app.gamenative.core.coroutines.ApplicationScope
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class DefaultGameSessionManager @Inject constructor(
    private val componentBuilderProvider: Provider<GameSessionComponent.Builder>,
    @ApplicationScope private val appScope: CoroutineScope,
) : GameSessionManager {

    private val _activeSession = MutableStateFlow<ActiveGameSession?>(null)
    override val activeSession: StateFlow<ActiveGameSession?> = _activeSession.asStateFlow()

    private val sessionMutex = Mutex()

    init {
        appScope.launch {
            cleanupCrashedSessions()
        }
    }

    private suspend fun cleanupCrashedSessions() {
        sessionMutex.withLock {
            Timber.d("Checking for crashed sessions to clean up...")
        }
    }

    override suspend fun startSession(
        info: ActiveGameSessionInfo,
        onTeardown: suspend () -> Unit,
    ): ActiveGameSession {
        return sessionMutex.withLock {
            _activeSession.value?.let { existing ->
                Timber.w("Ending previous active session for ${existing.info.appId} before starting ${info.appId}")
                existing.terminate()
            }

            val component = componentBuilderProvider.get()
                .setSessionInfo(info)
                .build()

            val entryPoint = EntryPoints.get(component, GameSessionEntryPoint::class.java)
            val sessionScope = entryPoint.sessionScope()
            val runtime = entryPoint.gameSessionRuntime()
            runtime.startSession(info)

            var sessionRef: ActiveGameSession? = null
            val session = ActiveGameSession(
                info = info,
                component = component,
                sessionScope = sessionScope,
                runtime = runtime,
                onTeardown = {
                    sessionRef?.let { _activeSession.compareAndSet(it, null) }
                    onTeardown()
                },
            )
            sessionRef = session

            _activeSession.value = session
            Timber.i("Started new game session: ${info.title} (${info.appId}, source=${info.source})")
            session
        }
    }

    override suspend fun endSession() {
        sessionMutex.withLock {
            val current = _activeSession.value
            if (current != null) {
                Timber.i("Ending active game session: ${current.info.title} (${current.info.appId})")
                _activeSession.value = null
                current.terminate()
            }
        }
    }
}
```

### 3.6 Global Utilities: `ScreenSizeResolver` & `EventDispatcher`
1. **`ScreenSizeResolver.kt`** in `app/src/main/java/app/gamenative/utils/ScreenSizeResolver.kt`:
```kotlin
package app.gamenative.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import com.winlator.container.Container
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenSizeResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cachedDefaultScreenSize: String? = null

    fun getDefaultScreenSize(): String {
        cachedDefaultScreenSize?.let { return it }

        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            if (display != null) {
                val width: Int
                val height: Int

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val mode = display.mode
                    width = mode.physicalWidth
                    height = mode.physicalHeight
                } else {
                    val displayMetrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    display.getRealMetrics(displayMetrics)
                    width = displayMetrics.widthPixels
                    height = displayMetrics.heightPixels
                }

                val aspectRatio = maxOf(width, height).toFloat() / minOf(width, height).toFloat()

                val result = when {
                    aspectRatio < 1.5f -> Container.DEFAULT_SCREEN_SIZE_4_3
                    aspectRatio < 1.7f -> Container.DEFAULT_SCREEN_SIZE_16_10
                    else -> Container.DEFAULT_SCREEN_SIZE_16_9
                }
                cachedDefaultScreenSize = result
                result
            } else {
                val fallback = Container.DEFAULT_SCREEN_SIZE_16_9
                cachedDefaultScreenSize = fallback
                fallback
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get device screen size")
            val fallback = Container.DEFAULT_SCREEN_SIZE_16_9
            cachedDefaultScreenSize = fallback
            fallback
        }
    }
}
```

2. **`EventsModule.kt`** in `app/src/main/java/app/gamenative/di/EventsModule.kt`:
```kotlin
package app.gamenative.di

import app.gamenative.events.EventDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EventsModule {

    @Provides
    @Singleton
    fun provideEventDispatcher(): EventDispatcher = EventDispatcher()
}
```

3. **`AppUtilsEntryPoint.kt` Addition**:
```kotlin
    fun gameSessionManager(): GameSessionManager
    fun screenSizeResolver(): ScreenSizeResolver
    fun eventDispatcher(): EventDispatcher
```

### 3.7 Backward Compatibility Bridge in `PluviaApp.companion`
To allow incremental refactoring without breaking unaffected modules during intermediate commits, `PluviaApp.companion` can bridge accessors:
```kotlin
// Bridge delegators during M5 refactoring:
internal var xEnvironment: XEnvironment?
    get() = (instance.applicationContext as? Context)?.let { AppUtilsEntryPoint.get(it).gameSessionManager().currentRuntime?.xEnvironment }
    set(value) { (instance.applicationContext as? Context)?.let { AppUtilsEntryPoint.get(it).gameSessionManager().currentRuntime?.xEnvironment = value } }

fun shutdownEnvironment() {
    (instance.applicationContext as? Context)?.let {
        AppUtilsEntryPoint.get(it).gameSessionManager().currentRuntime?.shutdownEnvironment()
    }
}

fun getDefaultScreenSize(): String =
    (instance.applicationContext as? Context)?.let {
        AppUtilsEntryPoint.get(it).screenSizeResolver().getDefaultScreenSize()
    } ?: Container.DEFAULT_SCREEN_SIZE_16_9
```

---

## 4. Caveats

1. **Activity Context Leak Avoidance**:
   `XServerRendererView`, `TouchpadView`, and `InputControlsView` contain references to the hosting Activity `Context`. Storing them in `GameSessionRuntime` is safe *only* because `GameSessionRuntime` is torn down and nulled whenever the game stops, and because `GameSessionComponent` is garbage-collected upon session end. However, if an Activity is destroyed without calling `endSession()`, `MainActivity.onDestroy()` or `ImmersiveXrActivity.onDestroy()` *must* invoke `gameSessionManager.currentRuntime?.shutdownEnvironment()`.
2. **Circular Dependencies**:
   `SteamManager` checks `gameSessionManager.isSessionRunning` when handling `LoggedInElsewhere`. In turn, `GameSessionRuntime` resets `SteamManager` achievements during `shutdownEnvironment()`. To break any construction-time circularity, `GameSessionRuntime` must inject `Provider<SteamManager>` rather than a direct reference.
3. **Compose UI State Observation**:
   `isOverlayPaused` in `GameSessionRuntime` uses `var isOverlayPaused by mutableStateOf(false)`. Since Compose state snapshots are thread-aware, reading/writing `isOverlayPaused` from background threads or UI threads is safe, but any composable reading `runtime.isOverlayPaused` will correctly trigger recomposition.
4. **Offline and Multi-container Scenarios**:
   GameNative operates on a single active container session model (enforced by `ActiveGameRegistry`). `DefaultGameSessionManager.startSession()` enforces this single-session invariant via `Mutex` by terminating any prior session.

---

## 5. Conclusion

1. **Architecture Completeness**:
   The design fully addresses Group 6 of R1: extracting `xEnvironment`, static UI views, coordinators, suspend state, and `shutdownEnvironment` from `PluviaApp.companion` into `@GameSessionScoped class GameSessionRuntime` bound to `@DefineComponent GameSessionComponent`.
2. **Actionable Implementation Sequence**:
   - **Step 1**: Create `ScreenSizeResolver` and `EventsModule`; add bindings to `AppUtilsEntryPoint`.
   - **Step 2**: Create `GameSessionRuntime`; update `GameSessionEntryPoint`, `ActiveGameSession`, `GameSessionManager`, and `DefaultGameSessionManager`.
   - **Step 3**: Update `FakeGameSessionManager` and unit tests.
   - **Step 4**: Refactor `PluviaApp.kt` companion object and update callers in `XServerScreen.kt`, `MainActivity.kt`, `ImmersiveXrActivity.kt`, and `SteamManager.kt`.
3. **Status**:
   Ready for Worker implementation in Milestone 5.

---

## 6. Verification Method

### 6.1 Independent Verification Commands
1. **Compilation Check**:
   ```bash
   ./gradlew compileModernDebugKotlin
   ```
   *Expected Result*: Clean compilation with 0 errors.
2. **Unit Test Suite**:
   ```bash
   ./gradlew :app:testModernDebugUnitTest
   ```
   *Expected Result*: All existing tests pass, plus new tests in `GameSessionManagerTest` and `GameSessionRuntimeTest`.
3. **Dagger Hilt Verification**:
   Verify that Dagger code generation generates `DaggerPluviaApp_HiltComponents.GameSessionComponentImpl` without binding missing errors.

### 6.2 File Inspection Checklist
- `app/src/main/java/app/gamenative/core/runtime/GameSessionRuntime.kt`: Exists, annotated with `@GameSessionScoped`, injects `Provider<SteamManager>` and `ActiveGameSessionInfo`.
- `app/src/main/java/app/gamenative/core/runtime/GameSessionEntryPoint.kt`: Declares `fun gameSessionRuntime(): GameSessionRuntime`.
- `app/src/main/java/app/gamenative/core/runtime/ActiveGameSession.kt`: Holds `val runtime: GameSessionRuntime` and calls `runtime.shutdownEnvironment()` on terminate.
- `app/src/main/java/app/gamenative/core/runtime/GameSessionManager.kt`: Exposes `val currentRuntime: GameSessionRuntime?`.
- `app/src/main/java/app/gamenative/utils/ScreenSizeResolver.kt`: Exists and is annotated with `@Singleton`.
- `app/src/main/java/app/gamenative/PluviaApp.kt`: Companion object has all static session fields removed or converted to bridge delegates.

### 6.3 Invalidation Conditions
- Any occurrence of `EntryPointAccessors.fromApplication` inside `GameSessionRuntime` or other converted domain classes.
- Any memory leak where `xEnvironment` or `xServerView` references persist after `GameSessionManager.endSession()`.
- Dagger graph cycle between `SteamManager` and `DefaultGameSessionManager` (prevented by `Provider<SteamManager>`).
