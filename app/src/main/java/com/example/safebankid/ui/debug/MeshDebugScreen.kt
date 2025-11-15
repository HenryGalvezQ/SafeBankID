package com.example.safebankid.ui.debug

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.safebankid.services.camera.CameraManager
import com.example.safebankid.services.ml.FaceMeshAnalyzer
import com.example.safebankid.services.ml.FaceMeshPacket
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun MeshDebugScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val meshResultsFlow = remember { MutableStateFlow<FaceMeshPacket?>(null) }
    val meshPacket by meshResultsFlow.collectAsState()

    val meshAnalyzer = remember {
        FaceMeshAnalyzer(
            context = context,
            resultsFlow = meshResultsFlow,
            enableLogs = true
        )
    }

    val cameraManager = remember { CameraManager(context) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    cameraManager.bind(this, lifecycleOwner, meshAnalyzer)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 👇 aquí solo espejamos para la cámara frontal
        FaceMeshOverlay(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = -1f),
            packet = meshPacket
        )
    }
}

@Composable
fun FaceMeshOverlay(
    modifier: Modifier = Modifier,
    packet: FaceMeshPacket?
) {
    Canvas(modifier = modifier) {
        val packetNonNull = packet ?: return@Canvas
        val result = packetNonNull.result
        val rotation = packetNonNull.rotation
        val frameW = packetNonNull.frameWidth
        val frameH = packetNonNull.frameHeight

        if (result.faceLandmarks().isEmpty()) return@Canvas

        // ancho/alto del frame ya considerando la rotación
        val (imgW, imgH) = if (rotation == 90 || rotation == 270) {
            frameH.toFloat() to frameW.toFloat()
        } else {
            frameW.toFloat() to frameH.toFloat()
        }

        val canvasW = size.width
        val canvasH = size.height

        // 👇 FILL: usamos el factor más grande
        val scale = maxOf(
            canvasW / imgW,
            canvasH / imgH
        )

        val drawW = imgW * scale
        val drawH = imgH * scale

        // centramos y recortamos lo que sobre
        val offsetX = (canvasW - drawW) / 2f
        val offsetY = (canvasH - drawH) / 2f

        result.faceLandmarks().forEach { landmarks ->
            landmarks.forEach { landmark ->
                val nx = landmark.x()
                val ny = landmark.y()

                // la rotación que ya vimos que te funciona
                val (rx, ry) = when (rotation) {
                    90 -> 1f - ny to nx
                    270 -> ny to 1f - nx
                    180 -> 1f - nx to 1f - ny
                    else -> nx to ny
                }

                val x = offsetX + rx * drawW
                val y = offsetY + ry * drawH

                drawCircle(
                    color = Color(0xFF00FF00),
                    radius = 4f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
