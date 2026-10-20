package com.example.mpod.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Subscriptions : Screen("subscriptions")
    object Settings : Screen("settings")
    object AddPodcast : Screen("add_podcast")
    object AddProxy : Screen("add_proxy")
    object EditProxy : Screen("edit_proxy/{serverId}") {
        fun createRoute(serverId: String): String = "edit_proxy/$serverId"
    }
}
