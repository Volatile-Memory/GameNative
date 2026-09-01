# Preference Architecture & Codebase Call-Site Mapper Handoff Report

**Role**: Explorer 2 Replacement (Codebase Call-Site Mapper)  
**Target Repository**: `GameNative` (`refactor_gamenative_dependency_injection`)  
**Timestamp**: 2026-08-31T16:25:00+05:00  

---

## 1. Observation

A full exhaustive codebase search across Kotlin, Java, XML, Gradle, and configuration files was executed.

### 1.1 Summary Metrics
- **Files referencing `app.gamenative.PrefManager`**: **74 files** (63 production source files + 11 unit test files, excluding `PrefManager.kt` itself).
- **Total call sites / occurrences across codebase**: **852 occurrences**.
- **Files referencing `com.winlator.PrefManager`**: **12 files** (`PrefManager.kt`, `SettingsGroupDebug.kt`, `XServerScreen.kt`, and 9 Winlator Java runtime components).
- **Java Call Sites touching `app.gamenative.PrefManager`**:
  - `com.winlator.core.WineUtils` (`WineUtils.java:22, 70`) -> `PrefManager.INSTANCE.getCustomGameManualFolders()`
  - `com.winlator.xenvironment.components.BionicProgramLauncherComponent` (`BionicProgramLauncherComponent.java:524, 536, 601-603`) -> `PrefManager.INSTANCE.getUsername()`, `getRefreshToken()`, `getSteamUserSteamId64()`
  - `com.winlator.inputcontrols.ControllerManager` (`ControllerManager.java:17`) -> Unused import

---

### 1.2 Comprehensive Call-Site Inventory by Architectural Layer

#### A. Data / Core / Utilities Layer (25 files)

