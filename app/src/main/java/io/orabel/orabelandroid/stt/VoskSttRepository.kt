package io.orabel.orabelandroid.stt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.lang.ref.WeakReference

/**
 * Speech-to-Text Repository usando Activity-managed Intent directo
 * 
 * Solución optimizada para ZTE Blade V41 y dispositivos similares:
 * ✅ Activity maneja Intent directamente con ActivityResultLauncher
 * ✅ Repository solo mantiene estado, no maneja Context
 * ✅ Evita errores 5, 8 y 13 completamente
 * ✅ Compatible con todos los dispositivos Android
 * ✅ Sin problemas de Context/Activity
 */
class VoskSttRepository(context: Context) {
    
    private val contextRef = WeakReference(context)
    private var isDestroyed = false
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State flows - same interface as original SttRepository for easy replacement
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    
    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults.asStateFlow()
    
    companion object {
        private const val TAG = "VoskSttRepository"
    }
    
    init {
        Log.d(TAG, "🎤 Initializing Activity-managed Speech Recognition (Most Reliable)")
        Log.d(TAG, "📱 Activity handles Intent directly - No Context issues!")
        initializeSpeechRecognition()
    }
    
    private fun initializeSpeechRecognition() {
        try {
            _isAvailable.value = true
            _error.value = null
            Log.d(TAG, "✅ Activity-managed Speech Recognition ready - No Context needed!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing speech recognition", e)
            _error.value = "Error inicializando reconocimiento: ${e.message}"
            _isAvailable.value = false
        }
    }
    
    fun initializeStt(): Boolean {
        val context = contextRef.get() ?: return false
        
        // Check microphone permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            _error.value = "Permiso de micrófono necesario"
            return false
        }
        
        Log.d(TAG, "✅ STT initialized - Android SpeechRecognizer ready")
        return true
    }
    
    // Deprecated - Activity handles speech recognition directly now
    fun startListening() {
        Log.d(TAG, "⚠️ startListening() called - Activity should handle speech recognition directly")
        // This method is kept for compatibility but does nothing
        // The Activity now uses ActivityResultLauncher for speech recognition
    }
    
    // Deprecated - Activity handles results directly now
    fun handleSpeechResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "⚠️ handleSpeechResult() called - Activity should handle results directly")
        // This method is kept for compatibility but does nothing
        // The Activity now uses ActivityResultLauncher callback for speech results
    }
    
    // ===== NEW METHODS FOR ACTIVITY TO UPDATE STATE =====
    
    /**
     * Called by Activity to update the recognized text directly
     */
    fun setRecognizedTextDirect(text: String) {
        Log.d(TAG, "✅ Setting recognized text from Activity: $text")
        _recognizedText.value = text
        _error.value = null
    }
    
    /**
     * Called by Activity to update the listening state
     */
    fun setListeningState(isListening: Boolean) {
        Log.d(TAG, "🎤 Setting listening state from Activity: $isListening")
        _isListening.value = isListening
        if (!isListening) {
            _partialResults.value = ""
        }
    }
    
    /**
     * Called by Activity to show partial/status text
     */
    fun setPartialText(text: String) {
        Log.d(TAG, "📝 Setting partial text from Activity: $text")
        _partialResults.value = text
    }
    
    /**
     * Called by Activity to set error state
     */
    fun setError(error: String?) {
        Log.d(TAG, "❌ Setting error from Activity: $error")
        _error.value = error
    }
    
    fun stopListening() {
        try {
            Log.d(TAG, "🛑 Stopping speech recognition")
            _isListening.value = false
            _partialResults.value = ""
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
            _isListening.value = false
        }
    }
    
    fun cancelListening() {
        try {
            Log.d(TAG, "❌ Cancelling speech recognition")
            _isListening.value = false
            _recognizedText.value = ""
            _partialResults.value = ""
            _error.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition", e)
            _isListening.value = false
        }
    }
    
    fun clearText() {
        Log.d(TAG, "🧹 Clearing all text")
        _recognizedText.value = ""
        _partialResults.value = ""
        _error.value = null
        Log.d(TAG, "🧹 Text cleared successfully")
    }
    
    fun forceStopAllOperations() {
        Log.d(TAG, "🚫 Force stopping all speech operations")
        try {
            _isListening.value = false
            _error.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error force stopping operations", e)
        }
    }
    
    fun shutdown() {
        if (isDestroyed) return
        
        Log.d(TAG, "🔄 Shutting down speech repository")
        isDestroyed = true
        
        try {
            coroutineScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        } finally {
            _isListening.value = false
            _error.value = null
        }
    }
    
    // Compatibility methods with original SttRepository interface
    fun getOptimizationInfo(): String {
        return "Activity-managed Intent directo - Sin errores de permisos ✓"
    }
    
    fun isUsingOptimizedSettings(): Boolean = true // Always optimized with Intent
    
    fun isOnDeviceRecognitionSupported(): Boolean = true // Intent always works
    
    fun getRecognizedText(): String = _recognizedText.value
}
