package app.gamenative.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import app.gamenative.preferences.AuthPreferences
import app.gamenative.preferences.ContainerPreferences
import app.gamenative.preferences.DefaultAuthPreferences
import app.gamenative.preferences.DefaultContainerPreferences
import app.gamenative.preferences.DefaultDownloadPreferences
import app.gamenative.preferences.DefaultGeneralPreferences
import app.gamenative.preferences.DefaultHudPreferences
import app.gamenative.preferences.DefaultInputPreferences
import app.gamenative.preferences.DefaultLibraryPreferences
import app.gamenative.preferences.DownloadPreferences
import app.gamenative.preferences.GeneralPreferences
import app.gamenative.preferences.HudPreferences
import app.gamenative.preferences.InputPreferences
import app.gamenative.preferences.LibraryPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifier for the central PluviaPreferences DataStore.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PluviaDataStore

private val Context.pluviaDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "PluviaPreferences",
    corruptionHandler = ReplaceFileCorruptionHandler {
        Timber.e("PluviaPreferences DataStore corrupted, resetting to empty.")
        emptyPreferences()
    },
)

@Module
@InstallIn(SingletonComponent::class)
object PreferencesDataStoreModule {

    @Provides
    @Singleton
    @PluviaDataStore
    fun providePluviaDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.pluviaDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferencesBindingModule {

    @Binds
    @Singleton
    abstract fun bindAuthPreferences(
        impl: DefaultAuthPreferences,
    ): AuthPreferences

    @Binds
    @Singleton
    abstract fun bindContainerPreferences(
        impl: DefaultContainerPreferences,
    ): ContainerPreferences

    @Binds
    @Singleton
    abstract fun bindInputPreferences(
        impl: DefaultInputPreferences,
    ): InputPreferences

    @Binds
    @Singleton
    abstract fun bindHudPreferences(
        impl: DefaultHudPreferences,
    ): HudPreferences

    @Binds
    @Singleton
    abstract fun bindLibraryPreferences(
        impl: DefaultLibraryPreferences,
    ): LibraryPreferences

    @Binds
    @Singleton
    abstract fun bindDownloadPreferences(
        impl: DefaultDownloadPreferences,
    ): DownloadPreferences

    @Binds
    @Singleton
    abstract fun bindGeneralPreferences(
        impl: DefaultGeneralPreferences,
    ): GeneralPreferences
}
