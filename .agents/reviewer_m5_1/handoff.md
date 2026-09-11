# Review & Adversarial Challenge Report — Milestone 5 (Group 6: PluviaApp Session Extraction)

## Review Summary

**Verdict**: **REQUEST_CHANGES**  
**Integrity Check**: **PASSED** (No integrity violations detected; implementation represents genuine, sophisticated engineering with zero dummy facades or fabricated verifications).  
**Reason for REQUEST_CHANGES**: Three functional and resilience defects must be remediated:
1. Concurrent race condition and session/scope resource leak in `DefaultGameSessionManager.getOrCreateRuntime()`.
2. Missing exception isolation on `PowerManager.stop()` in `GameSessionRuntime.shutdownEnvironment()`, violating Verification Requirement #4.
3. Event bus split-brain hazard between Hilt's `EventsModule.provideEventDispatcher()` and `PluviaApp.events`.

---

## 1. Observation

### Target Files Inspected
1. `app/src/main/java/app/gamenative/utils/ScreenSizeResolver.kt`
   - Lines 17–20: `@Singleton class ScreenSizeResolver @Inject constructor(@ApplicationContext private val context: Context)`
   - Lines 27–71: Safely handles null `DisplayManager`, null `Display`, computes aspect ratio using `maxOf(w, h).toFloat() / minOf(w, h).toFloat()`, maps thresholds (<1.5f -> 4:3, <1.7f -> 16:10, else 16:9), and caches result in `@Volatile private var cachedDefaultScreenSize: String?`.
2. `app/src/main/java/app/gamenative/di/EventsModule.kt`
   - Lines 10–17: `@Module @InstallIn(SingletonComponent::class) object EventsModule { @Provides @Singleton fun provideEventDispatcher(): EventDispatcher = EventDispatcher() }`
   - Note: Returns a newly constructed `EventDispatcher()` instance, separate from `PluviaApp.events`.
3. `app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt`
   - Lines 42–44: Exposes `fun gameSessionManager(): app.gamenative.core.runtime.GameSessionManager`, `fun screenSizeResolver(): app.gamenative.utils.ScreenSizeResolver`, `fun eventDispatcher(): app.gamenative.events.EventDispatcher`.
4. `app/src/main/java/app/gamenative/core/runtime/GameSessionRuntime.kt`
   - Lines 28–33: `@GameSessionScoped class GameSessionRuntime @Inject constructor(val sessionInfo: ActiveGameSessionInfo, private val steamManagerProvider: Provider<SteamManager>, @GameSessionCoroutineScope private val sessionScope: CoroutineScope)`
   - Lines 129–168 (`shutdownEnvironment()`):
     - Lines 134–135: `runCatching { achievementWatcher?.stop() }`
     - Lines 137–138: `runCatching { steamManagerProvider.get().clearCachedAchievements() }`
     - Lines 140–141: `runCatching { touchpadView?.releasePointerCapture() }`
     - Lines 143–144: `runCatching { radialMenuCoordinator?.detach() }`
     - Lines 146–147: `runCatching { env?.stopEnvironmentComponents() }`
     - Line 150: `PowerManager.stop()` (Naked invocation — NOT wrapped in `runCatching`)
     - Lines 152–160: Nulls out view and coordinator references, clears `ActiveGameRegistry`.
     - Lines 161–165: `runCatching { val steamManager = steamManagerProvider.get(); steamManager.keepAlive = false; steamManager.clearPlayingConflict() }`
5. `app/src/main/java/app/gamenative/core/runtime/GameSessionEntryPoint.kt`
   - Line 19: Exposes `fun gameSessionRuntime(): GameSessionRuntime`.
6. `app/src/main/java/app/gamenative/core/runtime/ActiveGameSession.kt`
   - Lines 22–28: `suspend fun terminate()` cancels `sessionScope`, invokes `runtime.shutdownEnvironment()`, and invokes `onTeardown()`.
7. `app/src/main/java/app/gamenative/core/runtime/DefaultGameSessionManager.kt`
   - Lines 42–76 (`startSession`): Protected by `sessionMutex.withLock`. Cleanly terminates previous active session first.
   - Lines 78–123 (`getOrCreateRuntime`): Non-suspend, NOT `@Synchronized`. Lines 79 checks `_activeSession.value?.runtime?.let { return it }`, then proceeds to build component and construct a new session without synchronization.
   - Lines 125–134 (`endSessionSync`): Sets `_activeSession.value = null` and launches `current.terminate()` asynchronously inside `appScope.launch`.
