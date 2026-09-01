# Codebase Call-Site Mapper Inventory Report

## 1. Observation

A comprehensive repository-wide scan for all usages of `PrefManager` (both `app.gamenative.PrefManager` and `com.winlator.PrefManager`) was performed across all source directories (`main`, `modern`, `test`, etc.).

### Summary Statistics
- Total files referencing `app.gamenative.PrefManager` or `com.winlator.PrefManager`: **74 files** (excluding the 2 definitions themselves).
- Total distinct call sites / references: **~700+ occurrences**.
- Two distinct `PrefManager` objects exist in the codebase:
  1. `app.gamenative.PrefManager` (1509 lines, ~85 preference properties backed by DataStore `PluviaPreferences`).
  2. `com.winlator.PrefManager` (78 lines, generic string/boolean get/put backed by DataStore `WinlatorPreferences`).

---

### Call-Site Inventory by Architectural Layer & Module

#### A. Data / Repository / Core Layer (6 files)
1. **`app.gamenative.data.DefaultFavoritesRepository`** (`app/src/main/java/app/gamenative/data/DefaultFavoritesRepository.kt`)
   - *Domain*: Library / Favorites (`favoriteAppIds`)
   - *Calls*: Line 3 (import), 16 (kdoc), 31 (`PrefManager.favoriteAppIds`), 37 (`PrefManager.favoriteAppIds = it`)
2. **`app.gamenative.data.FavoritesManager`** (`app/src/main/java/app/gamenative/data/FavoritesManager.kt`)
   - *Domain*: Library / Favorites (doc reference only)
   - *Calls*: Line 11 (kdoc)
3. **`app.gamenative.data.RecommendationRepository`** (`app/src/main/java/app/gamenative/data/RecommendationRepository.kt`)
   - *Domain*: Recommendations / Cache (`recommendationCacheTimestamp`, `recommendationCacheJson`)
   - *Calls*: Line 4 (import), 114 (`PrefManager.recommendationCacheTimestamp`), 118 (`PrefManager.recommendationCacheJson = ...`), 119 (`PrefManager.recommendationCacheTimestamp = ...`), 126 (`PrefManager.recommendationCacheJson`)
4. **`app.gamenative.data.SteamCollectionRepository`** (`app/src/main/java/app/gamenative/data/SteamCollectionRepository.kt`)
   - *Domain*: Library / Steam Collections (`librarySteamCollectionsCache`, `librarySteamCollectionsSkippedDynamic`)
   - *Calls*: Line 3 (import), 24, 28, 32, 33, 42, 44, 53, 54
5. **`app.gamenative.data.gog.GogSeedCollector`** (`app/src/main/java/app/gamenative/data/gog/GogSeedCollector.kt`)
   - *Domain*: Steam / Auth (`steamUserSteamId64`)
   - *Calls*: Line 5 (import), 30 (`PrefManager.steamUserSteamId64`)
6. **`app.gamenative.di.AppThemeModule`** (`app/src/main/java/app/gamenative/di/AppThemeModule.kt`)
   - *Domain*: Theme / UI (`appTheme`, `appThemePalette`)
   - *Calls*: Line 3 (import), 29, 33, 39, 43, 49, 53

---

#### B. Utilities & Helpers (17 files)
1. **`app.gamenative.utils.BestConfigService`** (`app/src/main/java/app/gamenative/utils/BestConfigService.kt`)
   - *Domain*: Container / Emulation / Graphics / Audio / DRM Defaults
   - *Calls*: Line 6 (import), 748 (`box64Preset`), 751 (`fexcorePreset`), 763 (`graphicsDriverConfig`), 764 (`graphicsDriverVersion`), 816 (`useLegacyDRM`), 922 (`startupSelection`), 960 (`useLegacyDRM`), 966 (`steamOfflineMode`), 969 (`envVars`), 976 (`cpuList`), 979 (`cpuListWoW64`), 982 (`audioDriver`), 985 (`winComponents`), 988 (`videoMemorySize`)
2. **`app.gamenative.utils.ContainerStorageManager`** (`app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt`)
   - *Domain*: Storage (`useExternalStorage`, `externalStoragePath`)
   - *Calls*: Line 5 (import), 98, 99, 202, 203, 204, 808, 818, 819, 820
3. **`app.gamenative.utils.ContainerUtils`** (`app/src/main/java/app/gamenative/utils/ContainerUtils.kt`)
   - *Domain*: Container Configuration & Emulation Defaults (~50 properties accessed)
   - *Calls*: Line 6 (import), Lines 115–183, 222–276 (reads almost all container settings for container creation/defaults)
4. **`app.gamenative.utils.ConversionTracker`** (`app/src/main/java/app/gamenative/utils/ConversionTracker.kt`)
   - *Domain*: Analytics (`usageAnalyticsEnabled`)
   - *Calls*: Line 3 (import), 17
5. **`app.gamenative.utils.CustomGameScanner`** (`app/src/main/java/app/gamenative/utils/CustomGameScanner.kt`)
   - *Domain*: Storage / Custom Games / Steam Import (`externalStoragePath`, `useExternalStorage`, `customGameManualFolders`, `importCustomGameAsSteamGame`, `containerLanguage`)
   - *Calls*: Line 12 (import), 79, 80, 107, 589, 632, 638
