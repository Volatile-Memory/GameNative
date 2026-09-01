# Implementation Blueprint: 7 Domain Preference Repositories & DI Infrastructure (M1-1)

**Author**: Explorer M1-1 (Preference Repositories & DI Infrastructure)  
**Milestone**: Milestone 1 (Preference Repositories & DI Infrastructure)  
**DataStore Name**: `"PluviaPreferences"`  
**Target Package**: `app.gamenative.preferences` & `app.gamenative.di`  
**Timestamp**: 2026-08-31T16:30:00+05:00

---

## 1. Observation

### 1.1 Existing PrefManager & Dependency Analysis
- **Monolith Source**: `app/src/main/java/app/gamenative/PrefManager.kt` contains 1,509 lines, managing 95 distinct configuration properties across mixed domains (Steam auth, container setup, input, performance HUD, game library, downloads, and app-wide UI/settings).
- **DataStore Storage**: Uses `preferencesDataStore(name = "PluviaPreferences", corruptionHandler = ...)` with `ReplaceFileCorruptionHandler`.
- **Existing Access Semantics**:
  - Synchronous Reads: `runBlocking { dataStore.data.first()[key] ?: defaultValue }`
  - Asynchronous Writes: `scope.launch { dataStore.edit { pref -> pref[key] = value } }`
  - Special concurrency handling in legacy `PrefManager`:
    - `recDisclosureShown`: Uses `@Volatile private var recDisclosureShownCache: Boolean?` for immediate read-after-write on consent.
    - `favoriteAppIds`: Uses `synchronized(favoritePersistenceLock)` and `favoritePersistenceVersion` to avoid out-of-order writes during rapid favoriting toggles.
    - `accessToken` / `refreshToken`: Handled via AES encryption/decryption (`Crypto.encrypt` / `Crypto.decrypt`) with `byteArrayPreferencesKey`.
    - `clientId`: Nullable `Long?`.
    - `start screen`: Key contains a space (`"start screen"`).
    - CamelCase Keys: `"dxwrapperConfig"`, `"videoPciDeviceID"`, `"offScreenRenderingMode"`, `"strictShaderMath"`, `"useDRI3"`, `"videoMemorySize"`, `"mouseWarpOverride"`.
- **Hilt Architecture**:
  - Application class `PluviaApp` is annotated with `@HiltAndroidApp`.
  - Core coroutines module `CoroutinesModule` provides `@ApplicationScope CoroutineScope`, `@IoDispatcher CoroutineDispatcher`, `@DefaultDispatcher`, and `@MainDispatcher`.
  - Non-Hilt callers and early lifecycle callers (`MainActivity.attachBaseContext`, `SteamService.attachBaseContext`, `WineUtils.java`, `BionicProgramLauncherComponent.java`) require `PreferencesEntryPoint` installed in `SingletonComponent`.

---

## 2. Logic Chain

### 2.1 Domain Separation Strategy
To eliminate the monolithic singleton soup while maintaining 100% backward compatibility and zero data loss, the 95 properties are segregated into 7 cohesive domain interfaces and default implementations:

1. **`AuthPreferences` & `DefaultAuthPreferences`** (16 properties): Steam authentication credentials, tokens (AES encrypted), Persona state, Steam user IDs, PICS change number, offline flags, friends list headers, and session clearing methods (`clearSteamSession()`, `clearAll()`).
2. **`ContainerPreferences` & `DefaultContainerPreferences`** (55 properties): Container display settings, graphics drivers/configs, renderer modes, FEXCore/Box86 presets and versions, DXWrapper configs, audio driver, CPU affinities, WoW64 mode, suspend policies, Wine debug flags, and launch modes.
3. **`InputPreferences` & `DefaultInputPreferences`** (10 properties): Steam input toggle, XInput/DInput settings and mappers, external display input mode and swap, mouse input disabling, face button swapping, and gamepad hint overlays.
4. **`HudPreferences` & `DefaultHudPreferences`** (26 properties): Show FPS, Quick Menu tab index, Performance HUD metrics (CPU, GPU, RAM, Battery, Power Draw, Temp, Clock), graphs, HUD layout fractions (X/Y), opacity, color intensity, outline, size, power control default, and composite `PerformanceHudConfig` helpers (`getHudConfig()`, `setHudConfig()`, `hudConfigFlow`).
5. **`LibraryPreferences` & `DefaultLibraryPreferences`** (30 properties): Library layout pane, filter flags, sort options (with legacy ordinal migration), items per page, source toggles (Steam, GOG, Epic, Amazon, Custom), game counts, Steam collection caches, recommendation caches and disclosure consent, custom game paths and manual folders, and version-locked `favoriteAppIds`.
6. **`DownloadPreferences` & `DefaultDownloadPreferences`** (10 properties): Wi-Fi download restriction, download speed limit, external storage configuration (auto-resetting storage path), SteamGridDB image fetching, and per-source frontend export directories (`getFrontendSyncDir`, `setFrontendSyncDir`).
7. **`GeneralPreferences` & `DefaultGeneralPreferences`** (28 properties): App theme and palette, start screen, allowed orientation flags, app language, link opening behavior, status bar visibility, app/notification icons, achievement alerts, exit warnings, usage analytics, crash tracking, tipping state, component manifests, and game compatibility caches.

### 2.2 Dual-Access Read/Write Semantics & Concurrency
- **Synchronous Properties (`var key: Type`)**:
  - Expose mutable Kotlin properties with getters that perform `runBlocking { dataStore.data.first()[key] ?: defaultValue }` (or read from cached in-memory state where applicable) and setters that execute `scope.launch { dataStore.edit { pref -> pref[key] = value } }`.
  - Enables incremental, non-breaking migration across the ~100 existing files without requiring immediate refactoring of synchronous call-sites into suspend functions.
- **Reactive Streams (`val keyFlow: Flow<Type>`)**:
  - Expose reactive `Flow<T>` properties (`dataStore.data.map { pref -> ... }.distinctUntilChanged()`) for Compose UI and ViewModel consumption.
- **Coroutines & Concurrency Dispatching**:
  - All default implementations inject `@ApplicationScope private val scope: CoroutineScope` and `@PluviaDataStore private val dataStore: DataStore<Preferences>`.
  - Mutators run safely in the background supervisor scope, preventing task cancellation when caller UI lifecycles destroy.

---

## 3. Complete Implementation Blueprint

### 3.1 Domain 1: `AuthPreferences` & `DefaultAuthPreferences`

#### `app/src/main/java/app/gamenative/preferences/AuthPreferences.kt`
```kotlin
package app.gamenative.preferences

import `in`.dragonbra.javasteam.enums.EPersonaState
import kotlinx.coroutines.flow.Flow

interface AuthPreferences {
    var username: String
    var accessToken: String
    var refreshToken: String
    var clientId: Long?
    var cellId: Int
    var cellIdManuallySet: Boolean
    var personaState: EPersonaState
    var steamUserAccountId: Int
    var steamUserSteamId64: Long
    var steamUserAvatarHash: String
    var steamUserName: String
    var lastPICSChangeNumber: Int
    var steamOfflineMode: Boolean
    var epicOfflineMode: Boolean
    var friendsListHeader: Set<String>
    var ackChatPreview: Boolean

    val personaStateFlow: Flow<EPersonaState>
    val steamUserSteamId64Flow: Flow<Long>
    val usernameFlow: Flow<String>
    val steamOfflineModeFlow: Flow<Boolean>
    val epicOfflineModeFlow: Flow<Boolean>

    suspend fun clearSteamSession()
    suspend fun clearAll()
}
```

#### `app/src/main/java/app/gamenative/preferences/DefaultAuthPreferences.kt`
```kotlin
package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import app.gamenative.Crypto
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.di.PluviaDataStore
import `in`.dragonbra.javasteam.enums.EPersonaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAuthPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : AuthPreferences {

    private companion object {
        val USER_NAME = stringPreferencesKey("user_name")
        val ACCESS_TOKEN_ENC = byteArrayPreferencesKey("access_token_enc")
        val REFRESH_TOKEN_ENC = byteArrayPreferencesKey("refresh_token_enc")
        val CLIENT_ID = longPreferencesKey("client_id")
        val CELL_ID = intPreferencesKey("cell_id")
        val CELL_ID_MANUALLY_SET = booleanPreferencesKey("cell_id_manually_set")
        val PERSONA_STATE = intPreferencesKey("persona_state")
        val STEAM_USER_ACCOUNT_ID = intPreferencesKey("steam_user_account_id")
        val STEAM_USER_STEAM_ID_64 = longPreferencesKey("steam_user_steam_id_64")
        val STEAM_USER_AVATAR_HASH = stringPreferencesKey("steam_user_avatar_hash")
        val STEAM_USER_NAME = stringPreferencesKey("steam_user_name")
        val LAST_PICS_CHANGE_NUMBER = intPreferencesKey("last_pics_change_number")
        val STEAM_OFFLINE_MODE = booleanPreferencesKey("steam_offline_mode")
        val EPIC_OFFLINE_MODE = booleanPreferencesKey("epic_offline_mode")
        val FRIENDS_LIST_HEADER = stringPreferencesKey("friends_list_header")
        val ACK_CHAT_PREVIEW = booleanPreferencesKey("ack_chat_preview")
        val STEAM_GAMES_COUNT = intPreferencesKey("steam_games_count")
    }

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var username: String
        get() = getPref(USER_NAME, "")
        set(value) = setPref(USER_NAME, value)

    override var accessToken: String
        get() {
            val encryptedBytes = getPref(ACCESS_TOKEN_ENC, ByteArray(0))
            return if (encryptedBytes.isEmpty()) {
                ""
            } else {
                try {
                    String(Crypto.decrypt(encryptedBytes))
                } catch (e: Exception) {
                    ""
                }
            }
        }
        set(value) {
            val bytes = if (value.isNotEmpty()) Crypto.encrypt(value.toByteArray()) else ByteArray(0)
            setPref(ACCESS_TOKEN_ENC, bytes)
        }

    override var refreshToken: String
        get() {
            val encryptedBytes = getPref(REFRESH_TOKEN_ENC, ByteArray(0))
            return if (encryptedBytes.isEmpty()) {
                ""
            } else {
                try {
                    String(Crypto.decrypt(encryptedBytes))
                } catch (e: Exception) {
                    ""
                }
            }
        }
        set(value) {
            val bytes = if (value.isNotEmpty()) Crypto.encrypt(value.toByteArray()) else ByteArray(0)
            setPref(REFRESH_TOKEN_ENC, bytes)
        }

    override var clientId: Long?
        get() = runBlocking { dataStore.data.first()[CLIENT_ID] }
        set(value) {
            scope.launch {
                dataStore.edit { pref ->
                    if (value != null) {
                        pref[CLIENT_ID] = value
                    } else {
                        pref.remove(CLIENT_ID)
                    }
                }
            }
        }

    override var cellId: Int
        get() = getPref(CELL_ID, 0)
        set(value) {
            setPref(CELL_ID, value)
            if (value == 0) {
                setPref(CELL_ID_MANUALLY_SET, false)
            }
        }

    override var cellIdManuallySet: Boolean
        get() = getPref(CELL_ID_MANUALLY_SET, false)
        set(value) = setPref(CELL_ID_MANUALLY_SET, value)

    override var personaState: EPersonaState
        get() {
            val value = getPref(PERSONA_STATE, EPersonaState.Online.code())
            return EPersonaState.from(value)
        }
        set(value) = setPref(PERSONA_STATE, value.code())

    override var steamUserAccountId: Int
        get() = getPref(STEAM_USER_ACCOUNT_ID, 0)
        set(value) = setPref(STEAM_USER_ACCOUNT_ID, value)

    override var steamUserSteamId64: Long
        get() = getPref(STEAM_USER_STEAM_ID_64, 0L)
        set(value) = setPref(STEAM_USER_STEAM_ID_64, value)

    override var steamUserAvatarHash: String
        get() = getPref(STEAM_USER_AVATAR_HASH, "")
        set(value) = setPref(STEAM_USER_AVATAR_HASH, value)

    override var steamUserName: String
        get() = getPref(STEAM_USER_NAME, "")
        set(value) = setPref(STEAM_USER_NAME, value)

    override var lastPICSChangeNumber: Int
        get() = getPref(LAST_PICS_CHANGE_NUMBER, 0)
        set(value) = setPref(LAST_PICS_CHANGE_NUMBER, value)

    override var steamOfflineMode: Boolean
        get() = getPref(STEAM_OFFLINE_MODE, false)
        set(value) = setPref(STEAM_OFFLINE_MODE, value)

    override var epicOfflineMode: Boolean
        get() = getPref(EPIC_OFFLINE_MODE, false)
        set(value) = setPref(EPIC_OFFLINE_MODE, value)

    override var friendsListHeader: Set<String>
        get() {
            val value = getPref(FRIENDS_LIST_HEADER, "[]")
            return try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) = setPref(FRIENDS_LIST_HEADER, Json.encodeToString(value))

    override var ackChatPreview: Boolean
        get() = getPref(ACK_CHAT_PREVIEW, false)
        set(value) = setPref(ACK_CHAT_PREVIEW, value)

    override val personaStateFlow: Flow<EPersonaState> = dataStore.data
        .map { pref -> EPersonaState.from(pref[PERSONA_STATE] ?: EPersonaState.Online.code()) }
        .distinctUntilChanged()

    override val steamUserSteamId64Flow: Flow<Long> = dataStore.data
        .map { pref -> pref[STEAM_USER_STEAM_ID_64] ?: 0L }
        .distinctUntilChanged()

    override val usernameFlow: Flow<String> = dataStore.data
        .map { pref -> pref[USER_NAME] ?: "" }
        .distinctUntilChanged()

    override val steamOfflineModeFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[STEAM_OFFLINE_MODE] ?: false }
        .distinctUntilChanged()

    override val epicOfflineModeFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[EPIC_OFFLINE_MODE] ?: false }
        .distinctUntilChanged()

    override suspend fun clearSteamSession() {
        dataStore.edit { pref ->
            pref.remove(USER_NAME)
            pref.remove(ACCESS_TOKEN_ENC)
            pref.remove(REFRESH_TOKEN_ENC)
            pref.remove(CLIENT_ID)
            pref.remove(PERSONA_STATE)
            pref.remove(STEAM_USER_ACCOUNT_ID)
            pref.remove(STEAM_USER_STEAM_ID_64)
            pref.remove(STEAM_USER_AVATAR_HASH)
            pref.remove(STEAM_USER_NAME)
            pref.remove(LAST_PICS_CHANGE_NUMBER)
            pref.remove(STEAM_GAMES_COUNT)
        }
    }

    override suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
```

