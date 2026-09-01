package app.gamenative.preferences

import `in`.dragonbra.javasteam.enums.EPersonaState
import kotlinx.coroutines.flow.Flow

interface AuthPreferences {
    var username: String
    var accessToken: String
    var refreshToken: String
    var clientId: Long?
    var cellId: Int
    var cellIdManuallySet: Boolean
    var personaState: EPersonaState
    var steamUserAccountId: Int
    var steamUserSteamId64: Long
    var steamUserAvatarHash: String
    var steamUserName: String
    var lastPICSChangeNumber: Int
    var steamOfflineMode: Boolean
    var epicOfflineMode: Boolean
    var friendsListHeader: Set<String>
    var ackChatPreview: Boolean

    val personaStateFlow: Flow<EPersonaState>
    val steamUserSteamId64Flow: Flow<Long>
    val usernameFlow: Flow<String>
    val steamOfflineModeFlow: Flow<Boolean>
    val epicOfflineModeFlow: Flow<Boolean>

    suspend fun clearSteamSession()
    suspend fun clearAll()
}
