## 2026-09-01T20:59:10Z

You are a Worker agent assigned to implement Milestone 1 of the "Eradicate Mid-Level Singletons" refactoring initiative.

Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m1
Original request file: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
Survey Report: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_1\survey_report.md
Project Plan: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Scope & Task:
1. Target Conversions:
   - `HltbService.kt` and `HltbCache.kt` (or within `HltbService.kt`):
     - Convert `HltbCache` to `@Singleton class HltbCache @Inject constructor(private val generalPreferences: GeneralPreferences)`. Remove `@Volatile var preferences: GeneralPreferences? = null`.
     - Convert `HltbService` to `@Singleton class HltbService @Inject constructor(private val hltbCache: HltbCache, @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`. Keep companion constants (`GAME_URL`, `UNKNOWN_HOURS`) and pure helpers (`formatHours`, `normalize`, `levenshtein`).
   - `SteamGridDB.kt`:
     - Convert `SteamGridDB` to `@Singleton class SteamGridDB @Inject constructor(private val downloadPreferences: DownloadPreferences, @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`. Remove `@Volatile var preferences: DownloadPreferences? = null`.
   - `DeviceGameStatsCache.kt`:
     - Convert `DeviceGameStatsCache` to `@Singleton class DeviceGameStatsCache @Inject constructor(private val generalPreferences: GeneralPreferences)`. Remove `@Volatile var preferences: GeneralPreferences? = null`.
   - `GpuGameStatsCache.kt`:
     - Convert `GpuGameStatsCache` to `@Singleton class GpuGameStatsCache @Inject constructor(private val generalPreferences: GeneralPreferences)`. Remove `@Volatile var preferences: GeneralPreferences? = null`.
   - `GameCompatibilityCache.kt`:
     - Convert `GameCompatibilityCache` to `@Singleton class GameCompatibilityCache @Inject constructor(private val generalPreferences: GeneralPreferences)`. Remove `@Volatile var preferences: GeneralPreferences? = null`.
   - `AppUtilsEntryPoint.kt`:
     - Create `app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt` to allow Composable UI trees to look up utility singletons via `remember(context) { AppUtilsEntryPoint.get(context) }`.

2. Call Sites Refactoring:
   - `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt`:
     - Inject `deviceGameStatsCache: DeviceGameStatsCache`, `gpuGameStatsCache: GpuGameStatsCache`, `gameCompatibilityCache: GameCompatibilityCache` into `@Inject constructor(...)`.
     - Update all static calls (`DeviceGameStatsCache.`, `GpuGameStatsCache.`, `GameCompatibilityCache.`) to use the injected instance variables.
   - `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt`:
     - Inject `deviceGameStatsCache: DeviceGameStatsCache`, `gpuGameStatsCache: GpuGameStatsCache`, `gameCompatibilityCache: GameCompatibilityCache` into `@Inject constructor(...)`.
     - Update all static calls to use the injected instance variables.
   - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt`:
     - Obtain `hltbService` and `gameCompatibilityCache` via `AppUtilsEntryPoint.get(context)` and update call sites.
   - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt`:
     - Obtain `steamGridDb` via `AppUtilsEntryPoint.get(context)` and update call sites.
   - `app/src/main/java/app/gamenative/ui/screen/library/LibraryAppScreen.kt`:
     - Check for any references and update.
   - Unit Tests:
     - `app/src/test/java/app/gamenative/utils/HltbCacheTest.kt`: Update test to instantiate `HltbCache(generalPreferences)` directly instead of static object calls.
     - `app/src/test/java/app/gamenative/utils/HltbServiceIntegrationTest.kt`: Update test to instantiate `HltbCache` and `HltbService` directly with mocked/fake preferences.
     - `app/src/test/java/app/gamenative/utils/HltbServiceTest.kt`: Ensure pure helper tests pass.

3. Build & Test Verification:
   - Build cache efficiency: do NOT use `--no-build-cache`.
   - Run `./gradlew compileModernDebugKotlin`
   - Run `./gradlew :app:testModernDebugUnitTest --tests "app.gamenative.utils.Hltb*"`
   - Ensure clean compilation with zero warnings/errors in modified files.

4. Output:
   - Write your handoff report to `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m1\handoff.md`.
   - Send completion message to parent.
