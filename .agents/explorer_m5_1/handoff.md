# Handoff Report: PluviaApp Companion & Session State Investigation (Milestone 5)

**Agent**: explorer_m5_1 (Codebase Researcher / Explorer)  
**Parent**: 4e0c7245-24ab-4ad8-b0b1-6787f82b4eba  
**Date**: 2026-09-05T05:38:00Z  
**Scope**: Group 6: PluviaApp Session Extraction  

---

## 1. Observation

### 1.1 Complete Inventory of `PluviaApp.companion`
In `app/src/main/java/app/gamenative/PluviaApp.kt` (lines 224–352), the companion object defines 20 members (13 properties and 7 methods):

```kotlin
224: companion object {
225:     @JvmField
226:     val events: EventDispatcher = EventDispatcher()
227:     internal var onDestinationChangedListener: NavChangedListener? = null
228: 
229:     lateinit var instance: PluviaApp
230:     private var cachedDefaultScreenSize: String? = null
231: 
232:     // TODO: find a way to make this saveable, this is terrible (leak that memory baby)
233:     internal var xEnvironment: XEnvironment? = null
234:     internal var xServerView: XServerRendererView? = null
235:     var inputControlsView: InputControlsView? = null
236:     var inputControlsManager: InputControlsManager? = null
237:     var touchpadView: TouchpadView? = null
238:     var radialMenuCoordinator: RadialMenuCoordinator? = null
239:     var achievementWatcher: app.gamenative.service.AchievementWatcher? = null
240: 
241:     var isOverlayPaused by mutableStateOf(false)
242:     @Volatile
243:     var isActivityInForeground: Boolean = true
244: 
245:     // Active runtime suspend policy for the current in-game session.
246:     var activeSuspendPolicy: String = Container.SUSPEND_POLICY_MANUAL
247:         private set
248:     private var hasInitializedSuspendPolicyState: Boolean = false
249: 
250:     fun setActiveSuspendPolicy(policy: String) {
251:         activeSuspendPolicy = Container.normalizeSuspendPolicy(policy)
252:         hasInitializedSuspendPolicyState = true
253:     }
254: 
255:     /**
256:      * full environment teardown — shared by XServerScreen.exit() and
257:      * MainActivity.onDestroy fallback so both paths clean up identically
258:      */
259:     fun shutdownEnvironment() {
260:         val env = xEnvironment
261:         Timber.i("shutdownEnvironment: env=%s", env != null)
262: 
263:         // per-step catch so one failing teardown doesn't prevent the rest from running
264:         runCatching { achievementWatcher?.stop() }
265:             .onFailure { Timber.e(it, "shutdownEnvironment: achievementWatcher.stop") }
266:         runCatching { SteamService.clearCachedAchievements() }
267:             .onFailure { Timber.e(it, "shutdownEnvironment: clearCachedAchievements") }
268:         runCatching { touchpadView?.releasePointerCapture() }
269:             .onFailure { Timber.e(it, "shutdownEnvironment: releasePointerCapture") }
270:         runCatching { radialMenuCoordinator?.detach() }
271:             .onFailure { Timber.e(it, "shutdownEnvironment: radialMenuCoordinator.detach") }
272:         runCatching { env?.stopEnvironmentComponents() }
273:             .onFailure { Timber.e(it, "shutdownEnvironment: stopEnvironmentComponents") }
274: 
275:         // Stop performance driver
276:         PowerManager.stop()
277: 
278:         xEnvironment = null
279:         inputControlsView = null
280:         inputControlsManager = null
281:         touchpadView = null
282:         radialMenuCoordinator = null
283:         achievementWatcher = null
284:         ActiveGameRegistry.clear()
285:         SteamService.keepAlive = false
286:         SteamService.clearPlayingConflict()
287:         clearActiveSuspendState()
288:     }
289: 
290:     fun clearActiveSuspendState() {
291:         activeSuspendPolicy = Container.SUSPEND_POLICY_MANUAL
292:         isOverlayPaused = false
293:         hasInitializedSuspendPolicyState = false
294:     }
295: 
296:     fun hasValidSuspendPolicyState(): Boolean = hasInitializedSuspendPolicyState
297: 
298:     fun isNeverSuspendMode(): Boolean = activeSuspendPolicy.equals(Container.SUSPEND_POLICY_NEVER, ignoreCase = true)
299: 
300:     fun isManualSuspendMode(): Boolean = activeSuspendPolicy.equals(Container.SUSPEND_POLICY_MANUAL, ignoreCase = true)
301: 
302:     fun getDefaultScreenSize(): String { ... }
352: }
```

