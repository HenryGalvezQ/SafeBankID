package com.example.safebankid.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys // <-- Importa 'MasterKeys' (plural)

// Constantes para las llaves de las preferencias
private const val PREF_FILE_NAME = "safebank_secure_prefs"
private const val KEY_AUTH_DETECTOR_ENABLED = "auth_detector_enabled"
private const val KEY_COMBINE_PIN_ENABLED = "combine_pin_enabled"
private const val KEY_PRIVACY_GUARD_ENABLED = "privacy_guard_enabled"
private const val KEY_ML_PASSWORD = "ml_password"

/**
 * Esta clase maneja la lectura y escritura en EncryptedSharedPreferences.
 * Es la única clase que sabe "cómo" se guardan los datos.
 */
class SecurityPreferences(context: Context) {

    // 1. Configura la llave maestra para la encripción (¡usando MasterKeys!)
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    // 2. Configura las preferencias encriptadas
    private val sharedPreferences = EncryptedSharedPreferences.create(
        PREF_FILE_NAME, // <-- Argumento 1: Nombre del archivo (String)
        masterKeyAlias, // <-- Argumento 2: La llave maestra (MasterKey)
        context,        // <-- Argumento 3: El contexto (Context)
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // <-- CORRECCIÓN DE TIPO
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // --- Métodos de Lectura (Getters) ---

    fun getAuthDetectorEnabled(): Boolean {
        // El valor predeterminado (true) solo se usa la primera vez que se abre la app
        return sharedPreferences.getBoolean(KEY_AUTH_DETECTOR_ENABLED, true)
    }

    fun getCombinePinEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_COMBINE_PIN_ENABLED, false)
    }

    fun getPrivacyGuardEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_PRIVACY_GUARD_ENABLED, false)
    }

    fun getPassword(): String {
        // "contraseña" es el valor predeterminado de fábrica
        return sharedPreferences.getString(KEY_ML_PASSWORD, "contraseña") ?: "contraseña"
    }

    // --- Métodos de Escritura (Setters) ---

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
}