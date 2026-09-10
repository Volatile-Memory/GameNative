# Dispatch: explorer_m5_3

## Identity
- Role: Codebase Researcher / Explorer
- Working Directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m5_3

## Mission
Investigate call sites, Activity/View lifecycle hooks, and unit test strategy for Milestone 5 (Group 6: PluviaApp Session Extraction).

## Authoritative Context
- Read ORIGINAL_REQUEST.md: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
- Read PROJECT.md: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md

## Scope & Tasks
1. Search and inspect all call sites invoking `PluviaApp` static members across:
   - `app/src/main/java/app/gamenative/ui/`
   - `app/src/main/java/app/gamenative/service/`
   - `app/src/main/java/com/winlator/`
2. Analyze how `XServerView`, touch/input controls, suspension listeners, and activity hooks interact with the session.
3. Identify existing unit tests that reference `PluviaApp` or test game session lifecycle:
   - What tests exist currently in `app/src/test`?
   - What tests need to be updated or created to verify `GameSessionRuntime` and `GameSessionManager`?
4. Formulate a concrete, step-by-step refactoring plan and risk mitigation strategy:
   - How to migrate callers incrementally or in a single safe pass without breaking game execution.
   - Exact file boundaries and ownership for Worker.
   - Verification commands (`./gradlew compileModernDebugKotlin` and `./gradlew :app:testModernDebugUnitTest`).

## Output
- Write your report to C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m5_3\handoff.md
- Send a completion message to parent orchestrator.

## 2026-09-05T05:11:17Z
Received initial dispatch prompt to investigate call sites, Activity/View lifecycle hooks, and unit test strategy for Milestone 5 (Group 6: PluviaApp Session Extraction).