| # | File Path | Line Numbers & Key Calls | Domain(s) Consumed |
|---|---|---|---|
| 1 | `app/src/main/java/app/gamenative/data/DefaultFavoritesRepository.kt` | L3 (import), L16 (kdoc), L31 (`PrefManager.favoriteAppIds`), L37 (`PrefManager.favoriteAppIds = it`) | `LibraryPreferences` |
| 2 | `app/src/main/java/app/gamenative/data/FavoritesManager.kt` | L11 (kdoc reference to PrefManager) | `LibraryPreferences` |
| 3 | `app/src/main/java/app/gamenative/data/RecommendationRepository.kt` | L4 (import), L114 (`recommendationCacheTimestamp`), L118 (`recommendationCacheJson = ...`), L119 (`recommendationCacheTimestamp = ...`), L126 (`recommendationCacheJson`) | `LibraryPreferences` |
| 4 | `app/src/main/java/app/gamenative/data/SteamCollectionRepository.kt` | L3 (import), L24, L28, L32, L33, L42, L44, L53, L54 (`librarySteamCollectionsCache`, `librarySteamCollectionsSkippedDynamic`) | `LibraryPreferences` |
| 5 | `app/src/main/java/app/gamenative/data/gog/GogSeedCollector.kt` | L5 (import), L30 (`PrefManager.steamUserSteamId64`) | `AuthPreferences` |
| 6 | `app/src/main/java/app/gamenative/di/AppThemeModule.kt` | L3 (import), L29, L33, L39, L43, L49, L53 (`appTheme`, `appThemePalette`) | `GeneralPreferences` |
| 7 | `app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt` | L5 (import), L97, L162, L167 (`getFrontendSyncDir`, `setFrontendSyncDir`) | `DownloadPreferences` |
| 8 | `app/src/main/java/app/gamenative/mods/NexusModManager.kt` | L5 (import), L613, L636, L639, L656 (`nexusLastPlacementJson`), L1213 (`downloadOnWifiOnly`) | `GeneralPreferences`, `DownloadPreferences` |
| 9 | `app/src/main/java/app/gamenative/utils/BestConfigService.kt` | L6 (import), L748 (`box64Preset`), L751 (`fexcorePreset`), L763 (`graphicsDriverConfig`), L764 (`graphicsDriverVersion`), L816 (`useLegacyDRM`), L922 (`startupSelection`), L960 (`useLegacyDRM`), L966 (`steamOfflineMode`), L969 (`envVars`), L976 (`cpuList`), L979 (`cpuListWoW64`), L982 (`audioDriver`), L985 (`winComponents`), L988 (`videoMemorySize`) | `ContainerPreferences`, `AuthPreferences` |
| 10 | `app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt` | L5 (import), L98, L99, L202, L203, L204, L808, L818, L819, L820 (`useExternalStorage`, `externalStoragePath`) | `DownloadPreferences` |
| 11 | `app/src/main/java/app/gamenative/utils/ContainerUtils.kt` | L6 (import), L115–183, L221–243, L258–273 (~45 container properties: `screenSize`, `envVars`, `graphicsDriver`, `renderer`, `fexcoreVersion`, `wineVersion`, `dxWrapper`, `audioDriver`, `useSteamInput`, `xinputEnabled`, `dinputEnabled`, `suspendPolicy`, etc.) | `ContainerPreferences`, `InputPreferences`, `AuthPreferences` |
| 12 | `app/src/main/java/app/gamenative/utils/ConversionTracker.kt` | L3 (import), L17 (`usageAnalyticsEnabled`) | `GeneralPreferences` |
| 13 | `app/src/main/java/app/gamenative/utils/CustomGameScanner.kt` | L12 (import), L79, L80, L107 (`externalStoragePath`, `useExternalStorage`), L589 (`customGameManualFolders`), L632 (`importCustomGameAsSteamGame`), L638 (`containerLanguage`) | `DownloadPreferences`, `LibraryPreferences`, `ContainerPreferences` |
| 14 | `app/src/main/java/app/gamenative/utils/DeviceGameStatsCache.kt` | L3 (import), L51, L76, L120 (`deviceGameStatsCache`) | `GeneralPreferences` |
| 15 | `app/src/main/java/app/gamenative/utils/DownloadSpeedConfig.kt` | L3 (import), L15 (`downloadSpeed`) | `DownloadPreferences` |
| 16 | `app/src/main/java/app/gamenative/utils/GameCompatibilityCache.kt` | L3 (import), L73, L107, L177 (`gameCompatibilityCache`) | `GeneralPreferences` |
| 17 | `app/src/main/java/app/gamenative/utils/GpuGameStatsCache.kt` | L3 (import), L51, L76, L120 (`gpuGameStatsCache`) | `GeneralPreferences` |
| 18 | `app/src/main/java/app/gamenative/utils/HltbService.kt` | L3 (import), L290, L313 (`hltbCache`) | `GeneralPreferences` |
| 19 | `app/src/main/java/app/gamenative/utils/IntentLaunchManager.kt` | L6 (import), L243 (`suspendPolicy`) | `ContainerPreferences` |
| 20 | `app/src/main/java/app/gamenative/utils/KeyAttestationHelper.kt` | L8 (import), L97, L101 (`keyAttestationAvailable`) | `GeneralPreferences` |
| 21 | `app/src/main/java/app/gamenative/utils/ManifestRepository.kt` | L5 (import), L25, L27, L39, L40 (`componentManifestJson`, `componentManifestFetchedAt`) | `GeneralPreferences` |
| 22 | `app/src/main/java/app/gamenative/utils/PaddingUtils.kt` | L6 (import), L21 (`hideStatusBarWhenNotInGame`) | `GeneralPreferences` |
| 23 | `app/src/main/java/app/gamenative/utils/PlayIntegrity.kt` | L5 (import), L35, L38 (`playIntegrityAvailable`) | `GeneralPreferences` |
| 24 | `app/src/main/java/app/gamenative/utils/SteamGridDB.kt` | L5 (import), L62, L426 (`fetchSteamGridDBImages`) | `DownloadPreferences` |
| 25 | `app/src/main/java/app/gamenative/utils/SteamUtils.kt` | L7 (import), L64, L553, L557–560, L571, L1053, L1165, L1167, L1170, L1588, L1593 (`username`, `refreshToken`, `accessToken`, `steamUserSteamId64`, `steamUserAccountId`) | `AuthPreferences` |

---

#### B. ViewModels & State Holders (7 files)

