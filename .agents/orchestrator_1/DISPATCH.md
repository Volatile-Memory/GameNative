## 2026-08-31T10:29:08Z

<USER_REQUEST>
You are the Project Orchestrator for the PrefManager refactoring task.

Your Working Directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\orchestrator_1
Project Root: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
Authoritative Request: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
Integrity Mode: development

Mission:
Refactor the massive `PrefManager` singleton object into logical, Dagger Hilt injected preference repositories (`AuthPreferences`, `ContainerPreferences`, etc.). Migrate the ~100 files using `PrefManager` to use these new injected interfaces in a phased approach, eliminating the singleton soup. Divide the work across different app layers concurrently.

Key Constraints & Requirements:
1. Preference Repositories: Define separate logical preference interfaces for distinct domains and implement them, delegating to existing `PluviaPreferences` DataStore keys for zero data loss.
2. Dependency Injection: Provide interfaces via Dagger Hilt modules so they can be injected into ViewModels, Services, and core classes.
3. Widespread Migration: Migrate all existing usages of `PrefManager` singleton object across the codebase.
4. Eradicate Singleton: Once all usages have been migrated, delete the `PrefManager` singleton object entirely.
5. Build Cache Efficiency: GRADLE_USER_HOME is on D:\. Do not use `--no-build-cache` unless strictly required.
6. Acceptance Criteria:
   - `./gradlew compileModernDebugKotlin` passes.
   - `./gradlew :app:testModernDebugUnitTest` passes.
   - Recursive text search for `PrefManager.getInstance()` or `PrefManager.` returns 0 results.
   - `PrefManager.kt` no longer contains `object PrefManager`.

Continuously maintain your `BRIEFING.md` and `progress.md` in your working directory. Report completion back to the Sentinel when all criteria are fully met.
</USER_REQUEST>