### 1.2 Call Sites by Member

#### A. `PluviaApp.xEnvironment`
- `app/gamenative/MainActivity.kt`:
  - Line 209: `if (SteamService.keepAlive && PluviaApp.xEnvironment == null) { PluviaApp.shutdownEnvironment() }` (detect and clean stale keepAlive session)
  - Line 428: `if (PluviaApp.xEnvironment == null) return false` (guard inside `hasReadyGameLifecycleState`)
  - Line 461: `PluviaApp.xEnvironment?.onResume()` (resume container processes in `onResume`)
  - Line 494: `PluviaApp.xEnvironment?.onPause()` (pause container processes in `onPause`)
- `app/gamenative/service/SteamManager.kt`:
  - Line 1518: `if (PluviaApp.xEnvironment != null)` (check if game is actively running when `EResult.LoggedInElsewhere` occurs)
- `app/gamenative/ui/screen/xserver/XServerScreen.kt`:
  - Line 808: `PluviaApp.xEnvironment?.onPause()`
  - Line 822: `PluviaApp.xEnvironment?.onResume()`
  - Line 828: `PluviaApp.xEnvironment?.onResume()`
  - Line 836: `PluviaApp.xEnvironment?.onResume()`
  - Line 1331: `PluviaApp.xEnvironment?.resumeGameProcesses()`
  - Line 1868: `PluviaApp.xEnvironment`
  - Line 2113: `if (PluviaApp.xEnvironment == null)`
  - Line 2233: `PluviaApp.xEnvironment = setupXEnvironment(...)` (session initialization)
  - Line 2272: `PluviaApp.xEnvironment?.onPause()`
  - Line 2285: `PluviaApp.xEnvironment?.stopEnvironmentComponents()` (cleanup on wine setup error)
  - Line 2289: `PluviaApp.xEnvironment = null`
- `app/gamenative/ui/screen/xr/ImmersiveXrActivity.kt`:
  - Line 420: `if (SteamService.keepAlive && PluviaApp.hasValidSuspendPolicyState() && PluviaApp.xEnvironment != null)`
  - Line 424: `PluviaApp.xEnvironment?.onResume()`
  - Line 440: `if (SteamService.keepAlive && PluviaApp.hasValidSuspendPolicyState() && PluviaApp.xEnvironment != null && !PluviaApp.isNeverSuspendMode())`
  - Line 442: `PluviaApp.xEnvironment?.onPause()`
- `app/gamenative/ui/PluviaMain.kt`:
  - Line 580: `else if (pending == null && PluviaApp.xEnvironment == null)` (navigate away from login after logon ended)
  - Line 1387: `if (shouldShowDialogs && !state.annoyingDialogShown && PluviaApp.xEnvironment == null && !SteamService.keepAlive ...)` (suppress dialogs while game is active)

#### B. `PluviaApp.xServerView`
- `app/gamenative/powercontrol/PowerManager.kt`:
  - Line 484: `val xServerView = PluviaApp.xServerView ?: return false` (frame rate limit configuration)
- `app/gamenative/ui/screen/xserver/XServerScreen.kt`:
  - Line 2297: `PluviaApp.xServerView = xServerView` (bind view on boot)
  - Line 2567-2568: `if (PluviaApp.xServerView === binding.xServerView) { PluviaApp.xServerView = null }` (unbind on view destruction)
  - Line 3175: `PluviaApp.xServerView?.getxServer()?.winHandler?.refreshControllerMappingsForHotplug()`
  - Line 3183: `PluviaApp.xServerView?.getRenderer()?.setCursorVisible(true)`
- `app/gamenative/ui/screen/xserver/RadialMenuCoordinator.kt`:
  - Line 564: `val winHandler = xServer.winHandler ?: PluviaApp.xServerView?.getxServer()?.winHandler`
  - Line 609: `val winHandler = xServer.winHandler ?: PluviaApp.xServerView?.getxServer()?.winHandler`
