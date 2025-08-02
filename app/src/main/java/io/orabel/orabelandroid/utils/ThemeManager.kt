/*
 * Copyright (C) 2024 Lyrion
 * Theme manager for consistent dark/light theme handling
 */

package io.orabel.orabelandroid.utils

import androidx.compose.runtime.*
import io.orabel.orabelandroid.data.OrabelPreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * Composable helper para obtener el estado del tema de manera reactiva
 */
@Composable
fun rememberThemeState(preferences: OrabelPreferences): Boolean {
    // Primer enfoque: usar el StateFlow
    val themeFromFlow by preferences.isDarkThemeFlow.collectAsState()
    
    // Segundo enfoque: estado local que se sincroniza manualmente
    var localTheme by remember { mutableStateOf(preferences.isDarkTheme()) }
    
    // Efecto que sincroniza ambos estados
    LaunchedEffect(themeFromFlow) {
        if (localTheme != themeFromFlow) {
            localTheme = themeFromFlow
        }
    }
    
    // Usar el StateFlow como fuente de verdad
    return themeFromFlow
}

/**
 * Helper para cambiar el tema de manera consistente
 */
fun changeTheme(preferences: OrabelPreferences, isDark: Boolean) {
    android.util.Log.d("ThemeManager", "Changing theme to: ${if (isDark) "Dark" else "Light"}")
    preferences.setDarkTheme(isDark)
}
