# Review Report — Milestone 1: Preference Repositories & DI

**Reviewer**: Reviewer M1-1 (`reviewer_m1_1`)  
**Verdict**: **APPROVE**  
**Timestamp**: 2026-08-31T17:00:00+05:00  
**Target Milestone**: Milestone 1 (Preference Repositories & DI Infrastructure)  
**Target Worker**: Worker M1 (`worker_m1`)  

---

## 1. Observation

A full code and structural audit was conducted across all 16 files implemented by Worker M1:

### 1.1 Verified File Structure & Packaging
All files reside in their designated architectural locations with correct package statements and annotations:
1. `app/src/main/java/app/gamenative/preferences/AuthPreferences.kt` (package `app.gamenative.preferences`)
2. `app/src/main/java/app/gamenative/preferences/DefaultAuthPreferences.kt` (`@Singleton`, `@Inject constructor`)
3. `app/src/main/java/app/gamenative/preferences/ContainerPreferences.kt` (package `app.gamenative.preferences`)
4. `app/src/main/java/app/gamenative/preferences/DefaultContainerPreferences.kt` (`@Singleton`, `@Inject constructor`)
5. `app/src/main/java/app/gamenative/preferences/InputPreferences.kt` (package `app.gamenative.preferences`)
6. `app/src/main/java/app/gamenative/preferences/DefaultInputPreferences.kt` (`@Singleton`, `@Inject constructor`)
7. `app/src/main/java/app/gamenative/preferences/HudPreferences.kt` (package `app.gamenative.preferences`)
8. `app/src/main/java/app/gamenative/preferences/DefaultHudPreferences.kt` (`@Singleton`, `@Inject constructor`)
9. `app/src/main/java/app/gamenative/preferences/LibraryPreferences.kt` (package `app.gamenative.preferences`)
10. `app/src/main/java/app/gamenative/preferences/DefaultLibraryPreferences.kt` (`@Singleton`, `@Inject constructor`)
11. `app/src/main/java/app/gamenative/preferences/DownloadPreferences.kt` (package `app.gamenative.preferences`)
12. `app/src/main/java/app/gamenative/preferences/DefaultDownloadPreferences.kt` (`@Singleton`, `@Inject constructor`)
13. `app/src/main/java/app/gamenative/preferences/GeneralPreferences.kt` (package `app.gamenative.preferences`)
14. `app/src/main/java/app/gamenative/preferences/DefaultGeneralPreferences.kt` (`@Singleton`, `@Inject constructor`)
15. `app/src/main/java/app/gamenative/preferences/PreferencesEntryPoint.kt` (`@EntryPoint`, `@InstallIn(SingletonComponent::class)`)
16. `app/src/main/java/app/gamenative/di/PreferencesModule.kt` (package `app.gamenative.di`, provides `@PluviaDataStore` and binds all 7 interfaces)

### 1.2 Key Inventory & Behavior Compatibility
- **100% Key Coverage**: Every single one of the ~90 preferences from `app/src/main/java/app/gamenative/PrefManager.kt` is present in the corresponding domain repository.
- **Legacy Special Cases Preserved**:
  - Exact legacy key names with irregular casing (e.g. `"dxwrapperConfig"`, `"videoPciDeviceID"`, `"offScreenRenderingMode"`, `"strictShaderMath"`, `"useDRI3"`, `"videoMemorySize"`, `"mouseWarpOverride"`) and spaces (`"start screen"`) match `PrefManager.kt` verbatim.
  - Concurrency synchronization: `favoritePersistenceLock` + version tracking in `DefaultLibraryPreferences` is preserved.
  - Volatile cache: `recDisclosureShownCache` in `DefaultLibraryPreferences` is preserved.
  - Side effects:
    - `cellId = 0` sets `cellIdManuallySet = false` in `DefaultAuthPreferences`.
    - `useExternalStorage` modification resets `externalStoragePath = ""` in `DefaultDownloadPreferences`.
    - `nexusLastPlacementJson` removes key on blank or `"{}"` in `DefaultGeneralPreferences`.
    - `clearSteamSession()` removes all 11 Steam session keys in `DefaultAuthPreferences`.
- **Integrity Check**:
  - No dummy or facade implementations found; all methods and properties interact directly with `DataStore<Preferences>`.
  - No hardcoded test results or shortcuts bypassing logic.

---

## 2. Logic Chain

1. **Clean Decomposition**: Worker M1 logically partitioned the monolithic `PrefManager` into 7 high-cohesion domain repositories without altering underlying DataStore keys or defaults.
2. **Backward-Compatible Dual-Access API**: Exposing both synchronous properties (`var key: Type`) and asynchronous `Flow<Type>` streams allows downstream workers (M2, M3, M4, M5) to perform non-breaking, incremental refactorings.
3. **Dagger Hilt Correctness**:
   - `PreferencesDataStoreModule` installs in `SingletonComponent` and provides `@PluviaDataStore DataStore<Preferences>` with a corruption fallback handler.
   - `PreferencesBindingModule` binds each `@Singleton Default*Preferences` to its respective interface in `SingletonComponent`.
   - `PreferencesEntryPoint` exposes typed accessors for Java components and non-injected classes.
4. **Conclusion Support**: The implementation satisfies all Milestone 1 requirements (R1, R2) with complete type safety and zero data loss.

---

## 3. Caveats

- `Crypto.kt` operates against AndroidKeyStore in runtime environments; unit tests running on standard JVM will require mocks or Robolectric.
- Downstream workers should prefer constructor injection of the specific domain repository interfaces (`AuthPreferences`, `ContainerPreferences`, etc.) and reserve `PreferencesEntryPoint` for Java bridges (`WineUtils.java`, `BionicProgramLauncherComponent.java`) or early application lifecycle entry points.

---

## 4. Conclusion

**Verdict: APPROVE**

Milestone 1 is fully realized and ready for downstream consumption. All 7 domain repositories, implementations, Hilt DI bindings, and EntryPoint accessors meet project specifications and quality standards.

---

## 5. Verification Method

- Static code inspection and reference checking across `app/src/main/java/app/gamenative/preferences/` and `app/src/main/java/app/gamenative/di/PreferencesModule.kt`.
- Compilation verification command:
  ```pwsh
  .\gradlew compileModernDebugKotlin
  ```
- Unit test verification command:
  ```pwsh
  .\gradlew :app:testModernDebugUnitTest
  ```