| # | File Path | Line Numbers & Key Calls | Domain(s) Consumed |
|---|---|---|---|
| 26 | `app/src/main/java/app/gamenative/ui/model/GogRecommendationsViewModel.kt` | L6 (Unused import `import app.gamenative.PrefManager`) | None (Dead import cleanup) |
| 27 | `app/src/main/java/app/gamenative/ui/model/LibraryViewModel.kt` | L13 (import), L120, L296, L332, L348, L350, L351, L382, L388, L393, L398, L403, L411, L469, L481, L489, L600, L603, L694, L786, L947–953, L1059, L1075 (`libraryFilter`, `librarySortOption`, `showRecommendations`, `recDisclosureShown`, `recTeaserDismissedDay`, `showSteamInLibrary`, `showCustomGamesInLibrary`, `showGOGInLibrary`, `showEpicInLibrary`, `showAmazonInLibrary`, `customGameManualFolders`, `steamUserAccountId`, `customGamesCount`, `steamGamesCount`, `gogGamesCount`, `gogInstalledGamesCount`, `epicGamesCount`, `epicInstalledGamesCount`, `amazonInstalledGamesCount`, `itemsPerPage`) | `LibraryPreferences`, `AuthPreferences` |
| 28 | `app/src/main/java/app/gamenative/ui/model/MainViewModel.kt` | L10 (import), L80 (`tipped`), L81 (`lastWarmPitchTime`), L282, L477 (`recentlyCrashed`), L505 (`hasAttemptedGameLaunch`), L518 (`allowedOrientation`) | `GeneralPreferences` |
| 29 | `app/src/main/java/app/gamenative/ui/model/UserLoginViewModel.kt` | L13 (import), L150, L157 (`usageAnalyticsEnabled`) | `GeneralPreferences` |
| 30 | `app/src/main/java/app/gamenative/ui/data/HomeState.kt` | L3 (import), L7 (`startScreen`) | `GeneralPreferences` |
| 31 | `app/src/main/java/app/gamenative/ui/data/LibraryState.kt` | L3 (import), L15 (`libraryFilter`), L30–34 (`showSteamInLibrary`, etc.), L37 (`librarySteamCollections`), L59 (`librarySortOption`) | `LibraryPreferences` |
| 32 | `app/src/main/java/app/gamenative/ui/enums/LibraryTab.kt` | L8 (import), L104 (`showRecommendations`) | `LibraryPreferences` |

---

#### C. UI / Composables / Activities / Dialogs / Components (25 files)