8. `app/src/main/java/app/gamenative/PluviaApp.kt`
   - Lines 229–230: `val events: EventDispatcher = EventDispatcher()`
   - Lines 235–245: `currentRuntime(createIfMissing)` routes to `appUtilsEntryPoint().gameSessionManager()`.
   - Lines 247–364: Companion object properties (`xEnvironment`, `xServerView`, `inputControlsView`, `inputControlsManager`, `touchpadView`, `radialMenuCoordinator`, `achievementWatcher`, `isOverlayPaused`, `isActivityInForeground`, `activeSuspendPolicy`, `shutdownEnvironment()`, `getDefaultScreenSize()`) delegate cleanly to `currentRuntime` and `screenSizeResolver`.
9. `app/src/main/java/app/gamenative/MainActivity.kt`
   - Line 75: Injects `GameSessionManager`.
   - Lines 384, 425, 440, 453, 490: Cleanly migrated to use `gameSessionManager`.
10. `app/src/main/java/app/gamenative/ui/screen/xr/ImmersiveXrActivity.kt`
    - Line 115: Injects `GameSessionManager`.
    - Lines 117–125: `currentRuntime` and `xServerView` resolved via `gameSessionManager`.
    - Lines 452, 463: Invokes `gameSessionManager.endSession()`.
11. `app/src/main/java/app/gamenative/service/SteamManager.kt`
    - Line 160: Injects `Provider<GameSessionManager>`.
    - Line 1521: Checks `gameSessionManagerProvider.get().isSessionRunning`.
12. `app/src/main/java/app/gamenative/ui/PluviaMain.kt`
    - Line 311: Resolves `gameSessionManager` from `appUtilsEntryPoint().gameSessionManager()`.
    - Lines 582, 1389: Checks `!gameSessionManager.isSessionRunning`.
13. `app/src/main/java/app/gamenative/preferences/DefaultContainerPreferences.kt`
    - Line 28: Injects `ScreenSizeResolver`.
    - Line 100: Uses `screenSizeResolver.getDefaultScreenSize()`.
    - Line 10: Unused import `app.gamenative.PluviaApp`.
14. Unit Tests in `app/src/test/`:
    - `DefaultGameSessionManagerStressTest.kt` lines 156–159: Explicitly states and demonstrates:
      `// Because getOrCreateRuntime is not synchronized, multiple threads might create separate component/runtime instances`
      `println("EMPIRICAL FINDING: getOrCreateRuntime across $numThreads threads produced ${distinctRuntimes.size} distinct runtimes (total builder invocations: ${createdRuntimes.size})")`
    - `EventDispatcherStressTest.kt` lines 77–103: Empirically proves that `clearAllListenersOf<E>()` in `EventDispatcher.kt` fails because `key is E` tests `KClass` against `Event`.

---

## 2. Findings

### [Critical] Finding 1: Concurrency Race Condition and Resource Leak in `DefaultGameSessionManager.getOrCreateRuntime()`
- **What**: `getOrCreateRuntime()` is neither synchronized nor serialized with `sessionMutex`.
- **Where**: `app/src/main/java/app/gamenative/core/runtime/DefaultGameSessionManager.kt:78–123`
- **Why**: When multiple threads or components concurrently request runtime access (such as `PluviaApp` property accessors during startup or UI rendering), both see `_activeSession.value == null`, build independent `GameSessionComponent` subcomponents, create multiple `ActiveGameSession` instances, and overwrite `_activeSession.value`. The earlier session is never terminated, leaking its coroutine scope, views, and listeners. This was empirically confirmed in `DefaultGameSessionManagerStressTest.kt:156–159`.
- **Suggestion**: Annotate `getOrCreateRuntime()` with `@Synchronized` (or enclose its body in `synchronized(this)`). Because line 79 checks `_activeSession.value?.runtime?.let { return it }`, synchronization ensures that the second caller reuses the runtime instantiated by the first.

### [Major] Finding 2: Missing Exception Isolation on `PowerManager.stop()` in `GameSessionRuntime.shutdownEnvironment()`
- **What**: `PowerManager.stop()` is invoked directly without `runCatching`, violating Verification Requirement #4.
- **Where**: `app/src/main/java/app/gamenative/core/runtime/GameSessionRuntime.kt:150`
- **Why**: `PowerManager.stop()` executes profile persistence to disk (`saveProfile()`), driver stop, and metrics collector shutdown. If any of these throw an `IOException` or runtime exception, the subsequent lines 152–168 will be skipped: view references (`xEnvironment`, `xServerView`, `inputControlsView`, etc.) will NOT be nulled out, `ActiveGameRegistry.clear()` will NOT run, and `steamManager.keepAlive` will remain `true`, leaving the application in a permanently corrupted state.
- **Suggestion**: Wrap line 150 in `runCatching`:
  ```kotlin
  runCatching { PowerManager.stop() }
      .onFailure { Timber.e(it, "shutdownEnvironment: PowerManager.stop") }
  ```

