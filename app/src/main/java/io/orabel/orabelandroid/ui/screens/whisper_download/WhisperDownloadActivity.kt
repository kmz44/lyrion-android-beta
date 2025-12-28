package io.orabel.orabelandroid.ui.screens.whisper_download

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orabel.orabelandroid.ui.screens.whisper_stt.WhisperSttActivity
import io.orabel.orabelandroid.ui.theme.OrabelAndroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.URL

data class WhisperModel(
    val name: String,
    val fileName: String,
    val url: String,
    val sizeMB: Long,
    val description: String
)

class WhisperDownloadActivity : ComponentActivity() {
    companion object {
        private const val TAG = "WhisperDownload"
        
        // Modelos disponibles - Verificados en HuggingFace 2025-10-05
        val MODELS = listOf(
            WhisperModel(
                name = "Whisper Base (Multilingüe)",
                fileName = "whisper-base.TOP_WORLD.tflite",
                url = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-base.TOP_WORLD.tflite",
                sizeMB = 108,
                description = "Recomendado - Equilibrio entre precisión y rendimiento"
            ),
            WhisperModel(
                name = "Whisper Small (Multilingüe)",
                fileName = "whisper-small.TOP_WORLD.tflite",
                url = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-small.TOP_WORLD.tflite",
                sizeMB = 307,
                description = "Alta precisión - Requiere más recursos"
            ),
            WhisperModel(
                name = "Whisper Tiny (Solo inglés)",
                fileName = "whisper-tiny.en.tflite",
                url = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-tiny.en.tflite",
                sizeMB = 42,
                description = "Ligero y rápido - Solo inglés"
            )
        )

        fun areModelsDownloaded(activity: ComponentActivity): Boolean {
            val modelsDir = File(activity.filesDir, "whisper_models")
            // Verificar si al menos un modelo existe
            return MODELS.any { model ->
                val file = File(modelsDir, model.fileName)
                file.exists() && file.length() > 0
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            OrabelAndroidTheme {
                WhisperDownloadScreen(
                    onNavigateBack = { finish() },
                    onDownloadComplete = {
                        // Regresar a WhisperSttActivity después de descargar
                        startActivity(Intent(this, WhisperSttActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperDownloadScreen(
    onNavigateBack: () -> Unit,
    onDownloadComplete: () -> Unit
) {
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedMB by remember { mutableStateOf(0L) }
    var totalMB by remember { mutableStateOf(0L) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var modelsStatus by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    
    val scope = rememberCoroutineScope()
    val activity = androidx.compose.ui.platform.LocalContext.current as WhisperDownloadActivity
    
    // Verificar estado inicial de modelos
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val status = WhisperDownloadActivity.MODELS.associate { model ->
                val modelsDir = File(activity.filesDir, "whisper_models")
                val file = File(modelsDir, model.fileName)
                model.fileName to (file.exists() && file.length() > 0)
            }
            modelsStatus = status
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descarga de Modelos Whisper", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Información general
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "¿Por qué descargar modelos?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Los modelos Whisper permiten reconocimiento de voz de alta precisión sin conexión a Internet. Descarga al menos un modelo para comenzar.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Lista de modelos
            WhisperDownloadActivity.MODELS.forEach { model ->
                ModelCard(
                    model = model,
                    isDownloaded = modelsStatus[model.fileName] == true,
                    isDownloading = isDownloading,
                    onDownload = {
                        scope.launch {
                            isDownloading = true
                            errorMessage = null
                            downloadStatus = "Descargando ${model.name}..."
                            
                            val result = downloadModel(
                                activity = activity,
                                model = model,
                                onProgress = { progress, downloadedBytes, totalBytes ->
                                    downloadProgress = progress
                                    downloadedMB = downloadedBytes / (1024 * 1024)
                                    totalMB = totalBytes / (1024 * 1024)
                                }
                            )
                            
                            isDownloading = false
                            
                            if (result) {
                                modelsStatus = modelsStatus + (model.fileName to true)
                                downloadStatus = "✓ ${model.name} descargado correctamente"
                                
                                // Si ya hay al menos un modelo, permitir continuar
                                if (modelsStatus.values.any { it }) {
                                    kotlinx.coroutines.delay(1000)
                                    onDownloadComplete()
                                }
                            } else {
                                errorMessage = "Error al descargar ${model.name}. Verifica tu conexión."
                                downloadStatus = ""
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Barra de progreso
            if (isDownloading) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = downloadStatus,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = downloadProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$downloadedMB MB / $totalMB MB (${(downloadProgress * 100).toInt()}%)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Mensaje de error
            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Botón continuar (solo si hay al menos un modelo descargado)
            if (modelsStatus.values.any { it } && !isDownloading) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDownloadComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Continuar a Whisper STT",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: WhisperModel,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDownloaded) 
                MaterialTheme.colorScheme.secondaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${model.sizeMB} MB",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (isDownloaded) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Descargado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Descargar")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = model.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

suspend fun downloadModel(
    activity: ComponentActivity,
    model: WhisperModel,
    onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val modelsDir = File(activity.filesDir, "whisper_models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        
        val modelFile = File(modelsDir, model.fileName)
        
        Log.d("WhisperDownload", "Descargando ${model.name} desde ${model.url}")
        
        val url = URL(model.url)
        val connection = url.openConnection()
        connection.readTimeout = 30000
        connection.connectTimeout = 30000
        
        val totalBytes = model.sizeMB * 1024 * 1024
        
        connection.getInputStream().use { input ->
            BufferedInputStream(input, 8192).use { bufferedInput ->
                FileOutputStream(modelFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L
                    var bytesRead: Int
                    
                    while (bufferedInput.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                        withContext(Dispatchers.Main) {
                            onProgress(progress, downloadedBytes, totalBytes)
                        }
                    }
                    output.flush()
                }
            }
        }
        
        // Verificar que el archivo se descargó correctamente
        Log.d("WhisperDownload", "Verificando archivo de ${model.name}")
        
        if (!modelFile.exists() || modelFile.length() == 0L) {
            Log.e("WhisperDownload", "Archivo descargado inválido o vacío")
            modelFile.delete()
            return@withContext false
        }
        
        Log.d("WhisperDownload", "${model.name} descargado correctamente (${modelFile.length()} bytes)")
        true
        
    } catch (e: Exception) {
        Log.e("WhisperDownload", "Error descargando modelo: ${e.message}", e)
        false
    }
}