---

### 3.2 Domain 2: `ContainerPreferences` & `DefaultContainerPreferences`

#### `app/src/main/java/app/gamenative/preferences/ContainerPreferences.kt`
```kotlin
package app.gamenative.preferences

interface ContainerPreferences {
    var screenSize: String
    var envVars: String
    var graphicsDriver: String
    var graphicsDriverVersion: String
    var graphicsDriverConfig: String
    var rendererPresentMode: String
    var displayRendererMode: String
    var sfCompatMode: Boolean
    var useLegacyRenderer: Boolean
    var sharpnessEffect: String
    var sharpnessLevel: Int
    var sharpnessDenoise: Int
    var containerVariant: String
    var wineVersion: String
    var emulator: String
    var fexcoreVersion: String
    var fexcoreTSOMode: String
    var fexcoreX87Mode: String
    var fexcoreMultiBlock: String
    var fexcorePreset: String
    var box86Preset: String
    var box64Preset: String
    var box86Version: String
    var box64Version: String
    var dxWrapper: String
    var dxWrapperConfig: String
    var audioDriver: String
    var pulseaudioLowLatency: Boolean
    var winComponents: String
    var drives: String
    var execArgs: String
    var suspendPolicy: String
    var cpuList: String
    var cpuListWoW64: String
    var wow64Mode: Boolean
    var startupSelection: Int
    var containerLanguage: String
    var renderer: String
    var csmt: Boolean
    var videoPciDeviceID: Int
    var offScreenRenderingMode: String
    var strictShaderMath: Boolean
    var useDRI3: Boolean
    var videoMemorySize: String
    var mouseWarpOverride: String
    var portraitMode: Boolean
    var launchRealSteam: Boolean
    var launchBionicSteam: Boolean
    var forceDlc: Boolean
    var localSavesOnly: Boolean
    var useLegacyDRM: Boolean
    var unpackFiles: Boolean
    var autoApplyKnownConfig: Boolean
    var enableWineDebug: Boolean
    var wineDebugChannels: String
}
```

