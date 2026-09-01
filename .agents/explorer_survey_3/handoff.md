# Handoff Report — Explorer 3: DI Architecture & Build Verifier

## 1. Observation

### Codebase & Dependency Injection State
- **Application Class**: `PluviaApp` (`app/src/main/java/app/gamenative/PluviaApp.kt:54`) is annotated with `@HiltAndroidApp` and inherits from `SplitCompatApplication`. It injects `GOGGameDao`, `AmazonGameDao`, and `FavoritesRepository`. In `onCreate()` line 104, it initializes `PrefManager.init(this)`.
- **Hilt Version & Plugins**:
  - Dagger Hilt `2.55` (`gradle/libs.versions.toml:9`)
  - KSP `2.1.21-2.0.2` (`gradle/libs.versions.toml:21`)
  - Kotlin `2.1.21` (`gradle/libs.versions.toml:19`)
  - Android Gradle Plugin `8.8.0` (`gradle/libs.versions.toml:3`)
  - DataStore Preferences `1.1.2` (`gradle/libs.versions.toml:10`)
  - Room `2.8.4` (`gradle/libs.versions.toml:39`)
- **Existing Hilt Modules**:
  1. `app.gamenative.di.DatabaseModule` (`app/src/main/java/app/gamenative/di/DatabaseModule.kt:25`): `@InstallIn(SingletonComponent::class)`, `@Provides @Singleton` for `PluviaDatabase` (using `@ApplicationContext context: Context`) and 14 Room DAOs.
  2. `app.gamenative.di.RepositoryModule` (`app/src/main/java/app/gamenative/di/RepositoryModule.kt:11`): `@InstallIn(SingletonComponent::class)`, `@Binds @Singleton` for `FavoritesRepository` (`DefaultFavoritesRepository`).
  3. `app.gamenative.di.AppThemeModule` (`app/src/main/java/app/gamenative/di/AppThemeModule.kt:58`): `@InstallIn(SingletonComponent::class)`, `@Provides @Singleton` for `IAppTheme` (`AppThemeImpl`). Currently reads/writes `PrefManager.appTheme` and `PrefManager.appThemePalette`.
  4. `app.gamenative.core.coroutines.CoroutinesModule` (`app/src/main/java/app/gamenative/core/coroutines/CoroutinesModule.kt:13`): `@InstallIn(SingletonComponent::class)`, provides `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`, `@MainImmediateDispatcher`, `@UnconfinedDispatcher`, and `@ApplicationScope CoroutineScope`.
  5. `app.gamenative.core.system.SystemServicesModule` (`app/src/main/java/app/gamenative/core/system/SystemServicesModule.kt:24`): `@InstallIn(SingletonComponent::class)`, provides 13 Android system services using `@ApplicationContext context: Context`.
  6. `app.gamenative.core.appinfo.AppBuildInfoModule` (`app/src/main/java/app/gamenative/core/appinfo/AppBuildInfoModule.kt:9`): `@InstallIn(SingletonComponent::class)`, `@Binds @Singleton` `AppBuildInfo` -> `DefaultAppBuildInfo`.
  7. `app.gamenative.core.appinfo.StringResolverModule` (`app/src/main/java/app/gamenative/core/appinfo/StringResolverModule.kt:9`): `@InstallIn(SingletonComponent::class)`, `@Binds @Singleton` `StringResolver` -> `AndroidStringResolver`.
  8. `app.gamenative.core.id.IdModule` (`app/src/main/java/app/gamenative/core/id/IdModule.kt:9`): `@InstallIn(SingletonComponent::class)`, `@Binds @Singleton` `IdGenerator` -> `DefaultIdGenerator`.
  9. `app.gamenative.core.time.TimeModule` (`app/src/main/java/app/gamenative/core/time/TimeModule.kt:9`): `@InstallIn(SingletonComponent::class)`, `@Binds @Singleton` `TimeProvider` -> `SystemTimeProvider`.
  10. `app.gamenative.core.storage.StorageModule` (`app/src/main/java/app/gamenative/core/storage/StorageModule.kt:9`): `@InstallIn(SingletonComponent::class)`, `@Binds @Singleton` `AppStoragePaths` -> `AndroidAppStoragePaths`.
  11. `app.gamenative.core.runtime.RuntimeModule` (`app/src/main/java/app/gamenative/core/runtime/RuntimeModule.kt:9`): `@InstallIn(SingletonComponent::class)`, `@Binds @Singleton` `GameSessionManager` -> `DefaultGameSessionManager`.
  12. `app.gamenative.core.runtime.GameSessionModule` (`app/src/main/java/app/gamenative/core/runtime/GameSessionModule.kt:13`): `@InstallIn(GameSessionComponent::class)`, provides `@GameSessionScoped @GameSessionCoroutineScope CoroutineScope`.
