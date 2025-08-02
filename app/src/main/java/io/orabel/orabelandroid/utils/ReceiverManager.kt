/*
 * Copyright (C) 2024 Lyrion
 * Manager para BroadcastReceivers para evitar memory leaks
 */

package io.orabel.orabelandroid.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

object ReceiverManager {
    
    private const val TAG = "ReceiverManager"
    private val registeredReceivers = ConcurrentHashMap<BroadcastReceiver, Context>()
    
    /**
     * Registra un BroadcastReceiver de forma segura con flags apropiadas para Android 13+
     */
    fun registerReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        try {
            // Para Android 13+ (API 33+), necesitamos especificar RECEIVER_NOT_EXPORTED 
            // ya que este receiver es para uso interno de la app
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            registeredReceivers[receiver] = context
            Log.d(TAG, "Receiver registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error registering receiver", e)
        }
    }
    
    /**
     * Desregistra un BroadcastReceiver de forma segura
     */
    fun unregisterReceiver(receiver: BroadcastReceiver) {
        try {
            val context = registeredReceivers.remove(receiver)
            context?.unregisterReceiver(receiver)
            Log.d(TAG, "Receiver unregistered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }
    
    /**
     * Limpia todos los receivers registrados
     */
    fun cleanup() {
        try {
            registeredReceivers.entries.forEach { (receiver, context) ->
                try {
                    context.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up receiver", e)
                }
            }
            registeredReceivers.clear()
            Log.d(TAG, "All receivers cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
