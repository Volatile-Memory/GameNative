package app.gamenative.preferences

import app.gamenative.data.GameSource
import kotlinx.coroutines.flow.Flow

interface DownloadPreferences {
    var downloadOnWifiOnly: Boolean
    var downloadSpeed: Int
    var useExternalStorage: Boolean
    var externalStoragePath: String
    var fetchSteamGridDBImages: Boolean
    var frontendSyncDirSteam: String
    var frontendSyncDirEpic: String
    var frontendSyncDirGog: String
    var frontendSyncDirAmazon: String
    var frontendSyncDirCustom: String

    fun getFrontendSyncDir(source: GameSource): String
    fun setFrontendSyncDir(source: GameSource, path: String)

    val downloadOnWifiOnlyFlow: Flow<Boolean>
    val downloadSpeedFlow: Flow<Int>
    val useExternalStorageFlow: Flow<Boolean>
    val externalStoragePathFlow: Flow<String>
}
