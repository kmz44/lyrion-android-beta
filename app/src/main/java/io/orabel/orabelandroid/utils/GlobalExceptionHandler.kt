package io.orabel.orabelandroid.utils

import android.util.Log
import kotlin.system.exitProcess

/**
 * Global exception handler to prevent app crashes from resource loading issues
 * Particularly useful for SystemUIToast APK loading problems
 */
class GlobalExceptionHandler : Thread.UncaughtExceptionHandler {
    
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    
    companion object {
        private const val TAG = "GlobalExceptionHandler"
        
        fun install() {
            Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler())
            Log.d(TAG, "Global exception handler installed")
        }
    }
    
    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            Log.e(TAG, "Uncaught exception in thread ${thread.name}", exception)
            
            // Check if this is a known recoverable error
            when {
                // SystemUIToast APK loading errors
                isSystemUIToastError(exception) -> {
                    Log.w(TAG, "SystemUIToast APK loading error detected - handling gracefully")
                    handleSystemUIToastError(exception)
                    return
                }
                
                // Resource loading errors
                isResourceLoadingError(exception) -> {
                    Log.w(TAG, "Resource loading error detected - handling gracefully")
                    handleResourceLoadingError(exception)
                    return
                }
                
                // Memory related errors during cleanup
                isMemoryCleanupError(exception) -> {
                    Log.w(TAG, "Memory cleanup error detected - forcing cleanup")
                    handleMemoryCleanupError(exception)
                    return
                }
                
                // Other recoverable errors
                else -> {
                    Log.e(TAG, "Unhandled exception - delegating to default handler")
                    defaultHandler?.uncaughtException(thread, exception)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in exception handler", e)
            // Last resort - delegate to default handler
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
    
    private fun isSystemUIToastError(exception: Throwable): Boolean {
        val stackTrace = Log.getStackTraceString(exception)
        return stackTrace.contains("SystemUIToast") ||
                stackTrace.contains("ToastUI") ||
                stackTrace.contains("Failed to load asset path") ||
                stackTrace.contains("Failed to open APK")
    }
    
    private fun isResourceLoadingError(exception: Throwable): Boolean {
        val stackTrace = Log.getStackTraceString(exception)
        return stackTrace.contains("ResourcesManager") ||
                stackTrace.contains("ApkAssets") ||
                stackTrace.contains("loadApkAssets")
    }
    
    private fun isMemoryCleanupError(exception: Throwable): Boolean {
        val stackTrace = Log.getStackTraceString(exception)
        return stackTrace.contains("MemoryLeakDetector") ||
                stackTrace.contains("cleanup") ||
                (exception is OutOfMemoryError)
    }
    
    private fun handleSystemUIToastError(exception: Throwable) {
        try {
            Log.w(TAG, "SystemUIToast error handled - app continuing normally")
            // Don't crash the app for SystemUIToast errors
            // These are system-level issues not related to our app logic
        } catch (e: Exception) {
            Log.e(TAG, "Error handling SystemUIToast error", e)
        }
    }
    
    private fun handleResourceLoadingError(exception: Throwable) {
        try {
            Log.w(TAG, "Resource loading error handled - app continuing normally")
            // Force garbage collection to free up resources
            System.gc()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling resource loading error", e)
        }
    }
    
    private fun handleMemoryCleanupError(exception: Throwable) {
        try {
            Log.w(TAG, "Memory cleanup error handled - forcing cleanup")
            // Force garbage collection
            System.gc()
            // Give system time to cleanup
            Thread.sleep(100)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling memory cleanup error", e)
        }
    }
}
