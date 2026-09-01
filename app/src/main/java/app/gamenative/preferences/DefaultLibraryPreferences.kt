package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.di.PluviaDataStore
import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.enums.SortOption
import java.util.EnumSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLibraryPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : LibraryPreferences {

    private companion object {
        val LIBRARY_LAYOUT = intPreferencesKey("library_layout")
        val LIBRARY_FILTER = intPreferencesKey("library_filter")
        val LIBRARY_SORT_KEY = stringPreferencesKey("library_sort_key")
        val LIBRARY_SORT_LEGACY = intPreferencesKey("library_sort")
        val ITEMS_PER_PAGE = intPreferencesKey("items_per_page")
        val SHOW_STEAM_IN_LIBRARY = booleanPreferencesKey("show_steam_in_library")
        val SHOW_CUSTOM_GAMES_IN_LIBRARY = booleanPreferencesKey("show_custom_games_in_library")
        val SHOW_GOG_IN_LIBRARY = booleanPreferencesKey("show_gog_in_library")
        val SHOW_EPIC_IN_LIBRARY = booleanPreferencesKey("show_epic_in_library")
        val SHOW_AMAZON_IN_LIBRARY = booleanPreferencesKey("show_amazon_in_library")
        val CUSTOM_GAMES_COUNT = intPreferencesKey("custom_games_count")
        val STEAM_GAMES_COUNT = intPreferencesKey("steam_games_count")
        val GOG_GAMES_COUNT = intPreferencesKey("gog_games_count")
        val EPIC_GAMES_COUNT = intPreferencesKey("epic_games_count")
        val GOG_INSTALLED_GAMES_COUNT = intPreferencesKey("gog_installed_games_count")
        val EPIC_INSTALLED_GAMES_COUNT = intPreferencesKey("epic_installed_games_count")
        val AMAZON_INSTALLED_GAMES_COUNT = intPreferencesKey("amazon_installed_games_count")
        val LIBRARY_STEAM_COLLECTIONS_CACHE = stringPreferencesKey("library_steam_collections_cache")
        val LIBRARY_STEAM_COLLECTIONS_SKIPPED_DYNAMIC = booleanPreferencesKey("library_steam_collections_skipped_dynamic")
        val LIBRARY_STEAM_COLLECTIONS = stringPreferencesKey("library_steam_collections")
        const val COLLECTION_ID_SEPARATOR = " "
        val RECOMMENDATION_CACHE_JSON = stringPreferencesKey("recommendation_cache_json")
        val RECOMMENDATION_CACHE_TIMESTAMP = longPreferencesKey("recommendation_cache_timestamp")
        val SHOW_RECOMMENDATIONS = booleanPreferencesKey("show_recommendations")
        val REC_DISCLOSURE_SHOWN = booleanPreferencesKey("rec_disclosure_shown")
        val REC_TEASER_DISMISSED_DAY = longPreferencesKey("rec_teaser_dismissed_day")
        val SHOW_ADD_CUSTOM_GAME_DIALOG = booleanPreferencesKey("show_add_custom_game_dialog")
        val IMPORT_CUSTOM_GAME_AS_STEAM_GAME = booleanPreferencesKey("import_custom_game_as_steam_game")
        val CUSTOM_GAME_PATHS = stringPreferencesKey("custom_game_paths")
        val CUSTOM_GAME_MANUAL_FOLDERS = stringPreferencesKey("custom_game_manual_folders")
        val FAVORITE_APP_IDS = stringPreferencesKey("favorite_app_ids")
        val GOG_AMAZON_PATH_MIGRATED = booleanPreferencesKey("gog_amazon_path_migrated")
    }

    private val favoritePersistenceLock = Any()
    private var favoritePersistenceVersion = 0L

    @Volatile
    private var recDisclosureShownCache: Boolean? = null

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var libraryLayout: PaneType
        get() {
            val value = getPref(LIBRARY_LAYOUT, PaneType.UNDECIDED.ordinal)
            return PaneType.entries.getOrNull(value) ?: PaneType.UNDECIDED
        }
        set(value) = setPref(LIBRARY_LAYOUT, value.ordinal)

    override var libraryFilter: EnumSet<AppFilter>
        get() {
            val value = getPref(LIBRARY_FILTER, AppFilter.toFlags(EnumSet.of(AppFilter.GAME, AppFilter.SHARED)))
            return AppFilter.fromFlags(value)
        }
        set(value) = setPref(LIBRARY_FILTER, AppFilter.toFlags(value))

    override var librarySortOption: SortOption
        get() {
            val keyValue = getPref(LIBRARY_SORT_KEY, "")
            return if (keyValue.isNotEmpty()) {
                SortOption.fromKey(keyValue)
            } else {
                val ordinal = getPref(LIBRARY_SORT_LEGACY, SortOption.INSTALLED_FIRST.ordinal)
                @Suppress("DEPRECATION")
                SortOption.fromOrdinal(ordinal)
            }
        }
        set(value) = setPref(LIBRARY_SORT_KEY, value.key)

    override var itemsPerPage: Int
        get() = getPref(ITEMS_PER_PAGE, 50)
        set(value) = setPref(ITEMS_PER_PAGE, value)

    override var showSteamInLibrary: Boolean
        get() = getPref(SHOW_STEAM_IN_LIBRARY, true)
        set(value) = setPref(SHOW_STEAM_IN_LIBRARY, value)

    override var showCustomGamesInLibrary: Boolean
        get() = getPref(SHOW_CUSTOM_GAMES_IN_LIBRARY, true)
        set(value) = setPref(SHOW_CUSTOM_GAMES_IN_LIBRARY, value)

    override var showGOGInLibrary: Boolean
        get() = getPref(SHOW_GOG_IN_LIBRARY, true)
        set(value) = setPref(SHOW_GOG_IN_LIBRARY, value)

    override var showEpicInLibrary: Boolean
        get() = getPref(SHOW_EPIC_IN_LIBRARY, true)
        set(value) = setPref(SHOW_EPIC_IN_LIBRARY, value)

    override var showAmazonInLibrary: Boolean
        get() = getPref(SHOW_AMAZON_IN_LIBRARY, true)
        set(value) = setPref(SHOW_AMAZON_IN_LIBRARY, value)

    override var customGamesCount: Int
        get() = getPref(CUSTOM_GAMES_COUNT, 0)
        set(value) = setPref(CUSTOM_GAMES_COUNT, value)

    override var steamGamesCount: Int
        get() = getPref(STEAM_GAMES_COUNT, 0)
        set(value) = setPref(STEAM_GAMES_COUNT, value)

    override var gogGamesCount: Int
        get() = getPref(GOG_GAMES_COUNT, 0)
        set(value) = setPref(GOG_GAMES_COUNT, value)

    override var epicGamesCount: Int
        get() = getPref(EPIC_GAMES_COUNT, 0)
        set(value) = setPref(EPIC_GAMES_COUNT, value)

    override var gogInstalledGamesCount: Int
        get() = getPref(GOG_INSTALLED_GAMES_COUNT, 0)
        set(value) = setPref(GOG_INSTALLED_GAMES_COUNT, value)

    override var epicInstalledGamesCount: Int
        get() = getPref(EPIC_INSTALLED_GAMES_COUNT, 0)
        set(value) = setPref(EPIC_INSTALLED_GAMES_COUNT, value)

    override var amazonInstalledGamesCount: Int
        get() = getPref(AMAZON_INSTALLED_GAMES_COUNT, 0)
        set(value) = setPref(AMAZON_INSTALLED_GAMES_COUNT, value)

    override var librarySteamCollectionsCache: String
        get() = getPref(LIBRARY_STEAM_COLLECTIONS_CACHE, "")
        set(value) = setPref(LIBRARY_STEAM_COLLECTIONS_CACHE, value)

    override var librarySteamCollectionsSkippedDynamic: Boolean
        get() = getPref(LIBRARY_STEAM_COLLECTIONS_SKIPPED_DYNAMIC, false)
        set(value) = setPref(LIBRARY_STEAM_COLLECTIONS_SKIPPED_DYNAMIC, value)

    override var librarySteamCollections: Set<String>
        get() {
            val raw = getPref(LIBRARY_STEAM_COLLECTIONS, "")
            if (raw.isEmpty()) return emptySet()
            return raw.split(COLLECTION_ID_SEPARATOR).filter { it.isNotEmpty() }.toSet()
        }
        set(value) = setPref(LIBRARY_STEAM_COLLECTIONS, value.joinToString(COLLECTION_ID_SEPARATOR))

    override var recommendationCacheJson: String
        get() = getPref(RECOMMENDATION_CACHE_JSON, "")
        set(value) = setPref(RECOMMENDATION_CACHE_JSON, value)

    override var recommendationCacheTimestamp: Long
        get() = getPref(RECOMMENDATION_CACHE_TIMESTAMP, 0L)
        set(value) = setPref(RECOMMENDATION_CACHE_TIMESTAMP, value)

    override var showRecommendations: Boolean
        get() = getPref(SHOW_RECOMMENDATIONS, true)
        set(value) = setPref(SHOW_RECOMMENDATIONS, value)

    override var recDisclosureShown: Boolean
        get() = recDisclosureShownCache
            ?: getPref(REC_DISCLOSURE_SHOWN, false).also { recDisclosureShownCache = it }
        set(value) {
            recDisclosureShownCache = value
            setPref(REC_DISCLOSURE_SHOWN, value)
        }

    override var recTeaserDismissedDay: Long
        get() = getPref(REC_TEASER_DISMISSED_DAY, 0L)
        set(value) = setPref(REC_TEASER_DISMISSED_DAY, value)

    override var showAddCustomGameDialog: Boolean
        get() = getPref(SHOW_ADD_CUSTOM_GAME_DIALOG, true)
        set(value) = setPref(SHOW_ADD_CUSTOM_GAME_DIALOG, value)

    override var importCustomGameAsSteamGame: Boolean
        get() = getPref(IMPORT_CUSTOM_GAME_AS_STEAM_GAME, false)
        set(value) = setPref(IMPORT_CUSTOM_GAME_AS_STEAM_GAME, value)

    override var customGamePaths: Set<String>
        get() {
            val value = getPref(CUSTOM_GAME_PATHS, "[]")
            return try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) = setPref(CUSTOM_GAME_PATHS, Json.encodeToString(value))

    override var customGameManualFolders: Set<String>
        get() {
            val value = getPref(CUSTOM_GAME_MANUAL_FOLDERS, "[]")
            return try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) = setPref(CUSTOM_GAME_MANUAL_FOLDERS, Json.encodeToString(value))

    override var favoriteAppIds: Set<String>
        get() {
            val value = getPref(FAVORITE_APP_IDS, "[]")
            return try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                Timber.w(e, "Failed to decode favorite app ids; falling back to empty set")
                emptySet()
            }
        }
        set(value) {
            val version = synchronized(favoritePersistenceLock) {
                favoritePersistenceVersion += 1
                favoritePersistenceVersion
            }
            scope.launch {
                val serialized = Json.encodeToString(value)
                dataStore.edit { pref ->
                    val isLatest = synchronized(favoritePersistenceLock) {
                        version == favoritePersistenceVersion
                    }
                    if (isLatest) {
                        pref[FAVORITE_APP_IDS] = serialized
                    }
                }
            }
        }

    override var gogAmazonPathMigrated: Boolean
        get() = getPref(GOG_AMAZON_PATH_MIGRATED, false)
        set(value) = setPref(GOG_AMAZON_PATH_MIGRATED, value)

    override val showRecommendationsFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_RECOMMENDATIONS] ?: true }
        .distinctUntilChanged()

    override val favoriteAppIdsFlow: Flow<Set<String>> = dataStore.data
        .map { pref ->
            val value = pref[FAVORITE_APP_IDS] ?: "[]"
            try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                emptySet()
            }
        }
        .distinctUntilChanged()

    override val libraryLayoutFlow: Flow<PaneType> = dataStore.data
        .map { pref ->
            val value = pref[LIBRARY_LAYOUT] ?: PaneType.UNDECIDED.ordinal
            PaneType.entries.getOrNull(value) ?: PaneType.UNDECIDED
        }
        .distinctUntilChanged()

    override val libraryFilterFlow: Flow<EnumSet<AppFilter>> = dataStore.data
        .map { pref ->
            val value = pref[LIBRARY_FILTER] ?: AppFilter.toFlags(EnumSet.of(AppFilter.GAME, AppFilter.SHARED))
            AppFilter.fromFlags(value)
        }
        .distinctUntilChanged()

    override val librarySortOptionFlow: Flow<SortOption> = dataStore.data
        .map { pref ->
            val keyValue = pref[LIBRARY_SORT_KEY] ?: ""
            if (keyValue.isNotEmpty()) {
                SortOption.fromKey(keyValue)
            } else {
                val ordinal = pref[LIBRARY_SORT_LEGACY] ?: SortOption.INSTALLED_FIRST.ordinal
                @Suppress("DEPRECATION")
                SortOption.fromOrdinal(ordinal)
            }
        }
        .distinctUntilChanged()

    override val showSteamInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_STEAM_IN_LIBRARY] ?: true }
        .distinctUntilChanged()

    override val showCustomGamesInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_CUSTOM_GAMES_IN_LIBRARY] ?: true }
        .distinctUntilChanged()

    override val showGOGInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_GOG_IN_LIBRARY] ?: true }
        .distinctUntilChanged()

    override val showEpicInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_EPIC_IN_LIBRARY] ?: true }
        .distinctUntilChanged()

    override val showAmazonInLibraryFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[SHOW_AMAZON_IN_LIBRARY] ?: true }
        .distinctUntilChanged()
}
