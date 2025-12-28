package io.orabel.orabelandroid.ui.screens.realtime_translator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Traductor en tiempo real con Whisper
 * Transcribe audio y traduce automáticamente al idioma seleccionado
 */
class RealtimeTranslatorActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private lateinit var whisperRepository: WhisperSttRepository
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadTranslationModel()
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
                loadTranslationModel()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        
        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(darkTheme = isDarkTheme) {
                RealtimeTranslatorScreen(
                    repository = whisperRepository,
                    onBackClick = { finish() }
                )
            }
        }
    }
    
    private fun loadTranslationModel() {
        lifecycleScope.launch {
            // Configurar para traducción automática
            whisperRepository.setAction(WhisperAction.TRANSLATE)
            
            val modelLoaded = whisperRepository.loadModel(
                modelName = WhisperSttRepository.MODEL_BASE,
                isMultilingual = true
            )
            
            if (!modelLoaded) {
                SafeToast.show(
                    this@RealtimeTranslatorActivity,
                    "ℹ️ Descarga el modelo Whisper para comenzar a traducir"
                )
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

data class TranslationEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val originalText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String = "en"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealtimeTranslatorScreen(
    repository: WhisperSttRepository,
    onBackClick: () -> Unit
) {
    val isListening by repository.isListening.collectAsState()
    val isProcessing by repository.isProcessing.collectAsState()
    val recognizedText by repository.recognizedText.collectAsState()
    val detectedLanguage by repository.detectedLanguage.collectAsState()
    val error by repository.error.collectAsState()
    val isAvailable by repository.isAvailable.collectAsState()
    
    var translations by remember { mutableStateOf<List<TranslationEntry>>(emptyList()) }
    var isContinuousMode by remember { mutableStateOf(false) }
    
    // Agregar traducción cuando se complete el reconocimiento
    // Whisper en modo TRANSLATE traduce automáticamente a inglés
    LaunchedEffect(recognizedText) {
        if (recognizedText.isNotEmpty() && !isProcessing) {
            val newTranslation = TranslationEntry(
                originalText = recognizedText,
                translatedText = recognizedText, // Whisper ya traduce automáticamente en modo TRANSLATE
                sourceLanguage = detectedLanguage.ifEmpty { "auto" },
                targetLanguage = "en" // Whisper siempre traduce a inglés
            )
            translations = listOf(newTranslation) + translations
            
            // En modo continuo, reiniciar automáticamente
            if (isContinuousMode && isAvailable) {
                delay(500)
                repository.clearText()
                delay(500)
                repository.startListening()
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🌐 Traductor en Tiempo Real",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            "Con tecnología Whisper",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { translations = emptyList() }
                    ) {
                        Icon(Icons.Default.Delete, "Limpiar historial")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF8B5CF6),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundColor)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Controles superiores
            ControlsCard(
                isListening = isListening,
                isProcessing = isProcessing,
                isAvailable = isAvailable,
                isContinuousMode = isContinuousMode,
                onToggleContinuous = { isContinuousMode = !isContinuousMode },
                onStartListening = { repository.startListening() },
                onStopListening = { repository.stopListening() }
            )
            
            // Error si existe
            error?.let { errorMsg ->
                ErrorBanner(errorMsg)
            }
            
            // Estado actual
            if (isListening || isProcessing) {
                CurrentStatusCard(
                    isListening = isListening,
                    isProcessing = isProcessing
                )
            }
            
            // Historial de traducciones
            TranslationsHistory(
                translations = translations,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ControlsCard(
    isListening: Boolean,
    isProcessing: Boolean,
    isAvailable: Boolean,
    isContinuousMode: Boolean,
    onToggleContinuous: () -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit
) {
    ModernCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Información importante sobre traducción
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "ℹ️ Traducción Automática",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        )
                        Text(
                            "Whisper traduce automáticamente a inglés cualquier idioma hablado. No requiere configuración adicional.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }
            
            Divider()
            
            // Modo continuo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Modo Continuo",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        "Traducción automática sin parar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Switch(
                    checked = isContinuousMode,
                    onCheckedChange = { onToggleContinuous() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF8B5CF6),
                        checkedTrackColor = Color(0xFF8B5CF6).copy(alpha = 0.5f)
                    )
                )
            }
            
            Divider()
            
            // Botón principal
            Button(
                onClick = {
                    if (isListening) onStopListening() else onStartListening()
                },
                enabled = isAvailable && !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) Color(0xFFEF4444) else Color(0xFF8B5CF6),
                    disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        if (isListening) "Detener" else "Comenzar Traducción",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentStatusCard(
    isListening: Boolean,
    isProcessing: Boolean
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        ModernCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color(0xFF8B5CF6)
                    )
                } else {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color(0xFFFF6B6B)
                    )
                }
                
                Column {
                    Text(
                        when {
                            isProcessing -> "Traduciendo..."
                            isListening -> "Escuchando..."
                            else -> "Listo"
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = when {
                            isProcessing -> Color(0xFF8B5CF6)
                            isListening -> Color(0xFFFF6B6B)
                            else -> Color(0xFF10B981)
                        }
                    )
                    Text(
                        when {
                            isProcessing -> "Procesando con Whisper"
                            isListening -> "Habla ahora"
                            else -> "En espera"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun ErrorBanner(error: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFEF4444).copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
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

@Composable
fun TranslationsHistory(
    translations: List<TranslationEntry>,
    modifier: Modifier = Modifier
) {
    ModernCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📚 Historial de Traducciones",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF8B5CF6)
                )
                if (translations.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                    ) {
                        Text(
                            "${translations.size}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF8B5CF6)
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            if (translations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            "No hay traducciones aún",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            "Presiona el botón para comenzar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(translations, key = { it.id }) { translation ->
                        TranslationItem(translation)
                    }
                }
            }
        }
    }
}

@Composable
fun TranslationItem(translation: TranslationEntry) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header con timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        timeFormatter.format(Date(translation.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.2f)
                ) {
                    Text(
                        "🌐 ${translation.sourceLanguage} → ${translation.targetLanguage}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B5CF6)
                    )
                }
            }
            
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            
            // Original
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Original:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    translation.originalText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Traducción
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF8B5CF6).copy(alpha = 0.1f)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Traducción:",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color(0xFF8B5CF6)
                    )
                    Text(
                        translation.translatedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