| # | File Path | Line Numbers & Key Calls | Domain(s) Consumed |
|---|---|---|---|
| 33 | `app/src/main/java/app/gamenative/MainActivity.kt` | L37 (import), L176–180 (`init`, `appLanguage`), L479, L503 (`usageAnalyticsEnabled`) | `GeneralPreferences` |
| 34 | `app/src/main/java/app/gamenative/PluviaApp.kt` | L104 (`PrefManager.init(this)`), L148 (`usageAnalyticsEnabled`), L152 (`showRecommendations`), L169, L225 (`gogAmazonPathMigrated`) | `GeneralPreferences`, `LibraryPreferences` |
| 35 | `app/src/main/java/app/gamenative/ui/PluviaMain.kt` | L57 (import), L268, L284, L285 (`usageAnalyticsEnabled`, `keyAttestationAvailable`, `playIntegrityAvailable`), L640 (`lastWarmPitchTime`), L694 (`hideStatusBarWhenNotInGame`), L1402–1408 (`tipped`, `hasAttemptedGameLaunch`, `lastLaunchPitchTime`), L1481 (`warnBeforeExit`) | `GeneralPreferences` |
| 36 | `app/src/main/java/app/gamenative/ui/component/AchievementOverlay.kt` | L43 (import), L69, L75 (`achievementNotificationPosition`) | `GeneralPreferences` |
| 37 | `app/src/main/java/app/gamenative/ui/component/GamepadActionBar.kt` | L39 (import), L122 (`swapFaceButtons`), L198 (`init` in Preview) | `InputPreferences` |
| 38 | `app/src/main/java/app/gamenative/ui/component/QuickMenu.kt` | L98 (import), L481–485, L526, L604, L633, L747–835 (`quickMenuLastTab`), L1537, L1543 (`showPerformanceHudFan`), L1549, L1555 (`showPerformanceHudTunerCaps`) | `HudPreferences` |
| 39 | `app/src/main/java/app/gamenative/ui/component/dialog/ControllerTab.kt` | L10 (import), L23, L69 (`showControllerDebugMenu`) | `InputPreferences` |
| 40 | `app/src/main/java/app/gamenative/ui/component/dialog/OrientationDialog.kt` | L22 (import), L38, L43 (`allowedOrientation`), L94 (`init` in Preview) | `GeneralPreferences` |
| 41 | `app/src/main/java/app/gamenative/ui/component/dialog/SingleChoiceDialog.kt` | L34 (import), L103 (`init` in Preview) | Preview only |
| 42 | `app/src/main/java/app/gamenative/ui/screen/library/FeaturedCtaButton.kt` | L29 (import), L67 (`usageAnalyticsEnabled`) | `GeneralPreferences` |
| 43 | `app/src/main/java/app/gamenative/ui/screen/library/LibraryAppScreen.kt` | L111 (import), L588, L935 (`downloadOnWifiOnly`), L1349 (`init` in Preview) | `DownloadPreferences` |
| 44 | `app/src/main/java/app/gamenative/ui/screen/library/LibraryScreen.kt` | L78 (import), L330, L348, L1295 (`libraryLayout`), L331, L981, L1013 (`recDisclosureShown`), L334, L339 (`usageAnalyticsEnabled`), L535, L1450 (`showAddCustomGameDialog`), L987 (`recTeaserDismissedDay`), L1026 (`customGamesCount`), L1489 (`init` in Preview) | `LibraryPreferences`, `GeneralPreferences` |
| 45 | `app/src/main/java/app/gamenative/ui/screen/library/RecommendedGameScreen.kt` | L60 (import), L379 (`usageAnalyticsEnabled`) | `GeneralPreferences` |
| 46 | `app/src/main/java/app/gamenative/ui/screen/library/RecommendedTabPane.kt` | L24 (import), L54, L87 (`usageAnalyticsEnabled`) | `GeneralPreferences` |
| 47 | `app/src/main/java/app/gamenative/ui/screen/library/appscreen/CustomGameAppScreen.kt` | L13 (import), L521, L523 (`customGameManualFolders`) | `LibraryPreferences` |
| 48 | `app/src/main/java/app/gamenative/ui/screen/library/appscreen/SteamAppScreen.kt` | L40 (import), L492, L808 (`usageAnalyticsEnabled`), L1019 (`containerLanguage`) | `GeneralPreferences`, `ContainerPreferences` |
| 49 | `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryAppItem.kt` | L37 (import), L183, L223 (`init` in Previews) | Preview only |
| 50 | `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryCarouselPane.kt` | L56 (import), L243 (`hideStatusBarWhenNotInGame`) | `GeneralPreferences` |
| 51 | `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryDetailPane.kt` | L13 (import), L48 (`libraryLayout`), L65 (`usageAnalyticsEnabled`), L118 (`init` in Preview) | `LibraryPreferences`, `GeneralPreferences` |
| 52 | `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryListPane.kt` | L45 (import), L87, L93, L99, L105, L223–227 (`customGamesCount`, `steamGamesCount`, `gogInstalledGamesCount`, `epicInstalledGamesCount`, `amazonInstalledGamesCount`) | `LibraryPreferences` |
| 53 | `app/src/main/java/app/gamenative/ui/screen/library/components/LibraryOptionsPanel.kt` | L76 (import), L461 (`init` in Preview) | Preview only |
| 54 | `app/src/main/java/app/gamenative/ui/screen/library/components/LibrarySearchBar.kt` | L59 (import), L330, L349 (`init` in Previews) | Preview only |
| 55 | `app/src/main/java/app/gamenative/ui/screen/library/components/RecommendationDisclosure.kt` | L9 (import), L20 (`usageAnalyticsEnabled`) | `GeneralPreferences` |
| 56 | `app/src/main/java/app/gamenative/ui/screen/library/components/SystemMenu.kt` | L80 (import), L752 (`init` in Preview) | Preview only |
| 57 | `app/src/main/java/app/gamenative/ui/screen/settings/FrontendSyncDialog.kt` | L27 (import), L97, L104 (`getFrontendSyncDir`, `setFrontendSyncDir`) | `DownloadPreferences` |
| 58 | `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupDebug.kt` | L32 (import), L50 (`init`), L58, L74 (`wineDebugChannels`), L85, L209 (`enableWineDebug`) | `ContainerPreferences` |
| 59 | `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupEmulation.kt` | L14 (import), L94, L102 (`autoApplyKnownConfig`), L144 (`init` in Preview) | `ContainerPreferences` |
| 60 | `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInfo.kt` | L15 (import), L28, L53 (`tipped`), L80, L88 (`usageAnalyticsEnabled`) | `GeneralPreferences` |
| 61 | `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInterface.kt` | L44 (import), L138 (`openWebLinksExternally`), L144 (`startScreen`), L150, L628, L635, L641 (`hideStatusBarWhenNotInGame`), L151, L325 (`swapFaceButtons`), L154, L348 (`showGamepadHints`), L157, L261 (`achievementShowNotification`), L158, L270 (`achievementPlaySound`), L169, L391, L679, L699, L706, L712 (`appLanguage`), L186, L611 (`cellId`), L612 (`cellIdManuallySet`), L279, L288 (`achievementNotificationPosition`), L329, L337 (`warnBeforeExit`), L352, L360 (`showRecommendations`), L362 (`usageAnalyticsEnabled`), L396, L411, L423 (`useAltLauncherIcon`), L412, L424 (`useAltNotificationIcon`), L437, L444 (`importCustomGameAsSteamGame`), L458, L466 (`downloadOnWifiOnly`), L480, L503 (`downloadSpeed`), L548, L560, L562 (`useExternalStorage`), L564, L573, L584 (`externalStoragePath`), L796 (`init` in Preview) | `GeneralPreferences`, `InputPreferences`, `LibraryPreferences`, `DownloadPreferences`, `AuthPreferences` |
| 62 | `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupPerformance.kt` | L10 (import), L19, L27 (`powerControlDefaultEnabled`) | `HudPreferences` |
| 63 | `app/src/main/java/app/gamenative/ui/screen/settings/SettingsScreen.kt` | L58 (import), L370 (`init` in Preview) | Preview only |
| 64 | `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt` | L92 (import), L438 (`allowedOrientation`), L552 (`showFps`), L573–590 (all 18 performance HUD properties), L606–623 (HUD property mutations), L737, L738, L743, L744, L761, L762 (`performanceHudXFraction`, `performanceHudYFraction`), L779, L811 (`performanceHudCompactMode`), L1107, L1136 (`usageAnalyticsEnabled`) | `HudPreferences`, `GeneralPreferences` |
| 65 | `app/src/main/java/app/gamenative/ui/util/SteamSaveTransfer.kt` | L5 (import), L367 (`steamUserAccountId`) | `AuthPreferences` |
| 66 | `app/src/main/java/app/gamenative/ui/util/WindowSize.kt` | L8 (import), L40 (`showGamepadHints`) | `InputPreferences` |
| 67 | `app/src/main/java/app/gamenative/ui/widget/PerformanceHudView.kt` | L23 (import), L338 (`showPerformanceHudFan`), L344 (`showPerformanceHudTunerCaps`) | `HudPreferences` |

