package app.gamenative.core.runtime

import app.gamenative.data.GameSource
import app.gamenative.service.AchievementWatcher
import app.gamenative.service.SteamManager
import app.gamenative.ui.screen.xserver.RadialMenuCoordinator
import com.winlator.container.Container
import com.winlator.widget.TouchpadView
import com.winlator.xenvironment.XEnvironment
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameSessionRuntimeTest {

    private val sessionInfo = ActiveGameSessionInfo(
        appId = "400",
        title = "Portal",
        source = GameSource.STEAM,
        containerId = "STEAM_400",
        startTimeMillis = 1000L,
    )

    private val steamManager: SteamManager = mockk(relaxed = true)
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private lateinit var runtime: GameSessionRuntime

    @Before
    fun setUp() {
        runtime = GameSessionRuntime(
            sessionInfo = sessionInfo,
            steamManagerProvider = { steamManager },
            sessionScope = sessionScope,
        )
    }

    @Test
    fun initialState_hasExpectedDefaults() {
        assertEquals("400", runtime.appId)
        assertEquals("Portal", runtime.title)
        assertEquals(GameSource.STEAM, runtime.source)
        assertEquals("STEAM_400", runtime.containerId)
        assertEquals(1000L, runtime.startTimeMillis)

        assertNull(runtime.xEnvironment)
        assertNull(runtime.xServerView)
        assertNull(runtime.inputControlsView)
        assertNull(runtime.inputControlsManager)
        assertNull(runtime.touchpadView)
        assertNull(runtime.radialMenuCoordinator)
        assertNull(runtime.achievementWatcher)

        assertFalse(runtime.isOverlayPaused)
        assertTrue(runtime.isActivityInForeground)
        assertEquals(Container.SUSPEND_POLICY_MANUAL, runtime.activeSuspendPolicy)
        assertFalse(runtime.hasValidSuspendPolicyState())
        assertTrue(runtime.isManualSuspendMode())
        assertFalse(runtime.isNeverSuspendMode())
    }

    @Test
    fun suspendPolicyTransitions_updateFlagsCorrectly() {
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_NEVER)
        assertTrue(runtime.hasValidSuspendPolicyState())
        assertTrue(runtime.isNeverSuspendMode())
        assertFalse(runtime.isManualSuspendMode())

        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_MANUAL)
        assertTrue(runtime.hasValidSuspendPolicyState())
        assertFalse(runtime.isNeverSuspendMode())
        assertTrue(runtime.isManualSuspendMode())

        runtime.clearActiveSuspendState()
        assertFalse(runtime.hasValidSuspendPolicyState())
        assertEquals(Container.SUSPEND_POLICY_MANUAL, runtime.activeSuspendPolicy)
        assertFalse(runtime.isOverlayPaused)
    }

    @Test
    fun pauseAndResume_withManualPolicy_controlsEnvironmentAndOverlay() {
        val mockEnv: XEnvironment = mockk(relaxed = true)
        runtime.xEnvironment = mockEnv
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_MANUAL)

        runtime.pauseSession()
        assertTrue(runtime.isOverlayPaused)
        verify(exactly = 1) { mockEnv.onPause() }

        runtime.resumeSession()
        assertFalse(runtime.isOverlayPaused)
        verify(exactly = 1) { mockEnv.onResume() }
    }

    @Test
    fun pauseSession_withNeverSuspendPolicy_skipsPause() {
        val mockEnv: XEnvironment = mockk(relaxed = true)
        runtime.xEnvironment = mockEnv
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_NEVER)

        runtime.pauseSession()
        assertFalse(runtime.isOverlayPaused)
        verify(exactly = 0) { mockEnv.onPause() }
    }

    @Test
    fun onActivityResumeAndPause_updatesForegroundAndEnvironment() {
        val mockEnv: XEnvironment = mockk(relaxed = true)
        runtime.xEnvironment = mockEnv
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_MANUAL)

        runtime.onActivityPause()
        assertFalse(runtime.isActivityInForeground)
        verify(exactly = 1) { mockEnv.onPause() }

        runtime.onActivityResume()
        assertTrue(runtime.isActivityInForeground)
        verify(exactly = 1) { mockEnv.onResume() }
    }

    @Test
    fun teardownResilience_continuesCleanupWhenComponentThrows() {
        val mockWatcher: AchievementWatcher = mockk(relaxed = true)
        every { mockWatcher.stop() } throws RuntimeException("Watcher stop exploded")

        val mockTouchpad: TouchpadView = mockk(relaxed = true)
        val mockRadial: RadialMenuCoordinator = mockk(relaxed = true)
        val mockEnv: XEnvironment = mockk(relaxed = true)

        runtime.achievementWatcher = mockWatcher
        runtime.touchpadView = mockTouchpad
        runtime.radialMenuCoordinator = mockRadial
        runtime.xEnvironment = mockEnv
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_MANUAL)

        runtime.shutdownEnvironment()

        // Verify remaining teardown steps were still invoked
        verify(exactly = 1) { steamManager.clearCachedAchievements() }
        verify(exactly = 1) { mockTouchpad.releasePointerCapture() }
        verify(exactly = 1) { mockRadial.detach() }
        verify(exactly = 1) { mockEnv.stopEnvironmentComponents() }
        verify(exactly = 1) { steamManager.clearPlayingConflict() }

        // Verify state is completely reset and references nulled
        assertNull(runtime.xEnvironment)
        assertNull(runtime.touchpadView)
        assertNull(runtime.radialMenuCoordinator)
        assertNull(runtime.achievementWatcher)
        assertFalse(runtime.hasValidSuspendPolicyState())
    }
}
