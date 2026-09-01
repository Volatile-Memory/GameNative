## 2026-08-31T13:42:29Z

You are Worker M3 Replacement (`worker_m3_2`) responsible for Milestone 3: Services & Background Layer Migration.
Your working directory is `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m3_2`.
Project Root: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection`.
Original Request: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md`.
Master Project Plan: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md`.
Call-site survey: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_replacement\handoff.md`.

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

Your Task:
Migrate all usages of `app.gamenative.PrefManager` in the above files to use domain preference interfaces (`AuthPreferences`, `ContainerPreferences`, `InputPreferences`, `HudPreferences`, `LibraryPreferences`, `DownloadPreferences`, `GeneralPreferences`) via `@Inject` constructor / field injection or `PreferencesEntryPoint.get(context)` / `context.preferencesEntryPoint()`. In `SteamService.kt`, update `attachBaseContext` using `PreferencesEntryPoint.get(newBase).generalPreferences().appLanguage`. Remove `import app.gamenative.PrefManager` from all migrated files.