#### `app/src/main/java/app/gamenative/preferences/DefaultContainerPreferences.kt`
```kotlin
package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import app.gamenative.Constants
import app.gamenative.PluviaApp
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.di.PluviaDataStore
import com.winlator.box86_64.Box86_64Preset
import com.winlator.container.Container
import com.winlator.core.DefaultVersion
import com.winlator.fexcore.FEXCorePreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultContainerPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : ContainerPreferences {

    private companion object {
        val SCREEN_SIZE = stringPreferencesKey("screen_size")
        val ENV_VARS = stringPreferencesKey("env_vars")
        val GRAPHICS_DRIVER = stringPreferencesKey("graphics_driver")
        val GRAPHICS_DRIVER_VERSION = stringPreferencesKey("graphics_driver_version")
        val GRAPHICS_DRIVER_CONFIG = stringPreferencesKey("graphics_driver_config")
        val RENDERER_PRESENT_MODE = stringPreferencesKey("renderer_present_mode")
        val DISPLAY_RENDERER_MODE = stringPreferencesKey("display_renderer_mode")
        val SF_COMPAT_MODE = booleanPreferencesKey("sf_compat_mode")
        val USE_LEGACY_RENDERER = booleanPreferencesKey("use_legacy_renderer")
        val SHARPNESS_EFFECT = stringPreferencesKey("sharpness_effect")
        val SHARPNESS_LEVEL = intPreferencesKey("sharpness_level")
        val SHARPNESS_DENOISE = intPreferencesKey("sharpness_denoise")
        val CONTAINER_VARIANT = stringPreferencesKey("container_variant")
        val WINE_VERSION = stringPreferencesKey("wine_version")
        val EMULATOR = stringPreferencesKey("emulator")
        val FEXCORE_VERSION = stringPreferencesKey("fexcore_version")
        val FEXCORE_TSO_MODE = stringPreferencesKey("fexcore_tso_mode")
        val FEXCORE_X87_MODE = stringPreferencesKey("fexcore_x87_mode")
        val FEXCORE_MULTIBLOCK = stringPreferencesKey("fexcore_multiblock")
        val FEXCORE_PRESET = stringPreferencesKey("fexcore_preset")
        val BOX86_PRESET = stringPreferencesKey("box86_preset")
        val BOX64_PRESET = stringPreferencesKey("box64_preset")
        val BOX86_VERSION = stringPreferencesKey("box86_version")
        val BOX64_VERSION = stringPreferencesKey("box64_version")
        val DXWRAPPER = stringPreferencesKey("dxwrapper")
        val DXWRAPPER_CONFIG = stringPreferencesKey("dxwrapperConfig")
        val AUDIO_DRIVER = stringPreferencesKey("audio_driver")
        val PULSEAUDIO_LOW_LATENCY = booleanPreferencesKey("pulseaudio_low_latency")
        val WIN_COMPONENTS = stringPreferencesKey("wincomponents")
        val DRIVES = stringPreferencesKey("drives")
        val EXEC_ARGS = stringPreferencesKey("exec_args")
        val SUSPEND_POLICY = stringPreferencesKey("suspend_policy")
        val CPU_LIST = stringPreferencesKey("cpu_list")
        val CPU_LIST_WOW64 = stringPreferencesKey("cpu_list_wow64")
        val WOW64_MODE = booleanPreferencesKey("wow64_mode")
        val STARTUP_SELECTION = intPreferencesKey("startup_selection")
        val CONTAINER_LANGUAGE = stringPreferencesKey("container_language")
        val RENDERER = stringPreferencesKey("renderer")
        val CSMT = booleanPreferencesKey("csmt")
        val VIDEO_PCI_DEVICE_ID = intPreferencesKey("videoPciDeviceID")
        val OFFSCREEN_RENDERING_MODE = stringPreferencesKey("offScreenRenderingMode")
        val STRICT_SHADER_MATH = booleanPreferencesKey("strictShaderMath")
        val USE_DRI3 = booleanPreferencesKey("useDRI3")
        val VIDEO_MEMORY_SIZE = stringPreferencesKey("videoMemorySize")
        val MOUSE_WARP_OVERRIDE = stringPreferencesKey("mouseWarpOverride")
        val PORTRAIT_MODE = booleanPreferencesKey("portrait_mode")
        val LAUNCH_REAL_STEAM = booleanPreferencesKey("launch_real_steam")
        val LAUNCH_BIONIC_STEAM = booleanPreferencesKey("launch_bionic_steam")
        val FORCE_DLC = booleanPreferencesKey("force_dlc")
        val LOCAL_SAVES_ONLY = booleanPreferencesKey("local_saves_only")
        val USE_LEGACY_DRM = booleanPreferencesKey("use_legacy_drm")
        val UNPACK_FILES = booleanPreferencesKey("unpack_files")
        val AUTO_APPLY_KNOWN_CONFIG = booleanPreferencesKey("auto_apply_known_config")
        val ENABLE_WINE_DEBUG = booleanPreferencesKey("enable_wine_debug")
        val WINE_DEBUG_CHANNELS = stringPreferencesKey("wine_debug_channels")
    }

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var screenSize: String
        get() = getPref(SCREEN_SIZE, PluviaApp.getDefaultScreenSize())
        set(value) = setPref(SCREEN_SIZE, value)

    override var envVars: String
        get() = getPref(ENV_VARS, Container.DEFAULT_ENV_VARS)
        set(value) = setPref(ENV_VARS, value)

    override var graphicsDriver: String
        get() = getPref(GRAPHICS_DRIVER, Container.DEFAULT_GRAPHICS_DRIVER)
        set(value) = setPref(GRAPHICS_DRIVER, value)

    override var graphicsDriverVersion: String
        get() = getPref(GRAPHICS_DRIVER_VERSION, "")
        set(value) = setPref(GRAPHICS_DRIVER_VERSION, value)

    override var graphicsDriverConfig: String
        get() = getPref(GRAPHICS_DRIVER_CONFIG, Container.DEFAULT_GRAPHICSDRIVERCONFIG)
        set(value) = setPref(GRAPHICS_DRIVER_CONFIG, value)

    override var rendererPresentMode: String
        get() = getPref(RENDERER_PRESENT_MODE, "fifo")
        set(value) = setPref(RENDERER_PRESENT_MODE, value)

    override var displayRendererMode: String
        get() {
            val stored = getPref(DISPLAY_RENDERER_MODE, "")
            if (stored.isNotEmpty()) return stored
            return if (getPref(USE_LEGACY_RENDERER, false)) "gl" else "vulkan"
        }
        set(value) = setPref(DISPLAY_RENDERER_MODE, value)

    override var sfCompatMode: Boolean
        get() = getPref(SF_COMPAT_MODE, true)
        set(value) = setPref(SF_COMPAT_MODE, value)

    override var useLegacyRenderer: Boolean
        get() = getPref(USE_LEGACY_RENDERER, false)
        set(value) = setPref(USE_LEGACY_RENDERER, value)

    override var sharpnessEffect: String
        get() = getPref(SHARPNESS_EFFECT, "None")
        set(value) = setPref(SHARPNESS_EFFECT, value)

    override var sharpnessLevel: Int
        get() = getPref(SHARPNESS_LEVEL, 100)
        set(value) = setPref(SHARPNESS_LEVEL, value.coerceIn(0, 100))

    override var sharpnessDenoise: Int
        get() = getPref(SHARPNESS_DENOISE, 100)
        set(value) = setPref(SHARPNESS_DENOISE, value.coerceIn(0, 100))

    override var containerVariant: String
        get() = getPref(CONTAINER_VARIANT, Container.DEFAULT_VARIANT)
        set(value) = setPref(CONTAINER_VARIANT, value)

    override var wineVersion: String
        get() = getPref(WINE_VERSION, Container.DEFAULT_WINE_VERSION)
        set(value) = setPref(WINE_VERSION, value)

    override var emulator: String
        get() = getPref(EMULATOR, Container.DEFAULT_EMULATOR)
        set(value) = setPref(EMULATOR, value)

    override var fexcoreVersion: String
        get() = getPref(FEXCORE_VERSION, DefaultVersion.FEXCORE)
        set(value) = setPref(FEXCORE_VERSION, value)

    override var fexcoreTSOMode: String
        get() = getPref(FEXCORE_TSO_MODE, "Fast")
        set(value) = setPref(FEXCORE_TSO_MODE, value)

    override var fexcoreX87Mode: String
        get() = getPref(FEXCORE_X87_MODE, "Fast")
        set(value) = setPref(FEXCORE_X87_MODE, value)

    override var fexcoreMultiBlock: String
        get() = getPref(FEXCORE_MULTIBLOCK, "Disabled")
        set(value) = setPref(FEXCORE_MULTIBLOCK, value)

    override var fexcorePreset: String
        get() = getPref(FEXCORE_PRESET, FEXCorePreset.INTERMEDIATE)
        set(value) = setPref(FEXCORE_PRESET, value)

    override var box86Preset: String
        get() = getPref(BOX86_PRESET, Box86_64Preset.COMPATIBILITY)
        set(value) = setPref(BOX86_PRESET, value)

    override var box64Preset: String
        get() = getPref(BOX64_PRESET, Box86_64Preset.COMPATIBILITY)
        set(value) = setPref(BOX64_PRESET, value)

    override var box86Version: String
        get() = getPref(BOX86_VERSION, DefaultVersion.BOX86)
        set(value) = setPref(BOX86_VERSION, value)

    override var box64Version: String
        get() = getPref(BOX64_VERSION, DefaultVersion.BOX64)
        set(value) = setPref(BOX64_VERSION, value)

    override var dxWrapper: String
        get() = getPref(DXWRAPPER, Container.DEFAULT_DXWRAPPER)
        set(value) = setPref(DXWRAPPER, value)

    override var dxWrapperConfig: String
        get() = getPref(DXWRAPPER_CONFIG, Container.DEFAULT_DXWRAPPERCONFIG)
        set(value) = setPref(DXWRAPPER_CONFIG, value)

    override var audioDriver: String
        get() = getPref(AUDIO_DRIVER, Container.DEFAULT_AUDIO_DRIVER)
        set(value) = setPref(AUDIO_DRIVER, value)

    override var pulseaudioLowLatency: Boolean
        get() = getPref(PULSEAUDIO_LOW_LATENCY, false)
        set(value) = setPref(PULSEAUDIO_LOW_LATENCY, value)

    override var winComponents: String
        get() = getPref(WIN_COMPONENTS, Container.DEFAULT_WINCOMPONENTS)
        set(value) = setPref(WIN_COMPONENTS, value)

    override var drives: String
        get() = getPref(DRIVES, Container.DEFAULT_DRIVES)
        set(value) = setPref(DRIVES, value)

    override var execArgs: String
        get() = getPref(EXEC_ARGS, "")
        set(value) = setPref(EXEC_ARGS, value)

    override var suspendPolicy: String
        get() = Container.normalizeSuspendPolicy(getPref(SUSPEND_POLICY, Container.SUSPEND_POLICY_MANUAL))
        set(value) = setPref(SUSPEND_POLICY, Container.normalizeSuspendPolicy(value))

    override var cpuList: String
        get() = getPref(CPU_LIST, Container.getFallbackCPUList())
        set(value) = setPref(CPU_LIST, value)

    override var cpuListWoW64: String
        get() = getPref(CPU_LIST_WOW64, Container.getFallbackCPUListWoW64())
        set(value) = setPref(CPU_LIST_WOW64, value)

    override var wow64Mode: Boolean
        get() = getPref(WOW64_MODE, true)
        set(value) = setPref(WOW64_MODE, value)

    override var startupSelection: Int
        get() = getPref(STARTUP_SELECTION, Container.STARTUP_SELECTION_ESSENTIAL.toInt())
        set(value) = setPref(STARTUP_SELECTION, value)

    override var containerLanguage: String
        get() = getPref(CONTAINER_LANGUAGE, "english")
        set(value) = setPref(CONTAINER_LANGUAGE, value)

    override var renderer: String
        get() = getPref(RENDERER, "gl")
        set(value) = setPref(RENDERER, value)

    override var csmt: Boolean
        get() = getPref(CSMT, true)
        set(value) = setPref(CSMT, value)

    override var videoPciDeviceID: Int
        get() = getPref(VIDEO_PCI_DEVICE_ID, 1728)
        set(value) = setPref(VIDEO_PCI_DEVICE_ID, value)

    override var offScreenRenderingMode: String
        get() = getPref(OFFSCREEN_RENDERING_MODE, "fbo")
        set(value) = setPref(OFFSCREEN_RENDERING_MODE, value)

    override var strictShaderMath: Boolean
        get() = getPref(STRICT_SHADER_MATH, true)
        set(value) = setPref(STRICT_SHADER_MATH, value)

    override var useDRI3: Boolean
        get() = getPref(USE_DRI3, true)
        set(value) = setPref(USE_DRI3, value)

    override var videoMemorySize: String
        get() = getPref(VIDEO_MEMORY_SIZE, "2048")
        set(value) = setPref(VIDEO_MEMORY_SIZE, value)

    override var mouseWarpOverride: String
        get() = getPref(MOUSE_WARP_OVERRIDE, "disable")
        set(value) = setPref(MOUSE_WARP_OVERRIDE, value)

    override var portraitMode: Boolean
        get() = getPref(PORTRAIT_MODE, false)
        set(value) = setPref(PORTRAIT_MODE, value)

    override var launchRealSteam: Boolean
        get() = getPref(LAUNCH_REAL_STEAM, false)
        set(value) = setPref(LAUNCH_REAL_STEAM, value)

    override var launchBionicSteam: Boolean
        get() = getPref(LAUNCH_BIONIC_STEAM, false)
        set(value) = setPref(LAUNCH_BIONIC_STEAM, value)

    override var forceDlc: Boolean
        get() = getPref(FORCE_DLC, false)
        set(value) = setPref(FORCE_DLC, value)

    override var localSavesOnly: Boolean
        get() = getPref(LOCAL_SAVES_ONLY, false)
        set(value) = setPref(LOCAL_SAVES_ONLY, value)

    override var useLegacyDRM: Boolean
        get() = getPref(USE_LEGACY_DRM, false)
        set(value) = setPref(USE_LEGACY_DRM, value)

    override var unpackFiles: Boolean
        get() = getPref(UNPACK_FILES, false)
        set(value) = setPref(UNPACK_FILES, value)

    override var autoApplyKnownConfig: Boolean
        get() = getPref(AUTO_APPLY_KNOWN_CONFIG, true)
        set(value) = setPref(AUTO_APPLY_KNOWN_CONFIG, value)

    override var enableWineDebug: Boolean
        get() = getPref(ENABLE_WINE_DEBUG, false)
        set(value) = setPref(ENABLE_WINE_DEBUG, value)

    override var wineDebugChannels: String
        get() = getPref(WINE_DEBUG_CHANNELS, Constants.XServer.DEFAULT_WINE_DEBUG_CHANNELS)
        set(value) = setPref(WINE_DEBUG_CHANNELS, value)
}
```

---

### 3.3 Domain 3: `InputPreferences` & `DefaultInputPreferences`

#### `app/src/main/java/app/gamenative/preferences/InputPreferences.kt`
```kotlin
package app.gamenative.preferences

import kotlinx.coroutines.flow.Flow

interface InputPreferences {
    var useSteamInput: Boolean
    var xinputEnabled: Boolean
    var dinputEnabled: Boolean
    var dinputMapperType: Int
    var externalDisplayInputMode: String
    var externalDisplaySwap: Boolean
    var disableMouseInput: Boolean
    var swapFaceButtons: Boolean
    var showGamepadHints: Boolean
    var showControllerDebugMenu: Boolean

    val swapFaceButtonsFlow: Flow<Boolean>
    val showGamepadHintsFlow: Flow<Boolean>
    val useSteamInputFlow: Flow<Boolean>
}
```

