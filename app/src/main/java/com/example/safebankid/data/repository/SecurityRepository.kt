package com.example.safebankid.data.repository

import com.example.safebankid.data.local.SecurityPreferences
import com.example.safebankid.ui.dashboard.SecurityUiState

/**
 * Repositorio que centraliza el acceso a las preferencias de seguridad.
 * El ViewModel hablará con esta clase, no directamente con SharedPreferences.
 */
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
}