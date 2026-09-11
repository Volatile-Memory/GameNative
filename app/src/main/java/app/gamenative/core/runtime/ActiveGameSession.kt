package app.gamenative.core.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handle to an active, running game session.
 * Encapsulates the session component, metadata, coroutine scope, and teardown logic.
 */
class ActiveGameSession(
    val info: ActiveGameSessionInfo,
    val component: GameSessionComponent,
    val sessionScope: CoroutineScope,
    val runtime: GameSessionRuntime,
    private val onTeardown: suspend () -> Unit = {},
) {

    private val _isClosed = AtomicBoolean(false)
    val isClosed: Boolean get() = _isClosed.get()

    suspend fun terminate() {
        if (!_isClosed.compareAndSet(false, true)) return
        sessionScope.cancel()
        runtime.shutdownEnvironment()
        onTeardown()
    }
}
