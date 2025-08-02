package io.orabel.orabelandroid.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Helper class to detect device capabilities and optimize TTS for low-resource devices
 * Specifically optimized for devices like ZTE Blade V41 and similar budget phones
 */
object DeviceResourcesHelper {
    private const val TAG = "DeviceResourcesHelper"
    
    // Threshold for low-memory devices (512MB)
    private const val LOW_MEMORY_THRESHOLD_MB = 512
    
    // Known low-resource device models (can be expanded)
    private val LOW_RESOURCE_DEVICES = listOf(
        "zte blade v41",
        "zte blade a71",
        "zte blade a51",
        "samsung galaxy a01",
        "samsung galaxy a02",
        "nokia 1",
        "nokia 2",
        "alcatel 1"
    )
    
    data class DeviceCapabilities(
        val isLowMemoryDevice: Boolean,
        val totalMemoryMB: Long,
        val isLowEndDevice: Boolean,
        val recommendedTtsSettings: TtsOptimizationSettings
    )
    
    data class TtsOptimizationSettings(
        val useCompactVoice: Boolean,
        val reducedSpeechRate: Float,
        val reducedPitch: Float,
        val enableMemoryOptimization: Boolean,
        val maxTextChunkSize: Int,
        val useSimpleEngine: Boolean
    )
    
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
    
    fun analyzeDeviceCapabilities(context: Context): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val totalMemoryMB = memInfo.totalMem / (1024 * 1024)
        val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".lowercase()
        
        val isLowMemoryDevice = totalMemoryMB < LOW_MEMORY_THRESHOLD_MB || 
                                activityManager.isLowRamDevice
        
        val isLowEndDevice = isLowMemoryDevice || 
                            LOW_RESOURCE_DEVICES.any { deviceModel.contains(it) } ||
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        
        val optimizationSettings = createOptimizationSettings(isLowEndDevice, totalMemoryMB)
        
        Log.d(TAG, "Device Analysis:")
        Log.d(TAG, "  Model: $deviceModel")
        Log.d(TAG, "  Total Memory: ${totalMemoryMB}MB")
        Log.d(TAG, "  Is Low Memory: $isLowMemoryDevice")
        Log.d(TAG, "  Is Low End: $isLowEndDevice")
        Log.d(TAG, "  Android API: ${Build.VERSION.SDK_INT}")
        
