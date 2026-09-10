package app.gamenative.service

import androidx.room.withTransaction
import app.gamenative.data.SteamApp
import app.gamenative.data.SteamCollectionRepository
import app.gamenative.data.SteamLicense
import `in`.dragonbra.javasteam.enums.ELicenseFlags
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesCloudconfigstoreSteamclient
import app.gamenative.data.SteamUnlockedBranch
import app.gamenative.steam.CloudConfigStoreService
import app.gamenative.steam.SteamCollectionParser
import app.gamenative.utils.CURRENT_UFS_PARSE_VERSION
import app.gamenative.utils.generateSteamApp
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.steamclient.AsyncJobFailedException
import java.lang.NullPointerException
import java.util.Collections
import java.util.EnumSet
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

internal fun SteamManager.continuousPICSChangesChecker(): Job = scope.launch {
    while (isActive && isLoggedIn) {
        delay(60.seconds)
        PICSChangesCheck()
    }
}

internal fun SteamManager.PICSChangesCheck() {
    scope.launch {
        ensureActive()

        try {
            val changesSince = _steamApps!!.picsGetChangesSince(
                lastChangeNumber = authPreferences.lastPICSChangeNumber,
                sendAppChangeList = true,
                sendPackageChangelist = true,
            ).await()

            if (authPreferences.lastPICSChangeNumber == changesSince.currentChangeNumber) {
                Timber.w("Change number was the same as last change number, skipping")
                return@launch
            }

            authPreferences.lastPICSChangeNumber = changesSince.currentChangeNumber

            Timber.d(
                "picsGetChangesSince:" +
                    "\n\tlastChangeNumber: ${changesSince.lastChangeNumber}" +
                    "\n\tcurrentChangeNumber: ${changesSince.currentChangeNumber}" +
                    "\n\tisRequiresFullUpdate: ${changesSince.isRequiresFullUpdate}" +
                    "\n\tisRequiresFullAppUpdate: ${changesSince.isRequiresFullAppUpdate}" +
                    "\n\tisRequiresFullPackageUpdate: ${changesSince.isRequiresFullPackageUpdate}" +
                    "\n\tappChangesCount: ${changesSince.appChanges.size}" +
                    "\n\tpkgChangesCount: ${changesSince.packageChanges.size}",
            )

            launch {
                changesSince.appChanges.values
                    .filter { changeData ->
                        val app = appDao.findApp(changeData.id) ?: return@filter false
                        changeData.changeNumber != app.lastChangeNumber
                    }
                    .map { PICSRequest(id = it.id) }
                    .chunked(SteamManager.MAX_PICS_BUFFER)
                    .forEach { chunk ->
                        ensureActive()
                        Timber.d("onPicsChanges: Queueing ${chunk.size} app(s) for PICS")
                        appPicsChannel.send(chunk)
                    }
            }

            launch {
                val pkgsWithChanges = changesSince.packageChanges.values
                    .filter { changeData ->
                        val pkg = licenseDao.findLicense(changeData.id) ?: return@filter false
                        changeData.changeNumber != pkg.lastChangeNumber
                    }

                if (pkgsWithChanges.isNotEmpty()) {
                    val pkgsForAccessTokens = pkgsWithChanges.filter { it.isNeedsToken }.map { it.id }

                    val accessTokens = _steamApps?.picsGetAccessTokens(emptyList(), pkgsForAccessTokens)
                        ?.await()?.packageTokens ?: emptyMap()

                    ensureActive()

                    pkgsWithChanges
                        .map { PICSRequest(it.id, accessTokens[it.id] ?: 0) }
                        .chunked(SteamManager.MAX_PICS_BUFFER)
                        .forEach { chunk ->
                            Timber.d("onPicsChanges: Queueing ${chunk.size} package(s) for PICS")
                            packagePicsChannel.send(chunk)
                        }
                }
            }
        } catch (e: NullPointerException) {
            Timber.w("No lastPICSChangeNumber, skipping")
        } catch (e: AsyncJobFailedException) {
            Timber.w("AsyncJobFailedException, skipping")
        }
    }
}