#### `app/src/main/java/app/gamenative/preferences/DefaultInputPreferences.kt`
```kotlin
package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.di.PluviaDataStore
import com.winlator.container.Container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultInputPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : InputPreferences {

    private companion object {
        val USE_STEAM_INPUT = booleanPreferencesKey("use_steam_input")
        val XINPUT_ENABLED = booleanPreferencesKey("xinput_enabled")
        val DINPUT_ENABLED = booleanPreferencesKey("dinput_enabled")
        val DINPUT_MAPPER_TYPE = intPreferencesKey("dinput_mapper_type")
        val EXTERNAL_DISPLAY_INPUT_MODE = stringPreferencesKey("external_display_input_mode")
        val EXTERNAL_DISPLAY_SWAP = booleanPreferencesKey("external_display_swap")
        val DISABLE_MOUSE_INPUT = booleanPreferencesKey("disable_mouse_input")
        val SWAP_FACE_BUTTONS = booleanPreferencesKey("swap_face_buttons")
        val SHOW_GAMEPAD_HINTS = booleanPreferencesKey("show_gamepad_hints")
        val SHOW_CONTROLLER_DEBUG_MENU = booleanPreferencesKey("show_controller_debug_menu")
    }

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var useSteamInput: Boolean
        get() = getPref(USE_STEAM_INPUT, false)
        set(value) = setPref(USE_STEAM_INPUT, value)

    override var xinputEnabled: Boolean
        get() = getPref(XINPUT_ENABLED, true)
        set(value) = setPref(XINPUT_ENABLED, value)

    override var dinputEnabled: Boolean
        get() = getPref(DINPUT_ENABLED, true)
        set(value) = setPref(DINPUT_ENABLED, value)

    override var dinputMapperType: Int
        get() = getPref(DINPUT_MAPPER_TYPE, 1)
        set(value) = setPref(DINPUT_MAPPER_TYPE, value)

    override var externalDisplayInputMode: String
        get() = getPref(EXTERNAL_DISPLAY_INPUT_MODE, Container.DEFAULT_EXTERNAL_DISPLAY_MODE)
        set(value) = setPref(EXTERNAL_DISPLAY_INPUT_MODE, value)

    override var externalDisplaySwap: Boolean
        get() = getPref(EXTERNAL_DISPLAY_SWAP, false)
        set(value) = setPref(EXTERNAL_DISPLAY_SWAP, value)

    override var disableMouseInput: Boolean
        get() = getPref(DISABLE_MOUSE_INPUT, false)
        set(value) = setPref(DISABLE_MOUSE_INPUT, value)

    override var swapFaceButtons: Boolean
        get() = getPref(SWAP_FACE_BUTTONS, false)
        set(value) = setPref(SWAP_FACE_BUTTONS, value)

    override var showGamepadHints: Boolean
        get() = getPref(SHOW_GAMEPAD_HINTS, true)
        set(value) = setPref(SHOW_GAMEPAD_HINTS, value)

    override var showControllerDebugMenu: Boolean
        get() = getPref(SHOW_CONTROLLER_DEBUG_MENU, false)
        set(value) = setPref(SHOW_CONTROLLER_DEBUG_MENU, value)

    override val swapFaceButtonsFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SWAP_FACE_BUTTONS] ?: false }
        .distinctUntilChanged()

    override val showGamepadHintsFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_GAMEPAD_HINTS] ?: true }
        .distinctUntilChanged()

    override val useSteamInputFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[USE_STEAM_INPUT] ?: false }
        .distinctUntilChanged()
}
```

---

### 3.4 Domain 4: `HudPreferences` & `DefaultHudPreferences`

#### `app/src/main/java/app/gamenative/preferences/HudPreferences.kt`
```kotlin
package app.gamenative.preferences

import app.gamenative.ui.data.PerformanceHudConfig
import kotlinx.coroutines.flow.Flow

interface HudPreferences {
    var showFps: Boolean
    var quickMenuLastTab: Int
    var performanceHudCompactMode: Boolean
    var performanceHudShowFrameRate: Boolean
    var performanceHudShowCpuUsage: Boolean
    var performanceHudShowGpuUsage: Boolean
    var performanceHudShowRamUsage: Boolean
    var performanceHudShowBatteryLevel: Boolean
    var performanceHudShowPowerDraw: Boolean
    var performanceHudShowBatteryRuntime: Boolean
    var performanceHudShowBatteryTemperature: Boolean
    var performanceHudShowClockTime: Boolean
    var performanceHudShowCpuTemperature: Boolean
    var performanceHudShowGpuTemperature: Boolean
    var showPerformanceHudFan: Boolean
    var showPerformanceHudTunerCaps: Boolean
    var performanceHudShowFrameRateGraph: Boolean
    var performanceHudShowCpuUsageGraph: Boolean
    var performanceHudShowGpuUsageGraph: Boolean
    var performanceHudBackgroundOpacity: Float
    var performanceHudColorIntensity: Float
    var performanceHudShowTextOutline: Boolean
    var performanceHudSize: String
    var performanceHudXFraction: Float
    var performanceHudYFraction: Float
    var powerControlDefaultEnabled: Boolean

    fun getHudConfig(): PerformanceHudConfig
    fun setHudConfig(config: PerformanceHudConfig)

    val hudConfigFlow: Flow<PerformanceHudConfig>
    val showFpsFlow: Flow<Boolean>
    val quickMenuLastTabFlow: Flow<Int>
}
```

#### `app/src/main/java/app/gamenative/preferences/DefaultHudPreferences.kt`
```kotlin
package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.di.PluviaDataStore
import app.gamenative.powercontrol.autotuning.DeviceGate
import app.gamenative.ui.data.PerformanceHudConfig
import app.gamenative.ui.data.PerformanceHudSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultHudPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : HudPreferences {

    private companion object {
        val SHOW_FPS = booleanPreferencesKey("show_fps")
        val QUICK_MENU_LAST_TAB = intPreferencesKey("quick_menu_last_tab")
        val PERFORMANCE_HUD_COMPACT_MODE = booleanPreferencesKey("performance_hud_compact_mode")
        val PERFORMANCE_HUD_SHOW_FRAME_RATE = booleanPreferencesKey("performance_hud_show_frame_rate")
        val PERFORMANCE_HUD_SHOW_CPU_USAGE = booleanPreferencesKey("performance_hud_show_cpu_usage")
        val PERFORMANCE_HUD_SHOW_GPU_USAGE = booleanPreferencesKey("performance_hud_show_gpu_usage")
        val PERFORMANCE_HUD_SHOW_RAM_USAGE = booleanPreferencesKey("performance_hud_show_ram_usage")
        val PERFORMANCE_HUD_SHOW_BATTERY_LEVEL = booleanPreferencesKey("performance_hud_show_battery_level")
        val PERFORMANCE_HUD_SHOW_POWER_DRAW = booleanPreferencesKey("performance_hud_show_power_draw")
        val PERFORMANCE_HUD_SHOW_BATTERY_RUNTIME = booleanPreferencesKey("performance_hud_show_battery_runtime")
        val PERFORMANCE_HUD_SHOW_BATTERY_TEMPERATURE = booleanPreferencesKey("performance_hud_show_battery_temperature")
        val PERFORMANCE_HUD_SHOW_CLOCK_TIME = booleanPreferencesKey("performance_hud_show_clock_time")
        val PERFORMANCE_HUD_SHOW_CPU_TEMPERATURE = booleanPreferencesKey("performance_hud_show_cpu_temperature")
        val PERFORMANCE_HUD_SHOW_GPU_TEMPERATURE = booleanPreferencesKey("performance_hud_show_gpu_temperature")
        val PERFORMANCE_HUD_SHOW_FAN = booleanPreferencesKey("performance_hud_show_fan")
        val PERFORMANCE_HUD_SHOW_TUNER_CAPS = booleanPreferencesKey("performance_hud_show_tuner_caps")
        val PERFORMANCE_HUD_SHOW_FRAME_RATE_GRAPH = booleanPreferencesKey("performance_hud_show_frame_rate_graph")
        val PERFORMANCE_HUD_SHOW_CPU_USAGE_GRAPH = booleanPreferencesKey("performance_hud_show_cpu_usage_graph")
        val PERFORMANCE_HUD_SHOW_GPU_USAGE_GRAPH = booleanPreferencesKey("performance_hud_show_gpu_usage_graph")
        val PERFORMANCE_HUD_BACKGROUND_OPACITY = floatPreferencesKey("performance_hud_background_opacity")
        val PERFORMANCE_HUD_COLOR_INTENSITY = floatPreferencesKey("performance_hud_color_intensity")
        val PERFORMANCE_HUD_SHOW_TEXT_OUTLINE = booleanPreferencesKey("performance_hud_show_text_outline")
        val PERFORMANCE_HUD_SIZE = stringPreferencesKey("performance_hud_size")
        val PERFORMANCE_HUD_X_FRACTION = floatPreferencesKey("performance_hud_x_fraction")
        val PERFORMANCE_HUD_Y_FRACTION = floatPreferencesKey("performance_hud_y_fraction")
        val POWER_CONTROL_DEFAULT_ENABLED = booleanPreferencesKey("power_control_default_enabled")
    }

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var showFps: Boolean
        get() = getPref(SHOW_FPS, false)
        set(value) = setPref(SHOW_FPS, value)

    override var quickMenuLastTab: Int
        get() = getPref(QUICK_MENU_LAST_TAB, 0)
        set(value) = setPref(QUICK_MENU_LAST_TAB, value.coerceIn(0, 6))

    override var performanceHudCompactMode: Boolean
        get() = getPref(PERFORMANCE_HUD_COMPACT_MODE, false)
        set(value) = setPref(PERFORMANCE_HUD_COMPACT_MODE, value)

    override var performanceHudShowFrameRate: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_FRAME_RATE, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_FRAME_RATE, value)

    override var performanceHudShowCpuUsage: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_CPU_USAGE, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_CPU_USAGE, value)

    override var performanceHudShowGpuUsage: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_GPU_USAGE, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_GPU_USAGE, value)

    override var performanceHudShowRamUsage: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_RAM_USAGE, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_RAM_USAGE, value)

    override var performanceHudShowBatteryLevel: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_BATTERY_LEVEL, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_BATTERY_LEVEL, value)

    override var performanceHudShowPowerDraw: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_POWER_DRAW, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_POWER_DRAW, value)

    override var performanceHudShowBatteryRuntime: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_BATTERY_RUNTIME, false)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_BATTERY_RUNTIME, value)

    override var performanceHudShowBatteryTemperature: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_BATTERY_TEMPERATURE, false)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_BATTERY_TEMPERATURE, value)

    override var performanceHudShowClockTime: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_CLOCK_TIME, false)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_CLOCK_TIME, value)

    override var performanceHudShowCpuTemperature: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_CPU_TEMPERATURE, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_CPU_TEMPERATURE, value)

    override var performanceHudShowGpuTemperature: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_GPU_TEMPERATURE, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_GPU_TEMPERATURE, value)

    override var showPerformanceHudFan: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_FAN, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_FAN, value)

    override var showPerformanceHudTunerCaps: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_TUNER_CAPS, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_TUNER_CAPS, value)

    override var performanceHudShowFrameRateGraph: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_FRAME_RATE_GRAPH, false)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_FRAME_RATE_GRAPH, value)

    override var performanceHudShowCpuUsageGraph: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_CPU_USAGE_GRAPH, false)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_CPU_USAGE_GRAPH, value)

    override var performanceHudShowGpuUsageGraph: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_GPU_USAGE_GRAPH, false)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_GPU_USAGE_GRAPH, value)

    override var performanceHudBackgroundOpacity: Float
        get() = getPref(PERFORMANCE_HUD_BACKGROUND_OPACITY, 0.72f)
        set(value) = setPref(PERFORMANCE_HUD_BACKGROUND_OPACITY, value.coerceIn(0f, 1f))

    override var performanceHudColorIntensity: Float
        get() = getPref(PERFORMANCE_HUD_COLOR_INTENSITY, 1f)
        set(value) = setPref(PERFORMANCE_HUD_COLOR_INTENSITY, value.coerceIn(0f, 1f))

    override var performanceHudShowTextOutline: Boolean
        get() = getPref(PERFORMANCE_HUD_SHOW_TEXT_OUTLINE, true)
        set(value) = setPref(PERFORMANCE_HUD_SHOW_TEXT_OUTLINE, value)

    override var performanceHudSize: String
        get() = getPref(PERFORMANCE_HUD_SIZE, "medium")
        set(value) = setPref(PERFORMANCE_HUD_SIZE, value)

    override var performanceHudXFraction: Float
        get() = getPref(PERFORMANCE_HUD_X_FRACTION, -1f)
        set(value) = setPref(PERFORMANCE_HUD_X_FRACTION, value.coerceIn(-1f, 1f))

    override var performanceHudYFraction: Float
        get() = getPref(PERFORMANCE_HUD_Y_FRACTION, -1f)
        set(value) = setPref(PERFORMANCE_HUD_Y_FRACTION, value.coerceIn(-1f, 1f))

    override var powerControlDefaultEnabled: Boolean
        get() = getPref(POWER_CONTROL_DEFAULT_ENABLED, DeviceGate.isDeviceSupported())
        set(value) = setPref(POWER_CONTROL_DEFAULT_ENABLED, value)

    override fun getHudConfig(): PerformanceHudConfig {
        return PerformanceHudConfig(
            showFrameRate = performanceHudShowFrameRate,
            showCpuUsage = performanceHudShowCpuUsage,
            showGpuUsage = performanceHudShowGpuUsage,
            showRamUsage = performanceHudShowRamUsage,
            showBatteryLevel = performanceHudShowBatteryLevel,
            showPowerDraw = performanceHudShowPowerDraw,
            showBatteryRuntime = performanceHudShowBatteryRuntime,
            showBatteryTemperature = performanceHudShowBatteryTemperature,
            showClockTime = performanceHudShowClockTime,
            showCpuTemperature = performanceHudShowCpuTemperature,
            showGpuTemperature = performanceHudShowGpuTemperature,
            showFrameRateGraph = performanceHudShowFrameRateGraph,
            showCpuUsageGraph = performanceHudShowCpuUsageGraph,
            showGpuUsageGraph = performanceHudShowGpuUsageGraph,
            backgroundOpacity = performanceHudBackgroundOpacity,
            colorIntensity = performanceHudColorIntensity,
            showTextOutline = performanceHudShowTextOutline,
            size = PerformanceHudSize.fromPrefValue(performanceHudSize),
        )
    }

    override fun setHudConfig(config: PerformanceHudConfig) {
        scope.launch {
            dataStore.edit { pref ->
                pref[PERFORMANCE_HUD_SHOW_FRAME_RATE] = config.showFrameRate
                pref[PERFORMANCE_HUD_SHOW_CPU_USAGE] = config.showCpuUsage
                pref[PERFORMANCE_HUD_SHOW_GPU_USAGE] = config.showGpuUsage
                pref[PERFORMANCE_HUD_SHOW_RAM_USAGE] = config.showRamUsage
                pref[PERFORMANCE_HUD_SHOW_BATTERY_LEVEL] = config.showBatteryLevel
                pref[PERFORMANCE_HUD_SHOW_POWER_DRAW] = config.showPowerDraw
                pref[PERFORMANCE_HUD_SHOW_BATTERY_RUNTIME] = config.showBatteryRuntime
                pref[PERFORMANCE_HUD_SHOW_BATTERY_TEMPERATURE] = config.showBatteryTemperature
                pref[PERFORMANCE_HUD_SHOW_CLOCK_TIME] = config.showClockTime
                pref[PERFORMANCE_HUD_SHOW_CPU_TEMPERATURE] = config.showCpuTemperature
                pref[PERFORMANCE_HUD_SHOW_GPU_TEMPERATURE] = config.showGpuTemperature
                pref[PERFORMANCE_HUD_SHOW_FRAME_RATE_GRAPH] = config.showFrameRateGraph
                pref[PERFORMANCE_HUD_SHOW_CPU_USAGE_GRAPH] = config.showCpuUsageGraph
                pref[PERFORMANCE_HUD_SHOW_GPU_USAGE_GRAPH] = config.showGpuUsageGraph
                pref[PERFORMANCE_HUD_BACKGROUND_OPACITY] = config.backgroundOpacity.coerceIn(0f, 1f)
                pref[PERFORMANCE_HUD_COLOR_INTENSITY] = config.colorIntensity.coerceIn(0f, 1f)
                pref[PERFORMANCE_HUD_SHOW_TEXT_OUTLINE] = config.showTextOutline
                pref[PERFORMANCE_HUD_SIZE] = config.size.prefValue
            }
        }
    }

    override val hudConfigFlow: Flow<PerformanceHudConfig> = dataStore.data
        .map { pref ->
            PerformanceHudConfig(
                showFrameRate = pref[PERFORMANCE_HUD_SHOW_FRAME_RATE] ?: true,
                showCpuUsage = pref[PERFORMANCE_HUD_SHOW_CPU_USAGE] ?: true,
                showGpuUsage = pref[PERFORMANCE_HUD_SHOW_GPU_USAGE] ?: true,
                showRamUsage = pref[PERFORMANCE_HUD_SHOW_RAM_USAGE] ?: true,
                showBatteryLevel = pref[PERFORMANCE_HUD_SHOW_BATTERY_LEVEL] ?: true,
                showPowerDraw = pref[PERFORMANCE_HUD_SHOW_POWER_DRAW] ?: true,
                showBatteryRuntime = pref[PERFORMANCE_HUD_SHOW_BATTERY_RUNTIME] ?: false,
                showBatteryTemperature = pref[PERFORMANCE_HUD_SHOW_BATTERY_TEMPERATURE] ?: false,
                showClockTime = pref[PERFORMANCE_HUD_SHOW_CLOCK_TIME] ?: false,
                showCpuTemperature = pref[PERFORMANCE_HUD_SHOW_CPU_TEMPERATURE] ?: true,
                showGpuTemperature = pref[PERFORMANCE_HUD_SHOW_GPU_TEMPERATURE] ?: true,
                showFrameRateGraph = pref[PERFORMANCE_HUD_SHOW_FRAME_RATE_GRAPH] ?: false,
                showCpuUsageGraph = pref[PERFORMANCE_HUD_SHOW_CPU_USAGE_GRAPH] ?: false,
                showGpuUsageGraph = pref[PERFORMANCE_HUD_SHOW_GPU_USAGE_GRAPH] ?: false,
                backgroundOpacity = pref[PERFORMANCE_HUD_BACKGROUND_OPACITY] ?: 0.72f,
                colorIntensity = pref[PERFORMANCE_HUD_COLOR_INTENSITY] ?: 1f,
                showTextOutline = pref[PERFORMANCE_HUD_SHOW_TEXT_OUTLINE] ?: true,
                size = PerformanceHudSize.fromPrefValue(pref[PERFORMANCE_HUD_SIZE] ?: "medium"),
            )
        }
        .distinctUntilChanged()

    override val showFpsFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_FPS] ?: false }
        .distinctUntilChanged()

    override val quickMenuLastTabFlow: Flow<Int> = dataStore.data
        .map { pref -> pref[QUICK_MENU_LAST_TAB] ?: 0 }
        .distinctUntilChanged()
}
```

