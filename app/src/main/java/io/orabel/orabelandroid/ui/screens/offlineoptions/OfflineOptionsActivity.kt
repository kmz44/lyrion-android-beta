package io.orabel.orabelandroid.ui.screens.offlineoptions

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.llm.ModelsRepository
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.screens.model_setup.ModernModelSetupActivity
import io.orabel.orabelandroid.ui.screens.translation.TranslationActivity
import io.orabel.orabelandroid.ui.screens.tts.TtsActivity
import io.orabel.orabelandroid.ui.screens.stt.SttActivity
import io.orabel.orabelandroid.ui.screens.ialive.IALiveActivity
import io.orabel.orabelandroid.ui.screens.whisper_stt.WhisperSttActivity
import io.orabel.orabelandroid.ui.screens.realtime_translator.RealtimeTranslatorActivity
import io.orabel.orabelandroid.ui.theme.*
import org.koin.android.ext.android.inject
// Import del motor TTS integrado
import com.k2fsa.sherpa.onnx.tts.engine.MainActivity as SherpaTtsMainActivity

class OfflineOptionsActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val modelsRepository by inject<ModelsRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Verificar si ya hay un modelo seleccionado y disponible
        val selectedModelId = orabelPreferences.getSelectedModelId()
        val hasValidModel = if (selectedModelId != -1L) {
            modelsRepository.getModelFromId(selectedModelId) != null
        } else {
            false
        }

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                OfflineOptionsScreen(
                    hasValidModel = hasValidModel,
                    onBackClick = { finish() },
                    onChatClick = ::openChat,
                    onSetupModelClick = ::openModelSetup,
                    onTranslationClick = ::openTranslation,
                    onTtsClick = ::openTts,
                    onSttClick = ::openStt,
                    onOcrClick = ::openOcr,
                    onIALiveClick = ::openIALive,
                    onSherpaTtsClick = ::openSherpaTts,
                    onWhisperSttClick = ::openWhisperStt,
                    onRealtimeTranslatorClick = ::openRealtimeTranslator
                )
            }
        }
    }

    private fun openChat() {
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
    }

    private fun openModelSetup() {
        val intent = Intent(this, ModernModelSetupActivity::class.java)
        intent.putExtra("openChatScreen", true)
        startActivity(intent)
    }
    
    private fun openTranslation() {
        val intent = Intent(this, TranslationActivity::class.java)
        startActivity(intent)
    }
    
    private fun openTts() {
        val intent = Intent(this, TtsActivity::class.java)
        startActivity(intent)
    }
    
    private fun openStt() {
        val intent = Intent(this, SttActivity::class.java)
        startActivity(intent)
    }
    
    private fun openOcr() {
        val intent = Intent(this, io.orabel.orabelandroid.ui.screens.ocr.OcrActivity::class.java)
        startActivity(intent)
    }
    
    private fun openIALive() {
        val intent = Intent(this, IALiveActivity::class.java)
        startActivity(intent)
    }

    private fun openSherpaTts() {
        val intent = Intent(this, SherpaTtsMainActivity::class.java)
        startActivity(intent)
    }


    
    private fun openWhisperStt() {
        val intent = Intent(this, WhisperSttActivity::class.java)
        startActivity(intent)
    }
    
    private fun openRealtimeTranslator() {
        val intent = Intent(this, RealtimeTranslatorActivity::class.java)
        startActivity(intent)
    }
}

