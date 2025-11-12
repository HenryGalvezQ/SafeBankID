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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import com.example.safebankid.services.ml.FaceMeshAnalyzer
import com.example.safebankid.services.ml.FaceMeshPacket
import com.example.safebankid.services.ml.mahalanobisDiagonal
import com.example.safebankid.services.ml.meanVar
import com.example.safebankid.services.ml.percentile

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
    private val meshFlow = MutableStateFlow<FaceMeshPacket?>(null)
    private val meshAnalyzer = FaceMeshAnalyzer(application, meshFlow, enableLogs = true)

    // estado para liveness (blink)
    private var verifying = false
    private var deadlineMs = 0L
    private var phase = 0 // 0: esperar ojos cerrados; 1: esperar abrir
    private var errorUntilMs = 0L


    // --- variables de enrolamiento ---
    private var enrollmentMode = false
    private var enrollmentTarget = 0
    private var enrollmentCollected = 0
    private var collectUntilMs = 0L
    private var nextCaptureAtMs = 0L
    private val captureIntervalMs = 120L   // ~8 fps efectivos

    init {
        // Colectar resultados de MediaPipe para estado + parpadeo
        viewModelScope.launch {
            meshFlow.collect { pkt ->
                val now = System.currentTimeMillis()

                val result = pkt?.result ?: run {
                    _uiState.value = LivenessState.SearchingFace
                    return@collect
                }
                val faces = result.faceLandmarks()
                if (faces.isEmpty()) {
                    if (now >= errorUntilMs) {
                        _uiState.value = LivenessState.SearchingFace
                    }
                    if (verifying && now > deadlineMs) {
                        verifying = false
                        postError("Rostro perdido durante la verificación", _lastSimilarity.value)
                    }
                    return@collect
                }
                // --- Captura automática durante ENROLAMIENTO --- // <<<
                if (enrollmentMode && collectUntilMs > 0L) {
                    val fvAuto = meshAnalyzer.getLastFeatureVector()
                    val nowAuto = now

                    if (fvAuto != null && nowAuto < collectUntilMs && nowAuto >= nextCaptureAtMs) {
                        securityRepo.appendLandmarkSample(fvAuto, maxKeep = 100)
                        enrollmentCollected += 1
                        nextCaptureAtMs = nowAuto + captureIntervalMs
                        _uiState.value = LivenessState.Enrollment(enrollmentCollected, enrollmentTarget)
                    }

                    // ¿terminamos por cantidad o por fin de ventana?
                    if (enrollmentCollected >= enrollmentTarget || nowAuto >= collectUntilMs) {
                        val samples = securityRepo.getLandmarkSamples()
                        val mv = meanVar(samples)
                        val dists = samples.map { mahalanobisDiagonal(it, mv.mean, mv.varDiag) }
                        val thr = percentile(dists, 0.90f) + 0.45f

                        securityRepo.saveLandmarkModel(
                            com.example.safebankid.data.repository.LandmarkModel(mv.mean, mv.varDiag, thr)
                        )
                        enrollmentMode = false
                        collectUntilMs = 0L
                        _uiState.value = LivenessState.EnrollmentDone
                    }

                    // No sigas procesando nada más en este frame si estás en enrolamiento
                    return@collect
                }
                // rostro presente
                if (!verifying) {
                    if (now >= errorUntilMs) {
                        _uiState.value = LivenessState.FaceFound
                    }
                    return@collect
                }

                // liveness por blendshapes: eyeBlinkLeft/Right altos = cerrados
                val blendsOpt = result.faceBlendshapes()
                if (!blendsOpt.isPresent) return@collect

                val cats = blendsOpt.get().firstOrNull() ?: return@collect
                var blinkL = 0f; var blinkR = 0f
                for (c in cats) {
                    val name = c.categoryName()
                    val sc = c.score()
                    if (name == "eyeBlinkLeft") blinkL = sc
                    else if (name == "eyeBlinkRight") blinkR = sc
                }
                val avgBlink = (blinkL + blinkR) / 2f

                when (phase) {
                    0 -> if (avgBlink > 0.55f) phase = 1 // detectó cierre
                    1 -> if (avgBlink < 0.30f) {
                        // apertura → éxito
                        verifying = false
                        handleBlinkSuccess()
                    }
                }

                // timeout
                if (System.currentTimeMillis() > deadlineMs) {
                    verifying = false
                    _uiState.value = LivenessState.Error("Tiempo agotado", lastSimilarity = _lastSimilarity.value)
                }
            }
        }
    }

    private fun postError(message: String, sim: Float? = null) {
        _uiState.value = LivenessState.Error(message, lastSimilarity = sim)
        errorUntilMs = System.currentTimeMillis() + 2500L // 2.5s “pegajoso”
    }
    fun attachCamera(previewView: PreviewView, owner: LifecycleOwner) {
        cameraManager.bind(previewView, owner, meshAnalyzer)
    }

    fun onVerifyClicked() {
        _uiState.value = LivenessState.AnalyzingBlink
        verifying = true
        deadlineMs = System.currentTimeMillis() + 6000L
        phase = 0
    }

    /**
     * Llamar esto cuando el usuario pulse "Re-configurar rostro"
     */
    fun startEnrollment(samples: Int = 20) {
        enrollmentMode = true
        enrollmentTarget = samples
        enrollmentCollected = 0
        _lastSimilarity.value = null
        securityRepo.clearLandmarkSamplesRepo()
        _uiState.value = LivenessState.Enrollment(0, enrollmentTarget)
    }
    private fun handleBlinkSuccess() {
        viewModelScope.launch {
            // (opcional) snapshot visual de debug
            meshAnalyzer.buildLastFaceSample()?.let { securityRepo.saveFaceSample(it, maxKeep = 5) }

            if (enrollmentMode) {
                // 👉 NO guardes una sola muestra aquí.
                // Abre una ventana de 2.5s para capturar automáticamente varias.
                val now = System.currentTimeMillis()
                collectUntilMs = now + 2500L
                nextCaptureAtMs = now
                _uiState.value = LivenessState.Enrollment(enrollmentCollected, enrollmentTarget)
                return@launch
            }

            // -------- Verificación normal (cuando NO estás enrolando) --------
            val fv = meshAnalyzer.getLastFeatureVector()
            if (fv == null) {
                postError("No se pudo leer landmarks", _lastSimilarity.value)
                return@launch
            }

            val model = securityRepo.loadLandmarkModel()
            if (model == null) {
                postError("Sin modelo: realiza la matrícula")
                return@launch
            }

            val d = mahalanobisDiagonal(fv, model.mean, model.varDiag)
            val sim = 1f / (1f + d)
            _lastSimilarity.value = sim

            if (d <= model.threshold) {
                _uiState.value = LivenessState.SuccessToDashboard
            } else {
                postError(
                    "No coincide (d=%.2f > τ=%.2f)".format(d, model.threshold),
                    sim
                )
            }
        }
    }
    override fun onCleared() {
        super.onCleared()
        // por si quieres liberar el executor de cámara
        // cameraManager.shutdown()  // <-- si tu CameraManager lo expone
    }
}
