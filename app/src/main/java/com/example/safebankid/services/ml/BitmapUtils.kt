package com.example.safebankid.services.ml

import android.graphics.*
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val yBuffer: ByteBuffer = image.planes[0].buffer
    val uBuffer: ByteBuffer = image.planes[1].buffer
    val vBuffer: ByteBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = java.io.ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
    val yuv = out.toByteArray()
    return BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
}

fun safeCrop(src: Bitmap, rect: Rect): Bitmap {
    val x = rect.left.coerceAtLeast(0)
    val y = rect.top.coerceAtLeast(0)
    val w = rect.width().coerceAtMost(src.width - x)
    val h = rect.height().coerceAtMost(src.height - y)
    return Bitmap.createBitmap(src, x, y, w, h)
}

fun resize112(bmp: Bitmap): Bitmap =
    Bitmap.createScaledBitmap(bmp, 112, 112, true)

fun bitmapToBase64Jpeg(bmp: Bitmap, quality: Int = 70): String {
    val baos = java.io.ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    return android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
}
fun base64ToBitmap(b64: String): android.graphics.Bitmap {
    val data = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
    return android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
}

fun rotateBitmap(source: Bitmap, angle: Int): Bitmap {
    if (angle == 0) return source

    val matrix = Matrix()
    matrix.postRotate(angle.toFloat())
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}