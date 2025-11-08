package com.example.safebankid.ui.fallback

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest

@Composable
fun FallbackPasswordScreen(
    navController: NavController,
    viewModel: FallbackPasswordViewModel = viewModel()
) {
    var password by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val isError = uiState == PasswordUiState.ERROR

    LaunchedEffect(uiState) {
        if (uiState == PasswordUiState.SUCCESS) {
            navController.navigate("dashboard") {
                popUpTo("auth") { inclusive = true } // Limpia el stack
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
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = isError,
            modifier = Modifier.fillMaxWidth()
        )

        if (isError) {
            Text(
                "Contraseña incorrecta",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
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