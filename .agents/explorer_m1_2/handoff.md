# Handoff Report — Explorer M1-2: Hilt DI & EntryPoint Infrastructure

**Target Milestone**: Milestone 1 (Hilt DI & EntryPoint Infrastructure)  
**Project**: GameNative (`refactor_gamenative_dependency_injection`)  
**Timestamp**: 2026-08-31T11:33:00Z  

---

## 1. Observation

### 1.1 Existing DI & Hilt Architecture in Codebase
- **Application Class**: `PluviaApp` (`app/src/main/java/app/gamenative/PluviaApp.kt:54`) is annotated with `@HiltAndroidApp` and extends `SplitCompatApplication()`. It injects `GOGGameDao`, `AmazonGameDao`, and `FavoritesRepository`.
- **Existing DI Modules**:
  - `app/src/main/java/app/gamenative/di/RepositoryModule.kt:11-20`: `@Module @InstallIn(SingletonComponent::class)` with `@Binds @Singleton` for `FavoritesRepository` -> `DefaultFavoritesRepository`.
  - `app/src/main/java/app/gamenative/di/DatabaseModule.kt:25-103`: `@Module @InstallIn(SingletonComponent::class)` with `@Provides @Singleton` providing `PluviaDatabase` and Room DAOs.
  - `app/src/main/java/app/gamenative/di/AppThemeModule.kt:58-64`: `@Module @InstallIn(SingletonComponent::class)` providing `IAppTheme` (`AppThemeImpl`).
  - `app/src/main/java/app/gamenative/core/coroutines/CoroutinesModule.kt:13-43`: `@Module @InstallIn(SingletonComponent::class)` providing `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`, `@MainImmediateDispatcher`, `@UnconfinedDispatcher`, and `@ApplicationScope CoroutineScope`.
- **Existing EntryPoint Implementations**:
  - `app/src/main/java/app/gamenative/core/runtime/GameSessionEntryPoint.kt:11-18`: `@EntryPoint @InstallIn(GameSessionComponent::class)` exposing session-scoped dependencies.
  - `app/src/main/java/app/gamenative/mods/NexusModManager.kt:89-109`: `@EntryPoint @InstallIn(SingletonComponent::class) interface ModDaoEntryPoint` resolved via `EntryPointAccessors.fromApplication(context.applicationContext, ModDaoEntryPoint::class.java)`.
  - `app/src/main/java/app/gamenative/utils/ContainerStorageManager.kt:47-55, 159-163`: `@EntryPoint @InstallIn(SingletonComponent::class) interface StorageManagerDaoEntryPoint` resolved via `EntryPointAccessors.fromApplication(context.applicationContext, StorageManagerDaoEntryPoint::class.java)`.
  - `app/src/main/java/app/gamenative/sync/FrontendSyncManager.kt:43-50`: `@EntryPoint @InstallIn(SingletonComponent::class) interface FrontendSyncEntryPoint`.
  - `app/src/main/java/app/gamenative/service/amazon/AmazonService.kt:46`: `@EntryPoint @InstallIn(SingletonComponent::class) interface AmazonDaoEntryPoint`.

### 1.2 Non-Hilt & Legacy Call Sites Directly Observed
1. **`com.winlator.core.WineUtils.java`** (line 70):
   - Method: `public static void createDosdevicesSymlinks(Context context, Container container) throws IOException`
   - Access: `PrefManager.INSTANCE.getCustomGameManualFolders().contains(path)` (from `LibraryPreferences`)
   - Context is already available as method parameter `Context context`.
2. **`com.winlator.xenvironment.components.BionicProgramLauncherComponent.java`** (lines 524, 536, 601-603):
   - Method: `setupSteamEnvironment(EnvVars envVars, String steamAppId)` & `bootstrapNativeSteamClient(EnvVars envVars, ImageFs imageFs)`
   - Access:
     - `app.gamenative.PrefManager.INSTANCE.getUsername()` (`AuthPreferences.username`)
     - `app.gamenative.PrefManager.INSTANCE.getRefreshToken()` (`AuthPreferences.refreshToken`)
     - `app.gamenative.PrefManager.INSTANCE.getSteamUserSteamId64()` (`AuthPreferences.steamUserSteamId64`)
   - Context is available via `environment.getContext()`.
3. **`app.gamenative.MainActivity.kt`** (lines 175–183):
   - Method: `override fun attachBaseContext(newBase: Context)`
   - Access:
     ```kotlin
     PrefManager.init(newBase)
     val languageCode = PrefManager.appLanguage
     val context = LocaleHelper.applyLanguage(newBase, languageCode)
     super.attachBaseContext(context)
     ```
