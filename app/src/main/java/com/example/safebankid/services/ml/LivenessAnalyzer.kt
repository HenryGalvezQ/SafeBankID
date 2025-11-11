package com.example.safebankid.services.ml

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.safebankid.data.repository.FaceSample
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.min

class LivenessAnalyzer(
    private val onFaceCentered: (Boolean) -> Unit,
    private val onBlinkResult: (Boolean, String?) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
    )

    // último frame bueno
    private var lastFrameBitmap: Bitmap? = null
    private var lastFaceBox: Rect? = null
    private var lastRotation: Int = 0
    private var lastFrameW: Int = 0
    private var lastFrameH: Int = 0
    private var lastLandmarks: Map<String, Float>? = null

    private var verifying = false
    private var deadlineMs = 0L
    private var phase = 0

    fun startBlinkWindow(ms: Long = 3000L) {
        verifying = true
        deadlineMs = System.currentTimeMillis() + ms
        phase = 0
    }

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }

                    val centered = isCentered(
                        face.boundingBox.centerX().toFloat(),
                        face.boundingBox.centerY().toFloat(),
                        inputImage.width.toFloat(),
                        inputImage.height.toFloat()
                    )
                    onFaceCentered(centered)

                    val bmp = imageProxyToBitmap(imageProxy)
                    val rotatedBmp = rotateBitmap(bmp, rotation)

                    lastFrameBitmap = rotatedBmp
                    lastFaceBox = face.boundingBox
                    lastRotation = rotation
                    lastFrameW = imageProxy.width
                    lastFrameH = imageProxy.height
                    lastLandmarks = extractLandmarks(face)

                    if (verifying) {
                        handleBlink(face)
                    }
                } else {
                    onFaceCentered(false)
                    if (verifying && System.currentTimeMillis() > deadlineMs) {
                        verifying = false
                        onBlinkResult(false, "Rostro perdido durante la verificación")
                    }
                }
            }
            .addOnFailureListener {
                onFaceCentered(false)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleBlink(face: Face) {
        val now = System.currentTimeMillis()
        if (now > deadlineMs) {
            verifying = false
            onBlinkResult(false, "Tiempo agotado")
            return
        }

        val left = face.leftEyeOpenProbability ?: 1f
        val right = face.rightEyeOpenProbability ?: 1f
        val avg = (left + right) / 2f

        when (phase) {
            0 -> if (avg < 0.35f) phase = 1
            1 -> if (avg > 0.60f) {
                verifying = false
                onBlinkResult(true, null)
            }
        }
    }

    /**
     * Construye la muestra facial con el formato REAL de tu proyecto:
     * ts, imgB64, w, h, rot, landmarks
     */
    fun buildLastFaceSample(): FaceSample? {
        val bmp = lastFrameBitmap ?: return null
        val box = lastFaceBox ?: return null

        val cropped = safeCrop(bmp, box)
        val normalized = resize112(cropped)
        val b64 = bitmapToBase64Jpeg(normalized, 70)

        return FaceSample(
            ts = System.currentTimeMillis(),
            imgB64 = b64,
            w = lastFrameW,
            h = lastFrameH,
            rot = lastRotation,
            landmarks = lastLandmarks
        )
    }

    private fun extractLandmarks(face: Face): Map<String, Float> {
        val lm = mutableMapOf<String, Float>()
        face.leftEyeOpenProbability?.let { lm["leftEyeOpen"] = it }
        face.rightEyeOpenProbability?.let { lm["rightEyeOpen"] = it }
        face.smilingProbability?.let { lm["smile"] = it }
        return lm
    }

    private fun isCentered(cx: Float, cy: Float, w: Float, h: Float): Boolean {
        val rx = w * 0.5f
        val ry = h * 0.5f
        val radius = min(w, h) * 0.3f
        val dx = cx - rx
        val dy = cy - ry
        return (dx * dx + dy * dy) <= radius * radius
    }
}
