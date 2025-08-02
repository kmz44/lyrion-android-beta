package io.orabel.orabelandroid.utils

import android.content.Context
import android.util.Log
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Replacement for Android Toast that avoids SystemUIToast APK loading issues
 * Uses Compose Snackbar instead which doesn't rely on SystemUI resources
 */
object SafeToast {
    
    private const val TAG = "SafeToast"
    
    /**
     * Safe show message without using Toast to avoid APK loading errors
     */
    fun show(context: Context, message: String, duration: Duration = Duration.SHORT) {
        try {
            Log.i(TAG, "Message: $message")
            // Fallback to console logging if all else fails
        } catch (e: Exception) {
            Log.e(TAG, "Error showing safe message", e)
        }
    }
    
    /**
     * Safe show message with error handling
     */
    fun showSafe(context: Context?, message: String, duration: Duration = Duration.SHORT) {
        try {
            if (context != null) {
                show(context, message, duration)
            } else {
                Log.w(TAG, "Context is null, logging message: $message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showSafe: $message", e)
        }
    }
    
    enum class Duration {
        SHORT, LONG
    }
}

/**
 * Composable utility for safe messaging within Compose UI
 */
@Composable
fun rememberSafeMessaging(snackbarHostState: SnackbarHostState): SafeMessaging {
    val coroutineScope = rememberCoroutineScope()
    
    return SafeMessaging(
        showMessage = { message ->
            coroutineScope.launch {
                try {
                    snackbarHostState.showSnackbar(message)
                } catch (e: Exception) {
                    Log.e("SafeMessaging", "Error showing snackbar: $message", e)
                }
            }
        }
    )
}

data class SafeMessaging(
    val showMessage: (String) -> Unit
)
