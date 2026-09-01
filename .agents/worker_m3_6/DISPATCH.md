## 2026-09-01T00:23:48Z
You are Worker M3 Replacement (`worker_m3_6`) responsible for completing Milestone 3: Services & Background Layer Migration.
Working Directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m3_6
Project Root: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection
Authoritative Request: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
Master Project Plan: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md
Domain Interfaces Reference: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\app\src\main\java\app\gamenative\preferences\
Survey details: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_replacement\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your Exclusive Write Ownership (17 files):
1. `app/src/main/java/app/gamenative/CrashHandler.kt`
2. `app/src/main/java/app/gamenative/service/AchievementWatcher.kt`
3. `app/src/main/java/app/gamenative/service/DownloadService.kt`
4. `app/src/main/java/app/gamenative/service/NexusModImportService.kt`
5. `app/src/main/java/app/gamenative/service/NotificationHelper.kt`
6. `app/src/main/java/app/gamenative/service/SteamAutoCloud.kt`
7. `app/src/main/java/app/gamenative/service/SteamService.kt`
8. `app/src/main/java/app/gamenative/service/SteamWishlistService.kt`
9. `app/src/main/java/app/gamenative/service/amazon/AmazonConstants.kt`
10. `app/src/main/java/app/gamenative/service/amazon/AmazonService.kt`
11. `app/src/main/java/app/gamenative/service/epic/EpicConstants.kt`
12. `app/src/main/java/app/gamenative/service/epic/EpicDownloadManager.kt`
13. `app/src/main/java/app/gamenative/service/epic/EpicManager.kt`
14. `app/src/main/java/app/gamenative/service/epic/EpicService.kt`
15. `app/src/main/java/app/gamenative/service/gog/GOGConstants.kt`
16. `app/src/main/java/app/gamenative/service/gog/GOGService.kt`
17. `app/src/main/java/app/gamenative/workshop/WorkshopManager.kt`

Instructions:
1. Run grep search across the 17 files to check which ones still contain `import app.gamenative.PrefManager` or `PrefManager.`.
2. Migrate all remaining usages to the domain preference interfaces (`AuthPreferences`, `ContainerPreferences`, `InputPreferences`, `HudPreferences`, `LibraryPreferences`, `DownloadPreferences`, `GeneralPreferences`):
   - For Android Services: `@Inject lateinit var` or `PreferencesEntryPoint.get(context)`.
   - In `SteamService.attachBaseContext(newBase: Context)`: `PreferencesEntryPoint.get(newBase).generalPreferences().appLanguage`.
   - For singletons: `PreferencesEntryPoint.get(context)` or `PreferencesEntryPoint.get(PluviaApp.instance)`.
3. Verify zero references to `app.gamenative.PrefManager` remain across all 17 files.
4. Write your detailed handoff report to `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m3_6\handoff.md` and update `progress.md`.
5. Send completion message back to parent.
