package app.gamenative.ui.data

import app.gamenative.ui.enums.HomeDestination

data class HomeState(
    val currentDestination: HomeDestination = HomeDestination.Library,
    val confirmExit: Boolean = false,
)
