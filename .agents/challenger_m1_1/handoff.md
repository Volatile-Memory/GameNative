# Verification Report & Verdict — Milestone 1

**Challenger**: Challenger M1-1 (`challenger_m1_1`)  
**Milestone**: Milestone 1 (Preference Repositories & DI Infrastructure)  
**Timestamp**: 2026-08-31T16:58:00+05:00  
**Verdict**: **APPROVE**

---

## 1. Observation

### 1.1 Verified File Inventory
16 source files were verified in the codebase:
- `app/src/main/java/app/gamenative/preferences/AuthPreferences.kt` (33 lines)
- `app/src/main/java/app/gamenative/preferences/DefaultAuthPreferences.kt` (241 lines)
- `app/src/main/java/app/gamenative/preferences/ContainerPreferences.kt` (60 lines)
- `app/src/main/java/app/gamenative/preferences/DefaultContainerPreferences.kt` (322 lines)
- `app/src/main/java/app/gamenative/preferences/InputPreferences.kt` (24 lines)
- `app/src/main/java/app/gamenative/preferences/DefaultInputPreferences.kt` (120 lines)
- `app/src/main/java/app/gamenative/preferences/HudPreferences.kt` (41 lines)
- `app/src/main/java/app/gamenative/preferences/DefaultHudPreferences.kt` (255 lines)
- `app/src/main/java/app/gamenative/preferences/LibraryPreferences.kt` (52 lines)
- `app/src/main/java/app/gamenative/preferences/DefaultLibraryPreferences.kt` (329 lines)
- `app/src/main/java/app/gamenative/preferences/DownloadPreferences.kt` (26 lines)
- `app/src/main/java/app/gamenative/preferences/DefaultDownloadPreferences.kt` (128 lines)
- `app/src/main/java/app/gamenative/preferences/GeneralPreferences.kt` (46 lines)
- `app/src/main/java/app/gamenative/preferences/DefaultGeneralPreferences.kt` (245 lines)
- `app/src/main/java/app/gamenative/preferences/PreferencesEntryPoint.kt` (45 lines)
- `app/src/main/java/app/gamenative/di/PreferencesModule.kt` (106 lines)

### 1.2 Dagger Hilt & EntryPoint Resolution
In `app/src/main/java/app/gamenative/di/PreferencesModule.kt`:
```kotlin
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PluviaDataStore

@Module
@InstallIn(SingletonComponent::class)
object PreferencesDataStoreModule {
    @Provides
    @Singleton
    @PluviaDataStore
    fun providePluviaDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.pluviaDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesBindingModule {
    @Binds @Singleton abstract fun bindAuthPreferences(impl: DefaultAuthPreferences): AuthPreferences
    @Binds @Singleton abstract fun bindContainerPreferences(impl: DefaultContainerPreferences): ContainerPreferences
    @Binds @Singleton abstract fun bindInputPreferences(impl: DefaultInputPreferences): InputPreferences
    @Binds @Singleton abstract fun bindHudPreferences(impl: DefaultHudPreferences): HudPreferences
    @Binds @Singleton abstract fun bindLibraryPreferences(impl: DefaultLibraryPreferences): LibraryPreferences
    @Binds @Singleton abstract fun bindDownloadPreferences(impl: DefaultDownloadPreferences): DownloadPreferences
    @Binds @Singleton abstract fun bindGeneralPreferences(impl: DefaultGeneralPreferences): GeneralPreferences
}
```

In `app/src/main/java/app/gamenative/preferences/PreferencesEntryPoint.kt`:
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

    companion object {
        @JvmStatic
        fun get(context: Context): PreferencesEntryPoint {
            val appContext = context.applicationContext ?: context
            return EntryPointAccessors.fromApplication(
                appContext,
                PreferencesEntryPoint::class.java,
            )
        }
    }
}

fun Context.preferencesEntryPoint(): PreferencesEntryPoint =
    PreferencesEntryPoint.get(this)