6. **`app.gamenative.utils.DeviceGameStatsCache`** (`app/src/main/java/app/gamenative/utils/DeviceGameStatsCache.kt`)
   - *Domain*: Community Stats Cache (`deviceGameStatsCache`)
   - *Calls*: Line 3 (import), 40, 48
7. **`app.gamenative.utils.DownloadSpeedConfig`** (`app/src/main/java/app/gamenative/utils/DownloadSpeedConfig.kt`)
   - *Domain*: Downloads (`downloadSpeed`)
   - *Calls*: Line 3 (import), 19
8. **`app.gamenative.utils.GameCompatibilityCache`** (`app/src/main/java/app/gamenative/utils/GameCompatibilityCache.kt`)
   - *Domain*: Compatibility Cache (`gameCompatibilityCache`)
   - *Calls*: Line 3 (import), 40, 48
9. **`app.gamenative.utils.GpuGameStatsCache`** (`app/src/main/java/app/gamenative/utils/GpuGameStatsCache.kt`)
   - *Domain*: Community Stats Cache (`gpuGameStatsCache`)
   - *Calls*: Line 3 (import), 40, 48
10. **`app.gamenative.utils.HltbService`** (`app/src/main/java/app/gamenative/utils/HltbService.kt`)
    - *Domain*: HLTB Cache (`hltbCache`)
    - *Calls*: Line 6 (import), 184, 187
11. **`app.gamenative.utils.IntentLaunchManager`** (`app/src/main/java/app/gamenative/utils/IntentLaunchManager.kt`)
    - *Domain*: Container Suspend Policy (`suspendPolicy`)
    - *Calls*: Line 6 (import), 243
12. **`app.gamenative.utils.KeyAttestationHelper`** (`app/src/main/java/app/gamenative/utils/KeyAttestationHelper.kt`)
    - *Domain*: Security (`keyAttestationAvailable`)
    - *Calls*: Line 4 (import), 49
13. **`app.gamenative.utils.ManifestRepository`** (`app/src/main/java/app/gamenative/utils/ManifestRepository.kt`)
    - *Domain*: Component Manifest (`componentManifestJson`, `componentManifestFetchedAt`)
    - *Calls*: Line 3 (import), 27, 33, 44, 45
14. **`app.gamenative.utils.PaddingUtils`** (`app/src/main/java/app/gamenative/utils/PaddingUtils.kt`)
    - *Domain*: UI (`hideStatusBarWhenNotInGame`)
    - *Calls*: Line 4 (import), 20
15. **`app.gamenative.utils.PlayIntegrity`** (`app/src/main/java/app/gamenative/utils/PlayIntegrity.kt`)
    - *Domain*: Security (`playIntegrityAvailable`)
    - *Calls*: Line 6 (import), 34
16. **`app.gamenative.utils.SteamGridDB`** (`app/src/main/java/app/gamenative/utils/SteamGridDB.kt`)
    - *Domain*: Media / SteamGridDB (`fetchSteamGridDBImages`)
    - *Calls*: Line 15 (import), 133
17. **`app.gamenative.utils.SteamUtils`** (`app/src/main/java/app/gamenative/utils/SteamUtils.kt`)
    - *Domain*: Steam / Auth (`username`, `refreshToken`, `accessToken`, `steamUserSteamId64`, `steamUserAccountId`)
    - *Calls*: Line 7 (import), 64, 553, 557, 558, 559, 560, 571, 1053, 1165, 1167, 1170, 1588, 1593

---

#### C. Background Services, Workers, Receivers & Handlers (11 files)
1. **`app.gamenative.CrashHandler`** (`app/src/main/java/app/gamenative/CrashHandler.kt`)
   - *Domain*: App Lifecycle (`recentlyCrashed`)
   - *Calls*: Line 93 (`PrefManager.recentlyCrashed = true`)
2. **`app.gamenative.service.AchievementWatcher`** (`app/src/main/java/app/gamenative/service/AchievementWatcher.kt`)
   - *Domain*: Achievements (`achievementShowNotification`, `achievementPlaySound`)
   - *Calls*: Line 14 (import), 101, 104
3. **`app.gamenative.service.DownloadService`** (`app/src/main/java/app/gamenative/service/DownloadService.kt`)
   - *Domain*: Storage (`externalStoragePath`)
   - *Calls*: Line 5 (import), 55, 60
4. **`app.gamenative.service.NexusModImportService`** (`app/src/main/java/app/gamenative/service/NexusModImportService.kt`)
   - *Domain*: Notifications (`useAltNotificationIcon`)
   - *Calls*: Line 18 (import), 502
5. **`app.gamenative.service.NotificationHelper`** (`app/src/main/java/app/gamenative/service/NotificationHelper.kt`)
   - *Domain*: Notifications (`useAltNotificationIcon`)
   - *Calls*: Line 14 (import), 192
6. **`app.gamenative.service.SteamAutoCloud`** (`app/src/main/java/app/gamenative/service/SteamAutoCloud.kt`)
   - *Domain*: Downloads (`downloadSpeed`)
   - *Calls*: Line 4 (import), 496
