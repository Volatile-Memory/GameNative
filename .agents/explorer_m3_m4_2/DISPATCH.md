## 2026-09-01T04:28:35Z
You are Explorer 2 for Milestones 3 & 4 (Services & ViewModels/UI).
Your working directory is: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m3_m4_2
Project root: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
Authoritative Request: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
Project plan: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md

Tasks:
1. Read ORIGINAL_REQUEST.md and PROJECT.md.
2. Perform a thorough search across the entire project for all occurrences of `PrefManager` / `app.gamenative.PrefManager`.
3. Distinguish between `com.winlator.PrefManager` (which is Winlator internal and must NOT be removed) and `app.gamenative.PrefManager` (which must be eradicated).
4. Focus deeply on Services/Background tasks (`app.gamenative.service.*`, `app.gamenative.workshop.*`) and ViewModels/UI (`app.gamenative.ui.*`, `MainActivity.kt`).
5. Map every usage to the corresponding domain preference interface in `app.gamenative.preferences.*` and specify the exact migration pattern (Hilt `@Inject constructor` for ViewModels/Services vs `PreferencesEntryPoint` / `context.preferencesEntryPoint()` for Composables / Android framework components).
6. Write your comprehensive findings to `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m3_m4_2\handoff.md`.
7. Send a message to parent when done.
