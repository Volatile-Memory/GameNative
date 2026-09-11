package app.gamenative.events

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class EventDispatcherTest {

    private sealed interface TestEvent : Event<Unit> {
        data class Message(val text: String) : TestEvent
        object Ping : TestEvent
    }

    private sealed interface ComputeEvent : Event<Int> {
        data class Add(val value: Int) : ComputeEvent
    }

    private lateinit var dispatcher: EventDispatcher

    @Before
    fun setUp() {
        dispatcher = EventDispatcher()
    }

    @Test
    fun on_registersListenerAndReceivesEvents() {
        var receivedText: String? = null
        val listener: (TestEvent.Message) -> Unit = { event ->
            receivedText = event.text
        }

        dispatcher.on(listener)
        dispatcher.emit(TestEvent.Message("hello world"))

        assertEquals("hello world", receivedText)

        dispatcher.emit(TestEvent.Message("updated"))
        assertEquals("updated", receivedText)
    }

    @Test
    fun once_firesOnlyOnceAndIsRemoved() {
        var callCount = 0
        val listener: (TestEvent.Ping) -> Unit = {
            callCount++
        }

        dispatcher.once(listener)
        dispatcher.emit(TestEvent.Ping)
        assertEquals(1, callCount)

        dispatcher.emit(TestEvent.Ping)
        assertEquals(1, callCount)
    }

    @Test
    fun off_removesListener() {
        var callCount = 0
        val listener: (TestEvent.Ping) -> Unit = {
            callCount++
        }

        dispatcher.on(listener)
        dispatcher.emit(TestEvent.Ping)
        assertEquals(1, callCount)

        dispatcher.off(listener)
        dispatcher.emit(TestEvent.Ping)
        assertEquals(1, callCount)
    }

    @Test
    fun emit_withResultAggregator_aggregatesResults() {
        val listener1: (ComputeEvent.Add) -> Int = { it.value * 2 }
        val listener2: (ComputeEvent.Add) -> Int = { it.value + 10 }

        dispatcher.on(listener1)
        dispatcher.on(listener2)

        val total = dispatcher.emit(ComputeEvent.Add(5)) { results ->
            results.sum()
        }

        // listener1 -> 10, listener2 -> 15. Sum = 25.
        assertEquals(25, total)
    }

    @Test
    fun clearAllListeners_removesAllHandlers() {
        var pingCount = 0
        var msgCount = 0

        dispatcher.on<TestEvent.Ping, Unit> { pingCount++ }
        dispatcher.on<TestEvent.Message, Unit> { msgCount++ }

        dispatcher.clearAllListeners()

        dispatcher.emit(TestEvent.Ping)
        dispatcher.emit(TestEvent.Message("test"))

        assertEquals(0, pingCount)
        assertEquals(0, msgCount)
    }

    @Test
    fun emitJava_firesRegisteredListeners() {
        var received = false
        dispatcher.on<TestEvent.Ping, Unit> { received = true }

        dispatcher.emitJava(TestEvent.Ping)
        assertEquals(true, received)
    }
}
