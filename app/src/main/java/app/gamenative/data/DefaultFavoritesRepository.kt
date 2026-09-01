package app.gamenative.data

import app.gamenative.preferences.LibraryPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Default implementation of [FavoritesRepository] backed by [LibraryPreferences] DataStore preferences.
 * Supports Hilt dependency injection and can be easily mocked or tested via its internal constructor.
 */
@Singleton
class DefaultFavoritesRepository internal constructor(
    private val scope: CoroutineScope,
    private val loadPreferences: () -> Set<String>,
    private val savePreferences: (Set<String>) -> Unit,
) : FavoritesRepository {

    @Inject
    constructor(libraryPreferences: LibraryPreferences) : this(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        loadPreferences = {
            try {
                libraryPreferences.favoriteAppIds
            } catch (e: Exception) {
                Timber.tag("FavoritesRepository").e(e, "Failed to load favorite app ids")
                emptySet()
            }
        },
        savePreferences = { libraryPreferences.favoriteAppIds = it },
    )

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    override val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val lock = Any()

    init {
        scope.launch {
            try {
                val stored = loadPreferences()
                synchronized(lock) {
                    _favorites.value = stored
                }
            } catch (e: Exception) {
                Timber.tag("FavoritesRepository").e(e, "Failed to initialize favorite app ids")
                synchronized(lock) {
                    _favorites.value = emptySet()
                }
            } finally {
                _loaded.value = true
            }
        }
    }

    override fun toggle(appId: String): Boolean? {
        synchronized(lock) {
            if (!_loaded.value) return null
            val favorite = appId !in _favorites.value
            val updated = FavoritesUtils.apply(_favorites.value, appId, favorite)
            if (updated == _favorites.value) return null
            _favorites.value = updated
            savePreferences(updated)
            return favorite
        }
    }
}
