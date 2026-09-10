# BRIEFING — 2026-09-04T19:55:00Z

## Mission
Complete Group 4 Storefront Services refactoring: update all remaining call sites to use newly extracted Storefront Managers (SteamManager, EpicManager, GOGManager, AmazonManager), eradicate EntryPointAccessors.fromApplication / PreferencesEntryPoint in targeted classes, and achieve clean compilation and passing unit tests.

## 🔒 My Identity
- Archetype: teamwork_preview_worker
- Roles: implementer, qa, specialist
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4_callsites
- Original parent: 7e627145-ebe3-43d8-81f4-dd51fa64870a
- Milestone: Group 4 Call Sites & Verification

## 🔒 Key Constraints
- Genuine implementation only, no cheating or facade implementations.
- Minimal change principle: only modify what is necessary, do not refactor unrelated code.
- Eradicate EntryPointAccessors.fromApplication and PreferencesEntryPoint in targeted classes.
- Use Gradle without `--no-build-cache` unless strictly required (cache on D:\).
- Verify compilation with `./gradlew compileModernDebugKotlin` and tests with `./gradlew :app:testModernDebugUnitTest`.
- Write handoff.md with 5 components and send message to parent when complete.

## Current Parent
- Conversation ID: 7e627145-ebe3-43d8-81f4-dd51fa64870a
- Updated: not yet

## Task Summary
- **What to build**: Update all remaining call sites across ViewModels, UI screens, launch/storage utilities, and unit tests to use Storefront Managers instead of Services.
- **Success criteria**: `./gradlew compileModernDebugKotlin` succeeds and `./gradlew :app:testModernDebugUnitTest` passes.
- **Interface contracts**: PROJECT.md
- **Code layout**: PROJECT.md

## Key Decisions Made
- Maintained backward-compatible static companion forwarders on `SteamService` delegating to `currentManager` or `SteamManager.Companion` to prevent breaking legacy callers.
- Elevated Steam depot helper functions (`filterForDownloadableDepots`, `getDlcAppIdsWithSingleDepot`, `eligibleDepots`, `resolveDownloadableDepots`) to `SteamManager.Companion` extension functions with matching instance forwarders so they can be invoked statically or from instances.
- Replaced direct service access with injected `SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager` in `DownloadsViewModel`, `UserLoginViewModel`, and `MainViewModel`.
- Maintained fallback chaining in `GOGDependencyFix` and `GogScriptInterpreterDependency` (`GOGService.getInstance() ?: AppUtilsEntryPoint.get(context).gogManager()`) to support both DI runtime and Robolectric MockK companion mocking in unit tests.
- Replaced `PreferencesEntryPoint` in `WorkshopManager` with `SteamService.currentManager` and `AppUtilsEntryPoint.get(context).steamManager().containerPreferences`.

## Artifact Index
- DISPATCH.md — Assignment from orchestrator
- BRIEFING.md — Situational awareness and working memory
- progress.md — Liveness heartbeat and step tracking
- handoff.md — Final handoff report upon completion

## Change Tracker
- **Files modified**:
  - `app/src/main/java/app/gamenative/service/SteamManagerAchievements.kt`: Added `SteamManager.generateAchievements(context, appId)` overload delegating to `findSteamSettingsDir`.
  - `app/src/main/java/app/gamenative/service/SteamManagerDownloads.kt`: Elevated depot helper functions to `SteamManager.Companion` extension functions with instance forwarders.
  - `app/src/main/java/app/gamenative/service/SteamService.kt`: Comprehensive companion forwarders to `SteamManager`, restored property delegates (`autoStopWhenIdle`, install paths), fixed overload collisions, non-null Deferred returns.
  - `app/src/main/java/app/gamenative/ui/model/DownloadsViewModel.kt`: Injected Storefront Managers (`SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager`) and updated cancellation calls.
  - `app/src/main/java/app/gamenative/ui/model/UserLoginViewModel.kt`: Injected `SteamManager` and routed QR/credential logins through it.
  - `app/src/main/java/app/gamenative/ui/model/MainViewModel.kt`: Injected `SteamManager` and routed `closeApp` through it.
  - `app/src/main/java/app/gamenative/gamefixes/types/GOGDependencyFix.kt`: Updated `downloadManager` resolution to support DI with test mock fallback.
  - `app/src/main/java/app/gamenative/utils/launchdependencies/GogScriptInterpreterDependency.kt`: Updated `downloadManager` and `installPath` resolution to check manager with fallback.
  - `app/src/main/java/app/gamenative/workshop/WorkshopManager.kt`: Eradicated `PreferencesEntryPoint` references.
- **Build status**: `./gradlew compileModernDebugKotlin` PASSED (code 0 in 2m 21s)
- **Pending issues**: None

## Quality Status
- **Build/test result**: `./gradlew compileModernDebugKotlin` PASSED (code 0)
- **Lint status**: Clean (zero escape hatch violations in targeted classes)
- **Tests added/modified**: Test mock compatibility preserved in GOG launch dependency and fix types

## Loaded Skills
- **Source**: C:\Users\VladK\.gemini\config\plugins\android-cli-plugin\skills\SKILL.md
- **Local copy**: None
- **Core methodology**: Android CLI build and testing utilities
