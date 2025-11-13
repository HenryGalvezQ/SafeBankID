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
    // NUEVO: ¿estamos en flujo de reconfiguración de rostro?
    val isEnrollmentFlow =
        (mode == "enroll") && (
                uiState is LivenessState.Enrollment ||
                        uiState is LivenessState.EnrollmentDone ||
                        uiState is LivenessState.AnalyzingBlink ||
                        uiState is LivenessState.Error
                )

    val brightness by authViewModel.brightnessLevel.collectAsState(initial = null)
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
                isEnrollmentMode = isEnrollmentFlow,        // ⬅️ NUEVO
                captureBrightness = brightness,
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
                "Buscando rostro",
                "Alinea tu cara con el círculo hasta que se marque centrada.",
                MaterialTheme.colorScheme.onBackground
            )

            is LivenessState.FaceFound -> Triple(
                "Rostro detectado",
                "Mantén la posición y presiona “Verificar”.",
                MaterialTheme.colorScheme.primary
            )

            is LivenessState.AnalyzingBlink -> Triple(
                "Parpadea",
                "Parpadea suavemente, estamos analizando tu parpadeo.",
                MaterialTheme.colorScheme.primary
            )

            is LivenessState.Enrollment -> Triple(
                "Configurando tu rostro",
                "", // Descripción se arma abajo con las mini-fases
                MaterialTheme.colorScheme.primary
            )

            is LivenessState.EnrollmentDone -> Triple(
                "Rostro actualizado",
                "Tu patrón facial quedó guardado en el dispositivo.",
                Color(0xFF008D41)
            )

            is LivenessState.SuccessToDashboard,
            is LivenessState.SuccessToPin -> Triple(
                "Listo",
                "Verificación correcta. Abriendo tu sesión segura...",
                Color(0xFF008D41)
            )

            is LivenessState.RequireFallback -> Triple(
                "Verificación adicional",
                "Usa tu contraseña de respaldo para continuar.",
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
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = color,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        if (uiState is LivenessState.Enrollment) {
            // Mini-fases de enrollment
            EnrollmentMiniPhases(uiState)
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
            )
        }

        // Si quieres seguir mostrando info de similitud en errores, puedes dejarlo aquí
        if (lastSimilarity != null && uiState is LivenessState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Similitud con muestras guardadas: ${"%.2f".format(lastSimilarity)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EnrollmentMiniPhases(state: LivenessState.Enrollment) {
    val currentIndex = state.current + 1 // 1-based
    val phaseText = when (currentIndex) {
        in 1..5 -> "Mira de frente con buena luz."
        in 6..10 -> "Gira un poco la cabeza hacia los lados."
        else -> "Acércate a una ventana o cambia ligeramente de luz."
    }

    Text(
        text = "Muestra $currentIndex de ${state.total}",
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(4.dp))

    LinearProgressIndicator(
        progress = (currentIndex.coerceAtMost(state.total)).toFloat() / state.total.toFloat(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )

    Spacer(Modifier.height(4.dp))

    Text(
        text = phaseText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
fun CameraPanel(
    modifier: Modifier = Modifier,
    uiState: LivenessState,
    captureBrightness: Float?,
    isEnrollmentMode: Boolean,              // ⬅️ NUEVO// NUEVO
    onVerifyClicked: () -> Unit,
    onUseFallbackClicked: () -> Unit,
    onPreviewReady: (PreviewView) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Cámara en círculo
        val borderColor = when (uiState) {
            is LivenessState.FaceFound -> MaterialTheme.colorScheme.primary
            is LivenessState.SuccessToDashboard,
            is LivenessState.SuccessToPin,
            is LivenessState.EnrollmentDone -> Color(0xFF008D41)
            is LivenessState.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .border(
                    BorderStroke(
                        width = 6.dp,
                        color = borderColor
                    ),
                    CircleShape
                )
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        this.scaleType = PreviewView.ScaleType.FILL_CENTER
                        post {
                            onPreviewReady(this)
                        }
                    }
                }
            )
        }

        // Indicador de calidad de captura: Luz / Rostro
        CaptureQualityRow(
            brightness = captureBrightness,
            uiState = uiState
        )

        Spacer(Modifier.height(16.dp))

        val isVerifyEnabled = uiState is LivenessState.FaceFound || uiState is LivenessState.Enrollment

        val isAnalyzing = uiState is LivenessState.AnalyzingBlink

        val isButtonEnabled =
            !isAnalyzing && (
                    uiState is LivenessState.FaceFound ||
                            uiState is LivenessState.Error ||
                            (isEnrollmentMode && uiState is LivenessState.Enrollment)
                    )

        val buttonText = when {
            isEnrollmentMode && isAnalyzing -> "Analizando gesto..."
            isEnrollmentMode -> "Capturar muestra"
            else -> "Verificar"
        }

        Button(
            onClick = onVerifyClicked,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = isButtonEnabled
        ) {
            Text(
                text = buttonText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(8.dp))
        if (!isEnrollmentMode){
            TextButton(
                onClick = onUseFallbackClicked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Usar contraseña de respaldo",
                    textAlign = TextAlign.Center
                )
            }
        }

    }
}

@Composable
fun CaptureQualityRow(
    brightness: Float?,
    uiState: LivenessState
) {
    val (lightText, lightColor) = run {
        val b = brightness ?: 0f
        when {
            b >= 0.65f -> "Buena" to Color(0xFF008D41)
            b >= 0.40f -> "Media" to Color(0xFFFFA000)
            else -> "Baja" to Color(0xFFD32F2F)
        }
    }

    val isCentered = uiState is LivenessState.FaceFound ||
            uiState is LivenessState.AnalyzingBlink ||
            uiState is LivenessState.Enrollment

    val rostroText = if (isCentered) "Centrado" else "Muy lejos"
    val rostroColor = if (isCentered) Color(0xFF008D41) else Color.Gray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Luz",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Text(
                text = lightText,
                color = lightColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Rostro",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.End
            )
            Text(
                text = rostroText,
                color = rostroColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End
            )
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
