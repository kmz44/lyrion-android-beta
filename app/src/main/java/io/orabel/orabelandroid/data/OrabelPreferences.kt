/*
 * Copyright (C) 2024 Orabel IA
 * Preferences manager for Orabel IA app
 */

package io.orabel.orabelandroid.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import io.orabel.orabelandroid.ui.theme.ThemeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import java.util.Calendar

@Single
class OrabelPreferences(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "orabel_preferences"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_FIRST_TIME_SETUP = "first_time_setup"
        private const val KEY_RESPONSE_MAX_LENGTH = "response_max_length"
        private const val KEY_LAST_NAVIGATION_INDEX = "last_navigation_index"
        private const val KEY_IS_DARK_THEME = "is_dark_theme"
    private const val KEY_HOTWORD_ENABLED = "hotword_enabled"
    private const val KEY_HOTWORD_START = "hotword_start"
    private const val KEY_HOTWORD_STOP = "hotword_stop"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // ThemeManager para gestión avanzada de temas
    private val themeManager = ThemeManager(context)
    
    // StateFlow para el tema oscuro - usa ThemeManager
    private val _isDarkTheme = MutableStateFlow(calculateDarkTheme(false))
    val isDarkThemeFlow: StateFlow<Boolean> = _isDarkTheme.asStateFlow()
    
    // Listener para cambios en SharedPreferences
    private val sharedPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_IS_DARK_THEME || key == ThemeManager.KEY_THEME_MODE) {
            updateDarkTheme()
        }
    }
    
    init {
        // Registrar el listener para mantenerse sincronizado
        prefs.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
        
        // Verificar periódicamente si cambió la hora (para modo automático)
        startAutoThemeChecker()
    }
    
    /**
     * Calcula si debe usar modo oscuro según la configuración del ThemeManager
     */
    private fun calculateDarkTheme(isSystemInDarkMode: Boolean): Boolean {
        return themeManager.shouldUseDarkTheme(isSystemInDarkMode)
    }
    
    /**
     * Actualiza el tema oscuro según la configuración actual
     */
    fun updateDarkTheme(isSystemInDarkMode: Boolean = false) {
        val newValue = calculateDarkTheme(isSystemInDarkMode)
        if (_isDarkTheme.value != newValue) {
            _isDarkTheme.value = newValue
            android.util.Log.d("OrabelPreferences", "Theme updated to: ${if (newValue) "Dark" else "Light"}")
        }
    }
    
    /**
     * Inicia un verificador periódico para el modo automático por horario
     */
    private fun startAutoThemeChecker() {
        // Verificar cada minuto si cambió la hora (para modo auto)
        android.os.Handler(android.os.Looper.getMainLooper()).post(object : Runnable {
            override fun run() {
                if (themeManager.getThemeMode() == ThemeManager.MODE_AUTO_TIME) {
                    updateDarkTheme()
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 60000) // 1 minuto
            }
        })
    }
    
    /**
     * Guarda el ID del modelo seleccionado
     */
    fun setSelectedModelId(modelId: Long) {
        prefs.edit().putLong(KEY_SELECTED_MODEL_ID, modelId).apply()
    }
    
    /**
     * Obtiene el ID del modelo seleccionado, -1 si no hay ninguno
     */
    fun getSelectedModelId(): Long {
        return prefs.getLong(KEY_SELECTED_MODEL_ID, -1L)
    }
    
    /**
     * Verifica si hay un modelo seleccionado
     */
    fun hasSelectedModel(): Boolean {
        return getSelectedModelId() != -1L
    }
    
    /**
     * Elimina el modelo seleccionado (para cambiar de modelo)
     */
    fun clearSelectedModel() {
        prefs.edit().remove(KEY_SELECTED_MODEL_ID).apply()
    }
    
    /**
     * Marca que el usuario ya completó el setup inicial
     */
    fun setFirstTimeSetupCompleted() {
        prefs.edit().putBoolean(KEY_FIRST_TIME_SETUP, false).apply()
    }
    
    /**
     * Verifica si es la primera vez que el usuario abre la app
     */
    fun isFirstTimeSetup(): Boolean {
        return prefs.getBoolean(KEY_FIRST_TIME_SETUP, true)
    }
    
    /**
     * Establece la longitud máxima de respuesta (0 = sin límite)
     */
    fun setResponseMaxLength(maxLength: Int) {
        prefs.edit().putInt(KEY_RESPONSE_MAX_LENGTH, maxLength).apply()
    }
    
    /**
     * Obtiene la longitud máxima de respuesta (0 = sin límite)
     */
    fun getResponseMaxLength(): Int {
        return prefs.getInt(KEY_RESPONSE_MAX_LENGTH, 0) // 0 = sin límite por defecto
    }
    
    /**
     * Guarda el índice de la navegación donde se quedó el usuario
     */
    fun setLastNavigationIndex(index: Int) {
        prefs.edit().putInt(KEY_LAST_NAVIGATION_INDEX, index).apply()
    }
    
    /**
     * Obtiene el índice de la navegación donde se quedó el usuario
     */
    fun getLastNavigationIndex(): Int {
        return prefs.getInt(KEY_LAST_NAVIGATION_INDEX, 0) // 0 = Inicio por defecto
    }
    
    /**
     * Guarda la preferencia de tema oscuro (modo manual)
     */
    fun setDarkTheme(isDark: Boolean) {
        android.util.Log.d("OrabelPreferences", "setDarkTheme called with: $isDark")
        
        // Cambiar a modo siempre oscuro o siempre claro
        val mode = if (isDark) ThemeManager.MODE_ALWAYS_DARK else ThemeManager.MODE_ALWAYS_LIGHT
        themeManager.setThemeMode(mode)
        
        // Actualizar el StateFlow
        updateDarkTheme()
    }
    
    /**
     * Obtiene la preferencia de tema oscuro
     */
    fun isDarkTheme(): Boolean {
        return _isDarkTheme.value
    }
    
    /**
     * Establece el modo de tema (system, auto_time, always_dark, always_light)
     */
    fun setThemeMode(mode: String) {
        themeManager.setThemeMode(mode)
        updateDarkTheme()
    }
    
    /**
     * Obtiene el modo de tema actual
     */
    fun getThemeMode(): String {
        return themeManager.getThemeMode()
    }
    
    /**
     * Obtiene el ThemeManager
     */
    fun getThemeManager(): ThemeManager {
        return themeManager
    }

    // === IA Live: Hotword preferences ===
    fun setHotwordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HOTWORD_ENABLED, enabled).apply()
    }

    fun isHotwordEnabled(): Boolean {
        return prefs.getBoolean(KEY_HOTWORD_ENABLED, true)
    }

    fun setStartKeyword(keyword: String) {
        prefs.edit().putString(KEY_HOTWORD_START, keyword).apply()
    }

    fun getStartKeyword(): String {
        return prefs.getString(KEY_HOTWORD_START, "kevin") ?: "kevin"
    }

    fun setStopKeyword(keyword: String) {
        prefs.edit().putString(KEY_HOTWORD_STOP, keyword).apply()
    }

    fun getStopKeyword(): String {
        val v = prefs.getString(KEY_HOTWORD_STOP, "finaliza") ?: "finaliza"
        return if (v == "finalisa") "finaliza" else v
    }
}
