# Inbound Dispatch Record

## 2026-09-02T03:32:26+05:00

You are the Project Orchestrator for the "Eradicate Mid-Level Singletons" refactoring initiative.

# Workspace & Request
- Working Directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
- Original Request File: C:\Users\VladK\.gemini\antigravity\brain\532fc074-4524-41e3-aef8-438b1d4d973e\ORIGINAL_REQUEST.md

# Mission
Convert mid-level `object` singletons into `@Singleton class` components with `@Inject` constructors to eliminate Dagger Hilt `EntryPoint` escape hatches and `Context` prop-drilling. Refactor all upstream callers.

Please check the current repository and `.agents/` state, determine what progress has been made, and resume execution across the 6 Logical Domain Groups:

**Group 1: Metadata & HowLongToBeat**
- `HltbService`, `HltbCache`, `SteamGridDB`

**Group 2: Hardware & Compatibility Caches**
- `DeviceGameStatsCache`, `GpuGameStatsCache`, `GameCompatibilityCache`

**Group 3: User Library Managers**
- `FavoritesManager`, `FrontendSyncManager`, `CustomGameScanner`

**Group 4: The Storefront Services (Hidden Singletons)**
- `SteamService`, `EpicService`, `GOGService`, `AmazonService` 
*(Extract their business logic into injected `SteamManager`, `EpicManager`, etc. The Android Service classes should become thin shells).*

**Group 5: Advanced Subsystems**
- `BestConfigService`, `WorkshopManager`

**Group 6: PluviaApp (The Final Boss)**
- `PluviaApp.companion` 
*(Extract `xEnvironment` and static UI views into the `@GameSessionScoped` lifecycle component).*

## Requirements
- Eradicate `PreferencesEntryPoint` and `EntryPointAccessors`. Inject domain preference repositories directly.
- Eradicate `context: Context` from function signatures and avoid in `@Inject constructor` where possible (inject proper Hilt abstractions like `StringResolver`, `AppStoragePaths`, `NotificationManager`).
- Refactor all downstream callers to use `@Inject` instance methods.
- Efficient build cache (GRADLE_USER_HOME is on D:\). Do not use `--no-build-cache`.

## Acceptance Criteria
- `./gradlew compileModernDebugKotlin` builds cleanly.
- `./gradlew :app:testModernDebugUnitTest` passes.
- Targeted classes are `@Singleton class ... @Inject constructor`.
- No targeted class contains `EntryPointAccessors.fromApplication`.