- **Custom Components & EntryPoints**:
  - `GameSessionComponent` (`app/src/main/java/app/gamenative/core/runtime/GameSessionComponent.kt:12`): `@DefineComponent(parent = SingletonComponent::class)` representing session lifecycle.
  - `GameSessionEntryPoint` (`app/src/main/java/app/gamenative/core/runtime/GameSessionEntryPoint.kt:11`): `@EntryPoint @InstallIn(GameSessionComponent::class)`.
  - Established EntryPoint Access in Non-Hilt classes:
    - `NexusModManager` (`app/src/main/java/app/gamenative/mods/NexusModManager.kt:89`): `@EntryPoint @InstallIn(SingletonComponent::class) interface ModDaoEntryPoint` -> resolved with `EntryPointAccessors.fromApplication(context, ModDaoEntryPoint::class.java)`.
    - `ContainerStorageManager` (`app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt:47`): `@EntryPoint @InstallIn(SingletonComponent::class) interface StorageManagerDaoEntryPoint` -> resolved with `EntryPointAccessors.fromApplication(context.applicationContext, StorageManagerDaoEntryPoint::class.java)`.
    - `FrontendSyncManager` (`app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt:43`): `@EntryPoint @InstallIn(SingletonComponent::class) interface FrontendSyncEntryPoint` -> resolved with `EntryPointAccessors.fromApplication(context, FrontendSyncEntryPoint::class.java)`.
    - `AmazonService` (`app/src/main/java/app/gamenative/service/amazon/AmazonService.kt:46`): `@EntryPoint @InstallIn(SingletonComponent::class) interface AmazonDaoEntryPoint`.
- **Android EntryPoints & ViewModels**:
  - Activities: `MainActivity` (`@AndroidEntryPoint`), `ImmersiveXrActivity` (`@AndroidEntryPoint`).
  - Services: `SteamService` (`@AndroidEntryPoint`), `EpicService` (`@AndroidEntryPoint`), `GOGService` (`@AndroidEntryPoint`), `AmazonService` (`@AndroidEntryPoint`).
  - ViewModels with `@HiltViewModel`: `MainViewModel`, `LibraryViewModel`, `DownloadsViewModel`, `GogRecommendationsViewModel`.
  - ViewModels currently without `@HiltViewModel`: `UserLoginViewModel`, `HomeViewModel`, `XServerViewModel`.
- **PrefManager Analysis**:
  - `app.gamenative.PrefManager` (`app/src/main/java/app/gamenative/PrefManager.kt`): 1509 lines, singleton `object PrefManager`.
  - DataStore name: `"PluviaPreferences"` (`preferencesDataStore(name = "PluviaPreferences", ...)`).
  - Usages: ~100 files across UI, ViewModels, Services, Utils, Data Repositories, and Java components.
  - Java accesses: `WineUtils.java:70` and `BionicProgramLauncherComponent.java:524, 536, 601-603` access `app.gamenative.PrefManager.INSTANCE`.
  - Note on Winlator Preferences: `com.winlator.PrefManager` (`app/src/main/java/com/winlator/PrefManager.kt`) is a distinct 78-line manager for `"WinlatorPreferences"`. `ControllerManager.java` had an unused import of `app.gamenative.PrefManager` which can be cleaned up.
