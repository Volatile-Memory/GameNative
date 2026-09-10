# Milestone 4 Execution Plan: Group 5 Advanced Subsystems

## 1. Objectives
Convert `BestConfigService` and `WorkshopManager` from Kotlin `object` singletons to Dagger Hilt `@Singleton class ... @Inject constructor`.
Eliminate `PreferencesEntryPoint` and service-locator anti-patterns.
Inject dependencies cleanly (`ContainerPreferences`, `AuthPreferences`, `DownloadPreferences`, `AppStoragePaths`, `Provider<SteamManager>`, `@ApplicationContext context: Context`, `stringResolver: StringResolver`).
Refactor all call sites across ViewModels, game launch pipelines, UI, and tests.
Verify clean build (`./gradlew compileModernDebugKotlin`) and unit tests (`./gradlew :app:testModernDebugUnitTest`).
Pass independent Gate Verification (2 Reviewers, 2 Challengers, 1 Forensic Auditor).

## 2. Target Files
- `app/src/main/java/app/gamenative/utils/BestConfigService.kt`
- `app/src/main/java/app/gamenative/workshop/WorkshopManager.kt`
- `app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt`
- Call sites in ViewModels, Services, Launch Pipelines, and UI Composable trees
- Associated unit tests in `app/src/test/`

## 3. Phased Execution Steps
- **Step 1: Exploration**:
  - Dispatch Explorer to map `BestConfigService` and `WorkshopManager`:
    - Constructor dependencies & interfaces
    - Current usages of `PreferencesEntryPoint`, `context`, etc.
    - All call sites across codebase (files, lines, callers)
    - Composable entry point needs
    - Test files needing updates
- **Step 2: Implementation**:
  - Dispatch Worker(s) to convert `BestConfigService` and `WorkshopManager` to `@Singleton class ... @Inject constructor`.
  - Add to `AppUtilsEntryPoint` if needed for non-Hilt Composable trees.
  - Refactor all call sites to receive instances via `@Inject constructor` or `AppUtilsEntryPoint`.
  - Update unit tests.
  - Run `./gradlew compileModernDebugKotlin` and `./gradlew :app:testModernDebugUnitTest`.
- **Step 3: Verification & Gate Review**:
  - Dispatch Reviewer 1 & Reviewer 2.
  - Dispatch Challenger 1 & Challenger 2.
  - Dispatch Forensic Auditor.
  - Gate evaluation in `GATE_STATUS.md`.
- **Step 4: Milestone Advance**:
  - Advance to Milestone 5 (Group 6: PluviaApp Session Extraction).
