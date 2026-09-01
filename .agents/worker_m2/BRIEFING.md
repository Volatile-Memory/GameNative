# BRIEFING — 2026-08-31T17:35:00+05:00

## Mission
Migrate all usages of `app.gamenative.PrefManager` across the 25 files in the Data, Core & Utilities layer to use domain preference interfaces (`AuthPreferences`, `ContainerPreferences`, `InputPreferences`, `HudPreferences`, `LibraryPreferences`, `DownloadPreferences`, `GeneralPreferences`) via `@Inject constructor(...)` or `PreferencesEntryPoint`.

## 🔒 My Identity
- Archetype: implementer, qa, specialist
- Roles: implementer, qa
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m2
- Original parent: dacc0236-7f70-4e9f-a26d-6b6f0e7ae794
- Milestone: M2 (Data, Core & Utilities Layer Migration)

## 🔒 Key Constraints
- 25 files strictly assigned to worker_m2.
- Remove `import app.gamenative.PrefManager` from all 25 files.
- Replace with `@Inject constructor(...)` for Hilt injectable classes or `context.preferencesEntryPoint()` / `PreferencesEntryPoint.get(context)` where Context is available in static/utility contexts.
- No hardcoded test results, facade implementations, or circumventing genuine logic.
- Ensure build cache efficiency (do not use `--no-build-cache` unless strictly needed).

## Current Parent
- Conversation ID: dacc0236-7f70-4e9f-a26d-6b6f0e7ae794
- Updated: 2026-08-31T17:35:00+05:00

## Task Summary
- **What to build**: Migrate 25 data, core, utils, mods, sync files to domain preference interfaces.
- **Success criteria**: All 25 files migrated, 0 references to `app.gamenative.PrefManager` in them, `./gradlew compileModernDebugKotlin` passes.
- **Interface contracts**: Domain interfaces in `app.gamenative.preferences.*`, `PreferencesEntryPoint`.
- **Code layout**: `app/src/main/java/app/gamenative/{data,di,sync,mods,utils}/*`

## Change Tracker
- **Files modified**: [TBD]
- **Build status**: [TBD]
- **Pending issues**: None

## Quality Status
- **Build/test result**: [TBD]
- **Lint status**: Clean
- **Tests added/modified**: None yet

## Loaded Skills
- None required for core Kotlin/Hilt refactor.

## Key Decisions Made
- Use constructor injection where classes are `@Singleton` or instantiated via Hilt.
- Use `PreferencesEntryPoint` in static objects / helpers where context is passed as a parameter or available.
