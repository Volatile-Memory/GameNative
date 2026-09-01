package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
