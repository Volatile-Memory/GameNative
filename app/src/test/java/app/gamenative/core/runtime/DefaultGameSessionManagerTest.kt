package app.gamenative.core.runtime

import app.gamenative.data.GameSource
import app.gamenative.service.SteamManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

class DefaultGameSessionManagerTest {

    interface TestSessionComponent : GameSessionComponent, GameSessionEntryPoint

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val steamManager: SteamManager = mockk(relaxed = true)

    private lateinit var builder: GameSessionComponent.Builder
    private lateinit var manager: DefaultGameSessionManager

    private var currentInfo: ActiveGameSessionInfo? = null

    @Before
    fun setUp() {
        builder = mockk()

        every { builder.setSessionInfo(any()) } answers {
            currentInfo = firstArg()
            builder
        }

        every { builder.build() } answers {
            val info = currentInfo ?: ActiveGameSessionInfo("0", "default", GameSource.CUSTOM_GAME, "0")
            val dummyScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val dummyRuntime = GameSessionRuntime(
                sessionInfo = info,
                steamManagerProvider = { steamManager },
                sessionScope = dummyScope,
            )

            object : TestSessionComponent {
                override fun sessionInfo(): ActiveGameSessionInfo = info
                override fun sessionScope(): CoroutineScope = dummyScope
                override fun gameSessionRuntime(): GameSessionRuntime = dummyRuntime
            }
        }

        manager = DefaultGameSessionManager(
            componentBuilderProvider = Provider { builder },
            appScope = appScope,
        )
    }

    @Test
    fun initialState_hasNoActiveSession() {
        assertFalse(manager.isSessionRunning)
        assertNull(manager.activeSession.value)
        assertNull(manager.currentRuntime)
        assertNull(manager.getActiveSessionInfo())
    }

    @Test
    fun startSession_createsActiveSessionAndRuntime() = runBlocking {
        val info = ActiveGameSessionInfo(
            appId = "400",
            title = "Portal",
            source = GameSource.STEAM,
            containerId = "STEAM_400",
        )

        var teardownInvoked = false
        val session = manager.startSession(info, onTeardown = { teardownInvoked = true })

        assertTrue(manager.isSessionRunning)
        assertEquals(session, manager.activeSession.value)
        assertEquals(info, manager.getActiveSessionInfo())
        assertNotNull(manager.currentRuntime)
        assertEquals("400", manager.currentRuntime?.appId)
        assertFalse(session.isClosed)

        manager.endSession()

        assertFalse(manager.isSessionRunning)
        assertNull(manager.activeSession.value)
        assertNull(manager.currentRuntime)
        assertTrue(session.isClosed)
        assertTrue(teardownInvoked)
    }

    @Test
    fun startingNewSession_terminatesPreviousActiveSession() = runBlocking {
        val info1 = ActiveGameSessionInfo("400", "Portal", GameSource.STEAM, "1")
        val session1 = manager.startSession(info1)

        val info2 = ActiveGameSessionInfo("620", "Portal 2", GameSource.STEAM, "2")
        val session2 = manager.startSession(info2)

        assertTrue(session1.isClosed)
        assertFalse(session2.isClosed)
        assertEquals(info2, manager.getActiveSessionInfo())
        assertEquals("620", manager.currentRuntime?.appId)
    }

    @Test
    fun getOrCreateRuntime_createsSessionWhenNoneActive_orReturnsExisting() = runBlocking {
        assertFalse(manager.isSessionRunning)

        val runtime1 = manager.getOrCreateRuntime()
        assertNotNull(runtime1)
        assertTrue(manager.isSessionRunning)

        val runtime2 = manager.getOrCreateRuntime()
        assertEquals(runtime1, runtime2)
    }
}
