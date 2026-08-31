package app.gamenative.provider

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import app.gamenative.R
import app.gamenative.api.GameNativeApi
import app.gamenative.data.GameSource
import app.gamenative.db.DATABASE_NAME
import app.gamenative.utils.ContainerUtils
import com.winlator.container.Container
import com.winlator.xenvironment.ImageFs
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Exposes game storage (Wine prefix saves and game install directories) to the Android
 * Files app and other DocumentsProvider-aware apps.
 *
 * Each installed game container appears as a separate root, named after the game.
 * Inside each root, users see:
 *   - "Wine Prefix" (.wine/) — contains Windows-convention save locations
 *     (Documents, AppData, Saved Games). Write-enabled for the user profile subtree.
 *   - "Game Files" (A: drive path) — the game installation directory. Read-only.
 */
class GameStorageProvider : DocumentsProvider() {

    companion object {
        private const val TAG = "GameStorageProvider"
        private const val THUMBS_CACHE_DIR = "provider_thumbs"
        private const val THUMB_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000 // 7 days

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_DISPLAY_NAME,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_ICON,
        )

        // The subtree of the Wine prefix where user-writable saves live.
        private val WRITABLE_WINE_SUBPATH =
            ".wine${File.separatorChar}drive_c${File.separatorChar}users${File.separatorChar}${ImageFs.USER}"
    }

    override fun onCreate(): Boolean = true

    // -----------------------------------------------------------------------
    // queryRoots — one root per installed game container
    // -----------------------------------------------------------------------

    override fun queryRoots(projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val ctx = context ?: return result

        val homeDir = File(ImageFs.find(ctx).rootDir, "home")
        if (!homeDir.isDirectory) return result

        homeDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("${ImageFs.USER}-") }
            ?.forEach { containerDir ->
                val containerId = containerDir.name.removePrefix("${ImageFs.USER}-")
                val gameName = resolveGameName(containerId)
                val gameSource = safeExtractSource(containerId)
                val iconRes = platformIconRes(gameSource)

                result.newRow().apply {
                    add(Root.COLUMN_ROOT_ID, containerDir.absolutePath)
                    add(Root.COLUMN_DOCUMENT_ID, containerDir.absolutePath)
                    add(Root.COLUMN_DISPLAY_NAME, gameName)
                    add(Root.COLUMN_SUMMARY, ctx.getString(R.string.provider_root_summary))
                    add(
                        Root.COLUMN_FLAGS,
                        Root.FLAG_SUPPORTS_CREATE or
                            Root.FLAG_SUPPORTS_RECENTS or
                            Root.FLAG_SUPPORTS_SEARCH,
                    )
                    add(Root.COLUMN_ICON, iconRes)
                    add(Root.COLUMN_MIME_TYPES, "*/*")
                }
            }

        return result
    }

    // -----------------------------------------------------------------------
    // queryDocument / queryChildDocuments
    // -----------------------------------------------------------------------

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        includeFile(result, File(documentId))
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?,
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val parentFile = File(parentDocumentId)

        val children: Array<File>? = if (isContainerRootDir(parentDocumentId)) {
            // Curated view: only expose .wine/ and the game install directory (A: drive)
            curatedContainerChildren(parentFile)
        } else {
            parentFile.listFiles()
        }

        children
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { includeFile(result, it) }

        return result
    }

    // -----------------------------------------------------------------------
    // openDocument
    // -----------------------------------------------------------------------

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        return ParcelFileDescriptor.open(File(documentId), ParcelFileDescriptor.parseMode(mode))
    }

    // -----------------------------------------------------------------------
    // openDocumentThumbnail — game cover art for container root folders
    // -----------------------------------------------------------------------

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?,
    ): AssetFileDescriptor? {
        if (!isContainerRootDir(documentId)) return null

        val containerDir = File(documentId)
        val containerId = containerDir.name.removePrefix("${ImageFs.USER}-")

        val thumbDir = File(context!!.cacheDir, THUMBS_CACHE_DIR).also { it.mkdirs() }
        val thumbFile = File(thumbDir, "$containerId.jpg")

        val isFresh = thumbFile.exists() &&
            (System.currentTimeMillis() - thumbFile.lastModified()) < THUMB_MAX_AGE_MS

        if (!isFresh) {
            val url = getThumbnailUrl(containerId) ?: return null
            try {
                val request = Request.Builder().url(url).get().build()
                GameNativeApi.httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.bytes()?.let { thumbFile.writeBytes(it) }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to download thumbnail for $containerId")
                return null
            }
        }

        if (!thumbFile.exists()) return null

        return AssetFileDescriptor(
            ParcelFileDescriptor.open(thumbFile, ParcelFileDescriptor.MODE_READ_ONLY),
            0,
            AssetFileDescriptor.UNKNOWN_LENGTH,
        )
    }

    // -----------------------------------------------------------------------
    // isChildDocument — needed for correct URI permission grants
    // -----------------------------------------------------------------------

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith(parentDocumentId)

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun isContainerRootDir(documentId: String): Boolean {
        val file = File(documentId)
        return file.parentFile?.name == "home" &&
            file.name.startsWith("${ImageFs.USER}-")
    }

    /**
     * For the container root we show only .wine/ and the game install directory (A: drive),
     * hiding internal Wine tooling directories (.local/, .cache/, etc.).
     */
    private fun curatedContainerChildren(containerDir: File): Array<File> {
        val children = mutableListOf<File>()

        val wineDir = File(containerDir, ".wine")
        if (wineDir.isDirectory) children.add(wineDir)

        val configFile = File(containerDir, ".container")
        if (configFile.exists()) {
            try {
                val json = JSONObject(configFile.readText())
                val drives = json.optString("drives", "")
                val aDrivePath = ContainerUtils.getADrivePath(drives)
                if (aDrivePath != null) {
                    val gameFilesDir = File(aDrivePath)
                    if (gameFilesDir.isDirectory) children.add(gameFilesDir)
                }
            } catch (e: JSONException) {
                Timber.tag(TAG).w(e, "Failed to parse container config for ${containerDir.name}")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to read container config for ${containerDir.name}")
            }
        }

        return children.toTypedArray()
    }

    private fun includeFile(cursor: MatrixCursor, file: File) {
        val isDir = file.isDirectory
        val mimeType = if (isDir) Document.MIME_TYPE_DIR else getMimeType(file.name)
        val flags = computeFlags(file)
        val iconRes = if (isDir) folderIconRes(file.name) else null

        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, file.absolutePath)
            add(Document.COLUMN_DISPLAY_NAME, getFriendlyName(file))
            add(Document.COLUMN_MIME_TYPE, mimeType)
            add(Document.COLUMN_SIZE, if (isDir) null else file.length())
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_ICON, iconRes)
        }
    }

    private fun getFriendlyName(file: File): String {
        val ctx = context ?: return file.name
        return when (file.name) {
            ".wine" -> ctx.getString(R.string.provider_wine_prefix)
            ImageFs.USER -> ctx.getString(R.string.provider_user_profile)
            else -> {
                if (isContainerRootDir(file.absolutePath)) {
                    val containerId = file.name.removePrefix("${ImageFs.USER}-")
                    resolveGameName(containerId)
                } else {
                    file.name
                }
            }
        }
    }

    private fun computeFlags(file: File): Int {
        var flags = 0
        val path = file.absolutePath
        val writeable = path.contains(WRITABLE_WINE_SUBPATH)

        if (file.isDirectory) {
            if (writeable) flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            if (writeable) flags = flags or Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE
        }

        if (isContainerRootDir(path)) {
            flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        }

        return flags
    }

    private fun getMimeType(filename: String): String {
        val ext = filename.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            ?: "application/octet-stream"
    }

    private fun folderIconRes(name: String): Int? = when (name) {
        "drive_c"                           -> R.drawable.ic_folder_drive
        "windows"                           -> R.drawable.ic_folder_windows
        "system32", "syswow64"              -> R.drawable.ic_folder_system
        "users"                             -> R.drawable.ic_folder_users
        "Program Files", "Program Files (x86)" -> R.drawable.ic_folder_programs
        "Temp", "temp"                      -> R.drawable.ic_folder_temp
        "Documents", "My Documents"         -> R.drawable.ic_folder_documents
        "Desktop"                           -> R.drawable.ic_folder_desktop
        "Downloads"                         -> R.drawable.ic_folder_downloads
        "Pictures", "My Pictures"           -> R.drawable.ic_folder_pictures
        "Videos", "My Videos"               -> R.drawable.ic_folder_videos
        "Music", "My Music"                 -> R.drawable.ic_folder_music
        "AppData"                           -> R.drawable.ic_folder_appdata
        "Saved Games"                       -> R.drawable.ic_folder_saved_games
        else                                -> null
    }

    private fun platformIconRes(source: GameSource): Int = when (source) {
        GameSource.STEAM -> R.drawable.ic_steam
        GameSource.GOG -> R.drawable.ic_gog
        GameSource.EPIC -> R.drawable.ic_epic
        GameSource.AMAZON -> R.drawable.ic_amazon
        GameSource.CUSTOM_GAME -> R.drawable.ic_custom_game
    }

    private fun safeExtractSource(containerId: String): GameSource = try {
        ContainerUtils.extractGameSourceFromContainerId(containerId)
    } catch (e: Exception) {
        GameSource.CUSTOM_GAME
    }

    // -----------------------------------------------------------------------
    // Game name resolution
    // -----------------------------------------------------------------------

    /**
     * Resolves the human-readable name for a game container.
     *
     * First tries the in-memory service singletons (fast, works when the app is running).
     * Falls back to a direct raw SQLite query so names appear even when the app is not running.
     */
    private fun resolveGameName(containerId: String): String {
        // Try the in-memory service first (populated when the app is running)
        val fromService = try {
            ContainerUtils.resolveGameName(containerId).takeIf { it != "Unknown" }
        } catch (e: Exception) {
            null
        }
        if (fromService != null) return fromService

        // Fall back to a direct DB read (works when services haven't started)
        return try {
            val source = safeExtractSource(containerId)
            val gameId = ContainerUtils.extractGameIdFromContainerId(containerId)
            queryGameNameFromDb(source, gameId) ?: containerId
        } catch (e: Exception) {
            containerId
        }
    }

    // -----------------------------------------------------------------------
    // Thumbnail URL resolution
    // -----------------------------------------------------------------------

    /**
     * Returns the cover-art URL for a container, or null if unavailable.
     *
     * Steam URLs are deterministic from the appId and require no DB access.
     * Other platforms use a raw SQLite read of the local Room database.
     */
    private fun getThumbnailUrl(containerId: String): String? {
        return try {
            val source = safeExtractSource(containerId)
            val gameId = ContainerUtils.extractGameIdFromContainerId(containerId)
            when (source) {
                // Steam header images have a predictable CDN URL from the app ID alone
                GameSource.STEAM ->
                    "https://shared.steamstatic.com/store_item_assets/steam/apps/$gameId/header.jpg"

                GameSource.GOG ->
                    queryStringColumn("gog_games", "image_url", "id", gameId.toString())

                GameSource.EPIC ->
                    // artCover is the tall capsule image; fall back to other art columns
                    queryStringColumn("epic_games", "art_cover", "id", gameId.toString())
                        ?: queryStringColumn("epic_games", "art_square", "id", gameId.toString())
                        ?: queryStringColumn("epic_games", "art_logo", "id", gameId.toString())

                GameSource.AMAZON ->
                    queryStringColumn("amazon_games", "art_url", "app_id", gameId.toString())

                GameSource.CUSTOM_GAME -> null
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to get thumbnail URL for $containerId")
            null
        }
    }

    // -----------------------------------------------------------------------
    // Raw SQLite helpers (avoids Room TypeConverter complexity)
    // -----------------------------------------------------------------------

    /**
     * Opens the Room SQLite database in read-only mode and queries a single String column.
     * Returns null if the row is not found or the value is empty.
     */
    private fun queryStringColumn(
        table: String,
        column: String,
        idColumn: String,
        id: String,
    ): String? {
        val dbFile = context?.getDatabasePath(DATABASE_NAME) ?: return null
        if (!dbFile.exists()) return null
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            ).use { db ->
                db.rawQuery(
                    "SELECT $column FROM $table WHERE $idColumn = ? LIMIT 1",
                    arrayOf(id),
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotEmpty() }
                    else null
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to query $column from $table")
            null
        }
    }

    private fun queryGameNameFromDb(source: GameSource, gameId: Int): String? = when (source) {
        GameSource.STEAM ->
            queryStringColumn("steam_app", "name", "id", gameId.toString())
        GameSource.GOG ->
            queryStringColumn("gog_games", "title", "id", gameId.toString())
        GameSource.EPIC ->
            queryStringColumn("epic_games", "title", "id", gameId.toString())
        GameSource.AMAZON ->
            queryStringColumn("amazon_games", "title", "app_id", gameId.toString())
        GameSource.CUSTOM_GAME -> null
    }
}
