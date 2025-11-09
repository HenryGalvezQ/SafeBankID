package com.example.safebankid.ui.fallback

import android.app.Application // <-- Importar
import androidx.lifecycle.AndroidViewModel // <-- Cambiar de ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safebankid.data.local.SecurityPreferences // <-- Importar
import com.example.safebankid.data.repository.SecurityRepository // <-- Importar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// CAMBIO: Nuevos estados de éxito
enum class PasswordUiState { IDLE, LOADING, SUCCESS_TO_DASHBOARD, SUCCESS_TO_PIN, ERROR }

class FallbackPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PasswordUiState.IDLE)
    val uiState = _uiState.asStateFlow()

    // Inicializar el repositorio
    private val preferences = SecurityPreferences(application)
    private val repository = SecurityRepository(preferences)

    // El Backend conectará esto a EncryptedSharedPreferences
    // Leemos la contraseña real desde el repositorio
    private val correctPassword get() = repository.getPassword()

    fun validatePassword(password: String) {
        viewModelScope.launch {
            _uiState.value = PasswordUiState.LOADING
            delay(500)
            if (password == correctPassword) {
                // --- ¡NUEVA LÓGICA DE NAVEGACIÓN! ---
                val combinePinEnabled = repository.getInitialSecurityUiState().combinePinEnabled
                if (combinePinEnabled) {
                    _uiState.value = PasswordUiState.SUCCESS_TO_PIN
                } else {
                    _uiState.value = PasswordUiState.SUCCESS_TO_DASHBOARD
                }
            } else {
                _uiState.value = PasswordUiState.ERROR
                delay(1000)
                _uiState.value = PasswordUiState.IDLE
            }
        }
    }
}