```

### 1.3 Key & Zero Data Loss Audit
- **All 7 domains** accurately map 1:1 to every legacy key in `PrefManager.kt`.
- Legacy keys with unique casing or formatting are preserved verbatim:
  - `"dxwrapperConfig"` (DefaultContainerPreferences.kt:56)
  - `"videoPciDeviceID"` (DefaultContainerPreferences.kt:70)
  - `"offScreenRenderingMode"` (DefaultContainerPreferences.kt:71)
  - `"strictShaderMath"` (DefaultContainerPreferences.kt:72)
  - `"useDRI3"` (DefaultContainerPreferences.kt:73)
  - `"videoMemorySize"` (DefaultContainerPreferences.kt:74)
  - `"mouseWarpOverride"` (DefaultContainerPreferences.kt:75)
  - `"start screen"` with whitespace (DefaultGeneralPreferences.kt:36)
- Specialized behaviors and side effects verified:
  - `clearSteamSession()` in `DefaultAuthPreferences.kt:221-235` clears the exact 11 Steam session keys.
  - `cellId = 0` clears `cellIdManuallySet` flag (`DefaultAuthPreferences.kt:143`).
  - `useExternalStorage` modification resets `externalStoragePath` (`DefaultDownloadPreferences.kt:63`).
  - `favoritePersistenceLock` + `favoritePersistenceVersion` guarantees atomic out-of-order write rejection (`DefaultLibraryPreferences.kt:246-261`).
  - `recDisclosureShownCache` provides volatile in-memory caching (`DefaultLibraryPreferences.kt:194`).
  - `nexusLastPlacementJson` removes the preference key when blank or `"{}"` (`DefaultGeneralPreferences.kt:206`).

---

## 2. Logic Chain

1. **Architecture Conformance**: The design separates concerns into 7 distinct domain repositories (`AuthPreferences`, `ContainerPreferences`, `InputPreferences`, `HudPreferences`, `LibraryPreferences`, `DownloadPreferences`, `GeneralPreferences`), resolving the monolithic singleton issue.
2. **Dual-Access Layer**: Every repository implements both synchronous snapshot access (`var key: Type`) and asynchronous reactive streams (`val keyFlow: Flow<Type>`), ensuring existing synchronous call sites can be migrated incrementally without breakage, while new code can consume reactive Flows.
3. **Dependency Injection Support**:
   - Standard `@Inject` constructor injection is enabled across all 7 repositories via `PreferencesBindingModule`.
   - Non-Hilt components, Java callers (e.g. `WineUtils.java`, `BionicProgramLauncherComponent.java`), and early Android lifecycle hooks (e.g. `attachBaseContext`) are supported via `PreferencesEntryPoint.get(context)` and `Context.preferencesEntryPoint()`.
4. **Data Continuity**: Zero data loss is guaranteed since all 7 repositories point to the existing `"PluviaPreferences"` DataStore file and use the identical key constants and converters.

---

## 3. Caveats

1. **`com.winlator.PrefManager`**: Operates on `"WinlatorPreferences"` and is purposely out of scope for this Pluvia migration.
2. **JVM Unit Testing for Crypto**: `Crypto.kt` depends on `AndroidKeyStore`. Unit tests for `AuthPreferences` on standard JVM require Robolectric or KeyStore mocks.
3. **Downstream Execution**: Milestones M2 through M5 can proceed concurrently to migrate call sites to the new injected interfaces.

---

## 4. Conclusion

**Verdict: APPROVE**

Milestone 1 satisfies all requirements R1, R2, and interface specifications from `PROJECT.md`. The preference repositories, Hilt modules, and EntryPoint infrastructure are complete, correct, and ready for downstream consumption.

---

## 5. Verification Method

To independently verify:
1. Inspect the 15 preference files in `app/src/main/java/app/gamenative/preferences/`
2. Inspect `app/src/main/java/app/gamenative/di/PreferencesModule.kt`
3. Compile modernDebug target:
   ```bash
   ./gradlew compileModernDebugKotlin
   ```
4. Run unit tests:
   ```bash
   ./gradlew :app:testModernDebugUnitTest
   ```
