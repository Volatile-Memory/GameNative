package app.gamenative.utils

import app.gamenative.data.GameSource
import app.gamenative.preferences.GeneralPreferences
import app.gamenative.utils.DeviceGameStatsService.DeviceGameStats
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeviceGameStatsCacheTest {

    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var cache: DeviceGameStatsCache

    @Before
    fun setUp() {
        generalPreferences = mockk<GeneralPreferences>(relaxed = true)
        every { generalPreferences.deviceGameStatsCache } returns "{}"
        every { generalPreferences.deviceGameStatsCache = any() } just runs
        cache = DeviceGameStatsCache(generalPreferences)
    }

    @Test
    fun loadCache_handlesEmptyAndDefaultJson() {
        every { generalPreferences.deviceGameStatsCache } returns ""
        val emptyCache = DeviceGameStatsCache(generalPreferences)
        assertTrue(emptyCache.getAll().isEmpty())
        assertNull(emptyCache.getStats(GameSource.STEAM, "Portal"))

        every { generalPreferences.deviceGameStatsCache } returns "{}"
        val defaultCache = DeviceGameStatsCache(generalPreferences)
        assertTrue(defaultCache.getAll().isEmpty())
    }

    @Test
    fun loadCache_handlesMalformedJsonGracefullyWithoutThrowing() {
        every { generalPreferences.deviceGameStatsCache } returns "{ this is not valid json !!! }"
        val resilientCache = DeviceGameStatsCache(generalPreferences)

        // Should not throw, should return empty
        assertTrue(resilientCache.getAll().isEmpty())
        assertNull(resilientCache.getStats(GameSource.STEAM, "Portal"))
    }

    @Test
    fun loadCache_ignoresUnknownPlatformEnumGracefully() {
        val jsonWithUnknownPlatform = """
            {
                "stats": {
                    "UNKNOWN_FUTURE_PLATFORM": {
                        "Portal": {
                            "successfulRuns": 10,
                            "medianFps": 60,
                            "fiveStarReviews": 5,
                            "medianSessionSec": 3600
                        }
                    },
                    "STEAM": {
                        "Half-Life": {
                            "successfulRuns": 100,
                            "medianFps": 120,
                            "fiveStarReviews": 50,
                            "medianSessionSec": 7200
                        }
                    }
                },
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        every { generalPreferences.deviceGameStatsCache } returns jsonWithUnknownPlatform
        val cacheWithUnknown = DeviceGameStatsCache(generalPreferences)

        val stats = cacheWithUnknown.getStats(GameSource.STEAM, "Half-Life")
        assertNotNull(stats)
        assertEquals(100, stats?.successfulRuns)
        assertEquals(120, stats?.medianFps)
    }

    @Test
    fun clear_resetsMemoryAndPreferences() {
        val validJson = """
            {
                "stats": {
                    "STEAM": {
                        "Cyberpunk": {
                            "successfulRuns": 5,
                            "medianFps": 45,
                            "fiveStarReviews": 2,
                            "medianSessionSec": 1800
                        }
                    }
                },
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        every { generalPreferences.deviceGameStatsCache } returns validJson
        val activeCache = DeviceGameStatsCache(generalPreferences)

        assertNotNull(activeCache.getStats(GameSource.STEAM, "Cyberpunk"))

        activeCache.clear()

        assertNull(activeCache.getStats(GameSource.STEAM, "Cyberpunk"))
        assertTrue(activeCache.getAll().isEmpty())
        verify { generalPreferences.deviceGameStatsCache = "{}" }
    }

    @Test
    fun getStats_handlesExtremeLengthAndSpecialCharacters() {
        val longTitle = "A".repeat(5000) + " 日本語 🎮 $!#%^&*()_+"
        val json = """
            {
                "stats": {
                    "STEAM": {
                        "$longTitle": {
                            "successfulRuns": 1,
                            "medianFps": 30,
                            "fiveStarReviews": 0,
                            "medianSessionSec": 100
                        }
                    }
                },
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        every { generalPreferences.deviceGameStatsCache } returns json
        val longCache = DeviceGameStatsCache(generalPreferences)

        val stats = longCache.getStats(GameSource.STEAM, longTitle)
        assertNotNull(stats)
        assertEquals(1, stats?.successfulRuns)
    }
}
