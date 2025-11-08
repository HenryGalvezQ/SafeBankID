package com.example.safebankid.ui.fallback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PasswordUiState { IDLE, LOADING, SUCCESS, ERROR }

class FallbackPasswordViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordUiState.IDLE)
    val uiState = _uiState.asStateFlow()

    // El Backend conectará esto a EncryptedSharedPreferences
    private val correctPassword = "contraseña"

    fun validatePassword(password: String) {
        viewModelScope.launch {
            _uiState.value = PasswordUiState.LOADING
            delay(500)
            if (password == correctPassword) {
                _uiState.value = PasswordUiState.SUCCESS
            } else {
                _uiState.value = PasswordUiState.ERROR
                delay(1000)
                _uiState.value = PasswordUiState.IDLE
            }
        }
    }
}