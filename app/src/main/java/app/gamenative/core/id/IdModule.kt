package app.gamenative.core.id

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class IdModule {

    @Binds
    @Singleton
    abstract fun bindIdGenerator(
        impl: DefaultIdGenerator,
    ): IdGenerator
}
