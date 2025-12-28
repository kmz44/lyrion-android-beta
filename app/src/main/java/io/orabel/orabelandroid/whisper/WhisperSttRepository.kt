package io.orabel.orabelandroid.whisper

import android.content.Context
import android.util.Log
import io.orabel.orabelandroid.utils.DeviceResourcesHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.lang.ref.WeakReference

/**
 * Repositorio para el reconocimiento de voz avanzado con Whisper
 * Implementación más precisa y sofisticada que Vosk
 * Compatible con dispositivos de gama media-alta
 */
class WhisperSttRepository(context: Context) {
    
    companion object {
        private const val TAG = "WhisperSttRepository"
        
        // Modelos disponibles (nombres REALES de los archivos descargados)
        const val MODEL_TINY_EN = "whisper-tiny.en.tflite"  // Solo inglés, rápido
        const val MODEL_BASE = "whisper-base.TOP_WORLD.tflite"  // Multiidioma, balanceado
        const val MODEL_SMALL = "whisper-small.TOP_WORLD.tflite"  // Multiidioma, más preciso
        const val MODEL_BASE_ES = "whisper-base.EUROPEAN_UNION.tflite"  // Optimizado para español
        
        const val VOCAB_EN = "filters_vocab_en.bin"
        const val VOCAB_MULTILINGUAL = "filters_vocab_multilingual.bin"
    }
    
    private val contextRef = WeakReference(context)
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Estado
    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()
    
    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()
    
    private val _partialResults = MutableStateFlow("")
    val partialResults: StateFlow<String> = _partialResults.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    private val _detectedLanguage = MutableStateFlow("")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()
    
    // Componentes
    private var whisperRecorder: WhisperRecorder? = null
    private var whisperEngine: WhisperEngine? = null
    private var currentAction = WhisperAction.TRANSCRIBE
    private var currentLanguageToken = -1 // -1 = detección automática
    
    private var isDestroyed = false
    private val deviceCapabilities: DeviceResourcesHelper.DeviceCapabilities
    
    init {
        val ctx = contextRef.get()
        deviceCapabilities = ctx?.let { DeviceResourcesHelper.analyzeDeviceCapabilities(it) }
            ?: DeviceResourcesHelper.DeviceCapabilities(
                isLowEndDevice = true,
                isLowMemoryDevice = true,
                totalMemoryMB = 512,
                recommendedTtsSettings = DeviceResourcesHelper.TtsOptimizationSettings(
                    useCompactVoice = true,
                    reducedSpeechRate = 0.9f,
                    reducedPitch = 1.0f,
                    enableMemoryOptimization = true,
                    maxTextChunkSize = 100,
                    useSimpleEngine = true
                )
            )
        
        Log.d(TAG, "🎤 Inicializando Whisper STT Repository")
        Log.d(TAG, "📱 Dispositivo: ${if (deviceCapabilities.isLowEndDevice) "Gama Baja" else "Gama Media/Alta"}")
        initializeWhisper()
    }
    
    private fun initializeWhisper() {
        val context = contextRef.get() ?: return
        
        try {
            whisperRecorder = WhisperRecorder(context)
            whisperRecorder?.setListener(object : WhisperRecorder.RecorderListener {
                override fun onUpdateReceived(message: String) {
                    _partialResults.value = message
                }
                
                override fun onRecordingComplete(audioData: ByteArray) {
                    _isListening.value = false
                    processAudio()
                }
            })
            
            whisperEngine = WhisperEngine(context)
            
            _isAvailable.value = true
            Log.d(TAG, "✅ Whisper STT inicializado correctamente")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando Whisper", e)
            _error.value = "Error inicializando Whisper: ${e.message}"
            _isAvailable.value = false
        }
    }
    
    /**
     * Carga un modelo específico de Whisper
     */
    fun loadModel(modelName: String = MODEL_BASE_ES, isMultilingual: Boolean = true): Boolean {
        val context = contextRef.get() ?: return false
        
        return try {
            // Intentar obtener modelo automáticamente si es el default
            val modelPath = if (modelName == MODEL_BASE_ES) {
                WhisperEngine.getAvailableModelPath(context) ?: getModelPath(context, modelName)
            } else {
                getModelPath(context, modelName)
            }
            
            val vocabFileName = if (isMultilingual) VOCAB_MULTILINGUAL else VOCAB_EN
            val vocabPath = getVocabPath(context, vocabFileName)
            
            if (!modelPath.exists()) {
                _error.value = "Modelo no encontrado. Por favor, descarga un modelo primero."
                return false
            }
            
            // Copiar vocabulario desde assets si no existe
            if (!vocabPath.exists()) {
                Log.d(TAG, "📂 Copiando vocabulario desde assets: $vocabFileName")
                vocabPath.parentFile?.mkdirs()
                
                try {
                    context.assets.open(vocabFileName).use { input ->
                        vocabPath.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "✅ Vocabulario copiado: ${vocabPath.name} (${vocabPath.length()} bytes)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error copiando vocabulario desde assets", e)
                    _error.value = "Error: Archivo de vocabulario no encontrado"
                    return false
                }
            }
            
            _partialResults.value = "Cargando modelo Whisper..."
            whisperEngine?.loadModel(modelPath, vocabPath, isMultilingual)
            _partialResults.value = "✅ Modelo cargado: ${modelPath.name}"
            Log.d(TAG, "✅ Modelo Whisper cargado: ${modelPath.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando modelo", e)
            _error.value = "Error cargando modelo: ${e.message}"
            false
        }
    }
    
