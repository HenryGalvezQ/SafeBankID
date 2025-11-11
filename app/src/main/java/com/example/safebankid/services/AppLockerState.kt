package com.example.safebankid.services

/**
 * Objeto Singleton para mantener el estado de la app desbloqueada.
 * Esto permite a LockScreenActivity "avisar" al Service que no
 * vuelva a bloquear la misma app inmediatamente.
 */
object AppLockerState {
    var lastUnlockedApp: String? = null
    var lastUnlockedTime: Long = 0
}