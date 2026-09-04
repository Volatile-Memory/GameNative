package app.gamenative.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps track of which games the user has marked as favorite.
 *
 * Backed by [FavoritesRepository], which persists favorites to preferences and exposes
 * reactive state via StateFlow.
 */
@Singleton
class FavoritesManager @Inject constructor(
    private val repository: FavoritesRepository,
) : FavoritesRepository by repository