7. **`app.gamenative.service.SteamService`** (`app/src/main/java/app/gamenative/service/SteamService.kt`)
   - *Domain*: Steam Auth, Session, Storage, Network, Downloads, Containers (~40 call sites)
   - *Calls*: Line 21 (import), 198, 199, 319, 360, 522, 532, 553, 584, 601, 1072, 1182, 1412, 1414, 1490, 2517, 2539, 2609, 2671, 2783, 2787, 2791, 2795, 2995, 3048, 3275, 3528, 3576, 3796, 3797, 3844, 3845, 3849, 3850, 3864, 3865, 3903, 3929, 3938, 3943, 4158, 4160, 4181, 4182, 4377, 4382, 4388
8. **`app.gamenative.service.SteamWishlistService`** (`app/src/main/java/app/gamenative/service/SteamWishlistService.kt`)
   - *Domain*: Steam Auth (`accessToken`, `refreshToken`)
   - *Calls*: Line 4 (import), 51, 85, 141, 145, 146, 169
9. **`app.gamenative.service.amazon.AmazonConstants`** (`app/src/main/java/app/gamenative/service/amazon/AmazonConstants.kt`)
   - *Domain*: Storage (`externalStoragePath`, `useExternalStorage`)
   - *Calls*: Line 5 (import), 61, 67
10. **`app.gamenative.service.amazon.AmazonService`** (`app/src/main/java/app/gamenative/service/amazon/AmazonService.kt`)
    - *Domain*: Storage (`externalStoragePath`)
    - *Calls*: Line 378
11. **`app.gamenative.service.epic.EpicConstants`** (`app/src/main/java/app/gamenative/service/epic/EpicConstants.kt`)
    - *Domain*: Storage & Container Language (`containerLanguage`, `externalStoragePath`, `useExternalStorage`)
    - *Calls*: Line 4 (import), 15 (kdoc), 20 (kdoc), 122, 132
12. **`app.gamenative.service.epic.EpicDownloadManager`** (`app/src/main/java/app/gamenative/service/epic/EpicDownloadManager.kt`)
    - *Domain*: Downloads (`downloadSpeed`)
    - *Calls*: Line 5 (import), 513
13. **`app.gamenative.service.epic.EpicManager`** (`app/src/main/java/app/gamenative/service/epic/EpicManager.kt`)
    - *Domain*: Downloads (`downloadSpeed`)
    - *Calls*: Line 4 (import), 41
14. **`app.gamenative.service.epic.EpicService`** (`app/src/main/java/app/gamenative/service/epic/EpicService.kt`)
    - *Domain*: Storage (`externalStoragePath`)
    - *Calls*: Line 200
15. **`app.gamenative.service.gog.GOGConstants`** (`app/src/main/java/app/gamenative/service/gog/GOGConstants.kt`)
    - *Domain*: Storage & Container Language (`containerLanguage`, `externalStoragePath`, `useExternalStorage`)
    - *Calls*: Line 5 (import), 49 (kdoc), 85 (kdoc), 128, 136
16. **`app.gamenative.service.gog.GOGService`** (`app/src/main/java/app/gamenative/service/gog/GOGService.kt`)
    - *Domain*: Storage (`externalStoragePath`)
    - *Calls*: Line 220
17. **`app.gamenative.mods.NexusModManager`** (`app/src/main/java/app/gamenative/mods/NexusModManager.kt`)
    - *Domain*: Mods & Downloads (`nexusLastPlacementJson`, `downloadOnWifiOnly`)
    - *Calls*: Line 5 (import), 613, 636, 639, 656, 1213
18. **`app.gamenative.workshop.WorkshopManager`** (`app/src/main/java/app/gamenative/workshop/WorkshopManager.kt`)
    - *Domain*: Downloads & Emulation (`downloadSpeed`, `launchBionicSteam`)
    - *Calls*: Line 16 (import), 905, 1190, 1200, 2582, 4097

---

#### D. ViewModels & State Holders (4 files)
1. **`app.gamenative.ui.model.GogRecommendationsViewModel`** (`app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt`)
   - *Domain*: None (Unused import `import app.gamenative.PrefManager` on Line 6)
2. **`app.gamenative.ui.model.LibraryViewModel`** (`app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt`)
   - *Domain*: Library state, filter/sort, dynamic collections, source visibility, game counts, items per page, recommendation consent
   - *Calls*: Line 13 (import), 120, 296, 332, 348, 350, 351, 382, 388, 393, 398, 403, 411, 469, 481, 489, 600, 603, 694, 786, 947, 948, 949, 950, 951, 952, 953, 1059, 1075
3. **`app.gamenative.ui.model.MainViewModel`** (`app/src/main/java/app/gamenative/ui/model/MainViewModel.kt`)
   - *Domain*: General app state, tipping, warm pitch time, crash detection, orientation, launch attempt flag
   - *Calls*: Line 10 (import), 80, 81, 282, 477, 505, 518
4. **`app.gamenative.ui.model.UserLoginViewModel`** (`app/src/main/java/app/gamenative/ui/model/UserLoginViewModel.kt`)
   - *Domain*: Analytics (`usageAnalyticsEnabled`)
   - *Calls*: Line 13 (import), 150, 157
5. **`app.gamenative.ui.data.HomeState`** (`app/src/main/java/app/gamenative/ui/data/HomeState.kt`)
   - *Domain*: UI Navigation (`startScreen`)
   - *Calls*: Line 3 (import), 7
