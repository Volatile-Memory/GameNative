package app.gamenative.di

import app.gamenative.data.DefaultFavoritesRepository
import app.gamenative.data.FavoritesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(
        impl: DefaultFavoritesRepository,
    ): FavoritesRepository
}
