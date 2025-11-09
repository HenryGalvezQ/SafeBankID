package com.example.safebankid.services.ml

// "El Contrato" - Actualizado para el flujo con botón
sealed class LivenessState {

    // 1. La cámara está encendida, buscando un rostro
    object SearchingFace : LivenessState()

    // 2. Se detectó un rostro en el óvalo (se habilita el botón)
    object FaceFound : LivenessState()

    // 3. El usuario presionó "Verificar", ahora buscamos el parpadeo
    object AnalyzingBlink : LivenessState()

    // 4. Éxito (Reemplazamos 'Success' por dos destinos)
    object SuccessToDashboard : LivenessState() // Ir directo al dashboard
    object SuccessToPin : LivenessState()       // Ir primero al PIN

    // 5. Error (con un mensaje)
    data class Error(val message: String) : LivenessState()
}