package com.example.safebankid.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.safebankid.services.ml.LivenessState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AuthScreen(
    navController: NavController,
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            hasCameraPermission = isGranted
        }
    )

    SideEffect {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val uiState by authViewModel.uiState.collectAsState()

    // --- NAVEGACIÓN EN ÉXITO (MODIFICADA) ---
    LaunchedEffect(uiState) {
        when (uiState) {
            // Si el destino es Dashboard, limpiamos "auth" del stack
            is LivenessState.SuccessToDashboard -> {
                navController.navigate("dashboard") {
                    popUpTo("auth") { inclusive = true } // Limpia el stack
                }
            }
            // Si el destino es PIN, NO limpiamos "auth"
            // El stack será: auth -> pin -> dashboard
            is LivenessState.SuccessToPin -> {
                navController.navigate("pin")
            }
            else -> {
                // No hacer nada en otros estados
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background), // Fondo blanco
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasCameraPermission) {

            // --- 1. Panel Superior: Guía ---
            GuidePanel(
                // CAMBIO: Menos peso para la guía (más arriba)
                modifier = Modifier.weight(0.8f),
                uiState = uiState
            )

            // --- 2. Panel Inferior: Cámara y Acción ---
            CameraPanel(
                // CAMBIO: Más peso para la cámara (más grande)
                modifier = Modifier.weight(1.2f),
                uiState = uiState,
                onVerifyClicked = { authViewModel.onVerifyClicked() },
                // CAMBIO: La acción ahora navega a la nueva pantalla
                onUseFallbackClicked = { navController.navigate("fallbackPassword") }
            )

        } else {
            // UI si el permiso fue denegado
            PermissionDeniedContent(
                modifier = Modifier.fillMaxSize(),
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        }
    }
}

@Composable
fun GuidePanel(
    modifier: Modifier = Modifier,
    uiState: LivenessState
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center // Centra todo
    ) {

        // 1. El "GIF" (usamos un icono por ahora)
        Icon(
            imageVector = Icons.Default.FaceRetouchingNatural,
            contentDescription = "Guía de parpadeo",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        // 2. El Texto de Guía (Mejorado)
        val (title, text, color) = when (uiState) {
            is LivenessState.SearchingFace -> Triple(
                "Centra tu Rostro",
                "Posiciona tu rostro en el círculo de abajo.",
                MaterialTheme.colorScheme.onBackground
            )
            is LivenessState.FaceFound -> Triple(
                "¡Excelente!",
                "Presiona 'Verificar' para iniciar la grabación y parpadear.",
                MaterialTheme.colorScheme.primary
            )
            is LivenessState.AnalyzingBlink -> Triple(
                "Grabando...",
                "Parpadea lentamente. No te muevas.",
                MaterialTheme.colorScheme.primary
            )
            // Lógica de éxito actualizada
            is LivenessState.SuccessToDashboard, is LivenessState.SuccessToPin -> Triple(
                "¡Verificado!",
                "Iniciando sesión de forma segura...",
                Color(0xFF008D41)
            )
            is LivenessState.Error -> Triple(
                "Error en la Verificación",
                uiState.message,
                MaterialTheme.colorScheme.error
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text,
            color = color,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(48.dp) // Altura fija para evitar saltos
        )
    }
}

@Composable
fun CameraPanel(
    modifier: Modifier = Modifier,
    uiState: LivenessState,
    onVerifyClicked: () -> Unit,
    onUseFallbackClicked: () -> Unit, // CAMBIO de nombre
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly // Distribuye el espacio
    ) {

        // 1. El Círculo de la Cámara
        val borderColor = when (uiState) {
            is LivenessState.FaceFound -> MaterialTheme.colorScheme.primary
            is LivenessState.SuccessToDashboard, is LivenessState.SuccessToPin -> Color(0xFF008D41)
            is LivenessState.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        Box(
            modifier = Modifier
                .size(260.dp) // CAMBIO: Círculo más grande
                .clip(CircleShape)
                .background(Color.Black) // Fondo negro por si la cámara tarda en cargar
                .border(
                    BorderStroke(6.dp, borderColor),
                    CircleShape
                )
        ) {
            CameraView(
                modifier = Modifier.fillMaxSize(),
                onViewReady = {
                    // TODO: Conectar esto al CameraManager del Rol de ML
                    // authViewModel.startCamera(previewView, lifecycleOwner)
                }
            )
        }

        // 2. Columna de Botones
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onVerifyClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                // Se habilita solo cuando se encuentra un rostro
                enabled = uiState is LivenessState.FaceFound
            ) {
                Text("Verificar", fontSize = 18.sp)
            }

            Spacer(Modifier.height(16.dp))

            // CAMBIO: El texto y la acción del botón de fallback
            TextButton(onClick = onUseFallbackClicked) {
                Text("Usar Contraseña de Respaldo")
            }
        }
    }
}


@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    onViewReady: (PreviewView) -> Unit
) {
    val context = LocalContext.current
    AndroidView(
        factory = {
            val previewView = PreviewView(context).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            onViewReady(previewView)
            previewView
        },
        modifier = modifier
    )
}

@Composable
fun PermissionDeniedContent(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permiso de Cámara Requerido",
            textAlign = TextAlign.Center
        )
        Text(
            text = "SafeBank ID necesita acceso a la cámara para verificar tu identidad.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Conceder Permiso")
        }
    }
}