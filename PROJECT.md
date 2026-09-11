# Project: Eradicate Mid-Level Singletons

## Architecture
- **Dependency Injection Framework**: Dagger Hilt with `SingletonComponent`, `ActivityComponent`, and `@GameSessionScoped` `GameSessionComponent`.
- **Domain Managers & Caches (`@Singleton class ... @Inject constructor`)**:
  - `HltbService`, `HltbCache`, `SteamGridDB` (Metadata & HowLongToBeat)
  - `DeviceGameStatsCache`, `GpuGameStatsCache`, `GameCompatibilityCache` (Hardware & Compatibility Caches)
  - `FavoritesManager`, `FrontendSyncManager`, `CustomGameScanner` (User Library Managers)
  - `SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager` (Storefront Business Logic & State)
  - `BestConfigService`, `WorkshopManager` (Advanced Subsystems)
  - `GameSessionRuntime` (Active Game Session Lifecycle Component scoped to `@GameSessionScoped`)
- **Android Services as Thin Shells**:
  - `SteamService`, `EpicService`, `GOGService`, `AmazonService` retain Android `Service` lifecycle and foreground notification management, delegating all state, API calls, and business logic to injected Managers.
- **Escape Hatch Elimination**:
  - Direct injection of `AuthPreferences`, `ContainerPreferences`, `DownloadPreferences`, `GeneralPreferences`, `LibraryPreferences`, `InputPreferences`, `HudPreferences`.
  - Elimination of `PreferencesEntryPoint` and `EntryPointAccessors.fromApplication` inside converted domain classes.
  - Elimination of `Context` service-locator usages (using `StringResolver`, `AppStoragePaths`, `SystemServicesModule`).

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Group 1: Metadata & HowLongToBeat | Convert `HltbService`, `HltbCache`, `SteamGridDB` to `@Singleton class`, create `AppUtilsEntryPoint`, refactor `BaseAppScreen`, `CustomGameAppScreen`, `LibraryViewModel`, `GogRecommendationsViewModel`, and unit tests | M1 | ORIGINAL_REQUEST §R1 Group 1 |
| 2 | Group 2: Hardware & Compatibility Caches | Convert `DeviceGameStatsCache`, `GpuGameStatsCache`, `GameCompatibilityCache` to `@Singleton class`, inject `GeneralPreferences`, refactor callers in `LibraryViewModel`, `GogRecommendationsViewModel`, `BaseAppScreen` | M1 | ORIGINAL_REQUEST §R1 Group 2 |
| 3 | Group 3: User Library Managers | Convert `FavoritesManager`, `FrontendSyncManager`, `CustomGameScanner` to `@Singleton class`, eliminate `FrontendSyncEntryPoint` and mutable `@Volatile` preference fields, refactor all call sites across 25+ files | M2 | ORIGINAL_REQUEST §R1 Group 3 |
| 4 | Group 4: Storefront Services Extraction | Extract business logic, active download maps, and sync state from `SteamService`, `EpicService`, `GOGService`, `AmazonService` into `@Singleton` `SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager`, refactor Android Services into thin shells, and update callers across 100+ files | M3 | ORIGINAL_REQUEST §R1 Group 4 |
| 5 | Group 5: Advanced Subsystems | Convert `BestConfigService` and `WorkshopManager` to `@Singleton class`, eliminate `PreferencesEntryPoint` and `context: Context` prop-drilling, inject `StringResolver`, `AppStoragePaths`, `SteamManager`, refactor callers and tests | M4 | ORIGINAL_REQUEST §R1 Group 5 |
| 6 | Group 6: PluviaApp Session Extraction | Extract `xEnvironment`, static UI views, touchpad/radial coordinators, suspend state, and `shutdownEnvironment` from `PluviaApp.companion` into `@GameSessionScoped class GameSessionRuntime`, bind to `GameSessionComponent`, provide global utilities (`EventDispatcher`, `ScreenSizeResolver`), refactor callers | M5 | ORIGINAL_REQUEST §R1 Group 6 |
| 7 | Full Acceptance Verification & Audit | Clean compile (`compileModernDebugKotlin`), unit tests (`:app:testModernDebugUnitTest`), verify 0 mid-level `object` singletons, verify 0 `EntryPointAccessors.fromApplication` in target classes, complete Challenger and Forensic Auditor gates | M6 | ORIGINAL_REQUEST Acceptance Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Metadata & Compatibility Caches (Groups 1 & 2) | `HltbService.kt`, `SteamGridDB.kt`, `DeviceGameStatsCache.kt`, `GpuGameStatsCache.kt`, `GameCompatibilityCache.kt`, `AppUtilsEntryPoint.kt`, callers & tests | none | DONE |
| 2 | M2: User Library Managers (Group 3) | `FavoritesManager.kt`, `FrontendSyncManager.kt`, `CustomGameScanner.kt`, callers & tests | M1 | DONE |
| 3 | M3: Storefront Services & Managers (Group 4) | `SteamService`/`SteamManager`, `EpicService`/`EpicManager`, `GOGService`/`GOGManager`, `AmazonService`/`AmazonManager`, callers & tests | M2 | DONE |
| 4 | M4: Advanced Subsystems (Group 5) | `BestConfigService.kt`, `WorkshopManager.kt`, callers & tests | M3 | DONE |
| 5 | M5: PluviaApp & GameSession Runtime (Group 6) | `PluviaApp.kt`, `GameSessionRuntime.kt`, `GameSessionComponent.kt`, `GameSessionManager.kt`, callers & tests | M4 | DONE |
| 6 | M6: Acceptance Verification & Forensics | Full Gradle compile, unit test suite, Reviewers, Challengers, and Forensic Auditor verification | M1, M2, M3, M4, M5 | IN_PROGRESS |

