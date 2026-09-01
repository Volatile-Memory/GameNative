package app.gamenative.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import app.gamenative.Constants
import app.gamenative.preferences.PreferencesEntryPoint
import app.gamenative.R
import app.gamenative.ui.component.dialog.LibrariesDialog
import app.gamenative.ui.theme.settingsTileColors
import app.gamenative.ui.theme.settingsTileColorsAlt
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import com.alorma.compose.settings.ui.SettingsSwitch

@Composable
fun SettingsGroupInfo() {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val generalPrefs = remember(context, isPreview) {
        if (isPreview) null else PreferencesEntryPoint.get(context).generalPreferences()
    }

    SettingsGroup() {
        val uriHandler = LocalUriHandler.current
        var askForTip by rememberSaveable {
            mutableStateOf(if (isPreview) false else !(generalPrefs?.tipped ?: false))
        }
        var showLibrariesDialog by rememberSaveable { mutableStateOf(false) }

        LibrariesDialog(
            visible = showLibrariesDialog,
            onDismissRequest = { showLibrariesDialog = false },
        )

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.settings_info_send_tip_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_info_send_tip_subtitle)) },
            icon = { Icon(imageVector = Icons.Filled.MonetizationOn, contentDescription = "Tip") },
            onClick = {
                uriHandler.openUri(Constants.Misc.KO_FI_LINK)
            },
        )

        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = askForTip,
            title = { Text(stringResource(R.string.settings_info_ask_tip_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_info_ask_tip_subtitle)) },
            onCheckedChange = {
                askForTip = it
                if (!isPreview) {
                    generalPrefs?.tipped = !askForTip
                }
            },
        )

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_info_source_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_info_source_subtitle)) },
            onClick = { uriHandler.openUri(Constants.Misc.GITHUB_LINK) },
        )

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_info_libraries_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_info_libraries_subtitle)) },
            onClick = { showLibrariesDialog = true },
        )

        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(text = stringResource(R.string.settings_info_privacy_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_info_privacy_subtitle)) },
            onClick = {
                uriHandler.openUri(Constants.Misc.PRIVACY_LINK)
            },
        )

        var usageAnalytics by rememberSaveable {
            mutableStateOf(if (isPreview) false else generalPrefs?.usageAnalyticsEnabled ?: false)
        }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = usageAnalytics,
            title = { Text(stringResource(R.string.settings_info_usage_analytics_title)) },
            subtitle = { Text(text = stringResource(R.string.settings_info_usage_analytics_subtitle)) },
            onCheckedChange = {
                usageAnalytics = it
                if (!isPreview) {
                    generalPrefs?.usageAnalyticsEnabled = it
                }
            },
        )
    }
}
