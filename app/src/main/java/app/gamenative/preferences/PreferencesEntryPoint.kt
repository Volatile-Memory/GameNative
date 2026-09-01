package app.gamenative.preferences

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint to provide access to all 7 preference domain repositories
 * for non-Hilt classes (Java components, static utility objects, and early lifecycle hooks).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesEntryPoint {
    fun authPreferences(): AuthPreferences
    fun containerPreferences(): ContainerPreferences
    fun inputPreferences(): InputPreferences
    fun hudPreferences(): HudPreferences
    fun libraryPreferences(): LibraryPreferences
    fun downloadPreferences(): DownloadPreferences
    fun generalPreferences(): GeneralPreferences

    companion object {
        /**
         * Resolves the [PreferencesEntryPoint] from the given [Context].
         * Safe for both Kotlin and Java callers.
         */
        @JvmStatic
        fun get(context: Context): PreferencesEntryPoint {
            val appContext = context.applicationContext ?: context
            return EntryPointAccessors.fromApplication(
                appContext,
                PreferencesEntryPoint::class.java,
            )
        }
    }
}

/**
 * Kotlin extension function for ergonomic access to [PreferencesEntryPoint] from any [Context].
 */
fun Context.preferencesEntryPoint(): PreferencesEntryPoint =
    PreferencesEntryPoint.get(this)
