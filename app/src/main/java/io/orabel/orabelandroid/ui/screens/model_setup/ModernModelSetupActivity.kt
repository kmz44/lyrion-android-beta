/*
 * Copyright (C) 2024 Orabel IA
 * Modern model setup screen for teens
 */

package io.orabel.orabelandroid.ui.screens.model_setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.data.ModelsDB
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.components.AppProgressDialog
import io.orabel.orabelandroid.ui.components.hideProgressDialog
import io.orabel.orabelandroid.ui.components.setProgressDialogText
import io.orabel.orabelandroid.ui.components.setProgressDialogTitle
import io.orabel.orabelandroid.ui.components.showProgressDialog
import io.orabel.orabelandroid.ui.components.ModernBottomNavigation
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.screens.search.SearchActivity
import io.orabel.orabelandroid.ui.screens.calendar.CalendarActivity
import io.orabel.orabelandroid.ui.screens.welcome.WelcomeActivity
import io.orabel.orabelandroid.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths

const val LOGTAG_SETUP = "[ModelSetup]"
val LOGD_SETUP: (String) -> Unit = { Log.d(LOGTAG_SETUP, it) }

class ModernModelSetupActivity : ComponentActivity(), KoinComponent {
    private var openChatScreen: Boolean = true
    private val modelsDB: ModelsDB by inject()
    private val orabelPreferences: OrabelPreferences by inject()
    
