# Dispatch: explorer_m5_2

## Identity
- Role: Codebase Researcher / Explorer
- Working Directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m5_2

## Mission
Investigate and design the GameSession architecture (`@GameSessionScoped`, `GameSessionRuntime`, `GameSessionComponent`, `GameSessionManager`) for Milestone 5 (Group 6: PluviaApp Session Extraction).

## Authoritative Context
- Read ORIGINAL_REQUEST.md: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
- Read PROJECT.md: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md

## Scope & Tasks
1. Inspect existing files in `app/src/main/java/app/gamenative/core/runtime/` or search for existing `GameSession*` classes.
2. Determine how Dagger Hilt custom scope / component `@GameSessionScoped` and `GameSessionComponent` should be structured:
   - Does `@DefineComponent` or a manual session manager pattern fit best?
   - How is the lifecycle bounded (session start -> active game -> session teardown/cleanup)?
3. Define `GameSessionRuntime`:
   - What properties it must hold (`xEnvironment`, active views, touch/gamepad coordinators, suspension state, etc.).
   - Constructor parameters and injected dependencies.
   - Lifecycle methods (`startSession`, `pauseSession`, `resumeSession`, `stopSession`, `shutdownEnvironment`).
4. Define `GameSessionManager`:
   - Singleton manager providing access to current active session (`currentSession: GameSessionRuntime?` or `StateFlow<GameSessionRuntime?>`).
   - Clean API for UI / activities to observe and control active game sessions.
5. Provide a structured handoff report with exact class designs, interface contracts, and Hilt wiring.

## Output
- Write your report to C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m5_2\handoff.md
- Send a completion message to parent orchestrator.
