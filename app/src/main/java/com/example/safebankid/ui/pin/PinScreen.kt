package com.example.safebankid.ui.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PinScreen(
    navController: NavController,
    pinViewModel: PinViewModel = viewModel()
) {
    var pin by remember { mutableStateOf("") }
    val uiState by pinViewModel.uiState.collectAsState()
    val isError = uiState == PinUiState.ERROR

    // 1. Escucha el resultado del ViewModel
    LaunchedEffect(uiState) {
        if (uiState == PinUiState.SUCCESS) {
            navController.navigate("dashboard") {
                popUpTo("auth") { inclusive = true } // Limpia el stack
            }
        }
        if (uiState == PinUiState.ERROR) {
            pin = "" // Resetea el PIN si es incorrecto
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Usa el color de fondo del tema
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly // Distribuye mejor
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Ingresa tu PIN de Seguridad",
                style = MaterialTheme.typography.titleLarge
            )
            if (isError) {
                Text(
                    text = "PIN incorrecto. Intenta de nuevo.",
                    color = MaterialTheme.colorScheme.error, // Usa color de error
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // 2. Indicador de puntos
        PinIndicator(pin.length, isError)

        // 3. Teclado numérico
        NumericKeypad(
            onNumberClick = { num ->
                if (pin.length < 4) {
                    pin += num
                    if (pin.length == 4) {
                        pinViewModel.validatePin(pin)
                    }
                }
            },
            onBackspaceClick = {
                pin = pin.dropLast(1)
            },
            enabled = uiState != PinUiState.LOADING
        )
    }
}

@Composable
fun PinIndicator(length: Int, isError: Boolean) {
    val errorColor = MaterialTheme.colorScheme.error
    val defaultColor = MaterialTheme.colorScheme.primary

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp) // Más espacio
    ) {
        (0..3).forEach { index ->
            Box(
                modifier = Modifier
                    .size(20.dp) // Ligeramente más pequeño
                    .clip(CircleShape)
                    .background(
                        color = if (index < length) {
                            if (isError) errorColor else defaultColor
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant // Color de fondo para "vacío"
                        }
                    )
            )
        }
    }
}

@Composable
fun NumericKeypad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    enabled: Boolean
) {
    val buttons = (1..9).map { it.toString() } + " " + "0" + "DEL"

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
        modifier = Modifier.padding(horizontal = 32.dp) // Centra el teclado
    ) {
        items(count = buttons.size) { index ->
            val item = buttons[index]

            when (item) {
                " " -> {
                    Spacer(modifier = Modifier.size(72.dp)) // Botones más grandes
                }
                "DEL" -> {
                    IconButton( // Cambiado a IconButton para un look más limpio
                        onClick = onBackspaceClick,
                        modifier = Modifier.size(72.dp),
                        enabled = enabled
                    ) {
                        Icon(
                            Icons.Default.Backspace,
                            contentDescription = "Borrar",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                else -> {
                    FilledTonalButton( // Un botón menos "ruidoso" que Button
                        onClick = { onNumberClick(item) },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        enabled = enabled,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(item, fontSize = 28.sp) // Texto más grande
                    }
                }
            }
        }
    }
}