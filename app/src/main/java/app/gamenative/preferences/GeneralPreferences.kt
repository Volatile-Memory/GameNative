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
