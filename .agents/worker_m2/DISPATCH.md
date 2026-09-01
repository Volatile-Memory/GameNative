## 2026-08-31T12:32:45Z
You are Worker M2 (`worker_m2`) responsible for Milestone 2: Data, Core & Utilities Layer Migration.
Your working directory is `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m2`.
Project Root: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection`.
Original Request: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md`.
Master Project Plan: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md`.
Call-site survey: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_replacement\handoff.md`.

Your Exclusive Write Ownership (25 files):
1. `app/src/main/java/app/gamenative/data/DefaultFavoritesRepository.kt`
2. `app/src/main/java/app/gamenative/data/FavoritesManager.kt`
3. `app/src/main/java/app/gamenative/data/RecommendationRepository.kt`
4. `app/src/main/java/app/gamenative/data/SteamCollectionRepository.kt`
5. `app/src/main/java/app/gamenative/data/gog/GogSeedCollector.kt`
6. `app/src/main/java/app/gamenative/di/AppThemeModule.kt`
7. `app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt`
8. `app/src/main/java/app/gamenative/mods/NexusModManager.kt`
9. `app/src/main/java/app/gamenative/utils/BestConfigService.kt`
10. `app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt`
11. `app/src/main/java/app/gamenative/utils/ContainerUtils.kt`
12. `app/src/main/java/app/gamenative/utils/ConversionTracker.kt`
13. `app/src/main/java/app/gamenative/utils/CustomGameScanner.kt`
14. `app/src/main/java/app/gamenative/utils/DeviceGameStatsCache.kt`
15. `app/src/main/java/app/gamenative/utils/DownloadSpeedConfig.kt`
16. `app/src/main/java/app/gamenative/utils/GameCompatibilityCache.kt`
17. `app/src/main/java/app/gamenative/utils/GpuGameStatsCache.kt`
18. `app/src/main/java/app/gamenative/utils/HltbService.kt`
19. `app/src/main/java/app/gamenative/utils/IntentLaunchManager.kt`
20. `app/src/main/java/app/gamenative/utils/KeyAttestationHelper.kt`
21. `app/src/main/java/app/gamenative/utils/ManifestRepository.kt`
22. `app/src/main/java/app/gamenative/utils/PaddingUtils.kt`
23. `app/src/main/java/app/gamenative/utils/PlayIntegrity.kt`
24. `app/src/main/java/app/gamenative/utils/SteamGridDB.kt`
25. `app/src/main/java/app/gamenative/utils/SteamUtils.kt`

Your Task:
Migrate all usages of `app.gamenative.PrefManager` in the above 25 files to use the domain preference interfaces (`AuthPreferences`, `ContainerPreferences`, `InputPreferences`, `HudPreferences`, `LibraryPreferences`, `DownloadPreferences`, `GeneralPreferences`) via `@Inject constructor(...)` where Hilt injection applies, or `context.preferencesEntryPoint()` / `PreferencesEntryPoint.get(context)` where Context is available in static methods. Remove `import app.gamenative.PrefManager` from all migrated files.
