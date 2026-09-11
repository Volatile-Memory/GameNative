package app.gamenative.di

import app.gamenative.PluviaApp
import app.gamenative.events.EventDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EventsModule {

    @Provides
    @Singleton
    fun provideEventDispatcher(): EventDispatcher = PluviaApp.events
}
