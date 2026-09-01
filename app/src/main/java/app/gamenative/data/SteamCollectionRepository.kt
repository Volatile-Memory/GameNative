package app.gamenative.data

import app.gamenative.preferences.LibraryPreferences
import app.gamenative.steam.SteamCollectionParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

object SteamCollectionRepository {
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    var preferences: LibraryPreferences? = null

    // null = not yet loaded (show all); empty = loaded but none; non-empty = loaded
    private val _collections = MutableStateFlow<List<SteamCollection>?>(null)
    val collections: StateFlow<List<SteamCollection>?> = _collections.asStateFlow()

    private val _skippedDynamic = MutableStateFlow(false)
    val skippedDynamic: StateFlow<Boolean> = _skippedDynamic.asStateFlow()

    /** Populate from the persisted JSON snapshot so the filter works offline / before fetch. */
    fun loadFromCache(prefs: LibraryPreferences? = preferences) {
        val targetPrefs = prefs ?: preferences
        val raw = targetPrefs?.librarySteamCollectionsCache ?: ""
        if (raw.isEmpty()) return
        try {
            _collections.value = json.decodeFromString<List<SteamCollection>>(raw)
            _skippedDynamic.value = targetPrefs?.librarySteamCollectionsSkippedDynamic ?: false
        } catch (t: Throwable) {
            Timber.tag("SteamCollectionRepo").w(t, "Failed to load cached collections; clearing corrupt cache")
            _collections.value = null
            targetPrefs?.librarySteamCollectionsCache = ""
            targetPrefs?.librarySteamCollectionsSkippedDynamic = false
            _skippedDynamic.value = false
        }
    }

    /** Set the freshly-fetched collections and persist them. */
    fun update(result: SteamCollectionParser.ParseResult, prefs: LibraryPreferences? = preferences) {
        val targetPrefs = prefs ?: preferences
        _collections.value = result.collections
        _skippedDynamic.value = result.skippedDynamicCount > 0
        targetPrefs?.librarySteamCollectionsSkippedDynamic = _skippedDynamic.value
        try {
            targetPrefs?.librarySteamCollectionsCache = json.encodeToString(result.collections)
        } catch (t: Throwable) {
            Timber.tag("SteamCollectionRepo").w(t, "Failed to persist collections")
        }
    }

    fun clear(prefs: LibraryPreferences? = preferences) {
        val targetPrefs = prefs ?: preferences
        _collections.value = null
        _skippedDynamic.value = false
        targetPrefs?.librarySteamCollectionsCache = ""
        targetPrefs?.librarySteamCollectionsSkippedDynamic = false
    }
}