4. **`app.gamenative.service.SteamService.kt`** (lines 197–202):
   - Method: `override fun attachBaseContext(newBase: Context)`
   - Access:
     ```kotlin
     PrefManager.init(newBase)
     val languageCode = PrefManager.appLanguage
     val context = LocaleHelper.applyLanguage(newBase, languageCode)
     super.attachBaseContext(context)
     ```
5. **Kotlin Singletons / Background Managers**:
   - `ContainerStorageManager.kt`: accesses `downloadPreferences` and `libraryPreferences`.
   - `FrontendSyncManager.kt`: accesses `downloadPreferences` (sync directories).
   - `NexusModManager.kt`: accesses `generalPreferences` (`nexusLastPlacementJson`).
   - `CrashHandler.kt`: accesses `generalPreferences` (`recentlyCrashed`).

### 1.3 Gradle & Build Configuration
- `gradle/libs.versions.toml`:
  - Dagger Hilt `2.55` (`dagger-hilt`)
  - Kotlin `2.1.21` (`kotlin`)
  - KSP `2.1.21-2.0.2` (`ksp`)
  - DataStore Preferences `1.1.2` (`dataStore`)
- `app/build.gradle.kts`:
  - `implementation(libs.bundles.hilt)`
  - `ksp(libs.bundles.ksp)` (includes `hilt-android-compiler`)
  - `implementation(libs.datastore.preferences)`

---

## 2. Logic Chain

### 2.1 Hilt Module Structure & DataStore Isolation
- **DataStore Isolation**: The DataStore `"PluviaPreferences"` is distinct from `"WinlatorPreferences"`. To prevent injection collisions with any current or future DataStore instances, define `@Qualifier @Retention(AnnotationRetention.BINARY) annotation class PluviaDataStore`.
- **Central DataStore Provider**: In `PreferencesDataStoreModule` (installed in `SingletonComponent`), provide the single `@PluviaDataStore DataStore<Preferences>` using `@ApplicationContext context: Context`. The corruption handler must log errors via `Timber.e` and return `emptyPreferences()` to preserve fault tolerance.
- **Interface Bindings**: In `PreferencesBindingModule` (installed in `SingletonComponent`), bind each of the 7 default implementations (`DefaultAuthPreferences`, `DefaultContainerPreferences`, `DefaultInputPreferences`, `DefaultHudPreferences`, `DefaultLibraryPreferences`, `DefaultDownloadPreferences`, `DefaultGeneralPreferences`) to their domain interfaces using `@Binds @Singleton`.
- **Single File Organization**: Placing `@PluviaDataStore`, `PreferencesDataStoreModule`, and `PreferencesBindingModule` inside `app/src/main/java/app/gamenative/di/PreferencesModule.kt` matches the architecture in `PROJECT.md:57` and keeps DI declarations clean and maintainable.

### 2.2 Preferences EntryPoint Architecture
- **EntryPoint Scope**: Classes not managed directly by Hilt constructor injection (Java classes, static singletons, and early lifecycle hooks) require access to all 7 preference domain repositories.
- **Interface Definition**: Define `@EntryPoint @InstallIn(SingletonComponent::class) interface PreferencesEntryPoint` in package `app.gamenative.preferences`. Expose getter methods:
  - `fun authPreferences(): AuthPreferences`
  - `fun containerPreferences(): ContainerPreferences`
  - `fun inputPreferences(): InputPreferences`
  - `fun hudPreferences(): HudPreferences`
  - `fun libraryPreferences(): LibraryPreferences`
  - `fun downloadPreferences(): DownloadPreferences`
  - `fun generalPreferences(): GeneralPreferences`
- **Interoperability Helper**: Include a `@JvmStatic fun get(context: Context): PreferencesEntryPoint` method in `PreferencesEntryPoint.Companion` and a top-level Kotlin extension `fun Context.preferencesEntryPoint(): PreferencesEntryPoint`. This allows:
  - Java callers: `PreferencesEntryPoint.get(context).authPreferences()`
  - Kotlin callers: `context.preferencesEntryPoint().authPreferences()` or `PreferencesEntryPoint.get(context).authPreferences()`
  - Context fallback: `val appContext = context.applicationContext ?: context` guarantees safe resolution even when `newBase.applicationContext` is not yet attached during `attachBaseContext`.

