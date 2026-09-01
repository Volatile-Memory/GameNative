package app.gamenative.core.runtime

import dagger.BindsInstance
import dagger.hilt.DefineComponent
import dagger.hilt.components.SingletonComponent

/**
 * Custom Hilt subcomponent representing the lifecycle of an active game session.
 * Child of [SingletonComponent].
 */
@GameSessionScoped
@DefineComponent(parent = SingletonComponent::class)
interface GameSessionComponent {

    @DefineComponent.Builder
    interface Builder {
        fun setSessionInfo(@BindsInstance sessionInfo: ActiveGameSessionInfo): Builder
        fun build(): GameSessionComponent
    }
}
