package com.example.mpod.ui.navigation

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object Login : Screen("login")
    object Home : Screen("home")
    object Subscriptions : Screen("subscriptions")
    object Settings : Screen("settings")
    object AddPodcast : Screen("add_podcast")
}
