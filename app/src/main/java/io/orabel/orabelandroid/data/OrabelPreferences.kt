/*
 * Copyright (C) 2024 Orabel IA
 * Preferences manager for Orabel IA app
 */

package io.orabel.orabelandroid.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

@Single
class OrabelPreferences(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "orabel_preferences"
        private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        private const val KEY_FIRST_TIME_SETUP = "first_time_setup"
        private const val KEY_RESPONSE_MAX_LENGTH = "response_max_length"
        private const val KEY_LAST_NAVIGATION_INDEX = "last_navigation_index"
        private const val KEY_IS_DARK_THEME = "is_dark_theme"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // StateFlow para el tema oscuro - inicializar con el valor actual de SharedPreferences
    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean(KEY_IS_DARK_THEME, false))
    val isDarkThemeFlow: StateFlow<Boolean> = _isDarkTheme.asStateFlow()
    
    // Listener para cambios en SharedPreferences
    private val sharedPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_IS_DARK_THEME) {
            _isDarkTheme.value = prefs.getBoolean(KEY_IS_DARK_THEME, false)
        }
    }
    
    init {
        // Registrar el listener para mantenerse sincronizado
        prefs.registerOnSharedPreferenceChangeListener(sharedPreferencesListener)
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
     * Guarda la preferencia de tema oscuro
     */
    fun setDarkTheme(isDark: Boolean) {
        // Log para debug - eliminar en producción
        android.util.Log.d("OrabelPreferences", "setDarkTheme called with: $isDark")
        
        // Actualizar primero el StateFlow, luego SharedPreferences
        _isDarkTheme.value = isDark
        prefs.edit().putBoolean(KEY_IS_DARK_THEME, isDark).apply()
        
        // Verificar que se guardó correctamente
        val saved = prefs.getBoolean(KEY_IS_DARK_THEME, false)
        android.util.Log.d("OrabelPreferences", "Theme saved as: $saved, StateFlow value: ${_isDarkTheme.value}")
    }
    
    /**
     * Obtiene la preferencia de tema oscuro
     */
    fun isDarkTheme(): Boolean {
        return _isDarkTheme.value
    }
}
