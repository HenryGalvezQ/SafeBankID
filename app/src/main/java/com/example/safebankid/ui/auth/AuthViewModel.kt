package com.example.safebankid.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safebankid.services.ml.LivenessState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// El Rol de Backend completará la lógica con la cámara real
class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<LivenessState>(LivenessState.SearchingFace)
    val uiState: StateFlow<LivenessState> = _uiState

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
            // Simula un éxito
            _uiState.value = LivenessState.Success

            // Simula un error (puedes probar descomentando esto)
            // _uiState.value = LivenessState.Error("No se detectó parpadeo. Intenta de nuevo.")
            // delay(2000)
            // _uiState.value = LivenessState.SearchingFace
        }
    }
}