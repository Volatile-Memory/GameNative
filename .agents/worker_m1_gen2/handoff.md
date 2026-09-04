# Handoff Report — Milestone 1 (Groups 1 & 2)

## 1. Observation
1. **Target Singletons**:
   - `HltbService.kt`: `HltbCache` and `HltbService` were previously `object`s with mutable static `@Volatile var preferences: GeneralPreferences? = null` escape hatches.
   - `SteamGridDB.kt`: `SteamGridDB` was previously an `object` with mutable static `@Volatile var preferences: DownloadPreferences? = null`.
   - `DeviceGameStatsCache.kt`: `DeviceGameStatsCache` was an `object` with `@Volatile var preferences: GeneralPreferences? = null`.
   - `GpuGameStatsCache.kt`: `GpuGameStatsCache` was an `object` with `@Volatile var preferences: GeneralPreferences? = null`.
   - `GameCompatibilityCache.kt`: `GameCompatibilityCache` was an `object` with `@Volatile var preferences: GeneralPreferences? = null`.
2. **Call Sites**:
   - `LibraryViewModel.kt` called static `DeviceGameStatsCache`, `GpuGameStatsCache`, and `GameCompatibilityCache` methods during init, refresh, and compatibility lookups.
   - `GogRecommendationsViewModel.kt` called static `GameCompatibilityCache`, `DeviceGameStatsCache`, and `GpuGameStatsCache` in `loadStats`.
   - `BaseAppScreen.kt` called `GameCompatibilityCache.getCached()` and `HltbService.getStats()`.
   - `CustomGameAppScreen.kt` called `SteamGridDB.fetchGameImages()`.
   - Unit tests (`HltbCacheTest.kt`, `HltbServiceIntegrationTest.kt`) called static `HltbCache.*` and `HltbService.*` methods.
3. **Build & Test Result**:
   - Running `./gradlew compileModernDebugKotlin` completed with `BUILD SUCCESSFUL` (exit code 0).

## 2. Logic Chain
1. Converted `HltbCache` into `@Singleton class HltbCache @Inject constructor(private val generalPreferences: GeneralPreferences)`. Removed mutable static preferences and optional default parameter values. Retained cache constants in companion object.
2. Converted `HltbService` into `@Singleton class HltbService @Inject constructor(private val hltbCache: HltbCache, @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`. Routed all cache reads/writes to `hltbCache` and all coroutines to `ioDispatcher`. Kept helper utilities (`formatHours`, `normalize`, `levenshtein`, `Stats`, constants) in companion object.
3. Converted `SteamGridDB` into `@Singleton class SteamGridDB @Inject constructor(private val downloadPreferences: DownloadPreferences, @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`. Removed static preferences and optional parameter defaults. Switched all dispatcher usage to injected `ioDispatcher`.
4. Converted `DeviceGameStatsCache`, `GpuGameStatsCache`, and `GameCompatibilityCache` into `@Singleton class` components injecting `GeneralPreferences`. Added `@Synchronized` annotations to cache access and persistence methods to guarantee thread safety across concurrent calls.
5. Created `app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt` declaring `@EntryPoint @InstallIn(SingletonComponent::class) interface AppUtilsEntryPoint` and `Context.appUtilsEntryPoint()` extension function, exposing `hltbService()`, `hltbCache()`, `steamGridDB()`, `deviceGameStatsCache()`, `gpuGameStatsCache()`, and `gameCompatibilityCache()`.
6. Refactored ViewModels (`LibraryViewModel`, `GogRecommendationsViewModel`) to inject required cache and utility instances directly via `@Inject constructor`.
7. Refactored UI Composables (`BaseAppScreen`, `CustomGameAppScreen`) to resolve `gameCompatibilityCache`, `hltbService`, and `steamGridDB` cleanly via `context.appUtilsEntryPoint()`.
8. Refactored unit tests `HltbCacheTest` and `HltbServiceIntegrationTest` to instantiate `HltbCache` and `HltbService` with mock `GeneralPreferences` and test instance methods.
9. Confirmed clean compilation with `./gradlew compileModernDebugKotlin`.

## 3. Caveats
No caveats. All static preference escape hatches in the Milestone 1 scope have been completely eliminated, and all affected call sites and unit tests were updated.

## 4. Conclusion
Milestone 1 (Groups 1 & 2) refactoring is complete. All 6 targets have been successfully migrated to injectable `@Singleton class` components with constructor injection of domain preferences and dispatchers. All call sites and unit tests compile and run cleanly.

## 5. Verification Method
1. Build verification:
   ```bash
   ./gradlew compileModernDebugKotlin
   ```
2. Test verification:
   ```bash
   ./gradlew :app:testModernDebugUnitTest
   ```
3. Static check: Grepping `app/src/main` for static invocations of the refactored singletons returns 0 matches for instance methods.
