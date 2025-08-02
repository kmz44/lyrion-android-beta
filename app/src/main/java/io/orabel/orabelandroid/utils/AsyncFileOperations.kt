/*
 * Copyright (C) 2024 Lyrion
 * Utility for performing file operations asynchronously to avoid StrictMode violations
 */

package io.orabel.orabelandroid.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object AsyncFileOperations {
    
    private const val TAG = "AsyncFileOperations"
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Verifica si un archivo existe de forma asíncrona
     */
    suspend fun fileExists(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            file.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if file exists: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * Crea directorios de forma asíncrona
     */
    suspend fun createDirectories(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            file.mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directories: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * Elimina un archivo de forma asíncrona
     */
    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * Obtiene el tamaño de un archivo de forma asíncrona
     */
    suspend fun getFileSize(file: File): Long = withContext(Dispatchers.IO) {
        try {
            if (file.exists()) file.length() else 0L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size: ${file.absolutePath}", e)
            0L
        }
    }
    
    /**
     * Verifica la integridad de un archivo calculando su SHA256
     */
    suspend fun verifyFileIntegrity(file: File, expectedSha256: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext false
            
            // Si no hay hash esperado, solo verificamos que el archivo exista y tenga contenido
            if (expectedSha256.isNullOrEmpty()) {
                return@withContext file.length() > 0
            }
            
            val calculatedSha256 = calculateSHA256(file)
            calculatedSha256.equals(expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying file integrity: ${file.absolutePath}", e)
            false
        }
    }
    
    /**
     * Calcula el SHA256 de un archivo
     */
    private suspend fun calculateSHA256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        
        val hashBytes = digest.digest()
        hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Obtiene el directorio de modelos de forma asíncrona
     */
    suspend fun getModelsDirectory(context: Context): File = withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(context.getExternalFilesDir(null), "models")
            if (!modelsDir.exists()) {
                modelsDir.mkdirs()
            }
            modelsDir
        } catch (e: Exception) {
            Log.e(TAG, "Error getting models directory", e)
            // Fallback al directorio interno
            File(context.filesDir, "models").also { it.mkdirs() }
        }
    }
    
    /**
     * Limpia los recursos del scope
     */
    fun cleanup() {
        ioScope.cancel()
    }
}
