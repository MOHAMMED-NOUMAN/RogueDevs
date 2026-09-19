package com.itantra.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.itantra.app.feature.communication.ui.PairingScreen
import com.itantra.app.feature.settings.ui.SettingsScreen
import com.itantra.app.feature.home.ui.HomeScreen
import com.itantra.app.feature.nearby.ui.NearbyDevicesScreen
import com.itantra.app.feature.signup.ui.SignupScreen

/**
 * Single source of truth for app navigation.
 */
sealed class Screen(val route: String) {
    data object Signup : Screen("signup")
    data object Home : Screen("home")
    data object Pairing : Screen("pairing")
    data object Nearby : Screen("nearby")

    data object Settings : Screen(route="settings")
}

@Composable
fun ItantraNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Signup.route
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

        composable(Screen.Pairing.route) {
            PairingScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
        composable(Screen.Nearby.route) {
            NearbyDevicesScreen(
                onPairViaQr = {
                    // QR navigation will be connected later
                },
                onConnect = { deviceName ->
                    // Bluetooth connection will be connected later
                }
            )
        }
    }
}