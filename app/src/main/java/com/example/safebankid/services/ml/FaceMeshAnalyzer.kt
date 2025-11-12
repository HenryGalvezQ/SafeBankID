package com.example.safebankid.services.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Analizador que usa MediaPipe FaceLandmarker en modo LIVE_STREAM
 * y manda los resultados por un StateFlow para que el overlay los dibuje.
 * Ahora puede sacar logs detallados cuando enableLogs = true.
 */
class FaceMeshAnalyzer(
    context: Context,
    private val resultsFlow: MutableStateFlow<FaceMeshPacket?>,
    private val enableLogs: Boolean = false
) : ImageAnalysis.Analyzer {

    @Volatile
    private var lastRotation: Int = 0

    @Volatile
    private var lastFrameWidth: Int = 0

    @Volatile
    private var lastFrameHeight: Int = 0

    private val faceLandmarker: FaceLandmarker
    private val yuvToRgb = YuvToRgbConverter(context)
    @Volatile private var lastFeatureVec: FloatArray? = null
    @Volatile private var lastBitmap: Bitmap? = null
    @Volatile private var lastBbox: android.graphics.Rect? = null
    fun getLastFeatureVector(): FloatArray? = lastFeatureVec

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true)              // queremos los blendshapes
            .setOutputFacialTransformationMatrixes(true) // y también la matriz 4x4
            .setResultListener { result, _ ->
                // logs opcionales solo cuando abrimos la pantalla de malla
                if (enableLogs) {
                    logResult(result)
                }

                // mandamos TODO lo que necesita el overlay
                resultsFlow.value = FaceMeshPacket(
                    result = result,
                    rotation = lastRotation,
                    frameWidth = lastFrameWidth,
                    frameHeight = lastFrameHeight
                )

                val facesLandmarks = result.faceLandmarks()
                if (facesLandmarks.isNotEmpty()) {
                    val lms = facesLandmarks[0]
                    val n = lms.size
                    val xs = FloatArray(n)
                    val ys = FloatArray(n)
                    val zs = FloatArray(n)

                    var minX = 1f; var maxX = 0f
                    var minY = 1f; var maxY = 0f

                    for (i in 0 until n) {
                        val lm = lms[i]
                        val x = lms[i].x()
                        val y = lms[i].y()
                        val z = lm.z()
                        xs[i] = x; ys[i] = y; zs[i] = z
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }

                    // 1) vector de features
                    try {
                        lastFeatureVec = featuresFromLandmarksXYZ(xs, ys ,zs)
                    } catch (_: Throwable) { /* ignorar frame malo */ }

                    // 2) bbox en píxeles a partir de [0..1]
                    val W = lastFrameWidth.coerceAtLeast(1)
                    val H = lastFrameHeight.coerceAtLeast(1)
                    val l = (minX * W).toInt().coerceIn(0, W-1)
                    val t = (minY * H).toInt().coerceIn(0, H-1)
                    val r = (maxX * W).toInt().coerceIn(l+1, W)
                    val b = (maxY * H).toInt().coerceIn(t+1, H)
                    lastBbox = android.graphics.Rect(l, t, r, b)
                } else {
                    lastFeatureVec = null
                    lastBbox = null
                }

            }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {
            // guardamos los metadatos del frame
            lastRotation = imageProxy.imageInfo.rotationDegrees
            lastFrameWidth = imageProxy.width
            lastFrameHeight = imageProxy.height

            // pasamos de YUV a Bitmap (usando tu helper)
            val bitmap = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            lastBitmap = bitmap

            yuvToRgb.yuvToRgb(imageProxy, bitmap)

            // MediaPipe usa su propio tipo de imagen
            val mpImage = BitmapImageBuilder(bitmap).build()

            val imageOpts = ImageProcessingOptions.builder()
                .setRotationDegrees(lastRotation) // se la pasamos también a MediaPipe
                .build()

            // timestamp creciente
            faceLandmarker.detectAsync(
                mpImage,
                imageOpts,
                SystemClock.uptimeMillis()
            )
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Saca por Logcat lo que llega de MediaPipe:
     * - cuántas caras
     * - cuántos landmarks
     * - 3 primeros puntos xyz
     * - primeros 5 blendshapes
     * - matriz de transformación si viene
     */
    private fun logResult(result: FaceLandmarkerResult) {
        val TAG = "FaceMesh"

        // LANDMARKS
        val facesLandmarks = result.faceLandmarks()
        Log.d(TAG, "facesLandmarks count = ${facesLandmarks.size}")

        if (facesLandmarks.isNotEmpty()) {
            val firstFace = facesLandmarks[0]
            Log.d(TAG, "first face landmarks = ${firstFace.size}")

            // solo 3 para no inundar
            firstFace.take(3).forEachIndexed { i, lm ->
                Log.d(
                    TAG,
                    "lm[$i] x=${lm.x()} y=${lm.y()} z=${lm.z()}"
                )
            }
        }

        // BLENDSHAPES (vienen como Optional<List<List<Category>>>)
        val blendOpt = result.faceBlendshapes()
        if (blendOpt.isPresent) {
            val blendFaces = blendOpt.get()
            Log.d(TAG, "blendshapes faces = ${blendFaces.size}")

            if (blendFaces.isNotEmpty()) {
                val firstFaceBlends = blendFaces[0] // List<Category>
                firstFaceBlends.take(5).forEach { cat ->
                    Log.d(
                        TAG,
                        "blendshape=${cat.categoryName()} score=${cat.score()}"
                    )
                }
            }
        } else {
            Log.d(TAG, "no blendshapes in result")
        }

        // MATRIZ 4x4 DE LA CARA
        val matricesOpt = result.facialTransformationMatrixes()
        if (matricesOpt.isPresent) {
            val matrices = matricesOpt.get()
            Log.d(TAG, "facial matrices count = ${matrices.size}")
            if (matrices.isNotEmpty()) {
                val m0 = matrices[0]
                Log.d(TAG, "first matrix length = ${m0.size}") // debería ser 16
            }
        }
    }

    fun buildLastFaceSample(): com.example.safebankid.data.repository.FaceSample? {
        val bmp = lastBitmap ?: return null
        val box = lastBbox ?: return null
        val cropped = safeCrop(bmp, box)
        val normalized = resize112(cropped)
        val b64 = bitmapToBase64Jpeg(normalized, 70)

        return com.example.safebankid.data.repository.FaceSample(
            ts = System.currentTimeMillis(),
            imgB64 = b64,
            w = lastFrameWidth,
            h = lastFrameHeight,
            rot = lastRotation,
            landmarks = null // (opcional: podrías guardar el vector también)
        )
    }

}
