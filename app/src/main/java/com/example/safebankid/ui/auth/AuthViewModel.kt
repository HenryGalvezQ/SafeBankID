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
import com.example.safebankid.services.camera.CameraManager
import com.example.safebankid.services.ml.LivenessAnalyzer
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

// CAMBIO: Usar AndroidViewModel para acceder al contexto
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SecurityRepository(SecurityPreferences(application))
    private val _uiState = MutableStateFlow<LivenessState>(LivenessState.SearchingFace)
    val uiState: StateFlow<LivenessState> = _uiState

    // Inicializar el repositorio
    private val preferences = SecurityPreferences(application)
    private val repository = SecurityRepository(preferences)
    private val cameraManager = CameraManager(application)
    private val combinePinEnabled by lazy { repo.getInitialSecurityUiState().combinePinEnabled }
    private val analyzer: LivenessAnalyzer = LivenessAnalyzer(
        onFaceCentered = { centered ->
            _uiState.value = if (centered) LivenessState.FaceFound else LivenessState.SearchingFace
        },
        onBlinkResult = { ok, reason ->
            viewModelScope.launch {
                if (ok) {
                    // Guardar éxito y navegar según config
                    repo.appendVerification(success = true, reason = null)
                    // 2) Persistimos la última muestra facial (si existe)
                    val sample = analyzer.getLastFaceSample()
                    if (sample != null) {
                        repository.saveFaceSample(sample, maxKeep = 5) // guarda/rota últimas 5
                        val n = repository.getFaceSamples().size
                        android.util.Log.d("SafeBankID", "FaceSample guardado. Total=${n}")
                    }
                    val histN = repository.getVerificationHistory().size
                    android.util.Log.d("SafeBankID", "Intentos guardados=${histN}")
                    _uiState.value = if (combinePinEnabled) {
                        LivenessState.SuccessToPin
                    } else {
                        LivenessState.SuccessToDashboard
                    }
                } else {
                    // Guardar fallo y volver a buscar
                    repo.appendVerification(success = false, reason = reason ?: "Fallo")
                    _uiState.value = LivenessState.Error(reason ?: "Fallo en verificación")
                    _uiState.value = LivenessState.SearchingFace
                }
            }
        }
    )
    fun attachCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        cameraManager.bind(previewView, lifecycleOwner, analyzer)
    }

    fun onVerifyClicked() {
        _uiState.value = LivenessState.AnalyzingBlink
        analyzer.startBlinkWindow() // 3s por defecto
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.shutdown()
    }
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
}