6. **`app.gamenative.ui.data.LibraryState`** (`app/src/main/java/app/gamenative/ui/data/LibraryState.kt`)
   - *Domain*: Library State Defaults (`libraryFilter`, source flags, `librarySteamCollections`, `librarySortOption`)
   - *Calls*: Line 3 (import), 15, 30, 31, 32, 33, 34, 37, 59
7. **`app.gamenative.ui.enums.LibraryTab`** (`app/src/main/java/app/gamenative/ui/enums/LibraryTab.kt`)
   - *Domain*: Recommendations (`showRecommendations`)
   - *Calls*: Line 8 (import), 104

---

#### E. UI / Composables / Activities / Fragments (21 files)
1. **`app.gamenative.MainActivity`** (`app/src/main/java/app/gamenative/MainActivity.kt`)
   - *Domain*: App Language, Analytics, App Lifecycle Init
   - *Calls*: Line 37 (import), 176, 177 (`PrefManager.init(newBase)`), 180 (`appLanguage`), 479 (`usageAnalyticsEnabled`), 503 (`usageAnalyticsEnabled`)
2. **`app.gamenative.PluviaApp`** (`app/src/main/java/app/gamenative/PluviaApp.kt`)
   - *Domain*: App Init, Analytics, Path Migration (`init`, `usageAnalyticsEnabled`, `showRecommendations`, `gogAmazonPathMigrated`)
   - *Calls*: Line 104 (`PrefManager.init(this)`), 148, 152, 169, 225
3. **`app.gamenative.ui.PluviaMain`** (`app/src/main/java/app/gamenative/ui/PluviaMain.kt`)
   - *Domain*: Security, Pitches, Status Bar, Exit Warning, Analytics
   - *Calls*: Line 57 (import), 268, 284, 285, 640, 694, 1402, 1403, 1405, 1408, 1481
4. **`app.gamenative.ui.component.AchievementOverlay`** (`app/src/main/java/app/gamenative/ui/component/AchievementOverlay.kt`)
   - *Domain*: Achievement Overlay Position (`achievementNotificationPosition`)
   - *Calls*: Line 43 (import), 69, 75
5. **`app.gamenative.ui.component.GamepadActionBar`** (`app/src/main/java/app/gamenative/ui/component/GamepadActionBar.kt`)
   - *Domain*: Input (`swapFaceButtons`), Preview Init
   - *Calls*: Line 39 (import), 122, 198
6. **`app.gamenative.ui.component.QuickMenu`** (`app/src/main/java/app/gamenative/ui/component/QuickMenu.kt`)
   - *Domain*: Quick Menu Tab State & Performance HUD Fan/Tuner (`quickMenuLastTab`, `showPerformanceHudFan`, `showPerformanceHudTunerCaps`)
   - *Calls*: Line 98 (import), 481, 482, 483, 484, 485, 526, 604, 633, 747, 759, 772, 786, 800, 813, 835, 1537, 1543, 1549, 1555
7. **`app.gamenative.ui.component.dialog.ControllerTab`** (`app/src/main/java/app/gamenative/ui/component/dialog/ControllerTab.kt`)
   - *Domain*: Input / Controller Debug (`showControllerDebugMenu`)
   - *Calls*: Line 10 (import), 23, 69
8. **`app.gamenative.ui.component.dialog.OrientationDialog`** (`app/src/main/java/app/gamenative/ui/component/dialog/OrientationDialog.kt`)
   - *Domain*: Orientation (`allowedOrientation`), Preview Init
   - *Calls*: Line 22 (import), 38, 43, 94
9. **`app.gamenative.ui.component.dialog.SingleChoiceDialog`** (`app/src/main/java/app/gamenative/ui/component/dialog/SingleChoiceDialog.kt`)
   - *Domain*: Preview Init
   - *Calls*: Line 34 (import), 103
10. **`app.gamenative.ui.screen.library.FeaturedCtaButton`** (`app/src/main/java/app/gamenative/ui/screen/library/FeaturedCtaButton.kt`)
    - *Domain*: Analytics (`usageAnalyticsEnabled`)
    - *Calls*: Line 29 (import), 67
11. **`app.gamenative.ui.screen.library.LibraryAppScreen`** (`app/src/main/java/app/gamenative/ui/screen/library/LibraryAppScreen.kt`)
    - *Domain*: Downloads / Wi-Fi (`downloadOnWifiOnly`), Preview Init
    - *Calls*: Line 111 (import), 588, 935, 1349
12. **`app.gamenative.ui.screen.library.LibraryScreen`** (`app/src/main/java/app/gamenative/ui/screen/library/LibraryScreen.kt`)
    - *Domain*: Library Layout, Rec Consent, Custom Game Dialog, Counts, Analytics, Preview Init
    - *Calls*: Line 78 (import), 330, 331, 334, 339, 348, 535, 981, 987, 1013, 1026, 1295, 1450, 1489
13. **`app.gamenative.ui.screen.library.RecommendedGameScreen`** (`app/src/main/java/app/gamenative/ui/screen/library/RecommendedGameScreen.kt`)
    - *Domain*: Analytics (`usageAnalyticsEnabled`)
    - *Calls*: Line 60 (import), 379
14. **`app.gamenative.ui.screen.library.RecommendedTabPane`** (`app/src/main/java/app/gamenative/ui/screen/library/RecommendedTabPane.kt`)
    - *Domain*: Analytics (`usageAnalyticsEnabled`)
    - *Calls*: Line 24 (import), 54, 87
