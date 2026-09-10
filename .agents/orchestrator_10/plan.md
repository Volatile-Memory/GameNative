# Plan: Eradicate Mid-Level Singletons (Groups 4, 5, 6)

## Overview
This initiative converts mid-level `object` singletons and hidden Android Service singletons into `@Singleton class` components with `@Inject` constructors to eliminate Dagger Hilt `EntryPoint` escape hatches and `Context` prop-drilling across Groups 4, 5, and 6.

Groups 1, 2, and 3 are complete.
Group 4: Epic, GOG, and Amazon refactored. SteamManager domain extraction and SteamService thin shell complete. CustomGameScanner and DownloadsViewModel updated.
Remaining for Group 4: Complete remaining call sites (UI screens, ViewModels, launch/container utilities, tests), verify `./gradlew compileModernDebugKotlin` and `./gradlew :app:testModernDebugUnitTest`, and pass gate verification.
Next: Group 5 (BestConfigService, WorkshopManager) and Group 6 (PluviaApp.companion), followed by Final Acceptance & Forensics.

## Milestones & Execution Steps

### Milestone 1: Complete Group 4 — Storefront Services
- **Tasks**:
  1. Dispatch `worker_g4_steam_callsites` to complete all remaining call sites for `SteamService` / `SteamManager` and Storefront Managers:
     - ViewModels: `LibraryViewModel`, `UserLoginViewModel`, `MainViewModel`, `GogRecommendationsViewModel`, etc.
     - UI screens: `SteamAppScreen`, `EpicAppScreen`, `GOGAppScreen`, `AmazonAppScreen`, `BaseAppScreen`, etc.
     - Subsystems/Utilities: `ContainerUtils`, `WorkshopManager` (if referencing SteamService), launch helpers, and tests.
     - Ensure no lingering `SteamService.get...`, `SteamService.is...`, `SteamService.user...` static calls remain.
  2. Verify `./gradlew compileModernDebugKotlin` builds cleanly and `./gradlew :app:testModernDebugUnitTest` passes.
  3. Gate verification: 2 Reviewers, 2 Challengers, 1 Forensic Auditor.

### Milestone 2: Group 5 — Advanced Subsystems
- **Targets**: `BestConfigService`, `WorkshopManager`
- **Tasks**:
  1. Dispatch Explorers to map dependencies, call sites, and escape hatches in `BestConfigService` and `WorkshopManager`.
  2. Worker converts `object BestConfigService` and `object WorkshopManager` to `@Singleton class ... @Inject constructor`.
  3. Eradicate `PreferencesEntryPoint`, inject `ContainerPreferences`, `AuthPreferences`, `DownloadPreferences`, `StringResolver`, `AppStoragePaths`, `Provider<SteamManager>`.
  4. Refactor call sites across ViewModels, UI, and background workers.
  5. Verify compilation and tests.
  6. Gate verification: Reviewers, Challengers, Forensic Auditor.

### Milestone 3: Group 6 — PluviaApp Session Extraction
- **Target**: `PluviaApp.companion`
- **Tasks**:
  1. Dispatch Explorers to analyze `PluviaApp.companion` static fields, methods, `xEnvironment`, views, coordinators, suspend state, and `shutdownEnvironment`.
  2. Worker extracts session state into `@GameSessionScoped class GameSessionRuntime` or scoped lifecycle components, and global utilities.
  3. Refactor call sites to inject or access session runtime cleanly.
  4. Verify compilation and tests.
  5. Gate verification: Reviewers, Challengers, Forensic Auditor.

### Milestone 4: Final Acceptance Verification & Integrity Forensics
- **Tasks**:
  1. Verify clean compilation: `./gradlew compileModernDebugKotlin`.
  2. Verify full unit test suite: `./gradlew :app:testModernDebugUnitTest`.
  3. Verify all target classes are `class` (not `object`) with `@Singleton` and `@Inject constructor`.
  4. Verify zero `EntryPointAccessors.fromApplication` in target classes.
  5. Full gate verification: 2 Reviewers, 2 Challengers, 1 Forensic Auditor.
  6. Synthesize final report and present to user.
