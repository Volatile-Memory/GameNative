package app.gamenative.service.amazon

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.core.coroutines.IoDispatcher
import app.gamenative.data.AmazonCredentials
import app.gamenative.data.AmazonGame
import app.gamenative.data.DownloadInfo
import app.gamenative.data.GameSource
import app.gamenative.db.dao.AmazonGameDao
import app.gamenative.enums.Marker
import app.gamenative.events.AndroidEvent
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.ExecutableSelectionUtils
import app.gamenative.utils.MarkerUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Unified manager for Amazon Games library, downloads, authentication, and installation operations.
 */
@Singleton
class AmazonManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val amazonGameDao: AmazonGameDao,
    private val amazonDownloadManager: AmazonDownloadManager,
    private val downloadPreferences: DownloadPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    // Result of verifying installed files against cached manifest
    data class VerificationResult(
        val totalFiles: Int,
        val verifiedOk: Int,
        val missingFiles: Int,
        val sizeMismatch: Int,
        val hashMismatch: Int,
        val failedFiles: List<String>,
    ) {
        val isValid: Boolean get() = failedFiles.isEmpty()
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Active downloads keyed by Amazon product ID (e.g. "amzn1.adg.product.XXXX")
    private val activeDownloads = ConcurrentHashMap<String, DownloadInfo>()

    // Active install paths keyed by Amazon product ID (used for robust partial-download detection)
    private val activeDownloadPaths = ConcurrentHashMap<String, String>()

    // Foreground service notification hooks
    var onSyncStatusChanged: ((Boolean) -> Unit)? = null
    var onDownloadTracked: ((DownloadInfo, String) -> Unit)? = null

    // Sync tracking variables
    private var lastSyncTimestamp: Long = 0L
    private var hasPerformedInitialSync: Boolean = false
    private var syncInProgress: Boolean = false
    private var backgroundSyncJob: Job? = null
    private val SYNC_THROTTLE_MILLIS = 15 * 60 * 1000L

    fun setSyncInProgress(inProgress: Boolean) {
        syncInProgress = inProgress
        onSyncStatusChanged?.invoke(inProgress)
    }

    fun isSyncInProgress(): Boolean = syncInProgress

    fun hasActiveOperations(): Boolean {
        return syncInProgress || backgroundSyncJob?.isActive == true || hasActiveDownload()
    }

    // ── Authentication & Credentials ──────────────────────────────────────────

    fun hasStoredCredentials(): Boolean =
        AmazonAuthManager.hasStoredCredentials(context)

    fun hasStoredCredentials(targetContext: Context): Boolean =
        AmazonAuthManager.hasStoredCredentials(targetContext)

    suspend fun authenticateWithCode(
        authCode: String,
        targetContext: Context = context,
    ): Result<AmazonCredentials> = AmazonAuthManager.authenticateWithCode(targetContext, authCode)

    suspend fun logout(targetContext: Context = context): Result<Unit> = withContext(ioDispatcher) {
        try {
            Timber.tag("Amazon").i("Starting logout...")
            AmazonAuthManager.logout(targetContext)
            Timber.tag("Amazon").i("Credentials cleared")

            amazonGameDao.deleteAllNonInstalledGames()
            Timber.tag("Amazon").i("All non-installed Amazon games removed from database")

            Timber.tag("Amazon").i("Logout completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("Amazon").e(e, "Error during logout")
            Result.failure(e)
        }
    }

    // ── Library & Sync ────────────────────────────────────────────────────────

    /** Refresh the Amazon library from API and persist it in DB. */
    suspend fun refreshLibrary() = withContext(ioDispatcher) {
        Timber.i("[Amazon] Starting library refresh…")

        val credentialsResult = AmazonAuthManager.getStoredCredentials(context)
        if (credentialsResult.isFailure) {
            Timber.w("[Amazon] No stored credentials — ${credentialsResult.exceptionOrNull()?.message}")
            return@withContext
        }
        val credentials = credentialsResult.getOrNull()!!

        val games = AmazonApiClient.getEntitlements(
            bearerToken = credentials.accessToken,
            deviceSerial = credentials.deviceSerial,
        )

        if (games.isEmpty()) {
            Timber.w("[Amazon] No entitlements returned from API")
            return@withContext
        }

        amazonGameDao.upsertPreservingInstallStatus(games)
        Timber.i("[Amazon] Library refresh complete — ${games.size} game(s) in DB")
    }

    suspend fun syncLibrary(force: Boolean = false) {
        if (syncInProgress) {
            Timber.i("[Amazon] Sync already in progress — ignoring duplicate request")
            return
        }

        val now = System.currentTimeMillis()
        val timeSinceLastSync = now - lastSyncTimestamp
        if (!force && hasPerformedInitialSync && timeSinceLastSync < SYNC_THROTTLE_MILLIS) {
            Timber.d("[Amazon] Sync throttled (${(SYNC_THROTTLE_MILLIS - timeSinceLastSync) / 1000 / 60}m remaining)")
            return
        }

        setSyncInProgress(true)
        try {
            refreshLibrary()
            lastSyncTimestamp = System.currentTimeMillis()
            hasPerformedInitialSync = true
            Timber.i("[Amazon] Sync complete — next auto-sync in 15 minutes")
        } catch (e: Exception) {
            Timber.e(e, "[Amazon] Library sync failed")
        } finally {
            setSyncInProgress(false)
        }
    }

    // ── DAO & Game Lookups ───────────────────────────────────────────────────

    suspend fun getGameById(productId: String): AmazonGame? = withContext(ioDispatcher) {
        amazonGameDao.getByProductId(productId)
    }

    suspend fun getGameByAppId(appId: Int): AmazonGame? = withContext(ioDispatcher) {
        amazonGameDao.getByAppId(appId)
    }

    suspend fun getAllGames(): List<AmazonGame> = withContext(ioDispatcher) {
        amazonGameDao.getAllAsList()
    }

    suspend fun getNonInstalledGames(): List<AmazonGame> = withContext(ioDispatcher) {
        amazonGameDao.getNonInstalledGames()
    }

    suspend fun markInstalled(productId: String, installPath: String, installSize: Long, versionId: String = "") =
        withContext(ioDispatcher) {
            amazonGameDao.markAsInstalled(productId, installPath, installSize, versionId)
            Timber.i("[Amazon] Marked installed: $productId at $installPath (${installSize}B, version=$versionId)")
        }

    suspend fun markUninstalled(productId: String) = withContext(ioDispatcher) {
        amazonGameDao.markAsUninstalled(productId)
        Timber.i("[Amazon] Marked uninstalled: $productId")
    }

    suspend fun updateDownloadSize(productId: String, size: Long) = withContext(ioDispatcher) {
        amazonGameDao.updateDownloadSize(productId, size)
        Timber.i("[Amazon] Updated download size for $productId: $size bytes")
    }

    suspend fun getBearerToken(): String? = withContext(ioDispatcher) {
        AmazonAuthManager.getStoredCredentials(context).getOrNull()?.accessToken
    }

    suspend fun deleteAllNonInstalledGames() = withContext(ioDispatcher) {
        amazonGameDao.deleteAllNonInstalledGames()
        Timber.i("[Amazon] Deleted all non-installed games from DB")
    }

    fun getAmazonGameOf(productId: String): AmazonGame? = runBlocking(ioDispatcher) {
        getGameById(productId)
    }

    fun getAmazonGameByAppId(appId: Int): AmazonGame? = runBlocking(ioDispatcher) {
        getGameByAppId(appId)
    }

    // ── Install Queries & Detection ──────────────────────────────────────────

    suspend fun fetchDownloadSize(productId: String): Long? {
        val game = getGameById(productId) ?: return null
        if (game.entitlementId.isBlank()) return null

        val token = getBearerToken() ?: return null
        val size = AmazonApiClient.fetchDownloadSize(game.entitlementId, token) ?: return null

        updateDownloadSize(productId, size)
        return size
    }

    fun isGameInstalled(productId: String): Boolean = isGameInstalled(context, productId)

    fun isGameInstalled(targetContext: Context, productId: String): Boolean {
        val game = getAmazonGameOf(productId) ?: return false

        if (game.isInstalled && game.installPath.isNotEmpty()) {
            return MarkerUtils.hasMarker(game.installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
        }

        val installPath = game.installPath.takeIf { it.isNotEmpty() }
            ?: game.title.takeIf { it.isNotEmpty() }?.let {
                AmazonConstants.getGameInstallPath(targetContext, it)
            }
            ?: return false

        val isDownloadComplete = MarkerUtils.hasMarker(installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
        val isDownloadInProgress = MarkerUtils.hasMarker(installPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)
        if (isDownloadComplete && !isDownloadInProgress) {
            runBlocking(ioDispatcher) {
                markInstalled(productId, installPath, 0L)
            }
            return true
        }

        return false
    }

    fun isGameInstalledByAppId(appId: Int): Boolean = isGameInstalledByAppId(context, appId)

    fun isGameInstalledByAppId(targetContext: Context, appId: Int): Boolean {
        val game = getAmazonGameByAppId(appId) ?: return false
        return isGameInstalled(targetContext, game.productId)
    }

    fun getExpectedInstallPathByAppId(appId: Int): String? = getExpectedInstallPathByAppId(context, appId)

    fun getExpectedInstallPathByAppId(targetContext: Context, appId: Int): String? {
        val game = getAmazonGameByAppId(appId) ?: return null

        activeDownloadPaths[game.productId]?.let { return it }

        val title = game.title.ifBlank { return null }
        return AmazonConstants.getGameInstallPath(targetContext, title)
    }

    fun hasPartialDownloadByAppId(appId: Int): Boolean = hasPartialDownloadByAppId(context, appId)

    fun hasPartialDownloadByAppId(targetContext: Context, appId: Int): Boolean {
        if (getDownloadInfoByAppId(appId) != null) {
            Timber.tag("Amazon").d("[PARTIAL] appId=$appId partial=true reason=active_download")
            return true
        }
        if (isGameInstalledByAppId(targetContext, appId)) {
            Timber.tag("Amazon").d("[PARTIAL] appId=$appId partial=false reason=installed")
            return false
        }

        val expectedPath = getExpectedInstallPathByAppId(targetContext, appId) ?: return false
        val installDir = File(expectedPath)
        if (!installDir.exists()) {
            Timber.tag("Amazon").d("[PARTIAL] appId=$appId partial=false reason=path_missing path=$expectedPath")
            return false
        }

        if (MarkerUtils.hasMarker(expectedPath, Marker.DOWNLOAD_COMPLETE_MARKER)) {
            Timber.tag("Amazon").d("[PARTIAL] appId=$appId partial=false reason=complete_marker path=$expectedPath")
            return false
        }
        if (MarkerUtils.hasMarker(expectedPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)) {
            Timber.tag("Amazon").d("[PARTIAL] appId=$appId partial=true reason=in_progress_marker path=$expectedPath")
            return true
        }

        val children = installDir.listFiles() ?: return false
        if (children.isEmpty()) {
            Timber.tag("Amazon").d("[PARTIAL] appId=$appId partial=false reason=empty_dir path=$expectedPath")
            return false
        }

        val hasPartialPayload = children.any { child ->
            when (child.name) {
                Marker.DOWNLOAD_COMPLETE_MARKER.fileName,
                Marker.DOWNLOAD_IN_PROGRESS_MARKER.fileName,
                ".DownloadInfo" -> {
                    child.isDirectory && (child.listFiles()?.any { it.isFile && it.length() > 0L } == true)
                }
                else -> true
            }
        }

        val childNames = children.joinToString(limit = 8) { it.name }
        Timber.tag("Amazon").d(
            "[PARTIAL] appId=$appId partial=$hasPartialPayload reason=dir_scan path=$expectedPath children=$childNames"
        )
        return hasPartialPayload
    }

    fun getInstallPath(productId: String): String? {
        val game = getAmazonGameOf(productId) ?: return null
        return if (game.isInstalled && game.installPath.isNotEmpty()) game.installPath else null
    }

    fun getInstallPathByAppId(appId: Int): String? {
        val game = getAmazonGameByAppId(appId) ?: return null
        return if (game.isInstalled && game.installPath.isNotEmpty()) game.installPath else null
    }

    fun updateInstallPath(appId: Int, path: String) {
        runBlocking(ioDispatcher) {
            val game = getGameByAppId(appId) ?: return@runBlocking
            if (game.isInstalled && game.installPath != path) {
                markInstalled(game.productId, path, game.installSize, game.versionId)
            }
        }
    }

    fun getProductIdByAppId(appId: Int): String? {
        return getAmazonGameByAppId(appId)?.productId
    }

    fun getLaunchExecutable(containerId: String): String {
        val appId = runCatching { ContainerUtils.extractGameIdFromContainerId(containerId) }.getOrElse { return "" }
        if (appId <= 0) return ""

        val installPath = getInstallPathByAppId(appId) ?: return ""
        val installDir = File(installPath)
        if (!installDir.isDirectory) return ""

        val exeFile = ExecutableSelectionUtils.choosePrimaryExeFromDisk(
            installDir = installDir,
            gameName = installDir.name,
        ) ?: return ""

        return exeFile.path
    }

    fun getInstalledGamePath(gameId: String): String? = getInstallPath(gameId)

    suspend fun isUpdatePending(productId: String): Boolean {
        val game = getGameById(productId) ?: return false
        if (!game.isInstalled || game.versionId.isEmpty()) return false
        val token = getBearerToken() ?: return false
        return AmazonApiClient.isUpdateAvailable(productId, game.versionId, token) ?: false
    }

    suspend fun isUpdatePendingByAppId(appId: Int): Boolean {
        val productId = getProductIdByAppId(appId) ?: return false
        return isUpdatePending(productId)
    }

    // ── Download Operations ──────────────────────────────────────────────────

    fun getDownloadInfo(productId: String): DownloadInfo? = activeDownloads[productId]

    fun getActiveDownloads(): Map<String, DownloadInfo> = HashMap(activeDownloads)

    fun getDownloadInfoByAppId(appId: Int): DownloadInfo? {
        val productId = getProductIdByAppId(appId) ?: return null
        return getDownloadInfo(productId)
    }

    fun hasActiveDownload(): Boolean = activeDownloads.isNotEmpty()

    fun getPartialInstallPaths(targetContext: Context = context): Set<String> {
        val roots = buildList {
            add(AmazonConstants.internalAmazonGamesPath(targetContext))
            if (downloadPreferences.externalStoragePath.isNotBlank()) {
                add(AmazonConstants.externalAmazonGamesPath(targetContext))
            }
        }.distinct()

        return roots.asSequence()
            .flatMap { root -> MarkerUtils.findResumablePartialInstalls(root).asSequence() }
            .toSet()
    }

    suspend fun getPartialDownloads(targetContext: Context = context): List<String> {
        val partialInstallPaths = getPartialInstallPaths(targetContext)
        if (partialInstallPaths.isEmpty()) return emptyList()

        return getNonInstalledGames()
            .asSequence()
            .filter { game -> !activeDownloads.containsKey(game.productId) }
            .filter { game ->
                val expectedPaths = buildList {
                    game.installPath.takeIf { it.isNotBlank() }?.let(::add)
                    add(AmazonConstants.getGameInstallPath(targetContext, game.title))
                }
                expectedPaths.any(partialInstallPaths::contains)
            }
            .map { it.productId }
            .toList()
    }

    fun cancelDownload(productId: String): Boolean {
        val downloadInfo = activeDownloads[productId] ?: run {
            Timber.tag("Amazon").w("No active download for $productId")
            return false
        }
        Timber.tag("Amazon").i("Cancelling download for $productId")
        downloadInfo.cancel()
        return true
    }

    fun cancelDownloadByAppId(appId: Int): Boolean {
        val productId = getProductIdByAppId(appId) ?: return false
        return cancelDownload(productId)
    }

    suspend fun downloadGame(
        productId: String,
        installPath: String,
    ): Result<DownloadInfo> = downloadGame(context, productId, installPath)

    suspend fun downloadGame(
        targetContext: Context,
        productId: String,
        installPath: String,
    ): Result<DownloadInfo> {
        activeDownloads[productId]?.let { existing ->
            Timber.tag("Amazon").w("Download already in progress for $productId")
            return Result.success(existing)
        }

        val game = withContext(ioDispatcher) {
            getGameById(productId)
        } ?: return Result.failure(Exception("Game not found: $productId"))

        val downloadInfo = DownloadInfo(
            jobCount = 1,
            gameId = game.appId,
            downloadingAppIds = CopyOnWriteArrayList(),
        )
        downloadInfo.setPersistencePath(installPath)

        val persistedBytes = downloadInfo.loadPersistedBytesDownloaded(installPath)
        if (persistedBytes > 0L) {
            downloadInfo.initializeBytesDownloaded(persistedBytes)
        }

        downloadInfo.setActive(true)
        activeDownloads[productId] = downloadInfo
        activeDownloadPaths[productId] = installPath
        onDownloadTracked?.invoke(downloadInfo, game.title)

        MarkerUtils.removeMarker(installPath, Marker.DOWNLOAD_COMPLETE_MARKER)

        PluviaApp.events.emitJava(
            AndroidEvent.DownloadStatusChanged(game.appId, true)
        )

        val job = scope.launch {
            try {
                val result = amazonDownloadManager.downloadGame(
                    context = targetContext,
                    game = game,
                    installPath = installPath,
                    downloadInfo = downloadInfo,
                )

                if (result.isSuccess) {
                    Timber.tag("Amazon").i("Download succeeded for $productId")
                    downloadInfo.setActive(false)
                    downloadInfo.clearPersistedBytesDownloaded(installPath)
                    SnackbarManager.show("Download completed: ${game.title}")
                    PluviaApp.events.emitJava(
                        AndroidEvent.LibraryInstallStatusChanged(game.appId, GameSource.AMAZON)
                    )
                } else {
                    val error = result.exceptionOrNull()
                    Timber.tag("Amazon").e(error, "Download failed for $productId")
                    downloadInfo.setActive(false)
                    cleanupFailedInstall(targetContext, game, installPath)
                    SnackbarManager.show("Download failed: ${error?.message ?: "Unknown error"}")
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Timber.tag("Amazon").d("Download cancelled for $productId")
                } else {
                    Timber.tag("Amazon").e(e, "Download exception for $productId")
                    cleanupFailedInstall(targetContext, game, installPath)
                }
                downloadInfo.setActive(false)
            } finally {
                activeDownloads.remove(productId)
                activeDownloadPaths.remove(productId)
                PluviaApp.events.emitJava(
                    AndroidEvent.DownloadStatusChanged(game.appId, false)
                )
            }
        }

        downloadInfo.setDownloadJob(job)
        return Result.success(downloadInfo)
    }

    suspend fun cleanupFailedInstall(targetContext: Context, game: AmazonGame, installPath: String) {
        withContext(ioDispatcher) {
            MarkerUtils.removeMarker(installPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            MarkerUtils.removeMarker(installPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)

            runCatching {
                val dir = File(installPath)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }.onFailure {
                Timber.tag("Amazon").w(it, "Failed to clean partial install dir for ${game.productId}")
            }

            runCatching {
                markUninstalled(game.productId)
            }.onFailure {
                Timber.tag("Amazon").w(it, "Failed to mark game uninstalled after failed install: ${game.productId}")
            }
        }

        withContext(Dispatchers.Main) {
            ContainerUtils.deleteContainer(targetContext, "AMAZON_${game.appId}")
        }

        PluviaApp.events.emitJava(AndroidEvent.LibraryInstallStatusChanged(game.appId, GameSource.AMAZON))
    }

    // ── Uninstallation ───────────────────────────────────────────────────────

    suspend fun deleteGame(productId: String): Result<Unit> = deleteGame(context, productId)

    suspend fun deleteGame(targetContext: Context, productId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val game = getGameById(productId)
                ?: return@withContext Result.failure(Exception("Game not found: $productId"))

            val path = game.installPath.ifEmpty {
                AmazonConstants.getGameInstallPath(targetContext, game.title)
            }
            if (File(path).exists()) {
                val installDir = File(path)
                val manifestFile = File(targetContext.filesDir, "manifests/amazon/$productId.proto")

                if (manifestFile.exists()) {
                    Timber.tag("Amazon").i("Manifest-based uninstall for $productId")
                    try {
                        val manifest = AmazonManifest.parse(manifestFile.readBytes())
                        var deletedFiles = 0
                        var failedFiles = 0

                        for (mf in manifest.allFiles) {
                            val file = File(installDir, mf.unixPath)
                            if (file.exists()) {
                                if (file.delete()) {
                                    deletedFiles++
                                } else {
                                    failedFiles++
                                    Timber.tag("Amazon").w("Failed to delete: ${file.absolutePath}")
                                }
                            }
                        }

                        val dirs = mutableSetOf<File>()
                        for (mf in manifest.allFiles) {
                            var parent = File(installDir, mf.unixPath).parentFile
                            while (parent != null && parent != installDir && parent.toPath().startsWith(installDir.toPath())) {
                                dirs.add(parent)
                                parent = parent.parentFile
                            }
                        }
                        for (dir in dirs.sortedByDescending { it.absolutePath.length }) {
                            if (dir.exists() && dir.isDirectory && (dir.listFiles()?.isEmpty() == true)) {
                                dir.delete()
                            }
                        }

                        if (installDir.exists() && installDir.isDirectory &&
                            (installDir.listFiles()?.isEmpty() == true)
                        ) {
                            installDir.delete()
                        }

                        Timber.tag("Amazon").i(
                            "Manifest-based uninstall complete: $deletedFiles deleted, $failedFiles failed"
                        )
                    } catch (e: Exception) {
                        Timber.tag("Amazon").w(e, "Manifest parse failed — falling back to recursive delete")
                        installDir.deleteRecursively()
                    }
                } else {
                    Timber.tag("Amazon").i("No cached manifest — recursive delete: $path")
                    installDir.deleteRecursively()
                }

                MarkerUtils.removeMarker(path, Marker.DOWNLOAD_COMPLETE_MARKER)
                MarkerUtils.removeMarker(path, Marker.DOWNLOAD_IN_PROGRESS_MARKER)

                val downloadInfoDir = File(installDir, ".DownloadInfo")
                if (downloadInfoDir.exists()) {
                    downloadInfoDir.deleteRecursively()
                }

                if (installDir.exists()) {
                    val amazonRoot = File(AmazonConstants.defaultAmazonGamesPath(targetContext)).canonicalFile
                    val installCanonical = installDir.canonicalFile
                    val isUnderAmazonRoot = installCanonical.path == amazonRoot.path ||
                        installCanonical.path.startsWith("${amazonRoot.path}${File.separator}")

                    if (isUnderAmazonRoot) {
                        installCanonical.deleteRecursively()
                    } else {
                        Timber.tag("Amazon").w(
                            "Skipping final recursive uninstall cleanup outside Amazon root: ${installCanonical.path}"
                        )
                    }

                    Timber.tag("Amazon").i(
                        "[UNINSTALL] cleanup productId=$productId installDirExists=${installCanonical.exists()} path=${installCanonical.path}"
                    )
                }
            }

            markUninstalled(productId)

            try {
                val manifestFile = File(targetContext.filesDir, "manifests/amazon/$productId.proto")
                if (manifestFile.exists()) {
                    manifestFile.delete()
                    Timber.tag("Amazon").d("Deleted cached manifest for $productId")
                }
            } catch (e: Exception) {
                Timber.tag("Amazon").w(e, "Failed to delete cached manifest (non-fatal)")
            }

            withContext(Dispatchers.Main) {
                ContainerUtils.deleteContainer(targetContext, "AMAZON_${game.appId}")
            }

            val postUninstallPath = AmazonConstants.getGameInstallPath(targetContext, game.title)
            val postInstallDirExists = File(postUninstallPath).exists()
            val completeMarkerExists = MarkerUtils.hasMarker(postUninstallPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            val inProgressMarkerExists = MarkerUtils.hasMarker(postUninstallPath, Marker.DOWNLOAD_IN_PROGRESS_MARKER)

            Timber.tag("Amazon").i(
                "[UNINSTALL] final_state productId=$productId appId=${game.appId} installDirExists=$postInstallDirExists completeMarker=$completeMarkerExists inProgressMarker=$inProgressMarkerExists"
            )

            PluviaApp.events.emitJava(
                AndroidEvent.LibraryInstallStatusChanged(game.appId, GameSource.AMAZON)
            )

            Timber.tag("Amazon").i("Game uninstalled: $productId")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.tag("Amazon").e(e, "Failed to uninstall $productId")
            Result.failure(e)
        }
    }

    // ── File Verification ────────────────────────────────────────────────────

    suspend fun verifyGame(productId: String): Result<VerificationResult> =
        verifyGame(context, productId)

    suspend fun verifyGame(targetContext: Context, productId: String): Result<VerificationResult> = withContext(ioDispatcher) {
        try {
            val game = getGameById(productId)
                ?: return@withContext Result.failure(Exception("Game not found: $productId"))

            if (!game.isInstalled || game.installPath.isEmpty()) {
                return@withContext Result.failure(Exception("Game is not installed"))
            }

            val installDir = File(game.installPath)
            if (!installDir.exists()) {
                return@withContext Result.failure(Exception("Install directory not found: ${game.installPath}"))
            }

            val manifestFile = File(targetContext.filesDir, "manifests/amazon/$productId.proto")
            if (!manifestFile.exists()) {
                return@withContext Result.failure(Exception("No cached manifest — reinstall to enable verification"))
            }

            val manifest = AmazonManifest.parse(manifestFile.readBytes())
            val files = manifest.allFiles

            Timber.tag("Amazon").i("Verifying ${files.size} files for $productId at ${game.installPath}")

            var verifiedOk = 0
            var missingFiles = 0
            var sizeMismatch = 0
            var hashMismatch = 0
            val failedFiles = mutableListOf<String>()

            for (mf in files) {
                val file = File(installDir, mf.unixPath)

                if (!file.exists()) {
                    missingFiles++
                    failedFiles.add(mf.unixPath)
                    Timber.tag("Amazon").d("Verify MISSING: ${mf.unixPath}")
                    continue
                }

                if (file.length() != mf.size) {
                    sizeMismatch++
                    failedFiles.add(mf.unixPath)
                    Timber.tag("Amazon").d(
                        "Verify SIZE MISMATCH: ${mf.unixPath} (expected=${mf.size}, actual=${file.length()})"
                    )
                    continue
                }

                if (mf.hashAlgorithm == 0 && mf.hashBytes.isNotEmpty()) {
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    file.inputStream().buffered().use { input ->
                        val buf = ByteArray(8192)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            digest.update(buf, 0, read)
                        }
                    }
                    val computed = digest.digest()
                    if (!computed.contentEquals(mf.hashBytes)) {
                        hashMismatch++
                        failedFiles.add(mf.unixPath)
                        Timber.tag("Amazon").d("Verify HASH MISMATCH: ${mf.unixPath}")
                        continue
                    }
                }

                verifiedOk++
            }

            val result = VerificationResult(
                totalFiles = files.size,
                verifiedOk = verifiedOk,
                missingFiles = missingFiles,
                sizeMismatch = sizeMismatch,
                hashMismatch = hashMismatch,
                failedFiles = failedFiles,
            )

            if (result.isValid) {
                Timber.tag("Amazon").i("Verification PASSED: ${result.verifiedOk}/${result.totalFiles} files OK")
            } else {
                Timber.tag("Amazon").w(
                    "Verification FAILED: ${result.verifiedOk}/${result.totalFiles} OK, " +
                        "${result.missingFiles} missing, ${result.sizeMismatch} size mismatch, " +
                        "${result.hashMismatch} hash mismatch"
                )
            }

            Result.success(result)
        } catch (e: Exception) {
            Timber.tag("Amazon").e(e, "Verification failed for $productId")
            Result.failure(e)
        }
    }
}
