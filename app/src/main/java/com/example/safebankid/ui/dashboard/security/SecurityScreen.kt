package com.example.safebankid.ui.dashboard.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.example.safebankid.ui.dashboard.DashboardViewModel
import com.example.safebankid.data.repository.FaceSample
import com.example.safebankid.data.repository.SecurityRepository
import com.example.safebankid.services.AppLockerService

// --- PESTAÑA 2: SEGURIDAD (Centro de Control) ---

@Composable
fun SecurityScreen(
    navController: NavController,
    viewModel: DashboardViewModel // Recibe el ViewModel
) {

    // 1. Lee el estado de los switches DESDE el ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // --- MODALES DE SEGURIDAD ---

    // 1. El Modal que pide la contraseña ANTES de una acción
    if (uiState.isRequirePasswordModalVisible) {
        RequirePasswordModal(
            viewModel = viewModel,
            onDismiss = { viewModel.hideRequirePasswordModal() }
        )
    }

    // 2. El Modal para CAMBIAR la contraseña
    if (uiState.isChangePasswordModalVisible) {
        ChangePasswordModal(
            viewModel = viewModel,
            onDismiss = { viewModel.hideChangePasswordModal() }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- SECCIÓN DE LOGIN ---
        item {
            Text(
                text = "Seguridad de Inicio",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // 1. Switch Principal: Activar Auth Detector
        item {
            SecuritySwitchCard(
                icon = Icons.Default.Face,
                title = "Auth Detector (ML)",
                description = "Verificación facial al abrir la app",
                isEnabled = uiState.authDetectorEnabled,
                // CAMBIO: Ahora llama al ViewModel para que pida la contraseña
                onToggle = { viewModel.onAuthDetectorToggled(it) }
            )
        }

        // 2. Switch de Combinar PIN
        item {
            SecuritySwitchCard(
                icon = Icons.Default.Shield,
                title = "Exigir PIN además de Auth Detector",
                description = "Doble seguridad: Rostro + PIN",
                isEnabled = uiState.combinePinEnabled,
                enabled = uiState.authDetectorEnabled, // Se deshabilita si el switch de arriba está apagado
                onToggle = { viewModel.onCombinePinToggled(it) }
            )
        }

        // --- SECCIÓN DE PRIVACIDAD ---
        item {
            Text(
                text = "Privacidad en la App",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
        }

        // 3. Switch de Privacy Guard
        item {
            SecuritySwitchCard(
                icon = Icons.Default.Shield,
                title = "Privacy Guard",
                description = "Bloquear PIN si alguien espía",
                isEnabled = uiState.privacyGuardEnabled,
                onToggle = { viewModel.onPrivacyGuardToggled(it) } // Llama al ViewModel
            )
        }

        // --- SECCIÓN DE CUENTA ---
        item {
            Text(
                text = "Cuenta",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
        }

        // 1. NUEVO BOTÓN para cambiar contraseña
        item {
            SecurityActionCard(
                icon = Icons.Default.Lock,
                title = "Configurar Contraseña de Respaldo",
                description = "Se usará si el Auth Detector falla",
                onClick = { viewModel.showChangePasswordModal() }
            )
        }

        // 2. Botón de Re-configurar Rostro (ahora pide contraseña)
        item {
            SecurityActionCard(
                icon = Icons.Default.Face,
                title = "Re-configurar Rostro",
                description = "Actualiza tu verificación facial",
                onClick = {
                    viewModel.showRequirePasswordModal(navController)
                }
            )
        }
        item {
            Text(
                text = "Protección de Apps (App Locker)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
        }
        item {
            // Este es el nuevo panel de control para activar el servicio
            AppLockerPermissionCard()
        }
        // --- SECCIÓN DE DEPURACIÓN (solo dev) ---
        item {
            Text(
                text = "Depuración (solo desarrollo)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
        }
        item {
            // Si expusiste 'repository' como propiedad:
            FaceDatasetDebugCard(repository = viewModel.repository2)
            // O si usaste el getter:
            // FaceDatasetDebugCard(repository = viewModel.getRepository())
        }

        item {
            SecurityActionCard(
                icon = Icons.Default.Face, // O el icono que prefieras
                title = "Probar Face Mesh (468 Puntos)",
                description = "Ver la malla 3D de MediaPipe en tiempo real",
                onClick = {
                    navController.navigate("meshDebug")
                }
            )
        }
    }
}


// --- NUEVO MODAL: Pedir Contraseña (MODIFICADO) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequirePasswordModal(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }

    // --- 1. Estado para la visibilidad de la contraseña ---
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Acción Segura", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Ingresa tu contraseña de respaldo para continuar",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; isError = false },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),

                    // --- 2. Lógica de visibilidad ---
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),

                    // --- 3. Icono del "ojo" ---
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        val description = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"

                        Icon(
                            imageVector = image,
                            contentDescription = description,
                            // --- 4. Lógica de "Mantener Presionado" ---
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        passwordVisible = true
                                        try {
                                            awaitRelease()
                                        } finally {
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

                    Text(
                        text = "¿Olvidaste la contraseña?",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable(onClick = { /* No hace nada */ })
                    )
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isLoading = true
                            isError = false
                            viewModel.checkPasswordAndExecute(password) { success ->
                                isLoading = false
                                if (!success) { isError = true }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Confirmar")
                        }
                    }
                }
            }
        }
    }
}

