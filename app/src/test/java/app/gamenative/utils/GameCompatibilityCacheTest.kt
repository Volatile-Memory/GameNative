package app.gamenative.utils

import app.gamenative.preferences.GeneralPreferences
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GameCompatibilityCacheTest {

    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var cache: GameCompatibilityCache

    private val sampleResponse = GameCompatibilityService.GameCompatibilityResponse(
        gameName = "Cyberpunk 2077",
        totalPlayableCount = 100,
        gpuPlayableCount = 50,
        avgRating = 4.5f,
        hasBeenTried = true,
        isNotWorking = false,
    )

    @Before
    fun setUp() {
        generalPreferences = mockk<GeneralPreferences>(relaxed = true)
        every { generalPreferences.gameCompatibilityCache } returns "{}"
        every { generalPreferences.gameCompatibilityCache = any() } just runs
        cache = GameCompatibilityCache(generalPreferences)
    }

    @Test
    fun loadCache_handlesEmptyAndMalformedJsonGracefully() {
        every { generalPreferences.gameCompatibilityCache } returns ""
        val emptyCache = GameCompatibilityCache(generalPreferences)
        assertEquals(0, emptyCache.size())
        assertNull(emptyCache.getCached("Game"))

        every { generalPreferences.gameCompatibilityCache } returns "MALFORMED_JSON_STRING_12345"
        val malformedCache = GameCompatibilityCache(generalPreferences)
        assertEquals(0, malformedCache.size())
        assertNull(malformedCache.getCached("Game"))
    }

    @Test
    fun cacheAndGetCached_storesAndRetrievesItem() {
        cache.cache("Cyberpunk 2077", sampleResponse)

        assertTrue(cache.isCached("Cyberpunk 2077"))
        val retrieved = cache.getCached("Cyberpunk 2077")
        assertNotNull(retrieved)
        assertEquals("Cyberpunk 2077", retrieved?.gameName)
        assertEquals(100, retrieved?.totalPlayableCount)
        assertEquals(50, retrieved?.gpuPlayableCount)
        assertEquals(4.5f, retrieved?.avgRating ?: 0f, 0.01f)
        assertTrue(retrieved?.hasBeenTried == true)
        assertFalse(retrieved?.isNotWorking == true)
    }

    @Test
    fun cacheAll_storesMultipleEntries() {
        val batch = mapOf(
            "Game 1" to sampleResponse.copy(gameName = "Game 1", totalPlayableCount = 10),
            "Game 2" to sampleResponse.copy(gameName = "Game 2", totalPlayableCount = 20),
            "Game 3" to sampleResponse.copy(gameName = "Game 3", isNotWorking = true),
        )

        cache.cacheAll(batch)

        assertEquals(3, cache.size())
        assertEquals(10, cache.getCached("Game 1")?.totalPlayableCount)
        assertEquals(20, cache.getCached("Game 2")?.totalPlayableCount)
        assertTrue(cache.getCached("Game 3")?.isNotWorking == true)
    }

    @Test
    fun clear_emptiesCacheAndResetsPreferences() {
        cache.cache("Game", sampleResponse)
        assertEquals(1, cache.size())

        cache.clear()

        assertEquals(0, cache.size())
        assertNull(cache.getCached("Game"))
        verify { generalPreferences.gameCompatibilityCache = "{}" }
    }

    @Test
    fun getCached_handlesExtremeTitleLengthsAndSpecialCharacters() {
        val extremeName = "Z".repeat(4000) + " / \\ ? % * : | \" < > 🐉"
        val response = sampleResponse.copy(gameName = extremeName)

        cache.cache(extremeName, response)

        val retrieved = cache.getCached(extremeName)
        assertNotNull(retrieved)
        assertEquals(extremeName, retrieved?.gameName)
    }
}
