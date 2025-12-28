package io.orabel.orabelandroid.ui.screens.ialive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.orabel.orabel.Orabel
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.llm.ModelsRepository
import io.orabel.orabelandroid.ui.components.ModernCard
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.utils.SafeToast
import com.k2fsa.sherpa.onnx.tts.engine.TtsEngine
import com.k2fsa.sherpa.onnx.tts.engine.PreferenceHelper
import com.k2fsa.sherpa.onnx.tts.engine.LangDB
import com.k2fsa.sherpa.onnx.tts.engine.ManageLanguagesActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import org.koin.android.ext.android.inject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.util.Locale

class IALiveActivity : ComponentActivity(), RecognitionListener {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val modelsRepository by inject<ModelsRepository>()
    
    // Voice Recognition
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isListening by mutableStateOf(false)
    private var recognizedText by mutableStateOf("")
    private var partialText by mutableStateOf("") // Texto parcial en vivo
    private var isInitialized by mutableStateOf(false)
    private var isLoading by mutableStateOf(true)
    private var errorMessage by mutableStateOf("")
    
    // LLM Integration - Real Orabel implementation
    private var orabel: Orabel? = null
    private var isLLMLoaded by mutableStateOf(false)
    private var llmResponse by mutableStateOf("")
    private var isProcessingLLM by mutableStateOf(false)
    
    // TTS Integration con Sherpa
    private var ttsTrack: AudioTrack? = null
    private var ttsSamplesChannel = Channel<FloatArray>()
    private var ttsStopped: Boolean = false
    private lateinit var ttsPrefHelper: PreferenceHelper
    private lateinit var ttsLangDB: LangDB
    private var isTTSReady by mutableStateOf(false)
    private var isSpeaking by mutableStateOf(false)