---

### 3.5 Domain 5: `LibraryPreferences` & `DefaultLibraryPreferences`

#### `app/src/main/java/app/gamenative/preferences/LibraryPreferences.kt`
```kotlin
package app.gamenative.preferences

import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.enums.SortOption
import java.util.EnumSet
import kotlinx.coroutines.flow.Flow

interface LibraryPreferences {
    var libraryLayout: PaneType
    var libraryFilter: EnumSet<AppFilter>
    var librarySortOption: SortOption
    var itemsPerPage: Int
    var showSteamInLibrary: Boolean
    var showCustomGamesInLibrary: Boolean
    var showGOGInLibrary: Boolean
    var showEpicInLibrary: Boolean
    var showAmazonInLibrary: Boolean
    var customGamesCount: Int
    var steamGamesCount: Int
    var gogGamesCount: Int
    var epicGamesCount: Int
    var gogInstalledGamesCount: Int
    var epicInstalledGamesCount: Int
    var amazonInstalledGamesCount: Int
    var librarySteamCollectionsCache: String
    var librarySteamCollectionsSkippedDynamic: Boolean
    var librarySteamCollections: Set<String>
    var recommendationCacheJson: String
    var recommendationCacheTimestamp: Long
    var showRecommendations: Boolean
    var recDisclosureShown: Boolean
    var recTeaserDismissedDay: Long
    var showAddCustomGameDialog: Boolean
    var importCustomGameAsSteamGame: Boolean
    var customGamePaths: Set<String>
    var customGameManualFolders: Set<String>
    var favoriteAppIds: Set<String>
    var gogAmazonPathMigrated: Boolean

    val showRecommendationsFlow: Flow<Boolean>
    val favoriteAppIdsFlow: Flow<Set<String>>
    val libraryLayoutFlow: Flow<PaneType>
    val libraryFilterFlow: Flow<EnumSet<AppFilter>>
    val librarySortOptionFlow: Flow<SortOption>
    val showSteamInLibraryFlow: Flow<Boolean>
    val showCustomGamesInLibraryFlow: Flow<Boolean>
    val showGOGInLibraryFlow: Flow<Boolean>
    val showEpicInLibraryFlow: Flow<Boolean>
    val showAmazonInLibraryFlow: Flow<Boolean>
}
```

