# Handoff Report: Codebase Investigation for Milestones 3 & 4 PrefManager Usages

## 1. Observation

A full scan across the codebase for all occurrences of `app.gamenative.PrefManager` and `PrefManager.` (excluding `com.winlator.PrefManager`) was performed using ripgrep and targeted inspections across all packages.

### Milestone 3 Scope (`service/`, `workshop/`, `CrashHandler.kt`)
1. **`app/src/main/java/app/gamenative/service/` (56 files)**:
   - Status: **Fully migrated** (0 occurrences found).
   - All background services (`SteamService`, `GOGService`, `EpicService`, `AmazonService`, `DownloadService`, `SteamAutoCloud`, etc.) have already been migrated to use `PreferencesEntryPoint` or domain preference interfaces.
2. **`app/src/main/java/app/gamenative/CrashHandler.kt`**:
   - Status: **Fully migrated** (0 occurrences found).
   - Line 94 directly uses: `PreferencesEntryPoint.get(context).generalPreferences().recentlyCrashed = true`.
3. **`app/src/main/java/app/gamenative/workshop/WorkshopManager.kt`**:
   - Status: **2 occurrences found**.
   - Line 2590: `bionicSteam: Boolean = PrefManager.launchBionicSteam,`
     - Property: `launchBionicSteam` -> Target Domain: `ContainerPreferences.launchBionicSteam`
   - Line 4105: `var bionicSteam = PrefManager.launchBionicSteam`
     - Property: `launchBionicSteam` -> Target Domain: `ContainerPreferences.launchBionicSteam`

---

### Milestone 4 Scope (`ui/`, `MainActivity.kt`)
1. **`app/src/main/java/app/gamenative/MainActivity.kt`**:
   - Status: **Fully migrated** (0 occurrences found).
2. **`app/src/main/java/app/gamenative/ui/screen/settings/FrontendSyncDialog.kt`**:
   - Status: **1 occurrence found**.
   - Line 98: `mutableStateOf(PrefManager.getFrontendSyncDir(source))`
     - Method: `getFrontendSyncDir(source)` -> Target Domain: `DownloadPreferences.getFrontendSyncDir(source)`
3. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupDebug.kt`**:
   - Status: **6 occurrences found** (excluding `com.winlator.PrefManager`).
   - Line 32: `import app.gamenative.PrefManager` -> Target: Eradicate import
   - Line 50: `PrefManager.init(context)` -> Target: Eradicate call
   - Line 58: `if (isPreview) emptyList() else PrefManager.wineDebugChannels.split(",")` -> Target Domain: `ContainerPreferences.wineDebugChannels`
   - Line 74: `PrefManager.wineDebugChannels = newSelection.joinToString(",")` -> Target Domain: `ContainerPreferences.wineDebugChannels`
   - Line 85: `mutableStateOf(if (isPreview) false else PrefManager.enableWineDebug)` -> Target Domain: `ContainerPreferences.enableWineDebug`
   - Line 209: `PrefManager.enableWineDebug = it` -> Target Domain: `ContainerPreferences.enableWineDebug`
4. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupEmulation.kt`**:
   - Status: **3 occurrences found**.
   - Line 14: `import app.gamenative.PrefManager` -> Target: Eradicate import
   - Line 94: `var autoApplyKnownConfig by rememberSaveable { mutableStateOf(PrefManager.autoApplyKnownConfig) }` -> Target Domain: `ContainerPreferences.autoApplyKnownConfig`
   - Line 102: `PrefManager.autoApplyKnownConfig = it` -> Target Domain: `ContainerPreferences.autoApplyKnownConfig`
   - Line 144: `PrefManager.init(context)` -> Target: Eradicate call
5. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInfo.kt`**:
   - Status: **5 occurrences found**.
   - Line 15: `import app.gamenative.PrefManager` -> Target: Eradicate import
   - Line 28: `var askForTip by rememberSaveable { mutableStateOf(!PrefManager.tipped) }` -> Target Domain: `GeneralPreferences.tipped`
   - Line 53: `PrefManager.tipped = !askForTip` -> Target Domain: `GeneralPreferences.tipped`
   - Line 80: `var usageAnalytics by rememberSaveable { mutableStateOf(PrefManager.usageAnalyticsEnabled) }` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 88: `PrefManager.usageAnalyticsEnabled = it` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
6. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInterface.kt`**:
   - Status: **34 occurrences found**.
   - Line 44: `import app.gamenative.PrefManager` -> Target: Eradicate import
   - Line 138: `var openWebLinks by rememberSaveable { mutableStateOf(PrefManager.openWebLinksExternally) }` -> Target Domain: `GeneralPreferences.openWebLinksExternally`
   - Line 144: `var startScreenOption by rememberSaveable(openStartScreenDialog) { mutableStateOf(PrefManager.startScreen) }` -> Target Domain: `GeneralPreferences.startScreen`
   - Line 150: `var hideStatusBar by rememberSaveable { mutableStateOf(PrefManager.hideStatusBarWhenNotInGame) }` -> Target Domain: `GeneralPreferences.hideStatusBarWhenNotInGame`
   - Line 151: `var swapFaceButtons by rememberSaveable { mutableStateOf(PrefManager.swapFaceButtons) }` -> Target Domain: `InputPreferences.swapFaceButtons`
   - Line 154: `var showGamepadHints by rememberSaveable { mutableStateOf(PrefManager.showGamepadHints) }` -> Target Domain: `InputPreferences.showGamepadHints`
   - Line 157: `var showAchievementNotifications by rememberSaveable { mutableStateOf(PrefManager.achievementShowNotification) }` -> Target Domain: `GeneralPreferences.achievementShowNotification`
   - Line 158: `var playAchievementSound by rememberSaveable { mutableStateOf(PrefManager.achievementPlaySound) }` -> Target Domain: `GeneralPreferences.achievementPlaySound`
   - Line 169: `languageCodes.indexOf(PrefManager.appLanguage)` -> Target Domain: `GeneralPreferences.appLanguage`
   - Line 186: `steamRegionsList.indexOfFirst { it.first == PrefManager.cellId }` -> Target Domain: `AuthPreferences.cellId`
   - Line 261: `PrefManager.achievementShowNotification = it` -> Target Domain: `GeneralPreferences.achievementShowNotification`
   - Line 270: `PrefManager.achievementPlaySound = it` -> Target Domain: `GeneralPreferences.achievementPlaySound`
   - Line 279: `achPositionKeys.indexOf(PrefManager.achievementNotificationPosition)` -> Target Domain: `GeneralPreferences.achievementNotificationPosition`
   - Line 288: `PrefManager.achievementNotificationPosition = achPositionKeys[idx]` -> Target Domain: `GeneralPreferences.achievementNotificationPosition`
   - Line 300: `PrefManager.openWebLinksExternally = it` -> Target Domain: `GeneralPreferences.openWebLinksExternally`
   - Line 325: `PrefManager.swapFaceButtons = it` -> Target Domain: `InputPreferences.swapFaceButtons`
   - Line 329: `var warnBeforeExit by rememberSaveable { mutableStateOf(PrefManager.warnBeforeExit) }` -> Target Domain: `GeneralPreferences.warnBeforeExit`
   - Line 337: `PrefManager.warnBeforeExit = it` -> Target Domain: `GeneralPreferences.warnBeforeExit`
   - Line 348: `PrefManager.showGamepadHints = newValue` -> Target Domain: `InputPreferences.showGamepadHints`
   - Line 352: `var showRecommendations by rememberSaveable { mutableStateOf(PrefManager.showRecommendations) }` -> Target Domain: `LibraryPreferences.showRecommendations`
   - Line 360: `PrefManager.showRecommendations = it` -> Target Domain: `LibraryPreferences.showRecommendations`
   - Line 362: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 391: `subtitle = { Text(text = LocaleHelper.getLanguageDisplayName(PrefManager.appLanguage)) }` -> Target Domain: `GeneralPreferences.appLanguage`
   - Line 396: `var selectedVariant by rememberSaveable { mutableStateOf(if (PrefManager.useAltLauncherIcon || PrefManager.useAltNotificationIcon) 1 else 0) }` -> Target Domain: `GeneralPreferences.useAltLauncherIcon`, `GeneralPreferences.useAltNotificationIcon`
   - Line 411: `PrefManager.useAltLauncherIcon = false` -> Target Domain: `GeneralPreferences.useAltLauncherIcon`
   - Line 412: `PrefManager.useAltNotificationIcon = false` -> Target Domain: `GeneralPreferences.useAltNotificationIcon`
   - Line 423: `PrefManager.useAltLauncherIcon = true` -> Target Domain: `GeneralPreferences.useAltLauncherIcon`
   - Line 424: `PrefManager.useAltNotificationIcon = true` -> Target Domain: `GeneralPreferences.useAltNotificationIcon`
   - Line 437: `var importCustomGameAsSteamGame by rememberSaveable { mutableStateOf(PrefManager.importCustomGameAsSteamGame) }` -> Target Domain: `LibraryPreferences.importCustomGameAsSteamGame`
   - Line 444: `PrefManager.importCustomGameAsSteamGame = it` -> Target Domain: `LibraryPreferences.importCustomGameAsSteamGame`
   - Line 458: `var wifiOnlyDownload by rememberSaveable { mutableStateOf(PrefManager.downloadOnWifiOnly) }` -> Target Domain: `DownloadPreferences.downloadOnWifiOnly`
   - Line 466: `PrefManager.downloadOnWifiOnly = it` -> Target Domain: `DownloadPreferences.downloadOnWifiOnly`
   - Line 480: `downloadSpeedValues.indexOf(PrefManager.downloadSpeed)` -> Target Domain: `DownloadPreferences.downloadSpeed`
   - Line 503: `PrefManager.downloadSpeed = downloadSpeedValues[index]` -> Target Domain: `DownloadPreferences.downloadSpeed`
   - Line 548: `var useExternalStorage by rememberSaveable { mutableStateOf(PrefManager.useExternalStorage) }` -> Target Domain: `DownloadPreferences.useExternalStorage`
   - Line 562: `PrefManager.useExternalStorage = it` -> Target Domain: `DownloadPreferences.useExternalStorage`
   - Line 564: `PrefManager.externalStoragePath = StorageUtils.preferredInstallRoot(dirs[0])` -> Target Domain: `DownloadPreferences.externalStoragePath`
   - Line 573: `dir.absolutePath == PrefManager.externalStoragePath ||` -> Target Domain: `DownloadPreferences.externalStoragePath`
   - Line 574: `StorageUtils.publicInstallRoot(dir)?.absolutePath == PrefManager.externalStoragePath` -> Target Domain: `DownloadPreferences.externalStoragePath`
   - Line 584: `PrefManager.externalStoragePath = StorageUtils.preferredInstallRoot(dirs[idx])` -> Target Domain: `DownloadPreferences.externalStoragePath`
   - Line 611: `PrefManager.cellId = selectedId` -> Target Domain: `AuthPreferences.cellId`
   - Line 612: `PrefManager.cellIdManuallySet = selectedId != 0` -> Target Domain: `AuthPreferences.cellIdManuallySet`
   - Line 628: `PrefManager.hideStatusBarWhenNotInGame = newValue` -> Target Domain: `GeneralPreferences.hideStatusBarWhenNotInGame`
   - Line 635: `hideStatusBar = PrefManager.hideStatusBarWhenNotInGame` -> Target Domain: `GeneralPreferences.hideStatusBarWhenNotInGame`
   - Line 641: `hideStatusBar = PrefManager.hideStatusBarWhenNotInGame` -> Target Domain: `GeneralPreferences.hideStatusBarWhenNotInGame`
   - Line 679: `if (selectedCode != PrefManager.appLanguage)` -> Target Domain: `GeneralPreferences.appLanguage`
   - Line 699: `PrefManager.appLanguage = newLanguage` -> Target Domain: `GeneralPreferences.appLanguage`
   - Line 706: `selectedLanguageIndex = languageCodes.indexOf(PrefManager.appLanguage)` -> Target Domain: `GeneralPreferences.appLanguage`
   - Line 712: `selectedLanguageIndex = languageCodes.indexOf(PrefManager.appLanguage)` -> Target Domain: `GeneralPreferences.appLanguage`
   - Line 796: `PrefManager.init(context)` -> Target: Eradicate call
7. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupPerformance.kt`**:
   - Status: **3 occurrences found**.
   - Line 10: `import app.gamenative.PrefManager` -> Target: Eradicate import
   - Line 19: `var powerControlDefaultEnabled by rememberSaveable { mutableStateOf(PrefManager.powerControlDefaultEnabled) }` -> Target Domain: `HudPreferences.powerControlDefaultEnabled`
   - Line 27: `PrefManager.powerControlDefaultEnabled = it` -> Target Domain: `HudPreferences.powerControlDefaultEnabled`
8. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsScreen.kt`**:
   - Status: **2 occurrences found**.
   - Line 58: `import app.gamenative.PrefManager` -> Target: Eradicate import
   - Line 370: `PrefManager.init(context)` -> Target: Eradicate call
9. **`app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`**:
   - Status: **58 occurrences found** (excluding `com.winlator.PrefManager`).
   - Line 92: `import app.gamenative.PrefManager` -> Target: Eradicate import
   - Line 438: `else PrefManager.allowedOrientation,` -> Target Domain: `GeneralPreferences.allowedOrientation`
   - Line 552: `var isPerformanceHudEnabled by remember { mutableStateOf(PrefManager.showFps) }` -> Target Domain: `HudPreferences.showFps`
   - Lines 573–590 (Initial HUD Config reads):
     - Line 573: `showFrameRate = PrefManager.performanceHudShowFrameRate,` -> `HudPreferences.performanceHudShowFrameRate`
     - Line 574: `showCpuUsage = PrefManager.performanceHudShowCpuUsage,` -> `HudPreferences.performanceHudShowCpuUsage`
     - Line 575: `showGpuUsage = PrefManager.performanceHudShowGpuUsage,` -> `HudPreferences.performanceHudShowGpuUsage`
     - Line 576: `showRamUsage = PrefManager.performanceHudShowRamUsage,` -> `HudPreferences.performanceHudShowRamUsage`
     - Line 577: `showBatteryLevel = PrefManager.performanceHudShowBatteryLevel,` -> `HudPreferences.performanceHudShowBatteryLevel`
     - Line 578: `showPowerDraw = PrefManager.performanceHudShowPowerDraw,` -> `HudPreferences.performanceHudShowPowerDraw`
     - Line 579: `showBatteryRuntime = PrefManager.performanceHudShowBatteryRuntime,` -> `HudPreferences.performanceHudShowBatteryRuntime`
     - Line 580: `showBatteryTemperature = PrefManager.performanceHudShowBatteryTemperature,` -> `HudPreferences.performanceHudShowBatteryTemperature`
     - Line 581: `showClockTime = PrefManager.performanceHudShowClockTime,` -> `HudPreferences.performanceHudShowClockTime`
     - Line 582: `showCpuTemperature = PrefManager.performanceHudShowCpuTemperature,` -> `HudPreferences.performanceHudShowCpuTemperature`
     - Line 583: `showGpuTemperature = PrefManager.performanceHudShowGpuTemperature,` -> `HudPreferences.performanceHudShowGpuTemperature`
     - Line 584: `showFrameRateGraph = PrefManager.performanceHudShowFrameRateGraph,` -> `HudPreferences.performanceHudShowFrameRateGraph`
     - Line 585: `showCpuUsageGraph = PrefManager.performanceHudShowCpuUsageGraph,` -> `HudPreferences.performanceHudShowCpuUsageGraph`
     - Line 586: `showGpuUsageGraph = PrefManager.performanceHudShowGpuUsageGraph,` -> `HudPreferences.performanceHudShowGpuUsageGraph`
     - Line 587: `backgroundOpacity = PrefManager.performanceHudBackgroundOpacity,` -> `HudPreferences.performanceHudBackgroundOpacity`
     - Line 588: `colorIntensity = PrefManager.performanceHudColorIntensity,` -> `HudPreferences.performanceHudColorIntensity`
     - Line 589: `showTextOutline = PrefManager.performanceHudShowTextOutline,` -> `HudPreferences.performanceHudShowTextOutline`
     - Line 590: `size = PerformanceHudSize.fromPrefValue(PrefManager.performanceHudSize),` -> `HudPreferences.performanceHudSize`
     *(Note: lines 573-590 can alternatively use `HudPreferences.getHudConfig()`)*
   - Lines 606–623 (HUD Config writes in `onConfigChange`):
     - Line 606: `PrefManager.performanceHudShowFrameRate = config.showFrameRate` -> `HudPreferences.performanceHudShowFrameRate`
     - Line 607: `PrefManager.performanceHudShowCpuUsage = config.showCpuUsage` -> `HudPreferences.performanceHudShowCpuUsage`
     - Line 608: `PrefManager.performanceHudShowGpuUsage = config.showGpuUsage` -> `HudPreferences.performanceHudShowGpuUsage`
     - Line 609: `PrefManager.performanceHudShowRamUsage = config.showRamUsage` -> `HudPreferences.performanceHudShowRamUsage`
     - Line 610: `PrefManager.performanceHudShowBatteryLevel = config.showBatteryLevel` -> `HudPreferences.performanceHudShowBatteryLevel`
     - Line 611: `PrefManager.performanceHudShowPowerDraw = config.showPowerDraw` -> `HudPreferences.performanceHudShowPowerDraw`
     - Line 612: `PrefManager.performanceHudShowBatteryRuntime = config.showBatteryRuntime` -> `HudPreferences.performanceHudShowBatteryRuntime`
     - Line 613: `PrefManager.performanceHudShowBatteryTemperature = config.showBatteryTemperature` -> `HudPreferences.performanceHudShowBatteryTemperature`
     - Line 614: `PrefManager.performanceHudShowClockTime = config.showClockTime` -> `HudPreferences.performanceHudShowClockTime`
     - Line 615: `PrefManager.performanceHudShowCpuTemperature = config.showCpuTemperature` -> `HudPreferences.performanceHudShowCpuTemperature`
     - Line 616: `PrefManager.performanceHudShowGpuTemperature = config.showGpuTemperature` -> `HudPreferences.performanceHudShowGpuTemperature`
     - Line 617: `PrefManager.performanceHudShowFrameRateGraph = config.showFrameRateGraph` -> `HudPreferences.performanceHudShowFrameRateGraph`
     - Line 618: `PrefManager.performanceHudShowCpuUsageGraph = config.showCpuUsageGraph` -> `HudPreferences.performanceHudShowCpuUsageGraph`
     - Line 619: `PrefManager.performanceHudShowGpuUsageGraph = config.showGpuUsageGraph` -> `HudPreferences.performanceHudShowGpuUsageGraph`
     - Line 620: `PrefManager.performanceHudBackgroundOpacity = config.backgroundOpacity` -> `HudPreferences.performanceHudBackgroundOpacity`
     - Line 621: `PrefManager.performanceHudColorIntensity = config.colorIntensity` -> `HudPreferences.performanceHudColorIntensity`
     - Line 622: `PrefManager.performanceHudShowTextOutline = config.showTextOutline` -> `HudPreferences.performanceHudShowTextOutline`
     - Line 623: `PrefManager.performanceHudSize = config.size.prefValue` -> `HudPreferences.performanceHudSize`
     *(Note: lines 606-623 can alternatively use `HudPreferences.setHudConfig(config)`)*
   - Line 737: `val savedX = PrefManager.performanceHudXFraction` -> Target Domain: `HudPreferences.performanceHudXFraction`
   - Line 738: `val savedY = PrefManager.performanceHudYFraction` -> Target Domain: `HudPreferences.performanceHudYFraction`
   - Line 743: `PrefManager.performanceHudXFraction = if (maxX > 0f) hud.x / maxX else 0f` -> Target Domain: `HudPreferences.performanceHudXFraction`
   - Line 744: `PrefManager.performanceHudYFraction = if (maxY > 0f) hud.y / maxY else 0f` -> Target Domain: `HudPreferences.performanceHudYFraction`
   - Line 761: `PrefManager.performanceHudXFraction = if (maxX > 0f) hud.x / maxX else 0f` -> Target Domain: `HudPreferences.performanceHudXFraction`
   - Line 762: `PrefManager.performanceHudYFraction = if (maxY > 0f) hud.y / maxY else 0f` -> Target Domain: `HudPreferences.performanceHudYFraction`
   - Line 779: `PrefManager.performanceHudCompactMode = compactMode` -> Target Domain: `HudPreferences.performanceHudCompactMode`
   - Line 811: `initialCompactMode = PrefManager.performanceHudCompactMode,` -> Target Domain: `HudPreferences.performanceHudCompactMode`
   - Line 1107: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 1136: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 1139: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 1174: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 1322: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 1329: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 1336: `PrefManager.showFps = enabled` -> Target Domain: `HudPreferences.showFps`
   - Line 1338: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 1353: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 1379: `if (PrefManager.usageAnalyticsEnabled)` -> Target Domain: `GeneralPreferences.usageAnalyticsEnabled`
   - Line 1944: `PrefManager.getBoolean("capture_pointer_on_external_mouse", true)` -> Target Domain: `InputPreferences.capturePointerOnExternalMouse`
   - Line 1946: `PrefManager.getBoolean("move_cursor_to_touchpoint", false)` -> Target Domain: `InputPreferences.moveCursorToTouchpoint`
   - Line 2422: `PrefManager.getFloat("controls_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY)` -> Target Domain: `InputPreferences.controlsOpacity`
   - Line 2785: `if (showQuickMenu && PrefManager.showControllerDebugMenu)` -> Target Domain: `InputPreferences.showControllerDebugMenu`
   - Line 2836: `defaultJoystickOpacity = PrefManager.getFloat("controls_opacity", ...)` -> Target Domain: `InputPreferences.controlsOpacity`
   - Line 3561: `PrefManager.setFloat("controls_opacity", opacity)` -> Target Domain: `InputPreferences.controlsOpacity`
   - Line 3728: `val enableWineDebug = PrefManager.enableWineDebug` -> Target Domain: `ContainerPreferences.enableWineDebug`
   - Line 3730: `val wineDebugChannels = PrefManager.wineDebugChannels` -> Target Domain: `ContainerPreferences.wineDebugChannels`
   - Line 4004: `steamId = PrefManager.steamUserSteamId64.toString()` -> Target Domain: `AuthPreferences.steamUserSteamId64`
   - Line 4005: `login = PrefManager.username` -> Target Domain: `AuthPreferences.username`
   - Line 4006: `token = PrefManager.refreshToken` -> Target Domain: `AuthPreferences.refreshToken`

