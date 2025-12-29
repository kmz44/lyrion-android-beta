/*
 * Copyright (C) 2024 Orabel IA
 * Renovated for teens with modern UI/UX
 */

package io.orabel.orabelandroid

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.orabel.orabelandroid.auth.LoginActivity
import io.orabel.orabelandroid.auth.SupabaseClient
import io.orabel.orabelandroid.auth.OfflineSessionManager
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.llm.ModelsRepository
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.screens.welcome.WelcomeActivity
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.screens.model_setup.ModernModelSetupActivity
import io.orabel.orabelandroid.ui.screens.settings.SettingsActivity
import io.orabel.orabelandroid.ui.screens.first_setup.FirstSetupActivity
import io.orabel.orabelandroid.utils.RenderingOptimizer
import io.orabel.orabelandroid.utils.UserActivityManager
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val modelsRepository by inject<ModelsRepository>()
    private val offlineSessionManager by lazy { OfflineSessionManager(this) }
    private val userActivityManager by lazy { UserActivityManager.getInstance(this) }

    private val notificationPrefs: SharedPreferences by lazy {
        getSharedPreferences("notifications", MODE_PRIVATE)
    }

    private val requestNotificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("MainActivity", "POST_NOTIFICATIONS granted=$granted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        maybeRequestPostNotificationsOnce()
        
        // Optimizar rendering para mejor rendimiento
        RenderingOptimizer.optimizeRendering(this)
        RenderingOptimizer.optimizeCompose(this)
        RenderingOptimizer.optimizeOpenGL(this)
        
        // Inicializar UserActivityManager para tracking de estado en tiempo real
        userActivityManager.initialize()

        // 🔥 NUEVA LÓGICA: Verificar autenticación con soporte OFFLINE
        lifecycleScope.launch {
            try {
                // Intentar obtener sesión de Supabase (requiere internet)
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                
                if (session != null) {
                    Log.d("MainActivity", "✅ Sesión online activa: ${session.user?.email}")
                    // Usuario autenticado online, continuar
                    proceedWithNormalFlow()
                } else {
                    // No hay sesión online, verificar sesión OFFLINE
                    if (offlineSessionManager.hasOfflineSession()) {
                        val offlineEmail = offlineSessionManager.getOfflineEmail()
                        Log.d("MainActivity", "📴 Sin internet, pero sesión offline válida: $offlineEmail")
                        Log.d("MainActivity", "✅ Permitiendo acceso offline")
                        // Usuario ya inició sesión antes, permitir acceso offline
                        proceedWithNormalFlow()
                    } else {
                        Log.d("MainActivity", "❌ No hay sesión online ni offline, redirigir a login")
                        // Nunca ha iniciado sesión, redirigir a login
                        val intent = Intent(this@MainActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error verificando sesión online: ${e.message}")
                // Error al verificar sesión (probablemente sin internet)
                // Verificar si hay sesión offline como fallback
                if (offlineSessionManager.hasOfflineSession()) {
                    val offlineEmail = offlineSessionManager.getOfflineEmail()
                    Log.d("MainActivity", "📴 Error online, usando sesión offline: $offlineEmail")
                    // Usuario ya inició sesión antes, permitir acceso offline
                    proceedWithNormalFlow()
                } else {
                    Log.d("MainActivity", "❌ Error y no hay sesión offline, redirigir a login")
                    // No hay sesión offline, ir a login
                    val intent = Intent(this@MainActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun maybeRequestPostNotificationsOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val alreadyAsked = notificationPrefs.getBoolean("asked_post_notifications", false)
        if (alreadyAsked) return

        notificationPrefs.edit().putBoolean("asked_post_notifications", true).apply()

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        requestNotificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    
    /**
     * Continúa con el flujo normal de la aplicación después de verificar autenticación
     */
    private fun proceedWithNormalFlow() {

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
        
        // Si no hay modelo válido, verificar si el usuario estaba en el perfil
        if (!hasValidModel) {
            if (selectedModelId != -1L) {
                // Limpiar modelo inválido
                orabelPreferences.clearSelectedModel()
            }
            
            // Si el usuario estaba en el perfil (índice 4), mantenerlo ahí
            if (lastNavigationIndex == 4) {
                val intent = Intent(this, io.orabel.orabelandroid.ui.screens.profile.ProfileActivity::class.java)
                startActivity(intent)
                finish()
                return
            }
            
            // Para otros casos, ir a ModernMainActivity para configurar
            val intent = Intent(this, io.orabel.orabelandroid.ui.screens.main.ModernMainActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        // Si hay modelo válido, ir a la última pantalla visitada
        val intent = when (lastNavigationIndex) {
            0 -> Intent(this, ChatActivity::class.java)  // Home (Chat principal)
            1 -> Intent(this, io.orabel.orabelandroid.ui.screens.search.SearchActivity::class.java)  // Búsqueda
            2 -> Intent(this, ChatActivity::class.java)  // Chat
            3 -> Intent(this, io.orabel.orabelandroid.ui.screens.calendar.CalendarActivity::class.java)  // Calendar
            4 -> Intent(this, io.orabel.orabelandroid.ui.screens.profile.ProfileActivity::class.java)  // Perfil
            else -> Intent(this, ChatActivity::class.java)  // Default: Chat
        }
        
        startActivity(intent)
        finish()
    }
    
    override fun onStart() {
        super.onStart()
        // Notificar que la app está en foreground
        userActivityManager.onAppStart()
    }
    
    override fun onStop() {
        super.onStop()
        // Notificar que la app está en background
        userActivityManager.onAppStop()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos de rendering
        RenderingOptimizer.cleanup()
        // Limpiar UserActivityManager
        userActivityManager.cleanup()
    }
}
