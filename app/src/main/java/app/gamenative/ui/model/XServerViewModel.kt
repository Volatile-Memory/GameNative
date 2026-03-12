package app.gamenative.ui.model

import androidx.lifecycle.ViewModel
import app.gamenative.ResolutionSnapper
import app.gamenative.ui.data.XServerState
import com.winlator.container.Container
import com.winlator.core.KeyValueSet
import com.winlator.core.WineInfo
import com.winlator.xserver.XServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * Represents a pending runtime resolution change waiting for user confirmation.
 *
 * @param containerWidth  Raw Android container width (physical pixels).
 * @param containerHeight Raw Android container height (physical pixels).
 * @param snapped         Closest standard PC resolution for the new container size.
 */
data class PendingResolutionChange(
    val containerWidth: Int,
    val containerHeight: Int,
    val snapped: ResolutionSnapper.StandardResolution,
)

class XServerViewModel : ViewModel() {
    private val _xServerState = MutableStateFlow(XServerState())
    val xServerState: StateFlow<XServerState> = _xServerState.asStateFlow()

    /** Non-null while waiting for the user to confirm a runtime resolution change. */
    private val _pendingResolutionChange = MutableStateFlow<PendingResolutionChange?>(null)
    val pendingResolutionChange: StateFlow<PendingResolutionChange?> = _pendingResolutionChange.asStateFlow()

    // fun setEnvVars(envVars: EnvVars) {
    //     _xServerState.update { currentState ->
    //         currentState.copy(envVars = envVars)
    //     }
    // }

    fun setDxwrapper(dxwrapper: String) {
        _xServerState.update { currentState ->
            currentState.copy(dxwrapper = dxwrapper)
        }
    }

    fun setDxwrapperConfig(dxwrapperConfig: KeyValueSet?) {
        _xServerState.update { currentState ->
            Timber.i("Setting dxwrapperConfig to $dxwrapperConfig")
            currentState.copy(dxwrapperConfig = dxwrapperConfig)
        }
    }

    // fun setShortcut(shortcut: Shortcut?) {
    //     _xServerState.update { currentState ->
    //         currentState.copy(shortcut = shortcut)
    //     }
    // }

    fun setScreenSize(screenSize: String) {
        _xServerState.update { currentState ->
            currentState.copy(screenSize = screenSize)
        }
    }

    fun setWineInfo(wineInfo: WineInfo) {
        _xServerState.update { currentState ->
            currentState.copy(wineInfo = wineInfo)
        }
    }

    fun setGraphicsDriver(graphicsDriver: String) {
        _xServerState.update { currentState ->
            currentState.copy(graphicsDriver = graphicsDriver)
        }
    }

    fun setAudioDriver(audioDriver: String) {
        _xServerState.update { currentState ->
            currentState.copy(audioDriver = audioDriver)
        }
    }

    /**
     * Called when the GL surface settles at a new size (after the 150 ms debounce
     * in GLRenderer). Behaviour depends on [resolutionChangeMode]:
     * - PROMPT    — set [pendingResolutionChange] so the UI shows a confirmation dialog.
     * - SNAP      — apply the closest standard resolution immediately.
     * - ARBITRARY — apply the raw container dimensions immediately.
     */
    fun onContainerResized(
        xServer: XServer?,
        width: Int,
        height: Int,
        resolutionChangeMode: String,
    ) {
        if (xServer == null) return
        when (resolutionChangeMode) {
            Container.RESOLUTION_CHANGE_MODE_ARBITRARY -> {
                xServer.updateScreenSize(width, height)
            }
            Container.RESOLUTION_CHANGE_MODE_SNAP -> {
                val snapped = ResolutionSnapper.findClosest(width, height)
                xServer.updateScreenSize(snapped.width, snapped.height)
            }
            else -> { // PROMPT (default)
                val snapped = ResolutionSnapper.findClosest(width, height)
                val currentW = xServer.screenInfo.width.toInt()
                val currentH = xServer.screenInfo.height.toInt()
                if (snapped.width == currentW && snapped.height == currentH) return
                _pendingResolutionChange.value = PendingResolutionChange(width, height, snapped)
            }
        }
    }

    /** Called when the user confirms the resolution change dialog. */
    fun applyResolutionChange(xServer: XServer?, width: Int, height: Int) {
        _pendingResolutionChange.value = null
        xServer?.updateScreenSize(width, height)
    }

    /** Called when the user dismisses the resolution change dialog without applying. */
    fun dismissResolutionChange() {
        _pendingResolutionChange.value = null
    }
}
