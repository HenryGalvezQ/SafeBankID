package com.example.safebankid.data.repository

import com.example.safebankid.data.local.SecurityPreferences
import com.example.safebankid.ui.dashboard.SecurityUiState
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repositorio que centraliza el acceso a las preferencias de seguridad.
 * El ViewModel hablará con esta clase, no directamente con SharedPreferences.
 */

data class FaceSample(
    val ts: Long,
    val imgB64: String,
    val w: Int,
    val h: Int,
    val rot: Int,
    val landmarks: Map<String, Float>?
)

class SecurityRepository(private val preferences: SecurityPreferences) {

    private val gson = Gson()

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
            isChangePasswordModalVisible = false,
            isRequirePasswordModalVisible = false,
            pendingAction = null
        )
    }

    fun setAuthDetectorEnabled(isEnabled: Boolean) = preferences.setAuthDetectorEnabled(isEnabled)
    fun setCombinePinEnabled(isEnabled: Boolean) = preferences.setCombinePinEnabled(isEnabled)
    fun setPrivacyGuardEnabled(isEnabled: Boolean) = preferences.setPrivacyGuardEnabled(isEnabled)
    fun savePassword(password: String) = preferences.setPassword(password)

    fun getPassword(): String = preferences.getPassword()

    data class VerificationLog(val ts: Long, val ok: Boolean, val reason: String?)

    fun appendVerification(success: Boolean, reason: String? = null) {
        preferences.appendVerificationLog(success, reason)
    }

    fun getVerificationHistory(): List<VerificationLog> {
        val raw = preferences.getVerificationHistoryJson()
        val arr = JSONArray(raw)
        val out = mutableListOf<VerificationLog>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                VerificationLog(
                    o.getLong("ts"),
                    o.getBoolean("ok"),
                    if (o.has("reason") && !o.isNull("reason")) o.getString("reason") else null
                )
            )
        }
        return out.reversed() // más reciente primero
    }

    fun saveFaceSample(sample: FaceSample, maxKeep: Int = 5) {
        val o = JSONObject().apply {
            put("ts", sample.ts)
            put("imgB64", sample.imgB64)
            put("w", sample.w)
            put("h", sample.h)
            put("rot", sample.rot)
            sample.landmarks?.let { lm ->
                val lmObj = JSONObject()
                lm.forEach { (k, v) -> lmObj.put(k, v) }
                put("landmarks", lmObj)
            }
        }
        // esto ya lo tienes en SecurityPreferences
        preferences.appendFaceSampleJson(o.toString(), maxKeep)
    }

    fun getFaceSamples(): List<FaceSample> {
        val raw = preferences.getFaceSamplesJson()
        val arr = JSONArray(raw)
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

    /**
     * Guarda un embedding nuevo y mantiene solo [maxKeep].
     * Guardamos como JSON: List<List<Float>>
     */
    fun saveFaceEmbedding(embedding: FloatArray, maxKeep: Int = 5) {
        val current = getFaceEmbeddings().toMutableList()
        current.add(embedding)
        // rotación FIFO
        while (current.size > maxKeep) {
            current.removeAt(0)
        }
        val asList = current.map { emb -> emb.toList() }
        val json = gson.toJson(asList)
        preferences.setFaceEmbeddingsJson(json)
    }

    /**
     * Retorna la lista de embeddings guardados. Si no hay, lista vacía.
     */
    fun getFaceEmbeddings(): List<FloatArray> {
        val json = preferences.getFaceEmbeddingsJson() ?: return emptyList()
        if (json.isBlank()) return emptyList()

        val type = object : TypeToken<List<List<Float>>>() {}.type
        val list: List<List<Float>> = gson.fromJson(json, type)
        return list.map { inner -> inner.map { it.toFloat() }.toFloatArray() }
    }

    fun clearFaceEmbeddings() {
        preferences.setFaceEmbeddingsJson("[]")
    }
}
