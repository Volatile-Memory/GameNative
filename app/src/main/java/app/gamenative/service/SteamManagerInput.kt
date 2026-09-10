package app.gamenative.service

import app.gamenative.data.SteamControllerConfigDetail
import app.gamenative.utils.FileUtils
import `in`.dragonbra.javasteam.types.KeyValue
import timber.log.Timber
import java.io.File

fun SteamManager.selectSteamControllerConfig(
    details: List<SteamControllerConfigDetail>,
): SteamControllerConfigDetail? {
    if (details.isEmpty()) return null

    val branchPriority = listOf("default", "public")
    val controllerPriority = listOf(
        "controller_xbox360",
        "controller_xboxone",
        "controller_steamcontroller_gordon",
    )

    for (branch in branchPriority) {
        for (controllerType in controllerPriority) {
            val match = details.firstOrNull { detail ->
                detail.controllerType.equals(controllerType, ignoreCase = true) &&
                    detail.enabledBranches.any { it.equals(branch, ignoreCase = true) }
            }
            if (match != null) return match
        }
    }

    return null
}

fun SteamManager.resolveSteamInputManifestFile(
    appId: Int,
    appDirPath: String,
): File? {
    val manifestPath = getAppInfoOf(appId)
        ?.config
        ?.steamInputManifestPath
        ?.trim()
        .orEmpty()
    if (manifestPath.isEmpty()) return null

    return FileUtils.findFileCaseInsensitive(File(appDirPath), manifestPath)
}

fun SteamManager.loadConfigFromManifest(
    manifestFile: File,
): String? {
    if (!manifestFile.exists()) return null
    val manifestDirPath = manifestFile.parentFile?.path ?: return null

    val manifestText = manifestFile.readText(Charsets.UTF_8)
    val configText = try {
        parseManifestForConfig(manifestDirPath, manifestText)
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse Steam Input manifest config at ${manifestFile.path}")
        return null
    }
    return configText ?: manifestText
}

fun SteamManager.parseManifestForConfig(
    manifestDirPath: String,
    manifestText: String,
): String? {
    return try {
        val kv = KeyValue.loadFromString(manifestText) ?: return null
        val actionManifest = if (kv.name?.equals("Action Manifest", ignoreCase = true) == true) {
            kv
        } else {
            kv["Action Manifest"]
        }
        if (actionManifest === KeyValue.INVALID) return null

        val configs = actionManifest["configurations"]
        if (configs === KeyValue.INVALID || configs.children.isEmpty()) {
            throw IllegalStateException("No configurations found in Action Manifest")
        }

        val preferredControllers = listOf(
            "controller_xboxone",
            "controller_steamcontroller_gordon",
            "controller_generic",
            "controller_xbox360",
        )

        for (controllerType in preferredControllers) {
            val controllerBlock = configs[controllerType]
            if (controllerBlock === KeyValue.INVALID) continue

            for (entry in controllerBlock.children) {
                val pathNode = entry["path"]
                val configPath = pathNode.asString().orEmpty()
                if (pathNode === KeyValue.INVALID || configPath.isEmpty()) continue

                val configFile = FileUtils.findFileCaseInsensitive(File(manifestDirPath), configPath)
                    ?: continue
                return configFile.readText(Charsets.UTF_8)
            }
        }

        throw IllegalStateException("No valid controller configuration found in Action Manifest")
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse Steam Input manifest config")
        null
    }
}

fun SteamManager.readBuiltInSteamInputTemplate(fileName: String): String? {
    val assets = context.assets ?: return null
    return runCatching {
        assets.open("steaminput/$fileName").use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()
}

fun SteamManager.readDownloadedSteamInputTemplate(appId: Int): String? {
    val configFile = File(getAppDirPath(appId), SteamManager.STEAM_CONTROLLER_CONFIG_FILENAME)
    if (!configFile.exists()) return null
    return configFile.readText(Charsets.UTF_8)
}

fun SteamManager.resolveSteamControllerVdfText(appId: Int): String? {
    val config = getAppInfoOf(appId)?.config ?: return null
    return when (config.steamControllerTemplateIndex) {
        1 -> readDownloadedSteamInputTemplate(appId)
        13 -> {
            val manifestFile = resolveSteamInputManifestFile(appId, getAppDirPath(appId))
                ?: return null
            loadConfigFromManifest(manifestFile)
        }
        2, 12 -> readBuiltInSteamInputTemplate("controller_xboxone_gamepad_fps.vdf")
        6 -> readBuiltInSteamInputTemplate("controller_xboxone_wasd.vdf")
        4, 5 -> readBuiltInSteamInputTemplate("gamepad_joystick.vdf")
        else -> readBuiltInSteamInputTemplate("gamepad+mouse.vdf")
    }
}
