# Plan: Eradicate Mid-Level Singletons (Groups 4, 5, 6)

## Overview
This initiative converts mid-level `object` singletons and hidden Android Service singletons into `@Singleton class` components with `@Inject` constructors to eliminate Dagger Hilt `EntryPoint` escape hatches and `Context` prop-drilling.

Groups 1, 2, and 3 are complete.
Group 4 is partially complete (Epic, GOG, and Amazon refactored by worker_g4).
Remaining: SteamManager extraction, SteamService refactoring, AppUtilsEntryPoint, call sites, Group 5, Group 6, and final verification.

## Milestones & Execution Steps

### Milestone 1: Complete Group 4 — Storefront Services
- **Tasks**:
  1. Extract `SteamManager` as `@Singleton class SteamManager @Inject constructor(...)` from `SteamService.kt`.
  2. Reduce `SteamService.kt` to a thin foreground Android Service delegating all state and business logic to `SteamManager`.
  3. Update `AppUtilsEntryPoint` with `steamManager(): SteamManager`, `epicManager(): EpicManager`, `gogManager(): GOGManager`, `amazonManager(): AmazonManager`.
  4. Refactor all call sites across ViewModels (`DownloadsViewModel`, `MainViewModel`, `LibraryViewModel`, `UserLoginViewModel`), UI screens (`SteamAppScreen`, `EpicAppScreen`, `GOGAppScreen`, `AmazonAppScreen`, `BaseAppScreen`), utilities, launch dependencies, and unit tests.
  5. Compile cleanly with `./gradlew compileModernDebugKotlin` and run `./gradlew :app:testModernDebugUnitTest`.
  6. Run gate verification: 2 Reviewers, 2 Challengers, 1 Forensic Auditor.

### Milestone 2: Group 5 — Advanced Subsystems
- **Targets**: `BestConfigService`, `WorkshopManager`
- **Tasks**:
  1. Dispatch Explorers to map dependencies, call sites, and escape hatches in `BestConfigService` and `WorkshopManager`.
  2. Worker converts `object BestConfigService` and `object WorkshopManager` to `@Singleton class ... @Inject constructor`.
  3. Eradicate `PreferencesEntryPoint`, inject `ContainerPreferences`, `AuthPreferences`, `DownloadPreferences`, `StringResolver`, `AppStoragePaths`, `Provider<SteamManager>`.
  4. Refactor call sites across ViewModels, UI, and background workers.
  5. Verify compilation and tests.
  6. Run gate verification: Reviewers, Challengers, Forensic Auditor.

### Milestone 3: Group 6 — PluviaApp Session Extraction
- **Target**: `PluviaApp.companion`
- **Tasks**:
  1. Dispatch Explorers to analyze `PluviaApp.companion` static fields, methods, `xEnvironment`, views, coordinators, suspend state, and `shutdownEnvironment`.
  2. Worker extracts session state into `@GameSessionScoped class GameSessionRuntime` or scoped lifecycle components, and global utilities.
  3. Refactor call sites to inject or access session runtime cleanly.
  4. Verify compilation and tests.
  5. Run gate verification: Reviewers, Challengers, Forensic Auditor.

### Milestone 4: Final Acceptance Verification & Integrity Forensics
- **Tasks**:
  1. Run clean compilation: `./gradlew compileModernDebugKotlin`.
  2. Run full unit test suite: `./gradlew :app:testModernDebugUnitTest`.
  3. Verify all target classes are `class` (not `object`) with `@Singleton` and `@Inject constructor`.
  4. Verify zero `EntryPointAccessors.fromApplication` in target classes.
  5. Full gate verification: 2 Reviewers, 2 Challengers, 1 Forensic Auditor.
  6. Synthesize final report and present to user.
