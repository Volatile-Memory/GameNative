package app.gamenative.utils

import app.gamenative.preferences.GeneralPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent cache for game compatibility responses with 7-day TTL.
 * Uses lazy expiration - checks expiration on access, not on load (optimizes performance).
 */
@Singleton
class GameCompatibilityCache @Inject constructor(
    private val generalPreferences: GeneralPreferences,
) {
    companion object {
        private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
    }

    private val inMemoryCache = mutableMapOf<String, GameCompatibilityService.GameCompatibilityResponse>()
    private val timestamps = mutableMapOf<String, Long>()
    private var cacheLoaded = false

    @Serializable
    data class CachedCompatibilityResponse(
        val response: GameCompatibilityResponseData,
        val timestamp: Long
    )

    @Serializable
    data class GameCompatibilityResponseData(
        val gameName: String,
        val totalPlayableCount: Int,
        val gpuPlayableCount: Int,
        val avgRating: Float,
        val hasBeenTried: Boolean,
        val isNotWorking: Boolean
    )

    /**
     * Converts GameCompatibilityService.GameCompatibilityResponse to serializable format
     */
    private fun GameCompatibilityService.GameCompatibilityResponse.toData(): GameCompatibilityResponseData {
        return GameCompatibilityResponseData(
            gameName = this.gameName,
            totalPlayableCount = this.totalPlayableCount,
            gpuPlayableCount = this.gpuPlayableCount,
            avgRating = this.avgRating,
            hasBeenTried = this.hasBeenTried,
            isNotWorking = this.isNotWorking
        )
    }

    /**
     * Converts serializable format back to GameCompatibilityService.GameCompatibilityResponse
     */
    private fun GameCompatibilityResponseData.toResponse(): GameCompatibilityService.GameCompatibilityResponse {
        return GameCompatibilityService.GameCompatibilityResponse(
            gameName = this.gameName,
            totalPlayableCount = this.totalPlayableCount,
            gpuPlayableCount = this.gpuPlayableCount,
            avgRating = this.avgRating,
            hasBeenTried = this.hasBeenTried,
            isNotWorking = this.isNotWorking
        )
    }

    /**
     * Loads cache from persistent storage into memory.
     * Only parses JSON, no expiration filtering (lazy expiration).
     */
    @Synchronized
    private fun loadCache() {
        if (cacheLoaded) return

        try {
            val cacheJson = generalPreferences.gameCompatibilityCache
            if (cacheJson.isEmpty() || cacheJson == "{}") {
                cacheLoaded = true
                return
            }

            val cacheMap = Json.decodeFromString<Map<String, CachedCompatibilityResponse>>(cacheJson)

            // Load all entries into memory (no expiration check here - lazy expiration)
            // Store both response and timestamp for expiration checking
            cacheMap.forEach { (gameName, cached) ->
                inMemoryCache[gameName] = cached.response.toResponse()
                timestamps[gameName] = cached.timestamp
            }

            Timber.tag("GameCompatibilityCache").d("Loaded ${inMemoryCache.size} cached entries from persistent storage")
            cacheLoaded = true
        } catch (e: Exception) {
            Timber.tag("GameCompatibilityCache").e(e, "Failed to load cache from persistent storage")
            cacheLoaded = true // Mark as loaded to avoid retrying
        }
    }

    /**
     * Saves cache to persistent storage.
     */
    @Synchronized
    private fun saveCache() {
        try {
            val now = System.currentTimeMillis()
            val cacheMap = inMemoryCache.mapValues { (gameName, response) ->
                val timestamp = timestamps[gameName] ?: now
                CachedCompatibilityResponse(response.toData(), timestamp)
            }
            val cacheJson = Json.encodeToString(cacheMap)
            generalPreferences.gameCompatibilityCache = cacheJson
            Timber.tag("GameCompatibilityCache").d("Saved ${cacheMap.size} entries to persistent storage")
        } catch (e: Exception) {
            Timber.tag("GameCompatibilityCache").e(e, "Failed to save cache to persistent storage")
        }
    }

    /**
     * Gets cached compatibility response for a game, if available and not expired.
     * Uses lazy expiration - checks expiration on access.
     */
    @Synchronized
    fun getCached(gameName: String): GameCompatibilityService.GameCompatibilityResponse? {
        loadCache()

        val cached = inMemoryCache[gameName] ?: return null
        val timestamp = timestamps[gameName] ?: return null

        // Lazy expiration check - only check when accessing
        val now = System.currentTimeMillis()
        if (now - timestamp >= CACHE_TTL_MS) {
            // Expired - remove from cache
            inMemoryCache.remove(gameName)
            timestamps.remove(gameName)
            Timber.tag("GameCompatibilityCache").d("Removed expired cache entry for: $gameName")
            return null
        }

        return cached
    }

    /**
     * Caches a compatibility response for a game.
     */
    @Synchronized
    fun cache(gameName: String, response: GameCompatibilityService.GameCompatibilityResponse) {
        loadCache()
        val now = System.currentTimeMillis()
        inMemoryCache[gameName] = response
        timestamps[gameName] = now
        saveCache()
        Timber.tag("GameCompatibilityCache").d("Cached compatibility for: $gameName")
    }

    /**
     * Caches multiple compatibility responses at once.
     */
    @Synchronized
    fun cacheAll(responses: Map<String, GameCompatibilityService.GameCompatibilityResponse>) {
        loadCache()
        val now = System.currentTimeMillis()
        inMemoryCache.putAll(responses)
        responses.keys.forEach { gameName ->
            timestamps[gameName] = now
        }
        saveCache()
        Timber.tag("GameCompatibilityCache").d("Cached ${responses.size} compatibility entries")
    }

    /**
     * Checks if a game's compatibility is cached and not expired.
     */
    @Synchronized
    fun isCached(gameName: String): Boolean {
        loadCache()
        return getCached(gameName) != null
    }

    /**
     * Clears the entire cache (both memory and persistent storage).
     */
    @Synchronized
    fun clear() {
        inMemoryCache.clear()
        timestamps.clear()
        generalPreferences.gameCompatibilityCache = "{}"
        Timber.tag("GameCompatibilityCache").d("Cache cleared")
    }

    /**
     * Gets the current cache size.
     */
    @Synchronized
    fun size(): Int {
        loadCache()
        return inMemoryCache.size
    }
}