## Interface Contracts
### Group 1 & 2 ↔ UI & ViewModels
- `HltbService`: `@Singleton class HltbService @Inject constructor(hltbCache: HltbCache, @IoDispatcher ioDispatcher: CoroutineDispatcher)`
- `HltbCache`: `@Singleton class HltbCache @Inject constructor(generalPreferences: GeneralPreferences)`
- `SteamGridDB`: `@Singleton class SteamGridDB @Inject constructor(downloadPreferences: DownloadPreferences, @IoDispatcher ioDispatcher: CoroutineDispatcher)`
- `DeviceGameStatsCache`: `@Singleton class DeviceGameStatsCache @Inject constructor(generalPreferences: GeneralPreferences)`
- `GpuGameStatsCache`: `@Singleton class GpuGameStatsCache @Inject constructor(generalPreferences: GeneralPreferences)`
- `GameCompatibilityCache`: `@Singleton class GameCompatibilityCache @Inject constructor(generalPreferences: GeneralPreferences)`
- `AppUtilsEntryPoint`: `@EntryPoint @InstallIn(SingletonComponent::class)` provides instance access for Composable trees (`BaseAppScreen`, `CustomGameAppScreen`).

### Group 3 & 4 ↔ Consumers
- `FavoritesManager`: `@Singleton class FavoritesManager @Inject constructor(repository: FavoritesRepository) : FavoritesRepository by repository`
- `FrontendSyncManager`: `@Singleton class FrontendSyncManager @Inject constructor(@ApplicationContext context: Context, downloadPreferences: DownloadPreferences, stringResolver: StringResolver, appStoragePaths: AppStoragePaths)`
- `CustomGameScanner`: `@Singleton class CustomGameScanner @Inject constructor(@ApplicationContext context: Context, downloadPreferences: DownloadPreferences, libraryPreferences: LibraryPreferences, containerPreferences: ContainerPreferences, appStoragePaths: AppStoragePaths, steamManagerProvider: Provider<SteamManager>)`
- `SteamManager`, `EpicManager`, `GOGManager`, `AmazonManager`: `@Singleton class ... @Inject constructor(...)` hold state and business logic.
- Services (`SteamService`, `EpicService`, `GOGService`, `AmazonService`) inject their corresponding Manager and delegate background operations.

### Group 5 & 6 ↔ Runtime
- `BestConfigService`: `@Singleton class BestConfigService @Inject constructor(@ApplicationContext context: Context, containerPreferences: ContainerPreferences, authPreferences: AuthPreferences, stringResolver: StringResolver)`
- `WorkshopManager`: `@Singleton class WorkshopManager @Inject constructor(@ApplicationContext context: Context, downloadPreferences: DownloadPreferences, containerPreferences: ContainerPreferences, appStoragePaths: AppStoragePaths, steamManagerProvider: Provider<SteamManager>)`
- `ScreenSizeResolver`: `@Singleton class ScreenSizeResolver @Inject constructor(@ApplicationContext context: Context)`
- `EventsModule`: `@Module @InstallIn(SingletonComponent::class)` provides `@Singleton fun provideEventDispatcher(): EventDispatcher`
- `GameSessionRuntime`: `@GameSessionScoped class GameSessionRuntime @Inject constructor(val sessionInfo: ActiveGameSessionInfo, private val steamManagerProvider: Provider<SteamManager>, @GameSessionCoroutineScope private val sessionScope: CoroutineScope)` holds `xEnvironment`, views, coordinators, suspend state, and executes `shutdownEnvironment()`.
- `GameSessionManager`: `@Singleton class DefaultGameSessionManager : GameSessionManager` coordinates atomic session start, reactive state flow `activeSession`, and `endSession()`.

## Code Layout
- `app/src/main/java/app/gamenative/utils/`
  - `HltbService.kt`, `HltbCache.kt`, `SteamGridDB.kt`, `DeviceGameStatsCache.kt`, `GpuGameStatsCache.kt`, `GameCompatibilityCache.kt`, `BestConfigService.kt`, `CustomGameScanner.kt`
- `app/src/main/java/app/gamenative/data/`
  - `FavoritesManager.kt`
- `app/src/main/java/app/gamenative/sync/`
  - `FrontendSyncManager.kt`
- `app/src/main/java/app/gamenative/service/`
  - `SteamService.kt`, `SteamManager.kt`
  - `epic/EpicService.kt`, `epic/EpicManager.kt`
  - `gog/GOGService.kt`, `gog/GOGManager.kt`
  - `amazon/AmazonService.kt`, `amazon/AmazonManager.kt`
- `app/src/main/java/app/gamenative/workshop/`
  - `WorkshopManager.kt`
- `app/src/main/java/app/gamenative/core/runtime/`
  - `GameSessionRuntime.kt`, `GameSessionComponent.kt`, `GameSessionManager.kt`, `DefaultGameSessionManager.kt`
- `app/src/main/java/app/gamenative/di/`
  - `AppUtilsEntryPoint.kt`, `PreferencesModule.kt`, `RuntimeModule.kt`