- `app/gamenative/ui/screen/xr/ImmersiveXrActivity.kt`:
  - Line 294: `directRenderBlockedByEffects = (PluviaApp.xServerView?.renderer as? com.winlator.renderer.VulkanRenderer)?.isEffectsRequireCompositor()`
  - Line 316: `val vulkanRenderer = PluviaApp.xServerView?.renderer as? com.winlator.renderer.VulkanRenderer`
  - Line 499: `val winHandler = PluviaApp.xServerView?.getxServer()?.winHandler`
  - Line 1050: `val actualRenderer = PluviaApp.xServerView?.renderer`
  - Line 1120: `(PluviaApp.xServerView?.renderer as? VulkanRenderer)?.setVulkanXrFrameBridge(null)`
  - Line 1124: `(PluviaApp.xServerView?.renderer as? GLRenderer)?.setXrFrameBridge(null)`
  - Line 1125: `PluviaApp.xServerView?.queueEvent { bridge.release() }`
  - Line 1136, 1265: `val surfaceView = PluviaApp.xServerView as? SurfaceView`
  - Line 1143: `PluviaApp.xServerView?.queueEvent { glBridge.ensureAllocated(width, height) }`

#### C. `PluviaApp.inputControlsView`
- `app/gamenative/ui/screen/xserver/XServerScreen.kt`:
  - 42 call sites in `XServerScreen.kt` (lines 1179, 1193, 1203, 1204, 1284, 1285, 1530, 1582, 1833, 2375, 2396, 2404, 2611, 2613, 2617, 2624, 2628, 2633, 2635, 2636, 2653, 2655, 2656, 2657, 2658, 2666, 2669, 2769, 2772, 2809, 2921, 2922, 3113, 3172, 3173, 3174, 3186, 3401, 3417, 3492, 3510, 3511, 3519, 3532, 3542, 3549).
  - Used strictly for on-screen touch controls overlay, profile swapping, touch event dispatch, element customization, and hotplug visibility.

#### D. `PluviaApp.inputControlsManager`
- `app/gamenative/ui/screen/xserver/XServerScreen.kt`:
  - Lines 958, 1108, 1146, 1246, 1270, 2308 (`PluviaApp.inputControlsManager = InputControlsManager(context)`), 2334, 2664, 2849, 3073, 3509, 3556.
- `app/gamenative/ui/screen/xserver/RadialMenuCoordinator.kt`:
  - Line 320: `val manager = PluviaApp.inputControlsManager ?: InputControlsManager(context).also { PluviaApp.inputControlsManager = it }`
  - Line 362: `val manager = PluviaApp.inputControlsManager ?: return null`

#### E. `PluviaApp.touchpadView`
- `app/gamenative/ui/screen/xserver/XServerScreen.kt`:
  - Lines 922, 923, 978, 979, 1132, 1226, 1230, 1370, 1371, 1443, 1444, 1587, 1838, 1912 (`PluviaApp.touchpadView = TouchpadView(...)`), 1913, 1914, 1921, 1922, 1937, 1939, 1951, 2131, 2133, 2136, 2331, 2431, 2449, 2463, 2541, 2793, 3154, 3177, 3178, 3179, 3180, 3182.
- `app/gamenative/ui/screen/xserver/RadialMenuCoordinator.kt`:
  - Line 67: `coordinator.bindTouchpadView(PluviaApp.touchpadView)`

#### F. `PluviaApp.radialMenuCoordinator`
- `app/gamenative/ui/screen/xserver/XServerScreen.kt`:
  - Lines 500 (`detach()`), 501 (`null`), 1180, 1298, 1821, 2367, 2369, 2402, 2925, 3111.
- `app/gamenative/ui/screen/xserver/RadialMenuCoordinator.kt`:
  - Line 55: `PluviaApp.radialMenuCoordinator?.detach()`
  - Line 68: `PluviaApp.radialMenuCoordinator = coordinator`

#### G. `PluviaApp.achievementWatcher`
- `app/gamenative/ui/screen/xserver/XServerScreen.kt`:
  - Line 4076: `PluviaApp.achievementWatcher = AchievementWatcher(...)`
- `app/gamenative/PluviaApp.kt`:
  - Lines 239, 264, 283: stopped and cleared during `shutdownEnvironment()`.

#### H. Suspend Policy & Overlay Pause State
- `PluviaApp.isOverlayPaused`:
  - `MainActivity.kt`: lines 455, 496
  - `ImmersiveXrActivity.kt`: lines 423, 524, 525, 546, 931
  - `XServerScreen.kt`: lines 800, 809, 813, 827, 834, 1330, 1462, 1480, 1587, 2275, 2763
