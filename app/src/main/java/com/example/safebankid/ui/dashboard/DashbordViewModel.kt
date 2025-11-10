package com.example.safebankid.ui.dashboard

// Importaciones necesarias
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.safebankid.data.local.SecurityPreferences
import com.example.safebankid.data.repository.SecurityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 1. Un data class para guardar TODOS los estados de seguridad
data class SecurityUiState(
    // Switches
    val authDetectorEnabled: Boolean = true,
    val combinePinEnabled: Boolean = false,
    val privacyGuardEnabled: Boolean = false,

    // Contraseña (simulada)
    val mlPassword: String = "contraseña", // Simulamos la contraseña guardada

    // Estados de los Modales
    val isChangePasswordModalVisible: Boolean = false,
    val isRequirePasswordModalVisible: Boolean = false,

    // Estado para saber qué acción disparó el modal
    val pendingAction: (() -> Unit)? = null
)

// 2. CAMBIO: Usa AndroidViewModel para tener acceso al Context
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    // 3. Inicializa el Repositorio y las Preferencias
    private val preferences = SecurityPreferences(application)
    private val repository = SecurityRepository(preferences)
    val repository2: SecurityRepository = repository
    // 4. CAMBIO: Inicializa el estado leyendo desde el Repositorio
    private val _uiState = MutableStateFlow(repository.getInitialSecurityUiState())
    val uiState = _uiState.asStateFlow()

    // --- MANEJO DE SWITCHES (AHORA REQUIEREN CONTRASEÑA) ---

    fun onAuthDetectorToggled(isEnabled: Boolean) {
        _uiState.update {
            it.copy(
                isRequirePasswordModalVisible = true,
                pendingAction = {
                    // 5. CAMBIO: Guarda en disco ANTES de actualizar la UI
                    repository.setAuthDetectorEnabled(isEnabled)
                    if (!isEnabled) {
                        // Lógica de UX: Si apagas el Auth Detector, apaga también la combinación
                        repository.setCombinePinEnabled(false)
                    }

                    _uiState.update { s ->
                        s.copy(
                            authDetectorEnabled = isEnabled,
                            combinePinEnabled = if (!isEnabled) false else s.combinePinEnabled
                        )
                    }
                }
            )
        }
    }

    fun onCombinePinToggled(isEnabled: Boolean) {
        _uiState.update {
            it.copy(
                isRequirePasswordModalVisible = true,
                pendingAction = {
                    // 6. CAMBIO: Guarda en disco
                    repository.setCombinePinEnabled(isEnabled)
                    _uiState.update { s -> s.copy(combinePinEnabled = isEnabled) }
                }
            )
        }
    }

    fun onPrivacyGuardToggled(isEnabled: Boolean) {
        _uiState.update {
            it.copy(
                isRequirePasswordModalVisible = true,
                pendingAction = {
                    // 7. CAMBIO: Guarda en disco
                    repository.setPrivacyGuardEnabled(isEnabled)
                    _uiState.update { s -> s.copy(privacyGuardEnabled = isEnabled) }
                }
            )
        }
    }

    // --- MANEJO DE MODALES ---

    fun showChangePasswordModal() {
        _uiState.update { it.copy(isRequirePasswordModalVisible = true, pendingAction = {
            _uiState.update { s -> s.copy(isChangePasswordModalVisible = true) }
        }) }
    }

    fun hideChangePasswordModal() {
        _uiState.update { it.copy(isChangePasswordModalVisible = false) }
    }

    fun hideRequirePasswordModal() {
        _uiState.update { it.copy(isRequirePasswordModalVisible = false, pendingAction = null) }
    }

    fun checkPasswordAndExecute(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            // 8. CAMBIO: Compara contra la contraseña guardada en el repositorio
            val success = repository.getPassword() == password
            if (success) {
                _uiState.value.pendingAction?.invoke()
                hideRequirePasswordModal()
            }
            onResult(success)
        }
    }

    fun changePassword(current: String, new: String, onResult: (Boolean) -> Unit) {
        // 9. CAMBIO: Compara contra el repositorio
        if (repository.getPassword() == current) {
            // 10. CAMBIO: Guarda la nueva contraseña en el repositorio
            repository.savePassword(new)
            _uiState.update { it.copy(
                mlPassword = new, // Actualiza el estado en memoria
                isChangePasswordModalVisible = false
            ) }
            onResult(true)
        } else {
            onResult(false)
        }
    }
}