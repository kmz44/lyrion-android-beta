package io.orabel.orabelandroid.whisper

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.channels.FileChannel

/**
 * Motor REAL de Whisper usando TensorFlow Lite
 * Implementación completa de transcripción de voz a texto
 */
class WhisperEngine(private val context: Context) {
    
    companion object {
        private const val TAG = "WhisperEngine"
        
        // Nombres de modelos descargados
        const val MODEL_BASE = "whisper-base.TOP_WORLD.tflite"
        const val MODEL_SMALL = "whisper-small.TOP_WORLD.tflite"
        const val MODEL_TINY = "whisper-tiny.en.tflite"
        
        /**
         * Obtiene el primer modelo disponible en el directorio de modelos
         */
        fun getAvailableModelPath(context: Context): File? {
            val modelsDir = File(context.filesDir, "whisper_models")
            if (!modelsDir.exists()) return null
            
            // Priorizar Base > Tiny > Small
            val preferredOrder = listOf(MODEL_BASE, MODEL_TINY, MODEL_SMALL)
            
            for (modelName in preferredOrder) {
                val modelFile = File(modelsDir, modelName)
                if (modelFile.exists() && modelFile.length() > 0) {
                    Log.d(TAG, "✅ Modelo encontrado: ${modelFile.name} (${modelFile.length() / 1024 / 1024} MB)")
                    return modelFile
                }
            }
            
            return null
        }
    }
    
    private val whisperUtil = WhisperUtil()
    
    @Volatile
    private var initialized = false
    
    private var currentModelPath: String = ""
    private var isMultilingual = true
    private var interpreter: Interpreter? = null
    
    fun isInitialized(): Boolean = initialized
    
