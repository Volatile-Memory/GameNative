package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.gamenative.Constants
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
    private val screenSizeResolver: app.gamenative.utils.ScreenSizeResolver,
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
        get() = getPref(SCREEN_SIZE, screenSizeResolver.getDefaultScreenSize())
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
