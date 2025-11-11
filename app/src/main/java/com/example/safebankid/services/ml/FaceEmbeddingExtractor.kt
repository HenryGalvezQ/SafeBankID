package com.example.safebankid.services.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.sqrt

/**
 * Carga un modelo TFLite de embeddings faciales desde assets y expone extract(bitmap)
 * Esta versión asume:
 * - entrada: 160x160x3 RGB en rango [0,1]
 * - salida: embedding de 128D
 * Cambia inputSize / embeddingDim según tu modelo.
 */
class FaceEmbeddingExtractor(context: Context) {

    private val interpreter: Interpreter
    private val embeddingDim = 128
    private val inputSize = 160

    init {
        val modelBuffer = loadModelFile(context, "face_embedding.tflite")
        val options = Interpreter.Options()
        interpreter = Interpreter(modelBuffer, options)
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val afd = context.assets.openFd(modelName)
        FileInputStream(afd.fileDescriptor).use { fis ->
            val channel = fis.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }

    /**
     * Recibe un bitmap (cualquier tamaño) y lo adapta al modelo.
     */
    fun extract(bitmap: Bitmap): FloatArray {
        val scaled = if (bitmap.width != inputSize || bitmap.height != inputSize) {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        } else {
            bitmap
        }

        val inputBuffer =
            ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
                order(ByteOrder.nativeOrder())
            }

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val px = scaled.getPixel(x, y)
                // modelo de ese repo: /255f → [0,1]
                val r = (((px shr 16) and 0xFF) / 127.5f) - 1f
                val g = (((px shr 8) and 0xFF) / 127.5f) - 1f
                val b = ((px and 0xFF) / 127.5f) - 1f
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }
        }

        val output = Array(1) { FloatArray(embeddingDim) }
        interpreter.run(inputBuffer, output)
        val emb = output[0]

        l2Normalize(emb)

        return emb
    }

    private fun l2Normalize(vec: FloatArray) {
        var sum = 0f
        for (v in vec) sum += v * v
        if (sum == 0f) return
        val inv = 1f / sqrt(sum)
        for (i in vec.indices) {
            vec[i] *= inv
        }
    }
}
