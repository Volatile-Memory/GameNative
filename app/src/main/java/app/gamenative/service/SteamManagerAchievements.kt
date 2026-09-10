package app.gamenative.service

import android.content.Context
import app.gamenative.statsgen.StatType
import app.gamenative.statsgen.StatsAchievementsGenerator
import app.gamenative.statsgen.VdfParser
import app.gamenative.utils.ContainerUtils
import com.winlator.xenvironment.ImageFs
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamuserstats.Stats
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

suspend fun SteamManager.generateAchievements(appId: Int, configDirectory: String) {
    val steamUser = _steamUser ?: return
    val userStats = _steamUserStats?.getUserStats(appId, steamUser.steamID!!)?.await() ?: return
    val schemaArray = userStats.schema.toByteArray()
    val generator = StatsAchievementsGenerator()
    val result = generator.generateStatsAchievements(schemaArray, userStats, configDirectory)
    cachedAchievements = result.achievements
    cachedAchievementsAppId = appId

    val nameToBlockBit = result.nameToBlockBit
    Timber.d("nameToBlockBit size=${nameToBlockBit.size} for appId=$appId")
    if (nameToBlockBit.isNotEmpty()) {
        val configDir = File(configDirectory)
        if (!configDir.exists()) configDir.mkdirs()
        val mappingJson = JSONObject()
        nameToBlockBit.forEach { (name, pair) ->
            mappingJson.put(name, JSONArray(listOf(pair.first, pair.second)))
        }
        File(configDir, "achievement_name_to_block.json").writeText(mappingJson.toString(), Charsets.UTF_8)
    }

    val gseDirs = getGseSaveDirs(context, appId)
    seedGseSaveAchievements(gseDirs, result.achievements)
}

suspend fun SteamManager.generateAchievements(context: Context, appId: Int) {
    val configDirectory = findSteamSettingsDir(context, appId) ?: return
    generateAchievements(appId, configDirectory)
}

