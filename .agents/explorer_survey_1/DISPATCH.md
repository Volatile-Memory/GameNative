## 2026-09-01T20:27:16Z
You are an Explorer agent for the Mid-Level Singletons refactoring survey phase.

Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_1
Original request file: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md

Objective:
Thoroughly investigate and map out:
- Group 1: Metadata & HowLongToBeat (`HltbService`, `HltbCache`, `SteamGridDB`)
- Group 2: Hardware & Compatibility Caches (`DeviceGameStatsCache`, `GpuGameStatsCache`, `GameCompatibilityCache`)

For each targeted class:
1. Find exact file location and current class declaration (e.g. `object X`).
2. Identify all state, fields, caches, CoroutineScopes, and lifecycle requirements.
3. Identify all `PreferencesEntryPoint`, `EntryPointAccessors`, `Context`, `PrefManager`, or other escape hatch usages.
4. Identify which Hilt dependencies are needed (e.g. `AuthPreferences`, `ContainerPreferences`, `DownloadPreferences`, `GeneralPreferences`, `OkHttpClient`, `AppStoragePaths`, `StringResolver`, `@ApplicationContext Context` if needed).
5. Search for ALL call sites / references across the entire codebase (ViewModels, UI Composables, Services, Workers, etc.). List every caller file and how it currently accesses the singleton.
6. Propose the exact `@Singleton class ... @Inject constructor(...)` signature and migration plan for both the class and its callers.

Constraints:
- You are read-only: do NOT modify any source code files.
- Write your comprehensive findings to `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_1\survey_report.md`.
- Write your standard `handoff.md` and send a completion message to parent.
