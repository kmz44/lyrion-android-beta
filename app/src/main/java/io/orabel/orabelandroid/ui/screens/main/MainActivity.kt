package io.orabel.orabelandroid.ui.screens.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.camera.core.CameraSelector // Nuevo
import androidx.camera.core.Preview // Nuevo
import androidx.camera.lifecycle.ProcessCameraProvider // Nuevo
import androidx.camera.view.PreviewView // Nuevo
import androidx.compose.ui.viewinterop.AndroidView // Nuevo
import androidx.compose.ui.platform.LocalContext // Asegurar existencia
import androidx.compose.ui.platform.LocalLifecycleOwner // Nuevo import
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.orabel.orabelandroid.ui.screens.gemini_live.GeminiLiveActivity
import io.orabel.orabelandroid.ui.screens.offlineoptions.OfflineOptionsActivity
import io.orabel.orabelandroid.ui.screens.welcome.WelcomeActivity
import io.orabel.orabelandroid.ui.screens.settings.SettingsActivity
import io.orabel.orabelandroid.ui.screens.search.SearchActivity
import io.orabel.orabelandroid.ui.theme.*
import org.koin.android.ext.android.inject
// Import del motor TTS integrado
import com.k2fsa.sherpa.onnx.tts.engine.MainActivity as SherpaTtsMainActivity
// Import del servicio de asistente de voz
import io.orabel.orabelandroid.services.VoiceAssistantService
import android.app.ActivityManager
import android.os.Build
import android.net.Uri
import android.provider.Settings

class ModernMainActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val modelsRepository by inject<ModelsRepository>()
    
    // Estado del servicio de asistente de voz
    private var isVoiceAssistantActive = mutableStateOf(false)
    
    // Launcher para solicitar permiso de micrófono
    private val requestMicrophonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permiso concedido, verificar SYSTEM_ALERT_WINDOW
            checkOverlayPermissionAndStart()
        } else {
            Toast.makeText(this, "⚠️ Se requiere permiso de micrófono para el asistente de voz", Toast.LENGTH_LONG).show()
        }
    }

    // Launcher para solicitar permiso de cámara (para el fondo)
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Actualizar estado si es necesario
    }
    
    // Launcher para SYSTEM_ALERT_WINDOW
    private val requestOverlayPermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                startVoiceAssistantService()
            } else {
                Toast.makeText(this, "⚠️ Se requiere permiso de superposición para abrir Gemini Live con pantalla bloqueada", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Solicitar permiso de cámara para el fondo "Liquid Glass"
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        // Verificar si el servicio ya está corriendo
        isVoiceAssistantActive.value = isServiceRunning(VoiceAssistantService::class.java)

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
                    isVoiceAssistantActive = isVoiceAssistantActive.value,
                    onToggleVoiceAssistant = ::toggleVoiceAssistant,
                    onChatClick = ::openChat,
                    onSetupModelClick = ::openModelSetup,
                    onSearchClick = ::openSearch,
                    onWelcomeClick = ::openWelcome,
                    onSettingsClick = ::openSettings,
                    onOfflineOptionsClick = ::openOfflineOptions,
                    onCalendarClick = ::openCalendar,
                    onGeminiLiveClick = ::openGeminiLive,
                    lastNavigationIndex = orabelPreferences.getLastNavigationIndex()
                )
            }
        }
    }
    
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    private fun toggleVoiceAssistant() {
        if (isVoiceAssistantActive.value) {
            // Detener servicio
            val intent = Intent(this, VoiceAssistantService::class.java)
            stopService(intent)
            isVoiceAssistantActive.value = false
            Log.d("MainActivity", "🛑 Servicio de asistente de voz detenido")
        } else {
            // Verificar y solicitar permisos antes de iniciar
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                checkOverlayPermissionAndStart()
            }
        }
    }
    
    private fun checkOverlayPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                // Solicitar permiso de superposición
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                requestOverlayPermission.launch(intent)
            } else {
                startVoiceAssistantService()
            }
        } else {
            startVoiceAssistantService()
        }
    }
    
    private fun startVoiceAssistantService() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isVoiceAssistantActive.value = true
        Log.d("MainActivity", "✅ Servicio de asistente de voz iniciado")
    }
    
    private fun openChat() {
        orabelPreferences.setLastNavigationIndex(1) // Chat ahora es índice 1
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
        orabelPreferences.setLastNavigationIndex(4)
        val intent = Intent(this, io.orabel.orabelandroid.ui.screens.profile.ProfileActivity::class.java)
        startActivity(intent)
    }
    
    private fun openSettings() {
        // No guarda índice de navegación porque configuración no está en el menú inferior
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun openOfflineOptions() {
        val intent = Intent(this, OfflineOptionsActivity::class.java)
        startActivity(intent)
    }

    private fun openCalendar() {
        val intent = Intent(this, io.orabel.orabelandroid.ui.screens.calendar.CalendarActivity::class.java)
        startActivity(intent)
    }
    
    private fun openSearch() {
        orabelPreferences.setLastNavigationIndex(0) // Búsqueda ahora es índice 0
        val intent = Intent(this, SearchActivity::class.java)
        startActivity(intent)
    }
    
    private fun openGeminiLive() {
        val intent = Intent(this, GeminiLiveActivity::class.java)
        startActivity(intent)
    }
    override fun onStart() {
        super.onStart()
        io.orabel.orabelandroid.utils.UserActivityManager.getInstance(this).onAppStart()
    }

    override fun onStop() {
        super.onStop()
        io.orabel.orabelandroid.utils.UserActivityManager.getInstance(this).onAppStop()
    }
}

