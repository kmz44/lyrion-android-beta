package io.orabel.orabelandroid.utils

import android.content.Context
import android.util.Log
import io.orabel.orabelandroid.data.social.SocialRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.util.Date

/**
 * Gestiona el estado de actividad del usuario en tiempo real.
 * 
 * Dos tipos de estados:
 * 1. is_active (Boolean) - Usuario tiene la app abierta o en background
 * 2. status (String) - Estado específico: "available", "online", "chatting", "offline"
 * 
 * Heartbeat cada 30s mantiene is_active = true
 * Auto-timeout después de 45s marca como offline
 */
class UserActivityManager private constructor(
    private val context: Context
) {
    private val repository by lazy { SocialRepository.getInstance(context) }
    private var heartbeatJob: Job? = null
    private var isInitialized = false
    
    // Motor de tareas persistente (SupervisorJob evita que un fallo cancele todo)
    private var scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _isAppActive = MutableStateFlow(false)
    val isAppActive: StateFlow<Boolean> = _isAppActive
    
    private val _currentStatus = MutableStateFlow("offline")
    val currentStatus: StateFlow<String> = _currentStatus
    
    companion object {
        private const val TAG = "UserActivityManager"
        private const val HEARTBEAT_INTERVAL_MS = 30_000L // 30 segundos
        
        @Volatile
        private var instance: UserActivityManager? = null
        
        fun getInstance(context: Context): UserActivityManager {
            return instance ?: synchronized(this) {
                instance ?: UserActivityManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Inicializar el manager cuando la app arranca
     */
    fun initialize() {
        Log.d(TAG, "🚀 [INITIALIZE] Starting UserActivityManager")
        if (isInitialized && !scope.coroutineContext.job.isActive) {
            Log.d(TAG, "🔄 [INITIALIZE] Scope was inactive, recreating")
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        } else if (isInitialized) {
            Log.d(TAG, "⚠️ [INITIALIZE] Already initialized and active")
            return
        }
        
        isInitialized = true
        onAppStart()
    }
    
    /**
     * Llamar cuando la app pasa a foreground (onStart)
     */
    fun onAppStart() {
        Log.d(TAG, "📱 App started/resumed")
        _isAppActive.value = true
        
        scope.launch {
            try {
                // Marcar como activo y disponible
                // Marcar como activo pero "offline" por defecto (según requerimiento estricto)
                // El usuario solo quiere verse "online" cuando entra al chat
                repository.updateUserActiveStatus(true)
                repository.updateUserStatus("offline")
                _currentStatus.value = "offline"
                
                // Iniciar heartbeat
                startHeartbeat()
                
                Log.d(TAG, "✅ App marked as active")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error on app start: ${e.message}")
            }
        }
    }
    
    /**
     * Llamar cuando la app pasa a background (onStop)
     */
    fun onAppStop() {
        Log.d(TAG, "📴 App stopped/paused")
        _isAppActive.value = false
        
        // Detener heartbeat
        stopHeartbeat()
        
        scope.launch {
            try {
                // Marcar como inactivo y offline
                repository.updateUserActiveStatus(false)
                repository.updateUserStatus("offline")
                _currentStatus.value = "offline"
                
                Log.d(TAG, "✅ App marked as inactive")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error on app stop: ${e.message}")
            }
        }
    }
    
    /**
     * Llamar cuando el usuario entra a la pantalla de inbox/mensajes
     */
    private var statusJob: Job? = null

    /**
     * Helper para programar estado OFFLINE con debounce.
     * Evita que transiciones rápidas marquen al usuario como desconectado incorrectamente.
     */
    private fun scheduleOfflineExclusivity() {
        Log.d(TAG, "⏳ [ACTIVITY] Scheduling OFF-LINE state (debounce)")
        statusJob?.cancel()

        // Asegurar que el scope esté vivo
        if (!scope.coroutineContext.job.isActive) {
            Log.w(TAG, "⚠️ [ACTIVITY] Scope was dead, reviving for offline update")
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        statusJob = scope.launch {
            Log.d(TAG, "⏳ [ACTIVITY] Status debounce started (500ms)")
            // 1. Esperar (CANCELABLE) para dar oportunidad a enterChat de interrumpir
            try {
                delay(500)
            } catch (e: CancellationException) {
                Log.d(TAG, "❌ [ACTIVITY] Status debounce CANCELLED")
                throw e
            }
            
            // 2. Si llegamos aquí, confirmamos el estado Offline (NON-CANCELLABLE para asegurar consistencia)
            withContext(NonCancellable) {
                Log.d(TAG, "🔒 [ACTIVITY] Executing debounced Offline update")
                _currentStatus.value = "offline"
                val result = repository.updateUserStatus("offline")
                result.onSuccess {
                    Log.d(TAG, "✅ [ACTIVITY] Successfully marked as OFFLINE (Debounced)")
                }.onFailure { e ->
                    Log.e(TAG, "❌ [ACTIVITY] Failed to mark as offline: ${e.message}")
                }
            }
        }
    }

    /**
     * Llamar cuando el usuario entra a la pantalla de inbox/mensajes
     */
    fun enterMessaging() {
        Log.d(TAG, "💬 Entering messaging screen")
        // Inbox también se considera "Offline" en la lógica estricta
        scheduleOfflineExclusivity()
    }
    
    /**
     * Llamar cuando el usuario entra a un chat específico
     */
    fun enterChat() {
        Log.d(TAG, "💬 [ACTIVITY] Requesting ENTER CHAT state")
        // 🛑 CANCELAR cualquier intento de poner offline
        statusJob?.cancel()
        
        _currentStatus.value = "chatting"
        
        // Asegurar que el scope esté vivo
        if (!scope.coroutineContext.job.isActive) {
            Log.w(TAG, "⚠️ [ACTIVITY] Scope was dead, reviving for enterChat")
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }

        _currentStatus.value = "chatting"
        
        scope.launch {
            Log.d(TAG, "🚀 [ACTIVITY] Executing enterChat background task")
            val result = repository.updateUserStatus("chatting")
            result.onSuccess {
                Log.d(TAG, "✅ [ACTIVITY] Successfully marked as chatting in DB")
            }.onFailure { e ->
                Log.e(TAG, "❌ [ACTIVITY] Failed to mark as chatting: ${e.message}")
            }
        }
    }
    
    /**
     * Llamar cuando el usuario sale de un chat específico
     */
    fun exitChat() {
        Log.d(TAG, "💬 Exiting specific chat")
        scheduleOfflineExclusivity()
    }
    
    /**
     * Llamar cuando el usuario sale de la pantalla de mensajes
     */
    fun exitMessaging() {
        Log.d(TAG, "💬 Exiting messaging screen")
        scheduleOfflineExclusivity()
    }
    
    /**
     * Iniciar heartbeat que mantiene is_active = true
     */
    private fun startHeartbeat() {
        stopHeartbeat() // Detener cualquier heartbeat anterior
        
        Log.d(TAG, "💓 Starting heartbeat (every 30s)")
        
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    // Enviar heartbeat
                    repository.sendHeartbeat()
                    Log.d(TAG, "💓 Heartbeat sent")
                    
                    // Esperar 30 segundos
                    delay(HEARTBEAT_INTERVAL_MS)
                } catch (e: CancellationException) {
                    Log.d(TAG, "💓 Heartbeat cancelled")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Heartbeat error: ${e.message}")
                    delay(HEARTBEAT_INTERVAL_MS) // Intentar de nuevo después de 30s
                }
            }
        }
    }
    
    /**
     * Detener heartbeat
     */
    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "💓 Heartbeat stopped")
    }
    
    /**
     * Limpiar recursos
     */
    fun cleanup() {
        Log.d(TAG, "🧹 Cleaning up UserActivityManager")
        stopHeartbeat()
        scope.cancel()
        isInitialized = false
    }
}
