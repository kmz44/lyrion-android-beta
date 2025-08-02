package io.orabel.orabelandroid

import android.app.Application
import android.os.StrictMode
import android.util.Log
import io.orabel.orabelandroid.data.ObjectBoxStore
import io.orabel.orabelandroid.stt.SttRepository
import io.orabel.orabelandroid.tts.TtsRepository
import io.orabel.orabelandroid.utils.MemoryLeakDetector
import io.orabel.orabelandroid.utils.PerformanceUtils
import io.orabel.orabelandroid.utils.GlobalExceptionHandler
import io.orabel.orabelandroid.utils.AsyncFileOperations
import io.orabel.orabelandroid.utils.ReceiverManager
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.ksp.generated.module

class OrabelApplication : Application() {
    
    companion object {
        private const val TAG = "OrabelApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Application starting")
        
        // Install global exception handler first
        GlobalExceptionHandler.install()
        
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
            startKoin {
                androidContext(this@OrabelApplication)
                modules(KoinAppModule().module)
            }
            ObjectBoxStore.init(this)
            Log.d(TAG, "Application initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during application initialization", e)
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
            super.onTerminate()
        }
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
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
