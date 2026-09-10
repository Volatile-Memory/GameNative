package app.gamenative.service.epic

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.core.coroutines.IoDispatcher
import app.gamenative.data.DownloadInfo
import app.gamenative.data.EpicCredentials
import app.gamenative.data.EpicGame
import app.gamenative.data.EpicGameToken
import app.gamenative.data.LaunchInfo
import app.gamenative.data.LibraryItem
import app.gamenative.db.dao.EpicGameDao
import app.gamenative.enums.Marker
import app.gamenative.events.AndroidEvent
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.MarkerUtils
import app.gamenative.utils.Net
import app.gamenative.utils.sanitizeForFilename
import com.winlator.container.Container
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * EpicManager handles Epic Games library management
 */
@Singleton
class EpicManager @Inject constructor(
    private val epicGameDao: EpicGameDao,
    private val downloadPreferences: DownloadPreferences,
    @ApplicationContext private val context: Context,
    private val epicDownloadManagerProvider: Provider<EpicDownloadManager>,
    private val epicOverlayManagerProvider: Provider<EpicOverlayManager>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val REFRESH_BATCH_SIZE = 10

    // Deployment ID cache TTL — deployment IDs rarely change, but a periodic re-probe
    // gives automatic recovery from any poisoned cache entry (stale negative, truncated
    // value, etc.) without requiring manual intervention.
    private val DEPLOYMENT_ID_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000  // 30 days

    private val httpClient = Net.http

    private fun getCdnClient(): okhttp3.OkHttpClient {
        val parallelDownloads = downloadPreferences.downloadSpeed.coerceAtLeast(1)
        return Net.httpForParallelDownloads(parallelDownloads)
    }

    data class EpicAssetList(
        val appName: String,
        val labelName: String,
        val buildVersion: String,
        val catalogItemId: String,
        val namespace: String,
        val assetId: String,
        val metadata: AssetMetadata?,
    )

    data class AssetMetadata(
        val installationPoolId: String,
        val update_type: String,
    )

    data class EpicLibraryItem(
        val namespace: String,
        val catalogItemId: String?,
        val appName: String,
        val country: String?,
        val platform: List<String>?,
        val productId: String,
        val sandboxName: String,
        val sandboxType: String,
        val recordType: String?,
        val acquisitionDate: String?,
        val dependencies: List<String>?,
    )

    data class ParsedLibraryItem(
        val appName: String,
        val namespace: String,
        val catalogItemId: String,
        val sandboxType: String?,
        val country: String?,
    )

    data class LibraryItemsResponse(
        val responseMetadata: ResponseMetadata,
        val records: List<EpicLibraryItem>?,
    )

    data class ResponseMetadata(
        val nextCursor: String?,
        val stateToken: String?,
    )

    data class ManifestSizes(
        val installSize: Long,
        val downloadSize: Long,
    )

    // Usually consists of DieselGameBox and DieselGameBoxTall that we can use.
    data class EpicKeyImage(
        val type: String,
        val url: String, // Full URL of the game art.
        val md5: String?,
        val width: Int?,
        val height: Int?,
        val size: Int?,
        val uploadedDate: String?, // "2019-12-19T21:54:10.003Z"
    )

    data class EpicCategory(
        val path: String,
    )

    data class EpicCustomAttributeValue(
        val type: String,
        val value: String,
    )

    // Custom Attributes from the payload.
    data class EpicCustomAttributes(
        val canRunOffline: Boolean = false,
        val ownershipToken: Boolean = false,
        val cloudSaveFolder: String? = null,
        val cloudIncludeList: String? = null,
        val neverUpdate: Boolean = false,
        val folderName: String? = null,
        val presenceId: String? = null,
        val monitorPresence: Boolean = false,
        val useAccessControl: Boolean = false,
        val canSkipKoreanIdVerification: Boolean = true,
        val partnerLinkType: String? = null, // Ubisoft
        val thirdPartyManagedProvider: String? = null, // UbisoftConnect
        val thirdPartyManagedApp: String? = null, // The EA App | Origin
        val partnerLinkId: String? = null,
        val backgroundProcessName: String? = null,
        val registryPath: String? = null,
        val registryLocation: String? = null,
        val registryKey: String? = null,
        val additionalCommandline: String? = null,
        val processNames: String? = null,
        val gameId: String? = null,
        val executableName: String? = null,
    )

    data class EpicReleaseInfo(
        val id: String,
        val appId: String,
        val platform: List<String>?,
        val dateAdded: String?,
        val releaseNote: String?,
        val versionTitle: String?,
    )

    data class EpicMainGameItem(
        val id: String,
        val namespace: String,
    )

    data class GameInfoResponse(
        val id: String,
        val title: String,
        val description: String,
        val keyImages: List<EpicKeyImage>,
        val categories: List<EpicCategory>,
        val namespace: String,
        val status: String?,
        val creationDate: String?, // "2025-03-04T08:39:07.841Z",
        val lastModifiedDate: String?, // "2025-03-06T07:37:16.597Z",
        val customAttributes: EpicCustomAttributes?,
        val entitlementName: String?,
        val entitlementType: String?,
        val itemType: String?,
        val releaseInfo: EpicReleaseInfo,
        val developer: String,
        val developerId: String?,
        val eulaIds: List<String>?,
        val endOfSupport: Boolean?,
        val mainGameItemList: List<String>?,
        val ageGatings: Map<String, Int>?,
        val applicationId: String?,
        val baseAppName: String?,
        val baseProductId: String?,
        val mainGameItem: EpicMainGameItem?,
    )

    /**
     * Refresh the entire library (called manually by user or after login)
     * Fetches all games from Epic via Legendary and updates the database
     */
    suspend fun refreshLibrary(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!EpicAuthManager.hasStoredCredentials(context)) {
                Timber.w("Cannot refresh library: not authenticated with Epic")
                return@withContext Result.failure(Exception("Not authenticated with Epic"))
            }

            Timber.tag("Epic").i("Refreshing Epic library from Epic API...")

            // Get a list of basic info for each game.
            val listResult = fetchLibrary(context)

            if (listResult.isFailure) {
                val error = listResult.exceptionOrNull()
                Timber.tag("Epic").e(error, "Failed to fetch games from Epic: ${error?.message}")
                return@withContext Result.failure(error ?: Exception("Failed to fetch Epic library"))
            }

            val gamesList = listResult.getOrNull() ?: emptyList()
            Timber.tag("Epic").i("Successfully fetched ${gamesList.size} games from Epic")

            if (gamesList.isEmpty()) {
                Timber.tag("Epic").w("No games found in Epic library")
                return@withContext Result.success(0)
            }

            // Re-fetch catalog metadata for every game so customAttributes-derived
            // fields (requiresOT, canRunOffline, ...) backfill on existing rows.
            // Hoist credential read out of the per-game loop — refreshing once is
            // sufficient since a library scan completes in seconds and the token
            // has a 5-minute expiry buffer in getStoredCredentials.
            val credentialsResult = EpicAuthManager.getStoredCredentials(context)
            val accessToken = credentialsResult.getOrNull()?.accessToken
            if (credentialsResult.isFailure || accessToken.isNullOrEmpty()) {
                val error = credentialsResult.exceptionOrNull() ?: Exception("No access token")
                Timber.tag("Epic").e(error, "Cannot refresh library: ${error.message}")
                return@withContext Result.failure(error)
            }

            val epicGames = mutableListOf<EpicGame>()
            var processedCount = 0
            for ((index, game) in gamesList.withIndex()) {
                val result = fetchGameInfo(context, game, accessToken)

                if (result.isSuccess) {
                    val epicGame = result.getOrNull()
                    if (epicGame != null) {
                        epicGames.add(epicGame)
                        processedCount++
                        Timber.tag("Epic").d("Refreshed Game: ${epicGame.title}")
                    }
                } else {
                    Timber.tag("Epic").w("Epic game ${game.appName} could not be fetched")
                }

                if ((index + 1) % REFRESH_BATCH_SIZE == 0 || index == gamesList.lastIndex) {
                    if (epicGames.isNotEmpty()) {
                        epicGameDao.upsertPreservingInstallStatus(epicGames)
                        Timber.tag("Epic").d("Batch inserted ${epicGames.size} games (processed ${index + 1}/${gamesList.size})")
                        epicGames.clear()
                    }
                }
            }

            Timber.tag("Epic").i("Successfully refreshed Epic library")
            Result.success(processedCount)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh Epic library")
            Result.failure(e)
        }
    }

    /**
     *
     * Returns list of library items with app names, namespaces, and catalog IDs
     */
    suspend fun fetchLibrary(context: Context): Result<List<ParsedLibraryItem>> = withContext(Dispatchers.IO) {
        try {
            // Get Credentials and restore them
            val credentials = EpicAuthManager.getStoredCredentials(context)
            if (credentials.isFailure) {
                return@withContext Result.failure(credentials.exceptionOrNull() ?: Exception("No credentials"))
            }

            val accessToken = credentials.getOrNull()?.accessToken
            if (accessToken.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No access token"))
            }

            val gameList = mutableListOf<ParsedLibraryItem>()
            var cursor: String? = null

            // Fetch all pages of library items
            do {
                val url = buildString {
                    append("${EpicConstants.EPIC_LIBRARY_API_URL}?includeMetadata=true")
                    if (cursor != null) {
                        append("&cursor=$cursor")
                    }
                }

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .header("User-Agent", EpicConstants.EPIC_USER_AGENT)
                    .get()
                    .build()

                Timber.tag("Epic").d("Fetching Epic library page: cursor=$cursor")

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    val error = response.body?.string() ?: "Unknown error"
                    Timber.tag("Epic").e("Library fetch failed: ${response.code} - $error")
                    return@withContext Result.failure(Exception("HTTP ${response.code}: $error"))
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    Timber.tag("Epic").e("Empty response body from library API")
                    return@withContext Result.failure(Exception("Empty response"))
                }

                val json = JSONObject(body)
                val records = json.optJSONArray("records") ?: JSONArray()

                Timber.tag("Epic").d("Received ${records.length()} library items in this page")

                // Process records and fetch game info for each
                for (i in 0 until records.length()) {
                    val record = records.getJSONObject(i)

                    // Skip items without app name
                    if (!record.has("appName")) {
                        continue
                    }

                    val appName = record.getString("appName")
                    val namespace = record.getString("namespace")
                    val catalogItemId = record.getString("catalogItemId")
                    val sandboxType = record.optString("sandboxType", "")
                    val country = record.optString("country", "")
                    val platformsArray = record.optJSONArray("platform")
                    val platforms = buildList {
                        if (platformsArray != null) {
                            for (j in 0 until platformsArray.length()) {
                                add(platformsArray.getString(j))
                            }
                        }
                    }

                    // Skip UE assets, private sandboxes, and broken entries
                    if (namespace == "ue" || sandboxType == "PRIVATE" || appName == "1") {
                        Timber.tag("Epic").d("Skipping due to invalid app: $appName (namespace=$namespace, sandbox=$sandboxType)")
                        continue
                    }

                    // Skip invalid platform (such as Android versions)
                    if(platforms.isNotEmpty() && !platforms.contains("Win32") && !platforms.contains("Windows")){
                        Timber.tag("Epic").d("Skipping due to invalid platform: $appName (namespace=$namespace, sandbox=$sandboxType)")
                        continue
                    }

                    // Add the basic game to the gameList.
                    val gameInfo = ParsedLibraryItem(appName, namespace, catalogItemId, sandboxType, country)
                    gameList.add(gameInfo)
                }
                // Get cursor for next page - stop if cursor is null or same as previous
                val metadata = json.optJSONObject("responseMetadata")
                val oldCursor = cursor
                cursor = metadata?.optString("nextCursor")?.takeIf { it.isNotEmpty() }
            } while (cursor != null && cursor != oldCursor)

            Timber.tag("Epic").i("Successfully fetched ${gameList.size} games from Epic library")
            Result.success(gameList)
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "Failed to fetch Epic library")
            Result.failure(e)
        }
    }

    /**
     * Resolves the effective launch executable for an Epic game.
     * Returns empty string if game is not installed or no executable can be found.
     */
    suspend fun getLaunchExecutable(appId: Int): String {
        return getInstalledExe(appId)
    }

    suspend fun getInstalledExe(appId: Int): String {
        // Strip EPIC_ prefix to get the raw Epic app name
        val game = getGameById(appId)
        if (game == null || !game.isInstalled || game.installPath.isEmpty()) {
            Timber.tag("Epic").e("Game not installed: $appId")
            return ""
        }

        // For now, return the install path - actual executable detection would require
        // parsing the game's launch manifest or config files
        // Most Epic games have a .exe in the root or Binaries folder
        val installDir = File(game.installPath)
        if (!installDir.exists()) {
            Timber.tag("Epic").e("Install directory does not exist: ${game.installPath}")
            return ""
        }

        // Try to find the main executable
        // Common patterns: Game.exe, GameName.exe, or in Binaries/Win64/
        val exeFiles = installDir.walk()
            .filter { it.extension.equals("exe", ignoreCase = true) }
            .filter { !it.name.contains("UnityCrashHandler", ignoreCase = true) }
            .filter { !it.name.contains("UnrealCEFSubProcess", ignoreCase = true) }
            .sortedBy { it.absolutePath.length } // Prefer shorter paths (usually main exe)
            .toList()

        val mainExe = exeFiles.firstOrNull()
        if (mainExe != null) {
            Timber.tag("Epic").i("Found executable: ${mainExe.absolutePath}")
            return mainExe.relativeTo(installDir).path
        }

        Timber.tag("Epic").w("No executable found in ${game.installPath}")
        return ""
    }

    private suspend fun fetchGameInfo(
        context: Context,
        game: ParsedLibraryItem,
        accessToken: String,
    ): Result<EpicGame> = withContext(Dispatchers.IO) {
        try {
            // ! We should expertiment with the country to see what affects language downloads
            val gameData = fetchCatalogItem(
                namespace = game.namespace,
                catalogItemId = game.catalogItemId,
                accessToken = accessToken,
                country = game.country ?: "US",
                includeDLCDetails = true,
                includeMainGameDetails = true,
            ) ?: return@withContext Result.failure(Exception("Game data not found in response"))

            Result.success(parseGameFromCatalog(gameData, game.appName))
        } catch (e: Exception) {
            Timber.w(e, "Error fetching game info for ${game.catalogItemId}")
            Result.failure(e)
        }
    }

    /**
     * GET a single item from `/shared/namespace/{ns}/bulk/items` and return the
     * per-`catalogItemId` JSONObject (or null if the request failed / item missing).
     * Caller must be on a background dispatcher; the underlying HTTP call is blocking.
     */
    private fun fetchCatalogItem(
        namespace: String,
        catalogItemId: String,
        accessToken: String,
        country: String = "US",
        includeDLCDetails: Boolean = false,
        includeMainGameDetails: Boolean = false,
    ): JSONObject? {
        val params = buildString {
            append("?id=").append(catalogItemId)
            if (includeDLCDetails) append("&includeDLCDetails=true")
            if (includeMainGameDetails) append("&includeMainGameDetails=true")
            append("&country=").append(country)
        }
        val url = "${EpicConstants.EPIC_CATALOG_API_URL}/shared/namespace/$namespace/bulk/items$params"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", EpicConstants.EPIC_USER_AGENT)
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.tag("Epic").w("Catalog fetch failed for $namespace:$catalogItemId: ${response.code}")
                return@use null
            }
            val body = response.body?.string()
            if (body.isNullOrEmpty()) return@use null
            JSONObject(body).optJSONObject(catalogItemId)
        }
    }

    /**
     * Parse customAttributes object from Epic catalog API
     */
    private fun parseCustomAttributes(customAttributesJson: JSONObject?): EpicCustomAttributes {
        if (customAttributesJson == null) {
            return EpicCustomAttributes()
        }

        // Helper function to extract value from attribute object
        fun getAttribute(name: String): String? {
            val attrObj = customAttributesJson.optJSONObject(name)
            return attrObj?.optString("value")?.takeIf { it.isNotEmpty() }
        }

        // Helper function to parse boolean attributes
        fun getBooleanAttribute(name: String, default: Boolean = false): Boolean {
            val value = getAttribute(name)
            return when (value?.lowercase()) {
                "true" -> true
                "false" -> false
                else -> default
            }
        }

        return EpicCustomAttributes(
            canRunOffline = getBooleanAttribute("CanRunOffline", false),
            ownershipToken = getBooleanAttribute("OwnershipToken", false),
            cloudSaveFolder = getAttribute("CloudSaveFolder"),
            cloudIncludeList = getAttribute("CloudIncludeList"),
            folderName = getAttribute("FolderName"),
            presenceId = getAttribute("PresenceId"),
            monitorPresence = getBooleanAttribute("MonitorPresence", false),
            useAccessControl = getBooleanAttribute("UseAccessControl", false),
            canSkipKoreanIdVerification = getBooleanAttribute("CanSkipKoreanIdVerification", true),
            thirdPartyManagedApp = getAttribute("ThirdPartyManagedApp"),
            thirdPartyManagedProvider = getAttribute("ThirdPartyManagedProvider"),
            partnerLinkType = getAttribute("partnerLinkType"),
            additionalCommandline = getAttribute("AdditionalCommandLine"),
            executableName = getAttribute("MainWindowProcessName"),
        )
    }

    /**
     * Parse Epic catalog JSON into EpicGame object
     */
    internal fun parseGameFromCatalog(data: JSONObject, libraryAppName: String): EpicGame {
        val catalogItemId = data.getString("id")
        val namespace = data.getString("namespace")
        val title = data.getString("title")
        val description = data.optString("description", "")

        val appName = libraryAppName

        val keyImages = data.optJSONArray("keyImages")
        var artCover = "" // DieselGameBoxTall - Tall cover art
        var artSquare = "" // DieselGameBox - Square box art
        var artLogo = "" // DieselGameBoxLogo - Logo image
        var artPortrait = "" // DieselStoreFrontWide - Wide banner

        if (keyImages != null) {
            for (i in 0 until keyImages.length()) {
                val img = keyImages.getJSONObject(i)
                val imgType = img.optString("type")
                val imgUrl = img.optString("url", "")

                when (imgType) {
                    "DieselGameBoxTall" -> artCover = imgUrl
                    "DieselGameBox" -> artSquare = imgUrl
                    "DieselGameBoxLogo" -> artLogo = imgUrl
                    "DieselStoreFrontWide" -> artPortrait = imgUrl
                    "Thumbnail" -> if (artSquare.isEmpty()) artSquare = imgUrl
                }
            }
        }

        // Check if this is DLC
        val isDLC = data.has("mainGameItem")
        val baseGameAppName = if (isDLC) {
            data.optJSONObject("mainGameItem")?.optString("id", "") ?: ""
        } else {
            ""
        }

        // Get developer/publisher
        val developer = data.optString("developer", "")

        // Get categories to check for mods
        val categories = data.optJSONArray("categories")
        var isMod = false
        if (categories != null) {
            for (i in 0 until categories.length()) {
                val cat = categories.getJSONObject(i)
                if (cat.optString("path") == "mods") {
                    isMod = true
                    break
                }
            }
        }

        // Release date - convert to string format
        val releaseInfo = data.optJSONArray("releaseInfo")
        var releaseDate = ""
        if (releaseInfo != null && releaseInfo.length() > 0) {
            val release = releaseInfo.getJSONObject(0)
            releaseDate = release.optString("dateAdded", "")
        }
        // Parse genres/tags from categories
        val genresList = mutableListOf<String>()
        val tagsList = mutableListOf<String>()
        if (categories != null) {
            for (i in 0 until categories.length()) {
                val cat = categories.getJSONObject(i)
                val path = cat.optString("path", "")
                if (path.startsWith("games/")) {
                    genresList.add(path.removePrefix("games/"))
                } else if (path.isNotEmpty() && path != "mods") {
                    tagsList.add(path)
                }
            }
        }

        // Parse custom attributes for cloud saves and offline support
        val parsedAttributes = parseCustomAttributes(data.optJSONObject("customAttributes"))
        val canRunOffline = parsedAttributes.canRunOffline
        val requiresOwnershipToken = parsedAttributes.ownershipToken
        val cloudSaveEnabled = !parsedAttributes.cloudSaveFolder.isNullOrEmpty()
        val saveFolder = parsedAttributes.cloudSaveFolder ?: ""
        val executable = parsedAttributes.executableName ?: ""
        val thirdPartyApp = listOfNotNull(
            parsedAttributes.thirdPartyManagedApp,
            parsedAttributes.thirdPartyManagedProvider,
            parsedAttributes.partnerLinkType,
        ).firstOrNull() ?: ""

        val isEaManaged = if (parsedAttributes.thirdPartyManagedApp != null &&
            parsedAttributes.thirdPartyManagedApp.lowercase() in listOf("origin", "the ea app")
        ) {
            true
        } else {
            false
        }

        Timber.d("Game $appName - CloudSaveFolder: $saveFolder, CloudIncludeList: ${parsedAttributes.cloudIncludeList}, CanRunOffline: $canRunOffline")

        return EpicGame(
            id = 0, // Auto-generated by Room
            catalogId = catalogItemId,
            appName = appName,
            title = title,
            namespace = namespace,
            developer = developer,
            publisher = "",
            description = description,
            artCover = artCover,
            artSquare = artSquare,
            artLogo = artLogo,
            artPortrait = artPortrait,
            isDLC = isDLC,
            baseGameAppName = baseGameAppName,
            releaseDate = releaseDate,
            genres = genresList,
            tags = tagsList,
            isInstalled = false, // Will be updated from local database
            installPath = "",
            platform = "Windows",
            version = "",
            executable = executable,
            installSize = 0,
            downloadSize = 0,
            canRunOffline = canRunOffline,
            requiresOT = requiresOwnershipToken,
            cloudSaveEnabled = cloudSaveEnabled,
            saveFolder = saveFolder,
            thirdPartyManagedApp = thirdPartyApp,
            isEAManaged = isEaManaged,
            lastPlayed = 0,
            playTime = 0,
        )
    }

    suspend fun deleteAllNonInstalledGames() {
        withContext(Dispatchers.IO) {
            epicGameDao.deleteAllNonInstalledGames()
        }
    }


    /**
     * Get a single game by ID
     */
    suspend fun getGamesById(gameIds: List<Int>): List<EpicGame> {
        return withContext(Dispatchers.IO) {
            try {
                epicGameDao.getGamesById(gameIds)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Epic games by IDs: ${gameIds.size}")
                emptyList()
            }
        }
    }

    /**
     * Get a single game by ID
     */
    suspend fun getGameById(appId: Int): EpicGame? {
        return withContext(Dispatchers.IO) {
            try {
                epicGameDao.getById(appId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Epic game by ID: $appId")
                null
            }
        }
    }

    suspend fun getDLCForTitle(appId: Int): List<EpicGame> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.tag("Epic").i("Getting DLC for appId: $appId")
                epicGameDao.getDLCForTitle(appId).firstOrNull() ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get DLC for app name: $appId")
                emptyList()
            }
        }
    }

    /**
     * Get a single game by app name (Legendary identifier)
     */
    suspend fun getGameByAppName(appName: String): EpicGame? {
        return withContext(Dispatchers.IO) {
            try {
                epicGameDao.getByAppName(appName)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Epic game by app name: $appName")
                null
            }
        }
    }

    suspend fun insertGame(game: EpicGame) {
        withContext(Dispatchers.IO) {
            epicGameDao.insert(game)
        }
    }

    suspend fun updateGame(game: EpicGame) {
        withContext(Dispatchers.IO) {
            epicGameDao.update(game)
        }
    }

    suspend fun uninstall(appId: Int) {
        withContext(Dispatchers.IO) {
            epicGameDao.uninstall(appId)
        }
    }

    suspend fun getNonInstalledGames(): List<EpicGame> {
        return withContext(Dispatchers.IO) {
            epicGameDao.getNonInstalledGames()
        }
    }

    /**
     * Start background sync (called after login)
     */
    suspend fun startBackgroundSync(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!EpicAuthManager.hasStoredCredentials(context)) {
                Timber.w("Cannot start background sync: no stored credentials")
                return@withContext Result.failure(Exception("No stored credentials found"))
            }

            Timber.tag("Epic").i("Starting Epic library background sync...")

            val result = refreshLibrary(context)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                Timber.tag("Epic").i("Background sync completed: $count games synced")
                Result.success(Unit)
            } else {
                val error = result.exceptionOrNull()
                Timber.e(error, "Background sync failed: ${error?.message}")
                Result.failure(error ?: Exception("Background sync failed"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync Epic library in background")
            Result.failure(e)
        }
    }

    data class ManifestResult(
        val manifestBytes: ByteArray,
        val cdnUrls: List<CdnUrl>,
    )

    data class CdnUrl(
        val baseUrl: String,
        val authQueryParams: String,
        val cloudDir: String = "", // Full build path for chunk downloads
    )

    /**
     * Fetch manifest binary data from Epic API and CDN
     *
     * Returns the raw manifest bytes and CDN base URLs from the API response
     */
    suspend fun fetchManifestFromEpic(
        context: Context,
        namespace: String,
        catalogItemId: String,
        appName: String,
    ): Result<ManifestResult> = withContext(Dispatchers.IO) {
        try {
            // Get credentials
            val credentials = EpicAuthManager.getStoredCredentials(context)
            if (credentials.isFailure) {
                return@withContext Result.failure(credentials.exceptionOrNull() ?: Exception("No credentials"))
            }

            val accessToken = credentials.getOrNull()?.accessToken
            if (accessToken.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No access token"))
            }

            // Fetch manifest URL from Epic API
            val manifestUrl = "${EpicConstants.EPIC_LAUNCHER_API_URL}/launcher/api/public/assets/v2/platform" +
                "/Windows/namespace/$namespace/catalogItem/$catalogItemId/app" +
                "/$appName/label/Live"

            Timber.tag("Epic").d("Fetching manifest metadata from: $manifestUrl")

            val request = Request.Builder()
                .url(manifestUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", EpicConstants.EPIC_USER_AGENT)
                .get()
                .build()

            val manifestJson = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Manifest API request failed: ${response.code}"))
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    return@withContext Result.failure(Exception("Empty manifest API response"))
                }

                JSONObject(body)
            }
            val elements = manifestJson.optJSONArray("elements")

            if (elements == null || elements.length() == 0) {
                return@withContext Result.failure(Exception("No elements in manifest API response"))
            }

            val element = elements.getJSONObject(0)
            val manifests = element.optJSONArray("manifests")

            if (manifests == null || manifests.length() == 0) {
                return@withContext Result.failure(Exception("No manifests in API response"))
            }

            // Extract CDN base URLs from manifest URIs with their auth tokens
            // Each manifest entry represents the same content on a different CDN
            val cdnUrls = mutableListOf<CdnUrl>()
            for (i in 0 until manifests.length()) {
                val manifest = manifests.getJSONObject(i)
                val uri = manifest.getString("uri")

                // Extract base URL (e.g., "https://fastly-download.epicgames.com")
                val baseUrl = uri.substringBefore("/Builds")
                if (baseUrl.isEmpty() || !baseUrl.startsWith("http")) {
                    continue
                }

                // Extract CloudDir (build path) from URI
                // Example: https://fastly-download.epicgames.com/Builds/Org/{org}/{build}/default/...
                // CloudDir: /Builds/Org/{org}/{build}/default
                val cloudDir = if (uri.contains("/Builds")) {
                    val afterBase = uri.substringAfter(baseUrl)
                    val manifestFilename = afterBase.substringAfterLast("/")
                    afterBase.substringBefore("/" + manifestFilename)
                } else {
                    ""
                }

                // Extract authentication query parameters for this CDN
                val queryParams = manifest.optJSONArray("queryParams")
                val authParams = if (queryParams != null && queryParams.length() > 0) {
                    val params = StringBuilder("?")
                    for (j in 0 until queryParams.length()) {
                        val param = queryParams.getJSONObject(j)
                        val name = param.getString("name")
                        val value = param.getString("value")
                        if (j > 0) params.append("&")
                        params.append("$name=$value")
                    }
                    params.toString()
                } else {
                    ""
                }

                cdnUrls.add(CdnUrl(baseUrl, authParams, cloudDir))
            }

            // Error if no CDN URLs could be extracted
            if (cdnUrls.isEmpty()) {
                return@withContext Result.failure(Exception("No CDN URLs found in manifest API response"))
            }

            Timber.tag("Epic").d("Found ${cdnUrls.size} CDN mirrors")

            // Use the first manifest to download the manifest file
            val manifestObj = manifests.getJSONObject(0)
            var manifestUri = manifestObj.getString("uri")

            // Append query parameters (CDN authentication tokens) for manifest download
            val manifestQueryParams = manifestObj.optJSONArray("queryParams")
            if (manifestQueryParams != null && manifestQueryParams.length() > 0) {
                val params = StringBuilder()
                for (i in 0 until manifestQueryParams.length()) {
                    val param = manifestQueryParams.getJSONObject(i)
                    val name = param.getString("name")
                    val value = param.getString("value")
                    if (i == 0) {
                        params.append("?")
                    } else {
                        params.append("&")
                    }
                    params.append("$name=$value")
                }
                manifestUri += params.toString()
            }

            Timber.tag("Epic").d("Downloading manifest binary from: $manifestUri")

            // Manifest downloads from CDN don't need/accept Epic auth tokens
            val manifestRequest = Request.Builder()
                .url(manifestUri)
                .header("User-Agent", EpicConstants.EPIC_USER_AGENT)
                .get()
                .build()

            val manifestBytes = getCdnClient().newCall(manifestRequest).execute().use { manifestResponse ->
                if (!manifestResponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to download manifest binary: ${manifestResponse.code}"))
                }

                val bytes = manifestResponse.body?.bytes()
                if (bytes == null) {
                    return@withContext Result.failure(Exception("Empty manifest bytes from CDN"))
                }

                bytes
            }

            Timber.tag("Epic").d("Manifest fetched with ${cdnUrls.size} CDN URLs")
            Result.success(ManifestResult(manifestBytes, cdnUrls))
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "Exception fetching manifest")
            Result.failure(e)
        }
    }

    /**
     * Fetch the EOS deployment id for a game from the launcher manifest API.
     *
     * Mirrors Legendary's sidecar handling (legendary/core.py, _update_assets_and_meta
     * and get_launch_parameters): the manifest API response contains
     * `elements[0].sidecar.config` as a JSON-encoded string, which carries the
     * game's `deploymentId`.  Passing `-epicdeploymentid=<id>` on the command
     * line is required for modern EOS-integrated games; without it, titles
     * such as "Deliver At All Costs" refuse to start with
     * "Failed to connect to the Epic Launcher".
     *
     * Cached per app-name under [Context.filesDir]/epic/deployment_ids/.
     *
     * @return the deployment id if the game exposes one, otherwise null. Null
     *         is a valid result – most titles do not have a sidecar.
     */
    suspend fun fetchDeploymentId(
        context: Context,
        namespace: String,
        catalogItemId: String,
        appName: String,
        forceRefresh: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.filesDir, "epic/deployment_ids").also { it.mkdirs() }
        val cacheFile = File(cacheDir, "${appName.sanitizeForFilename()}.txt")

        if (!forceRefresh && cacheFile.exists()) {
            val cacheAgeMs = System.currentTimeMillis() - cacheFile.lastModified()
            if (cacheAgeMs < DEPLOYMENT_ID_CACHE_TTL_MS) {
                return@withContext cacheFile.readText().trim().takeIf { it.isNotEmpty() }
            }
            Timber.tag("Epic").d(
                "fetchDeploymentId cache for $appName is stale (age=${cacheAgeMs}ms), refetching",
            )
        }

        try {
            val credentialsResult = EpicAuthManager.getStoredCredentials(context)
            val accessToken = credentialsResult.getOrNull()?.accessToken
            if (accessToken.isNullOrEmpty()) {
                Timber.tag("Epic").w("fetchDeploymentId: no access token")
                return@withContext null
            }

            val manifestUrl = "${EpicConstants.EPIC_LAUNCHER_API_URL}/launcher/api/public/assets/v2/platform" +
                "/Windows/namespace/$namespace/catalogItem/$catalogItemId/app" +
                "/$appName/label/Live"

            val request = Request.Builder()
                .url(manifestUrl)
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", EpicConstants.EPIC_USER_AGENT)
                .get()
                .build()

            val manifestJson = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.tag("Epic").w("fetchDeploymentId: manifest API ${response.code} for $appName")
                    return@withContext null
                }
                val body = response.body?.string()
                if (body.isNullOrEmpty()) return@withContext null
                JSONObject(body)
            }

            val elements = manifestJson.optJSONArray("elements")
            if (elements == null || elements.length() == 0) return@withContext null

            val sidecar = elements.getJSONObject(0).optJSONObject("sidecar")
            // sidecar.config is a JSON-encoded string, NOT a nested JSON object
            val configStr = sidecar?.optString("config", "") ?: ""
            if (configStr.isEmpty()) {
                // Cache negative result to avoid refetching every launch
                cacheFile.writeText("")
                return@withContext null
            }

            val deploymentId = try {
                JSONObject(configStr).optString("deploymentId", "").takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                // Malformed sidecar (transient Epic API hiccup or schema change).
                // Do NOT persist a negative cache here — next launch will retry the parse
                // rather than permanently treating this game as having no deployment id.
                Timber.tag("Epic").w(e, "fetchDeploymentId: failed to parse sidecar.config for $appName")
                return@withContext null
            }

            cacheFile.writeText(deploymentId ?: "")
            Timber.tag("Epic").d("fetchDeploymentId($appName) = ${deploymentId ?: "<none>"}")
            deploymentId
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "Exception fetching deployment id for $appName")
            null
        }
    }

    /**
     * Fetch `customAttributes.AdditionalCommandLine` from the Epic catalog API.
     * Mirrors legendary `Game.additional_command_line`. Cached per app-name with
     * the same TTL as deployment ids.
     */
    suspend fun fetchAdditionalCommandLine(
        context: Context,
        namespace: String,
        catalogItemId: String,
        appName: String,
        forceRefresh: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.filesDir, "epic/additional_cmdline").also { it.mkdirs() }
        val cacheFile = File(cacheDir, "${appName.sanitizeForFilename()}.txt")

        if (!forceRefresh && cacheFile.exists()) {
            val cacheAgeMs = System.currentTimeMillis() - cacheFile.lastModified()
            if (cacheAgeMs < DEPLOYMENT_ID_CACHE_TTL_MS) {
                return@withContext cacheFile.readText().trim().takeIf { it.isNotEmpty() }
            }
        }

        try {
            val credentials = EpicAuthManager.getStoredCredentials(context)
            val accessToken = credentials.getOrNull()?.accessToken
            if (accessToken.isNullOrEmpty()) return@withContext null

            val gameData = fetchCatalogItem(
                namespace = namespace,
                catalogItemId = catalogItemId,
                accessToken = accessToken,
            ) ?: return@withContext null

            val additionalCommandLine = parseCustomAttributes(
                gameData.optJSONObject("customAttributes"),
            ).additionalCommandline

            cacheFile.writeText(additionalCommandLine ?: "")
            additionalCommandLine
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "Exception fetching additional command line for $appName")
            null
        }
    }

    /**
     * Fetch install size for a game by downloading its manifest
     * Manifest is small (~500KB-1MB) and contains all file metadata
     * Returns size in bytes, or 0 if failed
     */
    suspend fun fetchManifestSizes(context: Context, appId: Int): ManifestSizes = withContext(Dispatchers.IO) {
        try {
            // Get the game info to get namespace and catalogItemId
            val game = getGameById(appId)

            if (game == null) {
                Timber.tag("Epic").w("Game not found in database: $game.appName")
                return@withContext ManifestSizes(installSize = 0L, downloadSize = 0L)
            }

            val appName = game.appName

            // Fetch manifest using shared function
            val manifestResult = fetchManifestFromEpic(context, game.namespace, game.catalogId, game.appName)
            if (manifestResult.isFailure) {
                Timber.tag("Epic").w("Failed to fetch manifest: ${manifestResult.exceptionOrNull()?.message}")
                return@withContext ManifestSizes(installSize = 0L, downloadSize = 0L)
            }

            val manifestData = manifestResult.getOrNull()!!

            // Parse with Kotlin parser
            val manifest = app.gamenative.service.epic.manifest.EpicManifest.readAll(manifestData.manifestBytes)

            // Required-only sizes for detail page display (download uses container language via getSizesForSelectedInstallTags elsewhere).
            val (downloadSize, installSize) = app.gamenative.service.epic.manifest.ManifestUtils.getSizesForSelectedInstallTags(manifest, emptyList())
            Timber.tag("Epic").d(
                "Manifest stats for $appName: version=${manifest.version}, featureLevel=${manifest.meta?.featureLevel}, " +
                    "buildVersion=${manifest.meta?.buildVersion}, buildId=${manifest.meta?.buildId}",
            )
            Timber.tag("Epic").d(
                "Manifest stats for $appName: files=${manifest.fileManifestList?.count}, " +
                    "chunks=${manifest.chunkDataList?.count}",
            )
            Timber.tag("Epic").d("Install size for $appName: $installSize bytes")
            Timber.tag("Epic").d("Download size for $appName: $downloadSize bytes")

            return@withContext ManifestSizes(installSize = installSize, downloadSize = downloadSize)
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "Exception fetching install size for appId: $appId")
            ManifestSizes(installSize = 0L, downloadSize = 0L)
        }
    }

    // ==========================================================================
    // State & Download Orchestration (Absorbed from EpicService)
    // ==========================================================================

    private val activeDownloads = ConcurrentHashMap<Int, DownloadInfo>()
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    // State observers for EpicService foreground notifications
    var onSyncStatusChanged: ((Boolean) -> Unit)? = null
    var onDownloadTracked: ((DownloadInfo, String) -> Unit)? = null

    private var syncInProgress: Boolean = false
    private var backgroundSyncJob: Job? = null
    private var lastSyncTimestamp: Long = 0L
    private var hasPerformedInitialSync: Boolean = false
    private val SYNC_THROTTLE_MILLIS = 15 * 60 * 1000L

    fun hasActiveOperations(): Boolean {
        return syncInProgress || backgroundSyncJob?.isActive == true || hasActiveDownload()
    }

    fun isSyncInProgress(): Boolean = syncInProgress

    fun setSyncInProgress(inProgress: Boolean) {
        syncInProgress = inProgress
        onSyncStatusChanged?.invoke(inProgress)
    }

    fun hasActiveDownload(): Boolean = activeDownloads.isNotEmpty()

    fun getCurrentlyDownloadingGame(): Int? = activeDownloads.keys.firstOrNull()

    fun getDownloadInfo(appId: Int): DownloadInfo? = activeDownloads[appId]

    fun getActiveDownloads(): Map<Int, DownloadInfo> = HashMap(activeDownloads)

    fun hasPartialDownload(appId: Int): Boolean = hasPartialDownload(context, appId)

    fun hasPartialDownload(targetContext: Context, appId: Int): Boolean {
        val game = getEpicGameOf(appId) ?: return false
        if (game.isInstalled) return false
        val appName = game.appName.ifBlank { return false }
        val installPath = EpicConstants.getGameInstallPath(targetContext, appName)
        return MarkerUtils.hasPartialInstall(installPath)
    }

    fun getPartialInstallPaths(targetContext: Context = context): Set<String> {
        val roots = buildList {
            add(EpicConstants.internalEpicGamesPath(targetContext))
            if (downloadPreferences.externalStoragePath.isNotBlank()) {
                add(EpicConstants.externalEpicGamesPath(targetContext))
            }
        }.distinct()

        return roots.asSequence()
            .flatMap { root -> MarkerUtils.findResumablePartialInstalls(root).asSequence() }
            .toSet()
    }

    suspend fun getPartialDownloads(): List<Int> = getPartialDownloads(context)

    suspend fun getPartialDownloads(targetContext: Context): List<Int> {
        val partialInstallPaths = getPartialInstallPaths(targetContext)
        if (partialInstallPaths.isEmpty()) return emptyList()

        return getNonInstalledGames()
            .asSequence()
            .filter { game -> !activeDownloads.containsKey(game.id) }
            .filter { game ->
                val appName = game.appName.ifBlank { return@filter false }
                partialInstallPaths.contains(EpicConstants.getGameInstallPath(targetContext, appName))
            }
            .map { it.id }
            .toList()
    }

    suspend fun deleteGame(appId: Int): Result<Unit> = deleteGame(context, appId)

    suspend fun deleteGame(targetContext: Context, appId: Int): Result<Unit> {
        return try {
            val game = getGameById(appId) ?: return Result.failure(Exception("Game not found: $appId"))
            val path = if (game.installPath.isNotEmpty()) game.installPath else EpicConstants.getGameInstallPath(targetContext, game.appName)
            if (File(path).exists()) {
                Timber.tag("Epic").i("Deleting installation folder: $path")
                val deleted = File(path).deleteRecursively()
                if (deleted) {
                    Timber.tag("Epic").i("Successfully deleted installation folder")
                } else {
                    Timber.tag("Epic").w("Failed to delete some files in installation folder")
                }
                MarkerUtils.removeMarker(path, Marker.DOWNLOAD_COMPLETE_MARKER)
                MarkerUtils.removeMarker(path, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
            }

            EpicDownloadManager.chunkCacheDirFor(targetContext, path).deleteRecursively()
            uninstall(appId)

            withContext(Dispatchers.Main) {
                ContainerUtils.deleteContainer(targetContext, "EPIC_${game.id}")
            }

            PluviaApp.events.emitJava(
                AndroidEvent.LibraryInstallStatusChanged(appId, app.gamenative.data.GameSource.EPIC)
            )

            Timber.tag("Epic").i("Game uninstalled: $appId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "Failed to uninstall game: $appId")
            Result.failure(e)
        }
    }

    suspend fun cleanupDownload(appId: Int) = cleanupDownload(context, appId)

    suspend fun cleanupDownload(targetContext: Context, appId: Int) {
        withContext(ioDispatcher) {
            getGameById(appId)?.let { game ->
                val path = EpicConstants.getGameInstallPath(targetContext, game.appName)
                MarkerUtils.removeMarker(path, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
            }
        }
        activeDownloads.remove(appId)
    }

    fun cancelDownload(appId: Int): Boolean {
        val downloadInfo = activeDownloads[appId]
        return if (downloadInfo != null) {
            Timber.tag("EPIC").i("Cancelling download for Epic game: $appId")
            downloadInfo.cancel()
            activeDownloads.remove(appId)
            Timber.tag("EPIC").d("Download cancelled for Epic game: $appId")
            true
        } else {
            Timber.w("No active download found for Epic game: $appId")
            false
        }
    }

    fun isGameInstalled(appId: Int): Boolean = isGameInstalled(context, appId)

    fun isGameInstalled(targetContext: Context, appId: Int): Boolean {
        val game = getEpicGameOf(appId) ?: return false

        if (game.isInstalled && game.installPath.isNotEmpty()) {
            return MarkerUtils.hasMarker(game.installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
        }

        val installPath = game.installPath.takeIf { it.isNotEmpty() }
            ?: game.appName.takeIf { it.isNotEmpty() }?.let {
                EpicConstants.getGameInstallPath(targetContext, it)
            }
            ?: return false

        val isDownloadComplete = MarkerUtils.hasMarker(installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
        val isDownloadInProgress = MarkerUtils.hasMarker(installPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
        if (isDownloadComplete && !isDownloadInProgress) {
            val updatedGame = game.copy(
                isInstalled = true,
                installPath = installPath,
            )
            runBlocking(ioDispatcher) {
                updateGame(updatedGame)
            }
            return true
        }

        return false
    }

    fun getInstallPath(appId: Int): String? {
        val game = getEpicGameOf(appId)
        return if (game?.isInstalled == true && game.installPath.isNotEmpty()) {
            game.installPath
        } else {
            null
        }
    }

    fun updateInstallPath(appId: Int, path: String) {
        runBlocking(ioDispatcher) {
            val game = getGameById(appId) ?: return@runBlocking
            if (game.installPath != path) {
                updateGame(game.copy(installPath = path))
            }
        }
    }

    fun getEpicGameOf(appId: Int): EpicGame? {
        return runBlocking(ioDispatcher) {
            getGameById(appId)
        }
    }

    fun getEpicGameByAppName(appName: String): EpicGame? {
        return runBlocking(ioDispatcher) {
            getGameByAppName(appName)
        }
    }

    fun getDLCForGame(appId: Int): List<EpicGame> {
        return runBlocking(ioDispatcher) {
            getDLCForTitle(appId)
        }
    }

    suspend fun updateEpicGame(game: EpicGame) {
        updateGame(game)
    }

    suspend fun getLaunchExecutable(containerId: String): String {
        val gameId = try {
            ContainerUtils.extractGameIdFromContainerId(containerId)
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "Failed to parse Epic containerId: $containerId")
            return ""
        }
        return getLaunchExecutable(gameId)
    }

    fun downloadGame(
        appId: Int,
        dlcGameIds: List<Int>,
        installPath: String,
        containerLanguage: String,
    ): Result<DownloadInfo> = downloadGame(context, appId, dlcGameIds, installPath, containerLanguage)

    fun downloadGame(
        targetContext: Context,
        appId: Int,
        dlcGameIds: List<Int>,
        installPath: String,
        containerLanguage: String,
    ): Result<DownloadInfo> {
        val game = runBlocking(ioDispatcher) { getGameById(appId) }
            ?: return Result.failure(Exception("Game not found for appId: $appId"))
        val gameId = game.id ?: return Result.failure(Exception("Game ID not found for appId: $appId"))

        if (activeDownloads.containsKey(appId)) {
            Timber.tag("Epic").w("Download already in progress for $appId")
            return Result.success(activeDownloads[appId]!!)
        }

        val downloadInfo = DownloadInfo(
            jobCount = 1,
            gameId = appId,
            downloadingAppIds = CopyOnWriteArrayList<Int>(),
        )
        downloadInfo.setPersistencePath(installPath)

        val persistedBytes = downloadInfo.loadPersistedBytesDownloaded(installPath)
        if (persistedBytes > 0L) {
            downloadInfo.initializeBytesDownloaded(persistedBytes)
        }

        activeDownloads[appId] = downloadInfo
        downloadInfo.setActive(true)
        onDownloadTracked?.invoke(downloadInfo, game.title ?: "")

        val job = scope.launch {
            try {
                val commonRedistDir = File(installPath, "_CommonRedist")
                Timber.tag("Epic").i("Starting download for game: ${game.title}, gameId: ${game.id}")

                val result = epicDownloadManagerProvider.get().downloadGame(
                    targetContext,
                    game,
                    installPath,
                    downloadInfo,
                    containerLanguage,
                    dlcGameIds,
                    commonRedistDir,
                )

                Timber.tag("Epic").d("Download result: ${if (result.isSuccess) "SUCCESS" else "FAILURE: ${result.exceptionOrNull()?.message}"}")

                if (result.isSuccess) {
                    Timber.i("[Download] Completed successfully for game $gameId")

                    val epicAppId = "EPIC_$gameId"
                    if (game.cloudSaveEnabled && !ContainerUtils.isLocalSavesOnly(targetContext, epicAppId)) {
                        downloadInfo.setPostInstallSyncing(true)
                        PluviaApp.events.emit(AndroidEvent.PostInstallSyncStatusChanged(gameId, true))
                        downloadInfo.updateStatusMessage("Syncing saves...")
                        try {
                            EpicCloudSavesManager.syncCloudSaves(
                                context = targetContext,
                                appId = gameId,
                                preferredAction = "download",
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "[PostInstallSync] Cloud save sync failed for game $gameId")
                        } finally {
                            downloadInfo.setPostInstallSyncing(false)
                            downloadInfo.updateStatusMessage(null)
                            PluviaApp.events.emit(AndroidEvent.PostInstallSyncStatusChanged(gameId, false))
                        }
                    }

                    SnackbarManager.show("Download completed successfully!")
                    downloadInfo.setProgress(1.0f)
                    downloadInfo.setActive(false)
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e(error, "[Download] Failed for game $gameId")
                    downloadInfo.setProgress(-1.0f)
                    downloadInfo.setActive(false)

                    SnackbarManager.show("Download failed: ${error?.message ?: "Unknown error"}")
                }
            } catch (e: CancellationException) {
                downloadInfo.setPostInstallSyncing(false)
                downloadInfo.updateStatusMessage(null)
                PluviaApp.events.emit(AndroidEvent.PostInstallSyncStatusChanged(gameId, false))
                throw e
            } catch (e: Exception) {
                Timber.e(e, "[Download] Exception for game $gameId")
                downloadInfo.setPostInstallSyncing(false)
                downloadInfo.updateStatusMessage(null)
                PluviaApp.events.emit(AndroidEvent.PostInstallSyncStatusChanged(gameId, false))
                downloadInfo.setProgress(-1.0f)
                downloadInfo.setActive(false)

                SnackbarManager.show("Download error: ${e.message ?: "Unknown error"}")
            } finally {
                activeDownloads.remove(appId)
                Timber.d("[Download] Finished for game $gameId, progress: ${downloadInfo.getProgress()}, active: ${downloadInfo.isActive()}")
            }
        }
        downloadInfo.setDownloadJob(job)
        return Result.success(downloadInfo)
    }

    suspend fun refreshSingleGame(appId: Int, targetContext: Context = context): Result<EpicGame?> {
        val game = getGameById(appId)
        return if (game != null) {
            Result.success(game)
        } else {
            Result.failure(Exception("Game not found: $appId"))
        }
    }

    suspend fun getGameLaunchToken(
        namespace: String? = null,
        catalogItemId: String? = null,
        requiresOwnershipToken: Boolean = false,
        targetContext: Context = context,
    ): Result<EpicGameToken> {
        return EpicAuthManager.getGameLaunchToken(targetContext, namespace, catalogItemId, requiresOwnershipToken)
    }

    suspend fun buildLaunchParameters(
        container: Container,
        game: EpicGame,
        offline: Boolean = false,
        languageCode: String = "en-US",
        targetContext: Context = context,
    ): Result<List<String>> {
        return EpicGameLauncher.buildLaunchParameters(targetContext, container, game, offline, languageCode)
    }

    fun cleanupLaunchTokens(container: Container? = null, targetContext: Context = context) {
        EpicGameLauncher.cleanupOwnershipTokens(targetContext, container)
    }

    suspend fun installOverlay(
        container: Container,
        forceReinstall: Boolean = false,
        onProgress: ((Int, Int) -> Unit)? = null,
        targetContext: Context = context,
    ): Result<Unit> {
        return epicOverlayManagerProvider.get().installOverlay(
            targetContext, container, forceReinstall, onProgress,
        )
    }

    suspend fun removeOverlay(container: Container, targetContext: Context = context): Result<Unit> {
        return epicOverlayManagerProvider.get().removeOverlay(targetContext, container)
    }

    fun hasStoredCredentials(targetContext: Context = context): Boolean {
        return EpicAuthManager.hasStoredCredentials(targetContext)
    }

    suspend fun getStoredCredentials(targetContext: Context = context): Result<EpicCredentials> {
        return EpicAuthManager.getStoredCredentials(targetContext)
    }

    suspend fun authenticateWithCode(authorizationCode: String, targetContext: Context = context): Result<EpicCredentials> {
        return EpicAuthManager.authenticateWithCode(targetContext, authorizationCode)
    }

    suspend fun logout(targetContext: Context = context): Result<Unit> = withContext(ioDispatcher) {
        try {
            Timber.tag("EPIC").i("Logging out from Epic...")
            val credentialsCleared = EpicAuthManager.clearStoredCredentials(targetContext)
            if (!credentialsCleared) {
                Timber.tag("Epic").e("Failed to clear credentials during logout")
                return@withContext Result.failure(Exception("Failed to clear stored credentials"))
            }

            deleteAllNonInstalledGames()
            Timber.tag("Epic").i("All Non-installed Epic games removed from database")

            Timber.tag("Epic").i("Logout completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "Error during logout")
            Result.failure(e)
        }
    }

    fun getAccountId(targetContext: Context = context): String? {
        return try {
            val credentialsResult = runBlocking(ioDispatcher) {
                EpicAuthManager.getStoredCredentials(targetContext)
            }
            credentialsResult.getOrNull()?.accountId
        } catch (e: Exception) {
            Timber.tag("Epic").e(e, "Failed to get account ID")
            null
        }
    }

    suspend fun startBackgroundSync(): Result<Unit> = startBackgroundSync(context)

    suspend fun refreshLibrary(): Result<Int> = refreshLibrary(context)

    suspend fun fetchManifestSizes(appId: Int): ManifestSizes = fetchManifestSizes(context, appId)
}
