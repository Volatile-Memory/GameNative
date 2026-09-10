# BRIEFING — 2026-09-04T07:42:00Z

## Mission
Investigate EpicService and GOGService for Group 4 storefront refactoring into @Singleton EpicManager and GOGManager.

## 🔒 My Identity
- Archetype: explorer
- Roles: investigation, synthesis
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_2
- Original parent: c1976959-ffe8-4d0e-bee2-b7f8bc2ddbf7
- Milestone: M3 (Group 4 Storefront Services)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Focus on EpicService and GOGService
- Locate definitions, inspect companion objects, stateful fields, methods
- Check business logic, state, API clients vs Service lifecycle
- Check PreferencesEntryPoint, EntryPointAccessors, Context service locator usages
- Enumerate callers/usages across codebase
- Concrete architecture and plan to extract EpicManager and GOGManager
- Write comprehensive report to .agents/explorer_g4_2/report.md

## Current Parent
- Conversation ID: c1976959-ffe8-4d0e-bee2-b7f8bc2ddbf7
- Updated: 2026-09-04T07:35:40Z

## Investigation State
- **Explored paths**: EpicService.kt, GOGService.kt, EpicManager.kt, GOGManager.kt, EpicDownloadManager.kt, GOGDownloadManager.kt, EpicOverlayManager.kt, EpicConstants.kt, GOGConstants.kt, all caller files across app/src (ViewModels, UI, utils, dependencies, tests).
- **Key findings**:
  - EpicService and GOGService act as hidden singletons with mutable companion state and active download maps.
  - EpicManager and GOGManager already exist as @Singleton classes, but lack download tracking, download dispatching, cloud save sync (GOG), and container uninstallation (Epic).
  - EpicDownloadManager and GOGDownloadManager have unused constructor parameters for EpicManager/GOGManager; removing these prevents circular dependencies.
  - PreferencesEntryPoint is used in EpicManager.kt:45, EpicService.kt:201, GOGService.kt:222, EpicConstants, and GOGConstants; can be replaced by direct injection of DownloadPreferences.
  - Over 50 call sites identified across 25 files for EpicService and 27 files for GOGService.
- **Unexplored areas**: None. Exploration complete.

## Key Decisions Made
- Fully documented extraction plan in report.md and handoff.md.

## Artifact Index
- report.md — comprehensive analysis report
- handoff.md — self-contained handoff report
- progress.md — liveness heartbeat
