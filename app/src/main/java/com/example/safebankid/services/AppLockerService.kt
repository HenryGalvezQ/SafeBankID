package com.example.safebankid.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.safebankid.ui.lock.LockScreenActivity

class AppLockerService : AccessibilityService() {

    // --- ¡AQUÍ CONFIGURAS QUÉ APPS BLOQUEAR! ---
    // Añade los "package names" de Yape, BCP, etc.
    // Para probar, he añadido la calculadora de Samsung y Google.
    private val blockedApps = setOf(
        "com.google.android.calculator",  // Calculadora de Google
        "com.sec.android.app.popupcalculator", // Calculadora de Samsung
        "com.bcp.bank.bcp", // Ejemplo BCP
        "com.bcp.innovacxion.yapeapp",   // Ejemplo Yape
    )


    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            if (packageName in blockedApps) {

                // --- SOLUCIÓN AL PROBLEMA 2 (Revisión) ---
                val now = System.currentTimeMillis()

                // Si la app fue desbloqueada hace menos de 5 segundos, no hacer nada.
                if (AppLockerState.lastUnlockedApp == packageName &&
                    (now - AppLockerState.lastUnlockedTime) < 5000) // 5 seg de gracia
                {
                    return // Ignorar, ya está desbloqueado
                }

                Log.d("AppLockerService", "Bloqueando app: $packageName")

                val intent = Intent(this, LockScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // --- 1. ¡IMPORTANTE! Pasamos el nombre del paquete a la Activity ---
                    putExtra("BLOCKED_APP_PACKAGE", packageName)
                }
                startActivity(intent)
            }
        }
    }

    override fun onInterrupt() {
        Log.w("AppLockerService", "Servicio interrumpido")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT
        }
        serviceInfo = info
        Log.i("AppLockerService", "Servicio de accesibilidad conectado")
    }
}