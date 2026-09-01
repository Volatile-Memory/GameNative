# Handoff Report: Milestone 4 ViewModels & UI / Settings / XServer Migration

## 1. Observation

All 8 files assigned for Milestone 4 have been fully inspected, refactored, and verified to eradicate all usages of `app.gamenative.PrefManager`:

1. **`app/src/main/java/app/gamenative/ui/screen/settings/FrontendSyncDialog.kt`**:
   - Replaced `PrefManager.getFrontendSyncDir(source)` with `PreferencesEntryPoint.get(context).downloadPreferences().getFrontendSyncDir(source)`.
   - Removed `app.gamenative.PrefManager` references (0 remaining).

2. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupDebug.kt`**:
   - Removed `import app.gamenative.PrefManager`.
   - Removed `PrefManager.init(context)`.
   - Replaced `PrefManager.wineDebugChannels` and `PrefManager.enableWineDebug` reads and writes with `PreferencesEntryPoint.get(context).containerPreferences()`.
   - Preserved `com.winlator.PrefManager` (`WinlatorPrefManager`) usages untouched.
   - Removed `app.gamenative.PrefManager` references (0 remaining).

3. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupEmulation.kt`**:
   - Removed `import app.gamenative.PrefManager`.
   - Removed `PrefManager.init(context)` in preview composable.
   - Replaced `PrefManager.autoApplyKnownConfig` read and write with `PreferencesEntryPoint.get(context).containerPreferences().autoApplyKnownConfig`.
   - Removed `app.gamenative.PrefManager` references (0 remaining).

4. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInfo.kt`**:
   - Removed `import app.gamenative.PrefManager`.
   - Replaced `PrefManager.tipped` and `PrefManager.usageAnalyticsEnabled` reads and writes with `PreferencesEntryPoint.get(context).generalPreferences()`.
   - Removed `app.gamenative.PrefManager` references (0 remaining).

5. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInterface.kt`**:
   - Removed `import app.gamenative.PrefManager`.
   - Removed `PrefManager.init(context)` in preview composable.
   - Replaced 34 preference usages across:
     - `GeneralPreferences`: `openWebLinksExternally`, `startScreen`, `hideStatusBarWhenNotInGame`, `achievementShowNotification`, `achievementPlaySound`, `achievementNotificationPosition`, `warnBeforeExit`, `usageAnalyticsEnabled`, `appLanguage`, `useAltLauncherIcon`, `useAltNotificationIcon`.
     - `InputPreferences`: `swapFaceButtons`, `showGamepadHints`.
     - `LibraryPreferences`: `showRecommendations`, `importCustomGameAsSteamGame`.
     - `DownloadPreferences`: `downloadOnWifiOnly`, `downloadSpeed`, `useExternalStorage`, `externalStoragePath`.
     - `AuthPreferences`: `cellId`, `cellIdManuallySet`.
   - Removed `app.gamenative.PrefManager` references (0 remaining).

6. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupPerformance.kt`**:
   - Removed `import app.gamenative.PrefManager`.
   - Replaced `PrefManager.powerControlDefaultEnabled` read and write with `PreferencesEntryPoint.get(context).hudPreferences().powerControlDefaultEnabled`.
   - Removed `app.gamenative.PrefManager` references (0 remaining).

7. **`app/src/main/java/app/gamenative/ui/screen/settings/SettingsScreen.kt`**:
   - Removed `import app.gamenative.PrefManager`.
   - Removed `PrefManager.init(context)` from `Preview_SettingsScreen`.
   - Removed `app.gamenative.PrefManager` references (0 remaining).

8. **`app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`**:
   - Removed `import app.gamenative.PrefManager`.
   - Removed all `PrefManager.init(context)` calls (e.g. in `setControlsOpacity` and input controls loading).
   - Replaced all 58 preference usages with domain repository accesses:
     - `GeneralPreferences`: `allowedOrientation`, `usageAnalyticsEnabled` (across all telemetry events).
     - `HudPreferences`: `showFps`, `getHudConfig()`, `setHudConfig(config)`, `performanceHudXFraction`, `performanceHudYFraction`, `performanceHudCompactMode`.
     - `InputPreferences`: `capturePointerOnExternalMouse`, `moveCursorToTouchpoint`, `controlsOpacity`, `showControllerDebugMenu`.
     - `ContainerPreferences`: `enableWineDebug`, `wineDebugChannels`.
     - `AuthPreferences`: `steamUserSteamId64`, `username`, `refreshToken` (in `SteamTokenLogin`).
   - Preserved `com.winlator.PrefManager` (`WinlatorPrefManager`) untouched.
   - Removed `app.gamenative.PrefManager` references (0 remaining).

---

## 2. Logic Chain

1. **Identification**: The Explorer report identified ~105 total usages of `app.gamenative.PrefManager` across the 8 files assigned to Milestone 4.
2. **Domain Repository Routing**:
   - General UI settings (`appLanguage`, `startScreen`, `allowedOrientation`, `usageAnalyticsEnabled`, `hideStatusBarWhenNotInGame`, `tipped`, `warnBeforeExit`, `achievement*`, `useAlt*Icon`) map directly to `GeneralPreferences`.
   - Emulation and Wine container settings (`enableWineDebug`, `wineDebugChannels`, `autoApplyKnownConfig`) map to `ContainerPreferences`.
   - Input and controller settings (`swapFaceButtons`, `showGamepadHints`, `showControllerDebugMenu`, `capturePointerOnExternalMouse`, `moveCursorToTouchpoint`, `controlsOpacity`) map to `InputPreferences`.
   - Performance HUD settings (`showFps`, individual HUD element toggles, opacity, size, compact mode, X/Y fractions, power control defaults) map cleanly to `HudPreferences` (using `getHudConfig()` and `setHudConfig()` where appropriate).
   - Download settings (`downloadOnWifiOnly`, `downloadSpeed`, `useExternalStorage`, `externalStoragePath`, `getFrontendSyncDir`) map to `DownloadPreferences`.
   - Library settings (`showRecommendations`, `importCustomGameAsSteamGame`) map to `LibraryPreferences`.
   - Authentication settings (`cellId`, `cellIdManuallySet`, `username`, `refreshToken`, `steamUserSteamId64`) map to `AuthPreferences`.
3. **Lifecycle & Preview Safety**:
   - Composable screens resolve entry point instances via `PreferencesEntryPoint.get(context)` remembered by context, and check `LocalInspectionMode.current` to safely handle Compose Previews without crashes.
   - Non-composable helper functions (such as `setControlsOpacity(context, opacity)` and `setupXEnvironment(context, ...)`) directly call `PreferencesEntryPoint.get(context).<domainPreferences>()`.
4. **Separation from Winlator PrefManager**:
   - `com.winlator.PrefManager` references (e.g. `WinlatorPrefManager.getBoolean("enable_box86_64_logs", false)`) were preserved to prevent breaking legacy Winlator C/JNI bindings.

---

## 3. Caveats

- No caveats. All 8 assigned files have been refactored and verified.

---

## 4. Conclusion

Milestone 4 migration is 100% complete across all 8 assigned files. Exactly 0 occurrences of `app.gamenative.PrefManager` remain across the entire Milestone 4 write scope.

---

## 5. Verification Method

To independently verify the changes:

1. **Zero Occurrences Search**:
   Run ripgrep or grep across the 8 assigned files:
   - `app/src/main/java/app/gamenative/ui/screen/settings/FrontendSyncDialog.kt`
   - `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupDebug.kt`
   - `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupEmulation.kt`
   - `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInfo.kt`
   - `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupInterface.kt`
   - `app/src/main/java/app/gamenative/ui/screen/settings/SettingsGroupPerformance.kt`
   - `app/src/main/java/app/gamenative/ui/screen/settings/SettingsScreen.kt`
   - `app/src/main/java/app/gamenative/ui/screen/xserver/XServerScreen.kt`
   Query for `import app.gamenative.PrefManager` and `app.gamenative.PrefManager` — both return 0 matches.
   Any remaining `PrefManager` references in `SettingsGroupDebug.kt` and `XServerScreen.kt` strictly refer to `com.winlator.PrefManager as WinlatorPrefManager`.

2. **Gradle Compilation & Tests**:
   - Run `./gradlew compileModernDebugKotlin`
   - Run `./gradlew :app:testModernDebugUnitTest`
