## 2026-09-02T01:27:17+05:00
You are an Explorer agent for the Mid-Level Singletons refactoring survey phase.

Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_3
Original request file: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md

Objective:
Thoroughly investigate and map out:
- Group 5: Advanced Subsystems (`BestConfigService`, `WorkshopManager`)
- Group 6: PluviaApp (The Final Boss: `PluviaApp.companion` - extract `xEnvironment` and static UI views into `@GameSessionScoped` lifecycle component or appropriate DI structure)
- DI Infrastructure & Utilities: Check existing Hilt modules (`SystemServicesModule`, `AppStoragePaths`, `StringResolver`, `PreferencesModule`, `@PluviaDataStore`, etc.) and identify if any helper providers or bindings need to be added.
- Existing Unit Tests: Find all unit tests touching these singletons or services.

For each targeted class / component:
1. Find exact file location and current declarations.
2. Map out `PluviaApp.companion` static members, `xEnvironment`, UI views, and how game session lifecycle is managed. Check if `@GameSessionScoped` or a session component exists or needs creation/refactoring.
3. Identify all `PreferencesEntryPoint`, `EntryPointAccessors`, `Context`, `PrefManager`, or other escape hatch usages.
4. Identify which Hilt dependencies are needed.
5. Search for ALL call sites / references across the entire codebase. List every caller file.
6. Propose the exact `@Singleton class ... @Inject constructor(...)`, `@GameSessionScoped` lifecycle component architecture, and migration plan.

Constraints:
- You are read-only: do NOT modify any source code files.
- Write your comprehensive findings to `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_3\survey_report.md`.
- Write your standard `handoff.md` and send a completion message to parent.
