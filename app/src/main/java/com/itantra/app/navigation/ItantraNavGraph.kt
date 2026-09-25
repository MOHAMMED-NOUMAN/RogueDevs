package com.itantra.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.app.feature.home.ui.HomeScreen
import com.itantra.app.feature.signup.ui.SignupScreen

/**
 * Single source of truth for app navigation. The tabs (Home, Team, Pair, Settings) live
 * inside [HomeScreen]'s bottom navigation, not here.
 */
sealed class Screen(val route: String) {
    data object Signup : Screen("signup")
    data object Home : Screen("home")
}

@Composable
fun ItantraNavGraph(
    startDestination: String,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
    ) {

        composable(Screen.Signup.route) {
            SignupScreen(
                onContinue = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Signup.route) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen()
        }
    }
}
