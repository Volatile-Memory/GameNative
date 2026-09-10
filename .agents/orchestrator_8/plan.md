# Plan: Eradicate Mid-Level Singletons (Groups 4, 5, 6)

## Overview
This initiative converts mid-level singletons into injected `@Singleton class` components with `@Inject` constructors to eliminate Dagger Hilt `EntryPoint` escape hatches and `Context` prop-drilling.

Groups 1, 2, and 3 are completed. We start with Group 4.

## Milestones & Execution Steps

### Phase 1: Group 4 — Storefront Services (Hidden Singletons)
- **Target Services**: `SteamService`, `EpicService`, `GOGService`, `AmazonService`
- **Goal**:
  - Extract business logic, download maps, and sync state into `@Singleton class SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager` with `@Inject constructor`.
  - Android Services (`SteamService`, `EpicService`, `GOGService`, `AmazonService`) become thin shells delegating work to the managers.
  - Eradicate `PreferencesEntryPoint` / `EntryPointAccessors.fromApplication`.
  - Eradicate `Context` from method signatures, inject abstractions (`StringResolver`, `AppStoragePaths`, etc.).
  - Refactor all callers across ViewModels, UI, and background workers.
- **Workflow**:
  1. Explorers (3 parallel) investigate storefront services, callers, and extraction strategy.
  2. Synthesize findings into concrete implementation instructions.
  3. Worker executes extraction, updates callers, and verifies build/tests.
  4. Independent Gate Verification: 2 Reviewers, 2 Challengers, 1 Forensic Auditor.

### Phase 2: Group 5 — Advanced Subsystems
- **Target Singletons**: `BestConfigService`, `WorkshopManager`
- **Goal**:
  - Convert `object BestConfigService` and `object WorkshopManager` to `@Singleton class ... @Inject constructor`.
  - Eradicate `PreferencesEntryPoint` and `Context` prop-drilling.
  - Inject required dependencies (`StringResolver`, `AppStoragePaths`, `SteamManager`, `ContainerPreferences`, etc.).
  - Refactor all callers and unit tests.
- **Workflow**:
  1. Explorers analyze `BestConfigService` and `WorkshopManager` call sites and dependencies.
  2. Worker implements class conversions and caller updates, verifies build/tests.
  3. Gate Verification (Reviewers, Challengers, Forensic Auditor).

### Phase 3: Group 6 — PluviaApp (The Final Boss)
- **Target**: `PluviaApp.companion`
- **Goal**:
  - Extract `xEnvironment`, static UI views, touchpad/radial coordinators, suspend state, and `shutdownEnvironment` from `PluviaApp.companion` into `@GameSessionScoped class GameSessionRuntime` or scoped lifecycle components.
  - Refactor callers to obtain state from the session runtime / DI.
- **Workflow**:
  1. Explorers analyze `PluviaApp.companion` static fields, methods, and call sites.
  2. Worker implements extraction and updates callers, verifies build/tests.
  3. Gate Verification (Reviewers, Challengers, Forensic Auditor).

### Phase 4: Final Acceptance Verification & Forensics
- Clean build: `./gradlew compileModernDebugKotlin`
- Unit tests: `./gradlew :app:testModernDebugUnitTest`
- Code integrity checks:
  - All targeted classes are `class` with `@Singleton` and `@Inject constructor`.
  - Zero `EntryPointAccessors.fromApplication` in targeted classes.
- Final Reviewers, Challengers, and Forensic Auditor verification.
