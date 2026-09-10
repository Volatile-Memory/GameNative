## 2026-09-05T00:08:32Z

You are worker_m4_1 (Group 5 Implementation Worker).
Your working directory is: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m4_1

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Read:
- ORIGINAL_REQUEST.md at: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\ORIGINAL_REQUEST.md
- PROJECT.md at: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\PROJECT.md
- Explorer Handoffs:
  - C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m4_1\handoff.md
  - C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m4_2\handoff.md
  - C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\explorer_m4_3\handoff.md

Your Mission:
Execute Milestone 4 (Group 5 Advanced Subsystems: BestConfigService and WorkshopManager).

File Ownership:
You own exclusively:
- app/src/main/java/app/gamenative/utils/BestConfigService.kt
- app/src/main/java/app/gamenative/workshop/WorkshopManager.kt
- app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt
- app/src/main/java/app/gamenative/service/SteamManager.kt
- app/src/main/java/app/gamenative/service/SteamManagerDownloads.kt
- app/src/main/java/app/gamenative/utils/ContainerUtils.kt
- app/src/main/java/app/gamenative/ui/util/ContainerConfigTransfer.kt
- app/src/main/java/app/gamenative/ui/PluviaMain.kt
- app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt
- app/src/main/java/app/gamenative/ui/screen/library/appscreen/SteamAppScreen.kt
- app/src/main/java/app/gamenative/ui/component/dialog/CommunityConfigsDialog.kt
- app/src/main/java/app/gamenative/ui/component/dialog/WorkshopManagerDialog.kt
- app/src/test/java/app/gamenative/di/AppUtilsEntryPointTest.kt
- app/src/test/java/app/gamenative/utils/BestConfigServiceTest.kt
- app/src/test/java/app/gamenative/utils/CommunityConfigApplicationTest.kt
- app/src/test/java/app/gamenative/workshop/WorkshopManagerTest.kt

Implementation Requirements:
1. BestConfigService.kt:
   - Convert from `object BestConfigService` to:
     ```kotlin
     @Singleton
     class BestConfigService @Inject constructor(
         @ApplicationContext private val context: Context,
         private val containerPreferences: ContainerPreferences,
         private val authPreferences: AuthPreferences,
         private val stringResolver: StringResolver,
     )
     ```
   - Eliminate `preferencesEntryPoint` at lines 812–813 (use injected `containerPreferences` and `authPreferences`).
   - In `getCompatibilityMessage`, use injected `stringResolver.getString(...)`. Remove `context: Context` parameter from `getCompatibilityMessage(matchType: String?)`.
   - Remove `context: Context` parameter from all other functions where possible, utilizing the injected `context`.
   - Keep nested data classes intact.

2. WorkshopManager.kt:
   - Convert from `object WorkshopManager` to:
     ```kotlin
     @Singleton
     class WorkshopManager @Inject constructor(
         @ApplicationContext private val context: Context,
         private val downloadPreferences: DownloadPreferences,
         private val containerPreferences: ContainerPreferences,
         private val appStoragePaths: AppStoragePaths,
         private val steamManagerProvider: Provider<SteamManager>,
     )
     ```
   - Eliminate `AppUtilsEntryPoint` at line 4104 (use injected `containerPreferences.launchBionicSteam`).
   - Eliminate static `SteamService.currentManager?.(download|container)Preferences` (use injected preferences directly).
   - Replace static `SteamService` calls with `steamManagerProvider.get()` instance calls.
   - Remove `context: Context` parameter from public methods where possible (using injected `context`).

3. AppUtilsEntryPoint.kt:
   - Add accessors:
     ```kotlin
     fun bestConfigService(): BestConfigService
     fun workshopManager(): WorkshopManager
     ```

4. Call Sites Refactoring:
   - In `ContainerUtils.kt`, `ContainerConfigTransfer.kt`, `PluviaMain.kt`, `BaseAppScreen.kt`, `CommunityConfigsDialog.kt`:
     Resolve `bestConfigService` via `context.appUtilsEntryPoint().bestConfigService()` and call instance methods.
   - In `PluviaMain.kt`, `SteamAppScreen.kt`, `WorkshopManagerDialog.kt`:
     Resolve `workshopManager` via `context.appUtilsEntryPoint().workshopManager()` and call instance methods.
   - In `SteamManager.kt` / `SteamManagerDownloads.kt`:
     Inject `workshopManagerProvider: Provider<WorkshopManager>` in `SteamManager` constructor so `resumePendingWorkshopDownloads()` can call `workshopManagerProvider.get().startWorkshopDownload(...)`.

5. Unit Tests:
   - `AppUtilsEntryPointTest.kt`: Mock `BestConfigService` and `WorkshopManager` and override methods in the test object; assert `assertNotNull`.
   - `BestConfigServiceTest.kt`: Update to instantiate `BestConfigService` in `@Before setUp()` with mocks; update all static calls to instance calls.
   - `CommunityConfigApplicationTest.kt`: Update to instantiate `BestConfigService` in `@Before setUp()` with mocks; update calls to instance calls.
   - `WorkshopManagerTest.kt`: Update to instantiate `WorkshopManager` in `@Before setUp()` with mocks; update all static calls to instance calls.

6. Verification:
   - Run `./gradlew compileModernDebugKotlin` using `run_command` and confirm exit code 0.
   - Run `./gradlew :app:testModernDebugUnitTest` and confirm all tests pass.

7. Write comprehensive report to C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m4_1\handoff.md and send completion message to parent.