    // Selector de archivos GGUF con filtro específico
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Validar que el archivo sea GGUF antes de procesarlo
            if (isValidGGUFFile(uri)) {
                LOGD_SETUP("Valid GGUF file selected")
                copyModelFile(uri)
            } else {
                LOGD_SETUP("Invalid file selected - not a GGUF file")
                Toast.makeText(
                    this,
                    "❌ Archivo no válido\n\nPor favor selecciona un archivo GGUF válido (.gguf)\n\nLos archivos GGUF son modelos de IA optimizados para dispositivos móviles.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }    /**
     * Copia el archivo del modelo desde la URI seleccionada al directorio interno de la app
     * y lo agrega a la base de datos de modelos. Automáticamente abre el chat si está configurado.
     */
    private fun copyModelFile(uri: Uri) {
        var fileName = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            fileName = cursor.getString(nameIndex)
        }
        
        if (fileName.isNotEmpty()) {
            setProgressDialogTitle("Configurando Modelo")
            setProgressDialogText("Preparando $fileName...")
            showProgressDialog()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Use the proper storage path for models
                    val modelPath = modelsDB.getModelStoragePath(fileName)
                    val modelFile = File(modelPath)
                    
                    // Update progress: Copying file
                    withContext(Dispatchers.Main) {
                        setProgressDialogText("📂 Copiando archivo del modelo...")
                    }
                    
                    contentResolver.openInputStream(uri).use { inputStream ->
                        FileOutputStream(modelFile).use { outputStream ->
                            inputStream?.copyTo(outputStream)
                        }
                    }
                    
                    // Update progress: Saving to database
                    withContext(Dispatchers.Main) {
                        setProgressDialogText("💾 Guardando en la base de datos...")
                    }
                    
                    val modelId = modelsDB.addModel(
                        fileName,
                        "",
                        modelPath,
                    )
                    
                    // Guardar este modelo como el seleccionado
                    orabelPreferences.setSelectedModelId(modelId)
                    orabelPreferences.setFirstTimeSetupCompleted()
                    
                    // Asegurarse de que el archivo se haya creado correctamente
                    if (modelFile.exists() && modelFile.length() > 0) {
                        LOGD_SETUP("Model file copied successfully: ${modelFile.absolutePath}, size: ${modelFile.length()}")
                        
                        withContext(Dispatchers.Main) {
                            setProgressDialogText("🚀 Modelo listo. Abriendo chat...")
                        }
                        
                        // Pequeño delay para que el usuario vea el mensaje
                        delay(1500)
                        
                        withContext(Dispatchers.Main) {
                            hideProgressDialog()
                            
                            LOGD_SETUP("About to open chat. openChatScreen = $openChatScreen")
                            
                            // Mostrar mensaje de éxito
                            Toast.makeText(this@ModernModelSetupActivity, 
                                "✅ ¡Modelo $fileName cargado exitosamente!", 
                                Toast.LENGTH_SHORT).show()
                            
                            // Abrir chat con información sobre el modelo recién cargado
                            if (openChatScreen) {
                                try {
                                    LOGD_SETUP("Creating intent to open ChatActivity")
                                    val intent = Intent(this@ModernModelSetupActivity, ChatActivity::class.java)
                                    intent.putExtra("newModelLoaded", true)
                                    intent.putExtra("modelName", fileName)
                                    LOGD_SETUP("Starting ChatActivity with intent: $intent")
                                    startActivity(intent)
                                    LOGD_SETUP("ChatActivity started, finishing ModelSetupActivity")
                                    finish()
                                } catch (e: Exception) {
                                    LOGD_SETUP("Error opening ChatActivity: ${e.message}")
                                    Toast.makeText(this@ModernModelSetupActivity, 
                                        "Error abriendo chat: ${e.message}", 
                                        Toast.LENGTH_LONG).show()
                                }
                            } else {
                                LOGD_SETUP("openChatScreen is false, not opening chat")
                            }
                        }
                    } else {
                        LOGD_SETUP("Model file validation failed. Exists: ${modelFile.exists()}, Size: ${if (modelFile.exists()) modelFile.length() else "N/A"}")
                        withContext(Dispatchers.Main) {
                            hideProgressDialog()
                            Toast.makeText(this@ModernModelSetupActivity, 
                                "❌ Error: El archivo del modelo no se copió correctamente", 
                                Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hideProgressDialog()
                        Toast.makeText(this@ModernModelSetupActivity, 
                            "Error copying file: ${e.message}", 
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, getString(R.string.invalid_file), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Valida que el archivo seleccionado sea un archivo GGUF válido
     */
    private fun isValidGGUFFile(uri: Uri): Boolean {
        var fileName = ""
        
        try {
            // Obtener el nombre del archivo
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    cursor.moveToFirst()
                    fileName = cursor.getString(nameIndex) ?: ""
                }
            }
            
            LOGD_SETUP("Validating file: $fileName")
            
            // Verificar extensión
            if (!fileName.lowercase().endsWith(".gguf")) {
                LOGD_SETUP("File extension validation failed: $fileName")
                return false
            }
            
            // Verificar que el archivo tenga contenido
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(4)
                val bytesRead = inputStream.read(buffer)
                
                if (bytesRead < 4) {
                    LOGD_SETUP("File too small: $bytesRead bytes")
                    return false
                }
                
                // Verificar la signatura GGUF (los primeros 4 bytes deben ser "GGUF")
                val signature = String(buffer, Charsets.UTF_8)
                LOGD_SETUP("File signature: $signature")
                
                if (signature == "GGUF") {
                    LOGD_SETUP("Valid GGUF file detected: $fileName")
                    return true
                } else {
                    LOGD_SETUP("Invalid GGUF signature: $signature")
                    return false
                }
            } ?: run {
                LOGD_SETUP("Could not open input stream for file: $fileName")
                return false
            }
        } catch (e: Exception) {
            LOGD_SETUP("Error validating GGUF file: ${e.message}")
            return false
        }
    }

    /**
     * Crea un Intent personalizado para seleccionar archivos GGUF
     */
    private fun createGGUFFilePickerIntent(): Intent {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "*/*"
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        
        // Agregar filtro para archivos GGUF
        val extraMimeTypes = arrayOf(
            "application/octet-stream",
            "application/x-gguf",
            "application/x-binary",
            "*/*"
        )
        intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
        intent.putExtra(Intent.EXTRA_TITLE, "Seleccionar archivo GGUF")
        
        return Intent.createChooser(intent, "Seleccionar modelo GGUF")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        openChatScreen = intent.extras?.getBoolean("openChatScreen") ?: true
        LOGD_SETUP("onCreate: openChatScreen = $openChatScreen, intent extras = ${intent.extras}")
        
        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                Box {
                    ModernModelSetupScreen(
                        onSelectFile = { 
                            // Lanzar selector con filtro específico para archivos GGUF
                            filePickerLauncher.launch("application/octet-stream") 
                        },
                        onBackPressed = { finish() },
                        onNavigateToHome = { openMainActivity() },
                        onNavigateToSearch = { openSearchActivity() },
                        onNavigateToChat = { openChat() },
                        onNavigateToCalendar = { openCalendarActivity() },
                        onNavigateToProfile = { openProfile() }
                    )
                    AppProgressDialog()
                }
            }
        }
    }
    
    private fun openMainActivity() {
        val intent = Intent(this, ModernMainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openChat() {
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
    }
    
    private fun openSearchActivity() {
        val intent = Intent(this, SearchActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openCalendarActivity() {
        val intent = Intent(this, CalendarActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openProfile() {
        val intent = Intent(this, WelcomeActivity::class.java)
        startActivity(intent)
    }
}

@Composable
fun ModernModelSetupScreen(
    onSelectFile: () -> Unit,
    onBackPressed: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(0f) }
    var selectedBottomNav by remember { mutableStateOf(2) } // Chat seleccionado (importar modelos es función de chat)
    var showAvailableModels by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Animación de entrada
    LaunchedEffect(Unit) {
        delay(100)
        scale = 1f
    }
    
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = ""
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Main content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .scale(animatedScale),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
            
            // Logo y título
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Logo
                Image(
                    painter = painterResource(R.drawable.ic_lyrion_logo),
                    contentDescription = "Lyrion Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                )
                
                // Título principal
                Text(
                    text = "📁 Seleccionar Modelo",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                
                // Descripción
                Text(
                    text = "Selecciona tu archivo GGUF para comenzar\n(Solo se mostrarán archivos compatibles)",
                    fontSize = 16.sp,
                    fontFamily = InterFontFamily,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            // Botones de acción
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Botón principal - Seleccionar archivo
                Button(
                    onClick = onSelectFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrabelPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "📂 Seleccionar Archivo GGUF",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily
                    )
                }
                
                // Botón modelos disponibles
                OutlinedButton(
                    onClick = { 
                        showAvailableModels = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OrabelPrimary
                    ),
                    border = BorderStroke(
                        width = 2.dp,
                        color = OrabelPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "🌐 Modelos Disponibles",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily
                    )
                }
            }
            
            // Botón volver
            OutlinedButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "⬅️ Volver",
                    fontSize = 16.sp,
                    fontFamily = InterFontFamily
                )
            }
            }
        }
        
        // Bottom Navigation
        ModernBottomNavigation(
            selectedItem = selectedBottomNav,
            onItemSelected = { index ->
                selectedBottomNav = index
                when (index) {
                    0 -> onNavigateToHome() // Home
                    1 -> onNavigateToSearch() // Búsqueda
                    2 -> { /* Chat - ya estamos en función de chat (importar modelos) */ }
                    3 -> onNavigateToCalendar() // Calendario
                    4 -> onNavigateToProfile() // Perfil
                }
            }
        )
    }
    
    // Dialog for available models
    if (showAvailableModels) {
        AvailableModelsDialog(
            onDismiss = { showAvailableModels = false },
            onModelSelected = { modelName, modelUrl ->
                showAvailableModels = false
                if (modelUrl.isNotEmpty()) {
                    // Aquí se podría implementar la descarga directa con ModelDownloader
                    // Por ahora, mostrar información sobre cómo descargar
                    Toast.makeText(
                        context,
                        "Abriendo enlace de descarga para $modelName",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Opcional: Abrir el enlace en el navegador
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(modelUrl))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Error al abrir enlace: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Visita Hugging Face para descargar $modelName",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

@Composable
fun AvailableModelsDialog(
    onDismiss: () -> Unit,
    onModelSelected: (String, String) -> Unit // Cambiado para incluir URL
) {
    val context = LocalContext.current
    
    // Usar los modelos reales del sistema
    val recommendedModels = listOf(
        ModelInfo(
            name = "⭐ Llama 3.2 1B Instruct", 
            size = "800 MB", 
            description = "Modelo estrella - Mayor rapidez en español, ideal para dispositivos de baja potencia",
            url = "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-IQ4_NL.gguf",
            isRecommended = true
        ),
        ModelInfo(
            name = "Phi-3 Mini 4K Instruct", 
            size = "600 MB", 
            description = "Respuestas precisas y rápidas, perfecto para dispositivos con recursos limitados",
            url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            isRecommended = false
        )
    )
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌐 Modelos Disponibles",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(
                        onClick = onDismiss
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_lyrion_logo),
                            contentDescription = "Cerrar",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Description
                Text(
                    text = "Estos modelos están optimizados para dispositivos móviles. Descárgalos directamente desde los enlaces o visita Hugging Face para más opciones.",
                    fontSize = 14.sp,
                    fontFamily = InterFontFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Destacar el modelo recomendado
                Surface(
                    color = OrabelPrimary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⭐ Modelo Recomendado",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily,
                        color = OrabelPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                
                // Models list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recommendedModels) { model ->
                        ModelCard(
                            model = model,
                            onModelSelected = onModelSelected
                        )
                    }
                }
                
                // Footer with Hugging Face link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            // Here you could open a browser to Hugging Face
                        }
                    ) {
                        Text(
                            text = "🤗 Visitar Hugging Face",
                            fontSize = 14.sp,
                            fontFamily = InterFontFamily,
                            color = OrabelPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: ModelInfo,
    onModelSelected: (String, String) -> Unit
) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                if (model.url.isNotEmpty()) {
                    onModelSelected(model.name, model.url)
                } else {
                    Toast.makeText(
                        context,
                        "Visita Hugging Face para descargar ${model.name}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (model.isRecommended) {
                OrabelPrimary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = if (model.isRecommended) {
            BorderStroke(2.dp, OrabelPrimary.copy(alpha = 0.5f))
        } else null,
        shape = RoundedCornerShape(12.dp)
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
                    text = model.name,
                    fontSize = 16.sp,
                    fontWeight = if (model.isRecommended) FontWeight.ExtraBold else FontWeight.Bold,
                    fontFamily = InterFontFamily,
                    color = if (model.isRecommended) OrabelPrimary else MaterialTheme.colorScheme.onSurface
                )
                
                Surface(
                    color = if (model.isRecommended) OrabelPrimary else OrabelPrimary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = model.size,
                        fontSize = 12.sp,
                        fontFamily = InterFontFamily,
                        color = if (model.isRecommended) Color.White else OrabelPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            Text(
                text = model.description,
                fontSize = 14.sp,
                fontFamily = InterFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            if (model.url.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        color = OrabelPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "📥 Toca para descargar",
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            color = OrabelPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

data class ModelInfo(
    val name: String,
    val size: String,
    val description: String,
    val url: String = "",
    val isRecommended: Boolean = false
)
