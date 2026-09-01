package app.gamenative.core.appinfo

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBuildInfoModule {

    @Binds
    @Singleton
    abstract fun bindAppBuildInfo(
        impl: DefaultAppBuildInfo,
    ): AppBuildInfo
}
