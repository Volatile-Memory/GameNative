package app.gamenative.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.gamenative.Crypto
import app.gamenative.core.coroutines.ApplicationScope
import app.gamenative.di.PluviaDataStore
import `in`.dragonbra.javasteam.enums.EPersonaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultAuthPreferences @Inject constructor(
    @PluviaDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) : AuthPreferences {

    private companion object {
        val USER_NAME = stringPreferencesKey("user_name")
        val ACCESS_TOKEN_ENC = byteArrayPreferencesKey("access_token_enc")
        val REFRESH_TOKEN_ENC = byteArrayPreferencesKey("refresh_token_enc")
        val CLIENT_ID = longPreferencesKey("client_id")
        val CELL_ID = intPreferencesKey("cell_id")
        val CELL_ID_MANUALLY_SET = booleanPreferencesKey("cell_id_manually_set")
        val PERSONA_STATE = intPreferencesKey("persona_state")
        val STEAM_USER_ACCOUNT_ID = intPreferencesKey("steam_user_account_id")
        val STEAM_USER_STEAM_ID_64 = longPreferencesKey("steam_user_steam_id_64")
        val STEAM_USER_AVATAR_HASH = stringPreferencesKey("steam_user_avatar_hash")
        val STEAM_USER_NAME = stringPreferencesKey("steam_user_name")
        val LAST_PICS_CHANGE_NUMBER = intPreferencesKey("last_pics_change_number")
        val STEAM_OFFLINE_MODE = booleanPreferencesKey("steam_offline_mode")
        val EPIC_OFFLINE_MODE = booleanPreferencesKey("epic_offline_mode")
        val FRIENDS_LIST_HEADER = stringPreferencesKey("friends_list_header")
        val ACK_CHAT_PREVIEW = booleanPreferencesKey("ack_chat_preview")
        val STEAM_GAMES_COUNT = intPreferencesKey("steam_games_count")
    }

    private fun <T> getPref(key: Preferences.Key<T>, defaultValue: T): T = runBlocking {
        dataStore.data.first()[key] ?: defaultValue
    }

    private fun <T> setPref(key: Preferences.Key<T>, value: T) {
        scope.launch {
            dataStore.edit { pref -> pref[key] = value }
        }
    }

    override var username: String
        get() = getPref(USER_NAME, "")
        set(value) = setPref(USER_NAME, value)

    override var accessToken: String
        get() {
            val encryptedBytes = getPref(ACCESS_TOKEN_ENC, ByteArray(0))
            return if (encryptedBytes.isEmpty()) {
                ""
            } else {
                try {
                    String(Crypto.decrypt(encryptedBytes))
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decrypt access token")
                    ""
                }
            }
        }
        set(value) {
            val bytes = if (value.isNotEmpty()) {
                try {
                    Crypto.encrypt(value.toByteArray())
                } catch (e: Exception) {
                    Timber.e(e, "Failed to encrypt access token")
                    ByteArray(0)
                }
            } else {
                ByteArray(0)
            }
            setPref(ACCESS_TOKEN_ENC, bytes)
        }

    override var refreshToken: String
        get() {
            val encryptedBytes = getPref(REFRESH_TOKEN_ENC, ByteArray(0))
            return if (encryptedBytes.isEmpty()) {
                ""
            } else {
                try {
                    String(Crypto.decrypt(encryptedBytes))
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decrypt refresh token")
                    ""
                }
            }
        }
        set(value) {
            val bytes = if (value.isNotEmpty()) {
                try {
                    Crypto.encrypt(value.toByteArray())
                } catch (e: Exception) {
                    Timber.e(e, "Failed to encrypt refresh token")
                    ByteArray(0)
                }
            } else {
                ByteArray(0)
            }
            setPref(REFRESH_TOKEN_ENC, bytes)
        }

    override var clientId: Long?
        get() = runBlocking { dataStore.data.first()[CLIENT_ID] }
        set(value) {
            scope.launch {
                dataStore.edit { pref ->
                    if (value != null) {
                        pref[CLIENT_ID] = value
                    } else {
                        pref.remove(CLIENT_ID)
                    }
                }
            }
        }

    override var cellId: Int
        get() = getPref(CELL_ID, 0)
        set(value) {
            setPref(CELL_ID, value)
            if (value == 0) {
                setPref(CELL_ID_MANUALLY_SET, false)
            }
        }

    override var cellIdManuallySet: Boolean
        get() = getPref(CELL_ID_MANUALLY_SET, false)
        set(value) = setPref(CELL_ID_MANUALLY_SET, value)

    override var personaState: EPersonaState
        get() {
            val value = getPref(PERSONA_STATE, EPersonaState.Online.code())
            return EPersonaState.from(value)
        }
        set(value) = setPref(PERSONA_STATE, value.code())

    override var steamUserAccountId: Int
        get() = getPref(STEAM_USER_ACCOUNT_ID, 0)
        set(value) = setPref(STEAM_USER_ACCOUNT_ID, value)

    override var steamUserSteamId64: Long
        get() = getPref(STEAM_USER_STEAM_ID_64, 0L)
        set(value) = setPref(STEAM_USER_STEAM_ID_64, value)

    override var steamUserAvatarHash: String
        get() = getPref(STEAM_USER_AVATAR_HASH, "")
        set(value) = setPref(STEAM_USER_AVATAR_HASH, value)

    override var steamUserName: String
        get() = getPref(STEAM_USER_NAME, "")
        set(value) = setPref(STEAM_USER_NAME, value)

    override var lastPICSChangeNumber: Int
        get() = getPref(LAST_PICS_CHANGE_NUMBER, 0)
        set(value) = setPref(LAST_PICS_CHANGE_NUMBER, value)

    override var steamOfflineMode: Boolean
        get() = getPref(STEAM_OFFLINE_MODE, false)
        set(value) = setPref(STEAM_OFFLINE_MODE, value)

    override var epicOfflineMode: Boolean
        get() = getPref(EPIC_OFFLINE_MODE, false)
        set(value) = setPref(EPIC_OFFLINE_MODE, value)

    override var friendsListHeader: Set<String>
        get() {
            val value = getPref(FRIENDS_LIST_HEADER, "[]")
            return try {
                Json.decodeFromString<Set<String>>(value)
            } catch (e: Exception) {
                emptySet()
            }
        }
        set(value) = setPref(FRIENDS_LIST_HEADER, Json.encodeToString(value))

    override var ackChatPreview: Boolean
        get() = getPref(ACK_CHAT_PREVIEW, false)
        set(value) = setPref(ACK_CHAT_PREVIEW, value)

    override val personaStateFlow: Flow<EPersonaState> = dataStore.data
        .map { pref -> EPersonaState.from(pref[PERSONA_STATE] ?: EPersonaState.Online.code()) }
        .distinctUntilChanged()

    override val steamUserSteamId64Flow: Flow<Long> = dataStore.data
        .map { pref -> pref[STEAM_USER_STEAM_ID_64] ?: 0L }
        .distinctUntilChanged()

    override val usernameFlow: Flow<String> = dataStore.data
        .map { pref -> pref[USER_NAME] ?: "" }
        .distinctUntilChanged()

    override val steamOfflineModeFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[STEAM_OFFLINE_MODE] ?: false }
        .distinctUntilChanged()

    override val epicOfflineModeFlow: Flow<Boolean> = dataStore.data
        .map { pref -> pref[EPIC_OFFLINE_MODE] ?: false }
        .distinctUntilChanged()

    override suspend fun clearSteamSession() {
        dataStore.edit { pref ->
            pref.remove(USER_NAME)
            pref.remove(ACCESS_TOKEN_ENC)
            pref.remove(REFRESH_TOKEN_ENC)
            pref.remove(CLIENT_ID)
            pref.remove(PERSONA_STATE)
            pref.remove(STEAM_USER_ACCOUNT_ID)
            pref.remove(STEAM_USER_STEAM_ID_64)
            pref.remove(STEAM_USER_AVATAR_HASH)
            pref.remove(STEAM_USER_NAME)
            pref.remove(LAST_PICS_CHANGE_NUMBER)
            pref.remove(STEAM_GAMES_COUNT)
        }
    }

    override suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
