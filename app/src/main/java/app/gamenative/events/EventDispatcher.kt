package app.gamenative.events

import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

// written with the help of Claude 3.5
sealed interface Event<T>

class EventDispatcher {
    val listeners = ConcurrentHashMap<KClass<out Event<*>>, CopyOnWriteArrayList<Pair<String, EventListener<Event<*>, *>>>>()

    open class EventListener<E : Event<T>, T>(
        val listener: (E) -> T,
        val once: Boolean = false,
    )

    inline fun <reified E : Event<T>, T> on(noinline listener: (E) -> T) {
        addListener<E, T>(listener, false)
    }

    inline fun <reified E : Event<T>, T> once(noinline listener: (E) -> T) {
        addListener<E, T>(listener, true)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified E : Event<T>, T> addListener(
        noinline listener: (E) -> T,
        once: Boolean,
    ) {
        val eventClass = E::class
        val typedListener = Pair(
            listener.toString(),
            EventListener<Event<T>, T>({ event ->
                // Log.d("EventDispatcher", "Dispatching event $event to $listener")
                listener(event as E)
            }, once),
        )
        // Log.d("EventDispatcher", "Putting $typedListener in $eventClass")
        listeners.getOrPut(eventClass) { CopyOnWriteArrayList() }.add(typedListener as Pair<String, EventListener<Event<*>, *>>)
    }

    inline fun <reified E : Event<T>, T> off(noinline listener: (E) -> T) {
        val eventClass = E::class
        listeners[eventClass]?.removeIf {
            // Log.d("EventDispatcher", "Removing if ${it.first} == $listener")
            it.first == listener.toString()
        }
    }

    inline fun <reified E : Event<*>> clearAllListenersOf() {
        listeners.remove(E::class)
    }

    fun clearAllListeners() {
        listeners.clear()
    }

    inline fun <reified E : Event<T>, reified T> emit(event: E, noinline resultAggregator: ((Array<T>) -> T)? = null): T? {
        val eventClass = E::class
        // Log.d("EventDispatcher", "Emitting $eventClass")
        return listeners[eventClass]?.let { eventListeners ->
            val snapshot = eventListeners.toList()
            // Remove one-time listeners after snapshotting for execution
            eventListeners.removeIf { it.second.once }
            val results = mutableListOf<T>()
            for (eventListener in snapshot) {
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    eventListener.second.listener(event) as T
                }.onSuccess {
                    results.add(it)
                }.onFailure {
                    Timber.e(it, "EventDispatcher: listener failure for %s", eventClass.simpleName)
                }
            }
            resultAggregator?.let { it(results.toTypedArray()) }
        }
    }

    // Java-friendly version that doesn't use reified generics
    @Suppress("UNCHECKED_CAST")
    fun emitJava(event: Event<*>): Any? {
        val eventClass = event::class
        return listeners[eventClass]?.let { eventListeners ->
            val snapshot = eventListeners.toList()
            // Remove one-time listeners after snapshotting for execution
            eventListeners.removeIf { it.second.once }
            val results = mutableListOf<Any?>()
            for (eventListener in snapshot) {
                runCatching {
                    eventListener.second.listener(event)
                }.onSuccess {
                    results.add(it)
                }.onFailure {
                    Timber.e(it, "EventDispatcher: emitJava listener failure for %s", eventClass.simpleName)
                }
            }
            results.firstOrNull()
        }
    }
}
