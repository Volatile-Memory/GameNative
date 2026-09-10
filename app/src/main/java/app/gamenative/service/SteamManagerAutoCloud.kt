package app.gamenative.service

import android.content.Context
import app.gamenative.data.GameProcessInfo
import app.gamenative.data.PostSyncInfo
import app.gamenative.data.UserFileInfo
import app.gamenative.enums.SaveLocation
import app.gamenative.enums.SyncResult
import app.gamenative.utils.SteamUtils
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientObjects.ECloudPendingRemoteOperation
import `in`.dragonbra.javasteam.steam.handlers.steamapps.GamePlayedInfo
import `in`.dragonbra.javasteam.steam.steamclient.AsyncJobFailedException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import timber.log.Timber

data class FileChanges(
    val filesDeleted: List<UserFileInfo>,
    val filesModified: List<UserFileInfo>,
    val filesCreated: List<UserFileInfo>,
)

suspend fun SteamManager.notifyRunningProcesses(vararg gameProcesses: GameProcessInfo) = withContext(Dispatchers.IO) {
    if (isConnected) {
        val gamesPlayed = gameProcesses.mapNotNull { gameProcess ->
            getAppInfoOf(gameProcess.appId)?.let { appInfo ->
                getPkgInfoOf(gameProcess.appId)?.let { pkgInfo ->
                    appInfo.branches[gameProcess.branch]?.let { branch ->
                        val processId = gameProcess.processes
                            .firstOrNull { it.parentIsSteam }
                            ?.processId
                            ?: gameProcess.processes.firstOrNull()?.processId
                            ?: 0

                        val userAccountId = userSteamId!!.accountID.toInt()
                        GamePlayedInfo(
                            gameId = gameProcess.appId.toLong(),
                            processId = processId,
                            ownerId = if (pkgInfo.ownerAccountId.contains(userAccountId)) {
                                userAccountId
                            } else {
                                pkgInfo.ownerAccountId.first()
                            },
                            launchSource = 100,
                            gameBuildId = branch.buildId.toInt(),
                            processIdList = gameProcess.processes,
                        )
                    }
                }
            }
        }

        Timber.i(
            "GameProcessInfo:%s",
            gamesPlayed.joinToString("\n") { game ->
                """
                |   processId: ${game.processId}
                |   gameId: ${game.gameId}
                |   processes: ${
                    game.processIdList.joinToString("\n") { process ->
                        """
                        |   processId: ${process.processId}
                        |   processIdParent: ${process.processIdParent}
                        |   parentIsSteam: ${process.parentIsSteam}
                        """.trimMargin()
                    }
                }
                """.trimMargin()
            },
        )

        _steamApps?.notifyGamesPlayed(
            gamesPlayed = gamesPlayed,
            clientOsType = EOSType.WinUnknown,
        )
    }
}

suspend fun SteamManager.kickPlayingSession(onlyGame: Boolean = true): Boolean = withContext(Dispatchers.IO) {
    val user = _steamUser ?: return@withContext false
    try {
        _isPlayingBlocked.value = true
        user.kickPlayingSession(onlyStopGame = onlyGame)

        // Wait for PlayingSessionStateCallback to indicate unblocked
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (!_isPlayingBlocked.value) return@withContext true
            delay(100)
        }
        false
    } catch (_: Exception) {
        false
    }
}

fun SteamManager.beginLaunchApp(
    appId: Int,
    parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    ignorePendingOperations: Boolean = false,
    preferredSave: SaveLocation = SaveLocation.None,
    prefixToPath: (String) -> String,
    isOffline: Boolean = false,
    onProgress: ((message: String, progress: Float) -> Unit)? = null,
): Deferred<PostSyncInfo> = parentScope.async {
    if (isOffline || !isConnected) {
        return@async PostSyncInfo(SyncResult.UpToDate)
    }
    if (!tryAcquireSync(appId)) {
        Timber.w("Cannot launch app when sync already in progress for appId=$appId")
        return@async PostSyncInfo(SyncResult.InProgress)
    }

    try {
        SteamUtils.migrateGSESavesToSteamUserdata(context, appId)

        var syncResult = PostSyncInfo(SyncResult.UnknownFail)
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            try {
                (authPreferences.clientId ?: 0L).let { clientId ->
                    getAppInfoOf(appId)?.let { appInfo ->
                        _steamCloud?.let { steamCloud ->
                            val postSyncInfo = SteamAutoCloud.syncUserFiles(
                                appInfo = appInfo,
                                clientId = clientId,
                                steamManager = this@beginLaunchApp,
                                steamCloud = steamCloud,
                                preferredSave = preferredSave,
                                parentScope = parentScope,
                                prefixToPath = prefixToPath,
                                onProgress = onProgress,
                            ).await()

                            postSyncInfo?.let { info ->
                                syncResult = info

                                if (info.syncResult == SyncResult.Success || info.syncResult == SyncResult.UpToDate) {
                                    Timber.i(
                                        "Signaling app launch:\n\tappId: %d\n\tclientId: %s\n\tosType: %s",
                                        appId,
                                        clientId,
                                        EOSType.WinUnknown,
                                    )

                                    val pendingRemoteOperations = steamCloud.signalAppLaunchIntent(
                                        appId = appId,
                                        clientId = clientId,
                                        machineName = SteamUtils.getMachineName(context),
                                        ignorePendingOperations = ignorePendingOperations,
                                        osType = EOSType.WinUnknown,
                                    ).await()

                                    if (pendingRemoteOperations.isNotEmpty() && !ignorePendingOperations) {
                                        syncResult = PostSyncInfo(
                                            syncResult = SyncResult.PendingOperations,
                                            pendingRemoteOperations = pendingRemoteOperations,
                                        )
                                    } else if (ignorePendingOperations &&
                                        pendingRemoteOperations.any {
                                            it.operation == ECloudPendingRemoteOperation.k_ECloudPendingRemoteOperationAppSessionActive
                                        }
                                    ) {
                                        _steamUser?.kickPlayingSession()
                                    }
                                }
                            }
                        }
                    }
                }
                break
            } catch (e: AsyncJobFailedException) {
                if (attempt == maxAttempts) {
                    Timber.e(e, "Cloud sync failed after $maxAttempts attempts")
                    syncResult = PostSyncInfo(SyncResult.UnknownFail)
                } else {
                    Timber.w("Cloud sync attempt $attempt failed (AsyncJobFailedException), retrying...")
                    delay(1000L * attempt)
                }
            }
        }

        return@async syncResult
    } finally {
        releaseSync(appId)
    }
}

