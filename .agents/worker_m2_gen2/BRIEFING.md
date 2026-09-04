# BRIEFING — 2026-09-02T03:12:25Z

## Mission
Milestone 2 (Group 3: User Library Managers): Convert FavoritesManager, FrontendSyncManager, and CustomGameScanner to @Singleton class with constructor injection, eradicate mutable volatile fields, init() calls, and static entry points, and update all callers.

## 🔒 My Identity
- Archetype: worker
- Roles: [implementer, qa, specialist]
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m2_gen2
- Original parent: 5b0233ae-db2c-4f7b-9816-2df8f2f40a66
- Milestone: Milestone 2 (Group 3: User Library Managers)

## 🔒 Key Constraints
- Pure genuine refactoring; no cheating, no facades, no hardcoded values.
- FavoritesManager: `@Singleton class FavoritesManager @Inject constructor(private val repository: FavoritesRepository) : FavoritesRepository by repository`
- FrontendSyncManager: `@Singleton class FrontendSyncManager @Inject constructor(@ApplicationContext private val context: Context, private val downloadPreferences: DownloadPreferences, private val stringResolver: StringResolver, private val appStoragePaths: AppStoragePaths)`
- CustomGameScanner: `@Singleton class CustomGameScanner @Inject constructor(@ApplicationContext private val context: Context, private val downloadPreferences: DownloadPreferences, private val libraryPreferences: LibraryPreferences, private val containerPreferences: ContainerPreferences, private val appStoragePaths: AppStoragePaths)`
- Expose all 3 via `AppUtilsEntryPoint.kt`.
- Clean up `PluviaApp.kt` (remove init calls and delegate assignment).
- Refactor all callers across ViewModels and UI Composables.
- Compile and test with Gradle.

## Current Parent
- Conversation ID: 5b0233ae-db2c-4f7b-9816-2df8f2f40a66
- Updated: 2026-09-02T03:12:25Z

## Task Summary
- **What to build**: Convert 3 mid-level singletons (FavoritesManager, FrontendSyncManager, CustomGameScanner) to Hilt injectable classes.
- **Success criteria**: Code compiles cleanly with `./gradlew compileModernDebugKotlin`, unit tests pass with `./gradlew :app:testModernDebugUnitTest`.
- **Interface contracts**: PROJECT.md, survey_report.md
- **Code layout**: Standard Android / Kotlin Hilt structure

## Key Decisions Made
- [TBD]

## Artifact Index
- `.agents/worker_m2_gen2/DISPATCH.md` — Assignment instructions
- `.agents/worker_m2_gen2/BRIEFING.md` — Agent briefing & memory
- `.agents/worker_m2_gen2/progress.md` — Progress tracker and heartbeat
- `.agents/worker_m2_gen2/handoff.md` — Final handoff report

## Change Tracker
- **Files modified**: None yet
- **Build status**: Untested
- **Pending issues**: None

## Quality Status
- **Build/test result**: Not started
- **Lint status**: Not started
- **Tests added/modified**: None yet

## Loaded Skills
None
