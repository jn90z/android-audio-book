package com.example.app.auth

/**
 * Keep application code behind this interface so storage can be replaced
 * without changing screens or API code.
 *
 * Production implementations should protect bearer credentials using
 * Android Keystore-backed cryptography appropriate for the target SDK.
 */
interface SessionStore {
    fun loadToken(): String?
    fun saveToken(token: String)
    fun clear()
}
