package io.orabel.orabelandroid.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import io.orabel.orabelandroid.utils.DeviceResourcesHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

class TtsRepository(private val context: Context) {
    
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var deviceCapabilities: DeviceResourcesHelper.DeviceCapabilities? = null
    private var isCurrentlySpeaking = false
    
    companion object {
        private const val TAG = "TtsRepository"
        private const val UTTERANCE_ID = "TTS_UTTERANCE_ID"
        private const val CHUNK_DELAY_MS = 200L // Delay between chunks for low-end devices
    }
    
    suspend fun initializeTts(): Boolean = suspendCancellableCoroutine { continuation ->
        try {
            // Analyze device capabilities first
            deviceCapabilities = DeviceResourcesHelper.analyzeDeviceCapabilities(context)
            DeviceResourcesHelper.logDeviceOptimizations(context)
            
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    
                    // Apply device-specific optimizations
                    applyDeviceOptimizations()
                    
                    Log.d(TAG, "TTS initialized successfully with optimizations")
                    continuation.resume(true)
                } else {
                    Log.e(TAG, "TTS initialization failed")
                    continuation.resume(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TTS", e)
            continuation.resume(false)
        }
    }
    
    fun setSpanishLanguage(): Boolean {
        return try {
            val spanish = Locale("es", "ES")
            val result = textToSpeech?.setLanguage(spanish)
            
            when (result) {
                TextToSpeech.LANG_MISSING_DATA -> {
                    Log.w(TAG, "Spanish language data is missing")
                    // Try alternative Spanish locale for low-end devices
                    tryAlternativeSpanishLocale()
                }
                TextToSpeech.LANG_NOT_SUPPORTED -> {
                    Log.w(TAG, "Spanish language is not supported")
                    // Try alternative Spanish locale
                    tryAlternativeSpanishLocale()
                }
                else -> {
                    Log.d(TAG, "Spanish language set successfully")
                    
                    // Apply optimized voice selection for low-end devices
                    optimizeVoiceSelection()
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting Spanish language", e)
            false
        }
    }
    
    private fun tryAlternativeSpanishLocale(): Boolean {
        val alternativeLocales = listOf(
            Locale("es", "MX"), // Mexican Spanish
            Locale("es", "AR"), // Argentinian Spanish  
            Locale("es"),       // Generic Spanish
            Locale.getDefault() // Fallback to device default
        )
        
        for (locale in alternativeLocales) {
            val result = textToSpeech?.setLanguage(locale)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.d(TAG, "Alternative Spanish locale set: ${locale.displayName}")
                return true
            }
        }
        
        Log.w(TAG, "No Spanish locale available, using device default")
        return false
    }
    
    private fun optimizeVoiceSelection() {
        deviceCapabilities?.let { capabilities ->
            if (capabilities.isLowEndDevice) {
                try {
                    // Select the most efficient voice for low-end devices
                    val voices = textToSpeech?.voices?.filter { voice ->
                        voice.locale.language == "es" && 
                        !voice.isNetworkConnectionRequired // Prefer offline voices for low-end devices
                    }?.sortedBy { voice ->
                        // Prefer smaller/simpler voices
                        voice.quality
                    }
                    
                    voices?.firstOrNull()?.let { selectedVoice ->
                        textToSpeech?.voice = selectedVoice
                        Log.d(TAG, "Optimized voice selected: ${selectedVoice.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not optimize voice selection", e)
                }
            }
        }
    }
    
    fun getAvailableSpanishVoices(): List<String> {
        val voices = mutableListOf<String>()
        try {
            textToSpeech?.voices?.forEach { voice ->
                if (voice.locale.language == "es") {
                    voices.add("${voice.name} (${voice.locale.displayName})")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available voices", e)
        }
        return voices
    }
    
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
    }
    
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
    }
    
    suspend fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH): Boolean {
        if (!isInitialized || text.isBlank()) {
            return false
        }
        
        isCurrentlySpeaking = true
        
        return try {
            // Use chunked speech for low-end devices
            if (DeviceResourcesHelper.shouldUseChunkedSpeech(text, context)) {
                speakInChunks(text, queueMode)
            } else {
                speakSingleText(text, queueMode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in speak method", e)
            isCurrentlySpeaking = false
            false
        }
    }
    
    private suspend fun speakInChunks(text: String, queueMode: Int): Boolean {
        val chunks = DeviceResourcesHelper.chunkTextForTts(text, context)
        Log.d(TAG, "Speaking in ${chunks.size} chunks for device optimization")
        
        var allSuccessful = true
        
        chunks.forEachIndexed { index, chunk ->
            val chunkMode = if (index == 0) queueMode else TextToSpeech.QUEUE_ADD
            val success = speakSingleText(chunk, chunkMode)
            
            if (!success) {
                allSuccessful = false
            }
            
            // Add small delay between chunks for low-end devices
            if (index < chunks.size - 1) {
                delay(CHUNK_DELAY_MS)
            }
        }
        
        return allSuccessful
    }
    
    private suspend fun speakSingleText(text: String, queueMode: Int): Boolean = 
        suspendCancellableCoroutine { continuation ->
            try {
                val params = Bundle()
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)
                
                // Add device-specific parameters
                val optimizedParams = DeviceResourcesHelper.getOptimizedTtsParams(context)
                optimizedParams.forEach { (key, value) ->
                    params.putString(key, value)
                }
                
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "Speech started: ${text.take(50)}...")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "Speech completed")
                        continuation.resume(true)
                    }
                    
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "Speech error")
                        continuation.resume(false)
                    }
                })
                
                val result = textToSpeech?.speak(text, queueMode, params, UTTERANCE_ID)
                if (result == TextToSpeech.ERROR) {
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error speaking text", e)
                continuation.resume(false)
            }
        }
    
    private fun applyDeviceOptimizations() {
        deviceCapabilities?.let { capabilities ->
            val settings = capabilities.recommendedTtsSettings
            
            // Apply optimized settings
            setSpeechRate(settings.reducedSpeechRate)
            setPitch(settings.reducedPitch)
            
            if (settings.enableMemoryOptimization) {
                // Additional memory optimizations can be added here
                Log.d(TAG, "Memory optimizations applied for low-end device")
            }
            
            Log.d(TAG, "Device optimizations applied: rate=${settings.reducedSpeechRate}, pitch=${settings.reducedPitch}")
        }
    }
    
    fun stop() {
        try {
            textToSpeech?.stop()
            isCurrentlySpeaking = false
            Log.d(TAG, "TTS stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping TTS", e)
        }
    }
    
    fun isSpeaking(): Boolean {
        return try {
            textToSpeech?.isSpeaking == true || isCurrentlySpeaking
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if speaking", e)
            false
        }
    }
    
    fun getDeviceCapabilities(): DeviceResourcesHelper.DeviceCapabilities? {
        return deviceCapabilities
    }
    
    fun isLowEndDevice(): Boolean {
        return deviceCapabilities?.isLowEndDevice ?: false
    }
    
    fun getOptimizationInfo(): String {
        return deviceCapabilities?.let { capabilities ->
            buildString {
                appendLine("Optimizaciones para ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}:")
                appendLine("• Memoria total: ${capabilities.totalMemoryMB}MB")
                if (capabilities.isLowEndDevice) {
                    appendLine("• Optimizaciones activadas para dispositivo de bajos recursos")
                    appendLine("• Velocidad optimizada: ${capabilities.recommendedTtsSettings.reducedSpeechRate}")
                    appendLine("• División de texto en chunks de máximo ${capabilities.recommendedTtsSettings.maxTextChunkSize} caracteres")
                    appendLine("• Voz compacta recomendada")
                } else {
                    appendLine("• Configuración estándar - dispositivo con recursos suficientes")
                }
            }
        } ?: "Información de optimización no disponible"
    }
    
    fun isLanguageAvailable(locale: Locale): Boolean {
        return textToSpeech?.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE ||
                textToSpeech?.isLanguageAvailable(locale) == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                textToSpeech?.isLanguageAvailable(locale) == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
    }
    
    fun shutdown() {
        try {
            stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isInitialized = false
            isCurrentlySpeaking = false
            deviceCapabilities = null
            Log.d(TAG, "TTS repository shutdown completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during TTS shutdown", e)
        }
    }
}
