package app.gamenative.di

import android.content.Context
import app.gamenative.data.FavoritesManager
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.preferences.GeneralPreferences
import app.gamenative.service.SteamManager
import app.gamenative.service.amazon.AmazonManager
import app.gamenative.service.epic.EpicManager
import app.gamenative.service.gog.GOGManager
import app.gamenative.sync.FrontendSyncManager
import app.gamenative.utils.CustomGameScanner
import app.gamenative.utils.DeviceGameStatsCache
import app.gamenative.utils.GameCompatibilityCache
import app.gamenative.utils.GpuGameStatsCache
import app.gamenative.utils.HltbCache
import app.gamenative.utils.HltbService
import app.gamenative.utils.SteamGridDB
import app.gamenative.utils.BestConfigService
import app.gamenative.workshop.WorkshopManager
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
        val steamManager = mockk<SteamManager>(relaxed = true)
        val epicManager = mockk<EpicManager>(relaxed = true)
        val gogManager = mockk<GOGManager>(relaxed = true)
        val amazonManager = mockk<AmazonManager>(relaxed = true)
        val bestConfigService = mockk<BestConfigService>(relaxed = true)
        val workshopManager = mockk<WorkshopManager>(relaxed = true)

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
            override fun steamManager(): SteamManager = steamManager
            override fun epicManager(): EpicManager = epicManager
            override fun gogManager(): GOGManager = gogManager
            override fun amazonManager(): AmazonManager = amazonManager
            override fun bestConfigService(): BestConfigService = bestConfigService
            override fun workshopManager(): WorkshopManager = workshopManager
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
        assertNotNull(mockEntryPoint.steamManager())
        assertNotNull(mockEntryPoint.epicManager())
        assertNotNull(mockEntryPoint.gogManager())
        assertNotNull(mockEntryPoint.amazonManager())
        assertNotNull(mockEntryPoint.bestConfigService())
        assertNotNull(mockEntryPoint.workshopManager())
    }
}
