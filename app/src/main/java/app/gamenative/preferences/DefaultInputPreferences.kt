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
import com.winlator.container.Container
import com.winlator.widget.InputControlsView
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
class DefaultInputPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : InputPreferences {

    private companion object {
        val USE_STEAM_INPUT = booleanPreferencesKey("use_steam_input")
        val XINPUT_ENABLED = booleanPreferencesKey("xinput_enabled")
        val DINPUT_ENABLED = booleanPreferencesKey("dinput_enabled")
        val DINPUT_MAPPER_TYPE = intPreferencesKey("dinput_mapper_type")
        val EXTERNAL_DISPLAY_INPUT_MODE = stringPreferencesKey("external_display_input_mode")
        val EXTERNAL_DISPLAY_SWAP = booleanPreferencesKey("external_display_swap")
        val DISABLE_MOUSE_INPUT = booleanPreferencesKey("disable_mouse_input")
        val SWAP_FACE_BUTTONS = booleanPreferencesKey("swap_face_buttons")
        val SHOW_GAMEPAD_HINTS = booleanPreferencesKey("show_gamepad_hints")
        val SHOW_CONTROLLER_DEBUG_MENU = booleanPreferencesKey("show_controller_debug_menu")
        val CAPTURE_POINTER_ON_EXTERNAL_MOUSE = booleanPreferencesKey("capture_pointer_on_external_mouse")
        val MOVE_CURSOR_TO_TOUCHPOINT = booleanPreferencesKey("move_cursor_to_touchpoint")
        val CONTROLS_OPACITY = floatPreferencesKey("controls_opacity")
    }

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var useSteamInput: Boolean
        get() = getPref(USE_STEAM_INPUT, false)
        set(value) = setPref(USE_STEAM_INPUT, value)

    override var xinputEnabled: Boolean
        get() = getPref(XINPUT_ENABLED, true)
        set(value) = setPref(XINPUT_ENABLED, value)

    override var dinputEnabled: Boolean
        get() = getPref(DINPUT_ENABLED, true)
        set(value) = setPref(DINPUT_ENABLED, value)

    override var dinputMapperType: Int
        get() = getPref(DINPUT_MAPPER_TYPE, 1)
        set(value) = setPref(DINPUT_MAPPER_TYPE, value)

    override var externalDisplayInputMode: String
        get() = getPref(EXTERNAL_DISPLAY_INPUT_MODE, Container.DEFAULT_EXTERNAL_DISPLAY_MODE)
        set(value) = setPref(EXTERNAL_DISPLAY_INPUT_MODE, value)

    override var externalDisplaySwap: Boolean
        get() = getPref(EXTERNAL_DISPLAY_SWAP, false)
        set(value) = setPref(EXTERNAL_DISPLAY_SWAP, value)

    override var disableMouseInput: Boolean
        get() = getPref(DISABLE_MOUSE_INPUT, false)
        set(value) = setPref(DISABLE_MOUSE_INPUT, value)

    override var swapFaceButtons: Boolean
        get() = getPref(SWAP_FACE_BUTTONS, false)
        set(value) = setPref(SWAP_FACE_BUTTONS, value)

    override var showGamepadHints: Boolean
        get() = getPref(SHOW_GAMEPAD_HINTS, true)
        set(value) = setPref(SHOW_GAMEPAD_HINTS, value)

    override var showControllerDebugMenu: Boolean
        get() = getPref(SHOW_CONTROLLER_DEBUG_MENU, false)
        set(value) = setPref(SHOW_CONTROLLER_DEBUG_MENU, value)

    override var capturePointerOnExternalMouse: Boolean
        get() = getPref(CAPTURE_POINTER_ON_EXTERNAL_MOUSE, true)
        set(value) = setPref(CAPTURE_POINTER_ON_EXTERNAL_MOUSE, value)

    override var moveCursorToTouchpoint: Boolean
        get() = getPref(MOVE_CURSOR_TO_TOUCHPOINT, false)
        set(value) = setPref(MOVE_CURSOR_TO_TOUCHPOINT, value)

    override var controlsOpacity: Float
        get() = getPref(CONTROLS_OPACITY, InputControlsView.DEFAULT_OVERLAY_OPACITY)
        set(value) = setPref(CONTROLS_OPACITY, value)

    override val swapFaceButtonsFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SWAP_FACE_BUTTONS] ?: false }
        .distinctUntilChanged()

    override val showGamepadHintsFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_GAMEPAD_HINTS] ?: true }
        .distinctUntilChanged()

    override val useSteamInputFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[USE_STEAM_INPUT] ?: false }
        .distinctUntilChanged()
}
