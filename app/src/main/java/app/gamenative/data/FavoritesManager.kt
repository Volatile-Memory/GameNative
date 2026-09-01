package app.gamenative.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps track of which games the user has marked as favorite.
 *
 * Favorites are stored as a set of [LibraryItem.appId] values, so they work across every source
 * (Steam, GOG, Epic, Amazon and custom games) without needing an account. The current set is
 * exposed as a [StateFlow] so the library list and the game cards update as soon as it changes,
 * while preferences keep the values on disk between sessions.
 *
 * Note: This object serves as a backward-compatible facade delegating to [FavoritesRepository].
 * Preferred usage in ViewModels and services is injecting [FavoritesRepository] via Hilt.
 */
object FavoritesManager : FavoritesRepository {

    @Volatile
    internal var delegate: FavoritesRepository = object : FavoritesRepository {
        private val _favorites = MutableStateFlow<Set<String>>(emptySet())
        override val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()
        override val loaded: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
        override fun toggle(appId: String): Boolean? = null
    }

    override val favorites: StateFlow<Set<String>>
        get() = delegate.favorites

    override val loaded: StateFlow<Boolean>
        get() = delegate.loaded

    override fun toggle(appId: String): Boolean? {
        return delegate.toggle(appId)
    }
}
