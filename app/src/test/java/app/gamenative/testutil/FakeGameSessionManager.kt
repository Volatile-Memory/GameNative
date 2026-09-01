package app.gamenative.testutil

import app.gamenative.core.runtime.ActiveGameSession
import app.gamenative.core.runtime.ActiveGameSessionInfo
import app.gamenative.core.runtime.GameSessionComponent
import app.gamenative.core.runtime.GameSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake [GameSessionManager] for unit testing session transitions without Hilt.
 */
class FakeGameSessionManager : GameSessionManager {

    private val _activeSession = MutableStateFlow<ActiveGameSession?>(null)
    override val activeSession: StateFlow<ActiveGameSession?> = _activeSession.asStateFlow()

    var startSessionCallCount: Int = 0
        private set

    var endSessionCallCount: Int = 0
        private set

    override suspend fun startSession(info: ActiveGameSessionInfo, onTeardown: suspend () -> Unit): ActiveGameSession {
        startSessionCallCount++
        _activeSession.value?.terminate()

        val dummyComponent = object : GameSessionComponent {}
        val dummyScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        var sessionRef: ActiveGameSession? = null
        val session = ActiveGameSession(
            info = info,
            component = dummyComponent,
            sessionScope = dummyScope,
            onTeardown = {
                sessionRef?.let { _activeSession.compareAndSet(it, null) }
                onTeardown()
            },
        )
        sessionRef = session
        _activeSession.value = session
        return session
    }

    override suspend fun endSession() {
        endSessionCallCount++
        val current = _activeSession.value
        _activeSession.value = null
        current?.terminate()
    }
}
