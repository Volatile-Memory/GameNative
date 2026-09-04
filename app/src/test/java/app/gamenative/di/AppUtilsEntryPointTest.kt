package app.gamenative.di

import android.content.Context
import app.gamenative.data.FavoritesManager
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.preferences.GeneralPreferences
import app.gamenative.sync.FrontendSyncManager
import app.gamenative.utils.CustomGameScanner
import app.gamenative.utils.DeviceGameStatsCache
import app.gamenative.utils.GameCompatibilityCache
import app.gamenative.utils.GpuGameStatsCache
import app.gamenative.utils.HltbCache
import app.gamenative.utils.HltbService
import app.gamenative.utils.SteamGridDB
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test

class AppUtilsEntryPointTest {

    @Test
    fun entryPoint_canBeInstantiatedAndPolymorphicallyInvoked() {
        val generalPreferences = mockk<GeneralPreferences>(relaxed = true)
        val downloadPreferences = mockk<DownloadPreferences>(relaxed = true)

        val hltbCache = HltbCache(generalPreferences)
        val hltbService = HltbService(hltbCache)
        val steamGridDB = SteamGridDB(downloadPreferences)
        val deviceGameStatsCache = DeviceGameStatsCache(generalPreferences)
        val gpuGameStatsCache = GpuGameStatsCache(generalPreferences)
        val gameCompatibilityCache = GameCompatibilityCache(generalPreferences)
        val favoritesManager = mockk<FavoritesManager>(relaxed = true)
        val frontendSyncManager = mockk<FrontendSyncManager>(relaxed = true)
        val customGameScanner = mockk<CustomGameScanner>(relaxed = true)

        val mockEntryPoint = object : AppUtilsEntryPoint {
            override fun hltbService(): HltbService = hltbService
            override fun hltbCache(): HltbCache = hltbCache
            override fun steamGridDB(): SteamGridDB = steamGridDB
            override fun deviceGameStatsCache(): DeviceGameStatsCache = deviceGameStatsCache
            override fun gpuGameStatsCache(): GpuGameStatsCache = gpuGameStatsCache
            override fun gameCompatibilityCache(): GameCompatibilityCache = gameCompatibilityCache
            override fun favoritesManager(): FavoritesManager = favoritesManager
            override fun frontendSyncManager(): FrontendSyncManager = frontendSyncManager
            override fun customGameScanner(): CustomGameScanner = customGameScanner
        }

        assertNotNull(mockEntryPoint.hltbService())
        assertNotNull(mockEntryPoint.hltbCache())
        assertNotNull(mockEntryPoint.steamGridDB())
        assertNotNull(mockEntryPoint.deviceGameStatsCache())
        assertNotNull(mockEntryPoint.gpuGameStatsCache())
        assertNotNull(mockEntryPoint.gameCompatibilityCache())
        assertNotNull(mockEntryPoint.favoritesManager())
        assertNotNull(mockEntryPoint.frontendSyncManager())
        assertNotNull(mockEntryPoint.customGameScanner())
    }
}
