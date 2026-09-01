package app.gamenative.core.runtime

import app.gamenative.core.coroutines.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class DefaultGameSessionManager @Inject constructor(
    private val componentBuilderProvider: Provider<GameSessionComponent.Builder>,
    @ApplicationScope private val appScope: CoroutineScope,
) : GameSessionManager {

    private val _activeSession = MutableStateFlow<ActiveGameSession?>(null)
    override val activeSession: StateFlow<ActiveGameSession?> = _activeSession.asStateFlow()

    private val sessionMutex = Mutex()

    init {
        // Automatically clean up any crashed sessions on initialization.
        appScope.launch {
            cleanupCrashedSessions()
        }
    }

    private suspend fun cleanupCrashedSessions() {
        sessionMutex.withLock {
            Timber.d("Checking for crashed sessions to clean up...")
            // TODO: Implement actual cleanup logic here (e.g. searching for stale containers or lock files)
            // and perform IO to delete/reset them.
        }
    }

    override suspend fun startSession(info: ActiveGameSessionInfo, onTeardown: suspend () -> Unit): ActiveGameSession {
        return sessionMutex.withLock {
            // If an existing session is running, cleanly terminate it first
            _activeSession.value?.let { existing ->
                Timber.w("Ending previous active session for ${existing.info.appId} before starting ${info.appId}")
                existing.terminate()
            }

            val component = componentBuilderProvider.get()
                .setSessionInfo(info)
                .build()

            val sessionScope = dagger.hilt.EntryPoints.get(component, GameSessionEntryPoint::class.java).sessionScope()

            var sessionRef: ActiveGameSession? = null
            val session = ActiveGameSession(
                info = info,
                component = component,
                sessionScope = sessionScope,
                onTeardown = {
                    sessionRef?.let { _activeSession.compareAndSet(it, null) }
                    onTeardown()
                },
            )
            sessionRef = session

            _activeSession.value = session
            Timber.i("Started new game session: ${info.title} (${info.appId}, source=${info.source})")
            session
        }
    }

    override suspend fun endSession() {
        sessionMutex.withLock {
            val current = _activeSession.value
            if (current != null) {
                Timber.i("Ending active game session: ${current.info.title} (${current.info.appId})")
                _activeSession.value = null
                current.terminate()
            }
        }
    }
}
