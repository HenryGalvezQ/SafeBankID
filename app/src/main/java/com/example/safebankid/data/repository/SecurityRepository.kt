package com.example.safebankid.data.repository

import com.example.safebankid.data.local.SecurityPreferences
import com.example.safebankid.ui.dashboard.SecurityUiState
import kotlin.text.toFloat

/**
 * Repositorio que centraliza el acceso a las preferencias de seguridad.
 * El ViewModel hablará con esta clase, no directamente con SharedPreferences.
 */
// --- FaceSample: lo que luego usarás para sacar embeddings ---
data class FaceSample(
    val ts: Long,
    val imgB64: String,                // JPEG Base64 (112x112)
    val w: Int,                        // ancho original del frame analizado
    val h: Int,                        // alto original
    val rot: Int,                      // rotación del InputImage
    val landmarks: Map<String, Float>? // ej. {"leftEye.x":..., "leftEye.y":..., ...}
)

class SecurityRepository(private val preferences: SecurityPreferences) {

    /**
     * Obtiene el estado COMPLETO de la UI desde el disco.
     * Esto se llama al iniciar el ViewModel.
     */
    fun getInitialSecurityUiState(): SecurityUiState {
        val authDetector = preferences.getAuthDetectorEnabled()
        val combinePin = preferences.getCombinePinEnabled()
        val privacyGuard = preferences.getPrivacyGuardEnabled()
        val password = preferences.getPassword()

        return SecurityUiState(
            authDetectorEnabled = authDetector,
            combinePinEnabled = combinePin,
            privacyGuardEnabled = privacyGuard,
            mlPassword = password,
            // Los modales siempre inician cerrados
            isChangePasswordModalVisible = false,
            isRequirePasswordModalVisible = false,
            pendingAction = null
        )
    }

    // --- Métodos de Escritura (pasan la llamada a Preferences) ---

    fun setAuthDetectorEnabled(isEnabled: Boolean) {
        preferences.setAuthDetectorEnabled(isEnabled)
    }

    fun setCombinePinEnabled(isEnabled: Boolean) {
        preferences.setCombinePinEnabled(isEnabled)
    }

    fun setPrivacyGuardEnabled(isEnabled: Boolean) {
        preferences.setPrivacyGuardEnabled(isEnabled)
    }

    fun savePassword(password: String) {
        preferences.setPassword(password)
    }

    // --- Métodos de Lectura (pasan la llamada a Preferences) ---

    fun getPassword(): String {
        return preferences.getPassword()
    }
    data class VerificationLog(val ts: Long, val ok: Boolean, val reason: String?)

    fun appendVerification(success: Boolean, reason: String? = null) {
        preferences.appendVerificationLog(success, reason)
    }

    fun getVerificationHistory(): List<VerificationLog> {
        val raw = preferences.getVerificationHistoryJson()
        val arr = org.json.JSONArray(raw)
        val out = mutableListOf<VerificationLog>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(VerificationLog(o.getLong("ts"), o.getBoolean("ok"),
                if (o.has("reason") && !o.isNull("reason")) o.getString("reason") else null))
        }
        return out.reversed() // más reciente primero
    }

    //NUEVO



    fun saveFaceSample(sample: FaceSample, maxKeep: Int = 5) {
        val o = org.json.JSONObject().apply {
            put("ts", sample.ts)
            put("imgB64", sample.imgB64)
            put("w", sample.w)
            put("h", sample.h)
            put("rot", sample.rot)
            if (sample.landmarks != null) {
                val lm = org.json.JSONObject()
                sample.landmarks.forEach { (k,v) -> lm.put(k, v) }
                put("landmarks", lm)
            }
        }
        preferences.appendFaceSampleJson(o.toString(), maxKeep)
    }

    fun getFaceSamples(): List<FaceSample> {
        val raw = preferences.getFaceSamplesJson()
        val arr = org.json.JSONArray(raw)
        val out = mutableListOf<FaceSample>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val lm = if (o.has("landmarks")) {
                val lmObj = o.getJSONObject("landmarks")
                lmObj.keys().asSequence().associateWith { k -> lmObj.getDouble(k).toFloat() }
            } else null
            out.add(
                FaceSample(
                    ts = o.getLong("ts"),
                    imgB64 = o.getString("imgB64"),
                    w = o.getInt("w"),
                    h = o.getInt("h"),
                    rot = o.getInt("rot"),
                    landmarks = lm
                )
            )
        }
        return out.reversed()
    }

    fun clearFaceSamples() = preferences.clearFaceSamples()
}