- `PluviaApp.isActivityInForeground`:
  - `MainActivity.kt`: lines 438, 487
  - `ImmersiveXrActivity.kt`: lines 251, 419, 431
  - `XServerScreen.kt`: line 2271
- `PluviaApp.activeSuspendPolicy`, `setActiveSuspendPolicy`, `clearActiveSuspendState`, `hasValidSuspendPolicyState`, `isNeverSuspendMode`, `isManualSuspendMode`:
  - `MainActivity.kt`: lines 424, 452, 456, 490, 495
  - `ImmersiveXrActivity.kt`: lines 420, 422, 423, 439
  - `XServerScreen.kt`: lines 436, 1460

#### I. `PluviaApp.shutdownEnvironment()`
- `MainActivity.kt`: lines 211, 383
- `ImmersiveXrActivity.kt`: lines 438, 449
- `XServerScreen.kt`: line 4583

#### J. Global Utilities & Singletons
- `PluviaApp.getDefaultScreenSize()`:
  - `ContainerData.kt`: line 15
  - `IntentLaunchManager.kt`: line 205
  - `XServerState.kt`: line 14
  - `DefaultContainerPreferences.kt`: line 99
- `PluviaApp.instance`:
  - Service-locator escape hatches in: `LibraryItem.kt` (line 63), `ContainerUtils.kt` (line 1293), `SteamWishlistService.kt` (line 33), `SteamSaveTransfer.kt` (line 368), `SteamService.kt` (line 227), `GOGConstants.kt` (lines 129, 141), `EpicConstants.kt` (line 123), `AmazonConstants.kt` (line 62), `LibraryTab.kt` (line 106).
- `PluviaApp.onDestinationChangedListener`:
  - `PluviaMain.kt`: lines 678, 685, 690.
- `PluviaApp.events`:
  - 36 files across `app/gamenative` for Android and Steam events.

---

## 2. Logic Chain

### Step 2.1: Session State Separation
- **Observation 1.1**: The companion object of `PluviaApp` contains 11 mutable session-scoped fields: `xEnvironment`, `xServerView`, `inputControlsView`, `inputControlsManager`, `touchpadView`, `radialMenuCoordinator`, `achievementWatcher`, `isOverlayPaused`, `activeSuspendPolicy`, `hasInitializedSuspendPolicyState`, and `isActivityInForeground`.
- **Deduction**: These fields do not belong to the Android Application class. They are created when a container/game is booted and must be destroyed when the container/game stops. Keeping them in `PluviaApp.companion` creates memory leaks (explicitly acknowledged by comment in line 232: `"// TODO: find a way to make this saveable, this is terrible (leak that memory baby)"`) and prevents multi-session or cleanly testable lifecycle management.

### Step 2.2: Session vs Out-of-Session Callers
- **Observation 1.2 (B, C, D, E, F, G)**: `inputControlsView`, `touchpadView`, `radialMenuCoordinator`, `achievementWatcher`, and `inputControlsManager` are accessed almost exclusively within `XServerScreen.kt` and `RadialMenuCoordinator.kt`.
- **Observation 1.2 (A, H, I)**: `xEnvironment`, `shutdownEnvironment()`, and suspend policy methods are accessed both inside `XServerScreen.kt` (in-session) AND inside `MainActivity.kt`, `ImmersiveXrActivity.kt`, `SteamManager.kt`, and `PluviaMain.kt` (out-of-session).
- **Deduction**: 
  1. Callers inside `XServerScreen.kt` and `RadialMenuCoordinator.kt` are **In-Session**: they operate on the active running instance.
  2. Callers inside `MainActivity.kt`, `ImmersiveXrActivity.kt`, `SteamManager.kt`, and `PluviaMain.kt` are **Out-of-Session**: they need to inspect whether a session is running (`gameSessionManager.isSessionRunning`), signal pause/resume to the active session (`gameSessionManager.activeSession.value?.runtime?.onPause()`), or trigger teardown (`gameSessionManager.endSession()`).

### Step 2.3: Existing Infrastructure Alignment
- **Observation**: `app/src/main/java/app/gamenative/core/runtime/` already contains:
  - `GameSessionComponent` (child of `SingletonComponent`, annotated with `@GameSessionScoped`)
  - `GameSessionScope` (`@Scope annotation class GameSessionScoped`)
  - `GameSessionManager` (interface with `activeSession: StateFlow<ActiveGameSession?>`, `isSessionRunning`, `startSession`, `endSession`)
  - `DefaultGameSessionManager` (implements `GameSessionManager`)
  - `ActiveGameSession` (encapsulates `info`, `component`, `sessionScope`, `terminate()`)
  - `ActiveGameSessionInfo` (session metadata)