fun SteamManager.forceSyncUserFiles(
    appId: Int,
    prefixToPath: (String) -> String,
    preferredSave: SaveLocation = SaveLocation.None,
    parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    overrideLocalChangeNumber: Long? = null,
): Deferred<PostSyncInfo> = parentScope.async {
    if (!tryAcquireSync(appId)) {
        Timber.w("Cannot force sync when sync already in progress for appId=$appId")
        return@async PostSyncInfo(SyncResult.InProgress)
    }

    try {
        SteamUtils.migrateGSESavesToSteamUserdata(context, appId)

        var syncResult = PostSyncInfo(SyncResult.UnknownFail)
        val maxAttempts = 3
        for (attempt in 1..maxAttempts) {
            try {
                (authPreferences.clientId ?: 0L).let { clientId ->
                    getAppInfoOf(appId)?.let { appInfo ->
                        _steamCloud?.let { steamCloud ->
                            val postSyncInfo = SteamAutoCloud.syncUserFiles(
                                appInfo = appInfo,
                                clientId = clientId,
                                steamManager = this@forceSyncUserFiles,
                                steamCloud = steamCloud,
                                preferredSave = preferredSave,
                                parentScope = parentScope,
                                prefixToPath = prefixToPath,
                                overrideLocalChangeNumber = overrideLocalChangeNumber,
                            ).await()

                            postSyncInfo?.let { info ->
                                syncResult = info
                                Timber.i("Force cloud sync completed for app $appId with result: ${info.syncResult}")
                            }
                        }
                    }
                }
                break
            } catch (e: AsyncJobFailedException) {
                if (attempt == maxAttempts) {
                    Timber.e(e, "Force cloud sync failed after $maxAttempts attempts")
                } else {
                    Timber.w("Force cloud sync attempt $attempt failed (AsyncJobFailedException), retrying...")
                    delay(1000L * attempt)
                }
            }
        }

        return@async syncResult
    } finally {
        releaseSync(appId)
    }
}

suspend fun SteamManager.closeApp(
    context: Context,
    appId: Int,
    isOffline: Boolean,
    prefixToPath: (String) -> String,
) = withContext(Dispatchers.IO) {
    async {
        if (isOffline || !isConnected) {
            addPendingSyncApp(appId)
            return@async
        }

        if (!tryAcquireSync(appId)) {
            Timber.w("Cannot close app when sync already in progress for appId=$appId")
            return@async
        }

        try {
            try {
                syncAchievementsFromGoldberg(context, appId)
            } catch (e: Exception) {
                Timber.e(e, "Achievement sync failed for appId=$appId, continuing with cloud save sync")
            }

            val maxAttempts = 3
            for (attempt in 1..maxAttempts) {
                try {
                    (authPreferences.clientId ?: 0L).let { clientId ->
                        getAppInfoOf(appId)?.let { appInfo ->
                            _steamCloud?.let { steamCloud ->
                                val postSyncInfo = SteamAutoCloud.syncUserFiles(
                                    appInfo = appInfo,
                                    clientId = clientId,
                                    steamManager = this@closeApp,
                                    steamCloud = steamCloud,
                                    parentScope = this,
                                    prefixToPath = prefixToPath,
                                ).await()

                                steamCloud.signalAppExitSyncDone(
                                    appId = appId,
                                    clientId = clientId,
                                    uploadsCompleted = postSyncInfo?.uploadsCompleted == true,
                                    uploadsRequired = postSyncInfo?.uploadsRequired == false,
                                )
                            }
                        }
                    }
                    break
                } catch (e: AsyncJobFailedException) {
                    if (attempt == maxAttempts) {
                        Timber.e(e, "Close app sync failed after $maxAttempts attempts")
                    } else {
                        Timber.w("Close app sync attempt $attempt failed (AsyncJobFailedException), retrying...")
                        delay(1000L * attempt)
                    }
                }
            }
        } finally {
            releaseSync(appId)
            removePendingSyncApp(appId)
        }
    }
}
