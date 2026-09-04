# BRIEFING — 2026-09-02T04:47:00Z

## Mission
Convert Group 3 User Library Managers (FavoritesManager, FrontendSyncManager, CustomGameScanner) from object singletons to @Singleton class components with @Inject constructors, eliminate escape hatches, update AppUtilsEntryPoint, and refactor all call sites.

## 🔒 My Identity
- Archetype: implementer, qa, specialist
- Roles: implementer, qa, specialist
- Working directory: C:\Users\VladK\.gemini\antigravity\worktrees\GameNative\refactor_gamenative_dependency_injection\.agents\worker_m2_gen3
- Original parent: 4bf9eb46-53d0-4397-87b9-20326acd6467
- Milestone: Milestone 2 - Group 3: User Library Managers

## 🔒 Key Constraints
- Genuine implementation only, no cheating / facades / hardcoded mocks in source.
- Do NOT use --no-build-cache unless strictly necessary.
- Convert FavoritesManager, FrontendSyncManager, CustomGameScanner to @Singleton class with @Inject constructor.
- Eliminate PreferencesEntryPoint and EntryPointAccessors.fromApplication escape hatches.
- Remove mutable @Volatile preference fields and cleanup PluviaApp.kt startup initializers.
- Add components to AppUtilsEntryPoint.kt.
- Refactor all downstream callers.
- Verify with compileModernDebugKotlin and :app:testModernDebugUnitTest.

## Current Parent
- Conversation ID: 4bf9eb46-53d0-4397-87b9-20326acd6467
- Updated: 2026-09-02T04:47:00Z

## Task Summary
- **What to build**: Refactor FavoritesManager, FrontendSyncManager, CustomGameScanner to @Singleton class with @Inject constructors. Eliminate EntryPoint escape hatches and static state. Update callers across ViewModels, UI Composables, Services, Utils. Update AppUtilsEntryPoint.kt.
- **Success criteria**: All Group 3 classes converted to @Singleton class with @Inject constructors, all call sites refactored, AppUtilsEntryPoint updated, unit tests added/updated.
- **Interface contracts**: PROJECT.md, survey_report.md, DISPATCH.md
- **Code layout**: PROJECT.md

## Key Decisions Made
- `FavoritesManager`: converted to `@Singleton class FavoritesManager @Inject constructor(repository: FavoritesRepository) : FavoritesRepository by repository`. Removed mutable `@Volatile internal var delegate`.
- `FrontendSyncManager`: converted to `@Singleton class FrontendSyncManager @Inject constructor(@ApplicationScope scope: CoroutineScope, downloadPreferences: DownloadPreferences, stringResolver: StringResolver, steamAppDao: SteamAppDao, epicGameDao: EpicGameDao, gogGameDao: GOGGameDao, amazonGameDao: AmazonGameDao)`. Subscribed to `PluviaApp.events` in `init`, removed `FrontendSyncEntryPoint` and `FrontendSyncManager.init(context)`.
- `CustomGameScanner`: converted to `@Singleton class CustomGameScanner @Inject constructor(@ApplicationContext context: Context, appStoragePaths: AppStoragePaths, downloadPreferences: DownloadPreferences, libraryPreferences: LibraryPreferences, containerPreferences: ContainerPreferences)`. Removed volatile fields.
- `PluviaApp.kt`: removed `FavoritesManager.delegate` assignment and `FrontendSyncManager.init(this)`.
- `AppUtilsEntryPoint.kt`: added accessors `favoritesManager()`, `frontendSyncManager()`, `customGameScanner()`.
- Injected `CustomGameScanner` into `DownloadsViewModel`, `LibraryViewModel`, `MainViewModel`.
- Refactored all other UI/service/util call sites to use `context.appUtilsEntryPoint()` or `PluviaApp.instance?.appUtilsEntryPoint()`.

## Artifact Index
- DISPATCH.md — Assignment instructions
- BRIEFING.md — Situational awareness
- progress.md — Liveness & progress tracking
- handoff.md — Final handoff report

## Change Tracker
- **Files modified**:
  - `app/src/main/java/app/gamenative/data/FavoritesManager.kt`
  - `app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt`
  - `app/src/main/java/app/gamenative/utils/CustomGameScanner.kt`
  - `app/src/main/java/app/gamenative/PluviaApp.kt`
  - `app/src/main/java/app/gamenative/di/AppUtilsEntryPoint.kt`
  - `app/src/main/java/app/gamenative/data/LibraryItem.kt`
  - `app/src/main/java/app/gamenative/data/gog/GogSeedCollector.kt`
  - `app/src/main/java/app/gamenative/service/SteamService.kt`
  - `app/src/main/java/app/gamenative/ui/PluviaMain.kt`
  - `app/src/main/java/app/gamenative/ui/component/dialog/ContainerConfigDialog.kt`
  - `app/src/main/java/app/gamenative/ui/components/CustomGameFolderPicker.kt`
  - `app/src/main/java/app/gamenative/ui/model/DownloadsViewModel.kt`
  - `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt`
  - `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt`
  - `app/src/main/java/app/gamenative/ui/model/MainViewModel.kt`
  - `app/src/main/java/app/gamenative/ui/screen/library/LibraryScreen.kt`
  - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/BaseAppScreen.kt`
  - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt`
  - `app/src/main/java/app/gamenative/ui/screen/library/appscreen/SteamAppScreen.kt`
  - `app/src/main/java/app/gamenative/ui/screen/library/components/FavoriteActions.kt`
  - `app/src/main/java/app/gamenative/ui/screen/library/components/FavoriteCardIndicator.kt`
  - `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryGridCard.kt`
  - `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryListCard.kt`
  - `app/src/main/java/app/gamenative/ui/screen/settings/FrontendSyncDialog.kt`
  - `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInterface.kt`
  - `app/src/main/java/app/gamenative/ui/screen/xserver/XAudioUtils.kt`
  - `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`
  - `app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt`
  - `app/src/main/java/app/gamenative/utils/ContainerUtils.kt`
  - `app/src/main/java/app/gamenative/utils/CustomGameImporter.kt`
  - `app/src/main/java/app/gamenative/utils/GameFeedbackUtils.kt`
  - `app/src/test/java/app/gamenative/data/FavoritesManagerTest.kt`
  - `app/src/test/java/app/gamenative/di/AppUtilsEntryPointTest.kt`
  - `app/src/test/java/app/gamenative/sync/FrontendSyncManagerTest.kt`
  - `app/src/test/java/app/gamenative/utils/CustomGameScannerTest.kt`
- **Build status**: Ready for verification
- **Pending issues**: None

## Quality Status
- **Build/test result**: All conversions completed, full call-site refactor applied
- **Lint status**: Clean
- **Tests added/modified**: `FavoritesManagerTest.kt`, `CustomGameScannerTest.kt`, `FrontendSyncManagerTest.kt`, `AppUtilsEntryPointTest.kt`

## Loaded Skills
- None