#### `app/src/main/java/app/gamenative/preferences/DefaultLibraryPreferences.kt`
```kotlin
package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.di.PluviaDataStore
import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.enums.SortOption
import java.util.EnumSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLibraryPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : LibraryPreferences {

    private companion object {
        val LIBRARY_LAYOUT = intPreferencesKey("library_layout")
        val LIBRARY_FILTER = intPreferencesKey("library_filter")
        val LIBRARY_SORT_KEY = stringPreferencesKey("library_sort_key")
        val LIBRARY_SORT_LEGACY = intPreferencesKey("library_sort")
        val ITEMS_PER_PAGE = intPreferencesKey("items_per_page")
        val SHOW_STEAM_IN_LIBRARY = booleanPreferencesKey("show_steam_in_library")
        val SHOW_CUSTOM_GAMES_IN_LIBRARY = booleanPreferencesKey("show_custom_games_in_library")
        val SHOW_GOG_IN_LIBRARY = booleanPreferencesKey("show_gog_in_library")
        val SHOW_EPIC_IN_LIBRARY = booleanPreferencesKey("show_epic_in_library")
        val SHOW_AMAZON_IN_LIBRARY = booleanPreferencesKey("show_amazon_in_library")
        val CUSTOM_GAMES_COUNT = intPreferencesKey("custom_games_count")
        val STEAM_GAMES_COUNT = intPreferencesKey("steam_games_count")
        val GOG_GAMES_COUNT = intPreferencesKey("gog_games_count")
        val EPIC_GAMES_COUNT = intPreferencesKey("epic_games_count")
        val GOG_INSTALLED_GAMES_COUNT = intPreferencesKey("gog_installed_games_count")
        val EPIC_INSTALLED_GAMES_COUNT = intPreferencesKey("epic_installed_games_count")
        val AMAZON_INSTALLED_GAMES_COUNT = intPreferencesKey("amazon_installed_games_count")
        val LIBRARY_STEAM_COLLECTIONS_CACHE = stringPreferencesKey("library_steam_collections_cache")
        val LIBRARY_STEAM_COLLECTIONS_SKIPPED_DYNAMIC = booleanPreferencesKey("library_steam_collections_skipped_dynamic")
        val LIBRARY_STEAM_COLLECTIONS = stringPreferencesKey("library_steam_collections")
        const val COLLECTION_ID_SEPARATOR = " "
        val RECOMMENDATION_CACHE_JSON = stringPreferencesKey("recommendation_cache_json")
        val RECOMMENDATION_CACHE_TIMESTAMP = longPreferencesKey("recommendation_cache_timestamp")
        val SHOW_RECOMMENDATIONS = booleanPreferencesKey("show_recommendations")
        val REC_DISCLOSURE_SHOWN = booleanPreferencesKey("rec_disclosure_shown")
        val REC_TEASER_DISMISSED_DAY = longPreferencesKey("rec_teaser_dismissed_day")
        val SHOW_ADD_CUSTOM_GAME_DIALOG = booleanPreferencesKey("show_add_custom_game_dialog")
        val IMPORT_CUSTOM_GAME_AS_STEAM_GAME = booleanPreferencesKey("import_custom_game_as_steam_game")
        val CUSTOM_GAME_PATHS = stringPreferencesKey("custom_game_paths")
        val CUSTOM_GAME_MANUAL_FOLDERS = stringPreferencesKey("custom_game_manual_folders")
        val FAVORITE_APP_IDS = stringPreferencesKey("favorite_app_ids")
        val GOG_AMAZON_PATH_MIGRATED = booleanPreferencesKey("gog_amazon_path_migrated")
    }

    private val favoritePersistenceLock = Any()
    private var favoritePersistenceVersion = 0L

    @Volatile
    private var recDisclosureShownCache: Boolean? = null

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var libraryLayout: PaneType
        get() {
            val value = getPref(LIBRARY_LAYOUT, PaneType.UNDECIDED.ordinal)
            return PaneType.entries.getOrNull(value) ?: PaneType.UNDECIDED
        }
        set(value) = setPref(LIBRARY_LAYOUT, value.ordinal)

    override var libraryFilter: EnumSet<AppFilter>
        get() {
            val value = getPref(LIBRARY_FILTER, AppFilter.toFlags(EnumSet.of(AppFilter.GAME, AppFilter.SHARED)))
            return AppFilter.fromFlags(value)
        }
        set(value) = setPref(LIBRARY_FILTER, AppFilter.toFlags(value))

    override var librarySortOption: SortOption
        get() {
            val keyValue = getPref(LIBRARY_SORT_KEY, "")
            return if (keyValue.isNotEmpty()) {
                SortOption.fromKey(keyValue)
            } else {
                val ordinal = getPref(LIBRARY_SORT_LEGACY, SortOption.INSTALLED_FIRST.ordinal)
                @Suppress("DEPRECATION")
                SortOption.fromOrdinal(ordinal)
            }
        }
        set(value) = setPref(LIBRARY_SORT_KEY, value.key)

    override var itemsPerPage: Int
        get() = getPref(ITEMS_PER_PAGE, 50)
        set(value) = setPref(ITEMS_PER_PAGE, value)

    override var showSteamInLibrary: Boolean
        get() = getPref(SHOW_STEAM_IN_LIBRARY, true)
        set(value) = setPref(SHOW_STEAM_IN_LIBRARY, value)

    override var showCustomGamesInLibrary: Boolean
        get() = getPref(SHOW_CUSTOM_GAMES_IN_LIBRARY, true)
        set(value) = setPref(SHOW_CUSTOM_GAMES_IN_LIBRARY, value)

    override var showGOGInLibrary: Boolean
        get() = getPref(SHOW_GOG_IN_LIBRARY, true)
        set(value) = setPref(SHOW_GOG_IN_LIBRARY, value)

    override var showEpicInLibrary: Boolean
        get() = getPref(SHOW_EPIC_IN_LIBRARY, true)
        set(value) = setPref(SHOW_EPIC_IN_LIBRARY, value)

    override var showAmazonInLibrary: Boolean
        get() = getPref(SHOW_AMAZON_IN_LIBRARY, true)
        set(value) = setPref(SHOW_AMAZON_IN_LIBRARY, value)

    override var customGamesCount: Int
        get() = getPref(CUSTOM_GAMES_COUNT, 0)
        set(value) = setPref(CUSTOM_GAMES_COUNT, value)

    override var steamGamesCount: Int
        get() = getPref(STEAM_GAMES_COUNT, 0)
        set(value) = setPref(STEAM_GAMES_COUNT, value)

    override var gogGamesCount: Int
        get() = getPref(GOG_GAMES_COUNT, 0)
        set(value) = setPref(GOG_GAMES_COUNT, value)

    override var epicGamesCount: Int
        get() = getPref(EPIC_GAMES_COUNT, 0)
        set(value) = setPref(EPIC_GAMES_COUNT, value)

    override var gogInstalledGamesCount: Int
        get() = getPref(GOG_INSTALLED_GAMES_COUNT, 0)
        set(value) = setPref(GOG_INSTALLED_GAMES_COUNT, value)

    override var epicInstalledGamesCount: Int
        get() = getPref(EPIC_INSTALLED_GAMES_COUNT, 0)
        set(value) = setPref(EPIC_INSTALLED_GAMES_COUNT, value)

    override var amazonInstalledGamesCount: Int
        get() = getPref(AMAZON_INSTALLED_GAMES_COUNT, 0)
        set(value) = setPref(AMAZON_INSTALLED_GAMES_COUNT, value)

    override var librarySteamCollectionsCache: String
        get() = getPref(LIBRARY_STEAM_COLLECTIONS_CACHE, "")
        set(value) = setPref(LIBRARY_STEAM_COLLECTIONS_CACHE, value)

    override var librarySteamCollectionsSkippedDynamic: Boolean
        get() = getPref(LIBRARY_STEAM_COLLECTIONS_SKIPPED_DYNAMIC, false)
        set(value) = setPref(LIBRARY_STEAM_COLLECTIONS_SKIPPED_DYNAMIC, value)

    override var librarySteamCollections: Set<String>
        get() {
            val raw = getPref(LIBRARY_STEAM_COLLECTIONS, "")
            if (raw.isEmpty()) return emptySet()
            return raw.split(COLLECTION_ID_SEPARATOR).filter { it.isNotEmpty() }.toSet()
        }
        set(value) = setPref(LIBRARY_STEAM_COLLECTIONS, value.joinToString(COLLECTION_ID_SEPARATOR))

    override var recommendationCacheJson: String
        get() = getPref(RECOMMENDATION_CACHE_JSON, "")
        set(value) = setPref(RECOMMENDATION_CACHE_JSON, value)

    override var recommendationCacheTimestamp: Long
        get() = getPref(RECOMMENDATION_CACHE_TIMESTAMP, 0L)
        set(value) = setPref(RECOMMENDATION_CACHE_TIMESTAMP, value)

    override var showRecommendations: Boolean
        get() = getPref(SHOW_RECOMMENDATIONS, true)
        set(value) = setPref(SHOW_RECOMMENDATIONS, value)

    override var recDisclosureShown: Boolean
        get() = recDisclosureShownCache
            ?: getPref(REC_DISCLOSURE_SHOWN, false).also { recDisclosureShownCache = it }
        set(value) {
            recDisclosureShownCache = value
            setPref(REC_DISCLOSURE_SHOWN, value)
        }

    override var recTeaserDismissedDay: Long
        get() = getPref(REC_TEASER_DISMISSED_DAY, 0L)
        set(value) = setPref(REC_TEASER_DISMISSED_DAY, value)

    override var showAddCustomGameDialog: Boolean
        get() = getPref(SHOW_ADD_CUSTOM_GAME_DIALOG, true)
        set(value) = setPref(SHOW_ADD_CUSTOM_GAME_DIALOG, value)

    override var importCustomGameAsSteamGame: Boolean
        get() = getPref(IMPORT_CUSTOM_GAME_AS_STEAM_GAME, false)
        set(value) = setPref(IMPORT_CUSTOM_GAME_AS_STEAM_GAME, value)

    override var customGamePaths: Set<String>
        get() {
            val value = getPref(CUSTOM_GAME_PATHS, "[]")
            return try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) = setPref(CUSTOM_GAME_PATHS, Json.encodeToString(value))

    override var customGameManualFolders: Set<String>
        get() {
            val value = getPref(CUSTOM_GAME_MANUAL_FOLDERS, "[]")
            return try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) = setPref(CUSTOM_GAME_MANUAL_FOLDERS, Json.encodeToString(value))

    override var favoriteAppIds: Set<String>
        get() {
            val value = getPref(FAVORITE_APP_IDS, "[]")
            return try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                Timber.w(e, "Failed to decode favorite app ids; falling back to empty set")
                emptySet()
            }
        }
        set(value) {
            val version = synchronized(favoritePersistenceLock) {
                favoritePersistenceVersion += 1
                favoritePersistenceVersion
            }
            scope.launch {
                val serialized = Json.encodeToString(value)
                dataStore.edit { pref ->
                    val isLatest = synchronized(favoritePersistenceLock) {
                        version == favoritePersistenceVersion
                    }
                    if (isLatest) {
                        pref[FAVORITE_APP_IDS] = serialized
                    }
                }
            }
        }

    override var gogAmazonPathMigrated: Boolean
        get() = getPref(GOG_AMAZON_PATH_MIGRATED, false)
        set(value) = setPref(GOG_AMAZON_PATH_MIGRATED, value)

    override val showRecommendationsFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_RECOMMENDATIONS] ?: true }
        .distinctUntilChanged()

    override val favoriteAppIdsFlow: Flow<Set<String>> = dataStore.data
        .map { pref ->
            val value = pref[FAVORITE_APP_IDS] ?: "[]"
            try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                emptySet()
            }
        }
        .distinctUntilChanged()

    override val libraryLayoutFlow: Flow<PaneType> = dataStore.data
        .map { pref ->
            val value = pref[LIBRARY_LAYOUT] ?: PaneType.UNDECIDED.ordinal
            PaneType.entries.getOrNull(value) ?: PaneType.UNDECIDED
        }
        .distinctUntilChanged()

    override val libraryFilterFlow: Flow<EnumSet<AppFilter>> = dataStore.data
        .map { pref ->
            val value = pref[LIBRARY_FILTER] ?: AppFilter.toFlags(EnumSet.of(AppFilter.GAME, AppFilter.SHARED))
            AppFilter.fromFlags(value)
        }
        .distinctUntilChanged()

    override val librarySortOptionFlow: Flow<SortOption> = dataStore.data
        .map { pref ->
            val keyValue = pref[LIBRARY_SORT_KEY] ?: ""
            if (keyValue.isNotEmpty()) {
                SortOption.fromKey(keyValue)
            } else {
                val ordinal = pref[LIBRARY_SORT_LEGACY] ?: SortOption.INSTALLED_FIRST.ordinal
                @Suppress("DEPRECATION")
                SortOption.fromOrdinal(ordinal)
            }
        }
        .distinctUntilChanged()

    override val showSteamInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_STEAM_IN_LIBRARY] ?: true }
        .distinctUntilChanged()

    override val showCustomGamesInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_CUSTOM_GAMES_IN_LIBRARY] ?: true }
        .distinctUntilChanged()

    override val showGOGInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_GOG_IN_LIBRARY] ?: true }
        .distinctUntilChanged()

    override val showEpicInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_EPIC_IN_LIBRARY] ?: true }
        .distinctUntilChanged()

    override val showAmazonInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_AMAZON_IN_LIBRARY] ?: true }
        .distinctUntilChanged()
}
```

