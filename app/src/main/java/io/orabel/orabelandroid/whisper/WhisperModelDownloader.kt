package io.orabel.orabelandroid.whisper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gestor de descarga de modelos Whisper desde Hugging Face
 */
object WhisperModelDownloader {
    
    private const val TAG = "WhisperModelDownloader"
    
    // URLs de los modelos en Hugging Face
    private const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"
    
    data class ModelInfo(
        val fileName: String,
        val displayName: String,
        val description: String,
        val sizeMB: Int,
        val isMultilingual: Boolean
    )
    
    val AVAILABLE_MODELS = listOf(
        ModelInfo(
            fileName = "ggml-tiny.en.bin",
            displayName = "Tiny (Solo Inglés)",
            description = "Modelo más pequeño, solo inglés, muy rápido",
            sizeMB = 75,
            isMultilingual = false
        ),
        ModelInfo(
            fileName = "ggml-base.bin",
            displayName = "Base (Multiidioma)",
            description = "Modelo balanceado, multiidioma, buena velocidad",
            sizeMB = 142,
            isMultilingual = true
        ),
        ModelInfo(
            fileName = "ggml-small.bin",
            displayName = "Small (Multiidioma)",
            description = "Modelo más preciso, multiidioma, más lento",
            sizeMB = 466,
            isMultilingual = true
        )
    )
    
    /**
     * Verifica si un modelo ya está descargado
     */
    fun isModelDownloaded(context: Context, modelFileName: String): Boolean {
        val modelFile = File(context.filesDir, "whisper_models/$modelFileName")
        return modelFile.exists() && modelFile.length() > 0
    }
    
    /**
     * Obtiene el tamaño de un modelo descargado
     */
    fun getModelSize(context: Context, modelFileName: String): Long {
        val modelFile = File(context.filesDir, "whisper_models/$modelFileName")
        return if (modelFile.exists()) modelFile.length() else 0
    }
    
    /**
     * Descarga un modelo de Whisper
     */
    suspend fun downloadModel(
        context: Context,
        modelInfo: ModelInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val modelsDir = File(context.filesDir, "whisper_models")
            modelsDir.mkdirs()
            
            val outputFile = File(modelsDir, modelInfo.fileName)
            
            // Si ya existe, retornar
            if (outputFile.exists() && outputFile.length() > 0) {
                Log.d(TAG, "Modelo ya descargado: ${modelInfo.fileName}")
                return@withContext Result.success(outputFile)
            }
            
            val url = URL(BASE_URL + modelInfo.fileName)
            Log.d(TAG, "Descargando desde: $url")
            
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()
            
            val fileLength = connection.contentLength
            
            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    var count: Int
                    
                    while (input.read(buffer).also { count = it } != -1) {
                        total += count
                        output.write(buffer, 0, count)
                        
                        // Actualizar progreso
                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt()
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "✅ Modelo descargado exitosamente: ${modelInfo.fileName}")
            Result.success(outputFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error descargando modelo", e)
            Result.failure(e)
        }
    }
    
    /**
     * Elimina un modelo descargado
     */
    fun deleteModel(context: Context, modelFileName: String): Boolean {
        return try {
            val modelFile = File(context.filesDir, "whisper_models/$modelFileName")
            if (modelFile.exists()) {
                modelFile.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando modelo", e)
            false
        }
    }
    
    /**
     * Obtiene el espacio disponible en el dispositivo
     */
    fun getAvailableStorageGB(context: Context): Double {
        val filesDir = context.filesDir
        val availableBytes = filesDir.usableSpace
        return availableBytes / (1024.0 * 1024.0 * 1024.0)
    }
    
    /**
     * Obtiene todos los modelos descargados
     */
    fun getDownloadedModels(context: Context): List<ModelInfo> {
        return AVAILABLE_MODELS.filter { isModelDownloaded(context, it.fileName) }
    }
}

/**
 * NOTA IMPORTANTE:
 * 
 * Para una implementación completa de descarga de modelos, considera:
 * 
 * 1. Los modelos de Whisper son grandes (75MB - 466MB)
 * 2. La descarga debe ser resiliente (manejar interrupciones, reanudar)
 * 3. Mostrar progreso detallado al usuario
 * 4. Verificar checksum/hash del archivo descargado
 * 5. Considerar usar WorkManager para descargas en background
 * 6. Permitir pausar/reanudar descargas
 * 
 * Este código es una base funcional. Para producción, considera usar
 * bibliotecas especializadas como:
 * - Android DownloadManager
 * - WorkManager de Jetpack
 * - Bibliotecas como PRDownloader
 */
