package app.gamenative.core.runtime

import kotlinx.coroutines.flow.StateFlow

/**
 * Controller managing the lifecycle of game playing sessions.
 */
interface GameSessionManager {
    /** Reactive flow emitting the current active session, or null if no session is running. */
    val activeSession: StateFlow<ActiveGameSession?>

    /** True if a game session is currently active. */
    val isSessionRunning: Boolean
        get() = activeSession.value != null

    /** Current active session runtime, or null if no session is running. */
    val currentRuntime: GameSessionRuntime?
        get() = activeSession.value?.runtime

    /** Returns current runtime or creates a new active session if none is running. */
    fun getOrCreateRuntime(): GameSessionRuntime

    /** Starts a new game session, ending any existing active session first. */
    suspend fun startSession(info: ActiveGameSessionInfo, onTeardown: suspend () -> Unit = {}): ActiveGameSession

    /** Terminates and cleans up the currently active session. */
    suspend fun endSession()

    /** Convenience accessor for current active session info. */
    fun getActiveSessionInfo(): ActiveGameSessionInfo? = activeSession.value?.info
}