// --- NUEVO MODAL: Cambiar Contraseña (MODIFICADO) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordModal(
    viewModel: DashboardViewModel,
    onDismiss: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    // --- 1. Estado para la visibilidad de CADA contraseña ---
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Cambiar Contraseña", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                // --- CAMPO 1: Contraseña Actual ---
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; isError = false },
                    label = { Text("Contraseña Actual") },
                    singleLine = true,
                    isError = isError,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (currentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (currentPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        Icon(
                            imageVector = image,
                            contentDescription = "Mostrar/Ocultar contraseña actual",
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        currentPasswordVisible = true
                                        try { awaitRelease() } finally { currentPasswordVisible = false }
                                    }
                                )
                            }
                        )
                    }
                )

                // --- Texto de "¿Olvidaste contraseña?" solo para el campo "Actual" ---
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    if (isError) {
                        Text(
                            text = "Contraseña actual incorrecta",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                    Text(
                        text = "¿Olvidaste la contraseña?",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .clickable(onClick = { /* No hace nada */ })
                    )
                }

                Spacer(Modifier.height(16.dp)) // Más espacio

                // --- CAMPO 2: Nueva Contraseña ---
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Nueva Contraseña") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (newPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        Icon(
                            imageVector = image,
                            contentDescription = "Mostrar/Ocultar nueva contraseña",
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        newPasswordVisible = true
                                        try { awaitRelease() } finally { newPasswordVisible = false }
                                    }
                                )
                            }
                        )
                    }
                )
                Spacer(Modifier.height(8.dp))

                // --- CAMPO 3: Confirmar Nueva Contraseña ---
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirmar Nueva Contraseña") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        val image = if (confirmPasswordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                        Icon(
                            imageVector = image,
                            contentDescription = "Mostrar/Ocultar confirmación",
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        confirmPasswordVisible = true
                                        try { awaitRelease() } finally { confirmPasswordVisible = false }
                                    }
                                )
                            }
                        )
                    }
                )

                // (Quitamos el 'if (isError)' de aquí, ya lo pusimos arriba)

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newPassword != confirmPassword) {
                                // TODO: Mostrar error de confirmación
                                return@Button
                            }
                            isLoading = true
                            isError = false
                            viewModel.changePassword(currentPassword, newPassword) { success ->
                                isLoading = false
                                if (!success) { isError = true }
                            }
                        },
                        enabled = !isLoading && newPassword.isNotEmpty() && confirmPassword.isNotEmpty()
                    ) {
                        Text("Guardar")
                    }
                }
            }
        }
    }
}


// --- Composables de Tarjetas de Seguridad (Sin cambios) ---

@Composable
fun SecurityActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        onClick = onClick // La tarjeta entera es clickeable
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp), // Más padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SecuritySwitchCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit, // Tu parámetro (este nombre está bien)
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
            Switch(
                checked = isEnabled,
                // --- LA CORRECCIÓN ESTÁ AQUÍ ---
                // El parámetro del Switch se llama 'onCheckedChange'
                onCheckedChange = onToggle,
                // ---------------------------------
                enabled = enabled
            )
        }
    }
}

@Composable
fun FaceDatasetDebugCard(repository: SecurityRepository) {
    val samples = remember { mutableStateListOf<FaceSample>() }

    LaunchedEffect(Unit) {
        samples.clear()
        samples.addAll(repository.getFaceSamples())
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Debug dataset facial", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Muestras guardadas: ${samples.size}")

            Spacer(Modifier.height(12.dp))
            if (samples.isNotEmpty()) {
                val last = samples.first()
                val bmp = remember(last.imgB64) {
                    com.example.safebankid.services.ml.base64ToBitmap(last.imgB64)
                }
                // Mostrar la última muestra 112×112
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Última muestra",
                    modifier = Modifier.size(112.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("ts=${last.ts}  rot=${last.rot}  (${last.w}x${last.h})",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("No hay muestras todavía", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AppLockerPermissionCard() {
    val context = LocalContext.current

    // Estos "produceState" verifican el estado actual de los permisos
    val accessibilityEnabled by produceState(initialValue = false, context) {
        value = isAccessibilityServiceEnabled(context)
    }
    val drawOverlayEnabled by produceState(initialValue = false, context) {
        value = Settings.canDrawOverlays(context)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AppBlocking, "App Locker", tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    Text("Activar App Locker", fontWeight = FontWeight.Bold)
                    Text("Protege otras apps (Yape, BCP) con tu rostro.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Botón 1: Permiso de Accesibilidad
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (accessibilityEnabled) Color(0xFF167C16) else MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                )
            ) {
                Text(if (accessibilityEnabled) "1. Servicio de Accesibilidad (✓ Activado)" else "1. Activar Servicio")
            }

            Spacer(Modifier.height(8.dp))

            // Botón 2: Permiso de Dibujar Encima
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (drawOverlayEnabled) Color(0xFF167C16) else MaterialTheme.colorScheme.secondary,
                    contentColor = Color.White
                )
            ) {
                Text(if (drawOverlayEnabled) "2. Dibujar sobre otras apps (✓ Activado)" else "2. Activar Superposición")
            }
        }
    }
}

// --- AÑADE ESTA FUNCIÓN HELPER AL FINAL DEL ARCHIVO ---
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val service = "${context.packageName}/${AppLockerService::class.java.canonicalName}"
    val settingValue = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return settingValue?.let {
        TextUtils.SimpleStringSplitter(':').apply { setString(it) }
            .any { s -> s.equals(service, ignoreCase = true) }
    } ?: false
}
