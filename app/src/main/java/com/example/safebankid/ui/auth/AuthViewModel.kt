package com.example.safebankid.ui.auth

import android.app.Application
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.safebankid.data.local.SecurityPreferences
import com.example.safebankid.data.repository.SecurityRepository
import com.example.safebankid.services.camera.CameraManager
import com.example.safebankid.services.ml.FaceEmbeddingExtractor
import com.example.safebankid.services.ml.LivenessAnalyzer
import com.example.safebankid.services.ml.base64ToBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

// 👇 estados de la UI/auth
sealed class LivenessState {
    object SearchingFace : LivenessState()
    object FaceFound : LivenessState()
    object AnalyzingBlink : LivenessState()
    object SuccessToDashboard : LivenessState()
    object SuccessToPin : LivenessState()
    object RequireFallback : LivenessState()

    // captura de muestras de enrolamiento
    data class Enrollment(val current: Int, val total: Int) : LivenessState()
    object EnrollmentDone : LivenessState()

    // error con similitud opcional
    data class Error(val message: String, val lastSimilarity: Float? = null) : LivenessState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val securityPrefs = SecurityPreferences(application)
    private val securityRepo = SecurityRepository(securityPrefs)

    private val _uiState = MutableStateFlow<LivenessState>(LivenessState.SearchingFace)
    val uiState: StateFlow<LivenessState> = _uiState

    // para mostrar en la UI
    private val _lastSimilarity = MutableStateFlow<Float?>(null)
    val lastSimilarity: StateFlow<Float?> = _lastSimilarity

    private val cameraManager = CameraManager(application)
    private val embeddingExtractor by lazy {
        FaceEmbeddingExtractor(application.applicationContext)
    }
    private val analyzer: LivenessAnalyzer

    // --- variables de enrolamiento ---
    private var enrollmentMode = false
    private var enrollmentTarget = 0
    private var enrollmentCollected = 0
    // Brillo promedio del recorte de rostro [0,1]
    private val _brightnessLevel = MutableStateFlow<Float?>(null)
    val brightnessLevel: StateFlow<Float?> = _brightnessLevel
    init {
        analyzer = LivenessAnalyzer(
            onFaceCentered = { centered ->
                // ⬇️ IMPORTANTE: no pisar el estado cuando estamos enrolando
                if (!enrollmentMode) {
                    _uiState.value =
                        if (centered) LivenessState.FaceFound else LivenessState.SearchingFace
                }
            },
            onBlinkResult = { ok, reason ->
                handleBlinkResult(ok, reason)
            },
            onBrightnessChanged = { value ->
                _brightnessLevel.value = value
            }
        )
    }

    fun attachCamera(previewView: PreviewView, owner: LifecycleOwner) {
        cameraManager.bind(previewView, owner, analyzer)
    }

    fun onVerifyClicked() {
        _uiState.value = LivenessState.AnalyzingBlink
        analyzer.startBlinkWindow()
    }

    /**
     * Llamar esto cuando el usuario pulse "Re-configurar rostro"
     */
    fun startEnrollment(samples: Int = 15) {
        enrollmentMode = true
        enrollmentTarget = samples
        enrollmentCollected = 0
        _lastSimilarity.value = null
        // limpiar embeddings anteriores
        securityRepo.clearFaceEmbeddings()
        _uiState.value = LivenessState.Enrollment(0, enrollmentTarget)
    }

    private fun handleBlinkResult(ok: Boolean, reason: String?) {
        viewModelScope.launch {
            if (!ok) {
                _uiState.value = LivenessState.Error(reason ?: "No se detectó parpadeo")
                return@launch
            }

            // 1. guardar muestra visual (para debug)
            val sample = analyzer.buildLastFaceSample()
            sample?.let { securityRepo.saveFaceSample(it, maxKeep = 5) }

            // 2. sacar embedding
            val newEmbedding: FloatArray? = try {
                sample?.let {
                    val bmp = base64ToBitmap(it.imgB64)
                    embeddingExtractor.extract(bmp)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Error generando embedding: ${e.message}", e)
                null
            }

            if (newEmbedding == null) {
                _uiState.value = LivenessState.Error("No se pudo procesar el rostro.")
                return@launch
            }

            // 3. si estamos enrolando, solo guardamos N embeddings
            if (enrollmentMode) {
                // guardamos bastante por si en el futuro subimos las muestras
                securityRepo.saveFaceEmbedding(newEmbedding, maxKeep = 50)
                enrollmentCollected += 1

                if (enrollmentCollected >= enrollmentTarget) {
                    enrollmentMode = false
                    _uiState.value = LivenessState.EnrollmentDone
                } else {
                    _uiState.value = LivenessState.Enrollment(
                        current = enrollmentCollected,
                        total = enrollmentTarget
                    )
                }
                return@launch
            }

            // 4. verificación normal
            val storedEmbeddings = securityRepo.getFaceEmbeddings()

            if (storedEmbeddings.isEmpty()) {
                // si no hay nada, podemos auto-enrolar
                securityRepo.saveFaceEmbedding(newEmbedding, maxKeep = 50)
                _uiState.value = LivenessState.EnrollmentDone
                return@launch
            }

            // calcular similitud contra todos
            val sims = storedEmbeddings
                .map { stored -> cosineSimilarity(newEmbedding, stored) }
                .sortedDescending()

            val best = sims.first()
            val avgTop3 = sims.take(5).average().toFloat()

            // guardar en flujo para que UI lo muestre si quiere
            _lastSimilarity.value = best

            // regla doble
            val pass = best >= 0.93f && avgTop3 >= 0.88f

            if (pass) {
                // opcional: refrescar solo si es muy bueno
                if (best >= 0.95f) {
                    securityRepo.saveFaceEmbedding(newEmbedding, maxKeep = 15)
                }
                _uiState.value = LivenessState.SuccessToDashboard
            } else {
                _uiState.value = LivenessState.Error(
                    message = "Rostro no coincide lo suficiente",
                    lastSimilarity = best
                )
            }
        }
    }

    // ------------ helpers -------------
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = (sqrt(na.toDouble()) * sqrt(nb.toDouble())).toFloat()
        return if (denom == 0f) 0f else dot / denom
    }

    override fun onCleared() {
        super.onCleared()
        // por si quieres liberar el executor de cámara
        // cameraManager.shutdown()  // <-- si tu CameraManager lo expone
    }
}
