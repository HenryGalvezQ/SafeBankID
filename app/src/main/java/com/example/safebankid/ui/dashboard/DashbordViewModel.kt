package com.example.safebankid.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class DashboardViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SecurityUiState())
    val uiState = _uiState.asStateFlow()

    // --- MANEJO DE SWITCHES (AHORA REQUIEREN CONTRASEÑA) ---

    fun onAuthDetectorToggled(isEnabled: Boolean) {
        // En lugar de cambiarlo, pedimos la contraseña
        _uiState.update {
            it.copy(
                isRequirePasswordModalVisible = true,
                pendingAction = {
                    _uiState.update { s ->
                        s.copy(
                            authDetectorEnabled = isEnabled,
                            // Lógica de UX: Si apagas el Auth Detector, apaga también la combinación
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
            val success = _uiState.value.mlPassword == password
            if (success) {
                _uiState.value.pendingAction?.invoke()
                hideRequirePasswordModal()
            }
            onResult(success)
        }
    }

    fun changePassword(current: String, new: String, onResult: (Boolean) -> Unit) {
        if (_uiState.value.mlPassword == current) {
            _uiState.update { it.copy(mlPassword = new, isChangePasswordModalVisible = false) }
            onResult(true)
        } else {
            onResult(false)
        }
    }
}