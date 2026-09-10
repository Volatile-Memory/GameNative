# DISPATCH — Worker Group 4 Steam & Call Sites

You are `worker_g4_steam`, a teamwork_preview_worker subagent.
Your dedicated working directory is:
`C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_steam`

## Mandatory Reference Documents
You MUST read:
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_1\report.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_2\handoff.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_3\report.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4\progress.md`
- `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4\BRIEFING.md`

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Objective
Complete the Group 4 Storefront Services refactoring:
1. Inspect the work already done in the codebase by `worker_g4` (EpicManager, EpicDownloadManager, EpicService, EpicOverlayManager; GOGManager, GOGDownloadManager, GOGService; AmazonManager, AmazonDownloadManager, AmazonService).
2. Extract `SteamManager` as `@Singleton class SteamManager @Inject constructor(...)` from `SteamService.kt` to hold in-memory state (active downloads, JavaSteam client, callback manager, AutoCloud, PICS, login/connection state).
3. Refactor `SteamService.kt` into a thin foreground Android Service delegating all state and business logic to `SteamManager`.
4. Update `AppUtilsEntryPoint` with `steamManager(): SteamManager`, `epicManager(): EpicManager`, `gogManager(): GOGManager`, and `amazonManager(): AmazonManager`.
5. Eradicate all `PreferencesEntryPoint` and `EntryPointAccessors.fromApplication` from all targeted classes.
6. Refactor all call sites across ViewModels, UI screens, utilities, launch dependencies, and unit tests to receive and use instances via `@Inject` or `AppUtilsEntryPoint`.
7. Build and test verification:
   - Run `./gradlew compileModernDebugKotlin` (do NOT use `--no-build-cache` unless strictly needed).
   - Run `./gradlew :app:testModernDebugUnitTest`.
8. Produce `handoff.md` and report back with `send_message` to parent.

## 2026-09-04T11:52:36Z
**Context**: Group 4 SteamManager extraction and Storefront refactoring
**Content**: Checking in on status. It has been ~18 minutes since your last progress update.
**Action**: Please update your progress.md with your current activity and report your current status.

## 2026-09-04T12:30:54Z
**Context**: Group 4 SteamManager extraction and Storefront refactoring
**Content**: Liveness check: It has been ~35 minutes since your last progress update.
**Action**: Please update your progress.md with your latest progress and status, or report if you have encountered any blocker.
## 2026-09-04T12:55:21Z
**Context**: Group 4 SteamManager extraction and Storefront refactoring
**Content**: Liveness query: It has been ~22 minutes since your last progress update.
**Action**: Please provide a quick status update on the SteamManager modular files and SteamService shell extraction, and update your progress.md timestamp.

## 2026-09-04T13:19:24Z
**Context**: Group 4 SteamManager extraction and Storefront refactoring
**Content**: Liveness check: It has been ~22 minutes since your last progress update.
**Action**: Please provide a quick status update on the SteamService.kt shell and call sites, and update your progress.md timestamp.
