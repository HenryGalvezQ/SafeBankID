package com.example.safebankid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.safebankid.ui.auth.AuthScreen
import com.example.safebankid.ui.auth.AuthViewModel
import com.example.safebankid.ui.dashboard.DashboardScreen
import com.example.safebankid.ui.dashboard.DashboardViewModel
import com.example.safebankid.ui.fallback.FallbackPasswordScreen // <-- Importa la nueva pantalla
import com.example.safebankid.ui.fallback.FallbackPasswordViewModel // <-- Importa el nuevo VM
import com.example.safebankid.ui.pin.PinScreen
import com.example.safebankid.ui.pin.PinViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // Inicia en "auth" (o "dashboard" para probar)
    NavHost(navController = navController, startDestination = "auth") {

        composable("auth") {
            val authViewModel: AuthViewModel = viewModel()
            AuthScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        // El PIN de 6 dígitos sigue existiendo para "Privacy Guard"
        composable("pin") {
            val pinViewModel: PinViewModel = viewModel()
            PinScreen(
                navController = navController,
                pinViewModel = pinViewModel
            )
        }

        // ¡NUEVA RUTA!
        composable("fallbackPassword") {
            val passwordViewModel: FallbackPasswordViewModel = viewModel()
            FallbackPasswordScreen(
                navController = navController,
                viewModel = passwordViewModel
            )
        }

        composable("dashboard") {
            val dashboardViewModel: DashboardViewModel = viewModel()
            DashboardScreen(viewModel = dashboardViewModel)
        }
    }
}