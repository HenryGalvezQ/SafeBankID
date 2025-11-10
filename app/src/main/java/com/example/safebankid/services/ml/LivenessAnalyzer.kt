// app/src/main/java/com/example/safebankid/services/ml/LivenessAnalyzer.kt
package com.example.safebankid.services.ml

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.min
import android.graphics.Rect
import com.example.safebankid.services.ml.bitmapToBase64Jpeg
import com.example.safebankid.services.ml.imageProxyToBitmap
import com.example.safebankid.services.ml.resize112
import com.example.safebankid.services.ml.safeCrop
import com.example.safebankid.data.repository.FaceSample

class LivenessAnalyzer(
    private val onFaceCentered: (Boolean) -> Unit,
    private val onBlinkResult: (Boolean, String?) -> Unit
) : ImageAnalysis.Analyzer {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // ojos
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(options)

    @Volatile private var verifying = false
    private var phase = 0 // 0=esperar cerrado, 1=vimos cerrado → esperar abierto
    private var deadline = 0L

    fun startBlinkWindow(ms: Long = 3000) {
        verifying = true
        phase = 0
        deadline = SystemClock.elapsedRealtime() + ms
    }

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(input)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face != null) {
                    // 1) rostro centrado (aprox. al círculo de la UI)
                    val cx = face.boundingBox.centerX().toFloat()
                    val cy = face.boundingBox.centerY().toFloat()
                    val centered = isCentered(cx, cy, imageProxy.width.toFloat(), imageProxy.height.toFloat())
                    onFaceCentered(centered)
                    try {
                        val bmp = imageProxyToBitmap(imageProxy)
                        val crop = safeCrop(bmp, face.boundingBox)
                        val norm = resize112(crop)
                        val b64 = bitmapToBase64Jpeg(norm, 70)

                        // Landmarks simples (si existen)
                        val lm = mutableMapOf<String, Float>()
                        face.leftEyeOpenProbability?.let { lm["leftEyeOpen"] = it }
                        face.rightEyeOpenProbability?.let { lm["rightEyeOpen"] = it }
                        face.smilingProbability?.let { lm["smile"] = it }

                        lastSample = com.example.safebankid.data.repository.FaceSample(
                            ts = System.currentTimeMillis(),
                            imgB64 = b64,
                            w = imageProxy.width,
                            h = imageProxy.height,
                            rot = imageProxy.imageInfo.rotationDegrees,
                            landmarks = if (lm.isEmpty()) null else lm
                        )
                    } catch (_: Throwable) { /* Ignorar si un frame falla */ }
                    // 2) ventana de parpadeo
                    if (verifying) {
                        val left = face.leftEyeOpenProbability ?: -1f
                        val right = face.rightEyeOpenProbability ?: -1f
                        val avg = if (left >= 0f && right >= 0f) (left + right) / 2f else -1f
                        val open = avg >= 0.60f
                        val closed = avg in 0f..0.35f

                        val now = SystemClock.elapsedRealtime()
                        if (now > deadline) {
                            verifying = false
                            onBlinkResult(false, "No se detectó parpadeo dentro del tiempo")
                        } else {
                            when (phase) {
                                0 -> if (closed) phase = 1
                                1 -> if (open) {
                                    verifying = false
                                    onBlinkResult(true, null)
                                }
                            }
                        }
                    }
                } else {
                    onFaceCentered(false)
                    if (verifying && SystemClock.elapsedRealtime() > deadline) {
                        verifying = false
                        onBlinkResult(false, "Rostro perdido durante la verificación")
                    }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    // Aproxima el círculo de la UI: acepta si el centro del rostro cae dentro de un radio del 30% del min(w,h)
    private fun isCentered(cx: Float, cy: Float, w: Float, h: Float): Boolean {
        val rx = w * 0.5f
        val ry = h * 0.5f
        val radius = min(w, h) * 0.3f
        val dx = cx - rx
        val dy = cy - ry
        return (dx * dx + dy * dy) <= radius * radius
    }



    @Volatile private var lastSample: FaceSample? = null
    fun getLastFaceSample(): FaceSample? = lastSample
}
