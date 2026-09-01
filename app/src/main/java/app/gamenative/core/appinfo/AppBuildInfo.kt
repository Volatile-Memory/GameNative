package app.gamenative.core.appinfo

/**
 * Pure abstraction for build metadata and flavor configuration.
 */
interface AppBuildInfo {
    val applicationId: String
    val versionCode: Int
    val versionName: String
    val isDebug: Boolean
    val flavor: String
    val isGold: Boolean
    val isXrBuild: Boolean
    val isModernAndroid: Boolean
    val preloadBionicSo: String
    val posthogApiKey: String
    val posthogHost: String
}
