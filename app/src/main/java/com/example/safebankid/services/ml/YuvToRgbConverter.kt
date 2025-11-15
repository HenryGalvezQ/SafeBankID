package com.example.safebankid.services.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Conversor simple de YUV_420_888 (CameraX) a Bitmap ARGB_8888.
 * Hay versiones más optimizadas, pero esta es clara.
 */
class YuvToRgbConverter(private val context: Context) {

    fun yuvToRgb(image: ImageProxy, output: Bitmap) {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U y V están intercambiados en ImageProxy
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // copiar al bitmap de salida (que tiene el tamaño correcto)
        val scaled = Bitmap.createScaledBitmap(bitmap, output.width, output.height, true)
        val canvas = android.graphics.Canvas(output)
        canvas.drawBitmap(scaled, 0f, 0f, null)
    }
}
