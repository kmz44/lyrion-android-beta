package io.orabel.orabelandroid.utils

import android.content.Context
import android.speech.SpeechRecognizer
import android.util.Log

object SttTestHelper {
    
    private const val TAG = "SttTestHelper"
    
    fun checkSttAvailability(context: Context): SttStatus {
        return try {
            val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
            val isOnDeviceAvailable = try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            } catch (e: Exception) {
                false
            }
            
            Log.d(TAG, "STT Available: $isAvailable")
            Log.d(TAG, "On-device STT Available: $isOnDeviceAvailable")
            
            when {
                !isAvailable -> SttStatus.NOT_AVAILABLE
                isOnDeviceAvailable -> SttStatus.AVAILABLE_ON_DEVICE
                else -> SttStatus.AVAILABLE_CLOUD_ONLY
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking STT availability", e)
            SttStatus.ERROR
        }
    }
    
    fun logDeviceInfo(context: Context) {
        try {
            Log.d(TAG, "=== Device STT Information ===")
            Log.d(TAG, "Device Manufacturer: ${android.os.Build.MANUFACTURER}")
            Log.d(TAG, "Device Model: ${android.os.Build.MODEL}")
            Log.d(TAG, "Android Version: ${android.os.Build.VERSION.RELEASE}")
            Log.d(TAG, "SDK Level: ${android.os.Build.VERSION.SDK_INT}")
            
            val status = checkSttAvailability(context)
            Log.d(TAG, "STT Status: $status")
            
            Log.d(TAG, "==============================")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging device info", e)
        }
    }
}

enum class SttStatus {
    NOT_AVAILABLE,
    AVAILABLE_CLOUD_ONLY,
    AVAILABLE_ON_DEVICE,
    ERROR
}