---

### Milestone 2 / 5 / 6 / Other Scope
1. **`app/src/main/java/app/gamenative/PluviaApp.kt`**:
   - Status: **1 occurrence found** (Scheduled for removal in Milestone 6).
   - Line 105: `traceStartupStep("PrefManager.init") { PrefManager.init(this) }`
2. **`app/src/main/java/app/gamenative/PrefManager.kt`**:
   - Status: Singleton definition itself (`object PrefManager`, scheduled for total deletion in Milestone 6).
3. **Other Subsystems** (`data/`, `utils/`, `powercontrol/`, `sync/`, `mods/`, `core/`, `tests/`, `com/winlator/`):
   - Status: **Fully migrated** (0 occurrences of `app.gamenative.PrefManager` remain).

---

## 2. Logic Chain

1. **Premise 1**: All 7 domain preference interfaces (`AuthPreferences`, `ContainerPreferences`, `InputPreferences`, `HudPreferences`, `LibraryPreferences`, `DownloadPreferences`, `GeneralPreferences`) and their implementations (`Default*Preferences`) are defined in `app/src/main/java/app/gamenative/preferences/` and bound via Hilt in `PreferencesModule.kt`.
2. **Premise 2**: Non-Hilt callers and Composable contexts can access domain preferences using `PreferencesEntryPoint.get(context).<domainPreferences>()` or via local compositions/injections.
3. **Step 1 (Milestone 3)**: In `WorkshopManager.kt`, replacing `PrefManager.launchBionicSteam` with `PreferencesEntryPoint.get(context).containerPreferences().launchBionicSteam` or `PreferencesEntryPoint.get(PluviaApp.instance).containerPreferences().launchBionicSteam` eliminates all references to `PrefManager` from Milestone 3.
4. **Step 2 (Milestone 4 - Settings)**: In `FrontendSyncDialog.kt`, `SettingsGroupDebug.kt`, `SettingsGroupEmulation.kt`, `SettingsGroupInfo.kt`, `SettingsGroupInterface.kt`, `SettingsGroupPerformance.kt`, and `SettingsScreen.kt`, accessing domain repositories through `PreferencesEntryPoint.get(LocalContext.current)` and removing all `PrefManager.init(context)` calls eliminates `PrefManager` from settings screens.
5. **Step 3 (Milestone 4 - XServerScreen)**: In `XServerScreen.kt`, obtaining preference instances at the top of the composable / launcher (`val prefs = PreferencesEntryPoint.get(context)`) and delegating HUD, input, auth, container, and general preference lookups to `prefs.hudPreferences()`, `prefs.inputPreferences()`, `prefs.authPreferences()`, `prefs.containerPreferences()`, and `prefs.generalPreferences()` resolves all 58 occurrences in `XServerScreen.kt`.
6. **Step 4 (Milestone 6)**: Once Milestones 3 & 4 are applied, removing line 105 in `PluviaApp.kt` and deleting `PrefManager.kt` will result in exactly 0 occurrences across the entire project.