    /**
     * Configura el idioma para la transcripción
     */
    fun setLanguage(languageCode: String) {
        currentLanguageToken = when (languageCode) {
            "es" -> 50262  // Español
            "en" -> 50259  // Inglés
            "fr" -> 50265  // Francés
            "de" -> 50261  // Alemán
            "it" -> 50274  // Italiano
            "pt" -> 50286  // Portugués
            else -> -1  // Auto-detección
        }
        Log.d(TAG, "🌐 Idioma configurado: $languageCode (token: $currentLanguageToken)")
    }
    
    /**
     * Configura la acción (transcribir o traducir)
     */
    fun setAction(action: WhisperAction) {
        currentAction = action
        Log.d(TAG, "🎯 Acción configurada: ${action.name}")
    }
    
    /**
     * Inicia la grabación y reconocimiento
     */
    fun startListening() {
        if (isDestroyed || _isListening.value) {
            Log.w(TAG, "No se puede iniciar - destruido: $isDestroyed, escuchando: ${_isListening.value}")
            return
        }
        
        if (whisperEngine?.isInitialized() != true) {
            _error.value = "Modelo Whisper no cargado. Carga un modelo primero."
            return
        }
        
        try {
            Log.d(TAG, "🎤 Iniciando reconocimiento Whisper...")
            _error.value = null
            _recognizedText.value = ""
            _partialResults.value = ""
            _detectedLanguage.value = ""
            _isListening.value = true
            
            whisperRecorder?.start()
            Log.d(TAG, "✅ Grabación iniciada")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando grabación", e)
            _error.value = "Error iniciando grabación: ${e.message}"
            _isListening.value = false
        }
    }
    
    /**
     * Detiene la grabación
     */
    fun stopListening() {
        try {
            Log.d(TAG, "🛑 Deteniendo grabación")
            whisperRecorder?.stop()
            _partialResults.value = "Procesando audio..."
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo grabación", e)
            _isListening.value = false
        }
    }
    
    /**
     * Procesa el audio grabado con Whisper
     */
    private fun processAudio() {
        if (_isProcessing.value) {
            Log.w(TAG, "Ya hay un procesamiento en curso")
            return
        }
        
        coroutineScope.launch {
            try {
                _isProcessing.value = true
                _partialResults.value = "🔄 Procesando con Whisper..."
                
                val result = whisperEngine?.processRecordBuffer(currentAction, currentLanguageToken)
                
                withContext(Dispatchers.Main) {
                    if (result != null) {
                        _recognizedText.value = result.text
                        _detectedLanguage.value = result.language
                        _partialResults.value = ""
                        _error.value = null
                        
                        Log.d(TAG, "✅ Reconocimiento completado")
                        Log.d(TAG, "📝 Texto: ${result.text}")
                        Log.d(TAG, "🌐 Idioma detectado: ${result.language}")
                    } else {
                        _error.value = "No se pudo procesar el audio"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error procesando audio", e)
                withContext(Dispatchers.Main) {
                    _error.value = "Error procesando audio: ${e.message}"
                    _partialResults.value = ""
                }
            } finally {
                _isProcessing.value = false
            }
        }
    }
    
    /**
     * Cancela la grabación actual
     */
    fun cancelListening() {
        try {
            Log.d(TAG, "❌ Cancelando reconocimiento")
            whisperRecorder?.stop()
            _isListening.value = false
            _recognizedText.value = ""
            _partialResults.value = ""
            _error.value = null
            RecordBuffer.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelando", e)
            _isListening.value = false
        }
    }
    
    /**
     * Limpia el texto reconocido
     */
    fun clearText() {
        _recognizedText.value = ""
        _partialResults.value = ""
        _error.value = null
        _detectedLanguage.value = ""
    }
    
    /**
     * Información de optimización
     */
    fun getOptimizationInfo(): String {
        return buildString {
            append("Motor Whisper - Reconocimiento avanzado\n")
            append("Dispositivo: ${if (deviceCapabilities.isLowEndDevice) "Gama Baja" else "Gama Media/Alta"}\n")
            append("RAM total: ${deviceCapabilities.totalMemoryMB} MB\n")
            append("Memoria baja: ${deviceCapabilities.isLowMemoryDevice}\n")
            append("Acción actual: ${currentAction.name}\n")
            append("Idioma: ${if (currentLanguageToken == -1) "Auto" else currentLanguageToken}")
        }
    }
    
    /**
     * Cierra y libera recursos
     */
    fun shutdown() {
        if (isDestroyed) return
        
        Log.d(TAG, "🔄 Cerrando Whisper repository")
        isDestroyed = true
        
        try {
            whisperRecorder?.shutdown()
            whisperEngine?.unloadModel()
            coroutineScope.cancel()
            RecordBuffer.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error durante shutdown", e)
        } finally {
            _isListening.value = false
            _isProcessing.value = false
            _error.value = null
        }
    }
    
    private fun getModelPath(context: Context, modelName: String): File {
        return File(context.filesDir, "whisper_models/$modelName")
    }
    
    private fun getVocabPath(context: Context, vocabName: String): File {
        return File(context.filesDir, "whisper_models/$vocabName")
    }
}
