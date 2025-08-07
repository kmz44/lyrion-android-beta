package io.orabel.orabelandroid.ui.screens.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.llm.ModelsRepository
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.screens.model_setup.ModernModelSetupActivity
import io.orabel.orabelandroid.ui.screens.translation.TranslationActivity
import io.orabel.orabelandroid.ui.screens.tts.TtsActivity
import io.orabel.orabelandroid.ui.screens.stt.SttActivity
import io.orabel.orabelandroid.ui.screens.ialive.IALiveActivity
import io.orabel.orabelandroid.ui.screens.welcome.WelcomeActivity
import io.orabel.orabelandroid.ui.screens.settings.SettingsActivity
import io.orabel.orabelandroid.ui.theme.*
import org.koin.android.ext.android.inject

class ModernMainActivity : ComponentActivity() {
    
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
                ModernMainScreen(
                    hasValidModel = hasValidModel,
                    onChatClick = ::openChat,
                    onSetupModelClick = ::openModelSetup,
                    onWelcomeClick = ::openWelcome,
                    onSettingsClick = ::openSettings,
                    onTranslationClick = ::openTranslation,
                    onTtsClick = ::openTts,
                    onSttClick = ::openStt,
                    onOcrClick = ::openOcr,
                    onIALiveClick = ::openIALive,
                    lastNavigationIndex = orabelPreferences.getLastNavigationIndex()
                )
            }
        }
    }

    private fun openChat() {
        orabelPreferences.setLastNavigationIndex(2)
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
    }

    private fun openModelSetup() {
        orabelPreferences.setLastNavigationIndex(1)
        val intent = Intent(this, ModernModelSetupActivity::class.java)
        intent.putExtra("openChatScreen", true) // Abrir chat automáticamente después de cargar modelo
        startActivity(intent)
    }

    private fun openWelcome() {
        orabelPreferences.setLastNavigationIndex(3)
        val intent = Intent(this, WelcomeActivity::class.java)
        startActivity(intent)
    }
    
    private fun openSettings() {
        // No guarda índice de navegación porque configuración no está en el menú inferior
        val intent = Intent(this, SettingsActivity::class.java)
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
}

