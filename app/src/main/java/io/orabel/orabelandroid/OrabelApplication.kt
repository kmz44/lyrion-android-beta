package io.orabel.orabelandroid

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.orabel.orabelandroid.data.ObjectBoxStore
import io.orabel.orabelandroid.data.social.SocialRepository
import io.orabel.orabelandroid.stt.SttRepository
import io.orabel.orabelandroid.tts.TtsRepository
import io.orabel.orabelandroid.utils.MemoryLeakDetector
import io.orabel.orabelandroid.utils.PerformanceUtils
import io.orabel.orabelandroid.utils.GlobalExceptionHandler
import io.orabel.orabelandroid.utils.AsyncFileOperations
import io.orabel.orabelandroid.utils.ReceiverManager
import io.orabel.orabelandroid.auth.SupabaseClient
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.ksp.generated.module
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class OrabelApplication : Application(), DefaultLifecycleObserver {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val foregroundListenerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var inboxListenerJob: Job? = null
    
    companion object {
        private const val TAG = "OrabelApplication"
    }
    
    override fun onCreate() {
        super<Application>.onCreate()
        
        Log.d(TAG, "Application starting")
        
        // Install global exception handler first
        GlobalExceptionHandler.install()
        
        // Inicializar Supabase con persistencia de sesión
        try {
            SupabaseClient.init(this)
            Log.d(TAG, "Supabase initialized with session persistence")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Supabase", e)
        }
        
        // Optimize Compose Snapshot system
        // Note: Snapshot optimizations are handled automatically in newer versions
        
        // Enable strict mode for debug builds to catch performance issues
        if (BuildConfig.DEBUG) {
            try {
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder()
                        .detectDiskReads()
                        .detectDiskWrites()
                        .detectNetwork()
                        // Solo log, no crash - permite operaciones I/O esenciales
                        .penaltyLog()
                        // Permitir algunas operaciones I/O necesarias durante el startup
                        .permitDiskReads()
                        .permitDiskWrites()
                        .build()
                )
                StrictMode.setVmPolicy(
                    StrictMode.VmPolicy.Builder()
                        .detectLeakedSqlLiteObjects()
                        .detectLeakedClosableObjects()
                        .detectActivityLeaks()
                        // Android 15: Detectar intents inseguros
                        .detectUnsafeIntentLaunch()
                        .penaltyLog()
                        .build()
                )
                
                // Install memory leak detector for debug builds (disabled temporarily)
                // MemoryLeakDetector.install(this)
                
                // Log initial memory usage
                PerformanceUtils.logMemoryUsage(this, "AppStartup")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up debug tools", e)
            }
        }
        
        try {
            // CRÍTICO: Inicializar ObjectBox ANTES de Koin
            // porque provideBoxStore() necesita ObjectBoxStore.store
            ObjectBoxStore.init(this)
            
            startKoin {
                androidContext(this@OrabelApplication)
                modules(KoinAppModule().module)
            }
            
            // Register lifecycle observer to handle app foreground/background
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
            Log.d(TAG, "🔄 Lifecycle observer registered for app foreground/background detection")
            
            Log.d(TAG, "Application initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during application initialization", e)
        }
    }
    
    // Called when app comes to FOREGROUND (any activity visible)
    override fun onStart(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStart(owner)
        Log.d(TAG, "🟢 App moved to FOREGROUND")
        applicationScope.launch {
            try {
                val socialRepository = SocialRepository.getInstance(this@OrabelApplication)
                socialRepository.updateUserStatus("online")
                Log.d(TAG, "✅ User status updated to: online")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating status to online: ${e.message}")
            }
        }

        // Realtime inbox listener ONLY while app is in foreground.
        // Importante: al arrancar la app puede NO existir sesión todavía; esperamos y reintentamos.
        if (inboxListenerJob?.isActive == true) return
        inboxListenerJob = foregroundListenerScope.launch {
            val socialRepository = SocialRepository.getInstance(this@OrabelApplication)

            while (true) {
                try {
                    val currentUserId = socialRepository.getCurrentUserId()
                    if (currentUserId.isNullOrBlank()) {
                        Log.d(TAG, "🔕 Foreground inbox listener: sin sesión aún (userId=null), reintentando...")
                        delay(1000)
                        continue
                    }

                    Log.d(TAG, "🔔 Foreground inbox listener START (userId=$currentUserId)")

                    socialRepository.subscribeToAllMessages().collect { message ->
                        Log.d(TAG, "📨 Foreground inbox listener: mensaje nuevo id=${message.id}")
                        // Mostrar notificación incluso en foreground (según requerimiento).
                        socialRepository.showIncomingMessageNotification(message)
                    }

                    // Si el flow termina sin excepción, reintentar (p.ej., reconexión realtime)
                    Log.w(TAG, "⚠️ Foreground inbox listener flow terminó; reintentando...")
                    delay(1500)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in foreground inbox listener: ${e.message}", e)
                    delay(2000)
                }
            }
        }
    }
    
    // Called when app goes to BACKGROUND (no activities visible)
    override fun onStop(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStop(owner)
        Log.d(TAG, "🔴 App moved to BACKGROUND")

        // Detener listener para evitar cualquier segundo plano.
        try {
            inboxListenerJob?.cancel()
        } catch (_: Exception) {
        }
        inboxListenerJob = null
        applicationScope.launch {
            try {
                val socialRepository = SocialRepository.getInstance(this@OrabelApplication)
                socialRepository.updateUserStatus("offline")
                Log.d(TAG, "✅ User status updated to: offline")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error updating status to offline: ${e.message}")
            }
        }
    }
    
    override fun onTerminate() {
        Log.d(TAG, "Application terminating")
        try {
            // Cleanup repositories before app terminates
            cleanupRepositories()
            stopKoin()
        } catch (e: Exception) {
            Log.e(TAG, "Error during application termination", e)
        } finally {
            super<Application>.onTerminate()
        }
    }
    
    override fun onLowMemory() {
        super<Application>.onLowMemory()
        Log.w(TAG, "Low memory warning - cleaning up resources")
        try {
            // Force garbage collection
            System.gc()
            
            // Log memory usage
            if (BuildConfig.DEBUG) {
                PerformanceUtils.logMemoryUsage(this, "LowMemory")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during low memory cleanup", e)
        }
    }
    
    private fun cleanupRepositories() {
        try {
            // Cleanup async file operations
            AsyncFileOperations.cleanup()
            
            // Cleanup receivers
            ReceiverManager.cleanup()
            
            // Inject and cleanup repositories
            val sttRepository: SttRepository by inject()
            val ttsRepository: TtsRepository by inject()
            
            sttRepository.shutdown()
            ttsRepository.shutdown()
            
            Log.d(TAG, "Repositories cleaned up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up repositories", e)
        }
    }
}
