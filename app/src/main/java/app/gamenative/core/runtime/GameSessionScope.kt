package app.gamenative.core.runtime

import javax.inject.Scope

/**
 * Scope for bindings that live as long as an active Game Playing Session.
 * Created when a container/game is launched and destroyed when it terminates.
 */
@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class GameSessionScoped