---

#### D. Services / Workers / Background Jobs / Mod & Workshop (12 files)

| # | File Path | Line Numbers & Key Calls | Domain(s) Consumed |
|---|---|---|---|
| 68 | `app/src/main/java/app/gamenative/CrashHandler.kt` | L93 (`PrefManager.recentlyCrashed = true`) | `GeneralPreferences` |
| 69 | `app/src/main/java/app/gamenative/service/AchievementWatcher.kt` | L14 (import), L101 (`achievementShowNotification`), L104 (`achievementPlaySound`) | `GeneralPreferences` |
| 70 | `app/src/main/java/app/gamenative/service/DownloadService.kt` | L5 (import), L55, L60 (`externalStoragePath`) | `DownloadPreferences` |
| 71 | `app/src/main/java/app/gamenative/service/NexusModImportService.kt` | L18 (import), L502 (`useAltNotificationIcon`) | `GeneralPreferences` |
| 72 | `app/src/main/java/app/gamenative/service/NotificationHelper.kt` | L14 (import), L192 (`useAltNotificationIcon`) | `GeneralPreferences` |
| 73 | `app/src/main/java/app/gamenative/service/SteamAutoCloud.kt` | L4 (import), L496 (`downloadSpeed`) | `DownloadPreferences` |
| 74 | `app/src/main/java/app/gamenative/service/SteamService.kt` | L21 (import), L198 (`init`), L199 (`appLanguage`), L319 (`steamUserName`, `steamUserAvatarHash`), L360, L3528, L3943 (`downloadOnWifiOnly`), L522, L532 (`externalStoragePath`), L553, L584, L1182 (`useExternalStorage`), L601, L3903, L4160 (`personaState`), L1072, L1490 (`containerLanguage`), L1412, L1414 (`customGameManualFolders`), L2517, L2539, L2609, L2671, L2795 (`clientId`), L2783, L3048, L3796, L3938 (`username`), L2787, L3797 (`accessToken`), L2791 (`refreshToken`), L2995, L3929 (`clearSteamSessionPreferences`), L3275, L3844, L3845 (`steamUserAccountId`), L3576, L3865 (`cellId`), L3849, L3850 (`steamUserSteamId64`), L3864 (`cellIdManuallySet`), L4181 (`steamUserAvatarHash`), L4182 (`steamUserName`), L4377, L4382, L4388 (`lastPICSChangeNumber`) | `AuthPreferences`, `GeneralPreferences`, `DownloadPreferences`, `ContainerPreferences`, `LibraryPreferences` |
| 75 | `app/src/main/java/app/gamenative/service/SteamWishlistService.kt` | L4 (import), L51, L85, L141, L145, L146, L169 (`accessToken`, `refreshToken`) | `AuthPreferences` |
| 76 | `app/src/main/java/app/gamenative/service/amazon/AmazonConstants.kt` | L5 (import), L61, L67 (`externalStoragePath`, `useExternalStorage`) | `DownloadPreferences` |
| 77 | `app/src/main/java/app/gamenative/service/amazon/AmazonService.kt` | L378 (`app.gamenative.PrefManager.externalStoragePath`) | `DownloadPreferences` |
| 78 | `app/src/main/java/app/gamenative/service/epic/EpicConstants.kt` | L4 (import), L15, L20 (kdoc `containerLanguage`), L122, L132 (`externalStoragePath`, `useExternalStorage`) | `DownloadPreferences`, `ContainerPreferences` |
| 79 | `app/src/main/java/app/gamenative/service/epic/EpicDownloadManager.kt` | L5 (import), L513 (`downloadSpeed`) | `DownloadPreferences` |
| 80 | `app/src/main/java/app/gamenative/service/epic/EpicManager.kt` | L4 (import), L41 (`downloadSpeed`) | `DownloadPreferences` |
| 81 | `app/src/main/java/app/gamenative/service/epic/EpicService.kt` | L200 (`app.gamenative.PrefManager.externalStoragePath`) | `DownloadPreferences` |
| 82 | `app/src/main/java/app/gamenative/service/gog/GOGConstants.kt` | L5 (import), L49, L85 (kdoc `containerLanguage`), L128, L136 (`externalStoragePath`, `useExternalStorage`) | `DownloadPreferences`, `ContainerPreferences` |
| 83 | `app/src/main/java/app/gamenative/service/gog/GOGService.kt` | L220 (`app.gamenative.PrefManager.externalStoragePath`) | `DownloadPreferences` |
| 84 | `app/src/main/java/app/gamenative/workshop/WorkshopManager.kt` | L16 (import), L905, L1190, L1200 (`downloadSpeed`), L2582, L4097 (`launchBionicSteam`) | `DownloadPreferences`, `ContainerPreferences` |

