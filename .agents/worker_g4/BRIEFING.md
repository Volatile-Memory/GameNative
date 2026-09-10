# BRIEFING — 2026-09-04T07:50:00Z

## Mission
Group 4 Storefront Services Refactoring: Convert SteamService, EpicService, GOGService, and AmazonService into thin Android service shells delegating to injected @Singleton SteamManager, EpicManager, GOGManager, and AmazonManager. Eradicate EntryPoint escape hatches and Context prop-drilling, and update all call sites.

## 🔒 My Identity
- Archetype: worker_g4
- Roles: implementer, qa, specialist
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4
- Original parent: c1976959-ffe8-4d0e-bee2-b7f8bc2ddbf7
- Milestone: Group 4 Storefront Services Refactoring

## 🔒 Key Constraints
- Genuine implementations only; no dummy/facade implementations or hardcoded values.
- Clean compilation: `./gradlew compileModernDebugKotlin` (do not use `--no-build-cache` unless strictly needed).
- Unit tests pass: `./gradlew :app:testModernDebugUnitTest`.
- Confirm all targeted classes are `class` (not `object`) with `@Singleton` and `@Inject constructor`.
- Confirm zero `EntryPointAccessors.fromApplication` in targeted classes.
- Follow minimal-change principle where applicable and preserve functionality.

## Current Parent
- Conversation ID: c1976959-ffe8-4d0e-bee2-b7f8bc2ddbf7
- Updated: not yet

## Task Summary
- **What to build**:
  1. SteamManager (@Singleton @Inject constructor) absorbing Steam logic/state, SteamService becomes thin shell.
  2. EpicManager & EpicService refactored: inject DownloadPreferences, remove circular dep in EpicDownloadManager, move download orchestration into EpicManager, EpicService thin shell.
  3. GOGManager & GOGService refactored: remove circular dep in GOGDownloadManager, inject GOGDownloadManager & DownloadPreferences into GOGManager, move download orchestration & cloud saves into GOGManager, GOGService thin shell.
  4. AmazonManager & AmazonService refactored: absorb downloads, delete AmazonDaoEntryPoint, AmazonService thin shell.
  5. AppUtilsEntryPoint updated with 4 managers.
  6. Call sites refactored across ViewModels, UI screens, utilities, unit tests.
- **Success criteria**:
  - compileModernDebugKotlin succeeds
  - testModernDebugUnitTest succeeds
  - zero EntryPointAccessors.fromApplication in targeted classes
  - clean architecture & DI compliant
- **Interface contracts**: PROJECT.md, ORIGINAL_REQUEST.md
- **Code layout**: PROJECT.md

## Change Tracker
- **Files modified**: [TBD]
- **Build status**: [TBD]
- **Pending issues**: none

## Quality Status
- **Build/test result**: [TBD]
- **Lint status**: [TBD]
- **Tests added/modified**: [TBD]

## Loaded Skills
- None loaded.

## Artifact Index
- DISPATCH.md — Assignment instructions
- progress.md — Heartbeat and progress log
- handoff.md — Final handoff report
- report.md — Completion report
