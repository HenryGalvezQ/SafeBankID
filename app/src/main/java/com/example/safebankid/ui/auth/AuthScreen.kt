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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun AuthScreen(
    navController: NavController, // Aún se necesita para el fallback y el re-enroll
    authViewModel: AuthViewModel = viewModel(),
    // --- 1. ¡NUEVO PARÁMETRO! ---
    // Esta es la función que se llamará en caso de éxito.
    onSuccess: (LivenessState) -> Unit,
    onNavigateToFallback: () -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val mode = backStackEntry?.arguments?.getString("mode")

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasCameraPermission = isGranted }
    )

    LaunchedEffect(mode) {
        if (mode == "enroll") {
            authViewModel.startEnrollment(samples = 15)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val uiState by authViewModel.uiState.collectAsState()
    val lastSimilarity by authViewModel.lastSimilarity.collectAsState(initial = null)
    val lifecycleOwner = LocalLifecycleOwner.current

    // --- 2. LÓGICA DE NAVEGACIÓN MODIFICADA ---
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            // Si es éxito (cualquiera de los dos)...
            is LivenessState.SuccessToDashboard,
            is LivenessState.SuccessToPin -> {
                // ...en lugar de navegar, ¡llamamos al callback!
                onSuccess(state)
            }
            is LivenessState.EnrollmentDone -> {
                // El re-enrolamiento SÍ es una acción interna,
                // así que esta navegación se queda aquí.
                navController.navigate("dashboard") {
                    popUpTo("dashboard") { inclusive = false }
                }
            }
            else -> Unit // Ignorar otros estados
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasCameraPermission) {
            GuidePanel(
                modifier = Modifier.weight(0.8f),
                uiState = uiState,
                lastSimilarity = lastSimilarity
            )
            CameraPanel(
                modifier = Modifier.weight(1.2f),
                uiState = uiState,
                onVerifyClicked = { authViewModel.onVerifyClicked() },
                // El botón de fallback usa el NavController (¡por eso lo conservamos!)
                onUseFallbackClicked = onNavigateToFallback,
                onPreviewReady = { previewView ->
                    authViewModel.attachCamera(previewView, lifecycleOwner)
                }
            )
        } else {
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
    uiState: LivenessState,
    lastSimilarity: Float? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Icon(
            imageVector = Icons.Default.FaceRetouchingNatural,
            contentDescription = "Guía de parpadeo",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(24.dp))

        val (title, text, color) = when (uiState) {
            is LivenessState.SearchingFace -> Triple(
                "Centra tu rostro",
                "Posiciona tu rostro en el círculo de abajo.",
                MaterialTheme.colorScheme.onBackground
            )
            is LivenessState.FaceFound -> Triple(
                "¡Excelente!",
                "Presiona “Verificar” para iniciar.",
                MaterialTheme.colorScheme.primary
            )
            is LivenessState.AnalyzingBlink -> Triple(
                "Grabando...",
                "Parpadea lentamente. No te muevas.",
                MaterialTheme.colorScheme.primary
            )
            is LivenessState.Enrollment -> Triple(
                "Entrenando rostro",
                "Muestra ${uiState.current + 1} de ${uiState.total}. Parpadea.",
                MaterialTheme.colorScheme.primary
            )
            is LivenessState.EnrollmentDone -> Triple(
                "Rostro actualizado",
                "Ya puedes volver a la app.",
                Color(0xFF008D41)
            )
            is LivenessState.SuccessToDashboard,
            is LivenessState.SuccessToPin -> Triple(
                "¡Verificado!",
                "Iniciando sesión segura...",
                Color(0xFF008D41)
            )
            // 👇 ESTA era la que te faltaba
            is LivenessState.RequireFallback -> Triple(
                "Verificación adicional",
                "Usa tu contraseña de respaldo.",
                MaterialTheme.colorScheme.tertiary
            )
            is LivenessState.Error -> Triple(
                "Error en la verificación",
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
            modifier = Modifier.height(48.dp)
        )

        // mostrar similitud solo si tenemos un valor
        if (lastSimilarity != null &&
            (uiState is LivenessState.Error || uiState is LivenessState.FaceFound)
        ) {
            Spacer(Modifier.height(8.dp))
            val percent = (lastSimilarity * 100f).toInt()
            Text(
                text = "Similitud: $percent%",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun CameraPanel(
    modifier: Modifier = Modifier,
    uiState: LivenessState,
    onVerifyClicked: () -> Unit,
    onUseFallbackClicked: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {

        val borderColor = when (uiState) {
            is LivenessState.FaceFound -> MaterialTheme.colorScheme.primary
            is LivenessState.SuccessToDashboard,
            is LivenessState.SuccessToPin -> Color(0xFF008D41)
            is LivenessState.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(CircleShape)
                .background(Color.Black)
                .border(
                    BorderStroke(6.dp, borderColor),
                    CircleShape
                )
        ) {
            CameraView(
                modifier = Modifier.fillMaxSize(),
                onViewReady = onPreviewReady
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Button(
                onClick = onVerifyClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState is LivenessState.FaceFound ||
                        uiState is LivenessState.Error ||
                        uiState is LivenessState.Enrollment // también en entrenamiento
            ) {
                Text("Verificar", fontSize = 18.sp)
            }

            Spacer(Modifier.height(16.dp))

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
        Text("Permiso de cámara requerido", textAlign = TextAlign.Center)
        Text(
            "SafeBank ID necesita acceso a la cámara para verificar tu identidad.",
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("Conceder permiso")
        }
    }
}
