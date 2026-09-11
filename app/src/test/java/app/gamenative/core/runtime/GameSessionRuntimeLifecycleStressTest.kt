package app.gamenative.core.runtime

import app.gamenative.PluviaApp
import app.gamenative.data.GameProcessInfo
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameSessionRuntimeLifecycleStressTest {

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
        mockkObject(PowerManager)
        every { PowerManager.stop() } returns Unit

        runtime = GameSessionRuntime(
            sessionInfo = sessionInfo,
            steamManagerProvider = { steamManager },
            sessionScope = sessionScope,
        )
    }

    @After
    fun tearDown() {
        unmockkObject(PowerManager)
        ActiveGameRegistry.clear()
    }

    // =========================================================================
    // 1. Lifecycle Transitions & Exception Resilience during shutdownEnvironment
    // =========================================================================

    @Test
    fun shutdownEnvironment_whenAchievementWatcherThrows_continuesRemainingTeardownAndNullsFields() {
        val mockWatcher = mockk<AchievementWatcher>(relaxed = true)
        every { mockWatcher.stop() } throws IllegalStateException("AchievementWatcher exploded")

        val mockTouchpad = mockk<TouchpadView>(relaxed = true)
        val mockRadial = mockk<RadialMenuCoordinator>(relaxed = true)
        val mockEnv = mockk<XEnvironment>(relaxed = true)

        runtime.achievementWatcher = mockWatcher
        runtime.touchpadView = mockTouchpad
        runtime.radialMenuCoordinator = mockRadial
        runtime.xEnvironment = mockEnv
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_MANUAL)

        runtime.shutdownEnvironment()

        verify(exactly = 1) { steamManager.clearCachedAchievements() }
        verify(exactly = 1) { mockTouchpad.releasePointerCapture() }
        verify(exactly = 1) { mockRadial.detach() }
        verify(exactly = 1) { mockEnv.stopEnvironmentComponents() }
        verify(exactly = 1) { PowerManager.stop() }

        assertNull("achievementWatcher must be nulled", runtime.achievementWatcher)
        assertNull("touchpadView must be nulled", runtime.touchpadView)
        assertNull("radialMenuCoordinator must be nulled", runtime.radialMenuCoordinator)
        assertNull("xEnvironment must be nulled", runtime.xEnvironment)
        assertFalse("Suspend state must be cleared", runtime.hasValidSuspendPolicyState())
    }

    @Test
    fun shutdownEnvironment_whenTouchpadViewThrows_continuesRemainingTeardownAndNullsFields() {
        val mockTouchpad = mockk<TouchpadView>(relaxed = true)
        every { mockTouchpad.releasePointerCapture() } throws RuntimeException("Pointer capture release failed")

        val mockRadial = mockk<RadialMenuCoordinator>(relaxed = true)
        val mockEnv = mockk<XEnvironment>(relaxed = true)

        runtime.touchpadView = mockTouchpad
        runtime.radialMenuCoordinator = mockRadial
        runtime.xEnvironment = mockEnv

        runtime.shutdownEnvironment()

        verify(exactly = 1) { mockRadial.detach() }
        verify(exactly = 1) { mockEnv.stopEnvironmentComponents() }
        verify(exactly = 1) { PowerManager.stop() }

        assertNull(runtime.touchpadView)
        assertNull(runtime.radialMenuCoordinator)
        assertNull(runtime.xEnvironment)
    }

    @Test
    fun shutdownEnvironment_whenEnvironmentStopComponentsThrows_continuesAndClearsRegistry() {
        val mockEnv = mockk<XEnvironment>(relaxed = true)
        every { mockEnv.stopEnvironmentComponents() } throws RuntimeException("XEnvironment stop exploded")

        runtime.xEnvironment = mockEnv
        ActiveGameRegistry.set(GameProcessInfo(appId = 400, processes = emptyList()))

        runtime.shutdownEnvironment()

        verify(exactly = 1) { PowerManager.stop() }
        assertNull(runtime.xEnvironment)
        assertNull("ActiveGameRegistry must be cleared", ActiveGameRegistry.get())
        assertFalse(runtime.hasValidSuspendPolicyState())
    }

    @Test
    fun shutdownEnvironment_whenAllTeardownComponentsThrowSimultaneously_survivesAndNullsFields() {
        val mockWatcher = mockk<AchievementWatcher>(relaxed = true)
        val mockTouchpad = mockk<TouchpadView>(relaxed = true)
        val mockRadial = mockk<RadialMenuCoordinator>(relaxed = true)
        val mockEnv = mockk<XEnvironment>(relaxed = true)

        every { mockWatcher.stop() } throws RuntimeException("Error 1")
        every { steamManager.clearCachedAchievements() } throws RuntimeException("Error 2")
        every { mockTouchpad.releasePointerCapture() } throws RuntimeException("Error 3")
        every { mockRadial.detach() } throws RuntimeException("Error 4")
        every { mockEnv.stopEnvironmentComponents() } throws RuntimeException("Error 5")
        every { steamManager.clearPlayingConflict() } throws RuntimeException("Error 6")

        runtime.achievementWatcher = mockWatcher
        runtime.touchpadView = mockTouchpad
        runtime.radialMenuCoordinator = mockRadial
        runtime.xEnvironment = mockEnv
        runtime.xServerView = mockk(relaxed = true)
        runtime.inputControlsView = mockk(relaxed = true)
        runtime.inputControlsManager = mockk(relaxed = true)
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_NEVER)
        ActiveGameRegistry.set(GameProcessInfo(appId = 400, processes = emptyList()))

        runtime.shutdownEnvironment()

        assertNull(runtime.achievementWatcher)
        assertNull(runtime.touchpadView)
        assertNull(runtime.radialMenuCoordinator)
        assertNull(runtime.xEnvironment)
        assertNull(runtime.xServerView)
        assertNull(runtime.inputControlsView)
        assertNull(runtime.inputControlsManager)
        assertNull(ActiveGameRegistry.get())
        assertFalse(runtime.hasValidSuspendPolicyState())
    }

    @Test
    fun shutdownEnvironment_whenPowerManagerThrows_allViewsNulledAndRegistryClearedAndSuspendPolicyReset() {
        every { PowerManager.stop() } throws RuntimeException("PServer driver uninitialized or stop failed")

        val mockWatcher = mockk<AchievementWatcher>(relaxed = true)
        val mockTouchpad = mockk<TouchpadView>(relaxed = true)
        val mockRadial = mockk<RadialMenuCoordinator>(relaxed = true)
        val mockEnv = mockk<XEnvironment>(relaxed = true)
        val mockServer = mockk<XServerRendererView>(relaxed = true)
        val mockControlsView = mockk<InputControlsView>(relaxed = true)
        val mockControlsManager = mockk<InputControlsManager>(relaxed = true)

        runtime.achievementWatcher = mockWatcher
        runtime.touchpadView = mockTouchpad
        runtime.radialMenuCoordinator = mockRadial
        runtime.xEnvironment = mockEnv
        runtime.xServerView = mockServer
        runtime.inputControlsView = mockControlsView
        runtime.inputControlsManager = mockControlsManager

        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_NEVER)
        runtime.isOverlayPaused = true
        ActiveGameRegistry.set(GameProcessInfo(appId = 400, processes = emptyList()))

        val caughtException = runCatching { runtime.shutdownEnvironment() }.exceptionOrNull()

        assertNull("PowerManager.stop() exception must be caught and isolated", caughtException)
        assertNull("xEnvironment must be nulled", runtime.xEnvironment)
        assertNull("xServerView must be nulled", runtime.xServerView)
        assertNull("inputControlsView must be nulled", runtime.inputControlsView)
        assertNull("inputControlsManager must be nulled", runtime.inputControlsManager)
        assertNull("touchpadView must be nulled", runtime.touchpadView)
        assertNull("radialMenuCoordinator must be nulled", runtime.radialMenuCoordinator)
        assertNull("achievementWatcher must be nulled", runtime.achievementWatcher)
        assertNull("ActiveGameRegistry must be cleared", ActiveGameRegistry.get())
        assertFalse("hasValidSuspendPolicyState must be false", runtime.hasValidSuspendPolicyState())
        assertFalse("isOverlayPaused must be reset to false", runtime.isOverlayPaused)
        assertEquals(Container.SUSPEND_POLICY_MANUAL, runtime.activeSuspendPolicy)
    }

    @Test
    fun terminate_whenPowerManagerThrows_stillExecutesOnTeardownAndClosesSession() = runBlocking {
        every { PowerManager.stop() } throws IllegalStateException("PowerManager stop failure")

        val component = mockk<GameSessionComponent>(relaxed = true)
        val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var teardownInvoked = false

        val session = ActiveGameSession(
            info = sessionInfo,
            component = component,
            sessionScope = testScope,
            runtime = runtime,
            onTeardown = { teardownInvoked = true },
        )

        session.terminate()

        assertTrue("Session must be marked closed", session.isClosed)
        assertTrue("onTeardown callback must be invoked even when PowerManager throws", teardownInvoked)
        assertFalse("Session scope must be cancelled", testScope.isActive)
        assertNull("xEnvironment must be nulled", runtime.xEnvironment)
        assertNull("ActiveGameRegistry must be cleared", ActiveGameRegistry.get())
    }

    // =========================================================================
    // 2. Suspend Policy Transitions & State Machine
    // =========================================================================

    @Test
    fun suspendPolicy_allVariants_handledProperly() {
        // Initial state
        assertFalse(runtime.hasValidSuspendPolicyState())
        assertTrue(runtime.isManualSuspendMode())
        assertFalse(runtime.isNeverSuspendMode())

        // NEVER variations
        runtime.setActiveSuspendPolicy("never")
        assertTrue(runtime.hasValidSuspendPolicyState())
        assertTrue(runtime.isNeverSuspendMode())
        assertFalse(runtime.isManualSuspendMode())

        runtime.setActiveSuspendPolicy("NEVER")
        assertTrue(runtime.isNeverSuspendMode())

        runtime.setActiveSuspendPolicy("Never")
        assertTrue(runtime.isNeverSuspendMode())

        // MANUAL variations
        runtime.setActiveSuspendPolicy("manual")
        assertTrue(runtime.hasValidSuspendPolicyState())
        assertFalse(runtime.isNeverSuspendMode())
        assertTrue(runtime.isManualSuspendMode())

        runtime.setActiveSuspendPolicy("MANUAL")
        assertTrue(runtime.isManualSuspendMode())

        // AUTO / Invalid falls back to AUTO
        runtime.setActiveSuspendPolicy("auto")
        assertTrue(runtime.hasValidSuspendPolicyState())
        assertFalse(runtime.isNeverSuspendMode())
        assertFalse(runtime.isManualSuspendMode())

        runtime.setActiveSuspendPolicy("invalid_policy_string")
        assertTrue(runtime.hasValidSuspendPolicyState())
        assertFalse(runtime.isNeverSuspendMode())
        assertFalse(runtime.isManualSuspendMode())

        // clearActiveSuspendState resets
        runtime.clearActiveSuspendState()
        assertFalse(runtime.hasValidSuspendPolicyState())
        assertEquals(Container.SUSPEND_POLICY_MANUAL, runtime.activeSuspendPolicy)
        assertFalse(runtime.isOverlayPaused)

        // startSession resets
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_NEVER)
        assertTrue(runtime.hasValidSuspendPolicyState())
        runtime.startSession()
        assertFalse("startSession must clear suspend state", runtime.hasValidSuspendPolicyState())
    }

    @Test
    fun pauseResumeStateMachine_respectsOverlayAndNeverSuspendMode() {
        val mockEnv = mockk<XEnvironment>(relaxed = true)
        runtime.xEnvironment = mockEnv
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_MANUAL)

        // 1. User manual overlay pause
        runtime.pauseSession()
        assertTrue(runtime.isOverlayPaused)
        verify(exactly = 1) { mockEnv.onPause() }

        // 2. Activity pause while overlay is paused
        runtime.onActivityPause()
        assertFalse(runtime.isActivityInForeground)
        verify(exactly = 2) { mockEnv.onPause() }

        // 3. Activity resume: because isOverlayPaused is true, onActivityResume should NOT resume env
        runtime.onActivityResume()
        assertTrue(runtime.isActivityInForeground)
        verify(exactly = 0) { mockEnv.onResume() }

        // 4. User presses resume in overlay: now env resumes
        runtime.resumeSession()
        assertFalse(runtime.isOverlayPaused)
        verify(exactly = 1) { mockEnv.onResume() }

        // 5. Double resume: no-op
        runtime.resumeSession()
        verify(exactly = 1) { mockEnv.onResume() }

        // 6. NEVER suspend policy: pauses are completely ignored
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_NEVER)
        runtime.pauseSession()
        assertFalse("Overlay pause should not be set in NEVER suspend mode", runtime.isOverlayPaused)
        runtime.onActivityPause()
        // verify no additional onPause was called
        verify(exactly = 2) { mockEnv.onPause() }
    }

    @Test
    fun pauseResumeStateMachine_autoPolicy_respectsOverlayPauseAndActivityTransitions() {
        val mockEnv = mockk<XEnvironment>(relaxed = true)
        runtime.xEnvironment = mockEnv
        runtime.setActiveSuspendPolicy(Container.SUSPEND_POLICY_AUTO)

        assertTrue(runtime.hasValidSuspendPolicyState())
        assertFalse(runtime.isNeverSuspendMode())
        assertFalse(runtime.isManualSuspendMode())

        // 1. User pause via overlay
        runtime.pauseSession()
        assertTrue(runtime.isOverlayPaused)
        verify(exactly = 1) { mockEnv.onPause() }

        // 2. Activity pause while overlay is paused
        runtime.onActivityPause()
        assertFalse(runtime.isActivityInForeground)
        verify(exactly = 2) { mockEnv.onPause() }

        // 3. Activity resume: because overlay is paused, must NOT resume environment
        runtime.onActivityResume()
        assertTrue(runtime.isActivityInForeground)
        verify(exactly = 0) { mockEnv.onResume() }

        // 4. Overlay resumed: now environment resumes
        runtime.resumeSession()
        assertFalse(runtime.isOverlayPaused)
        verify(exactly = 1) { mockEnv.onResume() }

        // 5. Subsequent normal background/foreground cycle resumes automatically
        runtime.onActivityPause()
        verify(exactly = 3) { mockEnv.onPause() }
        runtime.onActivityResume()
        verify(exactly = 2) { mockEnv.onResume() }
    }

    // =========================================================================
    // 3. Null Session Safety & PluviaApp.companion Delegation
    // =========================================================================

    @Test
    fun nullSessionSafety_pluviaAppCompanionDelegation_returnsSafeDefaultsWhenNoSessionRunning() {
        // Without PluviaApp.instance or active session, companion delegates must not throw NPE
        assertNull(PluviaApp.xEnvironment)
        assertNull(PluviaApp.xServerView)
        assertNull(PluviaApp.inputControlsView)
        assertNull(PluviaApp.inputControlsManager)
        assertNull(PluviaApp.touchpadView)
        assertNull(PluviaApp.radialMenuCoordinator)
        assertNull(PluviaApp.achievementWatcher)

        assertFalse(PluviaApp.isOverlayPaused)
        assertTrue(PluviaApp.isActivityInForeground)
        assertEquals(Container.SUSPEND_POLICY_MANUAL, PluviaApp.activeSuspendPolicy)
        assertFalse(PluviaApp.hasValidSuspendPolicyState())
        assertFalse(PluviaApp.isNeverSuspendMode())
        assertFalse(PluviaApp.isManualSuspendMode())

        // Mutators when no session is running should complete safely without NPE
        PluviaApp.xEnvironment = null
        PluviaApp.xServerView = null
        PluviaApp.inputControlsView = null
        PluviaApp.inputControlsManager = null
        PluviaApp.touchpadView = null
        PluviaApp.radialMenuCoordinator = null
        PluviaApp.achievementWatcher = null
        PluviaApp.isOverlayPaused = true
        PluviaApp.isActivityInForeground = false
        PluviaApp.setActiveSuspendPolicy("never")
        PluviaApp.clearActiveSuspendState()
        PluviaApp.shutdownEnvironment()

        assertEquals(Container.DEFAULT_SCREEN_SIZE_16_9, PluviaApp.getDefaultScreenSize())
    }
}