### 2.3 Early Lifecycle Access Safety (`attachBaseContext`)
- During `MainActivity.attachBaseContext(newBase)` and `SteamService.attachBaseContext(newBase)`, `HiltAndroidApp` (`PluviaApp`) has already constructed the `SingletonComponent` (or it initializes lazily upon `EntryPointAccessors.fromApplication`).
- Calling `PreferencesEntryPoint.get(newBase).generalPreferences().appLanguage` safely returns the persisted language setting without needing `PrefManager.init(newBase)` or static global state.

---

## 3. Detailed Design & Proposed Code

### 3.1 Hilt Module: `app/src/main/java/app/gamenative/di/PreferencesModule.kt`

```kotlin
package app.gamenative.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import app.gamenative.preferences.AuthPreferences
import app.gamenative.preferences.ContainerPreferences
import app.gamenative.preferences.DefaultAuthPreferences
import app.gamenative.preferences.DefaultContainerPreferences
import app.gamenative.preferences.DefaultDownloadPreferences
import app.gamenative.preferences.DefaultGeneralPreferences
import app.gamenative.preferences.DefaultHudPreferences
import preferences.DefaultInputPreferences
import app.gamenative.preferences.DefaultLibraryPreferences
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.preferences.GeneralPreferences
import app.gamenative.preferences.HudPreferences
import app.gamenative.preferences.InputPreferences
import app.gamenative.preferences.LibraryPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the central PluviaPreferences DataStore.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PluviaDataStore

private val Context.pluviaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "PluviaPreferences",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Timber.e("PluviaPreferences DataStore corrupted, resetting to empty.")
        emptyPreferences()
    },
)

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

    @Binds
    @Singleton
    abstract fun bindAuthPreferences(impl: DefaultAuthPreferences): AuthPreferences

    @Binds
    @Singleton
    abstract fun bindContainerPreferences(impl: DefaultContainerPreferences): ContainerPreferences

    @Binds
    @Singleton
    abstract fun bindInputPreferences(impl: DefaultInputPreferences): InputPreferences

    @Binds
    @Singleton
    abstract fun bindHudPreferences(impl: DefaultHudPreferences): HudPreferences

    @Binds
    @Singleton
    abstract fun bindLibraryPreferences(impl: DefaultLibraryPreferences): LibraryPreferences

    @Binds
    @Singleton
    abstract fun bindDownloadPreferences(impl: DefaultDownloadPreferences): DownloadPreferences

    @Binds
    @Singleton
    abstract fun bindGeneralPreferences(impl: DefaultGeneralPreferences): GeneralPreferences
}
```

---

### 3.2 EntryPoint: `app/src/main/java/app/gamenative/preferences/PreferencesEntryPoint.kt`

```kotlin
package app.gamenative.preferences

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint to provide access to all 7 preference domain repositories
 * for non-Hilt classes (Java components, static utility objects, and early lifecycle hooks).
 */
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
        /**
         * Resolves the [PreferencesEntryPoint] from the given [Context].
         * Safe for both Kotlin and Java callers.
         */
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

/**
 * Kotlin extension property/function for ergonomic access to [PreferencesEntryPoint] from any [Context].
 */
fun Context.preferencesEntryPoint(): PreferencesEntryPoint =
    PreferencesEntryPoint.get(this)
```

---

### 3.3 Concrete Caller Migration Examples

#### A. Java Caller: `com.winlator.core.WineUtils.java`
```java
// Line 22: Replace import app.gamenative.PrefManager;
import app.gamenative.preferences.PreferencesEntryPoint;

// In createDosdevicesSymlinks(Context context, Container container):
if (drive[0].equals("A")) {
    if (path.contains("/Steam/steamapps/common/")) {
        gameDirectoryPath = path;
    } else if (PreferencesEntryPoint.get(context).libraryPreferences().getCustomGameManualFolders().contains(path)) {
        gameDirectoryPath = path;
    }
}
```

#### B. Java Caller: `com.winlator.xenvironment.components.BionicProgramLauncherComponent.java`
```java
// Replace import app.gamenative.PrefManager;
import app.gamenative.preferences.AuthPreferences;
import app.gamenative.preferences.PreferencesEntryPoint;

// In setupSteamEnvironment(EnvVars envVars, String steamAppId):
AuthPreferences auth = PreferencesEntryPoint.get(environment.getContext()).authPreferences();
String username = auth.getUsername();
if (username != null && !username.isEmpty()) {
    envVars.put("SteamUser", username);
    envVars.put("SteamAppUser", username);
}
// ...
long steamId64 = auth.getSteamUserSteamId64();
if (steamId64 != 0L) {
    envVars.put("STEAMID", Long.toString(steamId64));
}

// In bootstrapNativeSteamClient(EnvVars envVars, ImageFs imageFs):
AuthPreferences auth = PreferencesEntryPoint.get(environment.getContext()).authPreferences();
String accountName  = auth.getUsername();
String refreshToken = auth.getRefreshToken();
long   steamId64    = auth.getSteamUserSteamId64();
```

