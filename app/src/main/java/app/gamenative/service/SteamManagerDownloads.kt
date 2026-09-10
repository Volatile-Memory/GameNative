package app.gamenative.service

import android.content.Context
import app.gamenative.PluviaApp
import app.gamenative.R
import app.gamenative.data.AppInfo
import app.gamenative.data.DepotInfo
import app.gamenative.data.DownloadInfo
import app.gamenative.data.DownloadingAppInfo
import app.gamenative.data.GameSource
import app.gamenative.enums.Marker
import app.gamenative.enums.OS
import app.gamenative.enums.OSArch
import app.gamenative.enums.PathType
import app.gamenative.enums.SaveLocation
import app.gamenative.enums.SteamRealm
import app.gamenative.enums.SyncResult
import app.gamenative.events.AndroidEvent
import app.gamenative.ui.util.SnackbarManager
import app.gamenative.utils.CaseInsensitiveFileSystem
import app.gamenative.utils.ContainerUtils
import app.gamenative.utils.DownloadSpeedConfig
import app.gamenative.utils.LsfgVkManager
import app.gamenative.utils.MarkerUtils
import app.gamenative.utils.SteamUtils
import app.gamenative.workshop.WorkshopManager
import com.winlator.container.Container
import com.winlator.container.ContainerManager
import com.winlator.xenvironment.ImageFs
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import okio.Path.Companion.toPath
import org.json.JSONObject
import timber.log.Timber

fun SteamManager.downloadApp(appId: Int): DownloadInfo? {
    val currentDownloadInfo = downloadJobs[appId]
    if (currentDownloadInfo != null) {
        val branch = getDownloadingAppInfoOf(appId)?.branch
            ?: getInstalledApp(appId)?.branch
            ?: "public"
        return downloadApp(appId, currentDownloadInfo.downloadingAppIds, branch = branch, isUpdateOrVerify = false)
    } else {
        val downloadingAppInfo = getDownloadingAppInfoOf(appId)
        if (downloadingAppInfo != null) {
            return downloadApp(appId, downloadingAppInfo.dlcAppIds.orEmpty(), branch = downloadingAppInfo.branch, isUpdateOrVerify = false)
        } else {
            val installedApp = getInstalledApp(appId)
            val branch = installedApp?.branch ?: "public"
            val dlcAppIds = getInstalledDlcDepotsOf(appId).orEmpty().toMutableList()

            getDownloadableDlcAppsOf(appId)?.forEach { dlcApp ->
                val installedDlcApp = getInstalledApp(dlcApp.id)
                if (installedDlcApp != null) {
                    dlcAppIds.add(installedDlcApp.id)
                }
            }

            return downloadApp(appId, dlcAppIds, branch = branch, isUpdateOrVerify = true)
        }
    }
}

fun SteamManager.downloadApp(
    appId: Int,
    dlcAppIds: List<Int>,
    branch: String = "public",
    isUpdateOrVerify: Boolean,
): DownloadInfo? {
    if (!checkWifiOrNotify()) return null
    return getAppInfoOf(appId)?.let { _ ->
        val container = ContainerManager(context).getContainerById("STEAM_${appId}")
        val containerLanguage = if (container != null) {
            container.language
        } else {
            containerPreferences.containerLanguage.ifEmpty { "english" }
        }

        Timber.tag("SteamManager").d("downloadApp: downloading app $appId with language $containerLanguage, branch $branch")

        val depots = getDownloadableDepots(appId = appId, preferredLanguage = containerLanguage)
        downloadApp(
            appId = appId,
            downloadableDepots = depots,
            userSelectedDlcAppIds = dlcAppIds,
            branch = branch,
            containerLanguage = containerLanguage,
            isUpdateOrVerify = isUpdateOrVerify,
        )
    }
}

