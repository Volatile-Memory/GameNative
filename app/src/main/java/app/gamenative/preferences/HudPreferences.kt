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