    // Hotword / manos libres
    private var hotwordEnabled by mutableStateOf(true)
    private var startKeyword by mutableStateOf("kevin")
    private var stopKeyword by mutableStateOf("finaliza")
    private var isConversationActive by mutableStateOf(false)
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeVosk()
            initializeLLM()
            initializeSherpaTTS()
        } else {
            errorMessage = "❌ Se requiere permiso de micrófono para funcionar"
            isLoading = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Inicializar dependencias TTS Sherpa
        ttsPrefHelper = PreferenceHelper(this)
        ttsLangDB = LangDB.getInstance(this)
        
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                // Cargar preferencias de hotword
                hotwordEnabled = orabelPreferences.isHotwordEnabled()
                startKeyword = orabelPreferences.getStartKeyword()
                stopKeyword = orabelPreferences.getStopKeyword()
                if (stopKeyword == "finalisa") {
                    stopKeyword = "finaliza"
                    orabelPreferences.setStopKeyword("finaliza")
                }
                initializeVosk()
                initializeLLM()
                initializeSherpaTTS()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        
        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                IALiveScreen(
                    isListening = isListening,
                    recognizedText = recognizedText,
                    partialText = partialText,
                    isInitialized = isInitialized,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    isLLMLoaded = isLLMLoaded,
                    llmResponse = llmResponse,
                    isProcessingLLM = isProcessingLLM,
                    isTTSReady = isTTSReady,
                    isSpeaking = isSpeaking,
                    hotwordEnabled = hotwordEnabled,
                    startKeyword = startKeyword,
                    stopKeyword = stopKeyword,
                    isConversationActive = isConversationActive,
                    onStartListening = { startListening() },
                    onStopListening = { stopListening() },
                    onToggleHotword = { 
                        hotwordEnabled = it
                        orabelPreferences.setHotwordEnabled(it)
                    },
                    onChangeStartKeyword = { 
                        val v = it.lowercase(Locale.getDefault())
                        startKeyword = v
                        orabelPreferences.setStartKeyword(v)
                    },
                    onChangeStopKeyword = { 
                        val v = it.lowercase(Locale.getDefault())
                        stopKeyword = v
                        orabelPreferences.setStopKeyword(v)
                    },
                    onClearText = { 
                        recognizedText = ""
                        partialText = ""
                        llmResponse = ""
                    },
                    onBackClick = { finish() }
                )
            }
        }
    }
    
    private fun initializeVosk() {
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                isLoading = true
            }
            
            try {
                LibVosk.setLogLevel(LogLevel.INFO)
                
                // Copiar modelo desde assets al directorio interno de la app
                val modelDir = File(filesDir, "models/vosk-model-small-es-0.42")
                
                if (!modelDir.exists() || !isModelComplete(modelDir)) {
                    withContext(Dispatchers.Main) {
                        // Mostrar progreso
                        SafeToast.show(this@IALiveActivity, "🔄 Preparando modelo de voz...")
                    }
                    
                    copyModelFromAssets("vosk-model-small-es-0.42", modelDir)
                }
                
                // Cargar el modelo desde el directorio interno
                val model = Model(modelDir.absolutePath)
                
                withContext(Dispatchers.Main) {
                    this@IALiveActivity.model = model
                    isInitialized = true
                    isLoading = false
                    SafeToast.show(this@IALiveActivity, "✅ IA Live - Modelo de voz OFFLINE cargado")
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "❌ Error al inicializar IA Live: ${e.message}"
                    isLoading = false
                }
            }
        }
    }
    
    private fun isModelComplete(modelDir: File): Boolean {
        val requiredFiles = listOf(
            "am/final.mdl",
            "graph/HCLG.fst",
            "conf/mfcc.conf",
            "ivector/final.ie"
        )
        
        return requiredFiles.all { File(modelDir, it).exists() }
    }
    
    private fun copyModelFromAssets(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        
        fun copyAssetFolder(assetPath: String, targetPath: String) {
            val assetManager = assets
            val files = assetManager.list(assetPath) ?: return
            
            val targetDirFile = File(targetPath)
            targetDirFile.mkdirs()
            
            for (file in files) {
                val assetFile = "$assetPath/$file"
                val targetFile = File(targetDirFile, file)
                
                val subFiles = assetManager.list(assetFile)
                if (subFiles != null && subFiles.isNotEmpty()) {
                    // Es un directorio, recursión
                    copyAssetFolder(assetFile, targetFile.absolutePath)
                } else {
                    // Es un archivo, copiarlo
                    assetManager.open(assetFile).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        
        copyAssetFolder(assetPath, targetDir.absolutePath)
    }

    private fun initializeLLM() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Obtener el modelo seleccionado
                val selectedModelId = orabelPreferences.getSelectedModelId()
                
                if (selectedModelId == -1L) {
                    withContext(Dispatchers.Main) {
                        SafeToast.show(this@IALiveActivity, "⚠️ No hay modelo LLM seleccionado")
                        isLLMLoaded = false
                    }
                    return@launch
                }
                
                val modelInfo = modelsRepository.getModelFromId(selectedModelId)
                if (modelInfo == null) {
                    withContext(Dispatchers.Main) {
                        SafeToast.show(this@IALiveActivity, "❌ Modelo LLM no encontrado")
                        isLLMLoaded = false
                    }
                    return@launch
                }
                
                // Crear instancia de Orabel y cargar el modelo
                orabel = Orabel()
                orabel?.create(
                    modelPath = modelInfo.path,
                    minP = 0.05f,
                    temperature = 0.8f,
                    storeChats = false
                )
                
                withContext(Dispatchers.Main) {
                    isLLMLoaded = true
                    SafeToast.show(this@IALiveActivity, "🤖 LLM ${modelInfo.name} cargado correctamente")
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    SafeToast.show(this@IALiveActivity, "❌ Error cargando LLM: ${e.message}")
                    isLLMLoaded = false
                }
            }
        }
    }

    private fun initializeSherpaTTS() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) { isTTSReady = false }
                val currentLang = ttsPrefHelper.getCurrentLanguage()
                if (currentLang.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        isTTSReady = false
                        SafeToast.show(this@IALiveActivity, "⚠️ No hay modelo TTS instalado. Abre Gestor de idiomas.")
                        startActivity(android.content.Intent(this@IALiveActivity, ManageLanguagesActivity::class.java))
                    }
                    return@launch
                }
                TtsEngine.createTts(this@IALiveActivity, currentLang)
                if (TtsEngine.tts == null) {
                    withContext(Dispatchers.Main) {
                        isTTSReady = false
                        SafeToast.show(this@IALiveActivity, "❌ Error cargando modelo TTS")
                    }
                    return@launch
                }
                initAudioTrackForTts()
                withContext(Dispatchers.Main) {
                    isTTSReady = true
                    SafeToast.show(this@IALiveActivity, "✅ Motor TTS OFFLINE listo")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isTTSReady = false
                    SafeToast.show(this@IALiveActivity, "❌ Error inicializando TTS: ${e.message}")
                }
            }
        }
    }

    private fun initAudioTrackForTts() {
        val tts = TtsEngine.tts ?: return
        val sampleRate = tts.sampleRate()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val bufLength = (minBuf * 4).coerceAtLeast(minBuf)
        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        ttsTrack = AudioTrack(attr, format, bufLength, AudioTrack.MODE_STREAM, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE)
        ttsTrack?.play()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun speakWithSherpa(text: String) {
        if (!isTTSReady || isSpeaking) return
        val engine = TtsEngine.tts ?: run {
            SafeToast.show(this, "❌ Motor TTS no listo")
            return
        }
        ttsStopped = false
        ttsTrack?.pause()
        ttsTrack?.flush()
        ttsTrack?.play()
        ttsSamplesChannel = Channel()

        // Consumidor de muestras
        CoroutineScope(Dispatchers.IO).launch {
            for (samples in ttsSamplesChannel) {
                ttsTrack?.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            }
        }

        isSpeaking = true
        CoroutineScope(Dispatchers.Default).launch {
            engine.generateWithCallback(
                text = text,
                sid = TtsEngine.speakerId,
                speed = TtsEngine.speed,
                callback = ::sherpaCallback
            )
            withContext(Dispatchers.Main) {
                isSpeaking = false
                // Auto-reinicio para nueva pregunta
                if (isListening) {
                    recognizedText = ""
                    partialText = ""
                    llmResponse = ""
                    if (hotwordEnabled) {
                        isConversationActive = false
                        val hint = if (startKeyword.isNotBlank()) startKeyword else "palabra clave"
                        SafeToast.show(this@IALiveActivity, "🟢 Di '$hint' para empezar")
                    } else {
                        isConversationActive = true
                        SafeToast.show(this@IALiveActivity, "🎤 Dime tu siguiente pregunta…")
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun sherpaCallback(samples: FloatArray): Int {
        return if (!ttsStopped) {
            val copy = samples.copyOf()
            CoroutineScope(Dispatchers.IO).launch {
                if (!ttsSamplesChannel.isClosedForSend) ttsSamplesChannel.send(copy)
            }
            1
        } else {
            try { ttsTrack?.stop() } catch (_: Exception) {}
            0
        }
    }
    
    private fun startListening() {
        if (!isInitialized || model == null) {
            SafeToast.show(this, "❌ El modelo de voz no está cargado")
            return
        }
        
        try {
            // Iniciar reconocimiento de voz
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
            isListening = true
            // Preparar flujo según hotword
            if (hotwordEnabled) {
                isConversationActive = false
                recognizedText = ""
                partialText = ""
                llmResponse = ""
                val hint = if (startKeyword.isNotBlank()) startKeyword else "palabra clave"
                SafeToast.show(this, "🟢 Di '$hint' para empezar")
            } else {
                isConversationActive = true
                recognizedText = ""
                partialText = ""
                llmResponse = ""
                SafeToast.show(this, "🎤 Escuchando tu pregunta…")
            }
        } catch (e: Exception) {
            SafeToast.show(this, "❌ Error al iniciar reconocimiento: ${e.message}")
        }
    }
    
    private fun stopListening() {
        speechService?.stop()
        speechService = null
        isListening = false
    isConversationActive = false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        model?.close()
        
        // Clean up LLM - Real implementation
        CoroutineScope(Dispatchers.IO).launch {
            try {
                orabel?.close()
                orabel = null
                isLLMLoaded = false
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        
        // Clean up TTS
        ttsTrack?.let { track ->
            track.stop()
            track.release()
        }
        ttsTrack = null
    }
    
    // RecognitionListener implementation for OFFLINE Vosk con Hotword
    override fun onPartialResult(hypothesis: String?) {
        hypothesis?.let { result ->
            try {
                val jsonResult = JSONObject(result)
                val partial = jsonResult.optString("partial", "")
                if (partial.isNotEmpty()) {
                    val lower = partial.lowercase(Locale.getDefault())
                    // Detectar inicio por hotword
                    if (hotwordEnabled && !isConversationActive && startKeyword.isNotBlank() && lower.contains(startKeyword)) {
                        isConversationActive = true
                        recognizedText = ""
                        partialText = ""
                        llmResponse = ""
                        SafeToast.show(this, "🎙️ Escuchando…")
                        return
                    }
                    // Detectar fin por hotword
                    if (isConversationActive && stopKeyword.isNotBlank() && lower.contains(stopKeyword)) {
                        val finalQuery = recognizedText.trim()
                        partialText = ""
                        isConversationActive = false
                        if (finalQuery.isNotEmpty() && isLLMLoaded) {
                            processWithLLM(finalQuery)
                        }
                        return
                    }
                    // Solo mostrar parcial cuando estamos en conversación activa
                    if (isConversationActive) {
                        partialText = partial
                    } else {
                        partialText = ""
                    }
                }
            } catch (_: Exception) { }
        }
    }
    
    override fun onResult(hypothesis: String?) {
        hypothesis?.let { result ->
            try {
                val jsonResult = JSONObject(result)
                val text = jsonResult.optString("text", "")
                if (text.isNotEmpty()) {
                    val lower = text.lowercase(Locale.getDefault())
                    // Activar conversación si llega hotword en resultado final y estamos esperando
                    if (hotwordEnabled && !isConversationActive && startKeyword.isNotBlank() && lower.contains(startKeyword)) {
                        isConversationActive = true
                        recognizedText = ""
                        partialText = ""
                        llmResponse = ""
                        return
                    }
                    // Si estamos activos, acumular texto sin palabras clave de fin
                    if (isConversationActive) {
                        var cleaned = text
                        if (stopKeyword.isNotBlank()) {
                            val regex = Regex("\\b${Regex.escape(stopKeyword)}\\b", RegexOption.IGNORE_CASE)
                            cleaned = cleaned.replace(regex, "").trim()
                        }
                        val newText = cleaned.trim()
                        if (newText.isNotEmpty()) {
                            recognizedText = if (recognizedText.isEmpty()) newText else "$recognizedText $newText"
                        }
                        // Si el resultado contiene stopKeyword, finalizar y procesar
                        if (stopKeyword.isNotBlank() && lower.contains(stopKeyword)) {
                            partialText = ""
                            val finalQuery = recognizedText.trim()
                            isConversationActive = false
                            if (finalQuery.isNotEmpty() && isLLMLoaded) {
                                processWithLLM(finalQuery)
                            }
                            return
                        }
                    }
                    // Limpiar texto parcial
                    partialText = ""
                }
            } catch (e: Exception) {
                // Ignorar errores de parsing JSON
            }
        }
    }
    
    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
        val finalText = recognizedText.trim()
        if (!hotwordEnabled) {
            // Modo clásico: procesar siempre al finalizar frase
            if (finalText.isNotEmpty() && isLLMLoaded) {
                isConversationActive = false
                processWithLLM(finalText)
            }
        } else if (isConversationActive) {
            // Con hotword: fallback por pausa natural
            if (finalText.isNotEmpty() && isLLMLoaded) {
                isConversationActive = false
                processWithLLM(finalText)
            }
        }
        // No parar automáticamente para permitir reconocimiento continuo
    }
    
    private fun processWithLLM(inputText: String) {
        if (!isLLMLoaded || isProcessingLLM || orabel == null) return
        
        CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                isProcessingLLM = true
                llmResponse = ""
            }
            
            try {
                // Usar el LLM real de Orabel
                val prompt = "Usuario dice: \"$inputText\". Responde de forma breve, natural y conversacional en español, como si fueras un asistente amigable:"
                
                orabel!!.getResponse(prompt)
                    .catch { error ->
                        withContext(Dispatchers.Main) {
                            SafeToast.show(this@IALiveActivity, "❌ Error LLM: ${error.message}")
                            isProcessingLLM = false
                        }
                    }
                    .collect { responseChunk ->
                        withContext(Dispatchers.Main) {
                            llmResponse += responseChunk
                        }
                    }
                
                // Cuando termine la generación de LLM, convertir a voz
                withContext(Dispatchers.Main) {
                    isProcessingLLM = false
                    val finalResponse = llmResponse.trim()
                    if (finalResponse.isNotEmpty() && isTTSReady) {
                        speakWithSherpa(finalResponse)
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessingLLM = false
                    SafeToast.show(this@IALiveActivity, "❌ Error procesando con LLM: ${e.message}")
                }
            }
        }
    }
    
    override fun onError(exception: Exception?) {
        SafeToast.show(this, "Error en reconocimiento IA Live: ${exception?.message}")
        stopListening()
    }
    
    override fun onTimeout() {
        SafeToast.show(this, "Tiempo de espera agotado - IA Live")
        stopListening()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IALiveScreen(
    isListening: Boolean,
    recognizedText: String,
    partialText: String,
    isInitialized: Boolean,
    isLoading: Boolean,
    errorMessage: String,
    isLLMLoaded: Boolean,
    llmResponse: String,
    isProcessingLLM: Boolean,
    isTTSReady: Boolean,
    isSpeaking: Boolean,
    hotwordEnabled: Boolean,
    startKeyword: String,
    stopKeyword: String,
    isConversationActive: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onToggleHotword: (Boolean) -> Unit,
    onChangeStartKeyword: (String) -> Unit,
    onChangeStopKeyword: (String) -> Unit,
    onClearText: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Volver",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Text(
                    text = "🤖 IA Live",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Encabezado estilo asistente personal
            ModernCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isListening) "Tu asistente está atento" else "Tu asistente offline",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        val status = buildString {
                            append(if (isInitialized) "Voz ✓" else "Voz ••")
                            append("  |  ")
                            append(if (isLLMLoaded) "IA ✓" else "IA ••")
                            append("  |  ")
                            append(if (isTTSReady) "TTS ✓" else "TTS ••")
                        }
                        Text(status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.7f))
                    }
                    AssistControls(
                        isListening = isListening,
                        onStart = onStartListening,
                        onStop = onStopListening
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Estado del sistema
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "🔄 Cargando modelo de voz...",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        errorMessage.isNotEmpty() -> {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.Red,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = errorMessage,
                                fontSize = 14.sp,
                                color = Color.Red,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        isInitialized -> {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.Green,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "✅ IA Live listo",
                                fontSize = 16.sp,
                                color = Color.Green,
                                fontWeight = FontWeight.Medium
                            )
                            
                            // Estado del sistema completo
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Color.Green,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Voz", fontSize = 12.sp)
                                }
                                
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = if (isLLMLoaded) Color.Green else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("LLM", fontSize = 12.sp)
                                }
                                
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = if (isTTSReady) Color.Green else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("TTS", fontSize = 12.sp)
                                }
                            }
                            
                            if (isListening) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (hotwordEnabled && !isConversationActive) "🟢 Esperando palabra clave…" else if (isConversationActive) "�️ Escuchando…" else "�🎤 Escuchando…",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            if (isProcessingLLM) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "🤖 Procesando con IA...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            if (isSpeaking) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "🔊 Reproduciendo voz...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Controles
            AssistSettings(
                hotwordEnabled = hotwordEnabled,
                startKeyword = startKeyword,
                stopKeyword = stopKeyword,
                onToggleHotword = onToggleHotword,
                onChangeStartKeyword = onChangeStartKeyword,
                onChangeStopKeyword = onChangeStopKeyword,
                onClearText = onClearText,
                isInitialized = isInitialized,
                isListening = isListening,
                onStart = onStartListening,
                onStop = onStopListening,
                recognizedTextNotEmpty = recognizedText.isNotEmpty()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Texto reconocido
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.TextFields,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Texto en Tiempo Real",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        if (recognizedText.isEmpty() && partialText.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🎤 El texto aparecerá aquí en tiempo real mientras hablas...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            val listState = rememberLazyListState()
                            
                            // Desplazamiento automático al final cuando hay nuevo texto
                            LaunchedEffect(recognizedText, partialText) {
                                if (recognizedText.isNotEmpty() || partialText.isNotEmpty()) {
                                    listState.animateScrollToItem(0) // Siempre mostrar el último elemento
                                }
                            }
                            
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                reverseLayout = true // Mostrar el contenido más nuevo abajo
                            ) {
                                // Texto parcial en vivo (se muestra al final)
                                if (partialText.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = partialText,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            lineHeight = 24.sp,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            ),
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                                
                                // Texto confirmado (se muestra primero)
                                if (recognizedText.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = recognizedText,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 24.sp,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Respuesta de IA
            if (llmResponse.isNotEmpty() || isProcessingLLM) {
                ModernCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Respuesta de IA",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            
                            if (isProcessingLLM) {
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.secondary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            if (llmResponse.isEmpty() && isProcessingLLM) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🤖 Generando respuesta de IA...",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else if (llmResponse.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                ) {
                                    item {
                                        Text(
                                            text = llmResponse,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 24.sp,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Indicador de TTS
                        if (isSpeaking) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🔊 Reproduciendo respuesta...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Información
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "IA Live - Conversación Completa",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Text(
                        text = "• Funciona completamente OFFLINE\n" +
                                "• Reconocimiento en español en tiempo real\n" +
                                "• Procesamiento con IA local (LLM integrado)\n" +
                                "• Síntesis de voz automática (TTS)\n" +
                                "• No requiere conexión a internet\n" +
                                "• Conversación natural: habla → IA → respuesta hablada\n" +
                                "• Usa tu modelo LLM seleccionado en Lyrion\n" +
                                "• Solo visualización - NO guarda conversaciones",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistControls(
    isListening: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val onColor = MaterialTheme.colorScheme.primary
        val offColor = Color.Red
        Button(
            onClick = if (isListening) onStop else onStart,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isListening) offColor else onColor
            )
        ) {
            Icon(
                if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(if (isListening) "Parar" else "Iniciar")
        }
    }
}

@Composable
private fun AssistSettings(
    hotwordEnabled: Boolean,
    startKeyword: String,
    stopKeyword: String,
    onToggleHotword: (Boolean) -> Unit,
    onChangeStartKeyword: (String) -> Unit,
    onChangeStopKeyword: (String) -> Unit,
    onClearText: () -> Unit,
    isInitialized: Boolean,
    isListening: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    recognizedTextNotEmpty: Boolean
) {
    ModernCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SettingsVoice, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Modo manos libres", fontWeight = FontWeight.SemiBold)
                }
                Switch(checked = hotwordEnabled, onCheckedChange = onToggleHotword)
            }
            if (hotwordEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = startKeyword,
                        onValueChange = onChangeStartKeyword,
                        label = { Text("Palabra de inicio") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = stopKeyword,
                        onValueChange = onChangeStopKeyword,
                        label = { Text("Palabra de fin") },
                        leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = if (isListening) onStop else onStart,
                    enabled = isInitialized,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) Color.Red else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isListening) "Parar" else "Iniciar", fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = onClearText,
                    enabled = recognizedTextNotEmpty,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Limpiar")
                }
            }
        }
    }
}
