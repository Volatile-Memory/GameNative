# DISPATCH — Orchestrator 10

## 2026-09-04T16:27:34Z
<USER_REQUEST>
You are the Project Orchestrator (Generation 10) for the "Eradicate Mid-Level Singletons" refactoring initiative (Groups 4, 5, 6).

# Working Directory & Metadata
- Project Root: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
- Your Dedicated Agent Directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\orchestrator_10
- Authoritative User Request: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
- Integrity mode: development
- Predecessor Directories: .agents/orchestrator_8, .agents/orchestrator_9, .agents/worker_g4_steam

# Context & Current Progress
You are succeeding orchestrator_9 following a transient API quota timeout.
Read:
- .agents/ORIGINAL_REQUEST.md
- PROJECT.md
- .agents/orchestrator_9/plan.md
- .agents/orchestrator_9/progress.md
- .agents/worker_g4_steam/progress.md

Current state of Group 4 Storefront Services:
- Epic, GOG, and Amazon services/managers are completely decoupled and refactored.
- SteamManager extraction is complete across all domain components (SteamManager.kt, SteamManagerDownloads.kt, SteamManagerAutoCloud.kt, SteamManagerPICS.kt, SteamManagerAchievements.kt, SteamManagerInput.kt; SteamAutoCloud.kt updated).
- SteamService.kt thin shell is complete.
- AppUtilsEntryPoint.kt is updated with all 4 managers.
- Call-site migrations: CustomGameScanner.kt and DownloadsViewModel.kt have been updated.
- Next steps: Complete remaining call sites (UI screens, ViewModels, launch/container utilities, tests), verify `./gradlew compileModernDebugKotlin` and `./gradlew :app:testModernDebugUnitTest`, and conduct gate verification before moving to Group 5 and Group 6.

# Mission & Requirements
Convert mid-level `object` singletons into `@Singleton class` components with `@Inject` constructors for Groups 4, 5, and 6, eliminating Dagger Hilt `EntryPoint` escape hatches and Context prop-drilling, refactoring all call sites, and achieving clean compilation and passing unit tests.

### R1. Target Conversions
Group 4: Storefront Services (SteamService, EpicService, GOGService, AmazonService -> SteamManager, EpicManager, GOGManager, AmazonManager, services become thin shells).
Group 5: Advanced Subsystems (BestConfigService, WorkshopManager).
Group 6: PluviaApp (PluviaApp.companion -> extract xEnvironment and static UI views into @GameSessionScoped).

### R2. Eradicate Escape Hatches
Inside newly converted classes:
- Delete usage of PreferencesEntryPoint or EntryPointAccessors. Inject domain preference repositories directly.
- Eradicate context: Context from function signatures and strive to keep Context out of @Inject constructors (use Hilt abstractions like StringResolver, AppStoragePaths).

### R3. Refactor Call Sites
Update all downstream callers to receive classes via @Inject.

### R4. Build Cache Efficiency
Use the Gradle build cache efficiently (GRADLE_USER_HOME on D:\). Do not use --no-build-cache unless strictly required.

## Acceptance Criteria
- ./gradlew compileModernDebugKotlin builds cleanly.
- ./gradlew :app:testModernDebugUnitTest passes.
- All targeted classes are declared as class (not object) with @Singleton and @Inject constructor.
- No targeted class contains EntryPointAccessors.fromApplication.

Maintain plan.md and progress.md in your dedicated directory (.agents/orchestrator_10). Report back when all groups are fully implemented, verified, and complete.
</USER_REQUEST>
