## 2026-08-31T12:32:46Z
You are Worker M5 (`worker_m5`) responsible for Milestone 5: Runtime, Java Bridges & Unit Tests Migration.
Your working directory is `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m5`.
Project Root: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection`.
Original Request: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md`.
Master Project Plan: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md`.
Call-site survey: `C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_survey_2_replacement\handoff.md`.

Your Exclusive Write Ownership (Runtime, Java Bridges, Power Control, Unit Tests):
1. `app/src/main/java/com/winlator/core/WineUtils.java`
2. `app/src/main/java/com/winlator/xenvironment/components/BionicProgramLauncherComponent.java`
3. `app/src/main/java/com/winlator/inputcontrols/ControllerManager.java`
4. `app/src/main/java/app/gamenative/powercontrol/PowerProfile.kt`
5. `app/src/main/java/app/gamenative/powercontrol/drivers/PServerDriver.kt`
6. `app/src/main/java/app/gamenative/powercontrol/drivers/SamsungPerformanceDriver.kt`
7. `app/src/test/java/app/gamenative/service/gog/GOGConstantsTest.kt`
8. `app/src/test/java/app/gamenative/service/gog/GOGDownloadManagerTest.kt`
9. `app/src/test/java/app/gamenative/utils/BestConfigServiceTest.kt`
10. `app/src/test/java/app/gamenative/utils/CommunityConfigApplicationTest.kt`
11. `app/src/test/java/app/gamenative/utils/HltbCacheTest.kt`
12. `app/src/test/java/app/gamenative/utils/HltbServiceIntegrationTest.kt`
13. `app/src/test/java/app/gamenative/utils/downloader/ContainerFilesDownloaderTest.kt`
14. `app/src/test/java/app/gamenative/utils/downloader/CoreDriverDownloaderTest.kt`
15. `app/src/test/java/app/gamenative/utils/downloader/DXWrapperDownloaderTest.kt`
16. `app/src/test/java/app/gamenative/utils/downloader/GraphicsDriverDownloaderTest.kt`
17. `app/src/test/java/app/gamenative/utils/downloader/WinComponentDownloaderTest.kt`

Your Task:
1. In Java bridges (`WineUtils.java`, `BionicProgramLauncherComponent.java`), access domain preferences via `PreferencesEntryPoint.get(context).libraryPreferences()` / `PreferencesEntryPoint.get(context).authPreferences()`. Remove unused `import app.gamenative.PrefManager;` in `ControllerManager.java`.
2. In `powercontrol/*`, use `HudPreferences` via `PreferencesEntryPoint.get(context).hudPreferences()` or constructor injection.
3. In all 11 Unit Test files: replace `mockkObject(PrefManager)` / `PrefManager.init()` / reflection on `PrefManager.dataStore` with mock domain preference interfaces (e.g. `mockk<GeneralPreferences>()`, `mockk<DownloadPreferences>()`, etc.) or in-memory preference fakes.
4. Remove `import app.gamenative.PrefManager` from all 17 files.