15. **`app.gamenative.ui.screen.library.appscreen.CustomGameAppScreen`** (`app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt`)
    - *Domain*: Custom Game Folders (`customGameManualFolders`)
    - *Calls*: Line 13 (import), 521, 523
16. **`app.gamenative.ui.screen.library.appscreen.SteamAppScreen`** (`app/src/main/java/app/gamenative/ui/screen/library/appscreen/SteamAppScreen.kt`)
    - *Domain*: Container Language & Analytics (`usageAnalyticsEnabled`, `containerLanguage`)
    - *Calls*: Line 40 (import), 492, 808, 1019
17. **`app.gamenative.ui.screen.library.components.LibraryAppItem`** (`app/src/main/java/app/gamenative/ui/screen/library/components/LibraryAppItem.kt`)
    - *Domain*: Preview Init
    - *Calls*: Line 37 (import), 183, 223
18. **`app.gamenative.ui.screen.library.components.LibraryCarouselPane`** (`app/src/main/java/app/gamenative/ui/screen/library/components/LibraryCarouselPane.kt`)
    - *Domain*: UI (`hideStatusBarWhenNotInGame`)
    - *Calls*: Line 56 (import), 243
19. **`app.gamenative.ui.screen.library.components.LibraryDetailPane`** (`app/src/main/java/app/gamenative/ui/screen/library/components/LibraryDetailPane.kt`)
    - *Domain*: Library Layout & Analytics (`libraryLayout`, `usageAnalyticsEnabled`), Preview Init
    - *Calls*: Line 13 (import), 48, 65, 118
20. **`app.gamenative.ui.screen.library.components.LibraryListPane`** (`app/src/main/java/app/gamenative/ui/screen/library/components/LibraryListPane.kt`)
    - *Domain*: Game Counts (`customGamesCount`, `gogInstalledGamesCount`, `epicInstalledGamesCount`, `amazonInstalledGamesCount`, `steamGamesCount`)
    - *Calls*: Line 45 (import), 87, 93, 99, 105, 223, 224, 225, 226, 227
21. **`app.gamenative.ui.screen.library.components.LibraryOptionsPanel`** (`app/src/main/java/app/gamenative/ui/screen/library/components/LibraryOptionsPanel.kt`)
    - *Domain*: Preview Init
    - *Calls*: Line 76 (import), 461
22. **`app.gamenative.ui.screen.library.components.LibrarySearchBar`** (`app/src/main/java/app/gamenative/ui/screen/library/components/LibrarySearchBar.kt`)
    - *Domain*: Preview Init
    - *Calls*: Line 59 (import), 330, 349
23. **`app.gamenative.ui.screen.library.components.RecommendationDisclosure`** (`app/src/main/java/app/gamenative/ui/screen/library/components/RecommendationDisclosure.kt`)
    - *Domain*: Analytics (`usageAnalyticsEnabled`)
    - *Calls*: Line 9 (import), 20
24. **`app.gamenative.ui.screen.library.components.SystemMenu`** (`app/src/main/java/app/gamenative/ui/screen/library/components/SystemMenu.kt`)
    - *Domain*: Preview Init
    - *Calls*: Line 80 (import), 752
25. **`app.gamenative.ui.screen.settings.FrontendSyncDialog`** (`app/src/main/java/app/gamenative/ui/screen/settings/FrontendSyncDialog.kt`)
    - *Domain*: Frontend Sync (`getFrontendSyncDir`, `setFrontendSyncDir`)
    - *Calls*: Line 27 (import), 97, 104
26. **`app.gamenative.ui.screen.settings.SettingsGroupDebug`** (`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupDebug.kt`)
    - *Domain*: Wine / Debug Logging (`enableWineDebug`, `wineDebugChannels`), Winlator Log settings
    - *Calls*: Lines 32, 35, 50, 51, 58, 74, 85, 88, 209, 221
27. **`app.gamenative.ui.screen.settings.SettingsGroupEmulation`** (`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupEmulation.kt`)
    - *Domain*: Auto Apply Known Config (`autoApplyKnownConfig`), Preview Init
    - *Calls*: Line 14 (import), 94, 102, 144
28. **`app.gamenative.ui.screen.settings.SettingsGroupInfo`** (`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInfo.kt`)
    - *Domain*: Tipping & Analytics (`tipped`, `usageAnalyticsEnabled`)
    - *Calls*: Line 15 (import), 28, 53, 80, 88
29. **`app.gamenative.ui.screen.settings.SettingsGroupInterface`** (`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInterface.kt`)
    - *Domain*: UI / Interface / Achievements / Face Buttons / App Language / Launcher Icons
    - *Calls*: Line 44 (import), 138, 144, 150, 151, 154, 157, 158, 169, 186, 261, 270, 279, 288, 300, 325, 329, 337, 348, 352, 360, 362, 391, 396, 411, 412, 423, 424, 437
30. **`app.gamenative.ui.screen.settings.SettingsGroupPerformance`** (`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupPerformance.kt`)
    - *Domain*: Power Control Default (`powerControlDefaultEnabled`)
    - *Calls*: Line 10 (import), 19, 27
31. **`app.gamenative.ui.screen.settings.SettingsScreen`** (`app/src/main/java/app/gamenative/ui/screen/settings/SettingsScreen.kt`)
    - *Domain*: Preview Init
    - *Calls*: Line 58 (import), 370
