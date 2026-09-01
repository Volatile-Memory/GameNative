package app.gamenative.core.runtime

import app.gamenative.core.coroutines.DefaultDispatcher
import app.gamenative.core.coroutines.GameSessionCoroutineScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(GameSessionComponent::class)
object GameSessionModule {

    @Provides
    @GameSessionScoped
    @GameSessionCoroutineScope
    fun provideGameSessionCoroutineScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher + CoroutineName("GameSessionScope"))
}
