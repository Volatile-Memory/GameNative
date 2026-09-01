package app.gamenative.core.appinfo

import app.gamenative.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAppBuildInfo @Inject constructor() : AppBuildInfo {
    override val applicationId: String = BuildConfig.APPLICATION_ID
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val versionName: String = BuildConfig.VERSION_NAME
    override val isDebug: Boolean = BuildConfig.DEBUG
    override val flavor: String = BuildConfig.FLAVOR
    override val isGold: Boolean = BuildConfig.GOLD
    override val isXrBuild: Boolean = BuildConfig.XR_BUILD
    override val isModernAndroid: Boolean = BuildConfig.MODERN_ANDROID
    override val preloadBionicSo: String = BuildConfig.PRELOAD_BIONIC_SO
    override val posthogApiKey: String = BuildConfig.POSTHOG_API_KEY
    override val posthogHost: String = BuildConfig.POSTHOG_HOST
}
