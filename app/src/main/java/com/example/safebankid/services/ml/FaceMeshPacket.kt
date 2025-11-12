package com.example.safebankid.services.ml

import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

data class FaceMeshPacket(
    val result: FaceLandmarkerResult,
    val rotation: Int,
    val frameWidth: Int,
    val frameHeight: Int
)