internal fun SteamManager.continuousPICSGetProductInfo(): Job = scope.launch {
    launch {
        appPicsChannel.receiveAsFlow()
            .filter { it.isNotEmpty() }
            .buffer(capacity = SteamManager.MAX_PICS_BUFFER, onBufferOverflow = BufferOverflow.SUSPEND)
            .collect { appRequests ->
                Timber.d("Processing ${appRequests.size} app PICS requests")

                ensureActive()
                if (!isLoggedIn) return@collect
                val steamApps = _steamApps ?: return@collect

                try {
                    val callback = steamApps.picsGetProductInfo(
                        apps = appRequests,
                        packages = emptyList(),
                    ).await()

                    callback.results.forEachIndexed { index, picsCallback ->
                        Timber.d(
                            "onPicsProduct: ${index + 1} of ${callback.results.size}" +
                                "\n\tReceived PICS result of ${picsCallback.apps.size} app(s)." +
                                "\n\tReceived PICS result of ${picsCallback.packages.size} package(s).",
                        )

                        ensureActive()
                        val steamAppsMap = picsCallback.apps.values.mapNotNull { app ->
                            val appFromDb = appDao.findApp(app.id)
                            val packageId = appFromDb?.packageId ?: SteamManager.INVALID_PKG_ID
                            val packageFromDb = if (packageId != SteamManager.INVALID_PKG_ID) licenseDao.findLicense(packageId) else null
                            val ownerAccountId = packageFromDb?.ownerAccountId ?: emptyList()

                            val ufsParseVersionOutdated = appFromDb != null && appFromDb.ufsParseVersion < CURRENT_UFS_PARSE_VERSION

                            if (app.changeNumber != appFromDb?.lastChangeNumber || ufsParseVersionOutdated) {
                                val newApp = app.keyValues.generateSteamApp().copy(
                                    packageId = packageId,
                                    ownerAccountId = ownerAccountId,
                                    receivedPICS = true,
                                    lastChangeNumber = app.changeNumber,
                                    licenseFlags = packageFromDb?.licenseFlags ?: EnumSet.noneOf(ELicenseFlags::class.java),
                                )
                                if (ufsParseVersionOutdated && newApp.ufs.saveFilePatterns.any { it.uploadRoot != it.root || it.uploadPath != it.path }) {
                                    changeNumbersDao.insert(app.id, 0L)
                                }
                                newApp
                            } else {
                                null
                            }
                        }

                        if (steamAppsMap.isNotEmpty()) {
                            Timber.i("Inserting ${steamAppsMap.size} PICS apps to database")
                            db.withTransaction {
                                appDao.insertAll(steamAppsMap)
                            }
                        }
                    }
                } catch (e: AsyncJobFailedException) {
                    Timber.w("Could not get PICS product info $e")
                }
            }
    }

    launch {
        packagePicsChannel.receiveAsFlow()
            .filter { it.isNotEmpty() }
            .buffer(capacity = SteamManager.MAX_PICS_BUFFER, onBufferOverflow = BufferOverflow.SUSPEND)
            .collect { packageRequests ->
                Timber.d("Processing ${packageRequests.size} package PICS requests")

                ensureActive()
                if (!isLoggedIn) return@collect
                val steamApps = _steamApps ?: return@collect

                val callback = steamApps.picsGetProductInfo(
                    apps = emptyList(),
                    packages = packageRequests,
                ).await()

                callback.results.forEach { picsCallback ->
                    if (!isLoggedIn) return@collect
                    val queue = Collections.synchronizedList(mutableListOf<Int>())

                    db.withTransaction {
                        val accountId = userSteamId?.accountID?.toInt()
                        val packageLicenses: Map<Int, SteamLicense> = if (accountId != null) {
                            val packageIds = picsCallback.packages.values.map { it.id }
                            licenseDao.findLicenses(packageIds).associateBy { it.packageId }
                        } else {
                            emptyMap()
                        }
                        val userOwnedPackageIds: Set<Int> = if (accountId != null) {
                            packageLicenses.values
                                .filter { it.ownerAccountId.contains(accountId) }
                                .mapTo(HashSet()) { it.packageId }
                        } else {
                            emptySet()
                        }

                        fun pkgRank(pkgId: Int): Int {
                            if (pkgId !in userOwnedPackageIds) return 0
                            val expired = packageLicenses[pkgId]?.licenseFlags?.contains(ELicenseFlags.Expired) == true
                            return if (expired) 1 else 2
                        }

                        val orderedPackages = picsCallback.packages.values.sortedBy { pkgRank(it.id) }

                        orderedPackages.forEach { pkg ->
                            val appIds = pkg.keyValues["appids"].children.map { it.asInteger() }
                            licenseDao.updateApps(pkg.id, appIds)

                            val depotIds = pkg.keyValues["depotids"].children.map { it.asInteger() }
                            licenseDao.updateDepots(pkg.id, depotIds)

                            appIds.forEach { appid ->
                                val existing = appDao.findApp(appid)
                                if (existing == null) {
                                    appDao.insert(SteamApp(id = appid, packageId = pkg.id))
                                    return@forEach
                                }
                                if (existing.packageId == pkg.id) {
                                    return@forEach
                                }
                                if (accountId != null && existing.packageId != SteamManager.INVALID_PKG_ID) {
                                    val existingLicense = packageLicenses[existing.packageId]
                                        ?: licenseDao.findLicense(existing.packageId)
                                    val existingRank = when {
                                        existingLicense == null -> 0
                                        !existingLicense.ownerAccountId.contains(accountId) -> 0
                                        ELicenseFlags.Expired in existingLicense.licenseFlags -> 1
                                        else -> 2
                                    }
                                    if (existingRank > pkgRank(pkg.id)) {
                                        return@forEach
                                    }
                                }
                                appDao.update(existing.copy(packageId = pkg.id))
                            }

                            queue.addAll(appIds)
                        }
                    }

                    try {
                        steamApps.picsGetAccessTokens(
                            appIds = queue,
                            packageIds = emptyList(),
                        ).await()
                            .appTokens
                            .forEach { (key, value) ->
                                appTokens[key] = value
                            }

                        queue
                            .map { PICSRequest(id = it, accessToken = appTokens[it] ?: 0L) }
                            .chunked(SteamManager.MAX_PICS_BUFFER)
                            .forEach { chunk ->
                                Timber.d("bufferedPICSGetProductInfo: Queueing ${chunk.size} for PICS")
                                appPicsChannel.send(chunk)
                            }
                    } catch (e: AsyncJobFailedException) {
                        Timber.w("Could not get PICS product info $e")
                    }
                }
            }
    }
}

