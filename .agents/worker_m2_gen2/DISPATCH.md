## 2026-09-02T03:12:25Z
You are the Worker agent for Milestone 2 of the "Eradicate Mid-Level Singletons" refactoring initiative.

# Working Directory
Your metadata/working directory is: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m2_gen2`
Store your BRIEFING.md, DISPATCH.md, progress.md, and handoff.md in this directory.

# Context & Inputs
- Original Request: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md` (Read first)
- Project Plan: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md`
- Survey Report: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_2\survey_report.md`

# Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

# Scope & Objective: Milestone 2 (Group 3: User Library Managers)
Convert `FavoritesManager`, `FrontendSyncManager`, and `CustomGameScanner` into `@Singleton class` components with `@Inject constructor`, eliminate all static entry points, mutable `@Volatile` preferences, and `init()` calls, and refactor all downstream callers:

1. **`app/src/main/java/app/gamenative/data/FavoritesManager.kt`**:
   - Convert `object FavoritesManager` to `@Singleton class FavoritesManager @Inject constructor(private val repository: FavoritesRepository) : FavoritesRepository by repository`.
   - Remove `@Volatile internal var delegate: FavoritesRepository`.
   - Remove `FavoritesManager.delegate = favoritesRepository` from `PluviaApp.kt`.
   - Expose `favoritesManager(): FavoritesManager` in `AppUtilsEntryPoint.kt`.
   - Update callers (`LibraryViewModel`, `LibraryScreen`, `BaseAppScreen`, `FavoriteActions`, `FavoriteCardIndicator`) to use constructor injection or `context.appUtilsEntryPoint().favoritesManager()`.

2. **`app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt`**:
   - Convert `object FrontendSyncManager` to `@Singleton class FrontendSyncManager @Inject constructor(@ApplicationContext private val context: Context, private val downloadPreferences: DownloadPreferences, private val stringResolver: StringResolver, private val appStoragePaths: AppStoragePaths)`.
   - Eradicate `FrontendSyncEntryPoint` and all `EntryPointAccessors.fromApplication` calls inside `FrontendSyncManager`.
   - Remove `FrontendSyncManager.init(this)` from `PluviaApp.kt`.
   - Expose `frontendSyncManager(): FrontendSyncManager` in `AppUtilsEntryPoint.kt`.
   - Update callers (`LibraryViewModel`, `SettingsScreen`, etc.) to use constructor injection or `context.appUtilsEntryPoint()`.

3. **`app/src/main/java/app/gamenative/utils/CustomGameScanner.kt`**:
   - Convert `object CustomGameScanner` to `@Singleton class CustomGameScanner @Inject constructor(@ApplicationContext private val context: Context, private val downloadPreferences: DownloadPreferences, private val libraryPreferences: LibraryPreferences, private val containerPreferences: ContainerPreferences, private val appStoragePaths: AppStoragePaths)`.
   - Remove all mutable `@Volatile var downloadPreferences`, `@Volatile var libraryPreferences`, `@Volatile var containerPreferences` fields and fallback defaults.
   - Expose `customGameScanner(): CustomGameScanner` in `AppUtilsEntryPoint.kt`.
   - Update all callers (across ViewModels, UI Composables, Dialogs) to use constructor injection or `context.appUtilsEntryPoint().customGameScanner()`.

4. **Verify Build and Unit Tests**:
   - Run `./gradlew compileModernDebugKotlin`
   - Run `./gradlew :app:testModernDebugUnitTest`
   - Verify zero errors and all unit tests pass.

# Output & Reporting
Write your handoff report to `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m2_gen2\handoff.md` and send a message back.
