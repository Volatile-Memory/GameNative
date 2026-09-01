package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.data.GameSource
import app.gamenative.di.PluviaDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDownloadPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : DownloadPreferences {

    private companion object {
        val DOWNLOAD_ON_WIFI_ONLY = booleanPreferencesKey("download_on_wifi_only")
        val DOWNLOAD_SPEED = intPreferencesKey("download_speed")
        val USE_EXTERNAL_STORAGE = booleanPreferencesKey("use_external_storage")
        val EXTERNAL_STORAGE_PATH = stringPreferencesKey("external_storage_path")
        val FETCH_STEAMGRIDDB_IMAGES = booleanPreferencesKey("fetch_steamgriddb_images")
        val FRONTEND_SYNC_DIR_STEAM = stringPreferencesKey("frontend_sync_dir_steam")
        val FRONTEND_SYNC_DIR_EPIC = stringPreferencesKey("frontend_sync_dir_epic")
        val FRONTEND_SYNC_DIR_GOG = stringPreferencesKey("frontend_sync_dir_gog")
        val FRONTEND_SYNC_DIR_AMAZON = stringPreferencesKey("frontend_sync_dir_amazon")
        val FRONTEND_SYNC_DIR_CUSTOM = stringPreferencesKey("frontend_sync_dir_custom")
    }

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var downloadOnWifiOnly: Boolean
        get() = getPref(DOWNLOAD_ON_WIFI_ONLY, true)
        set(value) = setPref(DOWNLOAD_ON_WIFI_ONLY, value)

    override var downloadSpeed: Int
        get() = getPref(DOWNLOAD_SPEED, 24)
        set(value) = setPref(DOWNLOAD_SPEED, value)

    override var useExternalStorage: Boolean
        get() = getPref(USE_EXTERNAL_STORAGE, false)
        set(value) {
            setPref(USE_EXTERNAL_STORAGE, value)
            setPref(EXTERNAL_STORAGE_PATH, "")
        }

    override var externalStoragePath: String
        get() = getPref(EXTERNAL_STORAGE_PATH, "")
        set(value) = setPref(EXTERNAL_STORAGE_PATH, value)

    override var fetchSteamGridDBImages: Boolean
        get() = getPref(FETCH_STEAMGRIDDB_IMAGES, true)
        set(value) = setPref(FETCH_STEAMGRIDDB_IMAGES, value)

    override var frontendSyncDirSteam: String
        get() = getPref(FRONTEND_SYNC_DIR_STEAM, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_STEAM, value)

    override var frontendSyncDirEpic: String
        get() = getPref(FRONTEND_SYNC_DIR_EPIC, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_EPIC, value)

    override var frontendSyncDirGog: String
        get() = getPref(FRONTEND_SYNC_DIR_GOG, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_GOG, value)

    override var frontendSyncDirAmazon: String
        get() = getPref(FRONTEND_SYNC_DIR_AMAZON, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_AMAZON, value)

    override var frontendSyncDirCustom: String
        get() = getPref(FRONTEND_SYNC_DIR_CUSTOM, "")
        set(value) = setPref(FRONTEND_SYNC_DIR_CUSTOM, value)

    override fun getFrontendSyncDir(source: GameSource): String = when (source) {
        GameSource.STEAM -> frontendSyncDirSteam
        GameSource.EPIC -> frontendSyncDirEpic
        GameSource.GOG -> frontendSyncDirGog
        GameSource.AMAZON -> frontendSyncDirAmazon
        GameSource.CUSTOM_GAME -> frontendSyncDirCustom
    }

    override fun setFrontendSyncDir(source: GameSource, path: String) {
        when (source) {
            GameSource.STEAM -> frontendSyncDirSteam = path
            GameSource.EPIC -> frontendSyncDirEpic = path
            GameSource.GOG -> frontendSyncDirGog = path
            GameSource.AMAZON -> frontendSyncDirAmazon = path
            GameSource.CUSTOM_GAME -> frontendSyncDirCustom = path
        }
    }

    override val downloadOnWifiOnlyFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[DOWNLOAD_ON_WIFI_ONLY] ?: true }
        .distinctUntilChanged()

    override val downloadSpeedFlow: Flow<Int> = dataStore.data
        .map { pref -> pref[DOWNLOAD_SPEED] ?: 24 }
        .distinctUntilChanged()

    override val useExternalStorageFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[USE_EXTERNAL_STORAGE] ?: false }
        .distinctUntilChanged()

    override val externalStoragePathFlow: Flow<String> = dataStore.data
        .map { pref -> pref[EXTERNAL_STORAGE_PATH] ?: "" }
        .distinctUntilChanged()
}