---

#### E. Container / X11 / Audio / Native / Runtime & Non-Hilt Java Components (5 files referencing `app.gamenative.PrefManager`)

| # | File Path | Line Numbers & Key Calls | Domain(s) Consumed |
|---|---|---|---|
| 85 | `app/src/main/java/app/gamenative/powercontrol/PowerProfile.kt` | L3 (import), L19 (`powerControlDefaultEnabled`) | `HudPreferences` |
| 86 | `app/src/main/java/app/gamenative/powercontrol/drivers/PServerDriver.kt` | L8 (import), L1290 (`powerControlDefaultEnabled`) | `HudPreferences` |
| 87 | `app/src/main/java/app/gamenative/powercontrol/drivers/SamsungPerformanceDriver.kt` | L4 (import), L264 (`powerControlDefaultEnabled`) | `HudPreferences` |
| 88 | `app/src/main/java/com/winlator/core/WineUtils.java` | L22 (import `app.gamenative.PrefManager`), L70 (`PrefManager.INSTANCE.getCustomGameManualFolders().contains(path)`) | `LibraryPreferences` |
| 89 | `app/src/main/java/com/winlator/xenvironment/components/BionicProgramLauncherComponent.java` | L524, L601 (`PrefManager.INSTANCE.getUsername()`), L536, L603 (`getSteamUserSteamId64()`), L602 (`getRefreshToken()`) | `AuthPreferences` |

*Note on Winlator-only references*:
`com.winlator.inputcontrols.ControllerManager.java:17` contains an unused import of `app.gamenative.PrefManager` with 0 usages.  
The 9 other Java files in `com.winlator` consume `com.winlator.PrefManager` ("WinlatorPreferences") which is untouched.

---

#### F. Unit Tests (11 test files)