internal fun SteamManager.seedGseSaveAchievements(
    dirs: List<File>,
    achievements: List<app.gamenative.statsgen.Achievement>,
) {
    if (achievements.isEmpty()) return
    for (dir in dirs) {
        try {
            dir.mkdirs()
            val file = File(dir, "achievements.json")
            val merged = if (file.exists()) {
                try {
                    JSONObject(file.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse existing GSE achievements.json in ${dir.absolutePath}, starting fresh")
                    JSONObject()
                }
            } else {
                JSONObject()
            }

            for (ach in achievements) {
                val existing = if (merged.has(ach.name)) merged.getJSONObject(ach.name) else JSONObject()
                val localEarned = existing.optBoolean("earned", false)
                val steamEarned = ach.unlocked ?: false
                val earned = localEarned || steamEarned
                val localTime = existing.optLong("earned_time", 0L)
                val steamTime = (ach.unlockTimestamp ?: 0).toLong()
                val earnedTime = maxOf(localTime, steamTime)
                existing.put("earned", earned)
                existing.put("earned_time", earnedTime)
                merged.put(ach.name, existing)
            }

            file.writeText(merged.toString(2), Charsets.UTF_8)
            Timber.d("Seeded GSE Saves achievements.json in ${dir.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to seed GSE Saves achievements.json in ${dir.absolutePath}")
        }
    }
}

fun SteamManager.getGseSaveDirs(context: Context, appId: Int): List<File> {
    val imageFs = ImageFs.find(context)
    val dirs = mutableListOf<File>()
    dirs.add(
        File(
            imageFs.rootDir,
            "${ImageFs.WINEPREFIX}/drive_c/users/xuser/AppData/Roaming/GSE Saves/$appId",
        ),
    )
    val accountId = userSteamId?.accountID?.toInt()
        ?: authPreferences.steamUserAccountId.takeIf { it != 0 }
    if (accountId != null) {
        dirs.add(
            File(
                imageFs.rootDir,
                "${ImageFs.WINEPREFIX}/drive_c/Program Files (x86)/Steam/userdata/$accountId/$appId",
            ),
        )
    }
    return dirs
}

fun SteamManager.collectGseUnlocksAndStats(gseDirs: List<File>): Pair<Set<String>, File?> {
    val unlocked = mutableSetOf<String>()
    var statsDir: File? = null
    for (dir in gseDirs) {
        val achFile = File(dir, "achievements.json")
        if (achFile.exists()) {
            try {
                val json = JSONObject(achFile.readText(Charsets.UTF_8))
                for (name in json.keys()) {
                    val entry = json.optJSONObject(name) ?: continue
                    if (entry.optBoolean("earned", false)) {
                        unlocked.add(name)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse achievements.json in ${dir.absolutePath}")
            }
        }
        val sd = File(dir, "stats")
        if (statsDir == null && sd.isDirectory && (sd.listFiles()?.isNotEmpty() == true)) {
            statsDir = sd
        }
    }
    return unlocked to statsDir
}

suspend fun SteamManager.syncAchievementsFromGoldberg(context: Context, appId: Int) {
    val gseSaveDirs = getGseSaveDirs(context, appId).filter { it.isDirectory }
    if (gseSaveDirs.isEmpty()) {
        Timber.d("No GSE save directory found for appId=$appId")
        return
    }

    val (unlockedNames, gseStatsDir) = collectGseUnlocksAndStats(gseSaveDirs)

    if (unlockedNames.isEmpty() && gseStatsDir == null) {
        Timber.d("No earned achievements or stats found in Goldberg output for appId=$appId")
        return
    }

    val configDirectory = findSteamSettingsDir(context, appId)
    if (configDirectory == null) {
        Timber.w("Could not find steam_settings directory for appId=$appId")
        return
    }

    val hasStats = gseStatsDir != null
    Timber.i("Found ${unlockedNames.size} earned achievements and ${if (hasStats) "stats" else "no stats"} for appId=$appId, syncing to Steam")
    val result = storeAchievementUnlocks(appId, configDirectory, unlockedNames, gseStatsDir ?: gseSaveDirs.first().resolve("stats"))
    result.onSuccess {
        Timber.i("Successfully synced achievements and stats to Steam for appId=$appId")
    }.onFailure { e ->
        Timber.e(e, "Failed to sync achievements and stats to Steam for appId=$appId")
    }
}

fun SteamManager.findSteamSettingsDir(context: Context, appId: Int): String? {
    val appDirPath = getAppDirPath(appId)
    val appDirSettings = File(appDirPath, "steam_settings")
    if (File(appDirSettings, "achievement_name_to_block.json").exists()) {
        return appDirSettings.absolutePath
    }

    val container = ContainerUtils.getContainer(context, "STEAM_$appId")
    val coldclientSettings = File(
        container.rootDir,
        ".wine/drive_c/Program Files (x86)/Steam/steam_settings",
    )
    if (File(coldclientSettings, "achievement_name_to_block.json").exists()) {
        return coldclientSettings.absolutePath
    }

    return null
}

suspend fun SteamManager.storeAchievementUnlocks(
    appId: Int,
    configDirectory: String,
    unlockedNames: Set<String>,
    gseStatsDir: File,
): Result<Unit> = runCatching {
    val steamUser = _steamUser ?: throw IllegalStateException("SteamUser is null")
    val userStats = _steamUserStats?.getUserStats(appId, steamUser.steamID!!)?.await()
        ?: throw IllegalStateException("getUserStats failed: userStats is null")
    if (userStats.result != EResult.OK) {
        throw IllegalStateException("getUserStats failed: ${userStats.result}")
    }

    val allStats = mutableMapOf<Int, Int>()

    val mappingFile = File(configDirectory, "achievement_name_to_block.json")
    if (mappingFile.exists() && unlockedNames.isNotEmpty()) {
        val mappingJson = JSONObject(mappingFile.readText(Charsets.UTF_8))
        val nameToBlockBit = mutableMapOf<String, Pair<Int, Int>>()
        for (key in mappingJson.keys()) {
            val arr = mappingJson.optJSONArray(key) ?: continue
            if (arr.length() >= 2) {
                nameToBlockBit[key] = Pair(arr.getInt(0), arr.getInt(1))
            }
        }

        for (block in userStats.achievementBlocks ?: emptyList()) {
            val blockId = (block.achievementId as? Number)?.toInt() ?: continue
            var bitmask = 0
            val unlockTimes = block.unlockTime ?: emptyList()
            for (i in unlockTimes.indices) {
                val t = unlockTimes[i]
                if ((t as? Number)?.toLong() != 0L) bitmask = bitmask or (1 shl i)
            }
            allStats[blockId] = bitmask
        }

        for (name in unlockedNames) {
            val (blockId, bitIndex) = nameToBlockBit[name] ?: continue
            val current = allStats.getOrDefault(blockId, 0)
            allStats[blockId] = current or (1 shl bitIndex)
        }
    }

    if (gseStatsDir.isDirectory) {
        val statNameToId = mutableMapOf<String, Int>()
        try {
            val parsedSchema = VdfParser().binaryLoads(userStats.schema.toByteArray())
            for ((_, appData) in parsedSchema) {
                if (appData !is Map<*, *>) continue
                val statInfo = (appData as Map<String, Any>)["stats"] as? Map<String, Any> ?: continue
                for ((statKey, statData) in statInfo) {
                    if (statData !is Map<*, *>) continue
                    val stat = statData as Map<String, Any>
                    val statType = stat["type"]?.toString() ?: continue
                    if (statType == StatType.STAT_TYPE_BITS || statType == StatType.ACHIEVEMENTS) continue
                    val name = stat["name"]?.toString()?.lowercase() ?: continue
                    val id = statKey.toIntOrNull() ?: continue
                    statNameToId[name] = id
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse schema for stat name mapping, appId=$appId")
        }

        if (statNameToId.isNotEmpty()) {
            for (statFile in gseStatsDir.listFiles() ?: emptyArray()) {
                if (!statFile.isFile) continue
                val statId = statNameToId[statFile.name.lowercase()] ?: continue
                val bytes = statFile.readBytes()
                if (bytes.size >= 4) {
                    val value = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
                    allStats[statId] = value
                    Timber.d("Read GSE stat: ${statFile.name} -> statId=$statId, value=$value")
                }
            }
        }
    }

    if (allStats.isEmpty()) {
        Timber.d("No stats or achievements to store for appId=$appId")
        return@runCatching
    }

    val statsToStore = allStats.map { (id, value) -> Stats(statId = id, statValue = value) }
    Timber.d("storeUserStats: appId=$appId, crcStats=${userStats.crcStats}, stats=$statsToStore")
    val mySteamId = steamUser.steamID!!
    val callback = _steamUserStats!!.storeUserStats(
        appId, statsToStore, mySteamId, mySteamId, userStats.crcStats,
    ).await()
    if (callback.result != EResult.OK) {
        throw IllegalStateException("storeUserStats failed: ${callback.result}")
    }
    if (callback.statsOutOfDate) {
        Timber.w("Stats were out of date on server for appId=$appId")
    }
    if (callback.statsFailedValidation.isNotEmpty()) {
        Timber.w("${callback.statsFailedValidation.size} stats failed validation for appId=$appId")
        callback.statsFailedValidation.forEach { f ->
            Timber.w("  statId=${f.statId} reverted to ${f.revertedStatValue}")
        }
    }
}

internal fun SteamManager.syncPendingOfflineAchievements() {
    offlineAchievementSyncJob?.cancel()
    offlineAchievementSyncJob = scope.launch {
        try {
            delay(2_000)

            if (!isConnected || !isLoggedIn) {
                Timber.tag("achievements").d("Skipping reconnect achievement sync sweep — Steam no longer connected")
                return@launch
            }

            val appsToSync = pendingSyncAppIds.toSet()
            if (appsToSync.isEmpty()) {
                Timber.tag("achievements").d("Skipping reconnect achievement sync sweep — no apps were closed while offline")
                return@launch
            }

            Timber.tag("achievements").i("Syncing offline achievements for ${appsToSync.size} app(s) closed while disconnected")
            for (appId in appsToSync) {
                ensureActive()

                if (!isConnected || !isLoggedIn) {
                    Timber.tag("achievements").d("Stopping reconnect achievement sync sweep — Steam no longer connected")
                    return@launch
                }

                val gseSaveDirs = getGseSaveDirs(context, appId).filter { it.isDirectory }
                if (gseSaveDirs.isEmpty()) {
                    removePendingSyncApp(appId)
                    continue
                }

                val hasOfflineAchievementData = gseSaveDirs.any { dir ->
                    File(dir, "achievements.json").exists() ||
                        (File(dir, "stats").isDirectory && (File(dir, "stats").listFiles()?.isNotEmpty() == true))
                }
                if (!hasOfflineAchievementData) {
                    removePendingSyncApp(appId)
                    continue
                }

                if (!tryAcquireSync(appId)) {
                    Timber.tag("achievements").d("Skipping reconnect achievement sync for appId=$appId — sync already in progress")
                    continue
                }

                try {
                    Timber.tag("achievements").i("Attempting reconnect achievement sync for appId=$appId")
                    syncAchievementsFromGoldberg(context, appId)
                    removePendingSyncApp(appId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("achievements").e(e, "Reconnect achievement sync failed for appId=$appId")
                } finally {
                    releaseSync(appId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("achievements").e(e, "Reconnect achievement sync sweep failed")
        } finally {
            if (offlineAchievementSyncJob?.isActive != true) {
                offlineAchievementSyncJob = null
            }
        }
    }
}

fun SteamManager.syncPendingOfflineAchievements(context: Context) {
    syncPendingOfflineAchievements()
}

