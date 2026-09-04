package app.gamenative.ui.screen.settings

import android.content.res.Configuration
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import app.gamenative.BuildConfig
import app.gamenative.R
import app.gamenative.preferences.PreferencesEntryPoint
import app.gamenative.ui.enums.HomeDestination
import app.gamenative.enums.AppTheme
import app.gamenative.ui.component.dialog.SingleChoiceDialog
import app.gamenative.ui.theme.settingsTileColorsAlt
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsSwitch
import com.materialkolor.PaletteStyle
import kotlinx.serialization.json.Json
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.gamenative.ui.theme.PluviaTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import app.gamenative.ui.component.settings.SettingsListDropdown
import app.gamenative.ui.component.ACHIEVEMENT_NOTIFICATION_POSITION
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import app.gamenative.utils.IconSwitcher
import com.alorma.compose.settings.ui.SettingsMenuLink
import androidx.compose.material3.Slider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import kotlin.math.roundToInt
import com.winlator.core.AppUtils
import app.gamenative.ui.component.dialog.MessageDialog
import app.gamenative.ui.component.dialog.LoadingDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import app.gamenative.utils.LocaleHelper
import app.gamenative.service.epic.EpicAuthManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import app.gamenative.PluviaApp
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.screen.auth.EpicOAuthActivity
import app.gamenative.ui.screen.auth.GOGOAuthActivity
import app.gamenative.ui.screen.auth.AmazonOAuthActivity
import app.gamenative.service.amazon.AmazonAuthManager
import app.gamenative.utils.PlatformOAuthHandlers
import app.gamenative.utils.StorageUtils
import app.gamenative.data.GameSource
import app.gamenative.di.appUtilsEntryPoint
import app.gamenative.ui.util.PlatformAuthUiHelpers
import app.gamenative.ui.util.SnackbarManager

/** Icon button that triggers FrontendSyncManager.resyncAll and shows a spinner while syncing. */
@Composable
private fun FrontendSyncResyncButton() {
    val context = LocalContext.current
    val frontendSyncManager = remember(context) { context.appUtilsEntryPoint().frontendSyncManager() }
    val isSyncing by frontendSyncManager.isSyncing.collectAsState()
    val resyncLabel = stringResource(R.string.frontend_sync_resync_all)
    IconButton(
        onClick = { frontendSyncManager.resyncAll() },
        modifier = Modifier.semantics { contentDescription = resyncLabel },
    ) {
        if (isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = stringResource(R.string.frontend_sync_resync_all),
            )
        }
    }
}