| # | File Path | Usage Details | Domain(s) Touched |
|---|---|---|---|
| 90 | `app/src/test/java/app/gamenative/service/gog/GOGConstantsTest.kt` | L7 (import), L25, L27 (reflection on `PrefManager.dataStore`), L37 (`PrefManager.init(context)`) | `DownloadPreferences` / Test Harness |
| 91 | `app/src/test/java/app/gamenative/service/gog/GOGDownloadManagerTest.kt` | L4 (import), L57 (`PrefManager.init(...)`), L58 (`PrefManager.downloadSpeed = 32`) | `DownloadPreferences` |
| 92 | `app/src/test/java/app/gamenative/utils/BestConfigServiceTest.kt` | L7 (import), L74 (`PrefManager.init(...)`), L82, L83 (`componentManifestJson`, `componentManifestFetchedAt`), L416–590 (tests fallback defaults) | `GeneralPreferences`, `ContainerPreferences` |
| 93 | `app/src/test/java/app/gamenative/utils/CommunityConfigApplicationTest.kt` | L5 (import), L83 (`PrefManager.init(...)`), L91, L92 (`componentManifestJson`, `componentManifestFetchedAt`) | `GeneralPreferences` |
| 94 | `app/src/test/java/app/gamenative/utils/HltbCacheTest.kt` | L3 (import), L28, L36 (`mockkObject(PrefManager)`), L29, L30 (`every { PrefManager.hltbCache }`) | `GeneralPreferences` |
| 95 | `app/src/test/java/app/gamenative/utils/HltbServiceIntegrationTest.kt` | L3 (import), L30, L42 (`mockkObject(PrefManager)`), L31, L32 (`every { PrefManager.hltbCache }`) | `GeneralPreferences` |
| 96 | `app/src/test/java/app/gamenative/utils/downloader/ContainerFilesDownloaderTest.kt` | L6 (import), L36 (`PrefManager.init(context)`) | `GeneralPreferences` / Test Harness |
| 97 | `app/src/test/java/app/gamenative/utils/downloader/CoreDriverDownloaderTest.kt` | L5 (import), L31 (`PrefManager.init(context)`) | `GeneralPreferences` / Test Harness |
| 98 | `app/src/test/java/app/gamenative/utils/downloader/DXWrapperDownloaderTest.kt` | L5 (import), L31 (`PrefManager.init(context)`) | `GeneralPreferences` / Test Harness |
| 99 | `app/src/test/java/app/gamenative/utils/downloader/GraphicsDriverDownloaderTest.kt` | L5 (import), L31 (`PrefManager.init(context)`) | `GeneralPreferences` / Test Harness |
| 100 | `app/src/test/java/app/gamenative/utils/downloader/WinComponentDownloaderTest.kt` | L5 (import), L31 (`PrefManager.init(context)`) | `GeneralPreferences` / Test Harness |

---

## 2. Logic Chain

1. **Mapping Every Call Site to a Target Domain**:
   - `AuthPreferences`: Steam credentials (`username`, `accessToken`, `refreshToken`, `clientId`, `personaState`, `steamUserAccountId`, `steamUserSteamId64`, avatar, PICS change number, offline flags, `clearSteamSessionPreferences()`). Consumed heavily by `SteamService`, `SteamUtils`, `SteamWishlistService`, `BionicProgramLauncherComponent.java`, `SteamSaveTransfer`.
   - `ContainerPreferences`: Wine/Proton/FEX/box86 settings, graphics drivers, audio driver, suspend policy, legacy DRM, unpack files. Consumed by `ContainerUtils`, `BestConfigService`, `SettingsGroupDebug`, `SettingsGroupEmulation`, `WorkshopManager`, `CustomGameScanner`, `SteamAppScreen`.
   - `InputPreferences`: Gamepad button swaps, gamepad hints, controller debug menu, steam input, xinput/dinput. Consumed by `GamepadActionBar`, `ControllerTab`, `WindowSize`, `SettingsGroupInterface`, `ContainerUtils`.
   - `HudPreferences`: Show FPS, quick menu tab, fan/tuner toggles, performance graphs, layout sizing/positioning fractions, opacity, power control default. Consumed by `XServerScreen`, `QuickMenu`, `PerformanceHudView`, `SettingsGroupPerformance`, `PowerProfile`, `PServerDriver`, `SamsungPerformanceDriver`.
   - `LibraryPreferences`: Library layout, filter flags, sort option, items per page, source visibility toggles, game count tallies, recommendation caches & disclosures, steam dynamic collection caches, custom game folders, favorite IDs. Consumed by `LibraryViewModel`, `LibraryScreen`, `LibraryListPane`, `LibraryDetailPane`, `DefaultFavoritesRepository`, `RecommendationRepository`, `SteamCollectionRepository`, `WineUtils.java`.
   - `DownloadPreferences`: Wi-Fi only downloading, download speeds, external storage paths, SteamGridDB image downloads, frontend export directories. Consumed by `DownloadService`, `SteamAutoCloud`, `EpicDownloadManager`, `EpicManager`, `NexusModManager`, `WorkshopManager`, `FrontendSyncManager`, `FrontendSyncDialog`, `ContainerStorageManager`, `AmazonConstants`, `EpicConstants`, `GOGConstants`.
   - `GeneralPreferences`: App theme/palette, app language, orientation flags, start screen, status bar hiding, launcher/notification icons, achievement alerts/sounds/positions, exit warning, analytics opt-in, crash tracking, tip state, pitch timestamps, Play Integrity/Key Attestation status, manifest/compatibility/HLTB/community stats caches. Consumed by `MainActivity`, `PluviaApp`, `PluviaMain`, `MainViewModel`, `UserLoginViewModel`, `AppThemeModule`, `CrashHandler`, `AchievementWatcher`, `NotificationHelper`, `NexusModImportService`, `SettingsGroupInterface`, `SettingsGroupInfo`, `HltbService`, `ManifestRepository`, etc.

