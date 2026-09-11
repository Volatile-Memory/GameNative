package app.gamenative.core.runtime

import app.gamenative.data.GameSource
import app.gamenative.service.SteamManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

class DefaultGameSessionManagerStressTest {

    interface TestSessionComponent : GameSessionComponent, GameSessionEntryPoint

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val steamManager: SteamManager = mockk(relaxed = true)

    private lateinit var builder: GameSessionComponent.Builder
    private lateinit var manager: DefaultGameSessionManager

    private val createdRuntimes = Collections.synchronizedList(mutableListOf<GameSessionRuntime>())

    @Before
    fun setUp() {
        createdRuntimes.clear()
        builder = mockk()

        every { builder.setSessionInfo(any()) } returns builder

        every { builder.build() } answers {
            val dummyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val info = ActiveGameSessionInfo("test_app", "Test App", GameSource.CUSTOM_GAME, "0")
            val dummyRuntime = GameSessionRuntime(
                sessionInfo = info,
                steamManagerProvider = { steamManager },
                sessionScope = dummyScope,
            )
            createdRuntimes.add(dummyRuntime)

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
    fun concurrentStartSession_serializesAndClosesAllPriorSessions() = runBlocking(Dispatchers.Default) {
        val count = 20
        val teardownCount = AtomicInteger(0)

        val deferreds = (0 until count).map { i ->
            async {
                val info = ActiveGameSessionInfo(
                    appId = "app_$i",
                    title = "App $i",
                    source = GameSource.STEAM,
                    containerId = "container_$i",
                )
                manager.startSession(info) {
                    teardownCount.incrementAndGet()
                }
            }
        }

        val allSessions = deferreds.awaitAll()
        val currentSession = manager.activeSession.value

        assertNotNull("An active session should be present", currentSession)
        assertTrue("Manager should report session running", manager.isSessionRunning)

        // Count closed vs open sessions
        val closedSessions = allSessions.filter { it.isClosed }
        val openSessions = allSessions.filter { !it.isClosed }

        assertEquals("Exactly one session should remain open", 1, openSessions.size)
        assertEquals("All other sessions should be closed", count - 1, closedSessions.size)
        assertEquals("Teardown should be invoked for all closed sessions", count - 1, teardownCount.get())

        manager.endSession()
        assertFalse(manager.isSessionRunning)
        assertNull(manager.activeSession.value)
        assertEquals("Final teardown count should equal total started sessions", count, teardownCount.get())
    }

    @Test
    fun rapidSequentialTransitions_maintainsStateFlowConsistency() = runBlocking(Dispatchers.Default) {
        val iterations = 30
        var totalTeardowns = 0

        for (i in 0 until iterations) {
            val info = ActiveGameSessionInfo("app_$i", "App $i", GameSource.CUSTOM_GAME, "$i")
            val session = manager.startSession(info) {
                totalTeardowns++
            }

            assertTrue(manager.isSessionRunning)
            assertEquals(session, manager.activeSession.value)
            assertNotNull(manager.currentRuntime)

            manager.endSession()

            assertFalse(manager.isSessionRunning)
            assertNull(manager.activeSession.value)
            assertNull(manager.currentRuntime)
            assertTrue(session.isClosed)
        }

        assertEquals(iterations, totalTeardowns)
    }

    @Test
    fun getOrCreateRuntime_concurrentAccess_demonstratesMultipleCreationsWithoutLock() {
        val numThreads = 10
        val executor = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)
        val runtimes = Collections.synchronizedList(mutableListOf<GameSessionRuntime>())

        for (i in 0 until numThreads) {
            executor.submit {
                try {
                    val rt = manager.getOrCreateRuntime()
                    runtimes.add(rt)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Because getOrCreateRuntime is not synchronized, multiple threads might create separate component/runtime instances
        val distinctRuntimes = runtimes.distinct()
        println("EMPIRICAL FINDING: getOrCreateRuntime across $numThreads threads produced ${distinctRuntimes.size} distinct runtimes (total builder invocations: ${createdRuntimes.size})")

        // Active session should be running
        assertTrue(manager.isSessionRunning)
    }
}
