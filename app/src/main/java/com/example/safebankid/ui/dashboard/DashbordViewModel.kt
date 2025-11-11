package com.example.safebankid.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.example.safebankid.data.local.SecurityPreferences
import com.example.safebankid.data.repository.SecurityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Estado de la pestaña Seguridad
data class SecurityUiState(
    val authDetectorEnabled: Boolean = true,
    val combinePinEnabled: Boolean = false,
    val privacyGuardEnabled: Boolean = false,

    val mlPassword: String = "contraseña",

    val isChangePasswordModalVisible: Boolean = false,
    val isRequirePasswordModalVisible: Boolean = false,

    // acción que se ejecuta después de validar la contraseña
    val pendingAction: (() -> Unit)? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = SecurityPreferences(application)
    private val repository = SecurityRepository(preferences)
    // lo usas en la tarjeta de debug
    val repository2: SecurityRepository = repository

    private val _uiState = MutableStateFlow(repository.getInitialSecurityUiState())
    val uiState = _uiState.asStateFlow()

    // ---------------- switches ----------------

    fun onAuthDetectorToggled(isEnabled: Boolean) {
        _uiState.update {
            it.copy(
                isRequirePasswordModalVisible = true,
                pendingAction = {
                    repository.setAuthDetectorEnabled(isEnabled)
                    if (!isEnabled) {
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
                    repository.setPrivacyGuardEnabled(isEnabled)
                    _uiState.update { s -> s.copy(privacyGuardEnabled = isEnabled) }
                }
            )
        }
    }

    // ---------------- modales ----------------

    fun showChangePasswordModal() {
        // primero pedir contraseña, luego mostrar modal de cambio
        _uiState.update {
            it.copy(
                isRequirePasswordModalVisible = true,
                pendingAction = {
                    _uiState.update { s -> s.copy(isChangePasswordModalVisible = true) }
                }
            )
        }
    }

    fun hideChangePasswordModal() {
        _uiState.update { it.copy(isChangePasswordModalVisible = false) }
    }

    fun hideRequirePasswordModal() {
        _uiState.update { it.copy(isRequirePasswordModalVisible = false, pendingAction = null) }
    }

    // 👇 ESTA es la que te faltaba
    fun setPendingAction(action: () -> Unit) {
        _uiState.update {
            it.copy(
                isRequirePasswordModalVisible = true, // mostramos el modal
                pendingAction = action
            )
        }
    }

    fun checkPasswordAndExecute(password: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.getPassword() == password
            if (success) {
                // ejecutamos lo que estaba pendiente
                _uiState.value.pendingAction?.invoke()
                hideRequirePasswordModal()
            }
            onResult(success)
        }
    }

    fun changePassword(current: String, new: String, onResult: (Boolean) -> Unit) {
        if (repository.getPassword() == current) {
            repository.savePassword(new)
            _uiState.update {
                it.copy(
                    mlPassword = new,
                    isChangePasswordModalVisible = false
                )
            }
            onResult(true)
        } else {
            onResult(false)
        }
    }

    fun showRequirePasswordModal(navController: NavController) {
        _uiState.update {
            it.copy(
                isRequirePasswordModalVisible = true,
                pendingAction = {
                    navController.navigate("auth?mode=enroll")
                }
            )
        }
    }

}
