package com.example.safebankid.ui.lock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.safebankid.services.AppLockerState // ¡Importar!
import com.example.safebankid.ui.auth.AuthScreen
import com.example.safebankid.ui.auth.AuthViewModel
import com.example.safebankid.ui.fallback.FallbackPasswordScreen
import com.example.safebankid.ui.fallback.FallbackPasswordViewModel
import com.example.safebankid.ui.theme.SafeBankIDTheme

class LockScreenActivity : ComponentActivity() {

    // Variable para guardar la app que estamos bloqueando
    private var blockedAppPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- 1. Leemos el paquete que nos mandó el servicio ---
        blockedAppPackage = intent.getStringExtra("BLOCKED_APP_PACKAGE")

        onBackPressedDispatcher.addCallback(this) {
            // No hacer nada
        }

        setContent {
            SafeBankIDTheme {

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    var showFallback by remember { mutableStateOf(false) }

                    // --- 2. Creamos una función de éxito reutilizable ---
                    val onUnlockSuccess = {
                        blockedAppPackage?.let {
                            AppLockerState.lastUnlockedApp = it
                            AppLockerState.lastUnlockedTime = System.currentTimeMillis()
                        }
                        finish()
                    }

                    if (showFallback) {
                        // --- PANTALLA 2: MODO FALLBACK ---
                        val passwordViewModel: FallbackPasswordViewModel = viewModel()

                        FallbackPasswordScreen(
                            navController = rememberNavController(),
                            viewModel = passwordViewModel,
                            // --- 3. Usamos la nueva función de éxito ---
                            onSuccess = { _ ->
                                onUnlockSuccess()
                            }
                        )
                    } else {
                        // --- PANTALLA 1: MODO AUTH FACIAL (Inicial) ---
                        val authViewModel: AuthViewModel = viewModel()

                        AuthScreen(
                            navController = rememberNavController(),
                            authViewModel = authViewModel,
                            // --- 3. Usamos la nueva función de éxito ---
                            onSuccess = { _ ->
                                onUnlockSuccess()
                            },
                            onNavigateToFallback = {
                                showFallback = true
                            }
                        )
                    }
                }
            }
        }
    }
}