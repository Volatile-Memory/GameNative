package app.gamenative.preferences

import app.gamenative.ui.enums.AppFilter
import app.gamenative.ui.enums.PaneType
import app.gamenative.ui.enums.SortOption
import java.util.EnumSet
import kotlinx.coroutines.flow.Flow

interface LibraryPreferences {
    var libraryLayout: PaneType
    var libraryFilter: EnumSet<AppFilter>
    var librarySortOption: SortOption
    var itemsPerPage: Int
    var showSteamInLibrary: Boolean
    var showCustomGamesInLibrary: Boolean
    var showGOGInLibrary: Boolean
    var showEpicInLibrary: Boolean
    var showAmazonInLibrary: Boolean
    var customGamesCount: Int
    var steamGamesCount: Int
    var gogGamesCount: Int
    var epicGamesCount: Int
    var gogInstalledGamesCount: Int
    var epicInstalledGamesCount: Int
    var amazonInstalledGamesCount: Int
    var librarySteamCollectionsCache: String
    var librarySteamCollectionsSkippedDynamic: Boolean
    var librarySteamCollections: Set<String>
    var recommendationCacheJson: String
    var recommendationCacheTimestamp: Long
    var showRecommendations: Boolean
    var recDisclosureShown: Boolean
    var recTeaserDismissedDay: Long
    var showAddCustomGameDialog: Boolean
    var importCustomGameAsSteamGame: Boolean
    var customGamePaths: Set<String>
    var customGameManualFolders: Set<String>
    var favoriteAppIds: Set<String>
    var gogAmazonPathMigrated: Boolean

    val showRecommendationsFlow: Flow<Boolean>
    val favoriteAppIdsFlow: Flow<Set<String>>
    val libraryLayoutFlow: Flow<PaneType>
    val libraryFilterFlow: Flow<EnumSet<AppFilter>>
    val librarySortOptionFlow: Flow<SortOption>
    val showSteamInLibraryFlow: Flow<Boolean>
    val showCustomGamesInLibraryFlow: Flow<Boolean>
    val showGOGInLibraryFlow: Flow<Boolean>
    val showEpicInLibraryFlow: Flow<Boolean>
    val showAmazonInLibraryFlow: Flow<Boolean>
}