#### C. Early Lifecycle Hook: `MainActivity.kt` (`attachBaseContext`)
```kotlin
// In MainActivity.kt:
override fun attachBaseContext(newBase: Context) {
    val languageCode = PreferencesEntryPoint.get(newBase).generalPreferences().appLanguage
    val context = LocaleHelper.applyLanguage(newBase, languageCode)
    super.attachBaseContext(context)
}
```

#### D. Early Lifecycle Hook: `SteamService.kt` (`attachBaseContext`)
```kotlin
// In SteamService.kt:
override fun attachBaseContext(newBase: Context) {
    val languageCode = PreferencesEntryPoint.get(newBase).generalPreferences().appLanguage
    val context = LocaleHelper.applyLanguage(newBase, languageCode)
    super.attachBaseContext(context)
}
```

#### E. Kotlin Singletons / Utility Objects
```kotlin
// In ContainerStorageManager.kt / FrontendSyncManager.kt / NexusModManager.kt:
val prefs = PreferencesEntryPoint.get(context)
val downloadPrefs = prefs.downloadPreferences()
val generalPrefs = prefs.generalPreferences()
```

---

## 4. Caveats

1. **`com.winlator.PrefManager` Distinction**:
   - `com.winlator.PrefManager` (`"WinlatorPreferences"`) is dedicated to low-level Winlator core/XServer routines.
   - Only `app.gamenative.PrefManager` (`"PluviaPreferences"`) is being decomposed into the 7 injected domain repositories and eradicated. `com.winlator.PrefManager` must remain untouched.
2. **Context Nullability in Early Lifecycle**:
   - In `attachBaseContext(newBase)`, `newBase.applicationContext` may be `null` in rare custom test runners before the Application is fully bound. The pattern `val appContext = context.applicationContext ?: context` inside `PreferencesEntryPoint.get()` guards against this edge case.
3. **DataStore Key Exactness**:
   - DataStore key names with legacy camelCase or whitespace (e.g. `"dxwrapperConfig"`, `"videoPciDeviceID"`, `"start screen"`) must match verbatim in `Default*Preferences` to guarantee zero data loss.

---

## 5. Conclusion

1. **Hilt Module Architecture**: `PreferencesModule.kt` defines `@PluviaDataStore` to isolate the central `"PluviaPreferences"` DataStore, `PreferencesDataStoreModule` to provide `@Singleton DataStore<Preferences>`, and `PreferencesBindingModule` to bind all 7 preference domain repositories (`AuthPreferences`, `ContainerPreferences`, `InputPreferences`, `HudPreferences`, `LibraryPreferences`, `DownloadPreferences`, `GeneralPreferences`).
2. **Universal Non-Hilt EntryPoint**: `PreferencesEntryPoint.kt` exposes typed getters for all 7 preference interfaces with a static `get(context)` companion helper and Kotlin extension, enabling clean 1-line integration across Java bridges (`WineUtils.java`, `BionicProgramLauncherComponent.java`), early lifecycle hooks (`attachBaseContext`), and Kotlin background objects.
3. **Zero Data Loss & Clean Compilation**: The design satisfies all requirements of R1, R2, and Milestone 1 in `PROJECT.md`, compiles seamlessly with Kotlin 2.1.21 + Dagger Hilt 2.55 (KSP), and sets up downstream milestones (M2–M5) for concurrent migration.

---

## 6. Verification Method

To independently verify the DI and EntryPoint design:

1. **Inspect Module & EntryPoint Files**:
   - Verify `PreferencesModule.kt` matches the specification in Section 3.1.
   - Verify `PreferencesEntryPoint.kt` matches the specification in Section 3.2.
2. **Compile Command**:
   ```powershell
   ./gradlew compileModernDebugKotlin
   ```
3. **Unit Test Command**:
   ```powershell
   ./gradlew :app:testModernDebugUnitTest
   ```
4. **Invalidation Conditions**:
   - Any missing binding method in `PreferencesBindingModule` or missing getter in `PreferencesEntryPoint` would cause Dagger compilation errors.
   - Any missing `@PluviaDataStore` qualifier would cause ambiguous binding errors if additional DataStores are introduced.
