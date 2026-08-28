package app.gamenative.data

import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for managing user favorite games.
 * Decouples favorites persistence and state observation from static singletons.
 */
interface FavoritesRepository {
    /**
     * Set of favorite [LibraryItem.appId] values.
     */
    val favorites: StateFlow<Set<String>>

    /**
     * Whether the saved favorites set has finished loading from disk/preferences.
     */
    val loaded: StateFlow<Boolean>

    /**
     * Toggles favorite status for a given [appId].
     *
     * @return The new favorite status (true if favorited, false if removed), or null if ignored (not loaded yet).
     */
    fun toggle(appId: String): Boolean?
}
