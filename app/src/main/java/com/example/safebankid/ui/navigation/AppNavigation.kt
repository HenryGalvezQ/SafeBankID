package com.example.safebankid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.safebankid.ui.auth.AuthScreen
import com.example.safebankid.ui.dashboard.DashboardScreen
import com.example.safebankid.ui.pin.PinScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "auth") {

        // Ruta de Autenticación (Inicio)
        composable("auth") {
            AuthScreen(navController = navController)
        }

        // Ruta de PIN
        composable("pin") {
            PinScreen(navController = navController)
        }

        // Ruta de Dashboard
        composable("dashboard") {
            DashboardScreen()
        }
    }
}