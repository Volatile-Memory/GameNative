## 2026-09-01T22:35:02Z
# Dispatch for Worker — Milestone 2 (Group 3: User Library Managers)

## Mission
Convert Group 3 User Library Managers (`FavoritesManager`, `FrontendSyncManager`, `CustomGameScanner`) from `object` singletons to `@Singleton class` components with `@Inject` constructors. Eliminate `PreferencesEntryPoint` and `EntryPointAccessors.fromApplication` escape hatches, remove mutable `@Volatile` preference fields, clean up `PluviaApp.kt` startup initializers, update `AppUtilsEntryPoint.kt`, and refactor all call sites.

## Mandatory Reading
- Original Request: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md`
- Project Plan: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md`
- Survey Report: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_2\survey_report.md`

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Target Conversions & Signatures
1. **`FavoritesManager.kt`** (`app/src/main/java/app/gamenative/data/FavoritesManager.kt`):
   - `@Singleton class FavoritesManager @Inject constructor(private val repository: FavoritesRepository) : FavoritesRepository by repository`
   - Remove mutable `@Volatile internal var delegate: FavoritesRepository`.
   - Remove `FavoritesManager.delegate = favoritesRepository` from `PluviaApp.kt`.
   - Add `fun favoritesManager(): FavoritesManager` to `AppUtilsEntryPoint.kt`.
   - Refactor callers (`LibraryViewModel`, `LibraryScreen`, `BaseAppScreen`, `FavoriteActions`, `FavoriteCardIndicator`) to use `@Inject` or `context.appUtilsEntryPoint().favoritesManager()`.

2. **`FrontendSyncManager.kt`** (`app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt`):
   - `@Singleton class FrontendSyncManager @Inject constructor(@ApplicationContext private val context: Context, private val downloadPreferences: DownloadPreferences, private val stringResolver: StringResolver, private val appStoragePaths: AppStoragePaths)`
   - Delete `FrontendSyncEntryPoint` and all `EntryPointAccessors.fromApplication` inside `FrontendSyncManager`.
   - Remove `FrontendSyncManager.init(this)` from `PluviaApp.kt`.
   - Add `fun frontendSyncManager(): FrontendSyncManager` to `AppUtilsEntryPoint.kt`.
   - Refactor callers (`PluviaMain.kt`, etc.) to use `@Inject` or `context.appUtilsEntryPoint().frontendSyncManager()`.

3. **`CustomGameScanner.kt`** (`app/src/main/java/app/gamenative/utils/CustomGameScanner.kt`):
   - `@Singleton class CustomGameScanner @Inject constructor(@ApplicationContext private val context: Context, private val downloadPreferences: DownloadPreferences, private val libraryPreferences: LibraryPreferences, private val containerPreferences: ContainerPreferences, private val appStoragePaths: AppStoragePaths, private val steamManagerProvider: Provider<SteamManager>)` (or lazy provider).
   - Delete all mutable `@Volatile` preference fields and fallback static initializers.
   - Add `fun customGameScanner(): CustomGameScanner` to `AppUtilsEntryPoint.kt`.
   - Refactor all 22+ call sites across ViewModels, UI Composables, Dialogs, Storage utilities.

## Verification Requirements
1. Run `./gradlew compileModernDebugKotlin` (efficient build cache, do NOT use `--no-build-cache`).
2. Run `./gradlew :app:testModernDebugUnitTest`.
3. Document all changes, files modified, build/test output, and verification commands in `handoff.md`.

## 2026-09-01T23:00:23Z
**Context**: Monitoring Milestone 2 progress (Group 3: FavoritesManager, FrontendSyncManager, CustomGameScanner)
**Content**: Please report your current status, which files you have edited or are currently refactoring, and whether you are running Gradle compile/tests.
**Action**: Reply with a brief status update and update your progress.md.

## 2026-09-01T23:40:47Z
**Context**: Milestone 2 (Group 3: FavoritesManager, FrontendSyncManager, CustomGameScanner)
**Content**: Checking in on progress. Have all call sites been updated, and are you currently running Gradle build/test verification (`compileModernDebugKotlin` / `:app:testModernDebugUnitTest`)?
**Action**: Please provide a brief status update.
