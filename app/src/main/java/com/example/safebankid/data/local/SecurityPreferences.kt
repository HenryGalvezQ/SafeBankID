package com.example.safebankid.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys // <-- Importa 'MasterKeys' (plural)

private const val PREF_FILE_NAME = "safebank_secure_prefs"
private const val KEY_AUTH_DETECTOR_ENABLED = "auth_detector_enabled"
private const val KEY_COMBINE_PIN_ENABLED = "combine_pin_enabled"
private const val KEY_PRIVACY_GUARD_ENABLED = "privacy_guard_enabled"
private const val KEY_ML_PASSWORD = "ml_password"
private const val KEY_VERIFICATION_HISTORY = "verification_history"
private const val KEY_FACE_SAMPLES = "face_samples_json"
private const val KEY_LM_SAMPLES = "lm_samples"
private const val KEY_LM_MODEL = "lm_model"
/**
 * Esta clase maneja la lectura y escritura en EncryptedSharedPreferences.
 * Es la única clase que sabe "cómo" se guardan los datos.
 */
class SecurityPreferences(context: Context) {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val KEY_FACE_EMBS = "face_embeddings_json"

    private val sharedPreferences = EncryptedSharedPreferences.create(
        PREF_FILE_NAME,
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getAuthDetectorEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTH_DETECTOR_ENABLED, true)
    }

    fun getCombinePinEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_COMBINE_PIN_ENABLED, false)
    }

    fun getPrivacyGuardEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_PRIVACY_GUARD_ENABLED, false)
    }

    fun getPassword(): String {
        return sharedPreferences.getString(KEY_ML_PASSWORD, "contraseña") ?: "contraseña"
    }

    fun setAuthDetectorEnabled(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTH_DETECTOR_ENABLED, isEnabled).apply()
    }

    fun setCombinePinEnabled(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_COMBINE_PIN_ENABLED, isEnabled).apply()
    }

    fun setPrivacyGuardEnabled(isEnabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_PRIVACY_GUARD_ENABLED, isEnabled).apply()
    }

    fun setPassword(password: String) {
        sharedPreferences.edit().putString(KEY_ML_PASSWORD, password).apply()
    }
    fun appendVerificationLog(success: Boolean, reason: String?) {
        val now = System.currentTimeMillis()
        val sp = sharedPreferences
        val current = sp.getString(KEY_VERIFICATION_HISTORY, "[]") ?: "[]"
        val arr = org.json.JSONArray(current)
        val obj = org.json.JSONObject()
            .put("ts", now)
            .put("ok", success)
            .put("reason", reason)

        arr.put(obj)

        // Conservamos las últimas 50
        val trimmed = org.json.JSONArray()
        val start = kotlin.math.max(0, arr.length() - 50)
        for (i in start until arr.length()) trimmed.put(arr.get(i))

        sp.edit().putString(KEY_VERIFICATION_HISTORY, trimmed.toString()).apply()
    }

    fun getVerificationHistoryJson(): String =
        sharedPreferences.getString(KEY_VERIFICATION_HISTORY, "[]") ?: "[]"

    fun appendFaceSampleJson(sampleJson: String, maxKeep: Int = 5) {
        val current = sharedPreferences.getString(KEY_FACE_SAMPLES, "[]") ?: "[]"
        val arr = org.json.JSONArray(current)
        arr.put(org.json.JSONObject(sampleJson))

        val trimmed = org.json.JSONArray()
        val start = kotlin.math.max(0, arr.length() - maxKeep)
        for (i in start until arr.length()) trimmed.put(arr.get(i))

        sharedPreferences.edit().putString(KEY_FACE_SAMPLES, trimmed.toString()).apply()
    }

    fun getFaceSamplesJson(): String =
        sharedPreferences.getString(KEY_FACE_SAMPLES, "[]") ?: "[]"

    fun clearFaceSamples() {
        sharedPreferences.edit().remove(KEY_FACE_SAMPLES).apply()
    }

    fun getFaceEmbeddingsJson(): String? =
        sharedPreferences.getString(KEY_FACE_EMBS, "")

    fun setFaceEmbeddingsJson(json: String) {
        sharedPreferences.edit().putString(KEY_FACE_EMBS, json).apply()
    }
    fun getLandmarkSamplesJson(): String =
        sharedPreferences.getString(KEY_LM_SAMPLES, "[]") ?: "[]"

    fun setLandmarkSamplesJson(json: String) {
        sharedPreferences.edit().putString(KEY_LM_SAMPLES, json).apply()
    }

    fun clearLandmarkSamples() {
        sharedPreferences.edit().remove(KEY_LM_SAMPLES).apply()
    }

    fun getLandmarkModelJson(): String? =
        sharedPreferences.getString(KEY_LM_MODEL, null)

    fun setLandmarkModelJson(json: String) {
        sharedPreferences.edit().putString(KEY_LM_MODEL, json).apply()
    }
}