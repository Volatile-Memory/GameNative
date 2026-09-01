# BRIEFING — 2026-08-31T16:25:00Z

## Mission
Comprehensive call-site mapping and architectural categorization of PrefManager usages across the GameNative codebase.

## 🔒 My Identity
- Archetype: Teamwork explorer
- Roles: Codebase Call-Site Mapper
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_replacement
- Original parent: 39631fec-37ca-4d7d-9fe3-fdb7c715ad76
- Milestone: Survey Phase

## 🔒 Key Constraints
- Read-only investigation — do NOT implement changes in source code
- Analyze problems, synthesize findings, produce structured reports

## Current Parent
- Conversation ID: 39631fec-37ca-4d7d-9fe3-fdb7c715ad76
- Updated: 2026-08-31T16:25:00Z

## Investigation State
- **Explored paths**: Entire codebase scanned via ripgrep (`app/src/main`, `app/src/test`, `com/winlator`, UI, Services, Data, Utils).
- **Key findings**:
  - Exactly 74 files consume `app.gamenative.PrefManager` (63 production + 11 unit tests).
  - Categorized into 6 layers and 7 domain repositories (`AuthPreferences`, `ContainerPreferences`, `InputPreferences`, `HudPreferences`, `LibraryPreferences`, `DownloadPreferences`, `GeneralPreferences`).
  - Java call sites identified in `WineUtils.java` and `BionicProgramLauncherComponent.java`.
  - Non-Hilt components resolved via `PreferencesEntryPoint`.
- **Unexplored areas**: None. Call site survey complete.

## Key Decisions Made
- Fully documented all 74 files with exact line numbers and domain category mapping.
- Clarified distinction with `com.winlator.PrefManager`.

## Artifact Index
- DISPATCH.md — Task history
- BRIEFING.md — Situational awareness
- progress.md — Heartbeat and step tracking
- handoff.md — Final structured report
