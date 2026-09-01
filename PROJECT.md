# Project: PrefManager Refactoring & Dagger Hilt Migration

## Architecture
- **DataStore**: Single central DataStore named `"PluviaPreferences"` provided via `@PluviaDataStore` in `SingletonComponent`.
- **Domain Interfaces**:
  - `AuthPreferences`: Steam credentials, tokens (AES encrypted), persona states, IDs, session clearing.
  - `ContainerPreferences`: Wine/Proton/FEX/Box86/driver configs, renderer, suspend policies, launch modes.
  - `InputPreferences`: Gamepad button mapping/swap, controller hints, xinput/dinput.
  - `HudPreferences`: Performance HUD toggles, layout coordinates/opacity/graphs, power controls.
  - `LibraryPreferences`: Library layout/sorting/filtering, game counts, recommendation/collection caches, custom game paths, favorites.
  - `DownloadPreferences`: Wi-Fi download restriction, download speed limits, external storage paths, SteamGridDB, frontend sync directories.
  - `GeneralPreferences`: App theme/palette, app language, orientation flags, start screen, notification icons, analytics, crash detection, compatibility caches.
- **Dependency Injection**:
  - `PreferencesDataStoreModule`: Provides `@PluviaDataStore` `DataStore<Preferences>`.
  - `PreferencesBindingModule`: `@Binds @Singleton` implementations to domain interfaces.
  - `PreferencesEntryPoint`: `@EntryPoint @InstallIn(SingletonComponent::class)` for non-Hilt Kotlin/Java callers and early lifecycle hooks (`attachBaseContext`).

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Preference Repositories Infrastructure | 7 domain interfaces, default implementations with 1:1 legacy keys, Hilt modules & `PreferencesEntryPoint` | M1 | ORIGINAL_REQUEST §R1, §R2 |
| 2 | Data, Core & Utilities Migration | Migrate 25 data/utils/sync/mods files to use injected preference interfaces or EntryPoint | M2 | ORIGINAL_REQUEST §R3 |
| 3 | Services & Background Layer Migration | Migrate 12 services/workers/workshop/crash handler files | M3 | ORIGINAL_REQUEST §R3 |
| 4 | ViewModels & UI/Screens Migration | Migrate 7 ViewModels/States and 25 UI/Screen/Settings/Composable files | M4 | ORIGINAL_REQUEST §R3 |
| 5 | Java Bridges, Runtime & Unit Tests Migration | Migrate Java callers (`WineUtils.java`, `BionicProgramLauncherComponent.java`), power control drivers, and 11 unit test files | M5 | ORIGINAL_REQUEST §R3 |
| 6 | Eradicate PrefManager & Acceptance Verification | Remove `PrefManager.init(this)`, delete `app.gamenative.PrefManager.kt`, verify 0 occurrences of `PrefManager.`, pass `./gradlew compileModernDebugKotlin` and `./gradlew :app:testModernDebugUnitTest` | M6 | ORIGINAL_REQUEST §R4, Acceptance Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Preference Repositories & DI | Create `app/gamenative/preferences/*` interfaces, implementations, `PreferencesModule.kt`, and `PreferencesEntryPoint` | none | DONE |
| 2 | M2: Data & Core Migration | Migrate `data/*`, `utils/*`, `sync/*`, `mods/*`, `core/*`, `AppThemeModule.kt` | M1 | IN_PROGRESS |
| 3 | M3: Services Migration | Migrate `service/*`, `workshop/*`, `CrashHandler.kt` | M1 | IN_PROGRESS |
| 4 | M4: ViewModels & UI Migration | Migrate `ui/model/*`, `ui/data/*`, `ui/screen/*`, `ui/component/*`, `MainActivity.kt` | M1 | IN_PROGRESS |
| 5 | M5: Runtime, Java & Test Migration | Migrate `com/winlator/core/WineUtils.java`, `BionicProgramLauncherComponent.java`, `powercontrol/*`, and 11 unit tests | M1 | DONE |
| 6 | M6: Eradicate Singleton & Verification | Delete `app.gamenative.PrefManager.kt`, clean up `PluviaApp.kt`, verify 0 grep matches, run full compile & test suite | M2, M3, M4, M5 | PLANNED |

## Interface Contracts
### Preferences ↔ Consumers
- All preference domain interfaces expose:
  - Kotlin properties (`var key: Type`) with synchronous snapshot access (backed by DataStore cached in-memory / fast read) for non-breaking incremental migration.
  - Reactive `Flow<Type>` properties for asynchronous/reactive UI & ViewModel consumption.
  - Suspend mutator functions where appropriate.
- Non-Hilt EntryPoint:
  - `EntryPointAccessors.fromApplication(context.applicationContext, PreferencesEntryPoint::class.java)` provides access to all 7 domain repositories.

## Code Layout
- `app/src/main/java/app/gamenative/preferences/`
  - `AuthPreferences.kt`, `DefaultAuthPreferences.kt`
  - `ContainerPreferences.kt`, `DefaultContainerPreferences.kt`
  - `InputPreferences.kt`, `DefaultInputPreferences.kt`
  - `HudPreferences.kt`, `DefaultHudPreferences.kt`
  - `LibraryPreferences.kt`, `DefaultLibraryPreferences.kt`
  - `DownloadPreferences.kt`, `DefaultDownloadPreferences.kt`
  - `GeneralPreferences.kt`, `DefaultGeneralPreferences.kt`
  - `PreferencesEntryPoint.kt`
- `app/src/main/java/app/gamenative/di/PreferencesModule.kt`