suspend fun SteamManager.refreshOwnedGamesFromServer(): Int = withContext(Dispatchers.IO) {
    val unifiedFriends = _unifiedFriends ?: return@withContext 0
    val steamId = userSteamId ?: return@withContext 0

    runCatching {
        val ownedGames = unifiedFriends.getOwnedGames(steamId.convertToUInt64())
        val remoteAppIds = ownedGames.map { it.appId }.filter { it > 0 }.toSet()
        if (remoteAppIds.isEmpty()) {
            return@runCatching 0
        }

        val localAppIds = appDao.getAllAppIds().toSet()
        val missingAppIds = remoteAppIds - localAppIds
        if (missingAppIds.isEmpty()) {
            return@runCatching 0
        }

        missingAppIds
            .chunked(SteamManager.MAX_PICS_BUFFER)
            .forEach { chunk ->
                val requests = chunk.map { PICSRequest(id = it) }
                appPicsChannel.send(requests)
            }

        missingAppIds.size
    }.onFailure { error ->
        Timber.tag("SteamManager").e(error, "Failed to refresh owned games from server")
    }.getOrDefault(0)
}

suspend fun SteamManager.checkDlcOwnershipViaPICSBatch(dlcAppIds: Set<Int>): Set<Int> {
    if (dlcAppIds.isEmpty()) return emptySet()
    val steamApps = _steamApps ?: return emptySet()

    try {
        val tokens = steamApps.picsGetAccessTokens(
            appIds = dlcAppIds.toList(),
            packageIds = emptyList(),
        ).await()

        Timber.d("Access tokens response:")
        Timber.d("  - Granted tokens: ${tokens.appTokens.keys}")
        Timber.d("  - Denied tokens: ${tokens.appTokensDenied}")

        val ownedAppIds = tokens.appTokens.keys.filter { it in dlcAppIds }.toSet()
        Timber.d("Owned appIds (from tokens): $ownedAppIds")

        if (ownedAppIds.isEmpty()) {
            Timber.w("No owned DLCs found via access tokens")
            return emptySet()
        }

        val picsRequests = ownedAppIds.mapNotNull { appId ->
            val token = tokens.appTokens[appId] ?: return@mapNotNull null
            PICSRequest(id = appId, accessToken = token)
        }

        Timber.d("Created ${picsRequests.size} PICS requests")
        if (picsRequests.isEmpty()) return emptySet()

        val chunkSize = 100
        val allOwnedAppIds = mutableSetOf<Int>()

        picsRequests.chunked(chunkSize).forEach { chunk ->
            Timber.d("Querying PICS chunk with ${chunk.size} apps")
            val callback = steamApps.picsGetProductInfo(
                apps = chunk,
                packages = emptyList(),
            ).await()

            callback.results.forEach { picsCallback ->
                val returnedAppIds = picsCallback.apps.keys
                Timber.d("  PICS result: ${returnedAppIds.size} apps returned")
                allOwnedAppIds.addAll(picsCallback.apps.keys)
            }
        }

        Timber.i("Final owned DLC appIds: $allOwnedAppIds")
        Timber.i("Total owned: ${allOwnedAppIds.size} out of ${dlcAppIds.size} checked")

        return allOwnedAppIds
    } catch (e: Exception) {
        Timber.e(e, "Failed to check DLC ownership via PICS batch for ${dlcAppIds.size} appIds")
        return emptySet()
    }
}

