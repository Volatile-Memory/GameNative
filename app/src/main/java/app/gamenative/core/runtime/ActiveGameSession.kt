package app.gamenative.core.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * Handle to an active, running game session.
 * Encapsulates the session component, metadata, coroutine scope, and teardown logic.
 */
class ActiveGameSession(
    val info: ActiveGameSessionInfo,
    val component: GameSessionComponent,
    val sessionScope: CoroutineScope,
    private val onTeardown: suspend () -> Unit = {},
) {

    @Volatile
    var isClosed: Boolean = false
        private set

    suspend fun terminate() {
        if (isClosed) return
        isClosed = true
        sessionScope.cancel()
        onTeardown()
    }
}
