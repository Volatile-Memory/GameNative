package app.gamenative.utils

import app.gamenative.preferences.GeneralPreferences
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HltbCacheTest {

    private val sampleStats = HltbService.Stats(
        mainHours = "10.0",
        mainPlusHours = "15.0",
        completeHours = "25.0",
        allStylesHours = "12.0",
        gameId = 42,
    )

    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var hltbCache: HltbCache

    @Before
    fun setUp() {
        generalPreferences = mockk<GeneralPreferences>(relaxed = true)
        every { generalPreferences.hltbCache } returns "{}"
        every { generalPreferences.hltbCache = any() } just runs
        hltbCache = HltbCache(generalPreferences)
    }

    @After
    fun tearDown() {
        hltbCache.reset()
    }

    @Test
    fun get_returnsStoredStatsForNormalizedKeys() {
        hltbCache.put("Hollow Knight!", sampleStats)

        listOf("Hollow Knight!", "hollow knight", "HOLLOW KNIGHT", "Hollow Knight")
            .forEach { key ->
                assertNotNull(hltbCache.get(key))
                assertEquals(sampleStats, hltbCache.get(key))
            }
    }

    @Test
    fun get_returnsNullForMissingEntry() {
        assertNull(hltbCache.get("Unknown Game"))
    }

    @Test
    fun put_evictsOldestWhenCapReached() {
        repeat(HltbCache.MAX_ENTRIES) { index ->
            hltbCache.put("Game $index", sampleStats)
        }
        assertNotNull(hltbCache.get("Game 0"))

        hltbCache.put("Overflow Game", sampleStats)

        assertNull(hltbCache.get("Game 0"))
        assertNotNull(hltbCache.get("Overflow Game"))
    }

    @Test
    fun reset_clearsAllEntries() {
        hltbCache.put("Halo", sampleStats)
        hltbCache.reset()
        assertNull(hltbCache.get("Halo"))
    }
}