fun SteamManager.downloadApp(
    appId: Int,
    downloadableDepots: Map<Int, DepotInfo>,
    userSelectedDlcAppIds: List<Int>,
    branch: String,
    containerLanguage: String,
    isUpdateOrVerify: Boolean,
): DownloadInfo? {
    val appDirPath = getAppDirPath(appId)

    if (!checkWifiOrNotify()) return null
    if (downloadJobs.contains(appId)) return getAppDownloadInfo(appId)
    Timber.d("depots is empty? " + downloadableDepots.isEmpty())
    if (downloadableDepots.isEmpty()) return null

    val indirectDlcAppIds = getDownloadableDlcAppsOf(appId).orEmpty().map { it.id }

    val hasDepotContent = { depot: DepotInfo ->
        depot.manifests.isNotEmpty() || depot.encryptedManifests.isNotEmpty()
    }

    // Depots from Main game
    val mainDepots = getMainAppDepots(appId, containerLanguage)
    var mainAppDepots = mainDepots.filter { (_, depot) ->
        depot.dlcAppId == SteamManager.INVALID_APP_ID
    } + mainDepots.filter { (_, depot) ->
        userSelectedDlcAppIds.contains(depot.dlcAppId) && hasDepotContent(depot)
    }

    // Depots from DLC App
    val dlcAppDepots = downloadableDepots.filter { (_, depot) ->
        !mainAppDepots.map { it.key }.contains(depot.depotId) &&
            userSelectedDlcAppIds.contains(depot.dlcAppId) && indirectDlcAppIds.contains(depot.dlcAppId) && hasDepotContent(depot)
    }

    // Remove depots that are already downloaded (not for update/verify)
    val appInfo = getInstalledApp(appId)
    if (appInfo != null && !isUpdateOrVerify) {
        mainAppDepots = mainAppDepots.filter { it.key !in appInfo.downloadedDepots }
    }

    // Combine main app and DLC depots
    val selectedDepots = mainAppDepots + dlcAppDepots

    val downloadingAppIds = CopyOnWriteArrayList<Int>()
    val calculatedDlcAppIds = CopyOnWriteArrayList<Int>()

    userSelectedDlcAppIds.forEach { dlcAppId ->
        if (dlcAppDepots.filter { (_, depot) -> depot.dlcAppId == dlcAppId }.isNotEmpty()) {
            downloadingAppIds.add(dlcAppId)
            calculatedDlcAppIds.add(dlcAppId)
        }
    }

    // Add main app ID if there are main app depots
    if (mainAppDepots.isNotEmpty()) {
        downloadingAppIds.add(appId)
    }

    // There are some apps, the dlc depots does not have dlcAppId in the data, need to set it back
    val mainAppDlcIds = getMainAppDlcIdsWithoutProperDepotDlcIds(appId)

    // If there are no DLC depots, download the main app only
    if (dlcAppDepots.isEmpty()) {
        mainAppDlcIds.addAll(mainAppDepots.filter { it.value.dlcAppId != SteamManager.INVALID_APP_ID }.map { it.value.dlcAppId }.distinct())
        calculatedDlcAppIds.clear()
        downloadingAppIds.clear()
        downloadingAppIds.add(appId)
    }

    Timber.i("selectedDepots is empty? " + selectedDepots.isEmpty())
    if (selectedDepots.isEmpty()) return null

    Timber.i("Starting download for $appId")
    Timber.i("App contains ${mainAppDepots.size} depot(s): ${mainAppDepots.keys}")
    Timber.i("DLC contains ${dlcAppDepots.size} depot(s): ${dlcAppDepots.keys}")
    Timber.i("downloadingAppIds: $downloadingAppIds")

    // Save downloading app info
    runBlocking {
        downloadingAppInfoDao.insert(
            DownloadingAppInfo(
                appId,
                dlcAppIds = userSelectedDlcAppIds,
                branch = branch,
            ),
        )
    }

    val info = DownloadInfo(selectedDepots.size, appId, downloadingAppIds).also { di ->
        di.setPersistencePath(appDirPath)
        val sizes = selectedDepots.map { (_, depot) ->
            val mInfo = depot.manifests[branch]
                ?: depot.encryptedManifests[branch]
                ?: return@map 1L
            SteamUtils.getDownloadBytes(mInfo).coerceAtLeast(1L)
        }
        sizes.forEachIndexed { i, bytes -> di.setWeight(i, bytes) }

        val totalBytes = sizes.sum()
        di.setTotalExpectedBytes(totalBytes)

        val persistedBytes = di.loadPersistedBytesDownloaded(appDirPath)
        if (persistedBytes > 0L) {
            di.initializeBytesDownloaded(persistedBytes)
            Timber.i("Resumed download: initialized with $persistedBytes bytes")
        }

        downloadJobs[appId] = di
        beginDepotKeyPrep(appId, selectedDepots.keys, di)

        notifyDownloadStarted(appId)
        notificationHelper.trackDownload(di, getAppInfoOf(appId)?.name.orEmpty(), NotificationHelper.NOTIFICATION_ID_STEAM)
        onDownloadTracked?.invoke(di, getAppInfoOf(appId)?.name.orEmpty())

        val chunkStagingRedirectDir = File(DownloadService.baseCacheDirPath, "depot_chunks/$appId")
            .takeIf { !appDirPath.startsWith(DownloadService.baseDataDirPath) }

        scope.launch {
            try {
                if (isUpdateOrVerify) {
                    SteamUtils.clearStaleDrmBackups(appDirPath)
                }

                val licenses = getLicensesFromDb()
                if (licenses.isEmpty()) {
                    Timber.w("No licenses available for download")
                    return@launch
                }

                val speedConfig = DownloadSpeedConfig()
                val cpuCores = speedConfig.cpuCores
                val maxDownloads = speedConfig.maxDownloads
                val maxDecompress = speedConfig.maxDecompress

                Timber.i("CPU Cores: $cpuCores, maxDownloads: $maxDownloads, maxDecompress: $maxDecompress")

                chunkStagingRedirectDir?.apply {
                    deleteRecursively()
                    mkdirs()
                }

                val client = steamClient ?: run {
                    Timber.e("SteamClient is null during download")
                    return@launch
                }

                val depotDownloader = DepotDownloader(
                    client,
                    licenses,
                    debug = false,
                    androidEmulation = true,
                    maxDownloads = maxDownloads,
                    maxDecompress = maxDecompress,
                    parentJob = coroutineContext[Job],
                    autoStartDownload = false,
                    skipLargeFileAllocation = chunkStagingRedirectDir != null,
                    filesystem = CaseInsensitiveFileSystem(
                        showDebugLog = false,
                        chunkStagingRedirect = chunkStagingRedirectDir?.absolutePath?.toPath(),
                    ),
                )

                val depotIdToIndex = selectedDepots.keys.mapIndexed { index, depotId -> depotId to index }.toMap()
                val listener = AppDownloadListener(this@downloadApp, di, depotIdToIndex)
                depotDownloader.addListener(listener)

                val branchPassword = steamUnlockedBranchDao
                    .getSteamUnlockedBranches(appId)
                    .firstOrNull { it.branchName == branch }
                    ?.password

                if (mainAppDepots.isNotEmpty()) {
                    val mainAppDepotIds = mainAppDepots.keys.sorted()
                    val mainAppItem = AppItem(
                        appId,
                        installDirectory = getAppDirPath(appId),
                        depot = mainAppDepotIds,
                        branch = branch,
                        branchPassword = branchPassword,
                    )
                    depotDownloader.add(mainAppItem)
                }

                calculatedDlcAppIds.forEach { dlcAppId ->
                    val dlcDepots = selectedDepots.filter { it.value.dlcAppId == dlcAppId }
                    val dlcDepotIds = dlcDepots.keys.sorted()
                    val dlcAppItem = AppItem(
                        dlcAppId,
                        installDirectory = getAppDirPath(appId),
                        depot = dlcDepotIds,
                        branch = branch,
                        branchPassword = branchPassword,
                    )
                    depotDownloader.add(dlcAppItem)
                }

                depotDownloader.finishAdding()
                depotDownloader.startDownloading()
                Timber.i("Downloading game to " + defaultAppInstallPath)

                depotDownloader.getCompletion().await()
                depotDownloader.close()

                val appConfig = getAppInfoOf(appId)?.config
                if (appConfig?.steamControllerTemplateIndex == 1) {
                    val controllerConfig = appConfig.steamControllerConfigDetails
                        .let { selectSteamControllerConfig(it) }

                    if (controllerConfig != null) {
                        val publishedFileId = controllerConfig.publishedFileId
                        runCatching {
                            val requestBody = FormBody.Builder()
                                .add("itemcount", "1")
                                .add("publishedfileids[0]", publishedFileId.toString())
                                .build()

                            val request = Request.Builder()
                                .url("https://api.steampowered.com/ISteamRemoteStorage/GetPublishedFileDetails/v1")
                                .post(requestBody)
                                .build()

                            app.gamenative.utils.Net.http.newCall(request).execute().use { response ->
                                if (response.isSuccessful) {
                                    val responseBody = response.body?.string()
                                    if (!responseBody.isNullOrEmpty()) {
                                        val responseJson = JSONObject(responseBody)
                                        val responseData = responseJson.optJSONObject("response")
                                        val fileDetails = responseData?.optJSONArray("publishedfiledetails")?.optJSONObject(0)
                                        val fileUrl = fileDetails?.optString("file_url", "")?.trim().orEmpty()
                                        if (fileUrl.isNotEmpty()) {
                                            val configFile = File(appDirPath, SteamManager.STEAM_CONTROLLER_CONFIG_FILENAME)
                                            val downloadRequest = Request.Builder().url(fileUrl).get().build()
                                            app.gamenative.utils.Net.http.newCall(downloadRequest).execute().use { downloadResponse ->
                                                if (downloadResponse.isSuccessful) {
                                                    downloadResponse.body?.byteStream()?.use { input ->
                                                        configFile.outputStream().use { output ->
                                                            input.copyTo(output)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (mainAppDepots.isNotEmpty()) {
                    completeAppDownload(
                        di,
                        appId,
                        mainAppDepots.keys.toList(),
                        mainAppDlcIds,
                        appDirPath,
                        branch,
                        parentScope = this,
                    )
                }

                calculatedDlcAppIds.forEach { dlcAppId ->
                    val dlcDepots = selectedDepots.filter { it.value.dlcAppId == dlcAppId }
                    val dlcDepotIds = dlcDepots.keys.toList()
                    completeAppDownload(
                        di,
                        dlcAppId,
                        dlcDepotIds,
                        emptyList(),
                        appDirPath,
                        branch,
                        parentScope = this,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Download failed for app $appId")
                if (e is CancellationException) {
                    di.cancel()
                    downloadingAppInfoDao.deleteApp(appId)
                    runCatching { chunkStagingRedirectDir?.deleteRecursively() }
                }
            } finally {
                runCatching { chunkStagingRedirectDir?.deleteRecursively() }
                removeDownloadJob(appId)
            }
        }
    }

    return info
}

internal suspend fun SteamManager.completeAppDownload(
    downloadInfo: DownloadInfo,
    downloadingAppId: Int,
    entitledDepotIds: List<Int>,
    selectedDlcAppIds: List<Int>,
    appDirPath: String,
    branch: String = "public",
    parentScope: CoroutineScope,
) {
    Timber.i("Item $downloadingAppId download completed, saving database")

    val appInfo = appInfoDao.getInstalledApp(downloadingAppId)
    if (appInfo != null) {
        val updatedDownloadedDepots = (appInfo.downloadedDepots + entitledDepotIds).distinct()
        val updatedDlcDepots = (appInfo.dlcDepots + selectedDlcAppIds).distinct()
        appInfoDao.update(
            appInfo.copy(
                isDownloaded = true,
                downloadedDepots = updatedDownloadedDepots.sorted(),
                dlcDepots = updatedDlcDepots.sorted(),
                branch = branch,
            ),
        )
    } else {
        appInfoDao.insert(
            AppInfo(
                downloadingAppId,
                isDownloaded = true,
                downloadedDepots = entitledDepotIds.sorted(),
                dlcDepots = selectedDlcAppIds.sorted(),
                branch = branch,
            ),
        )
    }

    downloadInfo.downloadingAppIds.removeIf { it == downloadingAppId }

    if (downloadInfo.downloadingAppIds.isEmpty()) {
        withContext(Dispatchers.IO) {
            MarkerUtils.addMarker(appDirPath, Marker.DOWNLOAD_COMPLETE_MARKER)
            MarkerUtils.removeMarker(appDirPath, Marker.STEAM_DLL_REPLACED)
            MarkerUtils.removeMarker(appDirPath, Marker.STEAM_COLDCLIENT_USED)
        }

        downloadingAppInfoDao.deleteApp(downloadInfo.gameId)
        downloadInfo.clearPersistedBytesDownloaded(appDirPath)

        val appId = downloadInfo.gameId
        val steamId = userSteamId
        val containerId = "${GameSource.STEAM.name}_$appId"
        val isUtilityApp = appId == LsfgVkManager.LOSSLESS_SCALING_APP_ID
        if (!isUtilityApp) {
            if (steamId != null && !ContainerUtils.isLocalSavesOnly(context, containerId)) {
                downloadInfo.setPostInstallSyncing(true)
                downloadInfo.updateStatusMessage("Syncing saves...")
                PluviaApp.events.emit(AndroidEvent.PostInstallSyncStatusChanged(appId, true))
                try {
                    val container = ContainerUtils.getOrCreateContainer(context, containerId)
                    val prefixToPath: (String) -> String = { prefix ->
                        PathType.from(prefix).toAbsPath(container, appId, steamId.accountID)
                    }
                    val postSyncInfo = forceSyncUserFiles(
                        appId = appId,
                        prefixToPath = prefixToPath,
                        preferredSave = SaveLocation.Remote,
                        parentScope = parentScope,
                    ).await()
                    if (postSyncInfo.syncResult !in setOf(SyncResult.Success, SyncResult.UpToDate)) {
                        Timber.w("[PostInstallSync] Cloud save sync finished with ${postSyncInfo.syncResult} for app $appId")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "[PostInstallSync] Cloud save sync failed for app $appId")
                } finally {
                    downloadInfo.setPostInstallSyncing(false)
                    downloadInfo.updateStatusMessage(null)
                    PluviaApp.events.emit(AndroidEvent.PostInstallSyncStatusChanged(appId, false))
                }
            }
        } else {
            Timber.d("Skipped container creation Lossless Scaling")
            PluviaApp.events.emit(AndroidEvent.PostInstallSyncStatusChanged(appId, false))
        }
    }
}

internal class AppDownloadListener(
    private val manager: SteamManager,
    private val downloadInfo: DownloadInfo,
    private val depotIdToIndex: Map<Int, Int>,
) : IDownloadListener {
    private val depotCumulativeCompressedBytes = mutableMapOf<Int, Long>()

    override fun onItemAdded(item: DownloadItem) {
        Timber.d("Item ${item.appId} added to queue")
    }

    override fun onDownloadStarted(item: DownloadItem) {
        Timber.i("Item ${item.appId} download started")
    }

    override fun onDownloadCompleted(item: DownloadItem) {
        Timber.i("Item ${item.appId} download completed")
    }

    override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
        Timber.e(error, "Item ${item.appId} failed to download")
        downloadInfo.failedToDownload()

        runBlocking {
            manager.downloadingAppInfoDao.deleteApp(downloadInfo.gameId)
        }

        manager.removeDownloadJob(downloadInfo.gameId)
        SnackbarManager.show(manager.context.getString(R.string.download_failed_try_again))
    }

    override fun onStatusUpdate(message: String) {
        Timber.d("Download status: $message")
        downloadInfo.updateStatusMessage(message)
    }

    override fun onChunkCompleted(
        depotId: Int,
        depotPercentComplete: Float,
        compressedBytes: Long,
        uncompressedBytes: Long,
    ) {
        manager.clearDepotKeyPrep(downloadInfo.gameId, owner = downloadInfo)

        val previousBytes = depotCumulativeCompressedBytes[depotId] ?: 0L
        val deltaBytes = compressedBytes - previousBytes
        depotCumulativeCompressedBytes[depotId] = compressedBytes

        if (deltaBytes > 0L) {
            downloadInfo.updateBytesDownloaded(deltaBytes, System.currentTimeMillis())
        }

        depotIdToIndex[depotId]?.let { index ->
            downloadInfo.setProgress(depotPercentComplete, index)
        }

        downloadInfo.persistProgressSnapshot()
    }

    override fun onDepotCompleted(depotId: Int, compressedBytes: Long, uncompressedBytes: Long) {
        Timber.i("Depot $depotId completed (compressed: $compressedBytes, uncompressed: $uncompressedBytes)")

        val previousBytes = depotCumulativeCompressedBytes[depotId] ?: 0L
        val deltaBytes = compressedBytes - previousBytes
        depotCumulativeCompressedBytes[depotId] = compressedBytes

        if (deltaBytes > 0L) {
            downloadInfo.updateBytesDownloaded(deltaBytes, System.currentTimeMillis())
        }

        depotIdToIndex[depotId]?.let { index ->
            downloadInfo.setProgress(1f, index)
        }

        downloadInfo.persistProgressSnapshot()
    }
}

fun SteamManager.Companion.filterForDownloadableDepots(
    depot: DepotInfo,
    prefer64Bit: Boolean,
    preferNonDeckWindows: Boolean,
    preferredLanguage: String,
    ownedDlc: Map<Int, DepotInfo>?,
    licensedDepotIds: Set<Int>? = null,
    hasSteamUnlockedBranch: Boolean = false,
    dlcAppIdsWithSingleDepots: Set<Int>? = null,
): Boolean {
    if (depot.manifests.isEmpty() && depot.encryptedManifests.isNotEmpty() && !hasSteamUnlockedBranch)
        return false
    val hasContent = depot.manifests.isNotEmpty() ||
        (hasSteamUnlockedBranch && depot.encryptedManifests.isNotEmpty()) ||
        depot.sharedInstall
    if (!hasContent) return false
    if (!depot.isWindowsCompatible) return false

    val archOk = when (depot.osArch) {
        OSArch.Arch64, OSArch.Unknown -> true
        OSArch.Arch32 -> !prefer64Bit
        else -> false
    }
    if (!archOk) return false

    if (depot.dlcAppId != SteamManager.INVALID_APP_ID && ownedDlc != null && !ownedDlc.containsKey(depot.depotId))
        return false

    if (depot.language.isNotEmpty() && depot.language != preferredLanguage) {
        if (depot.dlcAppId != SteamManager.INVALID_APP_ID) {
            if (dlcAppIdsWithSingleDepots != null && !dlcAppIdsWithSingleDepots.contains(depot.dlcAppId)) {
                return false
            }
        } else {
            return false
        }
    }

    if (depot.dlcAppId == SteamManager.INVALID_APP_ID && !depot.systemDefined && licensedDepotIds != null && depot.depotId !in licensedDepotIds)
        return false

    if (depot.steamDeck && preferNonDeckWindows) return false
    if (depot.realm == SteamRealm.SteamChina) return false

    return true
}

fun SteamManager.filterForDownloadableDepots(
    depot: DepotInfo,
    prefer64Bit: Boolean,
    preferNonDeckWindows: Boolean,
    preferredLanguage: String,
    ownedDlc: Map<Int, DepotInfo>?,
    licensedDepotIds: Set<Int>? = null,
    hasSteamUnlockedBranch: Boolean = false,
    dlcAppIdsWithSingleDepots: Set<Int>? = null,
): Boolean = SteamManager.filterForDownloadableDepots(
    depot,
    prefer64Bit,
    preferNonDeckWindows,
    preferredLanguage,
    ownedDlc,
    licensedDepotIds,
    hasSteamUnlockedBranch,
    dlcAppIdsWithSingleDepots,
)

fun SteamManager.Companion.getDlcAppIdsWithSingleDepot(depots: Map<Int, DepotInfo>): Set<Int> {
    return depots.values
        .filter { it.dlcAppId != SteamManager.INVALID_APP_ID }
        .groupBy { it.dlcAppId }
        .filterValues { it.size == 1 }
        .keys
}

fun SteamManager.getDlcAppIdsWithSingleDepot(depots: Map<Int, DepotInfo>): Set<Int> =
    SteamManager.getDlcAppIdsWithSingleDepot(depots)

fun SteamManager.Companion.eligibleDepots(
    depots: Map<Int, DepotInfo>,
    preferredLanguage: String,
    ownedDlc: Map<Int, DepotInfo>?,
    licensedDepotIds: Set<Int>?,
): Collection<DepotInfo> {
    val dlcAppIdsWithSingleDepots = getDlcAppIdsWithSingleDepot(depots)
    return depots.values.filter { depot ->
        filterForDownloadableDepots(
            depot,
            prefer64Bit = false,
            preferNonDeckWindows = false,
            preferredLanguage = preferredLanguage,
            ownedDlc = ownedDlc,
            licensedDepotIds = licensedDepotIds,
            dlcAppIdsWithSingleDepots = dlcAppIdsWithSingleDepots,
        )
    }
}

fun SteamManager.eligibleDepots(
    depots: Map<Int, DepotInfo>,
    preferredLanguage: String,
    ownedDlc: Map<Int, DepotInfo>?,
    licensedDepotIds: Set<Int>?,
): Collection<DepotInfo> = SteamManager.eligibleDepots(depots, preferredLanguage, ownedDlc, licensedDepotIds)

fun SteamManager.Companion.resolveDownloadableDepots(
    depots: Map<Int, DepotInfo>,
    preferredLanguage: String,
    ownedDlc: Map<Int, DepotInfo>?,
    licensedDepotIds: Set<Int>?,
    hasSteamUnlockedBranch: Boolean = false,
): Map<Int, DepotInfo> {
    val dlcAppIdsWithSingleDepots = getDlcAppIdsWithSingleDepot(depots)
    val effectiveLanguage = SteamUtils.effectiveDepotLanguage(
        depots, preferredLanguage, ownedDlc, licensedDepotIds, hasSteamUnlockedBranch,
    )
    val eligible = eligibleDepots(depots, effectiveLanguage, ownedDlc, licensedDepotIds)
    val has64Bit = eligible.any { it.osArch == OSArch.Arch64 }
    val hasNonDeckWin = eligible.any { !it.steamDeck && it.isWindowsCompatible }
    return depots.filter { (_, depot) ->
        filterForDownloadableDepots(
            depot,
            prefer64Bit = has64Bit,
            preferNonDeckWindows = hasNonDeckWin,
            preferredLanguage = effectiveLanguage,
            ownedDlc = ownedDlc,
            licensedDepotIds = licensedDepotIds,
            dlcAppIdsWithSingleDepots = dlcAppIdsWithSingleDepots,
        )
    }
}

fun SteamManager.resolveDownloadableDepots(
    depots: Map<Int, DepotInfo>,
    preferredLanguage: String,
    ownedDlc: Map<Int, DepotInfo>?,
    licensedDepotIds: Set<Int>?,
    hasSteamUnlockedBranch: Boolean = false,
): Map<Int, DepotInfo> = SteamManager.resolveDownloadableDepots(
    depots,
    preferredLanguage,
    ownedDlc,
    licensedDepotIds,
    hasSteamUnlockedBranch,
)

fun SteamManager.getMainAppDepots(appId: Int, containerLanguage: String): Map<Int, DepotInfo> {
    val appInfo = getAppInfoOf(appId) ?: return emptyMap()
    val ownedDlc = runBlocking { getOwnedAppDlc(appId) }
    val hasSteamUnlockedBranch = runBlocking { getSteamUnlockedBranches(appId).isNotEmpty() }
    val licensedDepots = getLicensedDepotIds(appId).orEmpty().toMutableSet()

    val mainPackageDepotIds = getPkgInfoOf(appId)?.depotIds.orEmpty().toSet()
    val mapDlcDepotIds = mutableMapOf<Int, List<Int>>()
    ownedDlc.forEach { (dlcAppId, _) ->
        val dlcDepotIds = getPkgInfoOf(dlcAppId)?.depotIds.orEmpty()
        licensedDepots.addAll(dlcDepotIds)

        if (mainPackageDepotIds.isNotEmpty()) {
            val dlcOnlyDepotIds = dlcDepotIds.filter { it !in mainPackageDepotIds }
            if (dlcOnlyDepotIds.isNotEmpty()) {
                mapDlcDepotIds[dlcAppId] = dlcOnlyDepotIds
            }
        }
    }

    val baseDepots = resolveDownloadableDepots(appInfo.depots, containerLanguage, ownedDlc, licensedDepots, hasSteamUnlockedBranch)

    val map = mutableMapOf<Int, DepotInfo>()
    baseDepots.forEach { (depotId, info) ->
        val foundDlcAppId = mapDlcDepotIds
            .filter { it.value.contains(info.depotId) }
            .keys.firstOrNull()
        map[depotId] = info.copy(dlcAppId = foundDlcAppId ?: info.dlcAppId)
    }

    return map
}

fun SteamManager.getDownloadableDepots(appId: Int): Map<Int, DepotInfo> {
    val preferredLanguage = containerPreferences.containerLanguage.ifEmpty { "english" }
    return getDownloadableDepots(appId, preferredLanguage)
}

fun SteamManager.getDownloadableDepots(appId: Int, preferredLanguage: String): Map<Int, DepotInfo> {
    val appInfo = getAppInfoOf(appId) ?: return emptyMap()
    val ownedDlc = runBlocking { getOwnedAppDlc(appId) }
    val hasSteamUnlockedBranch = runBlocking { getSteamUnlockedBranches(appId).isNotEmpty() }
    val licensedDepots = getLicensedDepotIds(appId).orEmpty().toMutableSet()

    val map = getMainAppDepots(appId, preferredLanguage).toMutableMap()

    val mainLanguage = SteamUtils.effectiveDepotLanguage(
        appInfo.depots, preferredLanguage, ownedDlc, licensedDepots, hasSteamUnlockedBranch,
    )
    val has64Bit = eligibleDepots(appInfo.depots, mainLanguage, ownedDlc, licensedDepots)
        .any { it.osArch == OSArch.Arch64 }

    val indirectDlcApps = getDownloadableDlcAppsOf(appId).orEmpty()
    indirectDlcApps.forEach { dlcApp ->
        val dlcAppIdsWithSingleDepots = getDlcAppIdsWithSingleDepot(dlcApp.depots)
        val dlcLicensedDepots = getLicensedDepotIds(dlcApp.id)
        val dlcLanguage = SteamUtils.effectiveDepotLanguage(
            dlcApp.depots, preferredLanguage, null, dlcLicensedDepots, hasSteamUnlockedBranch,
        )
        val dlcEligible = eligibleDepots(dlcApp.depots, dlcLanguage, null, dlcLicensedDepots)
        val dlcHasNonDeckWin = dlcEligible.any { !it.steamDeck && it.isWindowsCompatible }
        dlcApp.depots
            .filter { (_, depot) ->
                filterForDownloadableDepots(
                    depot,
                    prefer64Bit = has64Bit,
                    preferNonDeckWindows = dlcHasNonDeckWin,
                    preferredLanguage = dlcLanguage,
                    ownedDlc = null,
                    licensedDepotIds = dlcLicensedDepots,
                    hasSteamUnlockedBranch = hasSteamUnlockedBranch,
                    dlcAppIdsWithSingleDepots = dlcAppIdsWithSingleDepots,
                )
            }
            .forEach { (depotId, depot) ->
                map[depotId] = DepotInfo(
                    depotId = depot.depotId,
                    dlcAppId = dlcApp.id,
                    optionalDlcId = depot.optionalDlcId,
                    depotFromApp = depot.depotFromApp,
                    sharedInstall = depot.sharedInstall,
                    osList = depot.osList,
                    osArch = depot.osArch,
                    language = depot.language,
                    manifests = depot.manifests,
                    encryptedManifests = depot.encryptedManifests,
                    systemDefined = depot.systemDefined,
                    steamDeck = depot.steamDeck,
                )
            }
    }

    return map
}

fun SteamManager.isImageFsInstalled(context: Context): Boolean {
    return ImageFs.find(context).rootDir.exists()
}

fun SteamManager.isImageFsInstallable(context: Context, variant: String): Boolean {
    val imageFs = ImageFs.find(context)
    return if (variant == Container.BIONIC) {
        File(imageFs.filesDir, "imagefs_bionic.txz").exists() || context.assets.list("")?.contains("imagefs_bionic.txz") == true
    } else {
        File(imageFs.filesDir, "imagefs_gamenative.txz").exists() || context.assets.list("")?.contains("imagefs_gamenative.txz") == true
    }
}

fun SteamManager.isSteamInstallable(context: Context): Boolean {
    val imageFs = ImageFs.find(context)
    return File(imageFs.filesDir, "steam.tzst").exists()
}

fun SteamManager.isFileInstallable(context: Context, filename: String): Boolean {
    val imageFs = ImageFs.find(context)
    return File(imageFs.filesDir, filename).exists()
}

suspend fun SteamManager.fetchFile(
    url: String,
    dest: File,
    onProgress: (Float) -> Unit,
) = withContext(Dispatchers.IO) {
    val tmp = File(dest.absolutePath + ".part")
    try {
        val http = SteamUtils.http
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { rsp ->
            check(rsp.isSuccessful) { "HTTP ${rsp.code}" }
            val body = rsp.body ?: error("empty body")
            val total = body.contentLength()
            tmp.outputStream().use { out ->
                body.byteStream().copyTo(out, 8 * 1024) { read ->
                    onProgress(read.toFloat() / total)
                }
            }
            if (total > 0 && tmp.length() != total) {
                tmp.delete()
                error("incomplete download")
            }
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        }
    } catch (e: Exception) {
        tmp.delete()
        throw e
    }
}

suspend fun SteamManager.fetchFileWithFallback(
    fileName: String,
    dest: File,
    context: Context,
    onProgress: (Float) -> Unit,
) = withContext(Dispatchers.IO) {
    val primaryUrl = "https://downloads.gamenative.app/$fileName"
    val fallbackUrl = "https://pub-9fcd5294bd0d4b85a9d73615bf98f3b5.r2.dev/$fileName"
    try {
        fetchFile(primaryUrl, dest, onProgress)
    } catch (e: Exception) {
        Timber.w(e, "Primary download failed; retrying with fallback URL")
        try {
            fetchFile(fallbackUrl, dest, onProgress)
        } catch (e2: Exception) {
            dest.delete()
            throw IOException(
                "Failed to download $fileName. Please check your network connection or try a VPN.",
                e2,
            )
        }
    }
}

private inline fun InputStream.copyTo(
    out: OutputStream,
    bufferSize: Int = DEFAULT_BUFFER_SIZE,
    progress: (Long) -> Unit,
) {
    val buf = ByteArray(bufferSize)
    var bytesRead: Int
    var total = 0L
    while (read(buf).also { bytesRead = it } >= 0) {
        if (bytesRead == 0) continue
        out.write(buf, 0, bytesRead)
        total += bytesRead
        progress(total)
    }
}

fun SteamManager.downloadImageFs(
    onDownloadProgress: (Float) -> Unit,
    parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    variant: String,
    context: Context,
) = parentScope.async {
    Timber.i("imagefs will be downloaded")
    if (variant == Container.BIONIC) {
        val dest = File(context.filesDir, "imagefs_bionic.txz")
        Timber.d("Downloading imagefs_bionic to " + dest.toString())
        fetchFileWithFallback("imagefs_bionic.txz", dest, context, onDownloadProgress)
    } else {
        Timber.d("Downloading imagefs_gamenative to " + File(context.filesDir, "imagefs_gamenative.txz"))
        fetchFileWithFallback(
            "imagefs_gamenative.txz",
            File(context.filesDir, "imagefs_gamenative.txz"),
            context,
            onDownloadProgress,
        )
    }
}

fun SteamManager.downloadImageFsPatches(
    onDownloadProgress: (Float) -> Unit,
    parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    context: Context,
) = parentScope.async {
    Timber.i("imagefs will be downloaded")
    val dest = File(context.filesDir, "imagefs_patches_gamenative.tzst")
    Timber.d("Downloading imagefs_patches_gamenative.tzst to " + dest.toString())
    fetchFileWithFallback("imagefs_patches_gamenative.tzst", dest, context, onDownloadProgress)
}

fun SteamManager.downloadFile(
    onDownloadProgress: (Float) -> Unit,
    parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    context: Context,
    fileName: String,
) = parentScope.async {
    Timber.i("$fileName will be downloaded")
    val dest = File(context.filesDir, fileName)
    Timber.d("Downloading $fileName to " + dest.toString())
    fetchFileWithFallback(fileName, dest, context, onDownloadProgress)
}

fun SteamManager.downloadSteam(
    onDownloadProgress: (Float) -> Unit,
    parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    context: Context,
) = parentScope.async {
    Timber.i("imagefs will be downloaded")
    val dest = File(context.filesDir, "steam.tzst")
    Timber.d("Downloading steam.tzst to " + dest.toString())
    fetchFileWithFallback("steam.tzst", dest, context, onDownloadProgress)
}

internal suspend fun SteamManager.resumePendingWorkshopDownloads() {
    if (downloadPreferences.downloadOnWifiOnly && !hasWifiOrEthernet) {
        Timber.i("Skipping pending workshop downloads — WiFi-only mode and no WiFi")
        return
    }

    val pendingAppIds = appDao.getAppsWithPendingWorkshopDownloads()
    if (pendingAppIds.isEmpty()) return

    val workshopManager = workshopManagerProvider.get()
    Timber.i("Resuming ${pendingAppIds.size} pending workshop download(s)")
    for (appId in pendingAppIds) {
        if (!isAppInstalled(appId)) {
            Timber.i("App $appId no longer installed, clearing stale workshop state")
            appDao.clearWorkshopState(appId)
            continue
        }

        if (getAppDownloadInfo(appId) != null) continue

        val enabledIds = workshopManager.parseEnabledIds(
            appDao.getEnabledWorkshopItemIds(appId),
        )
        if (enabledIds.isEmpty()) {
            appDao.setWorkshopDownloadPending(appId, false)
            continue
        }

        workshopManager.startWorkshopDownload(appId, enabledIds)
    }
}
