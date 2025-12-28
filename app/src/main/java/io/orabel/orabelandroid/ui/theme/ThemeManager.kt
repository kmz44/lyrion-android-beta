/*
 * Copyright (C) 2024 Lyrion
 * Theme Manager - Sistema de gestión de modo oscuro
 */

package io.orabel.orabelandroid.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE)
    
    companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val MODE_SYSTEM = "system"
        const val MODE_AUTO_TIME = "auto_time"
        const val MODE_ALWAYS_DARK = "always_dark"
        const val MODE_ALWAYS_LIGHT = "always_light"
        
        const val DARK_START_HOUR = 20 // 8 PM
        const val DARK_END_HOUR = 6    // 6 AM
    }
    
    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME_MODE, MODE_AUTO_TIME) ?: MODE_AUTO_TIME
    }
    
    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }
    
    /**
     * Determina si debe usar modo oscuro basado en la configuración actual
     */
    fun shouldUseDarkTheme(isSystemInDarkMode: Boolean): Boolean {
        return when (getThemeMode()) {
            MODE_SYSTEM -> isSystemInDarkMode
            MODE_AUTO_TIME -> isNightTime()
            MODE_ALWAYS_DARK -> true
            MODE_ALWAYS_LIGHT -> false
            else -> isNightTime() // Default: auto por tiempo
        }
    }
    
    /**
     * Verifica si es hora nocturna (8 PM - 6 AM)
     */
    private fun isNightTime(): Boolean {
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return currentHour >= DARK_START_HOUR || currentHour < DARK_END_HOUR
    }
}