- **Deduction**: The subcomponent and manager infrastructure is already implemented and compiling! The missing piece for Milestone 5 is creating `@GameSessionScoped class GameSessionRuntime` bound to `GameSessionComponent`, exposing it through `GameSessionEntryPoint`, and routing callers from `PluviaApp.<static>` to `GameSessionRuntime` (for in-session callers) and `GameSessionManager` (for out-of-session callers).

---

## 3. Caveats

1. **Backwards Compatibility Bridge**: `XServerScreen.kt` has over 40 call sites touching `inputControlsView` and other views. To minimize churn and prevent runtime regressions while workers refactor call sites, `PluviaApp.companion` properties can temporarily delegate to `GameSessionManager.activeSession.value?.runtime` during intermediate steps, or callers can be updated directly to access the active session.
2. **`isActivityInForeground` Scope**: `isActivityInForeground` is an app/activity lifecycle property (modified by `MainActivity` and `ImmersiveXrActivity`), but read by `XServerScreen.kt:2271`. It can either remain on an app lifecycle holder or be tracked via `GameSessionRuntime.isActivityInForeground`.
3. **`events` & `ScreenSizeResolver` Scope**: `PluviaApp.events` is an app-wide event bus referenced across 36 files. Moving it to `@Singleton class EventDispatcher` can be done via dependency injection while keeping `PluviaApp.events` pointing to the singleton instance for static compatibility if necessary. Similarly, `ScreenSizeResolver` can encapsulate `getDefaultScreenSize()` logic.
4. **No other caveats.**

---

## 4. Conclusion & Actionable Extraction Targets

### 4.1 Target Specification: `GameSessionRuntime`
Extract the following fields and methods from `PluviaApp.companion` into a new class `app.gamenative.core.runtime.GameSessionRuntime`:

```kotlin
package app.gamenative.core.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gamenative.core.coroutines.GameSessionCoroutineScope
import app.gamenative.powercontrol.PowerManager
import app.gamenative.service.ActiveGameRegistry
import app.gamenative.service.SteamService
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

@GameSessionScoped
class GameSessionRuntime @Inject constructor(
    val sessionInfo: ActiveGameSessionInfo,
    @GameSessionCoroutineScope val sessionScope: CoroutineScope,
) {
    var xEnvironment: XEnvironment? = null
    var xServerView: XServerRendererView? = null
    var inputControlsView: InputControlsView? = null
    var inputControlsManager: InputControlsManager? = null
    var touchpadView: TouchpadView? = null
    var radialMenuCoordinator: RadialMenuCoordinator? = null
    var achievementWatcher: app.gamenative.service.AchievementWatcher? = null

    var isOverlayPaused: Boolean by mutableStateOf(false)
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

    fun onPause() {
        xEnvironment?.onPause()
    }

    fun onResume() {
        xEnvironment?.onResume()
    }

    fun resumeGameProcesses() {
        xEnvironment?.resumeGameProcesses()
    }

    fun shutdownEnvironment() {
        val env = xEnvironment
        Timber.i("GameSessionRuntime.shutdownEnvironment: env=%s", env != null)

        runCatching { achievementWatcher?.stop() }
            .onFailure { Timber.e(it, "shutdownEnvironment: achievementWatcher.stop") }
        runCatching { SteamService.clearCachedAchievements() }
            .onFailure { Timber.e(it, "shutdownEnvironment: clearCachedAchievements") }
        runCatching { touchpadView?.releasePointerCapture() }
            .onFailure { Timber.e(it, "shutdownEnvironment: releasePointerCapture") }
        runCatching { radialMenuCoordinator?.detach() }
            .onFailure { Timber.e(it, "shutdownEnvironment: radialMenuCoordinator.detach") }
        runCatching { env?.stopEnvironmentComponents() }
            .onFailure { Timber.e(it, "shutdownEnvironment: stopEnvironmentComponents") }

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
    }
}
```