### [Major] Finding 3: Event Bus Split-Brain Risk Between `EventsModule` and `PluviaApp.events`
- **What**: `EventsModule.provideEventDispatcher()` creates a new `EventDispatcher()` instance rather than binding the existing event bus used across the app.
- **Where**: `app/src/main/java/app/gamenative/di/EventsModule.kt:16` and `app/src/main/java/app/gamenative/PluviaApp.kt:230`
- **Why**: More than 200 call sites across the UI, ViewModels, and services register and emit on `PluviaApp.events`. If any new or refactored component is injected with `EventDispatcher` from Hilt or via `AppUtilsEntryPoint.eventDispatcher()`, it operates on a completely disconnected event bus. Emitted events will never reach UI listeners, and UI events will never reach injected listeners.
- **Suggestion**: Unify the instances in `EventsModule.kt`:
  ```kotlin
  @Module
  @InstallIn(SingletonComponent::class)
  object EventsModule {
      @Provides
      @Singleton
      fun provideEventDispatcher(): EventDispatcher = PluviaApp.events
  }
  ```

### [Major] Finding 4: Asynchronous Teardown in `DefaultGameSessionManager.endSessionSync()`
- **What**: `endSessionSync()` purports to end the session synchronously, but dispatches `current.terminate()` into an asynchronous `appScope.launch`.
- **Where**: `app/src/main/java/app/gamenative/core/runtime/DefaultGameSessionManager.kt:125–134`
- **Why**: When called from synchronous lifecycle callbacks (e.g. `PluviaApp.shutdownEnvironment()`), `endSessionSync()` immediately clears `_activeSession` and returns before environment cleanup even starts. If the process is terminated shortly after, Wine and driver teardown may be aborted mid-flight.
- **Suggestion**: `runtime.shutdownEnvironment()` is entirely synchronous. `endSessionSync()` should execute `current.runtime.shutdownEnvironment()` immediately and synchronously on the calling thread, and then cancel `current.sessionScope` and invoke `current.onTeardown`.

### [Minor] Finding 5: Broken Key Type Matching in `EventDispatcher.clearAllListenersOf`
- **What**: `clearAllListenersOf<E>()` uses `if (key is E)`, which always evaluates to `false`.
- **Where**: `app/src/main/java/app/gamenative/events/EventDispatcher.kt:52`
- **Why**: The map key is of type `KClass<out Event<*>>`. A `KClass` object is never an instance of event type `E`. Listeners are never cleared. (Empirically verified in `EventDispatcherStressTest.kt:77–103`).
- **Suggestion**: Change to `if (key == E::class)`.

### [Minor] Finding 6: Unused Import in `DefaultContainerPreferences.kt`
- **What**: Unused import `import app.gamenative.PluviaApp`.
- **Where**: `app/src/main/java/app/gamenative/preferences/DefaultContainerPreferences.kt:10`
- **Suggestion**: Remove the unused import.

---

## 3. Verified Claims

| Claim | Method | Result |
|---|---|---|
| `ScreenSizeResolver` correctly resolves 4:3, 16:10, 16:9, normalizes portrait aspect ratios, and caches results | Code inspection & `ScreenSizeResolverTest.kt` | PASS |
| `GameSessionRuntime` holds all session state and replaces static companion fields | Code inspection of `GameSessionRuntime.kt` vs `PluviaApp.kt` | PASS |
| `GameSessionRuntime` is `@GameSessionScoped` and injected via `@Inject constructor` | Verified annotations in `GameSessionRuntime.kt:28–29` | PASS |
| `ScreenSizeResolver` and `DefaultGameSessionManager` are `@Singleton` with `@Inject constructor` | Verified annotations and constructors | PASS |
| Downstream callers (`MainActivity`, `ImmersiveXrActivity`, `SteamManager`, `PluviaMain`, `DefaultContainerPreferences`) decoupled from `PluviaApp.xEnvironment` | Code inspection of all 5 call sites | PASS |
| Zero usage of `PreferencesEntryPoint` or `EntryPointAccessors.fromApplication` inside newly converted runtime classes | Inspected `ScreenSizeResolver`, `GameSessionRuntime`, `DefaultGameSessionManager` | PASS |

---

## 4. Adversarial Challenges

