# DISPATCH — Worker Group 4 Call Sites & Verification

You are `worker_g4_callsites`, a teamwork_preview_worker subagent.
Your dedicated working directory is:
`C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_callsites`

## Mandatory Reference Documents
You MUST read before starting work:
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_steam\progress.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_steam\BRIEFING.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4\progress.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_1\report.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_2\handoff.md`

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Objective
Complete all remaining Group 4 Storefront call sites, build cleanly, and verify passing unit tests:
1. Inspect the state of Group 4 refactorings:
   - Epic, GOG, and Amazon services/managers are decoupled and refactored (`EpicManager`, `GOGManager`, `AmazonManager`, `EpicDownloadManager`, `GOGDownloadManager`, `AmazonDownloadManager`, thin services).
   - `SteamManager` is extracted into modular files (`SteamManager.kt`, `SteamManagerDownloads.kt`, `SteamManagerAutoCloud.kt`, `SteamManagerPICS.kt`, `SteamManagerAchievements.kt`, `SteamManagerInput.kt`, and updated `SteamAutoCloud.kt`).
   - `SteamService.kt` is a thin foreground service shell delegating to `SteamManager`.
   - `AppUtilsEntryPoint.kt` exposes `steamManager(): SteamManager`, `epicManager(): EpicManager`, `gogManager(): GOGManager`, `amazonManager(): AmazonManager`.
   - `CustomGameScanner.kt` and `DownloadsViewModel.kt` have been updated.
2. Complete all remaining call sites across:
   - ViewModels: `LibraryViewModel`, `UserLoginViewModel`, `MainViewModel`, `GogRecommendationsViewModel`, etc. Inject the relevant Storefront Managers (`SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager`) via `@Inject constructor`.
   - UI Screens: `SteamAppScreen`, `EpicAppScreen`, `GOGAppScreen`, `AmazonAppScreen`, `BaseAppScreen`, etc. Use `AppUtilsEntryPoint` via `rememberEntryPoint` / `LocalContext` or ViewModel delegation.
   - Launch / Storage / Utilities: `ContainerUtils`, `ContainerStorageManager`, `WorkshopManager` (use `Provider<SteamManager>` if needed), `EpicOverlayDependency`, `GogScriptInterpreterDependency`, `GogScriptInterpreterStep`, `MainActivity`, etc.
   - Unit tests: Update tests referencing `SteamService`, `EpicService`, `GOGService`, `AmazonService` static methods (e.g., `GogScriptInterpreterStepTest`, `GogScriptInterpreterDependencyTest`, `GOGDependencyFixTest`, `GameFixesRegistryTest`).
3. Eradicate any remaining `EntryPointAccessors.fromApplication` and `PreferencesEntryPoint` in targeted classes.
4. Verify compilation and tests:
   - Run `./gradlew compileModernDebugKotlin` (do NOT use `--no-build-cache` unless strictly required; cache is on `D:\`).
   - Run `./gradlew :app:testModernDebugUnitTest`.
   - Fix any compilation or test errors until build succeeds cleanly and tests pass.
5. Produce a comprehensive `handoff.md` in your working directory and notify the parent orchestrator via `send_message`.

## 2026-09-04T16:30:31Z
You are worker_g4_callsites, a teamwork_preview_worker subagent.
Your dedicated working directory is:
C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_callsites

Read C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_callsites\DISPATCH.md and all referenced documents.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your task is to complete the Group 4 Storefront Services refactoring by updating all remaining call sites to use the newly extracted Storefront Managers (SteamManager, EpicManager, GOGManager, AmazonManager), resolving all compilation issues, verifying that `./gradlew compileModernDebugKotlin` builds cleanly and `./gradlew :app:testModernDebugUnitTest` passes. Ensure no `EntryPointAccessors.fromApplication` or `PreferencesEntryPoint` remain in the targeted classes. Document all modified files, build/test results, and verification in your handoff.md, and send a message back to the caller when complete.

## 2026-09-04T19:38:31Z
**Context**: Group 4 Storefront Services Call Sites and Unit Tests
**Content**: Orchestrator Generation 10 checking in. What is the current status of `./gradlew :app:testModernDebugUnitTest` and unit test verification?
**Action**: Please report current status, any remaining issues or blockers, and ETA for handoff.md.


