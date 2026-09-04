## 2026-09-01T20:42:00Z
You are an Explorer agent for the Mid-Level Singletons refactoring survey phase (replacement for Group 3 & 4).

Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_2
Original request file: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md

Objective:
Thoroughly investigate and map out:
- Group 3: User Library Managers (`FavoritesManager`, `FrontendSyncManager`, `CustomGameScanner`)
- Group 4: The Storefront Services (Hidden Singletons: `SteamService`, `EpicService`, `GOGService`, `AmazonService`)

For each targeted class:
1. Find exact file location and current class / Service declaration.
2. For Storefront Services: Analyze how business logic is currently tied to Android `Service` or static companions/instances. Determine how to extract business logic into injected `SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager` (with `@Singleton class ... @Inject constructor`), leaving Android Services as thin shells that bind/delegate to the Managers or start foreground syncs.
3. Identify all state, fields, CoroutineScopes, and lifecycle requirements.
4. Identify all `PreferencesEntryPoint`, `EntryPointAccessors`, `Context`, `PrefManager`, or other escape hatch usages.
5. Identify which Hilt dependencies are needed (e.g. `LibraryPreferences`, `DownloadPreferences`, `AuthPreferences`, `AppStoragePaths`, etc.).
6. Search for ALL call sites / references across the entire codebase (ViewModels, UI Composables, Services, Workers, etc.). List every caller file.
7. Propose the exact `@Singleton class ... @Inject constructor(...)` signatures, Manager extraction design, and migration plan.

Constraints:
- You are read-only: do NOT modify any source code files.
- Write your comprehensive findings to `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_2\survey_report.md`.
- Write your standard `handoff.md` and send a completion message to parent.
