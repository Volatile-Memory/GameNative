# BRIEFING — 2026-08-31T17:41:00Z

## Mission
Migrate all 42 assigned files in Milestone 4 (ViewModels & UI/Screens Migration) from legacy PrefManager to injected preference repositories and PreferencesEntryPoint.

## 🔒 My Identity
- Archetype: implementer, qa, specialist
- Roles: implementer, qa, specialist
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m4_3
- Original parent: 63c39ffc-5d43-4e0c-bdf7-517249e816dd
- Milestone: Milestone 4: ViewModels & UI/Screens Migration

## 🔒 Key Constraints
- Follow minimal change principle.
- Eliminate all `PrefManager.` calls in the 42 assigned files (except `PrefManager.init(this)` in `PluviaApp.kt` which stays until M6).
- For ViewModels: use `@HiltViewModel @Inject constructor(...)`.
- For `MainActivity.kt`: `@Inject lateinit var generalPreferences: GeneralPreferences`, etc. or `PreferencesEntryPoint.get(this)`.
- For Composables / UI: obtain entrypoint via `PreferencesEntryPoint.get(LocalContext.current.applicationContext)` or `LocalContext.current.preferencesEntryPoint()`.
- For Previews: remove redundant `PrefManager.init(LocalContext.current)` calls or make preview-safe.
- Zero data loss, maintain exact functionality.

## Current Parent
- Conversation ID: 63c39ffc-5d43-4e0c-bdf7-517249e816dd
- Updated: 2026-08-31T17:41:00Z

## Task Summary
- **What to build**: Migrate 42 files across ViewModels, State holders, UI Composables, Screens, and Dialogs.
- **Success criteria**: 0 references to `app.gamenative.PrefManager` across all 42 files (except `PluviaApp.kt`'s `PrefManager.init(this)`), compile & test passing.

## Change Tracker
- **Files modified**: None yet
- **Build status**: Pending
- **Pending issues**: None

## Quality Status
- **Build/test result**: Not yet run
- **Lint status**: Clean
- **Tests added/modified**: TBD

## Artifact Index
- `.agents/worker_m4_3/DISPATCH.md` — Assignment record
- `.agents/worker_m4_3/progress.md` — Progress tracker
- `.agents/worker_m4_3/handoff.md` — Final handoff report
