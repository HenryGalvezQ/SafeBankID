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
import kotlinx.coroutines.delay

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
    // Para evitar que el texto cambie a cada frame

    private var lastFaceUiUpdateTime = 0L
    private var lastCenteredFlag: Boolean? = null
    private var lastErrorAt: Long? = null

    // --- variables de enrolamiento ---
    private var enrollmentMode = false
    private var enrollmentTarget = 0
    private var enrollmentCollected = 0
    private var enrollmentRunning = false

    // Brillo promedio del recorte de rostro [0,1]
    private val _brightnessLevel = MutableStateFlow<Float?>(null)
    val brightnessLevel: StateFlow<Float?> = _brightnessLevel
    init {
        analyzer = LivenessAnalyzer(
            onFaceCentered = { centered ->
                // ⬇️ IMPORTANTE: no pisar el estado cuando estamos enrolando
                if (!enrollmentMode) {
                    updateFaceUiStateDebounced(centered)
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
    /**
     * Actualiza el estado de rostro (Buscando / Detectado) pero
     * con un pequeño "debounce" para que no cambie a cada frame.
     */
    private fun updateFaceUiStateDebounced(centered: Boolean) {
        val now = System.currentTimeMillis()
        val current = _uiState.value

        // No tocamos el estado si estamos analizando parpadeo o mostrando un error
        if (current is LivenessState.AnalyzingBlink) {
            return
        }
        // 2) Si estamos en Error, dejamos que el mensaje se vea ~1.2 segundos
        val errAt = lastErrorAt
        if (current is LivenessState.Error && errAt != null && (now - errAt) < 1200L) {
            // Aún dentro de la ventana de error, no cambiamos nada
            return
        }
        val newState: LivenessState =
            if (centered) LivenessState.FaceFound else LivenessState.SearchingFace

        val lastFlag = lastCenteredFlag
        val minIntervalMs = 400L  // mínimo tiempo entre cambios visibles

        // Si el estado lógico (centered / no centered) es el mismo y ha pasado poco tiempo, no hacemos nada
        if (lastFlag != null &&
            lastFlag == centered &&
            (now - lastFaceUiUpdateTime) < minIntervalMs
        ) {
            return
        }

        lastCenteredFlag = centered
        lastFaceUiUpdateTime = now
        _uiState.value = newState
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
    /**
     * Se llama cuando el usuario pulsa el botón de "Iniciar captura" en modo enrolamiento.
     * Toma 15 muestras automáticas:
     *  - Muestras  1..5 : frente
     *  - Muestras  6..10: mirando a la izquierda
     *  - Muestras 11..15: mirando a la derecha
     */
    fun onEnrollmentCaptureClicked() {
        if (!enrollmentMode || enrollmentRunning) return

        enrollmentRunning = true

        viewModelScope.launch {
            try {
                val total = enrollmentTarget
                val samplesPerPhase = 5
                val delayBetweenSamples = 700L    // tiempo entre muestras
                val delayBetweenPhases = 1500L    // pausa para que el usuario cambie de lado

                while (enrollmentCollected < total) {
                    val phaseIndex = enrollmentCollected / samplesPerPhase // 0,1,2

                    val sample = analyzer.buildLastFaceSample()
                    if (sample != null) {
                        // (opcional) guardar muestra visual
                        securityRepo.saveFaceSample(sample, maxKeep = 5)

                        val newEmbedding: FloatArray? = try {
                            val bmp = base64ToBitmap(sample.imgB64)
                            embeddingExtractor.extract(bmp)
                        } catch (e: Exception) {
                            Log.e("AuthViewModel", "Error generando embedding (enrolamiento): ${e.message}", e)
                            null
                        }

                        if (newEmbedding != null) {
                            securityRepo.saveFaceEmbedding(newEmbedding, maxKeep = 50)
                            enrollmentCollected++

                            // Actualizar la UI para que se vea "Muestra X de 15"
                            _uiState.value = LivenessState.Enrollment(
                                current = enrollmentCollected,
                                total = enrollmentTarget
                            )

                            // Si se completó un bloque de 5, damos una pausa extra
                            if (enrollmentCollected < total &&
                                enrollmentCollected % samplesPerPhase == 0
                            ) {
                                delay(delayBetweenPhases)
                                continue
                            }
                        }
                    }

                    if (enrollmentCollected >= total) break
                    delay(delayBetweenSamples)
                }

                if (enrollmentCollected >= total) {
                    enrollmentMode = false
                    _uiState.value = LivenessState.EnrollmentDone
                }
            } finally {
                enrollmentRunning = false
            }
        }
    }

    private fun handleBlinkResult(ok: Boolean, reason: String?) {
        viewModelScope.launch {
            if (!ok) {
                lastErrorAt = System.currentTimeMillis()
                // Mostrar el error
                _uiState.value = LivenessState.Error(reason ?: "No se detectó parpadeo")

                // Mantener el mensaje en pantalla ~1.2 segundos
                delay(1200L)

                // Si seguimos en Error y no estamos enrolando, volvemos a empezar la detección
                if (!enrollmentMode && _uiState.value is LivenessState.Error) {
                    _uiState.value = LivenessState.SearchingFace
                    lastCenteredFlag = null  // forzamos a recalcular desde cero
                }
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
                lastErrorAt = System.currentTimeMillis()
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
                lastErrorAt = System.currentTimeMillis()
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
