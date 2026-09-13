package com.itantra.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.app.feature.communication.ui.CommunicationScreen

/**
 * Single source of truth for app navigation. Add one line here per
 * screen — don't scatter navigation logic across composables.
 */
sealed class Screen(val route: String) {
    data object Communication : Screen("communication")
    // data object Emergency : Screen("emergency")       // add as that feature lands
    // data object TeamTracking : Screen("team_tracking")
}

@Composable
fun ItantraNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Communication.route) {
        composable(Screen.Communication.route) {
            CommunicationScreen()
        }
    }
}
