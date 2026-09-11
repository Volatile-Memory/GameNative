package app.gamenative.core.runtime

import app.gamenative.data.GameSource
import app.gamenative.testutil.FakeGameSessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionManagerTest {

    @Test
    fun fakeGameSessionManager_managesLifecycleCorrectly() = runBlocking {
        val manager = FakeGameSessionManager()
        assertFalse(manager.isSessionRunning)
        assertNull(manager.activeSession.value)

        var teardownCalled = false
        val info = ActiveGameSessionInfo(
            appId = "400",
            title = "Portal",
            source = GameSource.STEAM,
            containerId = "STEAM_400",
        )

        val session = manager.startSession(info, onTeardown = { teardownCalled = true })

        assertTrue(manager.isSessionRunning)
        assertEquals(info, manager.getActiveSessionInfo())
        assertEquals(1, manager.startSessionCallCount)
        assertFalse(session.isClosed)

        manager.endSession()

        assertFalse(manager.isSessionRunning)
        assertNull(manager.activeSession.value)
        assertEquals(1, manager.endSessionCallCount)
        assertTrue(session.isClosed)
        assertTrue(teardownCalled)
    }

    @Test
    fun fakeGameSessionManager_startingNewSessionEndsPrevious() = runBlocking {
        val manager = FakeGameSessionManager()
        var session1Teardown = false

        val info1 = ActiveGameSessionInfo("400", "Portal", GameSource.STEAM, "1")
        val session1 = manager.startSession(info1, onTeardown = { session1Teardown = true })

        val info2 = ActiveGameSessionInfo("620", "Portal 2", GameSource.STEAM, "2")
        val session2 = manager.startSession(info2)

        assertTrue(session1.isClosed)
        assertTrue(session1Teardown)
        assertFalse(session2.isClosed)
        assertEquals(info2, manager.getActiveSessionInfo())
    }

    @Test
    fun fakeGameSessionManager_currentRuntimeAndGetOrCreateRuntime() = runBlocking {
        val manager = FakeGameSessionManager()
        assertNull(manager.currentRuntime)

        val runtime = manager.getOrCreateRuntime()
        assertNotNull(runtime)
        assertTrue(manager.isSessionRunning)
        assertEquals(runtime, manager.currentRuntime)

        manager.endSession()
        assertNull(manager.currentRuntime)
        assertFalse(manager.isSessionRunning)
    }
}
