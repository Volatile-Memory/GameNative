## 2026-09-04T07:48:52Z
You are worker_g4. Your working directory is C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4.

Read the authoritative requirements at:
C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
and project architecture at:
C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md

Read the detailed exploration reports from the Group 4 Explorers:
- C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_1\report.md
- C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_2\report.md
- C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_g4_3\report.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your mission:
Execute Group 4 Storefront Services Refactoring: Convert SteamService, EpicService, GOGService, and AmazonService into thin Android service shells delegating to injected @Singleton SteamManager, EpicManager, GOGManager, and AmazonManager. Eradicate EntryPoint escape hatches and Context prop-drilling, and update all call sites.

Specific Tasks:
1. Create SteamManager (app/src/main/java/app/gamenative/service/SteamManager.kt):
   - Declare as @Singleton class SteamManager @Inject constructor(...)
   - Absorb all state, JavaSteam client handles, active download jobs map (downloadJobs), PICS channels, AutoCloud sync, Goldberg achievements, and business methods.
   - Inject Room DAOs, domain preferences (AuthPreferences, ContainerPreferences, DownloadPreferences, GeneralPreferences, LibraryPreferences), AppStoragePaths, StringResolver, NotificationHelper, Provider<WorkshopManager>, CoroutineScope, CoroutineDispatcher.
   - Eradicate all PreferencesEntryPoint usages from Steam domain logic.
   - Refactor SteamService.kt into a thin foreground service shell delegating to injected SteamManager.
2. Refactor EpicManager (app/src/main/java/app/gamenative/service/epic/EpicManager.kt) & EpicService:
   - Eradicate PreferencesEntryPoint fallback from EpicManager (inject DownloadPreferences directly).
   - Remove unused epicManager from EpicDownloadManager constructor to prevent circular dependency.
   - Inject EpicDownloadManager into EpicManager.
   - Move active download map (activeDownloads) and download orchestration (downloadGame, cancelDownload, cleanupDownload, getDownloadInfo, getActiveDownloads, hasActiveDownload, hasPartialDownload, getPartialDownloads, deleteGame) from EpicService into EpicManager.
   - Make EpicService.kt a thin foreground service shell delegating to injected EpicManager.
3. Refactor GOGManager (app/src/main/java/app/gamenative/service/gog/GOGManager.kt) & GOGService:
   - Remove unused gogManager from GOGDownloadManager constructor to prevent circular dependency.
   - Inject GOGDownloadManager and DownloadPreferences into GOGManager.
   - Move active download map (activeDownloads), download orchestration, and cloud save sync (syncCloudSaves, detectCloudSaveConflict) from GOGService into GOGManager.
   - Make GOGService.kt a thin foreground service shell delegating to injected GOGManager.
4. Expand AmazonManager (app/src/main/java/app/gamenative/service/amazon/AmazonManager.kt) & AmazonService:
   - Absorb active download map, download orchestration (downloadGame, cancelDownload, getDownloadInfo, getActiveDownloads, hasActiveDownload, getPartialDownloads), game deletion, file verification (verifyInstalledGame), and install path resolution from AmazonService.
   - Completely delete AmazonDaoEntryPoint interface and EntryPointAccessors.fromApplication(...) in AmazonService.
   - Make AmazonService.kt a thin foreground service shell delegating to injected AmazonManager.
5. Update AppUtilsEntryPoint (app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt):
   - Add steamManager(): SteamManager, epicManager(): EpicManager, gogManager(): GOGManager, amazonManager(): AmazonManager.
6. Refactor Call Sites across ViewModels (DownloadsViewModel, LibraryViewModel, MainViewModel, UserLoginViewModel, GogRecommendationsViewModel), UI screens (SteamAppScreen, EpicAppScreen, GOGAppScreen, AmazonAppScreen, BaseAppScreen), container utilities, launch dependencies, pre-install steps, and unit tests:
   - Replace static service calls with instance calls on the injected managers (or via context.appUtilsEntryPoint()).
7. Verify Build & Tests:
   - Run ./gradlew compileModernDebugKotlin (ensure clean compilation, DO NOT use --no-build-cache unless strictly required for corruption).
   - Run ./gradlew :app:testModernDebugUnitTest (ensure tests pass).
   - Confirm all targeted classes are class (not object) with @Singleton and @Inject constructor.
   - Confirm zero EntryPointAccessors.fromApplication in targeted classes.
8. Write your completion report to C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_g4\report.md and handoff.md, and send a message when done.

## 2026-09-04T09:11:12Z
Heartbeat check from parent:
**Context**: Milestone 3 (Group 4 Storefront Services Refactoring)
**Content**: Heartbeat check. Please report your current progress status, what task you are actively working on (e.g., SteamManager extraction, call site refactoring, or compilation/tests), and update your progress.md with your latest status and timestamp.
**Action**: Reply with your current status and progress update.
