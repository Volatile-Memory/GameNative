# Dispatch: worker_g4_tests

## Role & Mission
You are `worker_g4_tests`, a `teamwork_preview_worker` subagent.
Your dedicated working directory is:
`C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_tests`

## Context & Background
In Milestone 1 (Group 4 Storefront Services), production code (`app/src/main`) compiles cleanly via `./gradlew compileModernDebugKotlin` with code 0.
However, Gate Verification round 1 revealed that 4 unit test files in `app/src/test` have broken compilation due to updated constructor signatures, and a couple of minor call sites need cleanup:

1. **`app/src/test/java/app/gamenative/di/AppUtilsEntryPointTest.kt`** (lines 36–46):
   `AppUtilsEntryPoint` added 4 abstract accessor methods:
   - `fun steamManager(): app.gamenative.service.SteamManager`
   - `fun epicManager(): app.gamenative.service.epic.EpicManager`
   - `fun gogManager(): app.gamenative.service.gog.GOGManager`
   - `fun amazonManager(): app.gamenative.service.amazon.AmazonManager`
   The anonymous test object `mockEntryPoint` does not implement these 4 methods.
   Fix: Mock these 4 managers (e.g. using `mock(SteamManager::class.java)` or whatever mock library this file uses, e.g. mockito or mockk) and implement these 4 overrides in `mockEntryPoint`.

2. **`app/src/test/java/app/gamenative/service/epic/EpicManagerTest.kt`** (line 25):
   Calls `EpicManager(mockDao)` with 1 argument.
   The constructor in `EpicManager.kt` takes 6 arguments:
   - `epicGameDao: EpicGameDao`
   - `downloadPreferences: DownloadPreferences`
   - `context: Context`
   - `epicDownloadManagerProvider: Provider<EpicDownloadManager>`
   - `epicOverlayManagerProvider: Provider<EpicOverlayManager>`
   - `ioDispatcher: CoroutineDispatcher`
   Fix: Provide mocks or test instances (e.g. `Provider { mockDownloadManager }`, `Dispatchers.Unconfined` or test dispatcher) for the missing parameters.

3. **`app/src/test/java/app/gamenative/service/gog/GOGDownloadManagerTest.kt`** (line 55):
   Calls `GOGDownloadManager(apiClient, parser, gogManager, context)`.
   The actual constructor is:
   `GOGDownloadManager(apiClient: GOGApiClient, parser: GOGManifestParser, context: Context, gogManagerProvider: Provider<GOGManager>)`
   Fix: Pass parameters in the correct order: `(apiClient, parser, context, Provider { gogManager })`.

4. **`app/src/test/java/app/gamenative/service/SteamAutoCloudTest.kt`** (lines 320–327 and other test methods):
   Invocations pass `steamInstance = mockSteamService`.
   `SteamAutoCloud.syncUserFiles` was refactored so parameter `steamInstance: SteamService` became `steamManager: SteamManager`.
   Fix: Update the test invocations to pass `steamManager = mockSteamManager` (and mock `SteamManager` instead of `SteamService` if needed).

5. **`app/src/main/java/app/gamenative/ui/model/MainViewModel.kt`** (lines 754, 759):
   Calls `SteamService.getAppInfoOf(gameId)` and `SteamService.getWindowsLaunchInfos(gameId)`.
   Fix: `MainViewModel` already injects `steamManager: SteamManager`. Replace those static calls with `steamManager.getAppInfoOf(gameId)` and `steamManager.getWindowsLaunchInfos(gameId)`.

6. **`app/src/main/java/app/gamenative/service/SteamService.kt`** (lines 461, 468, 599):
   Calls `currentManager!!.downloadSteam(...)` etc.
   Fix: Remove the unsafe force-unwrap `!!` by using safe-calls `currentManager?.downloadSteam(...)` or null handling.

## MANDATORY INTEGRITY WARNING
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. An auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Verification Requirements
1. Run `./gradlew compileModernDebugKotlin` — MUST exit with code 0.
2. Run `./gradlew :app:testModernDebugUnitTest` or the relevant unit test commands (e.g. `./gradlew :app:testModernDebugUnitTest --tests "app.gamenative.di.AppUtilsEntryPointTest"` and the other test classes) to verify test compilation and passing.
3. Check that zero `EntryPointAccessors.fromApplication` or `PreferencesEntryPoint` are introduced in targeted classes.
4. Document all changed files, build/test results, and verification commands in `handoff.md`.
5. Send a message to the caller when complete.

## 2026-09-04T22:15:23Z
You are worker_g4_tests, a teamwork_preview_worker subagent.
Your dedicated working directory is:
C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_tests

Read:
1. C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
2. C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md
3. C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_tests\DISPATCH.md
4. C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\challenger_g4_2\handoff.md
5. C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\reviewer_g4_2\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. An auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your assignment:
1. Fix `app/src/test/java/app/gamenative/di/AppUtilsEntryPointTest.kt`: Add overrides for `steamManager()`, `epicManager()`, `gogManager()`, `amazonManager()` on `mockEntryPoint`.
2. Fix `app/src/test/java/app/gamenative/service/epic/EpicManagerTest.kt`: Supply all 6 constructor parameters to `EpicManager`.
3. Fix `app/src/test/java/app/gamenative/service/gog/GOGDownloadManagerTest.kt`: Correct parameter order and wrap `gogManager` in `Provider { gogManager }`.
4. Fix `app/src/test/java/app/gamenative/service/SteamAutoCloudTest.kt`: Pass `steamManager = mockSteamManager` instead of `steamInstance = mockSteamService`.
5. Fix `app/src/main/java/app/gamenative/ui/model/MainViewModel.kt` (lines 754, 759): Replace static `SteamService` calls with injected `steamManager`.
6. Fix `app/src/main/java/app/gamenative/service/SteamService.kt` (lines 461, 468, 599): Replace `currentManager!!` force-unwraps with safe calls or null handling.
7. Run `./gradlew compileModernDebugKotlin` (MUST exit 0) and verify test targets compile and pass via `./gradlew :app:testModernDebugUnitTest` (or specific test runs).
8. Record all changes, build/test results, and verification commands in `handoff.md` and send a message back to the caller when complete.

