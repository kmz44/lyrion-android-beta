package io.orabel.orabelandroid.ui.screens.tts

import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.tts.TtsRepository
import io.orabel.orabelandroid.ui.components.ModernCard
import io.orabel.orabelandroid.ui.components.ModernTopBar
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.utils.SafeToast
import io.orabel.orabelandroid.utils.rememberSafeMessaging
import io.orabel.orabelandroid.utils.DeviceResourcesHelper
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class TtsActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val ttsRepository: TtsRepository by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                TtsScreen(
                    ttsRepository = ttsRepository,
                    onBackClick = { finish() }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        ttsRepository.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsScreen(
    ttsRepository: TtsRepository,
    onBackClick: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var isInitialized by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var speechRate by remember { mutableStateOf(1.0f) }
    var pitch by remember { mutableStateOf(1.0f) }
    var availableVoices by remember { mutableStateOf(listOf<String>()) }
    var isLoading by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Initialize TTS when screen loads
    LaunchedEffect(Unit) {
        isLoading = true
        
        // Log device information for debugging
        DeviceResourcesHelper.logDeviceOptimizations(context)
        
        val success = ttsRepository.initializeTts()
        if (success) {
            val spanishSet = ttsRepository.setSpanishLanguage()
            if (spanishSet) {
                availableVoices = ttsRepository.getAvailableSpanishVoices()
                isInitialized = true
                
                // Show different messages based on device capabilities
                val message = if (ttsRepository.isLowEndDevice()) {
                    "TTS optimizado para ${android.os.Build.MODEL} inicializado"
                } else {
                    "TTS Español inicializado correctamente"
                }
                SafeToast.showSafe(context, message)
            } else {
                SafeToast.showSafe(context, "Error: Español no disponible")
            }
        } else {
            SafeToast.showSafe(context, "Error inicializando TTS")
        }
        isLoading = false
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
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
            
            Text(
                text = "🎙️ TTS Español",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            
            Box(modifier = Modifier.size(48.dp)) // Placeholder for balance
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else {
            // Status Card
            ModernCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            if (isInitialized) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isInitialized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isInitialized) "TTS Español Listo" else "TTS No Disponible",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    if (availableVoices.isNotEmpty()) {
                        Text(
                            text = "Voces disponibles: ${availableVoices.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Show device optimization info for low-end devices
                    if (isInitialized && ttsRepository.isLowEndDevice()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Optimizado para ${android.os.Build.MODEL}",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            // Device Optimization Info Card (for low-end devices)
            if (isInitialized && ttsRepository.isLowEndDevice()) {
                ModernCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Optimizaciones Activadas",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Text(
                            text = ttsRepository.getOptimizationInfo(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "💡 Consejos para mejor rendimiento:",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Textos más cortos se procesan mejor\n• El texto se divide automáticamente en fragmentos\n• Velocidad optimizada para tu dispositivo",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // Text Input
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Texto a pronunciar") },
                placeholder = { Text("Escribe aquí el texto en español que quieres escuchar...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            
            // Controls
            ModernCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "⚙️ Configuración de Voz",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Speech Rate Control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Velocidad",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(speechRate * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Slider(
                        value = speechRate,
                        onValueChange = { 
                            speechRate = it
                            if (isInitialized) {
                                ttsRepository.setSpeechRate(it)
                            }
                        },
                        valueRange = 0.1f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Pitch Control
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tono",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(pitch * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Slider(
                        value = pitch,
                        onValueChange = { 
                            pitch = it
                            if (isInitialized) {
                                ttsRepository.setPitch(it)
                            }
                        },
                        valueRange = 0.1f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
            
            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (text.isNotBlank() && isInitialized) {
                            coroutineScope.launch {
                                isSpeaking = true
                                
                                // Show appropriate message for device type
                                if (ttsRepository.isLowEndDevice() && text.length > 100) {
                                    SafeToast.showSafe(context, "Texto largo detectado - se dividirá en fragmentos para mejor rendimiento")
                                }
                                
                                val success = ttsRepository.speak(text)
                                if (!success) {
                                    SafeToast.showSafe(context, "Error al reproducir")
                                }
                                isSpeaking = false
                            }
                        } else {
                            SafeToast.showSafe(context, "Por favor, escribe algún texto")
                        }
                    },
                    enabled = isInitialized && text.isNotBlank() && !isSpeaking,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSpeaking) "Reproduciendo..." else if (ttsRepository.isLowEndDevice()) "Reproducir (Optimizado)" else "Reproducir")
                }
                
                Button(
                    onClick = {
                        ttsRepository.stop()
                        isSpeaking = false
                    },
                    enabled = isInitialized && isSpeaking,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Detener")
                }
            }
            
            // Sample Texts
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📝 Textos de Ejemplo",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    val sampleTexts = listOf(
                        "Hola, soy Lyrion IA. Puedo ayudarte con traducción y síntesis de voz en español.",
                        "La inteligencia artificial está transformando la forma en que interactuamos con la tecnología.",
                        "Este es un ejemplo de síntesis de voz en español con diferentes configuraciones.",
                        "¿Cómo estás hoy? Espero que tengas un día excelente."
                    )
                    
                    sampleTexts.forEach { sampleText ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sampleText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            
                            IconButton(
                                onClick = { text = sampleText },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Usar texto",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
