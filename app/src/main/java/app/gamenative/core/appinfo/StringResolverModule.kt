package app.gamenative.core.appinfo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StringResolverModule {

    @Binds
    @Singleton
    abstract fun bindStringResolver(
        impl: AndroidStringResolver,
    ): StringResolver
}
