/*
 * Copyright (C) 2024 Orabel IA
 * Modern theme for teens with clean, single source of truth
 */

package io.orabel.orabelandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Orabel Light Theme
private val OrabelLightColorScheme = lightColorScheme(
    primary = OrabelPrimary,
    onPrimary = OrabelTextOnPrimary,
    primaryContainer = OrabelPrimary.copy(alpha = 0.1f),
    onPrimaryContainer = OrabelPrimaryDark,
    
    secondary = OrabelSecondary,
    onSecondary = OrabelTextOnPrimary,
    secondaryContainer = OrabelSecondary.copy(alpha = 0.1f),
    onSecondaryContainer = OrabelSecondaryDark,
    
    tertiary = OrabelAccent,
    onTertiary = OrabelTextOnPrimary,
    tertiaryContainer = OrabelAccent.copy(alpha = 0.1f),
    onTertiaryContainer = OrabelAccentDark,
    
    error = OrabelError,
    onError = OrabelTextOnPrimary,
    errorContainer = OrabelError.copy(alpha = 0.1f),
    onErrorContainer = OrabelError,
    
    background = OrabelBackgroundLight,
    onBackground = OrabelTextPrimary,
    
    surface = OrabelSurfaceLight,
    onSurface = OrabelTextPrimary,
    surfaceVariant = OrabelSurfaceVariantLight,
    onSurfaceVariant = OrabelTextSecondary,
    
    outline = OrabelTextTertiary,
    outlineVariant = OrabelTextTertiary.copy(alpha = 0.3f)
)

// Orabel Dark Theme
private val OrabelDarkColorScheme = darkColorScheme(
    primary = OrabelPrimary,
    onPrimary = OrabelTextOnPrimary,
    primaryContainer = OrabelPrimaryDark,
    onPrimaryContainer = OrabelPrimary.copy(alpha = 0.8f),
    
    secondary = OrabelSecondary,
    onSecondary = OrabelTextOnPrimary,
    secondaryContainer = OrabelSecondaryDark,
    onSecondaryContainer = OrabelSecondary.copy(alpha = 0.8f),
    
    tertiary = OrabelAccent,
    onTertiary = OrabelTextOnPrimary,
    tertiaryContainer = OrabelAccentDark,
    onTertiaryContainer = OrabelAccent.copy(alpha = 0.8f),
    
    error = OrabelError,
    onError = OrabelTextOnPrimary,
    errorContainer = OrabelError.copy(alpha = 0.2f),
    onErrorContainer = OrabelError,
    
    background = OrabelBackgroundDark,
    onBackground = OrabelTextOnDark,
    
    surface = OrabelSurfaceDark,
    onSurface = OrabelTextOnDark,
    surfaceVariant = OrabelSurfaceVariantDark,
    onSurfaceVariant = OrabelTextSecondaryDark,
    
    outline = OrabelTextSecondaryDark,
    outlineVariant = OrabelTextTertiaryDark
)

/**
 * Main Orabel theme function - Single source of truth
 */
@Composable
fun OrabelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        OrabelDarkColorScheme
    } else {
        OrabelLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OrabelTypography,
        content = content
    )
}

/**
 * Legacy theme function for backward compatibility
 */
@Composable
fun OrabelAndroidTheme(content: @Composable () -> Unit) {
    OrabelTheme(content = content)
}