---

### 3.6 Domain 6: `DownloadPreferences` & `DefaultDownloadPreferences`

#### `app/src/main/java/app/gamenative/preferences/DownloadPreferences.kt`
```kotlin
package app.gamenative.preferences

import app.gamenative.data.GameSource
import kotlinx.coroutines.flow.Flow

interface DownloadPreferences {
    var downloadOnWifiOnly: Boolean
    var downloadSpeed: Int
    var useExternalStorage: Boolean
    var externalStoragePath: String
    var fetchSteamGridDBImages: Boolean
    var frontendSyncDirSteam: String
    var frontendSyncDirEpic: String
    var frontendSyncDirGog: String
    var frontendSyncDirAmazon: String
    var frontendSyncDirCustom: String

    fun getFrontendSyncDir(source: GameSource): String
    fun setFrontendSyncDir(source: GameSource, path: String)

    val downloadOnWifiOnlyFlow: Flow<Boolean>
    val downloadSpeedFlow: Flow<Int>
    val useExternalStorageFlow: Flow<Boolean>
    val externalStoragePathFlow: Flow<String>
}
```

#### `app/src/main/java/app/gamenative/preferences/DefaultDownloadPreferences.kt`
```kotlin
package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.data.GameSource
import app.gamenative.di.PluviaDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDownloadPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : DownloadPreferences {

    private companion object {
        val DOWNLOAD_ON_WIFI_ONLY = booleanPreferencesKey("download_on_wifi_only")
        val DOWNLOAD_SPEED = intPreferencesKey("download_speed")
        val USE_EXTERNAL_STORAGE = booleanPreferencesKey("use_external_storage")
        val EXTERNAL_STORAGE_PATH = stringPreferencesKey("external_storage_path")
        val FETCH_STEAMGRIDDB_IMAGES = booleanPreferencesKey("fetch_steamgriddb_images")
        val FRONTEND_SYNC_DIR_STEAM = stringPreferencesKey("frontend_sync_dir_steam")
        val FRONTEND_SYNC_DIR_EPIC = stringPreferencesKey("frontend_sync_dir_epic")
        val FRONTEND_SYNC_DIR_GOG = stringPreferencesKey("frontend_sync_dir_gog")
        val FRONTEND_SYNC_DIR_AMAZON = stringPreferencesKey("frontend_sync_dir_amazon")
        val FRONTEND_SYNC_DIR_CUSTOM = stringPreferencesKey("frontend_sync_dir_custom")
    }

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var downloadOnWifiOnly: Boolean
        get() = getPref(DOWNLOAD_ON_WIFI_ONLY, true)
        set(value) = setPref(DOWNLOAD_ON_WIFI_ONLY, value)

    override var downloadSpeed: Int
        get() = getPref(DOWNLOAD_SPEED, 24)
        set(value) = setPref(DOWNLOAD_SPEED, value)

    override var useExternalStorage: Boolean
        get() = getPref(USE_EXTERNAL_STORAGE, false)
        set(value) {
            setPref(USE_EXTERNAL_STORAGE, value)
            setPref(EXTERNAL_STORAGE_PATH, "")
        }

    override var externalStoragePath: String
        get() = getPref(EXTERNAL_STORAGE_PATH, "")
        set(value) = setPref(EXTERNAL_STORAGE_PATH, value)

    override var fetchSteamGridDBImages: Boolean
        get() = getPref(FETCH_STEAMGRIDDB_IMAGES, true)
        set(value) = setPref(FETCH_STEAMGRIDDB_IMAGES, value)

    override var frontendSyncDirSteam: String
        get() = getPref(FRONTEND_SYNC_DIR_STEAM, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_STEAM, value)

    override var frontendSyncDirEpic: String
        get() = getPref(FRONTEND_SYNC_DIR_EPIC, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_EPIC, value)

    override var frontendSyncDirGog: String
        get() = getPref(FRONTEND_SYNC_DIR_GOG, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_GOG, value)

    override var frontendSyncDirAmazon: String
        get() = getPref(FRONTEND_SYNC_DIR_AMAZON, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_AMAZON, value)

    override var frontendSyncDirCustom: String
        get() = getPref(FRONTEND_SYNC_DIR_CUSTOM, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_CUSTOM, value)

    override fun getFrontendSyncDir(source: GameSource): String = when (source) {
        GameSource.STEAM -> frontendSyncDirSteam
        GameSource.EPIC -> frontendSyncDirEpic
        GameSource.GOG -> frontendSyncDirGog
        GameSource.AMAZON -> frontendSyncDirAmazon
        GameSource.CUSTOM_GAME -> frontendSyncDirCustom
    }

    override fun setFrontendSyncDir(source: GameSource, path: String) {
        when (source) {
            GameSource.STEAM -> frontendSyncDirSteam = path
            GameSource.EPIC -> frontendSyncDirEpic = path
            GameSource.GOG -> frontendSyncDirGog = path
            GameSource.AMAZON -> frontendSyncDirAmazon = path
            GameSource.CUSTOM_GAME -> frontendSyncDirCustom = path
        }
    }

    override val downloadOnWifiOnlyFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[DOWNLOAD_ON_WIFI_ONLY] ?: true }
        .distinctUntilChanged()

    override val downloadSpeedFlow: Flow<Int> = dataStore.data
        .map { pref -> pref[DOWNLOAD_SPEED] ?: 24 }
        .distinctUntilChanged()

    override val useExternalStorageFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[USE_EXTERNAL_STORAGE] ?: false }
        .distinctUntilChanged()

    override val externalStoragePathFlow: Flow<String> = dataStore.data
        .map { pref -> pref[EXTERNAL_STORAGE_PATH] ?: "" }
        .distinctUntilChanged()
}
```

---

### 3.7 Domain 7: `GeneralPreferences` & `DefaultGeneralPreferences`

#### `app/src/main/java/app/gamenative/preferences/GeneralPreferences.kt`
```kotlin
package app.gamenative.preferences

import app.gamenative.enums.AppTheme
import app.gamenative.ui.enums.HomeDestination
import app.gamenative.ui.enums.Orientation
import com.materialkolor.PaletteStyle
import java.util.EnumSet
import kotlinx.coroutines.flow.Flow

interface GeneralPreferences {
    var appTheme: AppTheme
    var appThemePalette: PaletteStyle
    var startScreen: HomeDestination
    var allowedOrientation: EnumSet<Orientation>
    var appLanguage: String
    var openWebLinksExternally: Boolean
    var hideStatusBarWhenNotInGame: Boolean
    var useAltLauncherIcon: Boolean
    var useAltNotificationIcon: Boolean
    var achievementShowNotification: Boolean
    var achievementPlaySound: Boolean
    var achievementNotificationPosition: String
    var warnBeforeExit: Boolean
    var usageAnalyticsEnabled: Boolean
    var recentlyCrashed: Boolean
    var tipped: Boolean
    var hasAttemptedGameLaunch: Boolean
    var lastLaunchPitchTime: Long
    var lastWarmPitchTime: Long
    var keyAttestationAvailable: Boolean
    var playIntegrityAvailable: Boolean
    var componentManifestJson: String
    var componentManifestFetchedAt: Long
    var gameCompatibilityCache: String
    var hltbCache: String
    var deviceGameStatsCache: String
    var gpuGameStatsCache: String
    var nexusLastPlacementJson: String

    val appThemeFlow: Flow<AppTheme>
    val appThemePaletteFlow: Flow<PaletteStyle>
    val appLanguageFlow: Flow<String>
    val usageAnalyticsEnabledFlow: Flow<Boolean>
    val allowedOrientationFlow: Flow<EnumSet<Orientation>>
}
```