@Composable
fun ModernMainScreen(
    hasValidModel: Boolean,
    onChatClick: () -> Unit,
    onSetupModelClick: () -> Unit,
    onWelcomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTranslationClick: () -> Unit,
    onTtsClick: () -> Unit,
    onSttClick: () -> Unit,
    onOcrClick: () -> Unit,
    onIALiveClick: () -> Unit,
    lastNavigationIndex: Int
) {
    var selectedBottomNav by remember { mutableStateOf(lastNavigationIndex) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        ModernTopBar(
            title = "Lyrion",
            actions = {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configuración",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        
        // Main Content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Header
                Column(
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {                Text(
                    text = "Bienvenido a Lyrion",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                    
                    if (hasValidModel) {
                        Text(
                            text = "Tu modelo está listo. ¡Puedes empezar a chatear!",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        )
                    } else {
                        Text(
                            text = "Configura tu modelo de IA para empezar",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
            
            // Main Options
            items(getMainOptions(hasValidModel)) { option ->
                MainOptionCard(
                    option = option,
                    onClick = {
                        when (option.action) {
                            MainAction.CHAT -> onChatClick()
                            MainAction.SETUP_MODEL -> onSetupModelClick()
                            MainAction.WELCOME -> onWelcomeClick()
                            MainAction.TRANSLATION -> onTranslationClick()
                            MainAction.TTS -> onTtsClick()
                            MainAction.STT -> onSttClick()
                            MainAction.OCR -> onOcrClick()
                            MainAction.IA_LIVE -> onIALiveClick()
                        }
                    }
                )
            }
        }
        
        // Bottom Navigation
        ModernBottomNavigation(
            selectedItem = selectedBottomNav,
            onItemSelected = { index ->
                selectedBottomNav = index
                when (index) {
                    0 -> { /* Home - ya estamos aquí */ }
                    1 -> onSetupModelClick() // Búsqueda -> Setup de modelos
                    2 -> onChatClick() // Chat
                    3 -> onWelcomeClick() // Perfil
                }
            }
        )
    }
}

@Composable
fun MainOptionCard(
    option: MainOption,
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

data class MainOption(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val action: MainAction
)

enum class MainAction {
    CHAT, SETUP_MODEL, WELCOME, TRANSLATION, TTS, STT, OCR, IA_LIVE
}

private fun getMainOptions(hasValidModel: Boolean): List<MainOption> {
    return if (hasValidModel) {
        // Si hay un modelo válido, mostrar opciones de chat y configuración
        listOf(
            MainOption(
                title = "🤖 IA Live",
                description = "Conversación completa: habla y recibe respuesta por voz",
                icon = Icons.Default.RecordVoiceOver,
                color = Color(0xFFFF6B6B),
                action = MainAction.IA_LIVE
            ),
            MainOption(
                title = "Comenzar Chat",
                description = "Inicia una conversación con tu modelo de IA",
                icon = Icons.AutoMirrored.Filled.Chat,
                color = PrimaryColor,
                action = MainAction.CHAT
            ),
            MainOption(
                title = "Traductor",
                description = "Traduce texto entre español e inglés sin internet",
                icon = Icons.Default.Translate,
                color = Color(0xFF10B981),
                action = MainAction.TRANSLATION
            ),
            MainOption(
                title = "TTS Español",
                description = "Convierte texto a voz en español",
                icon = Icons.Default.VolumeUp,
                color = Color(0xFFEC4899),
                action = MainAction.TTS
            ),
            MainOption(
                title = "Voz a Texto",
                description = "Convierte tu voz a texto en español sin internet",
                icon = Icons.Default.Mic,
                color = Color(0xFF8B5CF6),
                action = MainAction.STT
            ),
            MainOption(
                title = "Imagen a Texto",
                description = "Extrae texto de imágenes 100% offline con OCR",
                icon = Icons.Default.CameraAlt,
                color = Color(0xFFFF6B35),
                action = MainAction.OCR
            ),
            MainOption(
                title = "Cambiar Modelo",
                description = "Selecciona un modelo diferente",
                icon = Icons.Default.Settings,
                color = SecondaryColor,
                action = MainAction.SETUP_MODEL
            ),
            MainOption(
                title = "Configuración",
                description = "Ajusta la configuración de la aplicación",
                icon = Icons.Default.SettingsApplications,
                color = AccentColor2,
                action = MainAction.WELCOME
            )
        )
    } else {
        // Si no hay modelo válido, mostrar opciones de configuración
        listOf(
            MainOption(
                title = "Configurar Modelo",
                description = "Configura tu primer modelo de IA",
                icon = Icons.Default.GetApp,
                color = PrimaryColor,
                action = MainAction.SETUP_MODEL
            ),
            MainOption(
                title = "🤖 IA Live",
                description = "Conversación completa: habla y recibe respuesta por voz (Requiere modelo)",
                icon = Icons.Default.RecordVoiceOver,
                color = Color(0xFFFF6B6B).copy(alpha = 0.6f),
                action = MainAction.IA_LIVE
            ),
            MainOption(
                title = "Traductor",
                description = "Traduce texto entre español e inglés sin internet",
                icon = Icons.Default.Translate,
                color = Color(0xFF10B981),
                action = MainAction.TRANSLATION
            ),
            MainOption(
                title = "TTS Español",
                description = "Convierte texto a voz en español",
                icon = Icons.Default.VolumeUp,
                color = Color(0xFFEC4899),
                action = MainAction.TTS
            ),
            MainOption(
                title = "Voz a Texto",
                description = "Convierte tu voz a texto en español sin internet",
                icon = Icons.Default.Mic,
                color = Color(0xFF8B5CF6),
                action = MainAction.STT
            ),
            MainOption(
                title = "Imagen a Texto",
                description = "Extrae texto de imágenes 100% offline con OCR",
                icon = Icons.Default.CameraAlt,
                color = Color(0xFFFF6B35),
                action = MainAction.OCR
            ),
            MainOption(
                title = "Información",
                description = "Aprende sobre Lyrion",
                icon = Icons.Default.Info,
                color = SecondaryColor,
                action = MainAction.WELCOME
            )
        )
    }
}
