package io.orabel.orabelandroid.ui.screens.whisper_stt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.components.ModernCard
import io.orabel.orabelandroid.ui.screens.whisper_download.WhisperDownloadActivity
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.utils.SafeToast
import io.orabel.orabelandroid.whisper.WhisperAction
import io.orabel.orabelandroid.whisper.WhisperSttRepository
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Actividad para reconocimiento de voz avanzado con Whisper
 * Motor más preciso y sofisticado que Vosk
 */
class WhisperSttActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private lateinit var whisperRepository: WhisperSttRepository
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadDefaultModel()
        } else {
            SafeToast.show(this, "❌ Se requiere permiso de micrófono")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Verificar si hay modelos descargados
        if (!WhisperDownloadActivity.areModelsDownloaded(this)) {
            // No hay modelos, redirigir a descarga
            startActivity(Intent(this, WhisperDownloadActivity::class.java))
            finish()
            return
        }
        
        whisperRepository = WhisperSttRepository(this)
        
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadDefaultModel()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        
        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(darkTheme = isDarkTheme) {
                WhisperSttScreen(
                    repository = whisperRepository,
                    onBackClick = { finish() }
                )
            }
        }
    }
    
    private fun loadDefaultModel() {
        lifecycleScope.launch {
            // Intentar cargar el modelo por defecto
            val modelLoaded = whisperRepository.loadModel(
                modelName = WhisperSttRepository.MODEL_BASE_ES,
                isMultilingual = true
            )
            
            if (!modelLoaded) {
                SafeToast.show(
                    this@WhisperSttActivity,
                    "ℹ️ Descarga el modelo Whisper para comenzar"
                )
            } else {
                // Configurar español por defecto
                whisperRepository.setLanguage("es")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::whisperRepository.isInitialized) {
            whisperRepository.shutdown()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhisperSttScreen(
    repository: WhisperSttRepository,
    onBackClick: () -> Unit
) {
    val isListening by repository.isListening.collectAsState()
    val isProcessing by repository.isProcessing.collectAsState()
    val recognizedText by repository.recognizedText.collectAsState()
    val partialResults by repository.partialResults.collectAsState()
    val detectedLanguage by repository.detectedLanguage.collectAsState()
    val error by repository.error.collectAsState()
    val isAvailable by repository.isAvailable.collectAsState()
    
    var selectedAction by remember { mutableStateOf(WhisperAction.TRANSCRIBE) }
    var selectedLanguage by remember { mutableStateOf("es") }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🎙️ Whisper STT",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            "Reconocimiento avanzado de voz",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Estado del motor
            StatusCard(
                isAvailable = isAvailable,
                isListening = isListening,
                isProcessing = isProcessing
            )
            
            // Configuración
            ConfigurationCard(
                selectedAction = selectedAction,
                selectedLanguage = selectedLanguage,
                onActionChanged = { action ->
                    selectedAction = action
                    repository.setAction(action)
                },
                onLanguageChanged = { language ->
                    selectedLanguage = language
                    repository.setLanguage(language)
                }
            )
            
            // Controles de grabación
            RecordingControls(
                isListening = isListening,
                isProcessing = isProcessing,
                isAvailable = isAvailable,
                onStartListening = { repository.startListening() },
                onStopListening = { repository.stopListening() },
                onCancelListening = { repository.cancelListening() }
            )
            
            // Resultados
            ResultsCard(
                recognizedText = recognizedText,
                partialResults = partialResults,
                detectedLanguage = detectedLanguage,
                error = error,
                onClearText = { repository.clearText() }
            )
            
            // Información
            InfoCard()
        }
    }
}

@Composable
fun StatusCard(
    isAvailable: Boolean,
    isListening: Boolean,
    isProcessing: Boolean
) {
    ModernCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Estado del Motor",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = PrimaryColor
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Motor Whisper:")
                StatusChip(
                    text = if (isAvailable) "✅ Listo" else "❌ No disponible",
                    color = if (isAvailable) Color(0xFF10B981) else Color(0xFFEF4444)
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Estado:")
                StatusChip(
                    text = when {
                        isProcessing -> "🔄 Procesando"
                        isListening -> "🎤 Escuchando"
                        else -> "⏸️ Inactivo"
                    },
                    color = when {
                        isProcessing -> Color(0xFF8B5CF6)
                        isListening -> Color(0xFFFF6B6B)
                        else -> Color.Gray
                    }
                )
            }
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = color
        )
    }
}

