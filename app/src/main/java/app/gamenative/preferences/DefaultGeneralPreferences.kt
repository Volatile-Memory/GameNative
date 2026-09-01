package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
