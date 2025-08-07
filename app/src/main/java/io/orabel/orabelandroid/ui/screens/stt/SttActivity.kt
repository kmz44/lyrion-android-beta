package io.orabel.orabelandroid.ui.screens.stt

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.components.ModernCard
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.utils.SafeToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class SttActivity : ComponentActivity(), RecognitionListener {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null
    private var isListening by mutableStateOf(false)
    private var isRecording by mutableStateOf(false)
    private var recognizedText by mutableStateOf("")
    private var partialText by mutableStateOf("") // Texto parcial en vivo
    private var isInitialized by mutableStateOf(false)
    private var isLoading by mutableStateOf(true)
    private var errorMessage by mutableStateOf("")
    private var currentSessionId by mutableStateOf("")
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioPermissionGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val storagePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
        }
        
        when {
            audioPermissionGranted && storagePermissionGranted -> {
                initializeVosk()
            }
            !audioPermissionGranted -> {
                errorMessage = "❌ Se requiere permiso de micrófono para funcionar"
                isLoading = false
            }
            !storagePermissionGranted -> {
                errorMessage = "❌ Se requiere permiso de almacenamiento para guardar archivos"
                isLoading = false
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && 
            hasStoragePermission() -> {
                initializeVosk()
            }
            else -> {
                requestPermissions()
            }
        }
        
        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                SttScreen(
                    isListening = isListening,
                    isRecording = isRecording,
                    recognizedText = recognizedText,
                    partialText = partialText,
                    isInitialized = isInitialized,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    storageStats = getStorageStats(),
                    onStartListening = { startListening() },
                    onStopListening = { stopListening() },
                    onClearText = { 
                        recognizedText = ""
                        partialText = ""
                    },
                    onSaveSession = { saveCurrentSession() },
                    onBackClick = { finish() }
                )
            }
        }
    }
    
    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // Para Android 11+, necesitamos dirigir al usuario a la configuración
                SafeToast.show(this, "⚠️ Se requiere acceso completo a archivos. Redirigiendo a configuración...")
                try {
                    val intent = android.content.Intent().apply {
                        action = android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                        data = android.net.Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    SafeToast.show(this, "❌ Error al abrir configuración: ${e.message}")
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
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
                        SafeToast.show(this@SttActivity, "🔄 Preparando modelo de voz...")
                    }
                    
                    copyModelFromAssets("vosk-model-small-es-0.42", modelDir)
                }
                
                // Cargar el modelo desde el directorio interno
                val model = Model(modelDir.absolutePath)
                
                withContext(Dispatchers.Main) {
                    this@SttActivity.model = model
                    isInitialized = true
                    isLoading = false
                    SafeToast.show(this@SttActivity, "✅ Modelo de voz OFFLINE cargado correctamente")
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "❌ Error al inicializar Vosk: ${e.message}"
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

    private fun startListening() {
        if (!isInitialized || model == null) {
            SafeToast.show(this, "❌ El modelo de voz no está cargado")
            return
        }
        
        try {
            // Generar ID único para esta sesión
            currentSessionId = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            
            // Iniciar grabación de audio
            startAudioRecording()
            
            // Iniciar reconocimiento de voz
            val recognizer = Recognizer(model, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(this)
            isListening = true
            SafeToast.show(this, "🎤 Reconocimiento OFFLINE iniciado - Habla ahora")
        } catch (e: Exception) {
            SafeToast.show(this, "❌ Error al iniciar reconocimiento: ${e.message}")
        }
    }
    
    private fun stopListening() {
        speechService?.stop()
        speechService = null
        isListening = false
        
        // Detener grabación de audio
        stopAudioRecording()
    }
    
    private fun startAudioRecording() {
        if (!hasStoragePermission()) {
            SafeToast.show(this, "⚠️ Sin permisos de almacenamiento - Solo se guardará el texto")
            return
        }
        
        try {
            val audioDir = getDownloadsAudioDir()
            audioDir.mkdirs()
            
            if (!audioDir.exists()) {
                throw IOException("No se pudo crear directorio de audio: ${audioDir.absolutePath}")
            }
            
            audioFilePath = File(audioDir, "audio_$currentSessionId.m4a").absolutePath
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFilePath)
                prepare()
                start()
            }
            
            isRecording = true
            SafeToast.show(this, "🔴 Grabación de audio iniciada")
            
        } catch (e: Exception) {
            SafeToast.show(this, "❌ Error grabación audio: ${e.message}")
            audioFilePath = null
            isRecording = false
        }
    }
    
    private fun stopAudioRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
        } catch (e: Exception) {
            // Ignorar errores al detener grabación
        }
    }
    
    // Métodos para obtener directorios en Downloads
    private fun getDownloadsDir(): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: Usar la carpeta Downloads pública
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LyrionVozTexto")
        } else {
            // Android 9 y anteriores
            File(Environment.getExternalStorageDirectory(), "Download/LyrionVozTexto")
        }
    }
    
    private fun getDownloadsTextDir(): File {
        return File(getDownloadsDir(), "Transcripciones")
    }
    
    private fun getDownloadsAudioDir(): File {
        return File(getDownloadsDir(), "Audio")
    }
    
    private fun getDownloadsSessionsDir(): File {
        return File(getDownloadsDir(), "SesionesCompletas")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        stopAudioRecording()
        model?.close()
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
                    
                    // Guardar automáticamente cada frase
                    saveTranscriptionToLocal(newText)
                    
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
        // Guardar sesión completa al final
        if (recognizedText.isNotEmpty()) {
            saveFullSessionToLocal()
        }
        // No parar automáticamente para permitir reconocimiento continuo
        // stopListening()
    }
    
    override fun onError(exception: Exception?) {
        SafeToast.show(this, "Error en reconocimiento offline: ${exception?.message}")
        stopListening()
    }
    
    override fun onTimeout() {
        SafeToast.show(this, "Tiempo de espera agotado - Reconocimiento offline")
        stopListening()
    }
    
    // Métodos de guardado local en Downloads
    private fun saveTranscriptionToLocal(text: String) {
        if (text.isBlank() || !hasStoragePermission()) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transcriptionsDir = getDownloadsTextDir()
                transcriptionsDir.mkdirs()
                
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val fileName = "transcripciones_${SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date())}.txt"
                val file = File(transcriptionsDir, fileName)
                
                // Agregar la frase con timestamp
                val entry = "[$timestamp] $text\n"
                file.appendText(entry)
                
            } catch (e: Exception) {
                println("Error guardando transcripción: ${e.message}")
            }
        }
    }
    
    private fun saveFullSessionToLocal() {
        if (recognizedText.isBlank() || !hasStoragePermission()) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessionsDir = getDownloadsSessionsDir()
                sessionsDir.mkdirs()
                
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val fileName = "sesion_$timestamp.txt"
                val file = File(sessionsDir, fileName)
                
                val sessionHeader = "=== SESIÓN DE VOZ ===\n"
                val sessionTimestamp = "Fecha: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n"
                val sessionLength = "Longitud: ${recognizedText.length} caracteres\n\n"
                val sessionContent = "TRANSCRIPCIÓN COMPLETA:\n$recognizedText\n\n"
                val sessionFooter = "=== FIN DE SESIÓN ==="
                
                file.writeText(sessionHeader + sessionTimestamp + sessionLength + sessionContent + sessionFooter)
                
            } catch (e: Exception) {
                println("Error guardando sesión completa: ${e.message}")
            }
        }
    }
    
    // Método para obtener estadísticas de uso
    private fun getStorageStats(): String {
        return try {
            val transcriptionsDir = getDownloadsTextDir()
            val sessionsDir = getDownloadsSessionsDir()
            val audioDir = getDownloadsAudioDir()
            
            val transcriptionFiles = transcriptionsDir.listFiles()?.size ?: 0
            val sessionFiles = sessionsDir.listFiles()?.size ?: 0
            val audioFiles = audioDir.listFiles()?.size ?: 0
            val totalSize = (transcriptionsDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() +
                           sessionsDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum() +
                           audioDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()) / 1024 // KB
            
            "� DOWNLOADS/LyrionVozTexto: $transcriptionFiles texto | $sessionFiles sesiones | $audioFiles audios | ${totalSize}KB"
        } catch (e: Exception) {
            "� Downloads: No disponible"
        }
    }
    
    // Método para guardar sesión completa con audio y texto en Downloads
    private fun saveCurrentSession() {
        if (recognizedText.isBlank()) {
            SafeToast.show(this, "❌ No hay texto para guardar")
            return
        }
        
        // Verificar permisos antes de guardar
        if (!hasStoragePermission()) {
            SafeToast.show(this, "❌ Sin permisos de almacenamiento. Solicitando permisos...")
            requestPermissions()
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    SafeToast.show(this@SttActivity, "🔄 Guardando sesión...")
                }
                
                val downloadsRoot = getDownloadsDir()
                val sessionsDir = getDownloadsSessionsDir()
                
                // Crear directorios
                downloadsRoot.mkdirs()
                sessionsDir.mkdirs()
                
                // Verificar que se crearon los directorios
                if (!sessionsDir.exists()) {
                    throw IOException("No se pudo crear el directorio: ${sessionsDir.absolutePath}")
                }
                
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                
                // Guardar archivo de texto
                val textFile = File(sessionsDir, "sesion_${currentSessionId}.txt")
                val sessionHeader = "=== SESIÓN COMPLETA DE VOZ Y TEXTO ===\n"
                val sessionTimestamp = "Fecha: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n"
                val sessionLength = "Longitud: ${recognizedText.length} caracteres\n"
                val audioInfo = if (audioFilePath != null && File(audioFilePath!!).exists()) {
                    "Audio: Downloads/LyrionVozTexto/Audio/audio_${currentSessionId}.m4a\n"
                } else {
                    "Audio: No disponible\n"
                }
                val locationInfo = "Ubicación: ${textFile.absolutePath}\n"
                val sessionContent = "\nTRANSCRIPCIÓN COMPLETA:\n$recognizedText\n\n"
                val sessionFooter = "=== FIN DE SESIÓN ==="
                
                val content = sessionHeader + sessionTimestamp + sessionLength + audioInfo + locationInfo + sessionContent + sessionFooter
                textFile.writeText(content)
                
                // Verificar que el archivo se escribió
                if (!textFile.exists() || textFile.length() == 0L) {
                    throw IOException("Error al escribir el archivo de texto")
                }
                
                withContext(Dispatchers.Main) {
                    val audioStatus = if (audioFilePath != null && File(audioFilePath!!).exists()) {
                        val audioFile = File(audioFilePath!!)
                        "Audio: ${audioFile.length() / 1024}KB"
                    } else {
                        "Audio: No disponible"
                    }
                    
                    SafeToast.show(this@SttActivity, "✅ GUARDADO EXITOSO\n📁 ${textFile.absolutePath}\n📊 Texto: ${recognizedText.length} caracteres\n🎵 $audioStatus")
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    SafeToast.show(this@SttActivity, "❌ ERROR AL GUARDAR: ${e.message}\n📁 Intentando en: ${getDownloadsDir().absolutePath}")
                    
                    // Información de debug
                    val debugInfo = """
                        Error: ${e.javaClass.simpleName}
                        Mensaje: ${e.message}
                        Directorio: ${getDownloadsDir().absolutePath}
                        Permisos: ${hasStoragePermission()}
                        Android: ${Build.VERSION.SDK_INT}
                    """.trimIndent()
                    
                    println("DEBUG GUARDADO: $debugInfo")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SttScreen(
    isListening: Boolean,
    isRecording: Boolean,
    recognizedText: String,
    partialText: String,
    isInitialized: Boolean,
    isLoading: Boolean,
    errorMessage: String,
    storageStats: String,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onClearText: () -> Unit,
    onSaveSession: () -> Unit,
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
                    text = "🎤 Voz a Texto OFFLINE",
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
                                text = "✅ Sistema OFFLINE listo",
                                fontSize = 16.sp,
                                color = Color.Green,
                                fontWeight = FontWeight.Medium
                            )
                            
                            if (isListening) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Column {
                                    Text(
                                        text = "🎤 Escuchando...",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (isRecording) {
                                        Text(
                                            text = "🔴 Grabando audio...",
                                            fontSize = 12.sp,
                                            color = Color.Red,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
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
                    onClick = onSaveSession,
                    enabled = recognizedText.isNotEmpty() && !isListening,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar")
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
                            text = "Texto Reconocido",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp), // Aumentamos altura para más texto
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
                                    text = "🎤 El texto reconocido aparecerá aquí en tiempo real...",
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

            Spacer(modifier = Modifier.height(24.dp))

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
                            text = "Información",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Text(
                        text = "• Funciona completamente OFFLINE\n" +
                                "• Reconocimiento en español en tiempo real\n" +
                                "• No requiere conexión a internet\n" +
                                "• Grabación de audio sin límite de duración\n" +
                                "• Guardado automático en Downloads/LyrionVozTexto\n" +
                                "• Texto en vivo mientras hablas\n" +
                                "• Desplazamiento automático del texto\n" +
                                "• Fácil acceso: Downloads > LyrionVozTexto\n" +
                                "• Carpetas: Audio, Transcripciones, SesionesCompletas",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Estadísticas de almacenamiento
                    Text(
                        text = storageStats,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    )
                }
            }
        }
    }
}
