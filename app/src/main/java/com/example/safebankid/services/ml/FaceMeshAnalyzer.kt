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
}
