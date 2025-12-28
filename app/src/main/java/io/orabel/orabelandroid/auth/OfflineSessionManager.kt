/*
 * Copyright (C) 2024 Lyrion
 * Manejo de sesión offline - Permite usar la app sin internet
 * después de haber iniciado sesión una vez
 */

package io.orabel.orabelandroid.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Gestor de sesión offline que permite persistencia local
 * de datos de autenticación para uso sin internet
 */
class OfflineSessionManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "offline_session_prefs"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_LAST_LOGIN_TIME = "last_login_time"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val TAG = "OfflineSessionManager"
    }
    
    private val prefs: SharedPreferences by lazy {
        // Usar SharedPreferences normal (la data no es tan sensible, solo email/nombre)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Guarda la sesión localmente para persistencia offline
     */
    fun saveOfflineSession(
        email: String,
        userId: String,
        displayName: String? = null,
        avatarUrl: String? = null
    ) {
        prefs.edit().apply {
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_ID, userId)
            putString(KEY_DISPLAY_NAME, displayName)
            putString(KEY_AVATAR_URL, avatarUrl)
            putLong(KEY_LAST_LOGIN_TIME, System.currentTimeMillis())
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
        Log.d(TAG, "✅ Sesión offline guardada para: $email")
    }
    
    /**
     * Verifica si hay una sesión offline válida guardada
     */
    fun hasOfflineSession(): Boolean {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val email = prefs.getString(KEY_USER_EMAIL, null)
        
        val hasSession = isLoggedIn && !email.isNullOrBlank()
        Log.d(TAG, "🔍 ¿Tiene sesión offline? $hasSession (email: $email)")
        return hasSession
    }
    
    /**
     * Obtiene el email del usuario guardado localmente
     */
    fun getOfflineEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }
    
    /**
     * Obtiene el userId guardado localmente
     */
    fun getOfflineUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }
    
    /**
     * Obtiene el nombre de display guardado localmente
     */
    fun getOfflineDisplayName(): String? {
        return prefs.getString(KEY_DISPLAY_NAME, null)
    }
    
    /**
     * Obtiene la URL del avatar guardada localmente
     */
    fun getOfflineAvatarUrl(): String? {
        return prefs.getString(KEY_AVATAR_URL, null)
    }
    
    /**
     * Obtiene el timestamp del último login
     */
    fun getLastLoginTime(): Long {
        return prefs.getLong(KEY_LAST_LOGIN_TIME, 0L)
    }
    
    /**
     * Elimina la sesión offline (logout manual)
     */
    fun clearOfflineSession() {
        prefs.edit().clear().apply()
        Log.d(TAG, "🗑️ Sesión offline eliminada")
    }
    
    /**
     * Verifica si la sesión offline es relativamente reciente
     * (Para logging, no afecta la persistencia - queremos persistencia infinita)
     */
    fun isRecentSession(): Boolean {
        val lastLogin = getLastLoginTime()
        if (lastLogin == 0L) return false
        
        val daysSinceLogin = (System.currentTimeMillis() - lastLogin) / (1000 * 60 * 60 * 24)
        Log.d(TAG, "📅 Días desde último login: $daysSinceLogin")
        return true // Siempre retorna true - sesión persiste indefinidamente
    }
    
    /**
     * Datos de sesión offline
     */
    data class OfflineSessionData(
        val email: String,
        val userId: String,
        val displayName: String?,
        val avatarUrl: String?,
        val lastLoginTime: Long
    )
    
    /**
     * Obtiene todos los datos de sesión offline si existen
     */
    fun getOfflineSessionData(): OfflineSessionData? {
        if (!hasOfflineSession()) return null
        
        val email = getOfflineEmail() ?: return null
        val userId = getOfflineUserId() ?: return null
        
        return OfflineSessionData(
            email = email,
            userId = userId,
            displayName = getOfflineDisplayName(),
            avatarUrl = getOfflineAvatarUrl(),
            lastLoginTime = getLastLoginTime()
        )
    }
}
