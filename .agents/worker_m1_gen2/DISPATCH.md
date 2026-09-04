## 2026-09-01T21:02:00Z
You are a Worker agent for Milestone 1 of the "Eradicate Mid-Level Singletons" refactoring initiative.

# Working Directory
Your metadata/working directory is: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m1_gen2`
Store your BRIEFING.md, DISPATCH.md, progress.md, and handoff.md in this directory.

# Context & Inputs
- Original Request: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md` (You MUST read this first).
- Project Plan: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md`
- Survey Report: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_1\survey_report.md`

# Scope & Objective: Milestone 1 (Groups 1 & 2)
Convert the following mid-level singletons into `@Singleton class` components with `@Inject constructor`, eliminate all static `@Volatile var preferences` escape hatches, inject domain preferences directly, and update all call sites:

1. **`app/src/main/java/app/gamenative/utils/HltbService.kt`**:
   - Convert `object HltbCache` to `@Singleton class HltbCache @Inject constructor(private val generalPreferences: GeneralPreferences)`. Remove `@Volatile var preferences: GeneralPreferences? = null` and optional parameter defaults `(prefs: GeneralPreferences? = preferences)`.
   - Convert `object HltbService` to `@Singleton class HltbService @Inject constructor(private val hltbCache: HltbCache, @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`. Update all internal cache calls to use the injected `hltbCache`.
2. **`app/src/main/java/app/gamenative/utils/SteamGridDB.kt`**:
   - Convert `object SteamGridDB` to `@Singleton class SteamGridDB @Inject constructor(private val downloadPreferences: DownloadPreferences, @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`. Remove `@Volatile var preferences: DownloadPreferences? = null` and parameter defaults.
3. **`app/src/main/java/app/gamenative/utils/DeviceGameStatsCache.kt`**:
   - Convert `object DeviceGameStatsCache` to `@Singleton class DeviceGameStatsCache @Inject constructor(private val generalPreferences: GeneralPreferences)`. Remove `@Volatile var preferences: GeneralPreferences? = null`.
4. **`app/src/main/java/app/gamenative/utils/GpuGameStatsCache.kt`**:
   - Convert `object GpuGameStatsCache` to `@Singleton class GpuGameStatsCache @Inject constructor(private val generalPreferences: GeneralPreferences)`. Remove `@Volatile var preferences: GeneralPreferences? = null`.
5. **`app/src/main/java/app/gamenative/utils/GameCompatibilityCache.kt`**:
   - Convert `object GameCompatibilityCache` to `@Singleton class GameCompatibilityCache @Inject constructor(private val generalPreferences: GeneralPreferences)`. Remove `@Volatile var preferences: GeneralPreferences? = null`.
6. **`app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt`**:
   - Create `@EntryPoint @InstallIn(SingletonComponent::class) interface AppUtilsEntryPoint` and extension helper `Context.appUtilsEntryPoint()` providing access to `hltbService()`, `steamGridDB()`, `deviceGameStatsCache()`, `gpuGameStatsCache()`, `gameCompatibilityCache()` for Composable UI trees.
7. **Call Sites Refactoring**:
   - `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt`: Inject `deviceGameStatsCache: DeviceGameStatsCache`, `gpuGameStatsCache: GpuGameStatsCache`, `gameCompatibilityCache: GameCompatibilityCache` via `@Inject constructor`.
   - `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt`: Inject `gameCompatibilityCache: GameCompatibilityCache`, `deviceGameStatsCache: DeviceGameStatsCache`, `gpuGameStatsCache: GpuGameStatsCache` via `@Inject constructor`.
   - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt`: Use `context.appUtilsEntryPoint().gameCompatibilityCache()` and `context.appUtilsEntryPoint().hltbService()`.
   - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt`: Use `context.appUtilsEntryPoint().steamGridDB()`.
   - Unit tests (`HltbCacheTest.kt`, `HltbServiceIntegrationTest.kt`): Update to instantiate the classes with fake/mock preference instances.

## 2026-09-01T21:20:24Z
**Context**: Milestone 1 Implementation (Groups 1 & 2)
**Content**: Checking in on your progress with the Milestone 1 refactoring, compilation, and tests. Please update your progress.md.
**Action**: Please provide a brief status update on which files you are currently modifying.