### 4.2 EntryPoint & Session Wiring
1. Update `GameSessionEntryPoint.kt`:
```kotlin
@EntryPoint
@InstallIn(GameSessionComponent::class)
interface GameSessionEntryPoint {
    fun sessionInfo(): ActiveGameSessionInfo
    
    @GameSessionCoroutineScope
    fun sessionScope(): CoroutineScope

    fun sessionRuntime(): GameSessionRuntime
}
```
2. Update `ActiveGameSession.kt`:
```kotlin
val runtime: GameSessionRuntime
    get() = dagger.hilt.EntryPoints.get(component, GameSessionEntryPoint::class.java).sessionRuntime()
```
3. Update `GameSessionManager`:
Provide convenience accessor `val currentRuntime: GameSessionRuntime? get() = activeSession.value?.runtime`.

### 4.3 Call Site Migration Strategy

| File | Old Usage | New Refactored Usage | Category |
|---|---|---|---|
| `MainActivity.kt` | `PluviaApp.xEnvironment == null` | `!gameSessionManager.isSessionRunning` | Out-of-Session |
| `MainActivity.kt` | `PluviaApp.shutdownEnvironment()` | `gameSessionManager.endSession()` | Out-of-Session |
| `MainActivity.kt` | `PluviaApp.xEnvironment?.onResume()` / `onPause()` | `gameSessionManager.currentRuntime?.onResume()` / `onPause()` | Out-of-Session |
| `MainActivity.kt` | `PluviaApp.isNeverSuspendMode()` | `gameSessionManager.currentRuntime?.isNeverSuspendMode() == true` | Out-of-Session |
| `ImmersiveXrActivity.kt`| `PluviaApp.shutdownEnvironment()` | `gameSessionManager.endSession()` | Out-of-Session |
| `ImmersiveXrActivity.kt`| `PluviaApp.xServerView` | `gameSessionManager.currentRuntime?.xServerView` | Out-of-Session / XR Bridge |
| `SteamManager.kt` | `PluviaApp.xEnvironment != null` | `gameSessionManager.isSessionRunning` | Out-of-Session |
| `PluviaMain.kt` | `PluviaApp.xEnvironment == null` | `!gameSessionManager.isSessionRunning` | Out-of-Session |
| `PowerManager.kt` | `PluviaApp.xServerView` | `gameSessionManager.currentRuntime?.xServerView` | In-Session Throttling |
| `XServerScreen.kt` | `PluviaApp.xEnvironment = ...` | `runtime.xEnvironment = ...` | In-Session |
| `XServerScreen.kt` | `PluviaApp.xServerView = ...` | `runtime.xServerView = ...` | In-Session |
| `XServerScreen.kt` | `PluviaApp.touchpadView` | `runtime.touchpadView` | In-Session |
| `XServerScreen.kt` | `PluviaApp.inputControlsView` | `runtime.inputControlsView` | In-Session |
| `XServerScreen.kt` | `PluviaApp.radialMenuCoordinator`| `runtime.radialMenuCoordinator` | In-Session |
| `RadialMenuCoordinator.kt`| `PluviaApp.touchpadView`, `inputControlsManager` | `runtime.touchpadView`, `runtime.inputControlsManager` | In-Session |
| `ContainerData.kt`, `XServerState.kt` | `PluviaApp.getDefaultScreenSize()` | `ScreenSizeResolver.getDefaultScreenSize()` | Utilities |

---

## 5. Verification Method

1. **Verify Target Code Locations**:
   - Inspect `app/src/main/java/app/gamenative/PluviaApp.kt` lines 224–352 to confirm all fields and methods cataloged above.
   - Inspect `app/src/main/java/app/gamenative/core/runtime/` to confirm existing `GameSessionComponent`, `GameSessionManager`, `DefaultGameSessionManager`, `ActiveGameSession`, `GameSessionEntryPoint`.
2. **Verify Call Sites**:
   - Search for `PluviaApp.xEnvironment`, `PluviaApp.xServerView`, `PluviaApp.inputControlsView`, `PluviaApp.touchpadView`, `PluviaApp.radialMenuCoordinator`, `PluviaApp.shutdownEnvironment` to confirm 100% concordance with the mapping table.
3. **Compilation & Build Command**:
   - `./gradlew compileModernDebugKotlin`
   - `./gradlew :app:testModernDebugUnitTest`
4. **Invalidation Conditions**:
   - Any remaining static mutable session view or environment reference directly held in `PluviaApp.companion` without scoping to `GameSessionRuntime`.
   - Any compilation failure during DI component generation or call site migration.
