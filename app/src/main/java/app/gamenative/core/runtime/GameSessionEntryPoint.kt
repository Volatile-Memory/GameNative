package app.gamenative.core.runtime

import app.gamenative.core.coroutines.GameSessionCoroutineScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import kotlinx.coroutines.CoroutineScope

/**
 * Entry point for retrieving dependencies bound to [GameSessionComponent].
 */
@EntryPoint
@InstallIn(GameSessionComponent::class)
interface GameSessionEntryPoint {
    fun sessionInfo(): ActiveGameSessionInfo
    
    @GameSessionCoroutineScope
    fun sessionScope(): CoroutineScope
}
