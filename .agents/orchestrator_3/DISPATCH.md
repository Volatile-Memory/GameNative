# Dispatch Assignment

## 2026-08-31T17:35:03Z

Resume the PrefManager refactoring task after server restart and quota refresh.

### Objectives:
- Execute and verify Milestone 2 (Data, Core & Utilities Migration - 25 files).
- Execute and verify Milestone 3 (Services & Background Workers Migration - 17 files).
- Execute and verify Milestone 4 (ViewModels & UI/Screens Migration - 41 files).
- Execute Milestone 6 (Singleton Eradication & Final Acceptance Verification):
  * Delete `object PrefManager` from `app/src/main/java/app/gamenative/PrefManager.kt`.
  * Remove `PrefManager.init(this)` from `PluviaApp.kt`.
  * Verify recursive text search for `PrefManager.getInstance()` or `PrefManager.` returns 0 results across the entire codebase.
  * Verify `./gradlew compileModernDebugKotlin` compiles successfully.
  * Verify `./gradlew :app:testModernDebugUnitTest` passes.
  * Use Gradle build cache efficiently (GRADLE_USER_HOME on D:\).
