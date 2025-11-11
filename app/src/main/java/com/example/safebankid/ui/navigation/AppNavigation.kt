package com.example.safebankid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.safebankid.data.local.SecurityPreferences
import com.example.safebankid.data.repository.SecurityRepository
import com.example.safebankid.ui.auth.AuthScreen
import com.example.safebankid.ui.auth.AuthViewModel
import com.example.safebankid.ui.dashboard.DashboardScreen
import com.example.safebankid.ui.dashboard.DashboardViewModel
import com.example.safebankid.ui.fallback.FallbackPasswordScreen
import com.example.safebankid.ui.fallback.FallbackPasswordViewModel
import com.example.safebankid.ui.pin.PinScreen
import com.example.safebankid.ui.pin.PinViewModel

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // --- lógica para decidir pantalla inicial ---
    val context = LocalContext.current
    val securityPreferences = SecurityPreferences(context)
    val securityRepository = SecurityRepository(securityPreferences)
    val isAuthDetectorEnabled = securityRepository.getInitialSecurityUiState().authDetectorEnabled

    val startDestination = if (isAuthDetectorEnabled) {
        "auth"
    } else {
        "pin"
    }

    NavHost(navController = navController, startDestination = startDestination) {

        // login facial normal
        composable("auth") {
            val authViewModel: AuthViewModel = viewModel()
            AuthScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        // login facial en modo enrolamiento (cuando vienes de "Re-configurar Rostro")
        composable(
            route = "auth?mode={mode}",
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            val authViewModel: AuthViewModel = viewModel()
            AuthScreen(
                navController = navController,
                authViewModel = authViewModel
            )
        }

        // PIN
        composable("pin") {
            val pinViewModel: PinViewModel = viewModel()
            PinScreen(
                navController = navController,
                pinViewModel = pinViewModel
            )
        }

        // contraseña de respaldo
        composable("fallbackPassword") {
            val passwordViewModel: FallbackPasswordViewModel = viewModel()
            FallbackPasswordScreen(
                navController = navController,
                viewModel = passwordViewModel
            )
        }

        // dashboard (👈 aquí estaba el error)
        composable("dashboard") {
            val dashboardViewModel: DashboardViewModel = viewModel()
            DashboardScreen(
                navController = navController,
                viewModel = dashboardViewModel
            )
        }
    }
}