    /**
     * Carga el modelo de Whisper REAL
     */
    fun loadModel(modelPath: File, vocabPath: File, multilingual: Boolean) {
        try {
            Log.d(TAG, "📦 Cargando modelo TFLite: ${modelPath.absolutePath}")
            Log.d(TAG, "📚 Cargando vocabulario: ${vocabPath.absolutePath}")
            
            if (!modelPath.exists()) {
                throw Exception("Archivo de modelo no encontrado: ${modelPath.absolutePath}")
            }
            
            if (!vocabPath.exists()) {
                throw Exception("Archivo de vocabulario no encontrado: ${vocabPath.absolutePath}")
            }
            
            isMultilingual = multilingual
            currentModelPath = modelPath.absolutePath
            
            // Cargar modelo TensorFlow Lite
            val fileInputStream = FileInputStream(modelPath)
            val fileChannel = fileInputStream.channel
            val startOffset = 0L
            val declaredLength = fileChannel.size()
            val tfliteModel = fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                startOffset,
                declaredLength
            )
            
            // Configurar intérprete de TensorFlow Lite
            val options = Interpreter.Options().apply {
                setUseXNNPACK(false)  // No se puede usar con tensores dinámicos
                setNumThreads(Runtime.getRuntime().availableProcessors())
                setCancellable(true)
            }
            
            interpreter = Interpreter(tfliteModel, options)
            Log.d(TAG, "✅ Intérprete TFLite creado")
            
            // Cargar filtros y vocabulario
            val vocabLoaded = whisperUtil.loadFiltersAndVocab(multilingual, vocabPath.absolutePath)
            if (!vocabLoaded) {
                throw Exception("Error cargando vocabulario")
            }
            
            initialized = true
            Log.d(TAG, "✅ Motor Whisper cargado exitosamente")
            
            // Log de signatures disponibles
            val signatures = interpreter?.signatureKeys
            Log.d(TAG, "📝 Signatures disponibles: ${signatures?.joinToString()}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando modelo", e)
            interpreter?.close()
            interpreter = null
            initialized = false
            throw e
        }
    }
    
    /**
     * Descarga el modelo
     */
    fun unloadModel() {
        try {
            Log.d(TAG, "🗑️ Descargando modelo")
            interpreter?.setCancelled(true)
            interpreter?.close()
            interpreter = null
            initialized = false
            currentModelPath = ""
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando modelo", e)
        }
    }
    
    /**
     * Procesa el buffer de audio grabado - IMPLEMENTACIÓN REAL
     */
    fun processRecordBuffer(action: WhisperAction, languageToken: Int): WhisperResult? {
        if (!initialized || interpreter == null) {
            Log.e(TAG, "Motor no inicializado")
            return null
        }
        
        try {
            Log.d(TAG, "🎯 Procesando audio - Acción: ${action.name}, Idioma token: $languageToken")
            
            // 1. Obtener muestras de audio del RecordBuffer
            val samples = RecordBuffer.getSamples()
            Log.d(TAG, "📊 Muestras obtenidas: ${samples.size}")
            
            // 2. Preparar entrada de tamaño fijo
            val fixedInputSize = WhisperUtil.WHISPER_SAMPLE_RATE * WhisperUtil.WHISPER_CHUNK_SIZE
            val inputSamples = FloatArray(fixedInputSize)
            val copyLength = minOf(samples.size, fixedInputSize)
            System.arraycopy(samples, 0, inputSamples, 0, copyLength)
            
            // 3. Calcular espectrograma Mel
            Log.d(TAG, "🔬 Calculando espectrograma Mel...")
            val cores = Runtime.getRuntime().availableProcessors()
            val melSpectrogram = whisperUtil.getMelSpectrogram(
                inputSamples,
                fixedInputSize,
                copyLength,
                cores
            )
            Log.d(TAG, "✅ Espectrograma Mel calculado: ${melSpectrogram.size} valores")
            
            // 4. Ejecutar inferencia
            val result = runInference(melSpectrogram, action, languageToken)
            Log.d(TAG, "✅ Inferencia completada: ${result.text}")
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando audio", e)
            return null
        }
    }
    
    /**
     * Ejecuta la inferencia del modelo TFLite - IMPLEMENTACIÓN REAL
     */
    private fun runInference(
        melSpectrogram: FloatArray,
        action: WhisperAction,
        languageToken: Int
    ): WhisperResult {
        val interp = interpreter ?: throw IllegalStateException("Intérprete no inicializado")
        
        try {
            // Log de signatures disponibles
            val signatures = interp.signatureKeys
            Log.d(TAG, "📝 Signatures: ${signatures.joinToString()}")
            
            // Determinar signature key a usar
            var signatureKey = "serving_default"
            
            when (action) {
                WhisperAction.TRANSLATE -> {
                    if ("serving_translate" in signatures) {
                        signatureKey = "serving_translate"
                    }
                }
                WhisperAction.TRANSCRIBE -> {
                    if ("serving_transcribe_lang" in signatures && languageToken != -1) {
                        signatureKey = "serving_transcribe_lang"
                    } else if ("serving_transcribe" in signatures) {
                        signatureKey = "serving_transcribe"
                    }
                }
            }
            
            Log.d(TAG, "🔑 Usando signature: $signatureKey")
            
            // Obtener tensores de entrada
            val inputTensor = interp.getInputTensor(0)
            val inputShape = inputTensor.shape()
            Log.d(TAG, "📥 Input shape: ${inputShape.joinToString()}")
            
            // Crear buffer de entrada
            val inputSize = inputShape[0] * inputShape[1] * inputShape[2] * Float.SIZE_BYTES
            val inputBuffer = ByteBuffer.allocateDirect(inputSize).apply {
                order(ByteOrder.nativeOrder())
                melSpectrogram.forEach { putFloat(it) }
                rewind()
            }
            
            // Crear mapa de entradas
            val inputs = interp.getSignatureInputs(signatureKey)
            val inputsMap = mutableMapOf<String, Any>()
            inputsMap[inputs[0]] = inputBuffer
            
            // Si es transcribe_lang, agregar token de idioma
            if (signatureKey == "serving_transcribe_lang") {
                Log.d(TAG, "🌐 Agregando language token: $languageToken")
                val langTokenBuffer = IntBuffer.allocate(1).apply {
                    put(languageToken)
                    rewind()
                }
                inputsMap[inputs[1]] = langTokenBuffer
            }
            
            // Crear tensor de salida
            val outputTensor = interp.getOutputTensor(0)
            val outputBuffer = TensorBuffer.createFixedSize(
                outputTensor.shape(),
                DataType.FLOAT32
            )
            
            // Crear mapa de salidas
            val outputs = interp.getSignatureOutputs(signatureKey)
            val outputsMap = mutableMapOf<String, Any>()
            outputsMap[outputs[0]] = outputBuffer.buffer
            
            // EJECUTAR INFERENCIA REAL
            Log.d(TAG, "🚀 Ejecutando inferencia...")
            interp.runSignature(inputsMap, outputsMap, signatureKey)
            Log.d(TAG, "✅ Inferencia completada")
            
            // Procesar resultados
            val resultBytes = mutableListOf<ByteArray>()
            var detectedLanguage = ""
            var detectedTask: WhisperAction? = null
            
            val outputLen = outputBuffer.intArray.size
            Log.d(TAG, "📤 Output length: $outputLen")
            
            outputBuffer.buffer.rewind()
            
            for (i in 0 until outputLen) {
                val token = outputBuffer.buffer.int
                
                // Fin de transcripción
                if (token == whisperUtil.getTokenEOT()) {
                    Log.d(TAG, "🛑 EOT token encontrado en posición $i")
                    break
                }
                
                // Token normal de palabra
                if (token < whisperUtil.getTokenEOT()) {
                    whisperUtil.getWordFromToken(token)?.let { wordBytes ->
                        resultBytes.add(wordBytes)
                    }
                } else {
                    // Tokens especiales
                    when (token) {
                        whisperUtil.getTokenTranscribe() -> {
                            Log.d(TAG, "📝 Token TRANSCRIBE detectado")
                            detectedTask = WhisperAction.TRANSCRIBE
                        }
                        whisperUtil.getTokenTranslate() -> {
                            Log.d(TAG, "🌍 Token TRANSLATE detectado")
                            detectedTask = WhisperAction.TRANSLATE
                        }
                        in 50259..50357 -> {
                            // Token de idioma
                            detectedLanguage = getLanguageFromToken(token)
                            Log.d(TAG, "🗣️ Idioma detectado: $detectedLanguage (token: $token)")
                        }
                        else -> {
                            val word = whisperUtil.getWordFromToken(token)
                            if (word != null) {
                                Log.d(TAG, "⏭️ Saltando token: $token, palabra: ${String(word, Charsets.UTF_8)}")
                            }
                        }
                    }
                }
            }
            
            // Combinar bytes en texto final
            val totalLength = resultBytes.sumOf { it.size }
            val combinedBytes = ByteArray(totalLength)
            var offset = 0
            resultBytes.forEach { byteArray ->
                System.arraycopy(byteArray, 0, combinedBytes, offset, byteArray.size)
                offset += byteArray.size
            }
            
            val transcription = String(combinedBytes, Charsets.UTF_8)
            Log.d(TAG, "📄 Transcripción: $transcription")
            
            return WhisperResult(
                text = transcription,
                language = detectedLanguage.ifEmpty { if (isMultilingual) "auto" else "en" },
                action = detectedTask ?: action
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en inferencia", e)
            return WhisperResult(
                text = "",
                language = "",
                action = action
            )
        }
    }
    
    /**
     * Mapea token de idioma a código ISO
     */
    private fun getLanguageFromToken(token: Int): String {
        return when (token) {
            50259 -> "en"  // English
            50260 -> "zh"  // Chinese
            50261 -> "de"  // German
            50262 -> "es"  // Spanish
            50263 -> "ru"  // Russian
            50264 -> "ko"  // Korean
            50265 -> "fr"  // French
            50266 -> "ja"  // Japanese
            50267 -> "pt"  // Portuguese
            50268 -> "tr"  // Turkish
            50269 -> "pl"  // Polish
            50270 -> "ca"  // Catalan
            50271 -> "nl"  // Dutch
            50272 -> "ar"  // Arabic
            50273 -> "sv"  // Swedish
            50274 -> "it"  // Italian
            50275 -> "id"  // Indonesian
            50276 -> "hi"  // Hindi
            50277 -> "fi"  // Finnish
            50278 -> "vi"  // Vietnamese
            50279 -> "he"  // Hebrew
            50280 -> "uk"  // Ukrainian
            50281 -> "el"  // Greek
            50282 -> "ms"  // Malay
            50283 -> "cs"  // Czech
            50284 -> "ro"  // Romanian
            50285 -> "da"  // Danish
            50286 -> "hu"  // Hungarian
            50287 -> "ta"  // Tamil
            50288 -> "no"  // Norwegian
            50289 -> "th"  // Thai
            50290 -> "ur"  // Urdu
            50291 -> "hr"  // Croatian
            50292 -> "bg"  // Bulgarian
            50293 -> "lt"  // Lithuanian
            50294 -> "la"  // Latin
            50295 -> "mi"  // Maori
            50296 -> "ml"  // Malayalam
            50297 -> "cy"  // Welsh
            50298 -> "sk"  // Slovak
            50299 -> "te"  // Telugu
            50300 -> "fa"  // Persian
            50301 -> "lv"  // Latvian
            50302 -> "bn"  // Bengali
            50303 -> "sr"  // Serbian
            50304 -> "az"  // Azerbaijani
            50305 -> "sl"  // Slovenian
            50306 -> "kn"  // Kannada
            50307 -> "et"  // Estonian
            50308 -> "mk"  // Macedonian
            50309 -> "br"  // Breton
            50310 -> "eu"  // Basque
            50311 -> "is"  // Icelandic
            50312 -> "hy"  // Armenian
            50313 -> "ne"  // Nepali
            50314 -> "mn"  // Mongolian
            50315 -> "bs"  // Bosnian
            50316 -> "kk"  // Kazakh
            50317 -> "sq"  // Albanian
            50318 -> "sw"  // Swahili
            50319 -> "gl"  // Galician
            50320 -> "mr"  // Marathi
            50321 -> "pa"  // Punjabi
            50322 -> "si"  // Sinhala
            50323 -> "km"  // Khmer
            50324 -> "sn"  // Shona
            50325 -> "yo"  // Yoruba
            50326 -> "so"  // Somali
            50327 -> "af"  // Afrikaans
            50328 -> "oc"  // Occitan
            50329 -> "ka"  // Georgian
            50330 -> "be"  // Belarusian
            50331 -> "tg"  // Tajik
            50332 -> "sd"  // Sindhi
            50333 -> "gu"  // Gujarati
            50334 -> "am"  // Amharic
            50335 -> "yi"  // Yiddish
            50336 -> "lo"  // Lao
            50337 -> "uz"  // Uzbek
            50338 -> "fo"  // Faroese
            50339 -> "ht"  // Haitian Creole
            50340 -> "ps"  // Pashto
            50341 -> "tk"  // Turkmen
            50342 -> "nn"  // Nynorsk
            50343 -> "mt"  // Maltese
            50344 -> "sa"  // Sanskrit
            50345 -> "lb"  // Luxembourgish
            50346 -> "my"  // Myanmar
            50347 -> "bo"  // Tibetan
            50348 -> "tl"  // Tagalog
            50349 -> "mg"  // Malagasy
            50350 -> "as"  // Assamese
            50351 -> "tt"  // Tatar
            50352 -> "haw" // Hawaiian
            50353 -> "ln"  // Lingala
            50354 -> "ha"  // Hausa
            50355 -> "ba"  // Bashkir
            50356 -> "jw"  // Javanese
            50357 -> "su"  // Sundanese
            else -> "auto"
        }
    }
    
    fun getCurrentModelPath(): String = currentModelPath
}
