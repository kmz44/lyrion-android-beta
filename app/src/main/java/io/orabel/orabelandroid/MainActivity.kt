/*
 * Copyright (C) 2024 Orabel IA
 * Renovated for teens with modern UI/UX
 */

package io.orabel.orabelandroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.llm.ModelsRepository
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.screens.welcome.WelcomeActivity
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.screens.model_setup.ModernModelSetupActivity
import io.orabel.orabelandroid.ui.screens.settings.SettingsActivity
import io.orabel.orabelandroid.ui.screens.first_setup.FirstSetupActivity
import io.orabel.orabelandroid.utils.RenderingOptimizer
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val modelsRepository by inject<ModelsRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Optimizar rendering para mejor rendimiento
        RenderingOptimizer.optimizeRendering(this)
        RenderingOptimizer.optimizeCompose(this)
        RenderingOptimizer.optimizeOpenGL(this)

        // Verificar si es la primera vez que se ejecuta la app
        if (orabelPreferences.isFirstTimeSetup()) {
            val intent = Intent(this, FirstSetupActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // Verificar si ya hay un modelo seleccionado y disponible
        val selectedModelId = orabelPreferences.getSelectedModelId()
        val hasValidModel = if (selectedModelId != -1L) {
            modelsRepository.getModelFromId(selectedModelId) != null
        } else {
            false
        }

        // Recordar la navegación anterior
        val lastNavigationIndex = orabelPreferences.getLastNavigationIndex()
        
        // Si no hay modelo válido, ir a Welcome para configurar
        if (!hasValidModel) {
            if (selectedModelId != -1L) {
                // Limpiar modelo inválido
                orabelPreferences.clearSelectedModel()
            }
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        // Si hay modelo válido, ir a la última pantalla visitada
        val intent = when (lastNavigationIndex) {
            0 -> Intent(this, ModernMainActivity::class.java)  // Home
            1 -> Intent(this, ModernModelSetupActivity::class.java)  // Búsqueda/Setup
            2 -> Intent(this, ChatActivity::class.java)  // Chat
            3 -> Intent(this, WelcomeActivity::class.java)  // Perfil
            else -> Intent(this, ModernMainActivity::class.java)  // Default: Home
        }
        
        startActivity(intent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos de rendering
        RenderingOptimizer.cleanup()
    }
}
