# Milestone 4 Execution Plan: Group 5 Advanced Subsystems (Orchestrator 12)

## 1. Objectives
Convert `BestConfigService` and `WorkshopManager` from Kotlin `object` singletons to Dagger Hilt `@Singleton class ... @Inject constructor`.
Eliminate `PreferencesEntryPoint` and service-locator anti-patterns.
Inject dependencies cleanly:
- `BestConfigService`: `@ApplicationContext context: Context`, `containerPreferences: ContainerPreferences`, `authPreferences: AuthPreferences`, `stringResolver: StringResolver`
- `WorkshopManager`: `@ApplicationContext context: Context`, `downloadPreferences: DownloadPreferences`, `containerPreferences: ContainerPreferences`, `appStoragePaths: AppStoragePaths`, `steamManagerProvider: Provider<SteamManager>`
Update `AppUtilsEntryPoint.kt` to expose both services.
Refactor call sites in `ContainerUtils.kt`, `ContainerConfigTransfer.kt`, `PluviaMain.kt`, `BaseAppScreen.kt`, `CommunityConfigsDialog.kt`, `SteamAppScreen.kt`, `WorkshopManagerDialog.kt`, `SteamManager.kt`, `SteamManagerDownloads.kt`.
Update unit tests in `AppUtilsEntryPointTest.kt`, `BestConfigServiceTest.kt`, `CommunityConfigApplicationTest.kt`, `WorkshopManagerTest.kt`.
Verify clean build (`./gradlew compileModernDebugKotlin`) and unit tests (`./gradlew :app:testModernDebugUnitTest`).
Pass independent Gate Verification (2 Reviewers, 2 Challengers, 1 Forensic Auditor).
Advance to Milestone 5 (Group 6: PluviaApp Session Extraction).

## 2. Target Files & Ownership
Worker `worker_m4_2` will own:
- `app/src/main/java/app/gamenative/utils/BestConfigService.kt`
- `app/src/main/java/app/gamenative/workshop/WorkshopManager.kt`
- `app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt`
- `app/src/main/java/app/gamenative/service/SteamManager.kt`
- `app/src/main/java/app/gamenative/service/SteamManagerDownloads.kt`
- `app/src/main/java/app/gamenative/utils/ContainerUtils.kt`
- `app/src/main/java/app/gamenative/ui/util/ContainerConfigTransfer.kt`
- `app/src/main/java/app/gamenative/ui/PluviaMain.kt`
- `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt`
- `app/src/main/java/app/gamenative/ui/screen/library/appscreen/SteamAppScreen.kt`
- `app/src/main/java/app/gamenative/ui/component/dialog/CommunityConfigsDialog.kt`
- `app/src/main/java/app/gamenative/ui/component/dialog/WorkshopManagerDialog.kt`
- `app/src/test/java/app/gamenative/di/AppUtilsEntryPointTest.kt`
- `app/src/test/java/app/gamenative/utils/BestConfigServiceTest.kt`
- `app/src/test/java/app/gamenative/utils/CommunityConfigApplicationTest.kt`
- `app/src/test/java/app/gamenative/workshop/WorkshopManagerTest.kt`

## 3. Workflow Steps
1. Dispatch `worker_m4_2` with comprehensive instructions, explorer handoff references, and mandatory anti-cheating warning.
2. Monitor `worker_m4_2` via progress file and liveness timer until completion.
3. Review worker completion handoff, ensuring `./gradlew compileModernDebugKotlin` passed with code 0 and unit tests passed.
4. Dispatch Gate Verifiers in parallel:
   - 2 Reviewers (`teamwork_preview_reviewer`)
   - 2 Challengers (`teamwork_preview_challenger`)
   - 1 Forensic Auditor (`teamwork_preview_auditor`)
5. Record gate verdicts in `GATE_STATUS.md`.
6. Upon unanimous pass (with CLEAN audit), mark Milestone 4 DONE in `PROJECT.md` and report progress.
