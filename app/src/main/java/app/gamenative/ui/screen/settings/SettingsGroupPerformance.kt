package app.gamenative.ui.screen.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import app.gamenative.preferences.PreferencesEntryPoint
import app.gamenative.R
import app.gamenative.ui.theme.settingsTileColorsAlt
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsSwitch

@Composable
fun SettingsGroupPerformance() {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val hudPrefs = remember(context, isPreview) {
        if (isPreview) null else PreferencesEntryPoint.get(context).hudPreferences()
    }

    SettingsGroup {
        var powerControlDefaultEnabled by rememberSaveable {
            mutableStateOf(if (isPreview) false else hudPrefs?.powerControlDefaultEnabled ?: false)
        }
        SettingsSwitch(
            colors = settingsTileColorsAlt(),
            state = powerControlDefaultEnabled,
            title = { Text(stringResource(R.string.settings_performance_power_control_title)) },
            subtitle = { Text(stringResource(R.string.settings_performance_power_control_subtitle)) },
            onCheckedChange = {
                powerControlDefaultEnabled = it
                if (!isPreview) {
                    hudPrefs?.powerControlDefaultEnabled = it
                }
            },
        )
    }
}