@Composable
fun CameraPreviewBackground() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, androidx.core.content.ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
fun ModernMainScreen(
    hasValidModel: Boolean,
    isVoiceAssistantActive: Boolean,
    onToggleVoiceAssistant: () -> Unit,
    onChatClick: () -> Unit,
    onSetupModelClick: () -> Unit,
    onSearchClick: () -> Unit,
    onWelcomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOfflineOptionsClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onGeminiLiveClick: () -> Unit,
    lastNavigationIndex: Int
) {
    var selectedBottomNav by remember { mutableStateOf(lastNavigationIndex) }
    val context = LocalContext.current
    
    // Verificar permiso de cámara
    val hasCameraPermission = remember {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, 
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .swipeableNavigation(
                currentIndex = 2,
                onSwipeLeft = { onCalendarClick() },
                onSwipeRight = { onChatClick() }
            )
    ) {
        // CAPA 1: Fondo (Cámara o Gradiente si no hay permiso)
        if (hasCameraPermission) {
            CameraPreviewBackground()
            // Capa oscura translúcida para mejorar legibilidad sobre la cámara
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0F2027),
                                Color(0xFF203A43),
                                Color(0xFF2C5364)
                            )
                        )
                    )
            )
        }
        
        // CAPA 2: Contenido
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar Transparente personalizada
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lyrion",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )

                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Configuración",
                        tint = Color.White
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Panel Liquid Glass Central
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    // Efecto Glass
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(
                        BorderStroke(1.dp, Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.4f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        )),
                        RoundedCornerShape(30.dp)
                    )
            ) {
                 Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                 ) {
                     // Texto Hola
                     Text(
                        text = "Hola, Usuario", // Idealmente aquí iría el nombre real
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        textAlign = TextAlign.Center
                     )
                     
                     // Texto Bienvenida
                     Text(
                        text = "Bienvenido a tu experiencia de computación espacial.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White.copy(alpha = 0.9f)
                        ),
                        textAlign = TextAlign.Center
                     )
                     
                     Spacer(modifier = Modifier.height(8.dp))
                     
                     // Botón Explorar
                     Button(
                        onClick = onChatClick, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4A90E2).copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                     ) {
                         Text("Explorar", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                     }
                 }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(80.dp)) // Espacio para bottom nav
        }

        // Bottom Navigation
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ModernBottomNavigation(
                selectedItem = selectedBottomNav,
                isGlassStyle = true,
                onItemSelected = { index ->
                    selectedBottomNav = index
                    when (index) {
                        0 -> onSearchClick()
                        1 -> onChatClick()
                        2 -> { } // Inicio
                        3 -> onCalendarClick()
                        4 -> onWelcomeClick()
                    }
                }
            )
        }
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
    CHAT, SETUP_MODEL, WELCOME
}

private fun getMainOptions(hasValidModel: Boolean): List<MainOption> {
    return if (hasValidModel) {
        // Si hay un modelo válido, mostrar opciones básicas
        listOf(
            MainOption(
                title = "Comenzar Chat",
                description = "Inicia una conversación con tu modelo de IA",
                icon = Icons.AutoMirrored.Filled.Chat,
                color = PrimaryColor,
                action = MainAction.CHAT
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
                title = "Información",
                description = "Aprende sobre Lyrion",
                icon = Icons.Default.Info,
                color = SecondaryColor,
                action = MainAction.WELCOME
            )
        )
    }
}