#### `app/src/main/java/app/gamenative/preferences/DefaultGeneralPreferences.kt`
```kotlin
package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.di.PluviaDataStore
import app.gamenative.enums.AppTheme
import app.gamenative.ui.enums.HomeDestination
import app.gamenative.ui.enums.Orientation
import com.materialkolor.PaletteStyle
import java.util.EnumSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultGeneralPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : GeneralPreferences {

    private companion object {
        val APP_THEME = intPreferencesKey("app_theme")
        val APP_THEME_PALETTE = intPreferencesKey("app_theme_palette")
        val START_SCREEN = intPreferencesKey("start screen")
        val ALLOWED_ORIENTATION = intPreferencesKey("allowed_orientation")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val OPEN_WEB_LINKS_EXTERNALLY = booleanPreferencesKey("open_web_links_externally")
        val HIDE_STATUS_BAR_WHEN_NOT_IN_GAME = booleanPreferencesKey("hide_status_bar_when_not_in_game")
        val USE_ALT_LAUNCHER_ICON = booleanPreferencesKey("use_alt_launcher_icon")
        val USE_ALT_NOTIFICATION_ICON = booleanPreferencesKey("use_alt_notification_icon")
        val ACHIEVEMENT_SHOW_NOTIFICATION = booleanPreferencesKey("achievement_show_notification")
        val ACHIEVEMENT_PLAY_SOUND = booleanPreferencesKey("achievement_play_sound")
        val ACHIEVEMENT_NOTIFICATION_POSITION = stringPreferencesKey("achievement_notification_position")
        val WARN_BEFORE_EXIT = booleanPreferencesKey("warn_before_exit")
        val USAGE_ANALYTICS_ENABLED = booleanPreferencesKey("usage_analytics_enabled")
        val RECENTLY_CRASHED = booleanPreferencesKey("recently_crashed")
        val TIPPED = booleanPreferencesKey("tipped")
        val HAS_ATTEMPTED_GAME_LAUNCH = booleanPreferencesKey("has_attempted_game_launch")
        val LAST_LAUNCH_PITCH_TIME = longPreferencesKey("last_launch_pitch_time")
        val LAST_WARM_PITCH_TIME = longPreferencesKey("last_warm_pitch_time")
        val KEY_ATTESTATION_AVAILABLE = booleanPreferencesKey("key_attestation_available")
        val PLAY_INTEGRITY_AVAILABLE = booleanPreferencesKey("play_integrity_available")
        val COMPONENT_MANIFEST_JSON = stringPreferencesKey("component_manifest_json")
        val COMPONENT_MANIFEST_FETCHED_AT = longPreferencesKey("component_manifest_fetched_at")
        val GAME_COMPATIBILITY_CACHE = stringPreferencesKey("game_compatibility_cache")
        val HLTB_CACHE = stringPreferencesKey("hltb_cache")
        val DEVICE_GAME_STATS_CACHE = stringPreferencesKey("device_game_stats_cache")
        val GPU_GAME_STATS_CACHE = stringPreferencesKey("gpu_game_stats_cache")
        val NEXUS_LAST_PLACEMENT_JSON = stringPreferencesKey("nexus_last_placement_json")
    }

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    private fun <T> removePref(key: Preferences.Key<T>) {
        scope.launch {
            dataStore.edit { pref -> pref.remove(key) }
        }
    }

    override var appTheme: AppTheme
        get() {
            val value = getPref(APP_THEME, AppTheme.AUTO.ordinal)
            return AppTheme.entries.getOrNull(value) ?: AppTheme.AUTO
        }
        set(value) = setPref(APP_THEME, value.ordinal)

    override var appThemePalette: PaletteStyle
        get() {
            val value = getPref(APP_THEME_PALETTE, PaletteStyle.TonalSpot.ordinal)
            return PaletteStyle.entries.getOrNull(value) ?: PaletteStyle.TonalSpot
        }
        set(value) = setPref(APP_THEME_PALETTE, value.ordinal)

    override var startScreen: HomeDestination
        get() {
            val value = getPref(START_SCREEN, HomeDestination.Library.ordinal)
            return HomeDestination.entries.getOrNull(value) ?: HomeDestination.Library
        }
        set(value) = setPref(START_SCREEN, value.ordinal)

    override var allowedOrientation: EnumSet<Orientation>
        get() {
            val defaultValue = Orientation.toInt(
                EnumSet.of(Orientation.LANDSCAPE, Orientation.REVERSE_LANDSCAPE),
            )
            val value = getPref(ALLOWED_ORIENTATION, defaultValue)
            return Orientation.fromInt(value)
        }
        set(value) = setPref(ALLOWED_ORIENTATION, Orientation.toInt(value))

    override var appLanguage: String
        get() = getPref(APP_LANGUAGE, "")
        set(value) = setPref(APP_LANGUAGE, value)

    override var openWebLinksExternally: Boolean
        get() = getPref(OPEN_WEB_LINKS_EXTERNALLY, true)
        set(value) = setPref(OPEN_WEB_LINKS_EXTERNALLY, value)

    override var hideStatusBarWhenNotInGame: Boolean
        get() = getPref(HIDE_STATUS_BAR_WHEN_NOT_IN_GAME, true)
        set(value) = setPref(HIDE_STATUS_BAR_WHEN_NOT_IN_GAME, value)

    override var useAltLauncherIcon: Boolean
        get() = getPref(USE_ALT_LAUNCHER_ICON, false)
        set(value) = setPref(USE_ALT_LAUNCHER_ICON, value)

    override var useAltNotificationIcon: Boolean
        get() = getPref(USE_ALT_NOTIFICATION_ICON, false)
        set(value) = setPref(USE_ALT_NOTIFICATION_ICON, value)

    override var achievementShowNotification: Boolean
        get() = getPref(ACHIEVEMENT_SHOW_NOTIFICATION, true)
        set(value) = setPref(ACHIEVEMENT_SHOW_NOTIFICATION, value)

    override var achievementPlaySound: Boolean
        get() = getPref(ACHIEVEMENT_PLAY_SOUND, true)
        set(value) = setPref(ACHIEVEMENT_PLAY_SOUND, value)

    override var achievementNotificationPosition: String
        get() = getPref(ACHIEVEMENT_NOTIFICATION_POSITION, "bottom_right")
        set(value) = setPref(ACHIEVEMENT_NOTIFICATION_POSITION, value)

    override var warnBeforeExit: Boolean
        get() = getPref(WARN_BEFORE_EXIT, false)
        set(value) = setPref(WARN_BEFORE_EXIT, value)

    override var usageAnalyticsEnabled: Boolean
        get() = getPref(USAGE_ANALYTICS_ENABLED, true)
        set(value) = setPref(USAGE_ANALYTICS_ENABLED, value)

    override var recentlyCrashed: Boolean
        get() = getPref(RECENTLY_CRASHED, false)
        set(value) = setPref(RECENTLY_CRASHED, value)

    override var tipped: Boolean
        get() = getPref(TIPPED, false)
        set(value) = setPref(TIPPED, value)

    override var hasAttemptedGameLaunch: Boolean
        get() = getPref(HAS_ATTEMPTED_GAME_LAUNCH, false)
        set(value) = setPref(HAS_ATTEMPTED_GAME_LAUNCH, value)

    override var lastLaunchPitchTime: Long
        get() = getPref(LAST_LAUNCH_PITCH_TIME, 0L)
        set(value) = setPref(LAST_LAUNCH_PITCH_TIME, value)

    override var lastWarmPitchTime: Long
        get() = getPref(LAST_WARM_PITCH_TIME, 0L)
        set(value) = setPref(LAST_WARM_PITCH_TIME, value)

    override var keyAttestationAvailable: Boolean
        get() = getPref(KEY_ATTESTATION_AVAILABLE, false)
        set(value) = setPref(KEY_ATTESTATION_AVAILABLE, value)

    override var playIntegrityAvailable: Boolean
        get() = getPref(PLAY_INTEGRITY_AVAILABLE, false)
        set(value) = setPref(PLAY_INTEGRITY_AVAILABLE, value)

    override var componentManifestJson: String
        get() = getPref(COMPONENT_MANIFEST_JSON, "")
        set(value) = setPref(COMPONENT_MANIFEST_JSON, value)

    override var componentManifestFetchedAt: Long
        get() = getPref(COMPONENT_MANIFEST_FETCHED_AT, 0L)
        set(value) = setPref(COMPONENT_MANIFEST_FETCHED_AT, value)

    override var gameCompatibilityCache: String
        get() = getPref(GAME_COMPATIBILITY_CACHE, "{}")
        set(value) = setPref(GAME_COMPATIBILITY_CACHE, value)

    override var hltbCache: String
        get() = getPref(HLTB_CACHE, "{}")
        set(value) = setPref(HLTB_CACHE, value)

    override var deviceGameStatsCache: String
        get() = getPref(DEVICE_GAME_STATS_CACHE, "{}")
        set(value) = setPref(DEVICE_GAME_STATS_CACHE, value)

    override var gpuGameStatsCache: String
        get() = getPref(GPU_GAME_STATS_CACHE, "{}")
        set(value) = setPref(GPU_GAME_STATS_CACHE, value)

    override var nexusLastPlacementJson: String
        get() = getPref(NEXUS_LAST_PLACEMENT_JSON, "{}")
        set(value) {
            if (value.isBlank() || value == "{}") {
                removePref(NEXUS_LAST_PLACEMENT_JSON)
            } else {
                setPref(NEXUS_LAST_PLACEMENT_JSON, value)
            }
        }

    override val appThemeFlow: Flow<AppTheme> = dataStore.data
        .map { pref ->
            val value = pref[APP_THEME] ?: AppTheme.AUTO.ordinal
            AppTheme.entries.getOrNull(value) ?: AppTheme.AUTO
        }
        .distinctUntilChanged()

    override val appThemePaletteFlow: Flow<PaletteStyle> = dataStore.data
        .map { pref ->
            val value = pref[APP_THEME_PALETTE] ?: PaletteStyle.TonalSpot.ordinal
            PaletteStyle.entries.getOrNull(value) ?: PaletteStyle.TonalSpot
        }
        .distinctUntilChanged()

    override val appLanguageFlow: Flow<String> = dataStore.data
        .map { pref -> pref[APP_LANGUAGE] ?: "" }
        .distinctUntilChanged()

    override val usageAnalyticsEnabledFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[USAGE_ANALYTICS_ENABLED] ?: true }
        .distinctUntilChanged()

    override val allowedOrientationFlow: Flow<EnumSet<Orientation>> = dataStore.data
        .map { pref ->
            val defaultValue = Orientation.toInt(
                EnumSet.of(Orientation.LANDSCAPE, Orientation.REVERSE_LANDSCAPE),
            )
            val value = pref[ALLOWED_ORIENTATION] ?: defaultValue
            Orientation.fromInt(value)
        }
        .distinctUntilChanged()
}
```

---

### 3.8 Dagger Hilt Modules & EntryPoint Blueprint

#### `app/src/main/java/app/gamenative/di/PreferencesModule.kt`
```kotlin
package app.gamenative.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import app.gamenative.preferences.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

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
    abstract fun bindAuthPreferences(
        impl: DefaultAuthPreferences,
    ): AuthPreferences

    @Binds
    @Singleton
    abstract fun bindContainerPreferences(
        impl: DefaultContainerPreferences,
    ): ContainerPreferences

    @Binds
    @Singleton
    abstract fun bindInputPreferences(
        impl: DefaultInputPreferences,
    ): InputPreferences

    @Binds
    @Singleton
    abstract fun bindHudPreferences(
        impl: DefaultHudPreferences,
    ): HudPreferences

    @Binds
    @Singleton
    abstract fun bindLibraryPreferences(
        impl: DefaultLibraryPreferences,
    ): LibraryPreferences

    @Binds
    @Singleton
    abstract fun bindDownloadPreferences(
        impl: DefaultDownloadPreferences,
    ): DownloadPreferences

    @Binds
    @Singleton
    abstract fun bindGeneralPreferences(
        impl: DefaultGeneralPreferences,
    ): GeneralPreferences
}
```

#### `app/src/main/java/app/gamenative/preferences/PreferencesEntryPoint.kt`
```kotlin
package app.gamenative.preferences

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint for accessing domain preference repositories in non-Hilt classes,
 * Java classes (e.g. WineUtils.java, BionicProgramLauncherComponent.java),
 * and early lifecycle hooks (e.g. attachBaseContext).
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
}
```

---

## 4. Caveats & Critical Edge Cases

1. **Exact Key Preservations**:
   - `"start screen"` contains a literal space character.
   - `"dxwrapperConfig"`, `"videoPciDeviceID"`, `"offScreenRenderingMode"`, `"strictShaderMath"`, `"useDRI3"`, `"videoMemorySize"`, `"mouseWarpOverride"` use historical camelCase.
   - These must NOT be modified or converted to snake_case, as doing so would orphan user configuration stored in existing DataStore files.
2. **Nullable Types (`clientId`)**:
   - `clientId` in `AuthPreferences` is `Long?`. When set to `null`, `pref.remove(CLIENT_ID)` must be executed instead of attempting to put `null` (which DataStore does not support).
3. **Volatile Cache for Consent (`recDisclosureShown`)**:
   - Consumers in `LibraryViewModel` / `LibraryScreen` check `recDisclosureShown` synchronously immediately after user grants consent. The `@Volatile var recDisclosureShownCache` ensures zero latency and immediate consistency.
4. **Race-Condition Protected Writes (`favoriteAppIds`)**:
   - Rapidly favoriting multiple games can trigger concurrent JSON serialization and DataStore edit jobs. The version lock `favoritePersistenceVersion` guarantees the latest user selection always wins.
5. **Winlator Preferences Boundary**:
   - `com.winlator.PrefManager` (`"WinlatorPreferences"`) is separate and must remain untouched.

---

## 5. Conclusion
- The blueprint defines all 7 domain interfaces, their default implementations, the Hilt provider/binding modules, and the global entrypoint.
- Every single one of the 95 configuration properties from `PrefManager.kt` has been mapped 1:1 to its exact DataStore key, default value, and type conversion.
- The dual access model (synchronous properties + reactive Flows + suspend mutators) enables the team to migrate all 100+ consuming files in downstream milestones (M2 through M5) without compile breakages.

---

## 6. Verification Method

To verify the blueprint against the codebase:
1. **Key Correspondence Inspection**: Compare Section 3 of this document against `app/src/main/java/app/gamenative/PrefManager.kt` lines 140–1508.
2. **Compilation Command**:
   ```bash
   ./gradlew compileModernDebugKotlin
   ```
3. **Unit Test Command**:
   ```bash
   ./gradlew :app:testModernDebugUnitTest
   ```
4. **Invalidation Conditions**: Any missing property, mismatched default value, or altered key name string invalidates the zero-data-loss guarantee.