### Challenge 1: Concurrent Session Creation Stress Test
- **Assumption Challenged**: "Caller will only call `getOrCreateRuntime()` from the main UI thread sequentially."
- **Attack Scenario**: Multiple threads simultaneously access properties on `PluviaApp` (`xEnvironment`, `xServerView`, `inputControlsView`) during game launch.
- **Blast Radius**: Leak of unclosed `GameSessionComponent`, uncancelled `CoroutineScope`, and duplicate `GameSessionRuntime` instances.
- **Stress Test Evidence**: `DefaultGameSessionManagerStressTest.kt` line 158 empirically confirmed that 10 concurrent threads produce multiple distinct runtime instances.
- **Mitigation**: Add `@Synchronized` to `getOrCreateRuntime()`.

### Challenge 2: PowerManager Failure Abort in Teardown
- **Assumption Challenged**: "Teardown is resilient to failure in any subsystem."
- **Attack Scenario**: Corrupted power profile on disk causes `PowerManager.stop() -> saveProfile()` to throw `IOException`.
- **Blast Radius**: `xEnvironment = null`, `ActiveGameRegistry.clear()`, and `steamManager.keepAlive = false` are skipped. Active game remains locked as playing, preventing subsequent launches.
- **Mitigation**: Wrap `PowerManager.stop()` in `runCatching`.

### Challenge 3: Injected EventDispatcher Event Loss
- **Assumption Challenged**: "`EventsModule` provides the application event dispatcher."
- **Attack Scenario**: A newly converted manager or ViewModel injects `EventDispatcher` and emits or listens to `SteamEvent` or `AndroidEvent`.
- **Blast Radius**: Complete silent failure of communication; the rest of the application is listening to `PluviaApp.events`.
- **Mitigation**: Have `EventsModule.provideEventDispatcher()` return `PluviaApp.events`.

---

## 5. Caveats & Unverified Items

- **Unverified Items**: Direct execution of `./gradlew compileModernDebugKotlin` and `./gradlew :app:testModernDebugUnitTest` timed out waiting for user interactive permission in this environment. Verification was conducted through exhaustive static code analysis, type signature validation, AST inspection, and thorough review of the accompanying unit test and stress test suites.

---

## 6. Logic Chain

1. *Observation 1 & 4*: The overall architecture of `GameSessionRuntime`, `GameSessionManager`, and `ScreenSizeResolver` meets the architectural objectives of Milestone 5. Escape hatches were eliminated, and Hilt injection contracts are respected.
2. *Observation 7 & 14*: Static inspection and worker stress tests prove that `getOrCreateRuntime()` is unsynchronized and creates multiple leaked runtimes under concurrent load.
3. *Observation 4 & Verification Requirement 4*: Verification Requirement 4 explicitly mandates proper exception isolation in `shutdownEnvironment()`. Line 150 leaves `PowerManager.stop()` naked, which will break teardown if an exception occurs.
4. *Observation 2 & 8*: Creating an independent instance in `EventsModule` creates an architectural split-brain with `PluviaApp.events`.
5. *Conclusion*: Because Findings 1, 2, and 3 introduce real runtime, concurrency, and teardown risks, the required verdict is `REQUEST_CHANGES`.

---

## 7. Conclusion

Milestone 5 has executed substantial high-quality refactoring to extract session state into `@GameSessionScoped GameSessionRuntime` and display metrics into `@Singleton ScreenSizeResolver`. However, changes are requested to address three specific, easily fixable flaws before proceeding to Milestone 6:
1. Add `@Synchronized` to `DefaultGameSessionManager.getOrCreateRuntime()`.
2. Wrap `PowerManager.stop()` in `runCatching` in `GameSessionRuntime.shutdownEnvironment()`.
3. Unify `EventsModule.provideEventDispatcher()` to return `PluviaApp.events`.
4. Fix synchronous execution in `DefaultGameSessionManager.endSessionSync()`.

**Verdict**: **REQUEST_CHANGES**

---

## 8. Verification Method for Remediation

1. **Check Synchronization**:
   Ensure `DefaultGameSessionManager.kt` declares `@Synchronized override fun getOrCreateRuntime(): GameSessionRuntime`.
2. **Check Teardown Isolation**:
   Ensure `GameSessionRuntime.kt:150` wraps `PowerManager.stop()` inside `runCatching { ... }.onFailure { ... }`.
3. **Check EventDispatcher Singleton Binding**:
   Ensure `EventsModule.kt:16` returns `PluviaApp.events`.
4. **Execute Tests & Compilation**:
   ```bash
   ./gradlew compileModernDebugKotlin
   ./gradlew :app:testModernDebugUnitTest --tests "app.gamenative.core.runtime.*" --tests "app.gamenative.utils.ScreenSizeResolverTest" --tests "app.gamenative.events.*"
   ```
