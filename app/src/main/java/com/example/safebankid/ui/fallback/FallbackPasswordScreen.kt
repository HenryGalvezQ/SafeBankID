package com.example.safebankid.ui.fallback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest

@Composable
fun FallbackPasswordScreen(
    navController: NavController, // Lo quitaremos, pero lo dejamos por ahora
    viewModel: FallbackPasswordViewModel = viewModel(),
    // --- 1. NUEVO CALLBACK ---
    onSuccess: (PasswordUiState) -> Unit
) {
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val isError = uiState == PasswordUiState.ERROR
    var passwordVisible by remember { mutableStateOf(false) }

    // --- 2. LÓGICA DE NAVEGACIÓN MODIFICADA ---
    LaunchedEffect(uiState) {
        when (uiState) {
            // Si el estado es de éxito (CUALQUIERA de los dos)...
            PasswordUiState.SUCCESS_TO_DASHBOARD,
            PasswordUiState.SUCCESS_TO_PIN -> {
                // ...en lugar de navegar, ¡llamamos al callback!
                onSuccess(uiState)
            }
            else -> {
                // No hacer nada en otros estados (IDLE, LOADING, ERROR)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Contraseña de Respaldo",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "Esta contraseña solo se usa si la verificación facial falla.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),

            // --- 2. Lógica de visibilidad ---
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),

            // --- 3. Icono del "ojo" ---
            trailingIcon = {
                val image = if (passwordVisible)
                    Icons.Filled.VisibilityOff
                else
                    Icons.Filled.Visibility

                val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"

                Icon(
                    imageVector = image,
                    contentDescription = description,
                    // --- 4. Lógica de "Mantener Presionado" ---
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                // Inicia al presionar
                                passwordVisible = true
                                try {
                                    // Espera a que se suelte el dedo
                                    awaitRelease()
                                } finally {
                                    // Vuelve a ocultar al soltar
                                    passwordVisible = false
                                }
                            }
                        )
                    }
                )
            }
        )

        // --- 5. Texto de error y "¿Olvidaste contraseña?" ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            if (isError) {
                Text(
                    text = "Contraseña incorrecta",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // --- 6. Texto de "Olvidaste contraseña" ---
            Text(
                text = "¿Olvidaste la contraseña?",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = { /* No hace nada, como pediste */ })
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.validatePassword(password) },
            enabled = uiState != PasswordUiState.LOADING,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (uiState == PasswordUiState.LOADING) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Ingresar")
            }
        }
    }
}