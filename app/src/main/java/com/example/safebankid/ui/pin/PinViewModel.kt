package com.example.safebankid.ui.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Estado de la UI para la pantalla de PIN
enum class PinUiState { IDLE, LOADING, SUCCESS, ERROR }

// El Rol de Backend completará esto.
// Tú (Frontend) solo necesitas saber que existe.
class PinViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PinUiState.IDLE)
    val uiState = _uiState.asStateFlow()

    // El Frontend llamará a esto cuando el PIN esté completo
    fun validatePin(pin: String) {
        viewModelScope.launch {
            _uiState.value = PinUiState.LOADING
            delay(500) // Simula una comprobación de seguridad

            // Lógica de simulación (Backend la reemplazará)
            if (pin == "1234") {
                _uiState.value = PinUiState.SUCCESS
            } else {
                _uiState.value = PinUiState.ERROR
                delay(1000)
                _uiState.value = PinUiState.IDLE
            }
        }
    }
}