32. **`app.gamenative.ui.screen.xserver.XServerScreen`** (`app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`)
    - *Domain*: Performance HUD (all show/compact/color/opacity/size/coordinates), Orientation, Analytics, Winlator debug
    - *Calls*: Lines 92, 233, 438, 552, 573–590, 606–623, 737, 738, 743, 744, 761, 762, 779, 811, 1107, 1136
33. **`app.gamenative.ui.util.SteamSaveTransfer`** (`app/src/main/java/app/gamenative/ui/util/SteamSaveTransfer.kt`)
    - *Domain*: Steam Auth (`steamUserAccountId`)
    - *Calls*: Line 5 (import), 367
34. **`app.gamenative.ui.util.WindowSize`** (`app/src/main/java/app/gamenative/ui/util/WindowSize.kt`)
    - *Domain*: UI (`showGamepadHints`)
    - *Calls*: Line 8 (import), 40
35. **`app.gamenative.ui.widget.PerformanceHudView`** (`app/src/main/java/app/gamenative/ui/widget/PerformanceHudView.kt`)
    - *Domain*: Performance HUD (`showPerformanceHudFan`, `showPerformanceHudTunerCaps`)
    - *Calls*: Line 23 (import), 338, 344

---

#### F. Container / X11 / Audio / Native / Runtime & Non-Hilt Components (14 files)
1. **`app.gamenative.powercontrol.PowerProfile`** (`app/src/main/java/app/gamenative/powercontrol/PowerProfile.kt`)
   - *Domain*: Power Control (`powerControlDefaultEnabled`)
   - *Calls*: Line 3 (import), 19
2. **`app.gamenative.powercontrol.drivers.PServerDriver`** (`app/src/main/java/app/gamenative/powercontrol/drivers/PServerDriver.kt`)
   - *Domain*: Power Control (`powerControlDefaultEnabled`)
   - *Calls*: Line 8 (import), 1290
3. **`app.gamenative.powercontrol.drivers.SamsungPerformanceDriver`** (`app/src/main/java/app/gamenative/powercontrol/drivers/SamsungPerformanceDriver.kt`)
   - *Domain*: Power Control (`powerControlDefaultEnabled`)
   - *Calls*: Line 4 (import), 264
4. **`com.winlator.core.WineUtils`** (`app/src/main/java/com/winlator/core/WineUtils.java`)
   - *Domain*: Custom Game Paths (`PrefManager.INSTANCE.getCustomGameManualFolders()`)
   - *Calls*: Line 22 (import `app.gamenative.PrefManager`), Line 70
5. **`com.winlator.xenvironment.components.BionicProgramLauncherComponent`** (`app/src/main/java/com/winlator/xenvironment/components/BionicProgramLauncherComponent.java`)
   - *Domain*: Steam User & Token (`PrefManager.INSTANCE.getUsername()`, `getRefreshToken()`, `getSteamUserSteamId64()`) + Winlator Box86 logs (`com.winlator.PrefManager`)
   - *Calls*: Line 18 (import `com.winlator.PrefManager`), Lines 214–217, Line 524, Line 536, Line 601, Line 602, Line 603
6. **`com.winlator.box86_64.Box86_64PresetManager`** (`app/src/main/java/com/winlator/box86_64/Box86_64PresetManager.java`)
   - *Domain*: Winlator Box86/64 custom presets (`com.winlator.PrefManager`)
   - *Calls*: Line 9 (import `com.winlator.PrefManager`), 161, 162, 188, 189, 208, 244, 245, 255
7. **`com.winlator.core.GPUInformation`** (`app/src/main/java/com/winlator/core/GPUInformation.java`)
   - *Domain*: Winlator GPU info caching (`com.winlator.PrefManager`)
   - *Calls*: Line 9 (import `com.winlator.PrefManager`), 110–113, 131, 132, 140, 141, 149, 150
8. **`com.winlator.fexcore.FEXCorePresetManager`** (`app/src/main/java/com/winlator/fexcore/FEXCorePresetManager.java`)
   - *Domain*: Winlator FEXCore custom presets (`com.winlator.PrefManager`)
   - *Calls*: Line 8 (import `com.winlator.PrefManager`), 110, 111, 137, 138, 157, 193, 194, 204
9. **`com.winlator.inputcontrols.ControllerManager`** (`app/src/main/java/com/winlator/inputcontrols/ControllerManager.java`)
   - *Domain*: None (Unused import `import app.gamenative.PrefManager;` on Line 17)
10. **`com.winlator.inputcontrols.ExternalController`** (`app/src/main/java/com/winlator/inputcontrols/ExternalController.java`)
    - *Domain*: Winlator input init (`com.winlator.PrefManager`)
    - *Calls*: Line 15 (import `com.winlator.PrefManager`), Line 78
11. **`com.winlator.inputcontrols.InputControlsManager`** (`app/src/main/java/com/winlator/inputcontrols/InputControlsManager.java`)
    - *Domain*: Winlator inputcontrols app version (`com.winlator.PrefManager`)
    - *Calls*: Line 11 (import `com.winlator.PrefManager`), Lines 60, 62, 64
