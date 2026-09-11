package app.gamenative.core.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.gamenative.core.coroutines.GameSessionCoroutineScope
import app.gamenative.data.GameSource
import app.gamenative.powercontrol.PowerManager
import app.gamenative.service.AchievementWatcher
import app.gamenative.service.ActiveGameRegistry
import app.gamenative.service.SteamManager
import app.gamenative.ui.screen.xserver.RadialMenuCoordinator
import com.winlator.container.Container
import com.winlator.inputcontrols.InputControlsManager
import com.winlator.widget.InputControlsView
import com.winlator.widget.TouchpadView
import com.winlator.widget.XServerRendererView
import com.winlator.xenvironment.XEnvironment
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

/**
 * Scoped runtime holder for the active game playing session.
 * Replaces static fields in [app.gamenative.PluviaApp.companion].
 */
@GameSessionScoped
class GameSessionRuntime @Inject constructor(
    val sessionInfo: ActiveGameSessionInfo,
    private val steamManagerProvider: Provider<SteamManager>,
    @GameSessionCoroutineScope private val sessionScope: CoroutineScope,
) {
    val appId: String get() = sessionInfo.appId
    val title: String get() = sessionInfo.title
    val source: GameSource get() = sessionInfo.source
    val containerId: String get() = sessionInfo.containerId
    val startTimeMillis: Long get() = sessionInfo.startTimeMillis

    var xEnvironment: XEnvironment? = null
    var xServerView: XServerRendererView? = null
    var inputControlsView: InputControlsView? = null
    var inputControlsManager: InputControlsManager? = null
    var touchpadView: TouchpadView? = null
    var radialMenuCoordinator: RadialMenuCoordinator? = null
    var achievementWatcher: AchievementWatcher? = null

    var isOverlayPaused: Boolean by mutableStateOf(false)
    @Volatile
    var isActivityInForeground: Boolean = true

    var activeSuspendPolicy: String = Container.SUSPEND_POLICY_MANUAL
        private set

    private var hasInitializedSuspendPolicyState: Boolean = false

    fun setActiveSuspendPolicy(policy: String) {
        activeSuspendPolicy = Container.normalizeSuspendPolicy(policy)
        hasInitializedSuspendPolicyState = true
    }

    fun clearActiveSuspendState() {
        activeSuspendPolicy = Container.SUSPEND_POLICY_MANUAL
        isOverlayPaused = false
        hasInitializedSuspendPolicyState = false
    }

    fun hasValidSuspendPolicyState(): Boolean = hasInitializedSuspendPolicyState
    fun isNeverSuspendMode(): Boolean = activeSuspendPolicy.equals(Container.SUSPEND_POLICY_NEVER, ignoreCase = true)
    fun isManualSuspendMode(): Boolean = activeSuspendPolicy.equals(Container.SUSPEND_POLICY_MANUAL, ignoreCase = true)

    fun startSession(info: ActiveGameSessionInfo = sessionInfo) {
        Timber.i("GameSessionRuntime: Starting session for ${info.title} (${info.appId})")
        clearActiveSuspendState()
    }

    fun pauseSession() {
        if (isNeverSuspendMode()) {
            Timber.d("GameSessionRuntime: Skipping overlay suspend due to suspend policy=never")
            return
        }
        xEnvironment?.onPause()
        isOverlayPaused = true
        Timber.d("GameSessionRuntime: Session paused")
    }

    fun resumeSession() {
        if (!isOverlayPaused) return
        if (!isNeverSuspendMode()) {
            xEnvironment?.onResume()
        }
        isOverlayPaused = false
        Timber.d("GameSessionRuntime: Session resumed")
    }

    fun onActivityResume() {
        isActivityInForeground = true
        if (hasValidSuspendPolicyState() && !isNeverSuspendMode() && !isOverlayPaused) {
            xEnvironment?.onResume()
        }
    }

    fun onActivityPause() {
        isActivityInForeground = false
        if (!isNeverSuspendMode()) {
            xEnvironment?.onPause()
        }
    }

    fun onResume() {
        xEnvironment?.onResume()
    }

    fun onPause() {
        xEnvironment?.onPause()
    }

    fun resumeGameProcesses() {
        xEnvironment?.resumeGameProcesses()
    }

    fun stopSession() {
        shutdownEnvironment()
    }

    /**
     * Full environment teardown — shared by session exit and activity onDestroy fallback.
     */
    fun shutdownEnvironment() {
        val env = xEnvironment
        Timber.i("GameSessionRuntime.shutdownEnvironment: env=%s", env != null)

        // Per-step catch so one failing teardown doesn't prevent the rest from running
        runCatching { achievementWatcher?.stop() }
            .onFailure { Timber.e(it, "shutdownEnvironment: achievementWatcher.stop") }

        runCatching { steamManagerProvider.get().clearCachedAchievements() }
            .onFailure { Timber.e(it, "shutdownEnvironment: clearCachedAchievements") }

        runCatching { touchpadView?.releasePointerCapture() }
            .onFailure { Timber.e(it, "shutdownEnvironment: releasePointerCapture") }

        runCatching { radialMenuCoordinator?.detach() }
            .onFailure { Timber.e(it, "shutdownEnvironment: radialMenuCoordinator.detach") }

        runCatching { env?.stopEnvironmentComponents() }
            .onFailure { Timber.e(it, "shutdownEnvironment: stopEnvironmentComponents") }

        // Stop performance driver
        runCatching { PowerManager.stop() }
            .onFailure { Timber.e(it, "shutdownEnvironment: PowerManager.stop") }

        xEnvironment = null
        xServerView = null
        inputControlsView = null
        inputControlsManager = null
        touchpadView = null
        radialMenuCoordinator = null
        achievementWatcher = null
        ActiveGameRegistry.clear()

        runCatching {
            val steamManager = steamManagerProvider.get()
            steamManager.keepAlive = false
            steamManager.clearPlayingConflict()
        }.onFailure { Timber.e(it, "shutdownEnvironment: steamManager reset") }

        clearActiveSuspendState()
    }
}
