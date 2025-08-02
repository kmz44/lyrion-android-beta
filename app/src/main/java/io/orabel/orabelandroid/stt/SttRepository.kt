package io.orabel.orabelandroid.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import io.orabel.orabelandroid.utils.SttTestHelper
import io.orabel.orabelandroid.utils.SttStatus
import io.orabel.orabelandroid.utils.DeviceResourcesHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import java.util.Locale
import java.lang.ref.WeakReference

class SttRepository(context: Context) {
    
    private val contextRef = WeakReference(context)
    private var speechRecognizer: SpeechRecognizer? = null
    private var isDestroyed = false
    
    // Device optimization settings
    private val deviceCapabilities = DeviceResourcesHelper.analyzeDeviceCapabilities(context)
    private val sttOptimizations = DeviceResourcesHelper.getSttOptimizationSettings(
        deviceCapabilities.isLowEndDevice,
        deviceCapabilities.totalMemoryMB
    )
    
    // Retry mechanism for low-end devices
    private var currentRetryCount = 0
    private var retryJob: Job? = null
    private var retryStartTime = 0L // Track when retries started
    private var isRetryInProgress = false // Prevent interference between retry operations
    private val maxRetryDurationMs = 30000L // 30 seconds max retry duration
    // Use GlobalScope for retry operations to avoid cancellation issues
    private val retryScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
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
        private const val TAG = "SttRepository"
    }
    
    init {
        // Log device optimizations
        DeviceResourcesHelper.logSttOptimizations(context, sttOptimizations)
        checkAvailability()
    }
    
    private fun checkAvailability() {
        val context = contextRef.get() ?: return
        try {
            val sttStatus = SttTestHelper.checkSttAvailability(context)
            val isRecognitionAvailable = sttStatus != SttStatus.NOT_AVAILABLE && sttStatus != SttStatus.ERROR
            
            _isAvailable.value = isRecognitionAvailable
            Log.d(TAG, "Speech recognition available: $isRecognitionAvailable (Status: $sttStatus)")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking STT availability", e)
            _isAvailable.value = false
        }
    }
    
    fun initializeStt(): Boolean {
        if (isDestroyed) {
            Log.w(TAG, "STT repository already destroyed")
            return false
        }
        
        val context = contextRef.get()
        if (context == null) {
            Log.e(TAG, "Context is null, cannot initialize STT")
            _error.value = "Error interno: contexto no disponible"
            return false
        }
        
        return try {
            if (!_isAvailable.value) {
                _error.value = "Reconocimiento de voz no disponible en este dispositivo"
                return false
            }
            
            // Cleanup existing recognizer
            cleanupRecognizer()
            
            // Apply device-specific optimization for speech recognizer creation
            speechRecognizer = try {
                if (sttOptimizations.useOnDeviceRecognition && 
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    Log.d(TAG, "Using on-device speech recognition (optimized for device)")
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    Log.d(TAG, "Using cloud-based speech recognition (optimized for low-resource device)")
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create preferred recognizer, using fallback", e)
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "✅ Ready for speech - STT session started successfully")
                    _isListening.value = true
                    _error.value = null
                    // Reset retry timer when speech recognition is actually ready
                    if (currentRetryCount > 0) {
                        Log.i(TAG, "🔄 STT recovered after $currentRetryCount retries")
                        currentRetryCount = 0
                        retryStartTime = 0L
                        retryJob?.cancel()
                    }
                    isRetryInProgress = false
                }
                
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech input begun")
                }
                
                override fun onRmsChanged(rmsdB: Float) {
                    // Volume level changed - no action needed
                }
                
                override fun onBufferReceived(buffer: ByteArray?) {
                    // Audio buffer received - no action needed
                }
                
                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech input ended")
                    _isListening.value = false
                }
                
                override fun onError(error: Int) {
                    Log.e(TAG, "🚨 Speech recognition error: $error (${getErrorDescription(error)})")
                    Log.i(TAG, "🔧 ENTERING onError handler - error: $error")
                    try {
                        Log.i(TAG, "📊 Current state - listening: ${_isListening.value}, retryCount: $currentRetryCount, startTime: $retryStartTime, retryInProgress: $isRetryInProgress")
                        
                        // CRITICAL FIX: Always set listening to false first to prevent infinite loops
                        _isListening.value = false
                        
                        // If a retry is already in progress, don't start another one
                        if (isRetryInProgress) {
                            Log.w(TAG, "⚠️ Retry already in progress - ignoring error $error")
                            return
                        }
                        
                        // Initialize retryStartTime if not set
                        if (retryStartTime == 0L) {
                            retryStartTime = System.currentTimeMillis()
                            Log.i(TAG, "🔧 Initializing retry start time: $retryStartTime")
                        }
                        
                        // Handle retry mechanism for errors 5 and 13 on ALL devices
                        val isRetriableError = (error == 5 || error == 13 || error == 8) 
                        val retryTimeElapsed = System.currentTimeMillis() - retryStartTime
                        val hasTimeForRetry = retryTimeElapsed < maxRetryDurationMs
                        val shouldRetry = isRetriableError && currentRetryCount < 3 && !isDestroyed && hasTimeForRetry
                        
                        Log.i(TAG, "🔍 Error analysis: error=$error, retriable=$isRetriableError, retryCount=$currentRetryCount, timeElapsed=${retryTimeElapsed}ms, shouldRetry=$shouldRetry")
                        
                        if (shouldRetry) {
                            currentRetryCount++
                            isRetryInProgress = true
                            Log.i(TAG, "🔄 Auto-retrying STT (attempt $currentRetryCount/3) after error $error")
                            _error.value = "🔄 Optimizando reconocimiento... (intento $currentRetryCount de 3)"
                            
                            // Cancel previous retry job if exists but preserve the retry logic
                            retryJob?.cancel()
                            
                            // Schedule retry with EXTREMELY robust scope that persists independently
                            retryJob = retryScope.launch {
                                val delayTime = when (error) {
                                    13 -> 1500L  // Shorter delay for error 13 (very common on low-end devices)
                                    5 -> 3000L   // Standard delay for error 5
                                    8 -> 2000L   // Audio error
                                    else -> 2500L
                                }
                                Log.i(TAG, "⏳ Scheduling retry in ${delayTime}ms for error $error...")
                                
                                try {
                                    delay(delayTime)
                                    
                                    // Double-check we still should retry before executing
                                    if (!isDestroyed && isActive && currentRetryCount > 0 && isRetryInProgress) {
                                        Log.i(TAG, "✅ Executing retry attempt $currentRetryCount for error $error")
                                        
                                        // Clean up previous recognizer state more thoroughly for error 13
                                        if (error == 13) {
                                            speechRecognizer?.cancel()
                                            delay(800) // Longer cleanup delay for error 13
                                        } else {
                                            speechRecognizer?.cancel()
                                            delay(500)
                                        }
                                        
                                        // Set listening to true before starting
                                        _isListening.value = true
                                        internalStartListening()
                                    } else {
                                        Log.w(TAG, "⚠️ Skipping retry - conditions no longer met (destroyed=$isDestroyed, active=$isActive, retryCount=$currentRetryCount, retryInProgress=$isRetryInProgress)")
                                        if (currentRetryCount > 0) {
                                            currentRetryCount = 0
                                            retryStartTime = 0L
                                        }
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    Log.w(TAG, "🔄 Retry job was cancelled - this may be due to user interaction or system state change")
                                    // Keep retry counters - user might want to restart
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ Error during retry attempt $currentRetryCount", e)
                                    _isListening.value = false
                                    _error.value = getOptimizedErrorMessage(error)
                                    currentRetryCount = 0
                                    retryStartTime = 0L
                                } finally {
                                    isRetryInProgress = false
                                }
                            }
                        } else {
                            // Final failure - reset and show error
                            val wasRetrying = currentRetryCount > 0
                            currentRetryCount = 0
                            retryJob?.cancel()
                            retryStartTime = 0L
                            isRetryInProgress = false
                            
                            val errorMessage = if (wasRetrying) {
                                "❌ Error persistente tras reintentos: ${getOptimizedErrorMessage(error)}"
                            } else {
                                getOptimizedErrorMessage(error)
                            }
                            
                            _error.value = errorMessage
                            Log.i(TAG, "❌ Final error state: $errorMessage")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "💥 Critical error in onError handler", e)
                        Log.e(TAG, "💥 Exception details: ${e.message}")
                        Log.e(TAG, "💥 Stack trace: ${e.stackTrace.joinToString("\n")}")
                        _isListening.value = false
                        _error.value = "Error crítico en reconocimiento"
                        currentRetryCount = 0
                        retryStartTime = 0L
                        isRetryInProgress = false
                        retryJob?.cancel()
                    }
                }
                
                override fun onResults(results: Bundle?) {
                    try {
                        Log.d(TAG, "✅ Speech recognition completed successfully")
                        _isListening.value = false
                        
                        // Reset retry state on successful recognition
                        if (currentRetryCount > 0) {
                            Log.i(TAG, "🎯 STT recovered successfully after $currentRetryCount retries")
                            currentRetryCount = 0
                            retryStartTime = 0L
                            retryJob?.cancel()
                        }
                        isRetryInProgress = false
                        
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            _recognizedText.value = recognizedText
                            _partialResults.value = ""
                            _error.value = null
                            Log.d(TAG, "Recognition result: $recognizedText")
                        } else {
                            Log.w(TAG, "No recognition results found")
                            _error.value = "No se reconoció ningún texto"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing recognition results", e)
                        _error.value = "Error procesando resultados"
                        _isListening.value = false
                        isRetryInProgress = false
                    }
                }
                
                override fun onPartialResults(partialResults: Bundle?) {
                    try {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty() && sttOptimizations.enablePartialResults) {
                            val partialText = matches[0]
                            _partialResults.value = partialText
                            Log.d(TAG, "Partial result: $partialText")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing partial results", e)
                    }
                }
                
                override fun onEvent(eventType: Int, params: Bundle?) {
                    Log.d(TAG, "Speech recognition event: $eventType")
                }
            })
            
            Log.d(TAG, "STT initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing STT", e)
            _error.value = "Error inicializando STT: ${e.message}"
            cleanupRecognizer()
            false
        }
    }
    
    fun startListening() {
        if (isDestroyed) {
            Log.w(TAG, "Cannot start listening, repository destroyed")
            return
        }
        
        if (!_isAvailable.value) {
            _error.value = "STT no disponible"
            return
        }
        
        if (_isListening.value) {
            Log.w(TAG, "Already listening")
            return
        }
        
        if (speechRecognizer == null) {
            _error.value = "STT no inicializado"
            return
        }
        
        // Reset retry mechanism when user manually starts listening
        currentRetryCount = 0
        retryStartTime = System.currentTimeMillis()
        retryJob?.cancel()
        
        Log.i(TAG, "🎤 User manually starting STT - resetting retry state (startTime: $retryStartTime)")
        _isListening.value = true
        
        internalStartListening()
    }
    
    private fun internalStartListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, sttOptimizations.enablePartialResults)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, if (deviceCapabilities.isLowEndDevice) 3 else 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, contextRef.get()?.packageName ?: "")
                
                // Apply device-specific optimizations
                try {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, sttOptimizations.preferOfflineMode)
                } catch (e: Exception) {
                    Log.w(TAG, "Offline mode preference not supported", e)
                }
                
                // Optimized timeout settings for device type - More generous for low-end devices
                try {
                    val silenceLength = if (deviceCapabilities.isLowEndDevice) 2500 else 3000 // Longer silence tolerance
                    val minLength = if (deviceCapabilities.isLowEndDevice) 500 else 1000 // Allow shorter input
                    val maxLength = if (deviceCapabilities.isLowEndDevice) 20000 else 30000 // More time for processing
                    
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceLength)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceLength)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, minLength)
                    
                    // Add maximum length for low-end devices
                    if (deviceCapabilities.isLowEndDevice) {
                        putExtra("android.speech.extras.SPEECH_INPUT_MAXIMUM_LENGTH_MILLIS", maxLength)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Timeout settings not supported", e)
                }
                
                // Audio source optimization for low-end devices
                if (sttOptimizations.useSimpleAudioSource) {
                    try {
                        putExtra("android.speech.extra.AUDIO_SOURCE", 1) // MIC audio source
                        putExtra("android.speech.extra.AUDIO_SESSION_ID", 0) // Default session
                        // Disable noise suppression and echo cancellation for performance
                        putExtra("android.speech.extra.DISABLE_NOISE_SUPPRESSION", true)
                        putExtra("android.speech.extra.DISABLE_ECHO_CANCELLATION", true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio optimization settings not supported", e)
                    }
                }
                
                // Performance optimizations for ZTE Blade V41 and similar
                if (deviceCapabilities.isLowEndDevice) {
                    try {
                        // Disable confidence scores to reduce processing
                        putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, false)
                        // Request simple recognition format
                        putExtra("android.speech.extra.RECOGNITION_FORMAT", "simple")
                        // Disable punctuation for faster processing  
                        putExtra("android.speech.extra.DISABLE_PUNCTUATION", true)
                    } catch (e: Exception) {
                        Log.w(TAG, "Performance optimization settings not supported", e)
                    }
                }
            }
            
            // Clear previous results
            _recognizedText.value = ""
            _partialResults.value = ""
            _error.value = null
            
            val deviceInfo = if (deviceCapabilities.isLowEndDevice) " (optimizado para ${DeviceResourcesHelper.getDeviceDisplayName()})" else ""
            Log.d(TAG, "Starting speech recognition$deviceInfo")
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition", e)
            _error.value = "Error iniciando reconocimiento: ${e.message}"
            _isListening.value = false
        }
    }
    
    fun stopListening() {
        if (isDestroyed) return
        
        try {
            Log.d(TAG, "🛑 User manually stopping speech recognition")
            Log.d(TAG, "🛑 Current state - retryCount: $currentRetryCount, listening: ${_isListening.value}")
            
            // If we have active retries, don't interfere with them
            if (currentRetryCount > 0 && retryJob?.isActive == true) {
                Log.w(TAG, "⚠️ Active retry in progress ($currentRetryCount/3) - NOT cancelling retries, user can restart later")
                // Just stop the current session gracefully without touching retry mechanism
                try {
                    speechRecognizer?.stopListening()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping current session during retry", e)
                }
            } else {
                Log.d(TAG, "🛑 No active retries - safe to stop completely")
                retryJob?.cancel()
                speechRecognizer?.stopListening()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping speech recognition", e)
        } finally {
            _isListening.value = false
        }
    }
    
    fun cancelListening() {
        if (isDestroyed) return
        
        try {
            Log.d(TAG, "❌ User manually cancelling speech recognition - force cleanup")
            // Force cancel retries when user explicitly cancels
            retryJob?.cancel()
            currentRetryCount = 0
            retryStartTime = 0L
            isRetryInProgress = false
            speechRecognizer?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling speech recognition", e)
        } finally {
            _isListening.value = false
            _recognizedText.value = ""
            _partialResults.value = ""
            _error.value = null
        }
    }
    
    fun clearText() {
        _recognizedText.value = ""
        _partialResults.value = ""
        _error.value = null
        // Only reset retry state if not currently retrying
        if (currentRetryCount == 0) {
            Log.d(TAG, "🧹 Text cleared - no active retries")
        } else {
            Log.d(TAG, "🧹 Text cleared - preserving retry state ($currentRetryCount/3)")
        }
    }
    
    /**
     * Get optimization information for UI display
     */
    fun getOptimizationInfo(): String? {
        return if (deviceCapabilities.isLowEndDevice) {
            "STT optimizado para ${DeviceResourcesHelper.getDeviceDisplayName()}"
        } else {
            null
        }
    }
    
    /**
     * Check if device is using optimized settings
     */
    fun isUsingOptimizedSettings(): Boolean = deviceCapabilities.isLowEndDevice
    
    /**
     * Force stop all STT operations including retries - use when user really wants to stop
     */
    fun forceStopAllOperations() {
        Log.d(TAG, "🚫 Force stopping ALL STT operations including retries")
        try {
            retryJob?.cancel()
            currentRetryCount = 0
            retryStartTime = 0L
            isRetryInProgress = false
            speechRecognizer?.cancel()
            _isListening.value = false
            _error.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error force stopping operations", e)
        }
    }
    
    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up recognizer", e)
        } finally {
            speechRecognizer = null
        }
    }
    
    fun shutdown() {
        if (isDestroyed) return
        
        Log.d(TAG, "🔄 Shutting down STT repository - cleaning all state")
        isDestroyed = true
        
        try {
            // Cancel any pending retry attempts
            retryJob?.cancel()
            currentRetryCount = 0
            retryStartTime = 0L
            isRetryInProgress = false
            
            cancelListening()
            cleanupRecognizer()
            
            // Cancel both coroutine scopes
            retryScope.cancel()
            coroutineScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        } finally {
            _isListening.value = false
            _error.value = null
        }
    }
    
    fun getRecognizedText(): String = _recognizedText.value
    
    fun isOnDeviceRecognitionSupported(): Boolean {
        val context = contextRef.get() ?: return false
        return try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking on-device recognition support", e)
            false
        }
    }
    
    fun getAvailableLanguages(): List<String> {
        return listOf(
            "es-ES", // Spanish (Spain)
            "es-MX", // Spanish (Mexico)
            "es-AR", // Spanish (Argentina)
            "es-CL", // Spanish (Chile)
            "es-CO", // Spanish (Colombia)
            "es-PE", // Spanish (Peru)
            "es-VE"  // Spanish (Venezuela)
        )
    }
    
    /**
     * Get human-readable description for speech recognition errors
     */
    private fun getErrorDescription(errorCode: Int): String {
        return when (errorCode) {
            1 -> "Network timeout"
            2 -> "Network error" 
            3 -> "Audio recording error"
            4 -> "Server sends error status"
            5 -> "Client side error"
            6 -> "No speech input"
            7 -> "No match found"
            8 -> "Recognition service busy"
            9 -> "Insufficient permissions"
            10 -> "Too many requests"
            11 -> "Server disconnected"
            12 -> "Language unavailable"
            13 -> "Language not supported"
            14 -> "Cannot check support"
            else -> "Unknown error ($errorCode)"
        }
    }
    
    /**
     * Get optimized error message for user display, especially for low-end devices
     */
    private fun getOptimizedErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            1, 2 -> "⚠️ Problema de conexión. Verifica tu conexión a internet."
            3 -> "🎤 Error de audio. Verifica que el micrófono esté disponible."
            4 -> "⚠️ Error del servidor. Intenta de nuevo en unos momentos."
            5 -> {
                if (deviceCapabilities.isLowEndDevice) {
                    "⚡ Procesando... El dispositivo está optimizando el reconocimiento."
                } else {
                    "⚠️ Error interno. Intenta de nuevo."
                }
            }
            6 -> "🤐 No se detectó voz. Habla más cerca del micrófono."
            7 -> "❓ No se entendió. Intenta hablar más claro."
            8 -> "⏳ Servicio ocupado. Espera un momento e intenta de nuevo."
            9 -> "🔒 Se necesitan permisos de micrófono para usar esta función."
            10 -> "⏱️ Demasiadas solicitudes. Espera un momento."
            11 -> "📡 Conexión perdida. Verifica tu conexión."
            12, 13 -> {
                if (deviceCapabilities.isLowEndDevice) {
                    "🌐 Configurando idioma optimizado para tu dispositivo..."
                } else {
                    "🌐 Idioma no disponible. Usando configuración predeterminada."
                }
            }
            14 -> "❓ No se puede verificar compatibilidad. Intenta de nuevo."
            else -> "⚠️ Error desconocido. Intenta reiniciar la aplicación."
        }
    }

    /**
     * Configuration class for STT optimizations on low-resource devices
     */
    data class SttOptimizationSettings(
        val useOnDeviceRecognition: Boolean,
        val preferOfflineMode: Boolean,
        val enablePartialResults: Boolean,
        val maxSpeechInputLength: Int, // in milliseconds
        val useSimpleAudioSource: Boolean,
        val enableRetryMechanism: Boolean,
        val retryDelay: Long, // in milliseconds
        val maxRetries: Int,
        val enableResourceMonitoring: Boolean,
        val useLowLatencyMode: Boolean
    )
}
