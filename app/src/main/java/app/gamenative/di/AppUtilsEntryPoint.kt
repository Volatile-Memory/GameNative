package app.gamenative.di

import android.content.Context
import app.gamenative.data.FavoritesManager
import app.gamenative.sync.FrontendSyncManager
import app.gamenative.utils.BestConfigService
import app.gamenative.utils.CustomGameScanner
import app.gamenative.utils.DeviceGameStatsCache
import app.gamenative.utils.GameCompatibilityCache
import app.gamenative.utils.GpuGameStatsCache
import app.gamenative.utils.HltbCache
import app.gamenative.utils.HltbService
import app.gamenative.utils.SteamGridDB
import app.gamenative.workshop.WorkshopManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint to provide access to singleton utilities and caches
 * for UI Composable trees and non-Hilt classes.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppUtilsEntryPoint {
    fun hltbService(): HltbService
    fun hltbCache(): HltbCache
    fun steamGridDB(): SteamGridDB
    fun deviceGameStatsCache(): DeviceGameStatsCache
    fun gpuGameStatsCache(): GpuGameStatsCache
    fun gameCompatibilityCache(): GameCompatibilityCache
    fun favoritesManager(): FavoritesManager
    fun frontendSyncManager(): FrontendSyncManager
    fun customGameScanner(): CustomGameScanner
    fun steamManager(): app.gamenative.service.SteamManager
    fun epicManager(): app.gamenative.service.epic.EpicManager
    fun gogManager(): app.gamenative.service.gog.GOGManager
    fun amazonManager(): app.gamenative.service.amazon.AmazonManager
    fun bestConfigService(): BestConfigService
    fun workshopManager(): WorkshopManager

    companion object {
        /**
         * Resolves the [AppUtilsEntryPoint] from the given [Context].
         */
        @JvmStatic
        fun get(context: Context): AppUtilsEntryPoint {
            val appContext = context.applicationContext ?: context
            return EntryPointAccessors.fromApplication(
                appContext,
                AppUtilsEntryPoint::class.java,
            )
        }
    }
}

/**
 * Extension function for ergonomic access to [AppUtilsEntryPoint] from any [Context].
 */
fun Context.appUtilsEntryPoint(): AppUtilsEntryPoint =
    AppUtilsEntryPoint.get(this)