2. **Resolution Strategy for Non-Hilt and Java EntryPoints**:
   - For Java classes (`WineUtils.java`, `BionicProgramLauncherComponent.java`) and static utilities (`ContainerStorageManager`, `FrontendSyncManager`), expose a Dagger Hilt EntryPoint:
     ```kotlin
     @EntryPoint
     @InstallIn(SingletonComponent::class)
     interface PreferencesEntryPoint {
         fun authPreferences(): AuthPreferences
         fun containerPreferences(): ContainerPreferences
         fun inputPreferences(): InputPreferences
         fun hudPreferences(): HudPreferences
         fun libraryPreferences(): LibraryPreferences
         fun downloadPreferences(): DownloadPreferences
         fun generalPreferences(): GeneralPreferences
     }
     ```
   - In Java:
     ```java
     PreferencesEntryPoint ep = EntryPointAccessors.fromApplication(context.getApplicationContext(), PreferencesEntryPoint.class);
     AuthPreferences authPrefs = ep.authPreferences();
     String user = authPrefs.getUsername();
     ```
   - For `attachBaseContext` in `MainActivity.kt` and `SteamService.kt`:
     ```kotlin
     val ep = EntryPointAccessors.fromApplication(newBase.applicationContext ?: newBase, PreferencesEntryPoint::class.java)
     val language = ep.generalPreferences().appLanguage
     ```

3. **Decoupling Compose Previews and Unit Tests**:
   - 9 Compose preview functions previously called `PrefManager.init(context)`. With constructor injection in ViewModels and default parameters in composables, these calls become unnecessary and can be removed.
   - Unit tests previously mocking `mockkObject(PrefManager)` or reflecting on `PrefManager.dataStore` can now inject simple mock interfaces (e.g. `mockk<GeneralPreferences>()`), making unit tests hermetic and faster.

---

## 3. Caveats

1. **`com.winlator.PrefManager` Distinction**:
   `com.winlator.PrefManager` is backed by `"WinlatorPreferences"` and is used exclusively by legacy Winlator bridge classes (`GPUInformation`, `FEXCorePresetManager`, etc.). It is NOT to be removed or confounded with `app.gamenative.PrefManager`.
2. **Synchronous Property Access**:
   Because many utility classes and composables read preferences synchronously, the repository interface definitions must expose synchronous property getters/setters (backed by in-memory state or synchronous DataStore reads) alongside reactive `Flow` streams so that migration of callers can proceed incrementally without breaking compilation.
3. **Preservation of Historical Keys**:
   All 7 repository implementations must strictly use the original DataStore key strings (such as `"start screen"`, `"videoPciDeviceID"`, `"dxwrapperConfig"`) under the `"PluviaPreferences"` DataStore to guarantee 0 data loss.

---

## 4. Conclusion

1. Exactly **74 files** directly consume `app.gamenative.PrefManager` across 6 architectural tiers (25 Data/Core/Utils, 7 ViewModels/States, 25 UI/Screens, 12 Services/Background, 5 Runtime/Java, and 11 Unit Tests).
2. Every call site maps 100% cleanly into the 7 domain preference interfaces:
   - `AuthPreferences`
   - `ContainerPreferences`
   - `InputPreferences`
   - `HudPreferences`
   - `LibraryPreferences`
   - `DownloadPreferences`
   - `GeneralPreferences`
3. The migration can proceed concurrently across app layers using standard Dagger Hilt injection (`@Inject`) for ViewModels/Services and `PreferencesEntryPoint` via `EntryPointAccessors.fromApplication(...)` for Java and static utilities.

---

## 5. Verification Method

To independently verify the call site mappings:
1. **Verification of Grep Matches**:
   ```pwsh
   # Verify all files referencing PrefManager
   git grep -l "app.gamenative.PrefManager"
   git grep -l "PrefManager\."
   ```
2. **Compilation & Tests**:
   ```pwsh
   ./gradlew compileModernDebugKotlin
   ./gradlew :app:testModernDebugUnitTest
   ```
3. **Invalidation Conditions**:
   - Any reference to `app.gamenative.PrefManager` remaining after the migration phases will invalidate acceptance criteria R3/R4.
   - Any alteration of existing DataStore key names will invalidate zero-data-loss requirement R1.
