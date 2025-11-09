package com.example.safebankid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    // --- LÓGICA DE INICIO (CORREGIDA) ---
    val context = LocalContext.current

    val securityPreferences = SecurityPreferences(context)
    val securityRepository = SecurityRepository(securityPreferences)

    val isAuthDetectorEnabled = securityRepository.getInitialSecurityUiState().authDetectorEnabled

    // Decidimos la ruta de inicio dinámicamente
    val startDestination = if (isAuthDetectorEnabled) {
        "auth" // Si está ON, vamos a la verificación facial
    } else {
        "pin"  // <-- ¡ESTA ES LA CORRECCIÓN! Si está OFF, vamos al PIN.
    }
    // ------------------------------------------

    NavHost(navController = navController, startDestination = startDestination) {

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

        // Esta ruta es solo para el *respaldo* desde la pantalla "auth"
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