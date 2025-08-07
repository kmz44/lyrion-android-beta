package io.orabel.orabelandroid.ui.screens.ialive

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

class IALiveActivity : ComponentActivity(), RecognitionListener, TextToSpeech.OnInitListener {
    
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
    
    // TTS Integration  
    private var tts: TextToSpeech? = null
    private var isTTSReady by mutableStateOf(false)
    private var isSpeaking by mutableStateOf(false)
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeVosk()
            initializeLLM()
        } else {
            errorMessage = "❌ Se requiere permiso de micrófono para funcionar"
            isLoading = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize TTS
        tts = TextToSpeech(this, this)
        
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                initializeVosk()
                initializeLLM()
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
                    onStartListening = { startListening() },
                    onStopListening = { stopListening() },
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

    // TextToSpeech.OnInitListener implementation
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { textToSpeech ->
                val result = textToSpeech.setLanguage(Locale("es", "ES"))
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to default language if Spanish is not available
                    textToSpeech.setLanguage(Locale.getDefault())
                }
                
                // Set TTS parameters for better quality
                textToSpeech.setSpeechRate(0.9f)
                textToSpeech.setPitch(1.0f)
                
                // Set utterance progress listener
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeaking = true
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        isSpeaking = false
                    }
                    
                    override fun onError(utteranceId: String?) {
                        isSpeaking = false
                        SafeToast.show(this@IALiveActivity, "❌ Error en síntesis de voz")
                    }
                })
                
                isTTSReady = true
                SafeToast.show(this, "✅ Síntesis de voz lista")
            }
        } else {
            isTTSReady = false
            SafeToast.show(this, "❌ Error inicializando síntesis de voz")
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
            SafeToast.show(this, "🎤 IA Live - Reconocimiento OFFLINE iniciado")
        } catch (e: Exception) {
            SafeToast.show(this, "❌ Error al iniciar reconocimiento: ${e.message}")
        }
    }
    
    private fun stopListening() {
        speechService?.stop()
        speechService = null
        isListening = false
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
        tts?.let { textToSpeech ->
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        tts = null
    }
    
    // RecognitionListener implementation for OFFLINE Vosk
    override fun onPartialResult(hypothesis: String?) {
        // Mostrar resultados parciales en tiempo real
        hypothesis?.let { result ->
            try {
                val jsonResult = JSONObject(result)
                val partial = jsonResult.optString("partial", "")
                if (partial.isNotEmpty()) {
                    partialText = partial
                }
            } catch (e: Exception) {
                // Ignorar errores de parsing JSON
            }
        }
    }
    
    override fun onResult(hypothesis: String?) {
        hypothesis?.let { result ->
            try {
                val jsonResult = JSONObject(result)
                val text = jsonResult.optString("text", "")
                if (text.isNotEmpty()) {
                    val newText = text.trim()
                    recognizedText = if (recognizedText.isEmpty()) {
                        newText
                    } else {
                        "$recognizedText $newText"
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
        
        // Procesar con LLM cuando el usuario deje de hablar
        val finalText = recognizedText.trim()
        if (finalText.isNotEmpty() && isLLMLoaded) {
            processWithLLM(finalText)
        }
        
        // No parar automáticamente para permitir reconocimiento continuo
        // stopListening()
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
                        speakText(finalResponse)
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
    
    private fun speakText(text: String) {
        if (!isTTSReady || isSpeaking) return
        
        tts?.let { textToSpeech ->
            val utteranceId = "TTS_${System.currentTimeMillis()}"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            
            val result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                SafeToast.show(this, "❌ Error en síntesis de voz")
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
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
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
                                    text = "🎤 Escuchando...",
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = if (isListening) onStopListening else onStartListening,
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
                    Text(
                        if (isListening) "Parar" else "Iniciar",
                        fontWeight = FontWeight.Medium
                    )
                }
                
                OutlinedButton(
                    onClick = onClearText,
                    enabled = recognizedText.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Limpiar")
                }
            }

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
