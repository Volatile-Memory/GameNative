## 2026-09-01T05:00:44Z
You are Explorer for Milestones 3 & 4.
Your working directory is: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m3_m4_1
Project root: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
Authoritative Request: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
Project plan: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md

Tasks:
1. Search across the entire codebase for all occurrences of `app.gamenative.PrefManager` and `PrefManager.` (ignoring `com.winlator.PrefManager`).
2. List EVERY file that still contains references to `app.gamenative.PrefManager`, grouped by:
   - Milestone 3: `app/src/main/java/app/gamenative/service/`, `app/src/main/java/app/gamenative/workshop/`, `CrashHandler.kt`
   - Milestone 4: `app/src/main/java/app/gamenative/ui/`, `MainActivity.kt`
   - Milestone 2 / 5 / other: any remaining files in `data/`, `utils/`, `powercontrol/`, `tests/`
3. For each file found, document the exact line numbers, the properties accessed, and the required domain preference mapping (AuthPreferences, ContainerPreferences, InputPreferences, HudPreferences, LibraryPreferences, DownloadPreferences, GeneralPreferences).
4. Write your comprehensive report to `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m3_m4_1\handoff.md`.
5. Send a message to parent when done.
