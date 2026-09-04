package app.gamenative.utils

import app.gamenative.preferences.GeneralPreferences
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class HltbEdgeCasesTest {

    private lateinit var server: MockWebServer
    private lateinit var generalPreferences: GeneralPreferences
    private lateinit var hltbCache: HltbCache
    private lateinit var hltbService: HltbService

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        generalPreferences = mockk<GeneralPreferences>(relaxed = true)
        every { generalPreferences.hltbCache } returns "{}"
        every { generalPreferences.hltbCache = any() } just runs

        hltbCache = HltbCache(generalPreferences)
        hltbService = HltbService(hltbCache, Dispatchers.Unconfined)
        hltbService.setApiBaseUrlForTesting(server.url("/").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        hltbService.resetForTesting()
        hltbCache.reset()
        server.shutdown()
    }

    @Test
    fun getStats_returnsNullImmediatelyForBlankOrWhitespaceQuery() = runBlocking {
        assertNull(hltbService.getStats(""))
        assertNull(hltbService.getStats("   "))
        assertNull(hltbService.getStats("\t\n\r"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun getStats_handlesMalformedInitResponseGracefully() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("THIS IS NOT JSON"))

        assertNull(hltbService.getStats("Portal"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun getStats_handlesInitResponseMissingRequiredAuthFields() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                JSONObject().put("token", "only_token_no_key_or_val").toString(),
            ),
        )

        assertNull(hltbService.getStats("Portal"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun getStats_handlesEmptySearchResponse() = runBlocking {
        enqueueAuthResponse()
        server.enqueue(MockResponse().setResponseCode(200).setBody(JSONObject().put("data", JSONArray()).toString()))

        assertNull(hltbService.getStats("Unknown Game 12345"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun getStats_handlesExtremeGameTitleLength() = runBlocking {
        val longTitle = "A".repeat(1500)
        enqueueAuthResponse()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                JSONObject().put(
                    "data",
                    JSONArray().put(
                        JSONObject()
                            .put("game_name", longTitle)
                            .put("comp_main", 36000)
                            .put("comp_plus", 54000)
                            .put("comp_100", 72000)
                            .put("comp_all", 40000)
                            .put("game_id", 9999),
                    ),
                ).toString(),
            ),
        )

        val stats = hltbService.getStats(longTitle)
        assertNotNull(stats)
        assertEquals("10.0", stats?.mainHours)
        assertEquals(9999, stats?.gameId)
    }

    @Test
    fun getStats_handlesAuthRejectionAndSuccessfulRetry() = runBlocking {
        // Initial auth response
        enqueueAuthResponse()
        // First search returns 401 Unauthorized
        server.enqueue(MockResponse().setResponseCode(401).setBody("Unauthorized"))
        // Re-auth response on retry
        enqueueAuthResponse()
        // Second search succeeds
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                JSONObject().put(
                    "data",
                    JSONArray().put(
                        JSONObject()
                            .put("game_name", "Doom")
                            .put("comp_main", 18000)
                            .put("comp_plus", 25000)
                            .put("comp_100", 36000)
                            .put("comp_all", 20000)
                            .put("game_id", 666),
                    ),
                ).toString(),
            ),
        )

        val stats = hltbService.getStats("Doom")
        assertNotNull(stats)
        assertEquals("5.0", stats?.mainHours)
        assertEquals(666, stats?.gameId)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun hltbCache_survivesMalformedPreferencesJson() {
        every { generalPreferences.hltbCache } returns "{ CORRUPT_JSON_DATA... }"
        val testCache = HltbCache(generalPreferences)

        assertNull(testCache.get("Game"))

        val stats = HltbService.Stats("10.0", "15.0", "20.0", "12.0", 1)
        testCache.put("Game", stats)
        assertEquals(stats, testCache.get("Game"))
    }

    private fun enqueueAuthResponse() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                JSONObject()
                    .put("token", "token-test")
                    .put("session_key", "hp-key-test")
                    .put("session_val", "hp-val-test")
                    .toString(),
            ),
        )
    }
}