@Composable
fun ConfigurationCard(
    selectedAction: WhisperAction,
    selectedLanguage: String,
    onActionChanged: (WhisperAction) -> Unit,
    onLanguageChanged: (String) -> Unit
) {
    ModernCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "⚙️ Configuración",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = PrimaryColor
            )
            
            // Acción
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Modo de operación:",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionButton(
                        text = "📝 Transcribir",
                        isSelected = selectedAction == WhisperAction.TRANSCRIBE,
                        onClick = { onActionChanged(WhisperAction.TRANSCRIBE) },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButton(
                        text = "🌐 Traducir",
                        isSelected = selectedAction == WhisperAction.TRANSLATE,
                        onClick = { onActionChanged(WhisperAction.TRANSLATE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Idioma (solo si es transcripción)
            if (selectedAction == WhisperAction.TRANSCRIBE) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Idioma de entrada:",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageButton(
                            text = "🇪🇸 ES",
                            isSelected = selectedLanguage == "es",
                            onClick = { onLanguageChanged("es") }
                        )
                        LanguageButton(
                            text = "🇬🇧 EN",
                            isSelected = selectedLanguage == "en",
                            onClick = { onLanguageChanged("en") }
                        )
                        LanguageButton(
                            text = "🌍 Auto",
                            isSelected = selectedLanguage == "auto",
                            onClick = { onLanguageChanged("auto") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) PrimaryColor else Color.Gray.copy(alpha = 0.3f),
            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 13.sp)
    }
}

@Composable
fun LanguageButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PrimaryColor.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) PrimaryColor else Color.Gray.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (isSelected) PrimaryColor else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun RecordingControls(
    isListening: Boolean,
    isProcessing: Boolean,
    isAvailable: Boolean,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onCancelListening: () -> Unit
) {
    ModernCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                isListening -> {
                    // Botón de detener
                    Button(
                        onClick = onStopListening,
                        modifier = Modifier
                            .size(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Detener",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Text(
                        "Detener grabación",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                isProcessing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(100.dp),
                        color = PrimaryColor,
                        strokeWidth = 8.dp
                    )
                    Text(
                        "Procesando con Whisper...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                else -> {
                    // Botón de iniciar
                    Button(
                        onClick = onStartListening,
                        enabled = isAvailable,
                        modifier = Modifier.size(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Grabar",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Text(
                        if (isAvailable) "Toca para hablar" else "Motor no disponible",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
            
            if (isListening) {
                TextButton(onClick = onCancelListening) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
fun ResultsCard(
    recognizedText: String,
    partialResults: String,
    detectedLanguage: String,
    error: String?,
    onClearText: () -> Unit
) {
    ModernCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📝 Resultados",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = PrimaryColor
                )
                
                if (recognizedText.isNotEmpty()) {
                    IconButton(onClick = onClearText) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Limpiar",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            }
            
            if (error != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFEF4444)
                        )
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }
            
            if (partialResults.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        partialResults,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryColor
                    )
                }
            }
            
            if (recognizedText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (detectedLanguage.isNotEmpty()) {
                            Text(
                                "🌐 Idioma detectado: $detectedLanguage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Text(
                            recognizedText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (partialResults.isEmpty() && error == null) {
                Text(
                    "Los resultados aparecerán aquí...",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun InfoCard() {
    ModernCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "ℹ️ Acerca de Whisper STT",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = PrimaryColor
            )
            
            Text(
                "• Motor de reconocimiento de voz avanzado basado en OpenAI Whisper",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "• Mayor precisión que otros motores para dispositivos de gama media-alta",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "• Soporta múltiples idiomas y traducción automática",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "• Requiere descarga de modelos (~150-435 MB según el modelo)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
