package io.orabel.orabelandroid.ui.screens.translation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.translation.TranslationRepository
import io.orabel.orabelandroid.ui.components.ModernCard
import io.orabel.orabelandroid.ui.components.ModernTopBar
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.utils.SafeToast
import io.orabel.orabelandroid.utils.rememberSafeMessaging
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class TranslationActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val translationRepository by inject<TranslationRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            val translationState by translationRepository.translationState.collectAsState()
            
            // Mostrar errores usando SafeToast
            LaunchedEffect(translationState.downloadError) {
                translationState.downloadError?.let { error ->
                    SafeToast.showSafe(
                        this@TranslationActivity,
                        "Error descargando modelos: $error"
                    )
                }
            }
            
            LaunchedEffect(translationState.translationError) {
                translationState.translationError?.let { error ->
                    SafeToast.showSafe(
                        this@TranslationActivity,
                        "Error traduciendo: $error"
                    )
                }
            }
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                TranslationScreen(
                    translationRepository = translationRepository,
                    onBackClick = { finish() },
                    onCopyClick = { text ->
                        SafeToast.showSafe(
                            this@TranslationActivity,
                            "Texto copiado al portapapeles"
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    translationRepository: TranslationRepository,
    onBackClick: () -> Unit,
    onCopyClick: (String) -> Unit
) {
    val translationState by translationRepository.translationState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    
    var inputText by remember { mutableStateOf("") }
    var translatedText by remember { mutableStateOf("") }
    var isAutoDetect by remember { mutableStateOf(true) }
    var isSpanishToEnglish by remember { mutableStateOf(true) }
    
    // Descargar modelos al iniciar
    LaunchedEffect(Unit) {
        if (!translationState.areModelsDownloaded) {
            translationRepository.downloadModels()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        ModernTopBar(
            title = "Traductor",
            showBackButton = true,
            onBackClick = onBackClick
        )
        
        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            if (translationState.isDownloading) {
                ModernCard(
                    backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Descargando modelos de traducción...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else if (translationState.areModelsDownloaded) {
                ModernCard(
                    backgroundColor = Color(0xFF10B981).copy(alpha = 0.1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Listo",
                            tint = Color(0xFF10B981)
                        )
                        Text(
                            text = "Modelos descargados. ¡Listo para traducir!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Translation Mode Selection
            ModernCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Modo de Traducción",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Auto-detect button
                        FilterChip(
                            selected = isAutoDetect,
                            onClick = { isAutoDetect = !isAutoDetect },
                            label = { Text("Auto-detectar") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Auto-detectar"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Manual mode button
                        FilterChip(
                            selected = !isAutoDetect,
                            onClick = { isAutoDetect = !isAutoDetect },
                            label = { Text("Manual") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = "Manual"
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    // Manual mode selection
                    if (!isAutoDetect) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = isSpanishToEnglish,
                                onClick = { isSpanishToEnglish = true },
                                label = { Text("ES → EN") },
                                modifier = Modifier.weight(1f)
                            )
                            
                            FilterChip(
                                selected = !isSpanishToEnglish,
                                onClick = { isSpanishToEnglish = false },
                                label = { Text("EN → ES") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Input Card
            ModernCard {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Texto a Traducir",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ingresa el texto aquí...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 6,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    // Translate button
                    Button(
                        onClick = {
                            if (inputText.isNotBlank() && translationState.areModelsDownloaded) {
                                scope.launch {
                                    val result = if (isAutoDetect) {
                                        translationRepository.detectLanguageAndTranslate(inputText)
                                    } else {
                                        if (isSpanishToEnglish) {
                                            translationRepository.translateSpanishToEnglish(inputText)
                                        } else {
                                            translationRepository.translateEnglishToSpanish(inputText)
                                        }
                                    }
                                    
                                    result?.let { translatedText = it }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = inputText.isNotBlank() && 
                                translationState.areModelsDownloaded && 
                                !translationState.isTranslating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor
                        )
                    ) {
                        if (translationState.isTranslating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Traducir"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (translationState.isTranslating) "Traduciendo..." else "Traducir",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Output Card
            if (translatedText.isNotBlank()) {
                ModernCard {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Traducción",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(translatedText))
                                    onCopyClick(translatedText)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copiar"
                                )
                            }
                        }
                        
                        SelectionContainer {
                            Text(
                                text = translatedText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
            
            // Info Card
            ModernCard(
                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Información",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    
                    Text(
                        text = "• Traducción completamente offline\n" +
                                "• Admite español ↔ inglés\n" +
                                "• Los modelos se descargan solo una vez\n" +
                                "• Funciona sin conexión a internet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
