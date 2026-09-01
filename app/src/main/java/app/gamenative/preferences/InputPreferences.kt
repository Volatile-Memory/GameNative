package app.gamenative.preferences

import kotlinx.coroutines.flow.Flow

interface InputPreferences {
    var useSteamInput: Boolean
    var xinputEnabled: Boolean
    var dinputEnabled: Boolean
    var dinputMapperType: Int
    var externalDisplayInputMode: String
    var externalDisplaySwap: Boolean
    var disableMouseInput: Boolean
    var swapFaceButtons: Boolean
    var showGamepadHints: Boolean
    var showControllerDebugMenu: Boolean
    var capturePointerOnExternalMouse: Boolean
    var moveCursorToTouchpoint: Boolean
    var controlsOpacity: Float

    val swapFaceButtonsFlow: Flow<Boolean>
    val showGamepadHintsFlow: Flow<Boolean>
    val useSteamInputFlow: Flow<Boolean>
}
