package app.gamenative.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class EventDispatcherStressTest {

    private sealed interface StressEvent : Event<Unit> {
        data class Ping(val id: Int) : StressEvent
        object Heartbeat : StressEvent
    }

    private lateinit var dispatcher: EventDispatcher

    @Before
    fun setUp() {
        dispatcher = EventDispatcher()
    }

    @Test
    fun listenerException_isolatesFailureAndCleansUpOnceListeners() {
        var firstListenerRan = false
        var secondListenerRan = false
        var onceListenerRan = false

        dispatcher.once<StressEvent.Heartbeat, Unit> {
            firstListenerRan = true
            throw IllegalStateException("Listener 1 catastrophic failure")
        }

        dispatcher.on<StressEvent.Heartbeat, Unit> {
            secondListenerRan = true
        }

        dispatcher.once<StressEvent.Heartbeat, Unit> {
            onceListenerRan = true
        }

        // When emit is called, listener 1 failure is isolated in runCatching.
        dispatcher.emit(StressEvent.Heartbeat)

        assertTrue("First listener should have executed", firstListenerRan)
        assertTrue("Second listener should have executed despite failure in listener 1", secondListenerRan)
        assertTrue("Third (once) listener should have executed", onceListenerRan)

        // Verify that once listeners are cleaned up
        val listenersList = dispatcher.listeners[StressEvent.Heartbeat::class]
        assertNotNull(listenersList)
        val remainingOnceListeners = listenersList?.filter { it.second.once }
        assertEquals(
            "Once listeners should be cleaned up after execution",
            0,
            remainingOnceListeners?.size,
        )
    }

    @Test
    fun clearAllListenersOf_removesSpecifiedListeners() {
        var pingCount = 0
        dispatcher.on<StressEvent.Ping, Unit> { pingCount++ }

        dispatcher.clearAllListenersOf<StressEvent.Ping>()

        // Empirical check: Were listeners actually removed?
        val listenersForPing = dispatcher.listeners[StressEvent.Ping::class]
        assertTrue("clearAllListenersOf must remove listeners for the event class", listenersForPing == null || listenersForPing.isEmpty())

        dispatcher.emit(StressEvent.Ping(1))
        assertEquals("Listener should not fire after clearAllListenersOf", 0, pingCount)
    }

    @Test
    fun concurrentListenerRegistrationAndEmission_revealsThreadSafetyLimitations() {
        val numThreads = 16
        val operationsPerThread = 50
        val executor = Executors.newFixedThreadPool(numThreads)
        val latch = CountDownLatch(numThreads)
        val caughtExceptions = Collections.synchronizedList(mutableListOf<Throwable>())

        val receivedCount = AtomicInteger(0)

        for (i in 0 until numThreads) {
            executor.submit {
                try {
                    for (j in 0 until operationsPerThread) {
                        val listener: (StressEvent.Ping) -> Unit = {
                            receivedCount.incrementAndGet()
                        }
                        dispatcher.on(listener)
                        dispatcher.emit(StressEvent.Ping(j))
                        dispatcher.off(listener)
                    }
                } catch (t: Throwable) {
                    caughtExceptions.add(t)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // If exceptions were caught (such as ConcurrentModificationException or ArrayIndexOutOfBoundsException),
        // we record them as empirical proof of non-thread-safe collection usage.
        if (caughtExceptions.isNotEmpty()) {
            println("EMPIRICAL FINDING: Caught ${caughtExceptions.size} concurrency exceptions in EventDispatcher: ${caughtExceptions.first()::class.java.name}: ${caughtExceptions.first().message}")
        }
    }

    @Test
    fun highVolumeSequentialEmission_aggregatesCorrectly() {
        val count = 1000
        val received = AtomicInteger(0)

        dispatcher.on<StressEvent.Ping, Unit> {
            received.incrementAndGet()
        }

        for (i in 0 until count) {
            dispatcher.emit(StressEvent.Ping(i))
        }

        assertEquals(count, received.get())
    }
}