internal suspend fun SteamManager.fetchSteamCollections() {
    val client = steamClient
    val fetchSteamId = client?.steamID?.convertToUInt64()
    fun sameSession() = isLoggedIn && steamClient?.steamID?.convertToUInt64() == fetchSteamId
    val um = client?.getHandler<SteamUnifiedMessages>()
    if (um == null) {
        Timber.tag("SteamCollections").w("UnifiedMessages handler unavailable; cannot fetch collections")
        return
    }
    val service = try {
        um.createService(CloudConfigStoreService::class.java)
    } catch (t: Throwable) {
        Timber.tag("SteamCollections").e(t, "Cannot create CloudConfigStore service; keeping cached snapshot")
        return
    }

    val request = SteammessagesCloudconfigstoreSteamclient.CCloudConfigStore_Download_Request.newBuilder()
        .addVersions(
            SteammessagesCloudconfigstoreSteamclient.CCloudConfigStore_NamespaceVersion.newBuilder()
                .setEnamespace(1)
                .setVersion(0L),
        )
        .build()

    val backoffsMs = longArrayOf(3_000L, 8_000L, 20_000L)
    val maxAttempts = backoffsMs.size + 1
    repeat(maxAttempts) { attempt ->
        if (!sameSession()) return
        try {
            val job = service.download(request)
            job.timeout = 30_000L
            val response = job.toFuture().await()

            val body = response.body.build()
            val rawEntries = body.dataList.flatMap { ns ->
                ns.entriesList.map { entry ->
                    SteamCollectionParser.RawEntry(
                        key = entry.key,
                        value = entry.value,
                        isDeleted = entry.isDeleted,
                    )
                }
            }

            val parsed = SteamCollectionParser.parse(rawEntries)
            Timber.tag("SteamCollections").i(
                "Fetched ${parsed.collections.size} Steam collections " +
                    "(${parsed.skippedDynamicCount} dynamic skipped) on attempt ${attempt + 1}",
            )
            if (sameSession()) SteamCollectionRepository.update(parsed)
            return
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val lastAttempt = attempt == maxAttempts - 1
            Timber.tag("SteamCollections").w(
                t,
                "Steam collections fetch attempt ${attempt + 1}/$maxAttempts failed" +
                    if (lastAttempt) "; keeping cached snapshot" else "; retrying",
            )
            if (!lastAttempt) delay(backoffsMs[attempt])
        }
    }
}

suspend fun SteamManager.isUpdatePending(
    appId: Int,
    branch: String = "public",
): Boolean = withContext(Dispatchers.IO) {
    if (!isConnected) return@withContext false

    val steamApps = _steamApps ?: return@withContext false

    val pics = steamApps.picsGetProductInfo(
        apps = listOf(PICSRequest(id = appId)),
        packages = emptyList(),
    ).await()

    val remoteAppInfo = pics.results
        .firstOrNull()
        ?.apps
        ?.values
        ?.firstOrNull()
        ?: return@withContext false

    val remoteSteamApp = remoteAppInfo.keyValues.generateSteamApp()
    val localSteamApp = getAppInfoOf(appId) ?: return@withContext true

    getDownloadableDepots(appId).keys.any { depotId ->
        val remoteManifest = remoteSteamApp.depots[depotId]?.manifests?.get(branch)
        val localManifest = localSteamApp.depots[depotId]?.manifests?.get(branch)
        if (remoteManifest == null) return@any false
        remoteManifest.gid != localManifest?.gid
    }
}

suspend fun SteamManager.checkPrivateBranchPassword(appId: Int, password: String): Map<String, ByteArray> =
    withContext(Dispatchers.IO) {
        val steamApps = _steamApps ?: return@withContext emptyMap()
        try {
            val callback = steamApps.checkAppBetaPassword(appId, password).await()
            if (callback.result == EResult.OK) {
                for ((branchName, _) in callback.betaPasswords) {
                    steamUnlockedBranchDao.insert(SteamUnlockedBranch(appId, branchName, password))
                }
                callback.betaPasswords
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            Timber.e(e, "checkPrivateBranchPassword failed for app $appId")
            emptyMap()
        }
    }

