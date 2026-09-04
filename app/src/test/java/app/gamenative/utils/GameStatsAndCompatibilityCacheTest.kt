package app.gamenative.utils

import app.gamenative.data.GameSource
import app.gamenative.preferences.GeneralPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GameStatsAndCompatibilityCacheTest {

    private lateinit var generalPreferences: GeneralPreferences
    private var prefsStore = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        prefsStore.clear()
        generalPreferences = mockk<GeneralPreferences>(relaxed = true)

        every { generalPreferences.gameCompatibilityCache } answers {
            prefsStore["gameCompatibilityCache"] ?: "{}"
        }
        every { generalPreferences.gameCompatibilityCache = any() } answers {
            prefsStore["gameCompatibilityCache"] = firstArg()
        }

        every { generalPreferences.deviceGameStatsCache } answers {
            prefsStore["deviceGameStatsCache"] ?: "{}"
        }
        every { generalPreferences.deviceGameStatsCache = any() } answers {
            prefsStore["deviceGameStatsCache"] = firstArg()
        }

        every { generalPreferences.gpuGameStatsCache } answers {
            prefsStore["gpuGameStatsCache"] ?: "{}"
        }
        every { generalPreferences.gpuGameStatsCache = any() } answers {
            prefsStore["gpuGameStatsCache"] = firstArg()
        }

        every { generalPreferences.hltbCache } answers {
            prefsStore["hltbCache"] ?: "{}"
        }
        every { generalPreferences.hltbCache = any() } answers {
            prefsStore["hltbCache"] = firstArg()
        }
    }

    @Test
    fun gameCompatibilityCache_cacheAndRetrieveSingleGame() {
        val cache = GameCompatibilityCache(generalPreferences)
        val response = GameCompatibilityService.GameCompatibilityResponse(
            gameName = "Cyberpunk 2077",
            totalPlayableCount = 10,
            gpuPlayableCount = 5,
            avgRating = 4.5f,
            hasBeenTried = true,
            isNotWorking = false,
        )

        assertNull(cache.getCached("Cyberpunk 2077"))
        assertFalse(cache.isCached("Cyberpunk 2077"))

        cache.cache("Cyberpunk 2077", response)

        assertTrue(cache.isCached("Cyberpunk 2077"))
        val retrieved = cache.getCached("Cyberpunk 2077")
        assertNotNull(retrieved)
        assertEquals("Cyberpunk 2077", retrieved?.gameName)
        assertEquals(10, retrieved?.totalPlayableCount)
        assertEquals(5, retrieved?.gpuPlayableCount)
        assertEquals(4.5f, retrieved?.avgRating ?: 0f, 0.001f)
    }

    @Test
    fun gameCompatibilityCache_cacheAllAndClear() {
        val cache = GameCompatibilityCache(generalPreferences)
        val response1 = GameCompatibilityService.GameCompatibilityResponse(
            gameName = "Game A",
            totalPlayableCount = 1,
            gpuPlayableCount = 1,
            avgRating = 5.0f,
            hasBeenTried = true,
            isNotWorking = false,
        )
        val response2 = GameCompatibilityService.GameCompatibilityResponse(
            gameName = "Game B",
            totalPlayableCount = 2,
            gpuPlayableCount = 2,
            avgRating = 3.0f,
            hasBeenTried = true,
            isNotWorking = false,
        )

        cache.cacheAll(mapOf("Game A" to response1, "Game B" to response2))
        assertEquals(2, cache.size())
        assertTrue(cache.isCached("Game A"))
        assertTrue(cache.isCached("Game B"))

        cache.clear()
        assertEquals(0, cache.size())
        assertFalse(cache.isCached("Game A"))
        assertFalse(cache.isCached("Game B"))
        assertEquals("{}", prefsStore["gameCompatibilityCache"])
    }

    @Test
    fun gameCompatibilityCache_persistsAndLoadsAcrossInstances() {
        val cache1 = GameCompatibilityCache(generalPreferences)
        val response = GameCompatibilityService.GameCompatibilityResponse(
            gameName = "Hades",
            totalPlayableCount = 20,
            gpuPlayableCount = 15,
            avgRating = 4.9f,
            hasBeenTried = true,
            isNotWorking = false,
        )
        cache1.cache("Hades", response)

        // Instantiate a second cache instance pointing to same preferences
        val cache2 = GameCompatibilityCache(generalPreferences)
        val retrieved = cache2.getCached("Hades")
        assertNotNull(retrieved)
        assertEquals("Hades", retrieved?.gameName)
        assertEquals(20, retrieved?.totalPlayableCount)
    }

    @Test
    fun gameCompatibilityCache_handlesCorruptedJsonGracefully() {
        prefsStore["gameCompatibilityCache"] = "{ malformed json ::: "
        val cache = GameCompatibilityCache(generalPreferences)
        assertNull(cache.getCached("Any Game"))
        assertEquals(0, cache.size())
    }

    @Test
    fun gameCompatibilityCache_expiredEntryReturnsNull() {
        // Prepare pre-expired JSON (older than 6h = 21,600,000 ms)
        val expiredTimestamp = System.currentTimeMillis() - 7 * 3600 * 1000L
        val entry = GameCompatibilityCache.CachedCompatibilityResponse(
            response = GameCompatibilityCache.GameCompatibilityResponseData(
                gameName = "Old Game",
                totalPlayableCount = 1,
                gpuPlayableCount = 1,
                avgRating = 4.0f,
                hasBeenTried = true,
                isNotWorking = false,
            ),
            timestamp = expiredTimestamp,
        )
        prefsStore["gameCompatibilityCache"] = Json.encodeToString(mapOf("Old Game" to entry))

        val cache = GameCompatibilityCache(generalPreferences)
        assertNull(cache.getCached("Old Game"))
        assertFalse(cache.isCached("Old Game"))
    }

    @Test
    fun gameCompatibilityCache_concurrentAccessIsThreadSafe() {
        val cache = GameCompatibilityCache(generalPreferences)
        val threadCount = 20
        val operationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until operationsPerThread) {
                        val name = "Game_${t}_$i"
                        val resp = GameCompatibilityService.GameCompatibilityResponse(
                            gameName = name,
                            totalPlayableCount = i,
                            gpuPlayableCount = i,
                            avgRating = 4.0f,
                            hasBeenTried = true,
                            isNotWorking = false,
                        )
                        cache.cache(name, resp)
                        val retrieved = cache.getCached(name)
                        if (retrieved == null || retrieved.totalPlayableCount != i) {
                            errorCount.incrementAndGet()
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals(0, errorCount.get())
        assertEquals(threadCount * operationsPerThread, cache.size())
    }

    @Test
    fun hltbCache_concurrentAccessStress() {
        val cache = HltbCache(generalPreferences)
        val threadCount = 20
        val operationsPerThread = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        val stats = HltbService.Stats(
            mainHours = "10.0",
            mainPlusHours = "15.0",
            completeHours = "20.0",
            allStylesHours = "12.0",
            gameId = 1,
        )

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until operationsPerThread) {
                        val name = "Game_${t}_$i"
                        cache.put(name, stats)
                        val retrieved = cache.get(name)
                        if (retrieved == null) {
                            // May be evicted if over MAX_ENTRIES, but should not throw exception
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        executor.shutdown()
        assertEquals(0, errorCount.get())
    }

    @Test
    fun deviceAndGpuStatsCache_clearAndCorruptedJsonResilience() {
        val devCache = DeviceGameStatsCache(generalPreferences)
        val gpuCache = GpuGameStatsCache(generalPreferences)

        assertEquals(emptyMap<GameSource, Map<String, DeviceGameStatsService.DeviceGameStats>>(), devCache.getAll())
        assertEquals(emptyMap<GameSource, Map<String, DeviceGameStatsService.DeviceGameStats>>(), gpuCache.getAll())

        devCache.clear()
        gpuCache.clear()
        assertEquals("{}", prefsStore["deviceGameStatsCache"])
        assertEquals("{}", prefsStore["gpuGameStatsCache"])

        prefsStore["deviceGameStatsCache"] = "corrupted json!!"
        prefsStore["gpuGameStatsCache"] = "corrupted json!!"

        val devCache2 = DeviceGameStatsCache(generalPreferences)
        val gpuCache2 = GpuGameStatsCache(generalPreferences)
        assertNull(devCache2.getStats(GameSource.STEAM, "Half-Life"))
        assertNull(gpuCache2.getStats(GameSource.STEAM, "Half-Life"))
    }
}
