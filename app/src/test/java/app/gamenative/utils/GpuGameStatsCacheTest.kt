package app.gamenative.utils

import app.gamenative.data.GameSource
import app.gamenative.preferences.GeneralPreferences
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

class GpuGameStatsCacheTest {

    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var cache: GpuGameStatsCache

    @Before
    fun setUp() {
        generalPreferences = mockk<GeneralPreferences>(relaxed = true)
        every { generalPreferences.gpuGameStatsCache } returns "{}"
        every { generalPreferences.gpuGameStatsCache = any() } just runs
        cache = GpuGameStatsCache(generalPreferences)
    }

    @Test
    fun loadCache_handlesEmptyAndMalformedJsonGracefully() {
        every { generalPreferences.gpuGameStatsCache } returns ""
        val emptyCache = GpuGameStatsCache(generalPreferences)
        assertTrue(emptyCache.getAll().isEmpty())

        every { generalPreferences.gpuGameStatsCache } returns "INVALID_JSON_CONTENT"
        val malformedCache = GpuGameStatsCache(generalPreferences)
        assertTrue(malformedCache.getAll().isEmpty())
    }

    @Test
    fun loadCache_parsesValidData() {
        val validJson = """
            {
                "stats": {
                    "GOG": {
                        "Witcher 3": {
                            "successfulRuns": 42,
                            "medianFps": 60,
                            "fiveStarReviews": 40,
                            "medianSessionSec": 5000
                        }
                    }
                },
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        every { generalPreferences.gpuGameStatsCache } returns validJson
        val activeCache = GpuGameStatsCache(generalPreferences)

        val stats = activeCache.getStats(GameSource.GOG, "Witcher 3")
        assertNotNull(stats)
        assertEquals(42, stats?.successfulRuns)
        assertEquals(60, stats?.medianFps)
        assertEquals(40, stats?.fiveStarReviews)
    }

    @Test
    fun clear_clearsMemoryAndUpdatesPreferences() {
        val validJson = """
            {
                "stats": {
                    "EPIC": {
                        "Control": {
                            "successfulRuns": 15,
                            "medianFps": 50,
                            "fiveStarReviews": 10,
                            "medianSessionSec": 3000
                        }
                    }
                },
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        every { generalPreferences.gpuGameStatsCache } returns validJson
        val activeCache = GpuGameStatsCache(generalPreferences)

        assertNotNull(activeCache.getStats(GameSource.EPIC, "Control"))

        activeCache.clear()

        assertNull(activeCache.getStats(GameSource.EPIC, "Control"))
        assertTrue(activeCache.getAll().isEmpty())
        verify { generalPreferences.gpuGameStatsCache = "{}" }
    }
}
