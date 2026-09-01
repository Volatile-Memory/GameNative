package app.gamenative.core.runtime

import app.gamenative.data.GameSource

/**
 * Metadata identifying the active running game session.
 */
data class ActiveGameSessionInfo(
    val appId: String,
    val title: String,
    val source: GameSource,
    val containerId: String,
    val startTimeMillis: Long = System.currentTimeMillis(),
)