- **Gradle Build & Flavors**:
  - Dimensions: `androidApi`
  - Flavors: `legacy` (minSdk 26, targetSdk 28), `legacyXr` (minSdk 26, targetSdk 28), `modern` (minSdk 29, targetSdk 36), `modernXr` (minSdk 29, targetSdk 36).
  - Build Types: `debug`, `release`, `release-signed`, `release-gold`.
  - Primary verification targets:
    - Compile: `./gradlew compileModernDebugKotlin`
    - Test: `./gradlew :app:testModernDebugUnitTest`
  - Gradle User Home configured on `D:\`. Build cache enabled; `--no-build-cache` flag prohibited unless strictly needed.

---

## 2. Logic Chain

1. **Requirement R1 & R2 (Preference Repositories & Dagger Hilt)**:
   - `PrefManager` currently acts as a monolithic singleton managing over 60 distinct preference keys with mixed responsibilities (Auth, Container configs, HUD toggles, Downloads, Steam/GOG/Epic credentials, Theme, UI state).
   - Because `PluviaApp` already uses `@HiltAndroidApp` with `SingletonComponent`, preference repositories must be defined as interfaces, implemented with `@Inject constructor(private val dataStore: DataStore<Preferences>, ...)` or injected dependencies, and exposed via `@InstallIn(SingletonComponent::class)` modules using `@Binds @Singleton`.
   - To guarantee **zero data loss** (R1 requirement), the DataStore name MUST remain `"PluviaPreferences"` and all preference keys (`stringPreferencesKey`, `booleanPreferencesKey`, `intPreferencesKey`, `floatPreferencesKey`, `longPreferencesKey`, `byteArrayPreferencesKey`) must use the exact identical string identifiers currently defined in `PrefManager.kt`.

2. **Domain Grouping Recommendation for Preference Repositories**:
   - `AuthPreferences`: Steam credentials (`user_name`, `access_token_enc`, `refresh_token_enc`, `steam_id`, `stable_anonymous_user_id`, `clearSteamSessionPreferences()`).
   - `ContainerPreferences`: Wine/Proton/FEX/box86 settings (`screen_size`, `env_vars`, `graphics_driver`, `graphics_driver_version`, `graphics_driver_config`, `renderer_present_mode`, `display_renderer_mode`, `sf_compat_mode`, `use_legacy_renderer`, `sharpness_effect`, `sharpness_level`, `sharpness_denoise`, `container_variant`, `wine_version`, `emulator`, `fexcore_version`, `fexcore_tso_mode`, `fexcore_x87_mode`, `fexcore_multiblock`, `dxwrapper`, `dxwrapperConfig`, `audio_driver`, `pulseaudio_low_latency`, `wincomponents`, `drives`, `custom_game_manual_folders`).
   - `PerformancePreferences`: HUD display toggles (`show_fps`, `performance_hud_compact_mode`, `performance_hud_show_frame_rate`, `performance_hud_show_cpu_usage`, `performance_hud_show_gpu_usage`, `performance_hud_show_ram_usage`, `performance_hud_show_battery_level`, `performance_hud_show_power_draw`, `performance_hud_show_battery_runtime`, `performance_hud_show_battery_temperature`, `performance_hud_show_clock_time`, `performance_hud_show_cpu_temperature`, `performance_hud_show_gpu_temperature`, `performance_hud_show_fan`, `performance_hud_show_tuner_caps`, graphs, opacity, color intensity, outline, size, position, auto-tuning profiles, power profiles).
   - `ThemePreferences` / `IAppTheme`: App theme and palette (`app_theme`, `app_theme_palette`). `AppThemeImpl` should be refactored to consume `ThemePreferences` instead of `PrefManager`.
   - `DownloadPreferences`: Download & storage settings (`auto_update_games`, `download_cellular`, `download_speed_limit`, `install_location`, `sd_card_path`, `automatic_shader_pre_caching`).
   - `SteamPreferences`: Steam ecosystem properties (`steam_branch`, `steam_cell_id`, `steam_persona_state`, `steam_persona_name`, `steam_force_run_sync_timestamp`, `component_manifest_json`, `component_manifest_fetched_at`, `last_pics_change_number`, `steam_app_run_map`, `steam_cache_version`, `steam_cloud_sync`).
   - `GogPreferences`: GOG account and sync settings (`gog_user_id`, `gog_access_token`, `gog_refresh_token`, `gog_expires_in`, `gog_sync_config_path`, `gog_export_dir`).
   - `EpicPreferences`: Epic account and sync settings (`epic_account_id`, `epic_display_name`, `epic_access_token`, `epic_refresh_token`, `epic_expires_at`, `epic_export_dir`, `epic_cloud_sync_enabled`).
   - `AmazonPreferences`: Amazon account and sync settings (`amazon_user_id`, `amazon_access_token`, `amazon_refresh_token`, `amazon_export_dir`).
   - `UiPreferences`: General UI and navigation state (`app_language`, `app_orientation`, `initial_screen`, `quick_menu_last_tab`, `default_filter`, `homepage_favorite_category`, `side_panel_open`, `favorite_app_ids`, `force_expanded_tablet_library`, `rec_disclosure_shown`, `show_recommendations`, `tipped`, `last_warm_pitch_time`).
   - `AnalyticsPreferences`: Analytics telemetry settings (`usage_analytics_enabled`, `crash_reporting_enabled`).

3. **Requirement R2 & Non-Hilt Classes (EntryPoint Pattern)**:
   - For classes not managed by Hilt (e.g. `ContainerStorageManager`, `FrontendSyncManager`, `NexusModManager`, `WineUtils.java`, `BionicProgramLauncherComponent.java`), create a consolidated `@EntryPoint`:
     ```kotlin
     @EntryPoint
     @InstallIn(SingletonComponent::class)
     interface PreferencesEntryPoint {
         fun authPreferences(): AuthPreferences
         fun containerPreferences(): ContainerPreferences
         fun performancePreferences(): PerformancePreferences
         fun themePreferences(): ThemePreferences
         fun downloadPreferences(): DownloadPreferences
         fun steamPreferences(): SteamPreferences
         fun gogPreferences(): GogPreferences
         fun epicPreferences(): EpicPreferences
         fun amazonPreferences(): AmazonPreferences
         fun uiPreferences(): UiPreferences
         fun analyticsPreferences(): AnalyticsPreferences
     }
     ```
   - For Kotlin callers:
     ```kotlin
     val prefs = EntryPointAccessors.fromApplication(context.applicationContext, PreferencesEntryPoint::class.java)
     ```
   - For Java callers (`WineUtils.java`, `BionicProgramLauncherComponent.java`):
     ```java
     PreferencesEntryPoint ep = EntryPointAccessors.fromApplication(context.getApplicationContext(), PreferencesEntryPoint.class);
     AuthPreferences authPrefs = ep.authPreferences();
     ```
   - For early lifecycle hooks (`attachBaseContext` in `MainActivity` and `SteamService`):
     ```kotlin
     val ep = EntryPointAccessors.fromApplication(newBase.applicationContext ?: newBase, PreferencesEntryPoint::class.java)
     val languageCode = ep.uiPreferences().appLanguage
     ```

4. **Testing Architecture & Fake/Mock Strategy**:
   - The project uses JUnit 4, Robolectric 4.14, Mockito Kotlin 5.3.1, and MockK 1.13.5.
   - With preference repositories segregated into clean interfaces, unit tests (such as `DefaultFavoritesRepositoryTest`, `LibraryViewModelTest`, `UserLoginViewModelTest`) can pass lightweight in-memory fake implementations or Mockito mocks of `AuthPreferences`, `ContainerPreferences`, etc., eliminating the need for `PrefManager.init(context)` or actual disk-backed DataStore operations in test harnesses.

---

## 3. Caveats

1. **Synchronous vs. Asynchronous Migration Access**:
   - `PrefManager` currently uses `runBlocking { dataStore.data.first()[key] ?: defaultValue }` for synchronous property getters.
   - To make the migration across ~100 files smooth and avoid race conditions or deadlocks:
     - The new preference repositories should provide convenient synchronous property getters/setters (or in-memory cached state flows) alongside reactive `Flow<T>` or suspend functions.
     - This ensures existing synchronous callers (e.g. inside composables, UI delegates, or legacy Winlator Java methods) can be migrated without rewriting the entire calling architecture to coroutines in a single leap.
2. **Double Preference Managers in Codebase**:
   - `app.gamenative.PrefManager` (subject of this refactoring) vs `com.winlator.PrefManager` (legacy Winlator preferences).
   - Only `app.gamenative.PrefManager` is being replaced and deleted; `com.winlator.PrefManager` should remain untouched.
3. **Execution Environment**:
   - Subagent terminal command execution for long-running Gradle tasks requires non-interactive execution or running with appropriate permissions.

---

## 4. Conclusion

- **DI Architecture Readiness**: The codebase is 100% ready for full preference repository migration. The Hilt foundation (`@HiltAndroidApp`, `SingletonComponent`, `CoroutinesModule`, `SystemServicesModule`, `DatabaseModule`, `RepositoryModule`, `@AndroidEntryPoint`, `@HiltViewModel`, `EntryPointAccessors`) is already well-established.
- **Repository Segregation Strategy**: Partition the monolithic 1509-line `PrefManager` into 11 domain repositories (`AuthPreferences`, `ContainerPreferences`, `PerformancePreferences`, `ThemePreferences`, `DownloadPreferences`, `SteamPreferences`, `GogPreferences`, `EpicPreferences`, `AmazonPreferences`, `UiPreferences`, `AnalyticsPreferences`).
- **DataStore Preservation**: Provide a single `DataStore<Preferences>` instance (`name = "PluviaPreferences"`) in `PreferencesDataStoreModule` to guarantee 0 data loss.
- **Non-Hilt Resolution**: Provide `PreferencesEntryPoint` installed in `SingletonComponent` using `EntryPointAccessors.fromApplication(...)` for static objects, native bridges, and Java classes (`WineUtils.java`, `BionicProgramLauncherComponent.java`).
- **Phased Implementation Execution**:
  1. Module & Repo definitions (`di/PreferencesModule.kt`, `preferences/*Preferences.kt`).
  2. Core / Data migration (`data/*`, `core/*`).
  3. Services migration (`service/*`).
  4. ViewModels & UI migration (`ui/*`, `MainActivity.kt`).
  5. Utils & Winlator bridges (`utils/*`, `com/winlator/*`).
  6. Final eradication of `object PrefManager` and validation.

---

## 5. Verification Method

To independently verify the architecture and baseline:

1. **Inspect Module & EntryPoint Patterns**:
   - View `app/src/main/java/app/gamenative/di/DatabaseModule.kt`
   - View `app/src/main/java/app/gamenative/di/RepositoryModule.kt`
   - View `app/src/main/java/app/gamenative/mods/NexusModManager.kt` (lines 85-110)
   - View `app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt` (lines 45-55, 155-165)
2. **Inspect PrefManager Dependencies**:
   - View `app/src/main/java/app/gamenative/PrefManager.kt`
   - View `app/src/main/java/com/winlator/core/WineUtils.java` (line 70)
   - View `app/src/main/java/com/winlator/xenvironment/components/BionicProgramLauncherComponent.java` (lines 524, 536, 601-603)
3. **Run Baseline Compilation & Unit Tests**:
   - Command (PowerShell): `.\gradlew compileModernDebugKotlin`
   - Command (PowerShell): `.\gradlew :app:testModernDebugUnitTest`
   - Ensure `GRADLE_USER_HOME` is set to `D:\` and do NOT use `--no-build-cache`.
4. **Post-Refactoring Acceptance Verification**:
   - Check `git grep "PrefManager"` to ensure 0 references remain to `app.gamenative.PrefManager`.
   - Verify `compileModernDebugKotlin` passes.
   - Verify `:app:testModernDebugUnitTest` passes.
