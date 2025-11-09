package com.example.safebankid.ui.auth

import android.app.Application // <-- Importar
import androidx.lifecycle.AndroidViewModel // <-- Cambiar de ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safebankid.data.local.SecurityPreferences // <-- Importar
import com.example.safebankid.data.repository.SecurityRepository // <-- Importar
import com.example.safebankid.services.ml.LivenessState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// CAMBIO: Usar AndroidViewModel para acceder al contexto
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LivenessState>(LivenessState.SearchingFace)
    val uiState: StateFlow<LivenessState> = _uiState

    // Inicializar el repositorio
    private val preferences = SecurityPreferences(application)
    private val repository = SecurityRepository(preferences)

    /**
     * El Rol de ML llamará a esto cuando el analizador
     * detecte que un rostro entró o salió del óvalo.
     */
    fun onFaceDetectionResult(faceFound: Boolean) {
        // Solo actualiza si el estado actual no es "analizando"
        if (_uiState.value !is LivenessState.AnalyzingBlink) {
            if (faceFound) {
                _uiState.value = LivenessState.FaceFound
            } else {
                _uiState.value = LivenessState.SearchingFace
            }
        }
    }

    /**
     * El Rol de Frontend (UI) llamará a esto cuando
     * el usuario presione el botón "Verificar".
     */
    fun onVerifyClicked() {
        _uiState.value = LivenessState.AnalyzingBlink

        // --- SIMULACIÓN (Backend/ML lo reemplazarán) ---
        // Simula una "grabación" de 3 segundos
        viewModelScope.launch {
            delay(3000)

            // --- ¡NUEVA LÓGICA DE NAVEGACIÓN! ---
            // 1. Leemos el estado guardado
            val combinePinEnabled = repository.getInitialSecurityUiState().combinePinEnabled

            // 2. Decidimos el destino
            if (combinePinEnabled) {
                _uiState.value = LivenessState.SuccessToPin // Ir al PIN
            } else {
                _uiState.value = LivenessState.SuccessToDashboard // Ir al Dashboard
            }
        }
    }
}