/** Settings group covering interface preferences: theme, downloads, frontend sync, language, and icons. */
@Composable
fun SettingsGroupInterface(
    appTheme: AppTheme,
    paletteStyle: PaletteStyle,
    onAppTheme: (AppTheme) -> Unit,
    onPaletteStyle: (PaletteStyle) -> Unit,
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val generalPrefs = remember(context, isPreview) {
        if (isPreview) null else PreferencesEntryPoint.get(context).generalPreferences()
    }
    val inputPrefs = remember(context, isPreview) {
        if (isPreview) null else PreferencesEntryPoint.get(context).inputPreferences()
    }
    val authPrefs = remember(context, isPreview) {
        if (isPreview) null else PreferencesEntryPoint.get(context).authPreferences()
    }
    val libraryPrefs = remember(context, isPreview) {
        if (isPreview) null else PreferencesEntryPoint.get(context).libraryPreferences()
    }
    val downloadPrefs = remember(context, isPreview) {
        if (isPreview) null else PreferencesEntryPoint.get(context).downloadPreferences()
    }

    var openWebLinks by rememberSaveable {
        mutableStateOf(if (isPreview) false else generalPrefs?.openWebLinksExternally ?: false)
    }

    var openAppThemeDialog by rememberSaveable { mutableStateOf(false) }
    var openAppPaletteDialog by rememberSaveable { mutableStateOf(false) }

    var openStartScreenDialog by rememberSaveable { mutableStateOf(false) }
    var startScreenOption by rememberSaveable(openStartScreenDialog) {
        mutableStateOf(if (isPreview) HomeDestination.Library else generalPrefs?.startScreen ?: HomeDestination.Library)
    }

    // Status bar hide/show confirmation dialog
    var showStatusBarRestartDialog by rememberSaveable { mutableStateOf(false) }
    var pendingStatusBarValue by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showStatusBarLoadingDialog by rememberSaveable { mutableStateOf(false) }
    var hideStatusBar by rememberSaveable {
        mutableStateOf(if (isPreview) false else generalPrefs?.hideStatusBarWhenNotInGame ?: false)
    }
    var swapFaceButtons by rememberSaveable {
        mutableStateOf(if (isPreview) false else inputPrefs?.swapFaceButtons ?: false)
    }

    // Controller/gamepad hints visibility
    var showGamepadHints by rememberSaveable {
        mutableStateOf(if (isPreview) true else inputPrefs?.showGamepadHints ?: true)
    }

    // Achievements
    var showAchievementNotifications by rememberSaveable {
        mutableStateOf(if (isPreview) true else generalPrefs?.achievementShowNotification ?: true)
    }
    var playAchievementSound by rememberSaveable {
        mutableStateOf(if (isPreview) true else generalPrefs?.achievementPlaySound ?: true)
    }

    // Language selection dialog
    var openLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showLanguageRestartDialog by rememberSaveable { mutableStateOf(false) }
    var pendingLanguageCode by rememberSaveable { mutableStateOf<String?>(null) }
    var showLanguageLoadingDialog by rememberSaveable { mutableStateOf(false) }
    val languageCodes = remember { LocaleHelper.getSupportedLanguageCodes() }
    val languageNames = remember { LocaleHelper.getSupportedLanguageNames() }
    var selectedLanguageIndex by rememberSaveable {
        mutableStateOf(
            languageCodes.indexOf(if (isPreview) "en" else generalPrefs?.appLanguage ?: "en").takeIf { it >= 0 } ?: 0,
        )
    }

    // Load Steam regions from assets
    val steamRegionsMap: Map<Int, String> = remember {
        val jsonString = context.assets.open("steam_regions.json").bufferedReader().use { it.readText() }
        Json.decodeFromString<Map<String, String>>(jsonString).mapKeys { it.key.toInt() }
    }
    val steamRegionsList = remember {
        // Always put 'Automatic' (id 0) first, then sort the rest alphabetically
        val entries = steamRegionsMap.toList()
        val (autoEntries, otherEntries) = entries.partition { it.first == 0 }
        autoEntries + otherEntries.sortedBy { it.second }
    }
    var openRegionDialog by rememberSaveable { mutableStateOf(false) }
    var selectedRegionIndex by rememberSaveable { mutableStateOf(
        steamRegionsList.indexOfFirst { it.first == (if (isPreview) 0 else authPrefs?.cellId ?: 0) }.takeIf { it >= 0 } ?: 0
    ) }

    // GOG login state
    var gogLoginLoading by rememberSaveable { mutableStateOf(false) }

    // GOG library sync state
    var gogLibrarySyncing by rememberSaveable { mutableStateOf(false) }
    var gogLibrarySyncError by rememberSaveable { mutableStateOf<String?>(null) }
    var gogLibrarySyncSuccess by rememberSaveable { mutableStateOf(false) }
    var gogLibraryGameCount by rememberSaveable { mutableStateOf(0) }

    // Epic login state
    var epicLoginLoading by rememberSaveable { mutableStateOf(false) }

    // Amazon login state
    var amazonLoginLoading by rememberSaveable { mutableStateOf(false) }

    // Epic logout confirmation dialog state
    var showEpicLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var epicLogoutLoading by rememberSaveable { mutableStateOf(false) }

    var showFrontendSyncDialog by rememberSaveable { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    // Use Activity lifecycle scope for the OAuth result callback so it stays valid after
    // returning from GOGOAuthActivity (composition may have been left → rememberCoroutineScope cancelled).
    val lifecycleScope = LocalLifecycleOwner.current.lifecycleScope

    // OAuth launchers are now provided by a parent composable so they can be
    // reused from both Settings and the System Menu. Settings continues to
    // derive its own loading state and toasts via callbacks in that parent.

    // Listen for GOG OAuth callback (e.g. from event)
    DisposableEffect(Unit) {
        Timber.d("[SettingsGOG]: Setting up GOG auth code event listener")
        val onGOGAuthCodeReceived: (AndroidEvent.GOGAuthCodeReceived) -> Unit = { event ->
            Timber.i("[SettingsGOG]: ✓ Received GOG auth code event")

            coroutineScope.launch {
                PlatformOAuthHandlers.handleGogAuthentication(
                    context = context,
                    authCode = event.authCode,
                    coroutineScope = coroutineScope,
                    onLoadingChange = { gogLoginLoading = it },
                    onError = { msg ->
                        if (msg != null) {
                            SnackbarManager.show(msg)
                        }
                    },
                    onSuccess = { count ->
                        gogLibraryGameCount = count
                        SnackbarManager.show(context.getString(R.string.gog_login_success_title))
                    },
                    onDialogClose = { }
                )
            }
        }

        PluviaApp.events.on<AndroidEvent.GOGAuthCodeReceived, Unit>(onGOGAuthCodeReceived)
        Timber.d("[SettingsGOG]: GOG auth code event listener registered")

        onDispose {
            PluviaApp.events.off<AndroidEvent.GOGAuthCodeReceived, Unit>(onGOGAuthCodeReceived)
            Timber.d("[SettingsGOG]: GOG auth code event listener unregistered")
        }
    }

    SettingsGroup(modifier = Modifier.background(Color.Transparent)) {
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_achievement_show_notification)) },
            state = showAchievementNotifications,
            onCheckedChange = {
                showAchievementNotifications = it
                if (!isPreview) {
                    generalPrefs?.achievementShowNotification = it
                }
            },
        )
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_achievement_play_sound)) },
            state = playAchievementSound,
            onCheckedChange = {
                playAchievementSound = it
                if (!isPreview) {
                    generalPrefs?.achievementPlaySound = it
                }
            },
        )
        // Achievement notification position
        val achPositionKeys = remember { ACHIEVEMENT_NOTIFICATION_POSITION.keys.toList() }
        val achPositionLabelResIds = remember { ACHIEVEMENT_NOTIFICATION_POSITION.values.toList() }
        val achPositionLabels = achPositionLabelResIds.map { stringResource(it) }
        var achPositionIndex by rememberSaveable {
            mutableStateOf(
                achPositionKeys.indexOf(if (isPreview) "bottom_right" else generalPrefs?.achievementNotificationPosition ?: "bottom_right").takeIf { it >= 0 } ?: achPositionKeys.indexOf("bottom_right")
            )
        }
        SettingsListDropdown(
            title = { Text(text = stringResource(R.string.settings_achievement_notification_position)) },
            items = achPositionLabels,
            value = achPositionIndex,
            onItemSelected = { idx ->
                achPositionIndex = idx
                if (!isPreview) {
                    generalPrefs?.achievementNotificationPosition = achPositionKeys[idx]
                }
            },
            colors = settingsTileColorsAlt(),
        )

        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_interface_external_links_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_interface_external_links_subtitle)) },
            state = openWebLinks,
            onCheckedChange = {
                openWebLinks = it
                if (!isPreview) {
                    generalPrefs?.openWebLinksExternally = it
                }
            },
        )

        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_interface_hide_statusbar_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_interface_hide_statusbar_subtitle)) },
            state = hideStatusBar,
            onCheckedChange = { newValue ->
                // Update UI immediately for responsive feel
                hideStatusBar = newValue
                // Store the pending value and show confirmation dialog
                pendingStatusBarValue = newValue
                showStatusBarRestartDialog = true
            },
        )

        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_interface_swap_face_buttons_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_interface_swap_face_buttons_subtitle)) },
            state = swapFaceButtons,
            onCheckedChange = {
                swapFaceButtons = it
                if (!isPreview) {
                    inputPrefs?.swapFaceButtons = it
                }
            },
        )

        var warnBeforeExit by rememberSaveable {
            mutableStateOf(if (isPreview) true else generalPrefs?.warnBeforeExit ?: true)
        }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_interface_warn_before_exit_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_interface_warn_before_exit_subtitle)) },
            state = warnBeforeExit,
            onCheckedChange = {
                warnBeforeExit = it
                if (!isPreview) {
                    generalPrefs?.warnBeforeExit = it
                }
            },
        )

        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_interface_show_gamepad_hints_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_interface_show_gamepad_hints_subtitle)) },
            state = showGamepadHints,
            onCheckedChange = { newValue ->
                showGamepadHints = newValue
                if (!isPreview) {
                    inputPrefs?.showGamepadHints = newValue
                }
            },
        )

        var showRecommendations by rememberSaveable {
            mutableStateOf(if (isPreview) true else libraryPrefs?.showRecommendations ?: true)
        }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_interface_show_recommendations_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_interface_show_recommendations_subtitle)) },
            state = showRecommendations,
            onCheckedChange = {
                showRecommendations = it
                if (!isPreview) {
                    libraryPrefs?.showRecommendations = it
                }
                PluviaApp.events.emit(AndroidEvent.RecommendationToggleChanged)
                if (!isPreview && generalPrefs?.usageAnalyticsEnabled == true) {
                    com.posthog.PostHog.capture(
                        event = "\$set",
                        properties = mapOf("\$set" to mapOf("recommendation_enabled" to it)),
                    )
                    if (!it) {
                        com.posthog.PostHog.capture("recommendation_disabled")
                    }
                }
            },
        )

        if (!BuildConfig.MODERN_ANDROID) {
            val frontendSyncManager = remember(context) { context.appUtilsEntryPoint().frontendSyncManager() }
            val anyFrontendSyncConfigured by frontendSyncManager.anyConfigured.collectAsState()
            SettingsMenuLink(
                colors = settingsTileColorsAlt(),
                title = { Text(text = stringResource(R.string.settings_interface_frontend_sync_title)) },
                subtitle = { Text(text = stringResource(R.string.settings_interface_frontend_sync_subtitle)) },
                action = if (anyFrontendSyncConfigured) {
                    { FrontendSyncResyncButton() }
                } else null,
                onClick = { showFrontendSyncDialog = true },
            )
        }

        // Language selection
        SettingsMenuLink(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_language)) },
            subtitle = { Text(text = LocaleHelper.getLanguageDisplayName(if (isPreview) "en" else generalPrefs?.appLanguage ?: "en")) },
            onClick = { openLanguageDialog = true },
        )

        // Unified visual icon picker (affects app and notification icons)
        var selectedVariant by rememberSaveable {
            mutableStateOf(if (if (isPreview) false else (generalPrefs?.useAltLauncherIcon == true || generalPrefs?.useAltNotificationIcon == true)) 1 else 0)
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.settings_interface_icon_style),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                IconVariantCard(
                    label = stringResource(R.string.settings_theme_default),
                    launcherIconRes = R.mipmap.ic_launcher,
                    notificationIconRes = R.drawable.ic_notification,
                    selected = selectedVariant == 0,
                    onClick = {
                        selectedVariant = 0
                        if (!isPreview) {
                            generalPrefs?.useAltLauncherIcon = false
                            generalPrefs?.useAltNotificationIcon = false
                        }
                        IconSwitcher.applyLauncherIcon(context, false)
                    },
                )
                IconVariantCard(
                    label = stringResource(R.string.settings_theme_alternate),
                    launcherIconRes = R.mipmap.ic_launcher_alt,
                    notificationIconRes = R.drawable.ic_notification_alt,
                    selected = selectedVariant == 1,
                    onClick = {
                        selectedVariant = 1
                        if (!isPreview) {
                            generalPrefs?.useAltLauncherIcon = true
                            generalPrefs?.useAltNotificationIcon = true
                        }
                        IconSwitcher.applyLauncherIcon(context, true)
                    },
                )
            }
        }
    }

    // Custom Game Settings
    SettingsGroup(
        modifier = Modifier.background(Color.Transparent),
        title = { Text(text = stringResource(R.string.settings_interface_custom_games)) },
    ) {
        var importCustomGameAsSteamGame by rememberSaveable {
            mutableStateOf(if (isPreview) false else libraryPrefs?.importCustomGameAsSteamGame ?: false)
        }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_interface_custom_game_import_as_steam)) },
            state = importCustomGameAsSteamGame,
            onCheckedChange = {
                importCustomGameAsSteamGame = it
                if (!isPreview) {
                    libraryPrefs?.importCustomGameAsSteamGame = it
                }
            },
        )
    }

    // Platform integrations now live in the System Menu. The detailed
    // integration tiles and logout flows have been removed from Settings
    // to avoid duplication.

    // Downloads settings
    SettingsGroup(
        modifier = Modifier.background(Color.Transparent),
        title = { Text(text = stringResource(R.string.settings_downloads_title)) },
    ) {
        var wifiOnlyDownload by rememberSaveable {
            mutableStateOf(if (isPreview) false else downloadPrefs?.downloadOnWifiOnly ?: false)
        }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_interface_wifi_only_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_interface_wifi_only_subtitle)) },
            state = wifiOnlyDownload,
            onCheckedChange = {
                wifiOnlyDownload = it
                if (!isPreview) {
                    downloadPrefs?.downloadOnWifiOnly = it
                }
            },
        )

        // Download speed setting
        val downloadSpeedLabels = listOf(
            stringResource(R.string.settings_download_slow),
            stringResource(R.string.settings_download_medium),
            stringResource(R.string.settings_download_fast),
            stringResource(R.string.settings_download_blazing),
        )
        val downloadSpeedValues = remember { listOf(8, 16, 24, 32) }
        var downloadSpeedValue by rememberSaveable {
            mutableStateOf(
                downloadSpeedValues.indexOf(if (isPreview) 24 else downloadPrefs?.downloadSpeed ?: 24).takeIf { it >= 0 }?.toFloat() ?: 2f
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_download_speed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = stringResource(R.string.settings_download_heat_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.size(8.dp))
            Slider(
                value = downloadSpeedValue,
                onValueChange = { newIndex ->
                    downloadSpeedValue = newIndex
                    val index = newIndex.roundToInt().coerceIn(0, 3)
                    if (!isPreview) {
                        downloadPrefs?.downloadSpeed = downloadSpeedValues[index]
                    }
                },
                valueRange = 0f..3f,
                steps = 2, // Creates exactly 4 positions: 0, 1, 2, 3
            )
            // Labels below slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                downloadSpeedLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(60.dp)
                    )
                }
            }
        }

        val ctx = LocalContext.current
        val sm = ctx.getSystemService(StorageManager::class.java)

        // All writable non-primary volumes (SD / USB).
        // getExternalFilesDirs misses USB OTG on most devices, so StorageUtils also
        // enumerates StorageManager.storageVolumes and synthesizes the per-app files dir.
        // Runs off the composition thread because synthesizing the USB candidate
        // may need mkdirs() on first plug-in.
        val externalStorageFallbackLabel = stringResource(R.string.storage_external)
        val dirs by produceState(initialValue = emptyList<File>(), ctx) {
            value = withContext(Dispatchers.IO) {
                StorageUtils.getAllExternalFilesDirs(ctx)
                    .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
                    .filter { sm?.getStorageVolume(it)?.isPrimary != true }
            }
        }

        // Labels the user sees
        val labels = remember(dirs) {
            dirs.map { dir ->
                sm?.getStorageVolume(dir)?.getDescription(ctx) ?: externalStorageFallbackLabel
            }
        }
        var useExternalStorage by rememberSaveable {
            mutableStateOf(if (isPreview) false else downloadPrefs?.useExternalStorage ?: false)
        }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            enabled = dirs.isNotEmpty(),
            title = { Text(text = stringResource(R.string.settings_interface_external_storage_title)) },
            subtitle = {
                if (dirs.isEmpty())
                    Text(stringResource(R.string.settings_interface_no_external_storage))
                else
                    Text(stringResource(R.string.settings_interface_external_storage_subtitle))
            },
            state = useExternalStorage,
            onCheckedChange = {
                useExternalStorage = it
                if (!isPreview) {
                    downloadPrefs?.useExternalStorage = it
                    if (it && dirs.isNotEmpty()) {
                        downloadPrefs?.externalStoragePath = StorageUtils.preferredInstallRoot(dirs[0])
                    }
                }
            },
        )
        if (useExternalStorage) {
            // Currently selected item
            var selectedIndex by rememberSaveable(dirs) {
                mutableStateOf(
                    dirs.indexOfFirst { dir ->
                        dir.absolutePath == (if (isPreview) "" else downloadPrefs?.externalStoragePath) ||
                            StorageUtils.publicInstallRoot(dir)?.absolutePath == (if (isPreview) "" else downloadPrefs?.externalStoragePath)
                    }.takeIf { it >= 0 } ?: 0,
                )
            }
            SettingsListDropdown(
                title = { Text(text = stringResource(R.string.settings_interface_storage_volume_title)) },
                items = labels,
                value = selectedIndex,
                onItemSelected = { idx ->
                    selectedIndex = idx
                    if (!isPreview) {
                        downloadPrefs?.externalStoragePath = StorageUtils.preferredInstallRoot(dirs[idx])
                    }
                },
                colors = settingsTileColorsAlt(),
            )
        }
        // Steam download server selection
        SettingsMenuLink(
            colors = settingsTileColorsAlt(),
            title = { Text(text = stringResource(R.string.settings_interface_download_server_title)) },
            subtitle = {
                Text(text = steamRegionsList.getOrNull(selectedRegionIndex)?.second ?: stringResource(R.string.settings_region_default))
            },
            onClick = { openRegionDialog = true },
        )
    }

    // Steam Download Server choice dialog
    SingleChoiceDialog(
        openDialog = openRegionDialog,
        icon = Icons.Default.Map,
        iconDescription = stringResource(R.string.settings_interface_download_server_title),
        title = stringResource(R.string.settings_interface_download_server_title),
        items = steamRegionsList.map { it.second },
        currentItem = selectedRegionIndex,
        onSelected = { index ->
            selectedRegionIndex = index
            val selectedId = steamRegionsList[index].first
            if (!isPreview) {
                authPrefs?.cellId = selectedId
                authPrefs?.cellIdManuallySet = selectedId != 0
            }
        },
        onDismiss = { openRegionDialog = false },
    )

    // Status bar restart confirmation dialog
    MessageDialog(
        visible = showStatusBarRestartDialog,
        title = stringResource(R.string.settings_interface_restart_required_title),
        message = stringResource(R.string.settings_language_restart_message),
        confirmBtnText = stringResource(R.string.settings_language_restart_confirm),
        dismissBtnText = stringResource(R.string.cancel),
        onConfirmClick = {
            showStatusBarRestartDialog = false
            val newValue = pendingStatusBarValue ?: return@MessageDialog
            // Save preference and show loading dialog
            if (!isPreview) {
                generalPrefs?.hideStatusBarWhenNotInGame = newValue
            }
            showStatusBarLoadingDialog = true
            pendingStatusBarValue = null
        },
        onDismissRequest = {
            showStatusBarRestartDialog = false
            // Revert toggle to original value
            hideStatusBar = if (isPreview) false else generalPrefs?.hideStatusBarWhenNotInGame ?: false
            pendingStatusBarValue = null
        },
        onDismissClick = {
            showStatusBarRestartDialog = false
            // Revert toggle to original value
            hideStatusBar = if (isPreview) false else generalPrefs?.hideStatusBarWhenNotInGame ?: false
            pendingStatusBarValue = null
        },
    )

    // Loading dialog while saving and restarting
    LaunchedEffect(showStatusBarLoadingDialog) {
        if (showStatusBarLoadingDialog) {
            // Wait a bit for the preference to be saved (DataStore operations are async)
            delay(300)
            // Verify the preference was saved by reading it back
            withContext(Dispatchers.IO) {
                // Small delay to ensure DataStore write completes
                delay(200)
            }
            // Restart the app
            AppUtils.restartApplication(context)
        }
    }

    LoadingDialog(
        visible = showStatusBarLoadingDialog,
        progress = -1f, // Indeterminate progress
        message = context.getString(R.string.settings_saving_restarting),
    )

    // Language selection dialog
    SingleChoiceDialog(
        openDialog = openLanguageDialog,
        icon = Icons.Default.Map,
        iconDescription = stringResource(R.string.settings_language),
        title = stringResource(R.string.settings_select_language),
        items = languageNames,
        currentItem = selectedLanguageIndex,
        onSelected = { index ->
            selectedLanguageIndex = index
            val selectedCode = languageCodes[index]
            // Check if language actually changed
            if (selectedCode != (if (isPreview) "en" else generalPrefs?.appLanguage ?: "en")) {
                pendingLanguageCode = selectedCode
                showLanguageRestartDialog = true
            }
            openLanguageDialog = false
        },
        onDismiss = { openLanguageDialog = false },
    )

    // Language change restart confirmation dialog
    MessageDialog(
        visible = showLanguageRestartDialog,
        title = stringResource(R.string.settings_language_restart_title),
        message = stringResource(R.string.settings_language_restart_message),
        confirmBtnText = stringResource(R.string.settings_language_restart_confirm),
        dismissBtnText = stringResource(R.string.cancel),
        onConfirmClick = {
            showLanguageRestartDialog = false
            val newLanguage = pendingLanguageCode ?: return@MessageDialog
            // Save preference and show loading dialog
            if (!isPreview) {
                generalPrefs?.appLanguage = newLanguage
            }
            showLanguageLoadingDialog = true
            pendingLanguageCode = null
        },
        onDismissRequest = {
            showLanguageRestartDialog = false
            // Revert selection to original value
            selectedLanguageIndex = languageCodes.indexOf(if (isPreview) "en" else generalPrefs?.appLanguage ?: "en").takeIf { it >= 0 } ?: 0
            pendingLanguageCode = null
        },
        onDismissClick = {
            showLanguageRestartDialog = false
            // Revert selection to original value
            selectedLanguageIndex = languageCodes.indexOf(if (isPreview) "en" else generalPrefs?.appLanguage ?: "en").takeIf { it >= 0 } ?: 0
            pendingLanguageCode = null
        },
    )

    // Loading dialog while saving and restarting for language change
    LaunchedEffect(showLanguageLoadingDialog) {
        if (showLanguageLoadingDialog) {
            // Wait a bit for the preference to be saved (DataStore operations are async)
            delay(300)
            // Verify the preference was saved by reading it back
            withContext(Dispatchers.IO) {
                // Small delay to ensure DataStore write completes
                delay(200)
            }
            // Restart the app
            AppUtils.restartApplication(context)
        }
    }

    LoadingDialog(
        visible = showLanguageLoadingDialog,
        progress = -1f, // Indeterminate progress
        message = stringResource(R.string.settings_language_changing),
    )

    if (showFrontendSyncDialog) {
        FrontendSyncDialog(onDismiss = { showFrontendSyncDialog = false })
    }

    // GOG/Epic/Amazon login and logout flows (including loading dialogs and
    // confirmations) are now owned by the System Menu and shared helpers.

}


@Composable
private fun IconVariantCard(
    label: String,
    launcherIconRes: Int,
    notificationIconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val border = if (selected) BorderStroke(2.dp, PluviaTheme.colors.accentPurple) else BorderStroke(
        1.dp,
        PluviaTheme.colors.borderDefault.copy(alpha = 0.5f),
    )
    Card(
        modifier = Modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = border,
        colors = CardDefaults.cardColors(),
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.BottomEnd) {
                AndroidView(
                    modifier = Modifier.matchParentSize(),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            setImageResource(launcherIconRes)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                        }
                    },
                )
                Image(
                    painter = painterResource(id = notificationIconRes),
                    contentDescription = "$label notification icon",
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = label)
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun Preview_SettingsScreen() {
    PluviaTheme {
        SettingsGroupInterface(
            appTheme = AppTheme.DAY,
            paletteStyle = PaletteStyle.TonalSpot,
            onAppTheme = { },
            onPaletteStyle = { },
        )
    }
}