@Composable
fun OfflineOptionsScreen(
    hasValidModel: Boolean,
    onBackClick: () -> Unit,
    onChatClick: () -> Unit,
    onSetupModelClick: () -> Unit,
    onTranslationClick: () -> Unit,
    onTtsClick: () -> Unit,
    onSttClick: () -> Unit,
    onOcrClick: () -> Unit,
    onIALiveClick: () -> Unit,
    onSherpaTtsClick: () -> Unit,
    onWhisperSttClick: () -> Unit,
    onRealtimeTranslatorClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar con botón de regreso
        ModernTopBar(
            title = "Opciones Offline",
            showBackButton = true,
            onBackClick = onBackClick
        )
        
        // Main Content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Todas las opciones offline",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "Funcionalidades que no requieren conexión a internet",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    )
                }
            }

            // Motor TTS avanzado
            item {
                ModernCard(
                    title = "ttsEngine-master",
                    description = "Motor TTS avanzado (descarga de modelos y servicio de sistema)",
                    icon = Icons.Filled.RecordVoiceOver,
                    primaryActionText = "Abrir",
                    onPrimaryActionClick = onSherpaTtsClick
                )
            }


            
            // Opciones principales offline
            items(getOfflineOptions(hasValidModel)) { option ->
                OfflineOptionCard(
                    option = option,
                    onClick = {
                        when (option.action) {
                            OfflineAction.CHAT -> onChatClick()
                            OfflineAction.SETUP_MODEL -> onSetupModelClick()
                            OfflineAction.TRANSLATION -> onTranslationClick()
                            OfflineAction.TTS -> onTtsClick()
                            OfflineAction.STT -> onSttClick()
                            OfflineAction.OCR -> onOcrClick()
                            OfflineAction.IA_LIVE -> onIALiveClick()
                            OfflineAction.WHISPER_STT -> onWhisperSttClick()
                            OfflineAction.REALTIME_TRANSLATOR -> onRealtimeTranslatorClick()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun OfflineOptionCard(
    option: OfflineOption,
    onClick: () -> Unit
) {
    ModernCard(
        onClick = onClick,
        elevation = 4
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = option.color.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = option.title,
                    tint = option.color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = option.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                )
            }
            
            // Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Ir",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

data class OfflineOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val action: OfflineAction
)

enum class OfflineAction {
    CHAT, SETUP_MODEL, TRANSLATION, TTS, STT, OCR, IA_LIVE, WHISPER_STT, REALTIME_TRANSLATOR
}

private fun getOfflineOptions(hasValidModel: Boolean): List<OfflineOption> {
    return if (hasValidModel) {
        listOf(
            OfflineOption(
                title = "🤖 IA Live",
                description = "Conversación completa: habla y recibe respuesta por voz",
                icon = Icons.Default.RecordVoiceOver,
                color = Color(0xFFFF6B6B),
                action = OfflineAction.IA_LIVE
            ),
            OfflineOption(
                title = "Comenzar Chat",
                description = "Inicia una conversación con tu modelo de IA",
                icon = Icons.AutoMirrored.Filled.Chat,
                color = PrimaryColor,
                action = OfflineAction.CHAT
            ),
            OfflineOption(
                title = "Traductor",
                description = "Traduce texto entre español e inglés sin internet",
                icon = Icons.Default.Translate,
                color = Color(0xFF10B981),
                action = OfflineAction.TRANSLATION
            ),
            OfflineOption(
                title = "TTS Español",
                description = "Convierte texto a voz en español",
                icon = Icons.Default.VolumeUp,
                color = Color(0xFFEC4899),
                action = OfflineAction.TTS
            ),
            OfflineOption(
                title = "Voz a Texto",
                description = "Convierte tu voz a texto en español sin internet",
                icon = Icons.Default.Mic,
                color = Color(0xFF8B5CF6),
                action = OfflineAction.STT
            ),
            OfflineOption(
                title = "🎙️ Whisper STT",
                description = "Reconocimiento de voz avanzado con Whisper (Dispositivos potentes)",
                icon = Icons.Default.SettingsVoice,
                color = Color(0xFF6366F1),
                action = OfflineAction.WHISPER_STT
            ),
            OfflineOption(
                title = "🌐 Traductor en Tiempo Real",
                description = "Traducción automática de voz con Whisper",
                icon = Icons.Default.GTranslate,
                color = Color(0xFF8B5CF6),
                action = OfflineAction.REALTIME_TRANSLATOR
            ),
            OfflineOption(
                title = "Imagen a Texto",
                description = "Extrae texto de imágenes 100% offline con OCR",
                icon = Icons.Default.CameraAlt,
                color = Color(0xFFFF6B35),
                action = OfflineAction.OCR
            ),
            OfflineOption(
                title = "Cambiar Modelo",
                description = "Selecciona un modelo diferente",
                icon = Icons.Default.Settings,
                color = SecondaryColor,
                action = OfflineAction.SETUP_MODEL
            )
        )
    } else {
        listOf(
            OfflineOption(
                title = "Configurar Modelo",
                description = "Configura tu primer modelo de IA",
                icon = Icons.Default.GetApp,
                color = PrimaryColor,
                action = OfflineAction.SETUP_MODEL
            ),
            OfflineOption(
                title = "🤖 IA Live",
                description = "Conversación completa: habla y recibe respuesta por voz (Requiere modelo)",
                icon = Icons.Default.RecordVoiceOver,
                color = Color(0xFFFF6B6B).copy(alpha = 0.6f),
                action = OfflineAction.IA_LIVE
            ),
            OfflineOption(
                title = "Traductor",
                description = "Traduce texto entre español e inglés sin internet",
                icon = Icons.Default.Translate,
                color = Color(0xFF10B981),
                action = OfflineAction.TRANSLATION
            ),
            OfflineOption(
                title = "TTS Español",
                description = "Convierte texto a voz en español",
                icon = Icons.Default.VolumeUp,
                color = Color(0xFFEC4899),
                action = OfflineAction.TTS
            ),
            OfflineOption(
                title = "Voz a Texto",
                description = "Convierte tu voz a texto en español sin internet",
                icon = Icons.Default.Mic,
                color = Color(0xFF8B5CF6),
                action = OfflineAction.STT
            ),
            OfflineOption(
                title = "🎙️ Whisper STT",
                description = "Reconocimiento de voz avanzado con Whisper",
                icon = Icons.Default.SettingsVoice,
                color = Color(0xFF6366F1),
                action = OfflineAction.WHISPER_STT
            ),
            OfflineOption(
                title = "🌐 Traductor en Tiempo Real",
                description = "Traducción automática de voz con Whisper",
                icon = Icons.Default.GTranslate,
                color = Color(0xFF8B5CF6),
                action = OfflineAction.REALTIME_TRANSLATOR
            ),
            OfflineOption(
                title = "Imagen a Texto",
                description = "Extrae texto de imágenes 100% offline con OCR",
                icon = Icons.Default.CameraAlt,
                color = Color(0xFFFF6B35),
                action = OfflineAction.OCR
            )
        )
    }
}