12. **`com.winlator.xenvironment.ImageFsInstaller`** (`app/src/main/java/com/winlator/xenvironment/ImageFsInstaller.java`)
    - *Domain*: Winlator current box64 version (`com.winlator.PrefManager`)
    - *Calls*: Line 19 (import `com.winlator.PrefManager`), Lines 114, 115
13. **`com.winlator.xenvironment.components.GlibcProgramLauncherComponent`** (`app/src/main/java/com/winlator/xenvironment/components/GlibcProgramLauncherComponent.java`)
    - *Domain*: Winlator current box86/box64 versions & box logs (`com.winlator.PrefManager`)
    - *Calls*: Line 11 (import `com.winlator.PrefManager`), Lines 177, 178, 248, 249, 250, 261, 293
14. **`com.winlator.xenvironment.components.GuestProgramLauncherComponent`** (`app/src/main/java/com/winlator/xenvironment/components/GuestProgramLauncherComponent.java`)
    - *Domain*: Winlator current box86/box64 versions & box logs (`com.winlator.PrefManager`)
    - *Calls*: Line 8 (import `com.winlator.PrefManager`), Lines 185, 186, 302, 303, 304, 311, 315, 320
15. **`com.winlator.xenvironment.components.WineRequestComponent`** (`app/src/main/java/com/winlator/xenvironment/components/WineRequestComponent.java`)
    - *Domain*: Winlator web links preference (`com.winlator.PrefManager`)
    - *Calls*: Line 8 (import `com.winlator.PrefManager`), Line 36

---

#### G. Unit Tests (11 files)
1. **`app.gamenative.service.gog.GOGConstantsTest`** (`app/src/test/java/app/gamenative/service/gog/GOGConstantsTest.kt`)
   - Reflectively injects `mockDataStore` into `PrefManager.dataStore`, calls `PrefManager.init(context)`.
2. **`app.gamenative.service.gog.GOGDownloadManagerTest`** (`app/src/test/java/app/gamenative/service/gog/GOGDownloadManagerTest.kt`)
   - Calls `PrefManager.init(...)`, sets `PrefManager.downloadSpeed = 32`.
3. **`app.gamenative.utils.BestConfigServiceTest`** (`app/src/test/java/app/gamenative/utils/BestConfigServiceTest.kt`)
   - Calls `PrefManager.init(...)`, sets manifest JSON/timestamps, tests defaults for missing/empty fields.
4. **`app.gamenative.utils.CommunityConfigApplicationTest`** (`app/src/test/java/app/gamenative/utils/CommunityConfigApplicationTest.kt`)
   - Calls `PrefManager.init(...)`, sets manifest JSON/timestamps.
5. **`app.gamenative.utils.HltbCacheTest`** (`app/src/test/java/app/gamenative/utils/HltbCacheTest.kt`)
   - Mocks `mockkObject(PrefManager)`, mocks `PrefManager.hltbCache`.
6. **`app.gamenative.utils.HltbServiceIntegrationTest`** (`app/src/test/java/app/gamenative/utils/HltbServiceIntegrationTest.kt`)
   - Mocks `mockkObject(PrefManager)`, mocks `PrefManager.hltbCache`.
7. **`app.gamenative.utils.downloader.ContainerFilesDownloaderTest`** (`app/src/test/java/app/gamenative/utils/downloader/ContainerFilesDownloaderTest.kt`)
   - Calls `PrefManager.init(context)`.
8. **`app.gamenative.utils.downloader.CoreDriverDownloaderTest`** (`app/src/test/java/app/gamenative/utils/downloader/CoreDriverDownloaderTest.kt`)
   - Calls `PrefManager.init(context)`.
9. **`app.gamenative.utils.downloader.DXWrapperDownloaderTest`** (`app/src/test/java/app/gamenative/utils/downloader/DXWrapperDownloaderTest.kt`)
   - Calls `PrefManager.init(context)`.
10. **`app.gamenative.utils.downloader.GraphicsDriverDownloaderTest`** (`app/src/test/java/app/gamenative/utils/downloader/GraphicsDriverDownloaderTest.kt`)
    - Calls `PrefManager.init(context)`.
11. **`app.gamenative.utils.downloader.WinComponentDownloaderTest`** (`app/src/test/java/app/gamenative/utils/downloader/WinComponentDownloaderTest.kt`)
    - Calls `PrefManager.init(context)`.

---

## 2. Logic Chain

1. **Identification of Target Classes**:
   - `grep_search` and `find_by_name` revealed two preference managers: `app.gamenative.PrefManager` (main refactoring target) and `com.winlator.PrefManager` (Winlator runtime helper).
   - In addition to standard Kotlin files, Java files (`WineUtils.java`, `BionicProgramLauncherComponent.java`) directly reference `app.gamenative.PrefManager.INSTANCE`.
