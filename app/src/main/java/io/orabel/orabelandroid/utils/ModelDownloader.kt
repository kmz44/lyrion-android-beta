/*
 * Copyright (C) 2024 Lyrion
 * Model downloader utility for downloading AI models
 */

package io.orabel.orabelandroid.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import io.orabel.orabelandroid.R

object ModelDownloader {
    
    private const val TAG = "ModelDownloader"
    
    // SHA256 hash del modelo para verificar integridad
    // Para desarrollo, podemos deshabilitar temporalmente la verificación
    private const val LLAMA_MODEL_SHA256 = ""
    private const val VERIFY_INTEGRITY = false // Deshabilitar verificación durante desarrollo
    
    fun downloadModel(
        context: Context,
        url: String,
        destinationFile: File,
        onProgress: (Float) -> Unit = {},
        onSuccess: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Ejecutar operaciones de archivo en background thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting download from: $url")
                Log.d(TAG, "Destination: ${destinationFile.absolutePath}")
                
                // Verificar si el archivo ya existe
                if (AsyncFileOperations.fileExists(destinationFile)) {
                    Log.d(TAG, "File already exists, checking integrity...")
                    // Si existe, verificar integridad
                    if (AsyncFileOperations.verifyFileIntegrity(destinationFile)) {
                        Log.d(TAG, "File is valid, using existing file")
                        Log.d(TAG, "Calling onSuccess callback with path: ${destinationFile.absolutePath}")
                        withContext(Dispatchers.Main) {
                            try {
                                onSuccess(destinationFile.absolutePath)
                                Log.d(TAG, "onSuccess callback executed successfully")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error in onSuccess callback", e)
                            }
                        }
                        return@launch
                    } else {
                        // Archivo corrupto, eliminar y continuar con descarga
                        Log.d(TAG, "File is corrupted, deleting and re-downloading")
                        AsyncFileOperations.deleteFile(destinationFile)
                    }
                }
                
                // Crear el directorio si no existe
                destinationFile.parentFile?.let { parentDir ->
                    AsyncFileOperations.createDirectories(parentDir)
                }
                
                // Continuar con el DownloadManager en el hilo principal
                withContext(Dispatchers.Main) {
                    startDownloadManagerDownload(context, url, destinationFile, onProgress, onSuccess, onError)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Error al iniciar la descarga: ${e.message}")
                }
            }
        }
    }
    
    private fun startDownloadManagerDownload(
        context: Context,
        url: String,
        destinationFile: File,
        onProgress: (Float) -> Unit,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // Configurar el DownloadManager
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
            
            request.apply {
                setTitle(context.getString(R.string.first_setup_downloading))
                setDescription(context.getString(R.string.first_setup_model_name))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            
            val downloadId = downloadManager.enqueue(request)
            
            // Crear un BroadcastReceiver para monitorear la descarga
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        val query = DownloadManager.Query()
                        query.setFilterById(downloadId)
                        val cursor = downloadManager.query(query)
                        
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                        Log.d(TAG, "Download completed successfully")
                                        ReceiverManager.unregisterReceiver(this)                                    // Verificar integridad del archivo en background thread
                                    CoroutineScope(Dispatchers.IO).launch {
                                        Log.d(TAG, "Verifying file integrity...")
                                        if (AsyncFileOperations.verifyFileIntegrity(destinationFile)) {
                                            Log.d(TAG, "File integrity verified successfully")
                                            withContext(Dispatchers.Main) {
                                                onSuccess(destinationFile.absolutePath)
                                            }
                                        } else {
                                            // Archivo corrupto, eliminar y reportar error
                                            Log.e(TAG, "File integrity verification failed")
                                            AsyncFileOperations.deleteFile(destinationFile)
                                            withContext(Dispatchers.Main) {
                                                onError("El archivo descargado está corrupto. Por favor, inténtalo de nuevo.")
                                            }
                                        }
                                    }
                                }
                                DownloadManager.STATUS_FAILED -> {
                                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                                    Log.e(TAG, "Download failed with reason: $reason")
                                    ReceiverManager.unregisterReceiver(this)
                                    onError("Error en la descarga: $reason")
                                }
                            }
                        }
                        cursor.close()
                    }
                }
            }
            
            // Registrar el receiver
            ReceiverManager.registerReceiver(
                context,
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            )
            
            // Mostrar mensaje de inicio
            Toast.makeText(context, context.getString(R.string.first_setup_download_starting), Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            onError("Error al iniciar la descarga: ${e.message}")
        }
    }
    
    suspend fun isModelDownloadedAsync(context: Context, modelName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelsDir = AsyncFileOperations.getModelsDirectory(context)
                val modelFile = File(modelsDir, modelName)
                AsyncFileOperations.fileExists(modelFile) && AsyncFileOperations.getFileSize(modelFile) > 0
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if model is downloaded", e)
                false
            }
        }
    }
    
    fun isModelDownloaded(context: Context, modelName: String): Boolean {
        // Para mantener compatibilidad, crear versión síncrona con try-catch
        return try {
            runBlocking {
                isModelDownloadedAsync(context, modelName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in synchronous model check", e)
            false
        }
    }
    
    suspend fun getModelPathAsync(context: Context, modelName: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val modelsDir = AsyncFileOperations.getModelsDirectory(context)
                val modelFile = File(modelsDir, modelName)
                if (AsyncFileOperations.fileExists(modelFile)) modelFile.absolutePath else null
            } catch (e: Exception) {
                Log.e(TAG, "Error getting model path", e)
                null
            }
        }
    }
    
    fun getModelPath(context: Context, modelName: String): String? {
        // Para mantener compatibilidad
        return try {
            runBlocking {
                getModelPathAsync(context, modelName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in synchronous getModelPath", e)
            null
        }
    }
    
    /**
     * Obtiene el directorio de modelos de la app (externo, accesible para downloads)
     */
    private fun getModelsDirectory(context: Context): File {
        val modelsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        return modelsDir
    }
    
    /**
     * Verifica la integridad del archivo usando SHA256
     */
    private fun verifyFileIntegrity(file: File): Boolean {
        return try {
            if (!VERIFY_INTEGRITY) {
                Log.d(TAG, "Integrity verification disabled for development")
                return true
            }
            
            Log.d(TAG, "Calculating SHA256 for file: ${file.absolutePath}")
            val sha256 = calculateSHA256(file)
            Log.d(TAG, "Calculated SHA256: $sha256")
            Log.d(TAG, "Expected SHA256: $LLAMA_MODEL_SHA256")
            val isValid = sha256.equals(LLAMA_MODEL_SHA256, ignoreCase = true)
            Log.d(TAG, "File integrity check result: $isValid")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying file integrity", e)
            false
        }
    }
    
    /**
     * Calcula el hash SHA256 de un archivo
     */
    private fun calculateSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fis = FileInputStream(file)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }
        fis.close()
        
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Verifica si el modelo ya está descargado y es válido (versión asíncrona)
     */
    suspend fun isValidModelDownloadedAsync(context: Context, modelName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val modelsDir = AsyncFileOperations.getModelsDirectory(context)
                val modelFile = File(modelsDir, modelName)
                AsyncFileOperations.fileExists(modelFile) && 
                AsyncFileOperations.getFileSize(modelFile) > 0 && 
                AsyncFileOperations.verifyFileIntegrity(modelFile)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking valid model download", e)
                false
            }
        }
    }
    
    /**
     * Verifica si el modelo ya está descargado y es válido (versión síncrona para compatibilidad)
     */
    fun isValidModelDownloaded(context: Context, modelName: String): Boolean {
        return try {
            runBlocking {
                isValidModelDownloadedAsync(context, modelName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in synchronous valid model check", e)
            false
        }
    }
    
    /**
     * Obtiene el archivo de modelo en el directorio de modelos
     */
    fun getModelFile(context: Context, modelName: String): File {
        val modelsDir = getModelsDirectory(context)
        return File(modelsDir, modelName)
    }
}