        return DeviceCapabilities(
            isLowMemoryDevice = isLowMemoryDevice,
            totalMemoryMB = totalMemoryMB,
            isLowEndDevice = isLowEndDevice,
            recommendedTtsSettings = optimizationSettings
        )
    }
    
    private fun createOptimizationSettings(
        isLowEndDevice: Boolean, 
        totalMemoryMB: Long
    ): TtsOptimizationSettings {
        return if (isLowEndDevice) {
            // Optimized settings for low-resource devices like ZTE Blade V41
            TtsOptimizationSettings(
                useCompactVoice = true,
                reducedSpeechRate = 0.8f, // Slightly slower for better processing
                reducedPitch = 0.9f, // Lower pitch uses less processing
                enableMemoryOptimization = true,
                maxTextChunkSize = 100, // Smaller chunks for low memory
                useSimpleEngine = true
            )
        } else {
            // Standard settings for capable devices
            TtsOptimizationSettings(
                useCompactVoice = false,
                reducedSpeechRate = 1.0f,
                reducedPitch = 1.0f,
                enableMemoryOptimization = false,
                maxTextChunkSize = 500,
                useSimpleEngine = false
            )
        }
    }
    
    fun getOptimizedTtsParams(context: Context): Map<String, String> {
        val capabilities = analyzeDeviceCapabilities(context)
        val params = mutableMapOf<String, String>()
        
        if (capabilities.isLowEndDevice) {
            // Optimize for low-end devices
            params["audio_stream"] = "notification" // Use lighter audio stream
            params["engine_preference"] = "simple" // Prefer simple TTS engine
            params["quality"] = "low" // Use lower quality for better performance
            params["buffer_size"] = "small" // Reduce buffer size
        }
        
        return params
    }
    
    fun shouldUseChunkedSpeech(text: String, context: Context): Boolean {
        val capabilities = analyzeDeviceCapabilities(context)
        return capabilities.isLowEndDevice && 
               text.length > capabilities.recommendedTtsSettings.maxTextChunkSize
    }
    
    fun chunkTextForTts(text: String, context: Context): List<String> {
        val capabilities = analyzeDeviceCapabilities(context)
        val maxChunkSize = capabilities.recommendedTtsSettings.maxTextChunkSize
        
        if (text.length <= maxChunkSize) {
            return listOf(text)
        }
        
        val chunks = mutableListOf<String>()
        var currentIndex = 0
        
        while (currentIndex < text.length) {
            val endIndex = minOf(currentIndex + maxChunkSize, text.length)
            var chunkEnd = endIndex
            
            // Try to break at sentence boundary
            if (endIndex < text.length) {
                val sentenceEnd = text.lastIndexOf('.', endIndex)
                val questionEnd = text.lastIndexOf('?', endIndex)
                val exclamationEnd = text.lastIndexOf('!', endIndex)
                
                val bestBreak = maxOf(sentenceEnd, questionEnd, exclamationEnd)
                if (bestBreak > currentIndex) {
                    chunkEnd = bestBreak + 1
                }
            }
            
            chunks.add(text.substring(currentIndex, chunkEnd).trim())
            currentIndex = chunkEnd
        }
        
        return chunks
    }
    
    fun logDeviceOptimizations(context: Context) {
        val capabilities = analyzeDeviceCapabilities(context)
        
        Log.i(TAG, "=== TTS Device Optimizations ===")
        Log.i(TAG, "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.i(TAG, "Total Memory: ${capabilities.totalMemoryMB}MB")
        Log.i(TAG, "Optimizations Applied:")
        
        if (capabilities.isLowEndDevice) {
            Log.i(TAG, "  ✓ Low-end device optimizations enabled")
            Log.i(TAG, "  ✓ Compact voice preference")
            Log.i(TAG, "  ✓ Reduced speech rate: ${capabilities.recommendedTtsSettings.reducedSpeechRate}")
            Log.i(TAG, "  ✓ Text chunking: max ${capabilities.recommendedTtsSettings.maxTextChunkSize} chars")
            Log.i(TAG, "  ✓ Memory optimization enabled")
        } else {
            Log.i(TAG, "  ✓ Standard TTS settings")
            Log.i(TAG, "  ✓ Full feature set available")
        }
        
        Log.i(TAG, "================================")
    }

    /**
     * Get optimized STT settings based on device capabilities
     */
    fun getSttOptimizationSettings(
        isLowEndDevice: Boolean,
        totalMemoryMB: Long
    ): SttOptimizationSettings {
        return if (isLowEndDevice) {
            // Optimized settings for low-resource devices like ZTE Blade V41
            SttOptimizationSettings(
                useOnDeviceRecognition = false, // Avoid on-device processing on low-end devices
                preferOfflineMode = false, // Cloud-based is often more reliable on low-end devices
                enablePartialResults = false, // Reduce processing overhead
                maxSpeechInputLength = 15000, // 15 seconds max for low memory
                useSimpleAudioSource = true, // Use simpler audio source
                enableRetryMechanism = true, // Auto-retry on errors 5 and 13
                retryDelay = 2000, // 2 second delay between retries
                maxRetries = 3, // Maximum retry attempts
                enableResourceMonitoring = true,
                useLowLatencyMode = false // Avoid low latency on resource-constrained devices
            )
        } else {
            // Standard settings for capable devices
            SttOptimizationSettings(
                useOnDeviceRecognition = true,
                preferOfflineMode = true,
                enablePartialResults = true,
                maxSpeechInputLength = 30000, // 30 seconds max
                useSimpleAudioSource = false,
                enableRetryMechanism = false,
                retryDelay = 1000,
                maxRetries = 1,
                enableResourceMonitoring = false,
                useLowLatencyMode = true
            )
        }
    }

    /**
     * Log device STT optimization information
     */
    fun logSttOptimizations(
        context: Context,
        settings: SttOptimizationSettings
    ) {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val totalMemory = context.let {
            val activityManager = it.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024 * 1024)
        }
        
        Log.i(TAG, "=== STT Device Optimizations ===")
        Log.i(TAG, "Device: $deviceName")
        Log.i(TAG, "Total Memory: ${totalMemory}MB")
        Log.i(TAG, "Optimizations Applied:")
        Log.i(TAG, "  ${if (settings.useOnDeviceRecognition) "✓" else "✗"} On-device recognition")
        Log.i(TAG, "  ${if (settings.preferOfflineMode) "✓" else "✗"} Offline mode preference")
        Log.i(TAG, "  ${if (settings.enablePartialResults) "✓" else "✗"} Partial results")
        Log.i(TAG, "  ${if (settings.enableRetryMechanism) "✓" else "✗"} Auto-retry on errors")
        Log.i(TAG, "  ${if (settings.useSimpleAudioSource) "✓" else "✗"} Simple audio source")
        Log.i(TAG, "  ⏱️ Max speech length: ${settings.maxSpeechInputLength}ms")
        Log.i(TAG, "  🔄 Max retries: ${settings.maxRetries}")
        Log.i(TAG, "  ⏳ Retry delay: ${settings.retryDelay}ms")
    }
    
    /**
     * Get a user-friendly device display name
     */
    fun getDeviceDisplayName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim()
    }
}