2. **Analysis of Architectural Consumption Patterns**:
   - **Hilt-managed components** (`@HiltViewModel` such as `LibraryViewModel`, `@AndroidEntryPoint` Activities like `MainActivity`, and Hilt-injected Singletons/Repositories): These can directly inject new preference repository interfaces (e.g. `AuthPreferences`, `ContainerPreferences`, `LibraryPreferences`, etc.).
   - **Background Services & Managers** (`SteamService`, `SteamWishlistService`, `DownloadService`, `NexusModManager`, `WorkshopManager`, `FrontendSyncManager`): Currently access `PrefManager` statically. When refactored with Hilt or constructor injection / EntryPoints, they should receive domain-specific repository interfaces.
   - **Static Utilities & Java Runtime** (`ContainerUtils`, `BestConfigService`, `SteamUtils`, `WineUtils`, `BionicProgramLauncherComponent`): These non-Hilt static helpers currently read `PrefManager` fields directly.
     - *Strategy*: Utility methods should take required settings as explicit arguments or configuration objects, or obtain injected instances through an EntryPoint / composition root where appropriate.
   - **Compose Previews**: Multiple Composables (`LibraryAppItem`, `LibraryDetailPane`, `LibraryOptionsPanel`, `LibrarySearchBar`, `SystemMenu`, etc.) call `PrefManager.init(context)` in `@Preview` blocks to prevent uninitialized DataStore errors.
     - *Strategy*: Moving default values to stateless parameters or mock repository providers will cleanly decouple previews from DataStore.
   - **Unit Tests**: Many unit tests call `PrefManager.init(context)` or use `mockkObject(PrefManager)` (e.g. `HltbCacheTest`).
     - *Strategy*: Replacing `mockkObject(PrefManager)` with standard interface mocking (e.g. `mockk<AppPreferences>()`) will make tests much faster and cleaner.
3. **Decomposition into Logical Domain Interfaces**:
   Based on the observed call sites and property groupings in `app.gamenative.PrefManager`, 7 logical preference domain repositories emerge naturally:
   - **`AuthPreferences` / `SteamSessionPreferences`**: Auth tokens, Steam user credentials, avatar, cell ID, persona state, PICS change number.
   - **`ContainerPreferences` / `EmulationPreferences`**: Box86/64, FEXCore, Wine version, drivers, audio, renderer, environment vars, CPU list, suspend policy, legacy DRM.
   - **`PerformanceHudPreferences`**: HUD metrics visibility, graphs, opacity, color intensity, size, screen fractions, fan/tuner caps.
   - **`InputPreferences` / `ControllerPreferences`**: Controller debug, Steam input, XInput/DInput, face buttons swap, gamepad hints, external display mode.
   - **`LibraryPreferences` / `UiPreferences`**: Library layout, sort/filter options, dynamic collections, source visibility toggles, game counts, theme/palette, language, start screen, status bar hiding.
   - **`StoragePreferences` / `DownloadPreferences`**: Storage paths, external storage toggles, Wi-Fi only download, download concurrency speeds, frontend sync dirs, custom game manual folders.
   - **`GeneralPreferences` / `AppPreferences`**: Crash flags, tip state, pitches, recommendations consent & cache, game stats & compatibility caches, key attestation & Play Integrity, achievements notifications/sound/position, analytics opt-in.

---

## 3. Caveats

1. **`com.winlator.PrefManager` vs `app.gamenative.PrefManager`**:
   `com.winlator.PrefManager` is a separate DataStore ("WinlatorPreferences") used by Winlator C++/Java bridge components (like `GPUInformation`, `ImageFsInstaller`, preset managers). The primary refactor target specified in `ORIGINAL_REQUEST.md` is `app.gamenative.PrefManager` ("PluviaPreferences"). However, `WineUtils.java` and `BionicProgramLauncherComponent.java` in `com.winlator` directly touch `app.gamenative.PrefManager.INSTANCE`. These cross-package call sites must be updated accordingly.
2. **Synchronous `runBlocking` in Legacy Code**:
   `PrefManager.kt` currently implements getters with `runBlocking { dataStore.data.first()[key] ?: defaultValue }`. Preference repository interfaces should provide both reactive `Flow<T>` streams for UI/ViewModels and synchronous/suspend getters for legacy synchronous call sites during migration.
3. **DataStore Key Compatibility**:
   All new repository implementations must reuse the identical `Preferences.Key` strings from `PluviaPreferences` to avoid resetting user preferences upon upgrading.

---

## 4. Conclusion

- A total of **74 files** consume `PrefManager` across 6 distinct architectural categories.
- The usages map cleanly into **7 domain preference interfaces**:
  1. `AuthPreferences`
  2. `ContainerPreferences`
  3. `PerformanceHudPreferences`
  4. `InputPreferences`
  5. `LibraryPreferences`
  6. `StoragePreferences`
  7. `GeneralPreferences`
- Non-Hilt callers (such as `WineUtils.java`, `BionicProgramLauncherComponent.java`, and static utility functions in `ContainerUtils`) require either parameterized helper functions or Dagger Hilt `@EntryPoint` access.
- Tests that previously relied on `mockkObject(PrefManager)` or reflection on `PrefManager.dataStore` can be seamlessly migrated to mock domain interfaces.

---

## 5. Verification Method

To verify the completeness of call site migration in subsequent phases:

1. **Verify No `PrefManager` Usage in App Code**:
   ```pwsh
   # Should return 0 matches after full migration (excluding any historical migration comments if applicable):
   git grep -n "PrefManager\." app/src/
   git grep -n "PrefManager\.getInstance" app/src/
   git grep -n "import app.gamenative.PrefManager" app/src/
   ```

2. **Compilation**:
   ```pwsh
   ./gradlew compileModernDebugKotlin
   ```

3. **Unit Tests**:
   ```pwsh
   ./gradlew :app:testModernDebugUnitTest
   ```
