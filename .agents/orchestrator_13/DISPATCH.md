## 2026-09-10T18:04:12Z

You are the Project Orchestrator (Generation 13) for the "Eradicate Mid-Level Singletons" refactoring initiative.

# Workspace & Working Directory
- Workspace Root: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
- Your dedicated working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\orchestrator_13
- Authoritative User Request: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
- Scope Document: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md
- Previous Orchestrator State: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\orchestrator_12\
- Worker m5_1 state: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m5_1\

# Current Status & Objectives
1. Context: Groups 1, 2, 3, 4, and initial work on Group 5 were handled in previous runs. You are starting from Group 5 / Group 6. For Group 5, some work was already started, so continue where the previous team left off.
2. Group 5: Advanced Subsystems (`BestConfigService`, `WorkshopManager`).
3. Group 6: PluviaApp (The Final Boss): `PluviaApp.companion` (extract `xEnvironment` and static UI views into the `@GameSessionScoped` lifecycle component).
4. Eradicate escape hatches: Inside newly converted classes, delete any usage of `PreferencesEntryPoint` or `EntryPointAccessors`. Inject the required domain preference repositories directly into constructor. Eradicate `context: Context` from function signatures, and keep it out of `@Inject constructor` (inject `StringResolver`, `AppStoragePaths`, etc. instead of using Context as a service locator; only use `@ApplicationContext context: Context` if genuinely required for direct framework methods).
5. Refactor Call Sites: Update all downstream callers that previously invoked these objects statically.
6. Acceptance Criteria:
   - `./gradlew compileModernDebugKotlin` builds cleanly.
   - `./gradlew :app:testModernDebugUnitTest` passes.
   - Targeted classes are `@Singleton class ... @Inject constructor`.
   - No targeted class contains `EntryPointAccessors.fromApplication`.
7. Build Cache Efficiency: GRADLE_USER_HOME is configured on D:\ drive. Do not use `--no-build-cache` unless strictly required.
8. When victory is achieved, notify Sentinel with your completion report.