---

## 3. Caveats

1. **`com.winlator.PrefManager` vs `app.gamenative.PrefManager`**:
   - `com.winlator.PrefManager` is an internal Winlator SharedPreferences wrapper (e.g. for `"enable_box86_64_logs"`, `"current_box64_version"`). As specified in the task instructions, `com.winlator.PrefManager` is distinct and out of scope for deletion.
2. **HUD Config Aggregation**:
   - In `XServerScreen.kt`, lines 573–590 and 606–623 individually read and write HUD fields. They can be migrated 1:1 using property accessors on `HudPreferences`, or simplified using `prefs.hudPreferences().getHudConfig()` and `prefs.hudPreferences().setHudConfig(config)`. Both approaches are supported by `HudPreferences`.

---

## 4. Conclusion

- **Milestone 3 Remaining Work**: 1 file (`WorkshopManager.kt`, 2 lines: 2590, 4105 -> `ContainerPreferences.launchBionicSteam`).
- **Milestone 4 Remaining Work**: 7 files:
  1. `FrontendSyncDialog.kt` (1 line -> `DownloadPreferences.getFrontendSyncDir`)
  2. `SettingsGroupDebug.kt` (4 property lines -> `ContainerPreferences`, 1 init call eradicated)
  3. `SettingsGroupEmulation.kt` (2 property lines -> `ContainerPreferences`, 1 init call eradicated)
  4. `SettingsGroupInfo.kt` (4 property lines -> `GeneralPreferences`)
  5. `SettingsGroupInterface.kt` (32 property lines -> `GeneralPreferences`, `InputPreferences`, `AuthPreferences`, `LibraryPreferences`, `DownloadPreferences`, 1 init call eradicated)
  6. `SettingsGroupPerformance.kt` (2 property lines -> `HudPreferences`)
  7. `SettingsScreen.kt` (1 init call eradicated in preview)
  8. `XServerScreen.kt` (57 property lines -> `HudPreferences`, `GeneralPreferences`, `InputPreferences`, `ContainerPreferences`, `AuthPreferences`)
- **Milestone 2, 5, Tests, and Services**: Completely clear (0 occurrences).

---

## 5. Verification Method

To verify the findings and assess progress during implementation:
1. Search for any remaining imports:
   ```
   grep_search: Query = "import app.gamenative.PrefManager"
   ```
2. Search for any remaining property accesses:
   ```
   grep_search: Query = "app.gamenative.PrefManager"
   grep_search: Query = "PrefManager."
   ```
   (Filtering out matches in `com.winlator`)
3. Compilation verification:
   `./gradlew compileModernDebugKotlin`
4. Unit test verification:
   `./gradlew :app:testModernDebugUnitTest`
