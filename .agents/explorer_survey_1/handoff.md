# Mid-Level Singletons Survey (Groups 1 & 2) Handoff Report

**Agent**: Explorer Survey Agent (`explorer_survey_1`)  
**Milestone**: Mid-Level Singletons Refactoring Survey  
**Date**: 2026-09-02  
**Target Scope**: 
- Group 1: Metadata & HowLongToBeat (`HltbService`, `HltbCache`, `SteamGridDB`)
- Group 2: Hardware & Compatibility Caches (`DeviceGameStatsCache`, `GpuGameStatsCache`, `GameCompatibilityCache`)
- Detailed Report Path: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_1\survey_report.md`

---

## 1. Observation

All 6 target singletons, their caller sites, and their unit tests were inspected across the codebase:

1. **`app/src/main/java/app/gamenative/utils/HltbService.kt`**:
   - `object HltbService` (line 33) holds `auth: Auth?`, `apiBaseUrl`, and an HTTP/1.1 OkHttpClient. Statically calls `HltbCache.get(name)` (line 105) and `HltbCache.put(name, stats)` (line 120).
   - `object HltbCache` (line 276) holds `@Volatile var preferences: GeneralPreferences? = null` (line 281), `mem` map, and `stamps` map. Uses `GeneralPreferences.hltbCache` for JSON serialization.
2. **`app/src/main/java/app/gamenative/utils/SteamGridDB.kt`**:
   - `object SteamGridDB` (line 24) holds `@Volatile var preferences: DownloadPreferences? = null` (line 32), an OkHttpClient (lines 34–37), and calls `GameMetadataManager.read(gameFolder)` (line 533) and `GameMetadataManager.update(...)` (line 537).
3. **`app/src/main/java/app/gamenative/utils/DeviceGameStatsCache.kt`**:
   - `object DeviceGameStatsCache` (line 18) holds `@Volatile var preferences: GeneralPreferences? = null` (line 22), `inMemory` map, and 6-hour TTL timestamp. Reads/writes `GeneralPreferences.deviceGameStatsCache` and calls `DeviceGameStatsService.fetchForDevice(...)`.
4. **`app/src/main/java/app/gamenative/utils/GpuGameStatsCache.kt`**:
   - `object GpuGameStatsCache` (line 18) holds `@Volatile var preferences: GeneralPreferences? = null` (line 22), `inMemory` map, and 6-hour TTL timestamp. Reads/writes `GeneralPreferences.gpuGameStatsCache` and calls `DeviceGameStatsService.fetchForGpu(...)`.
5. **`app/src/main/java/app/gamenative/utils/GameCompatibilityCache.kt`**:
   - `object GameCompatibilityCache` (line 14) holds `@Volatile var preferences: GeneralPreferences? = null` (line 18), `inMemoryCache`, and `timestamps`. Reads/writes `GeneralPreferences.gameCompatibilityCache`.
6. **Call Sites**:
   - `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` (lines 195, 200, 209, 210, 522–524, 551, 556, 564, 565, 681, 1215, 1249): Statically accesses `DeviceGameStatsCache`, `GpuGameStatsCache`, and `GameCompatibilityCache`.
   - `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt` (lines 101, 107, 116, 117): Statically accesses `GameCompatibilityCache`, `DeviceGameStatsCache`, and `GpuGameStatsCache`.
   - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt` (lines 304, 1194): Statically accesses `GameCompatibilityCache.getCached()` and `HltbService.getStats()`.
   - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt` (line 417): Statically accesses `SteamGridDB.fetchGameImages()`.
   - `app/src/test/java/app/gamenative/utils/HltbCacheTest.kt` & `HltbServiceIntegrationTest.kt`: Exercise `HltbCache` and `HltbService`.

---

## 2. Logic Chain

1. **Convert to `@Singleton class` with Constructor Injection**:
   - `HltbCache` takes `GeneralPreferences` via `@Inject constructor`.
   - `HltbService` takes `HltbCache` and `@IoDispatcher ioDispatcher: CoroutineDispatcher = Dispatchers.IO` via `@Inject constructor`.
   - `SteamGridDB` takes `DownloadPreferences` and `@IoDispatcher ioDispatcher: CoroutineDispatcher = Dispatchers.IO` via `@Inject constructor`.
   - `DeviceGameStatsCache`, `GpuGameStatsCache`, and `GameCompatibilityCache` take `GeneralPreferences` via `@Inject constructor`.
2. **Eliminate All Static Fallback Preference State**:
   - All 5 `@Volatile var preferences: DomainPreferences? = null` fields and optional parameter defaults `(prefs: DomainPreferences? = preferences)` are removed.
3. **Refactor Call Sites**:
   - ViewModels (`LibraryViewModel`, `GogRecommendationsViewModel`) inject the singleton caches directly into their constructor.
   - For Composable trees (`BaseAppScreen`, `CustomGameAppScreen`), provide `AppUtilsEntryPoint` (`@EntryPoint @InstallIn(SingletonComponent::class)`) so composables can retrieve instances cleanly via `context.appUtilsEntryPoint()`.
   - Unit tests instantiate classes directly (e.g. `HltbCache(generalPreferences)`), removing the need for global static reset methods.

---

## 3. Caveats

- `GameMetadataManager` is currently an `object` used by `SteamGridDB` to update `.gamenative` files. It has no preference escape hatches and does not hold global mutable cache state, so it does not block converting `SteamGridDB`.
- `DeviceGameStatsService` and `GameCompatibilityService` are stateless network utilities that do not hold state. Group 2 caches will continue to call them or receive them cleanly.

---

## 4. Conclusion

Groups 1 & 2 refactoring is completely surveyed, fully mapped, and ready for execution. All 6 targets have clean dependency graphs, 0 blocker dependencies, and well-defined call sites across ViewModels, Composables, and unit tests.

The complete survey report has been saved to:
`C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_1\survey_report.md`

---

## 5. Verification Method

To independently verify the survey observations:
1. Verify target declarations and lack of hard-coded EntryPoints in target files:
   ```pwsh
   git grep "object HltbService" app/src/main/java/
   git grep "object HltbCache" app/src/main/java/
   git grep "object SteamGridDB" app/src/main/java/
   git grep "object DeviceGameStatsCache" app/src/main/java/
   git grep "object GpuGameStatsCache" app/src/main/java/
   git grep "object GameCompatibilityCache" app/src/main/java/
   ```
2. Verify all caller locations matching the survey report:
   ```pwsh
   git grep "HltbService" app/src/main/java/
   git grep "HltbCache" app/src/main/java/
   git grep "SteamGridDB" app/src/main/java/
   git grep "DeviceGameStatsCache" app/src/main/java/
   git grep "GpuGameStatsCache" app/src/main/java/
   git grep "GameCompatibilityCache" app/src/main/java/
   ```
3. Invalidation condition: Any unmapped caller or unmapped preference dependency in the 6 target classes.
