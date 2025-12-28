/*
 * Copyright (C) 2025 Lyrion
 * Pantalla de Gemini 2.5 Flash Native Audio
 * Conversación por voz en tiempo real
 */

package io.orabel.orabelandroid.ui.screens.gemini_live

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.orabel.orabelandroid.gemini.GeminiAudioManager
import io.orabel.orabelandroid.gemini.GeminiLiveAudioClient
import io.orabel.orabelandroid.ui.theme.ModernOrabelTheme
import io.orabel.orabelandroid.data.OrabelPreferences
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

// Estados de la conversación
enum class ConversationState {
    DISCONNECTED,      // No conectado
    CONNECTING,        // Conectando al servidor
    IDLE,             // Conectado, esperando que el usuario hable
    LISTENING,        // Grabando audio del usuario
    PROCESSING,       // Enviando audio y esperando respuesta
    AI_SPEAKING,      // IA está hablando (reproduciendo audio)
    AI_INTERRUPTED,   // IA fue interrumpida por el usuario
    ERROR             // Error en la conexión
}

// Voces disponibles para Gemini
enum class GeminiVoice(val displayName: String, val configValue: String) {
    PUCK("Puck (Masculina)", "Puck"),
    CHARON("Charon (Neutral)", "Charon"),
    KORE("Kore (Femenina)", "Kore"),
    FENRIR("Fenrir (Grave)", "Fenrir"),
    AOEDE("Aoede (Dulce)", "Aoede")
}

class GeminiLiveActivity : ComponentActivity() {
    
    // Preferencias de tema
    private val orabelPreferences: OrabelPreferences by inject()
    
    // CAMBIAR A internal para acceso desde Composable
    internal lateinit var audioManager: GeminiAudioManager
    internal lateinit var liveClient: GeminiLiveAudioClient
    
    // TTS para confirmación de inicio
    private var tts: TextToSpeech? = null
    
    private var isConnected by mutableStateOf(false)
    private var conversationState by mutableStateOf(ConversationState.DISCONNECTED)
    private var transcription by mutableStateOf("")
    private var isSessionActive by mutableStateOf(false) // Controla si la sesión conversacional está activa
    private var isMicrophoneMuted by mutableStateOf(false) // Controla si el micrófono está silenciado (sin cerrar la sesión)
    private var selectedVoice by mutableStateOf(GeminiVoice.AOEDE) // Voz por defecto
    private var showVoiceSelector by mutableStateOf(false) // Mostrar selector de voces
    private var showMenu by mutableStateOf(false) // Menú de 3 puntos
    
    // Jobs para controlar los listeners y evitar duplicados entre conversaciones
    private var recordingJob: kotlinx.coroutines.Job? = null
    private var audioReceiveJob: kotlinx.coroutines.Job? = null
    private var turnCompleteJob: kotlinx.coroutines.Job? = null
    private var transcriptionJob: kotlinx.coroutines.Job? = null
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d("GeminiLive", "📋 Resultado de permiso: granted=$granted")
        if (granted) {
            Log.d("GeminiLive", "✅ Permiso otorgado, conectando...")
            connectToGemini()
        } else {
            Log.e("GeminiLive", "❌ Permiso denegado")
            conversationState = ConversationState.ERROR
            Toast.makeText(this, "Permiso de micrófono necesario para usar Gemini Live", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Si viene del VoiceAssistantService, habilitar que funcione con pantalla bloqueada
        if (intent.getBooleanExtra("from_voice_assistant", false)) {
            // Inicializar TTS para confirmación
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = java.util.Locale("es", "ES")
                    Log.d("GeminiLive", "✅ TTS inicializado para confirmación")
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // Android 8.1+ - Mostrar sobre lockscreen
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                // Android < 8.1 - Flags en la ventana
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                )
            }
            Log.d("GeminiLive", "🔓 Configurado para funcionar con pantalla bloqueada")
        }
        
        audioManager = GeminiAudioManager(this)
        liveClient = GeminiLiveAudioClient()
        
        setContent {
            // Tema reactivo a los cambios en las preferencias usando StateFlow
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            val isSystemInDarkMode = isSystemInDarkTheme()
            
            // Actualizar tema cuando cambie el modo del sistema
            LaunchedEffect(isSystemInDarkMode) {
                orabelPreferences.updateDarkTheme(isSystemInDarkMode)
            }
            
            // Log para depuración
            LaunchedEffect(isDarkTheme) {
                Log.d("GeminiLive", "🎨 Tema cambiado a: ${if (isDarkTheme) "Oscuro" else "Claro"}")
            }
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                // Auto-iniciar sesión si viene del VoiceAssistantService
                LaunchedEffect(Unit) {
                    if (intent.getBooleanExtra("from_voice_assistant", false)) {
                        Log.d("GeminiLive", "🤖 Iniciado desde VoiceAssistantService - Auto-iniciando sesión")
                        kotlinx.coroutines.delay(500) // Esperar a que la UI esté lista
                        startConversationSession()
                    }
                }
                
                ModernGeminiLiveScreen(
                    conversationState = conversationState,
                    isSessionActive = isSessionActive,
                    isMicrophoneMuted = isMicrophoneMuted,
                    transcription = transcription,
                    selectedVoice = selectedVoice,
                    showVoiceSelector = showVoiceSelector,
                    showMenu = showMenu,
                    onToggleSession = { handleToggleSession() },
                    onToggleMicrophone = { handleToggleMicrophone() },
                    onSendText = { text -> 
                        liveClient.sendText(text)
                    },
                    onVoiceSelected = { voice ->
                        Log.d("GeminiLive", "🎙️ Voz seleccionada: ${voice.displayName} (${voice.configValue})")
                        selectedVoice = voice
                        showVoiceSelector = false
                        
                        // Si hay sesión activa, reiniciar automáticamente con nueva voz
                        if (isSessionActive) {
                            Log.d("GeminiLive", "🔄 Reiniciando sesión con nueva voz: ${voice.configValue}")
                            
                            // Lanzar coroutine para manejar el reinicio
                            lifecycleScope.launch {
                                // Detener sesión actual (esto es asíncrono)
                                stopConversationSession()
                                
                                // Esperar 1.5 segundos para que se cierre completamente
                                kotlinx.coroutines.delay(1500)
                                
                                // Verificar que la activity sigue viva
                                if (!isFinishing && !isDestroyed) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        Log.d("GeminiLive", "▶️ Reiniciando con voz: ${voice.displayName}")
                                        startConversationSession()
                                    }
                                } else {
                                    Log.w("GeminiLive", "⚠️ Activity destruida, cancelando reinicio")
                                }
                            }
                        } else {
                            Log.d("GeminiLive", "ℹ️ Sin sesión activa, voz guardada para próxima sesión")
                        }
                    },
                    onShowVoiceSelector = { show -> 
                        Log.d("GeminiLive", "🔧 onShowVoiceSelector llamado: $show")
                        showVoiceSelector = show 
                    },
                    onShowMenu = { show -> 
                        Log.d("GeminiLive", "🔧 onShowMenu llamado: $show")
                        showMenu = show 
                    },
                    onBack = { finish() }
                )
            }
        }
    }
    
    // Maneja el toggle del botón principal: Iniciar/Detener sesión conversacional
    private fun handleToggleSession() {
        if (isSessionActive) {
            // Detener sesión completa
            stopConversationSession()
        } else {
            // Iniciar sesión conversacional
            startConversationSession()
        }
    }
    
    // Maneja el toggle del botón de micrófono: Solo silencia/activa el micrófono sin cerrar la sesión
    private fun handleToggleMicrophone() {
        if (!isSessionActive) {
            Log.d("GeminiLive", "⚠️ No se puede alternar micrófono: sesión inactiva")
            return
        }
        
        isMicrophoneMuted = !isMicrophoneMuted
        
        if (isMicrophoneMuted) {
            Log.d("GeminiLive", "🔇 Micrófono silenciado (sesión sigue activa, solo no envía audio)")
        } else {
            Log.d("GeminiLive", "🎤 Micrófono activado (enviando audio nuevamente)")
        }
    }
    
    private fun startConversationSession() {
        Log.d("GeminiLive", "🎯 Iniciando sesión conversacional")
        
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("GeminiLive", "⚠️ Solicitando permiso de micrófono")
            conversationState = ConversationState.CONNECTING
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            Log.d("GeminiLive", "✅ Permiso de micrófono otorgado")
            connectToGemini()
        }
    }
    
    private fun stopConversationSession() {
        Log.d("GeminiLive", "🛑 Deteniendo sesión conversacional")
        isSessionActive = false
        isMicrophoneMuted = false // Resetear estado del micrófono
        conversationState = ConversationState.DISCONNECTED
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 🔴 PRIMERO: Cancelar TODOS los listeners activos
                recordingJob?.cancel()
                audioReceiveJob?.cancel()
                turnCompleteJob?.cancel()
                transcriptionJob?.cancel()
                Log.d("GeminiLive", "🔴 Listeners cancelados")
                
                // SEGUNDO: Detener grabación si está activa
                audioManager.stopRecording()
                Log.d("GeminiLive", "🎤 Grabación detenida")
                
                // TERCERO: Detener reproducción
                audioManager.stopPlayback()
                Log.d("GeminiLive", "🔊 Reproducción detenida")
                
                // CUARTO: Desconectar WebSocket
                liveClient.disconnect()
                Log.d("GeminiLive", "🌐 WebSocket desconectado")
                
                // QUINTO: Esperar un poco para limpieza
                kotlinx.coroutines.delay(500)
                
                // SEXTO: Resetear audio manager (limpia buffers)
                audioManager.reset()
                Log.d("GeminiLive", "🔄 AudioManager reseteado")
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isConnected = false
                    transcription = ""
                    Log.d("GeminiLive", "✅ Sesión detenida y lista para reiniciar")
                }
            } catch (e: Exception) {
                Log.e("GeminiLive", "❌ Error deteniendo sesión: ${e.message}", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    conversationState = ConversationState.ERROR
                }
            }
        }
    }
    
    private fun connectToGemini() {
        Log.d("GeminiLive", "🔌 Iniciando conexión a Gemini")
        conversationState = ConversationState.CONNECTING
        
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // PASO 1: Si hay conexión previa, limpiarla
                if (isConnected) {
                    Log.d("GeminiLive", "⚠️ Conexión previa detectada, limpiando...")
                    // 🔴 CRÍTICO: Cancelar TODOS los listeners anteriores
                    recordingJob?.cancel()
                    audioReceiveJob?.cancel()
                    turnCompleteJob?.cancel()
                    transcriptionJob?.cancel()
                    
                    liveClient.disconnect()
                    audioManager.reset()
                    kotlinx.coroutines.delay(500)
                }
                
                // PASO 2: Iniciar playback de audio ANTES de conectar
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    audioManager.startPlayback()
                    Log.d("GeminiLive", "✅ AudioTrack iniciado para reproducción")
                }
                
                // PASO 3: Conectar al WebSocket
                liveClient.connect(
                    systemInstruction = """Eres Lyrion, un asistente de voz inteligente y amigable.
                        |Responde de forma natural y conversacional en español.
                        |Usa un tono cálido y empático.
                        |Mantén respuestas concisas pero completas.
                        |Responde como si estuvieras teniendo una conversación fluida.
                    """.trimMargin(),
                    voiceConfig = selectedVoice.configValue,
                    onConnected = {
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                            isConnected = true
                            isSessionActive = true
                            conversationState = ConversationState.IDLE
                            
                            // Si viene del asistente de voz, anunciar inicio
                            if (intent.getBooleanExtra("from_voice_assistant", false)) {
                                kotlinx.coroutines.delay(500)
                                tts?.speak("Iniciado, ya puede hablar", TextToSpeech.QUEUE_FLUSH, null, null)
                                Log.d("GeminiLive", "🔊 TTS: Iniciado, ya puede hablar")
                            }
                            
                            // ✅ FLUJO AUTOMÁTICO: Iniciar grabación inmediatamente
                            startListening()
                        }
                        
                        // 🔴 FLUJO REAL: Escuchar respuestas de audio del WebSocket
                        // Guardar referencia al Job para cancelarlo en la siguiente sesión
                        audioReceiveJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            liveClient.receiveAudio().collect { audioData ->
                                // ⚠️ CRÍTICO: Cancelar grabación cuando la IA empieza a hablar
                                recordingJob?.cancel()
                                audioManager.stopRecording()
                                
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    conversationState = ConversationState.AI_SPEAKING
                                }
                                
                                Log.d("GeminiLive", "🔊 Reproduciendo audio: ${audioData.size} bytes")
                                audioManager.playAudio(audioData)
                            }
                        }
                        
                        // 🔴 FLUJO REAL: Escuchar FIN DEL TURNO del modelo
                        // Guardar referencia al Job para cancelarlo en la siguiente sesión
                        turnCompleteJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            liveClient.receiveTurnComplete().collect {
                                Log.d("GeminiLive", "✅ Modelo terminó de hablar")
                                if (isSessionActive) {
                                    conversationState = ConversationState.IDLE
                                    // Pequeño delay para que el usuario pueda ver el cambio de estado
                                    kotlinx.coroutines.delay(500)
                                    startListening()
                                }
                            }
                        }
                        
                        // 🔴 FLUJO REAL: Escuchar transcripciones
                        // Guardar referencia al Job para cancelarlo en la siguiente sesión
                        transcriptionJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            liveClient.receiveTranscriptions().collect { text ->
                                transcription = text
                                Log.d("GeminiLive", "📝 Transcripción: $text")
                            }
                        }
                    },
                    onError = { error ->
                        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main) {
                            isConnected = false
                            isSessionActive = false
                            conversationState = ConversationState.ERROR
                            Toast.makeText(this@GeminiLiveActivity, error, Toast.LENGTH_LONG).show()
                        }
                    }
                )
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    conversationState = ConversationState.ERROR
                    Toast.makeText(this@GeminiLiveActivity, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun startListening() {
        if (!isSessionActive) return
        
        Log.d("GeminiLive", "🎤 Iniciando escucha automática")
        conversationState = ConversationState.LISTENING
        transcription = ""
        
        // Cancelar grabación anterior si existe
        recordingJob?.cancel()
        
        // FLUJO REAL: Capturar audio del micrófono → Enviar al WebSocket
        recordingJob = lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var bytesSent = 0
                var silenceCounter = 0
                val SILENCE_THRESHOLD = 40 // ~2 segundos de silencio para auto-enviar
                
                audioManager.startRecording().collect { audioData ->
                    if (!isSessionActive || conversationState != ConversationState.LISTENING) {
                        audioManager.stopRecording()
                        return@collect
                    }
                    
                    // Detección de silencio SIEMPRE (incluso si está silenciado)
                    val isSilent = audioData.all { (it.toInt() and 0xFF) < 5 }
                    if (isSilent) {
                        silenceCounter++
                    } else {
                        silenceCounter = 0
                    }
                    
                    // Solo enviar audio si el micrófono NO está silenciado
                    if (!isMicrophoneMuted) {
                        // Enviar audio REAL al WebSocket de Gemini
                        liveClient.sendAudio(audioData)
                        bytesSent += audioData.size
                    }
                    // Si está silenciado, simplemente ignorar los datos (no enviarlos)
                    
                    // Auto-enviar después de silencio prolongado
                    if (silenceCounter >= SILENCE_THRESHOLD && bytesSent > 16000) {
                        Log.d("GeminiLive", "🔇 Silencio detectado, enviando audio automáticamente")
                        stopListeningAndProcess()
                    }
                }
            } catch (e: Exception) {
                Log.e("GeminiLive", "❌ Error grabando audio", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    conversationState = ConversationState.ERROR
                }
            }
        }
    }
    
    private fun stopListeningAndProcess() {
        Log.d("GeminiLive", "🛑 Deteniendo escucha y procesando")
        conversationState = ConversationState.PROCESSING
        audioManager.stopRecording()
        
        // CRÍTICO: Indicar a Gemini que termine el turno de audio y genere respuesta
        liveClient.commitAudioBuffer()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            // Limpiar audio (maneja todas las excepciones internamente)
            audioManager.cleanup()
        } catch (e: Exception) {
            Log.w("GeminiLive", "⚠️ Error en audioManager.cleanup() (ignorado): ${e.javaClass.simpleName}")
        }
        
        try {
            // Desconectar WebSocket
            liveClient.disconnect()
        } catch (e: Exception) {
            Log.w("GeminiLive", "⚠️ Error en liveClient.disconnect() (ignorado): ${e.javaClass.simpleName}")
        }
        
        try {
            // Detener TTS
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w("GeminiLive", "⚠️ Error en tts.shutdown() (ignorado): ${e.javaClass.simpleName}")
        }
        
        // Notificar al VoiceAssistantService que Gemini Live finalizó (si fue iniciado desde allí)
        if (intent.getBooleanExtra("from_voice_assistant", false)) {
            try {
                val serviceIntent = android.content.Intent(this, io.orabel.orabelandroid.services.VoiceAssistantService::class.java)
                serviceIntent.action = "GEMINI_FINISHED"
                startService(serviceIntent)
                Log.d("GeminiLive", "✅ Notificado a VoiceAssistantService para reactivar Vosk")
            } catch (e: Exception) {
                Log.e("GeminiLive", "❌ Error notificando a VoiceAssistantService: ${e.message}")
            }
        }
    }
}

// ==================== NUEVA INTERFAZ MODERNA ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernGeminiLiveScreen(
    conversationState: ConversationState,
    isSessionActive: Boolean,
    isMicrophoneMuted: Boolean,
    transcription: String,
    selectedVoice: GeminiVoice,
    showVoiceSelector: Boolean,
    showMenu: Boolean,
    onToggleSession: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onSendText: (String) -> Unit,
    onVoiceSelected: (GeminiVoice) -> Unit,
    onShowVoiceSelector: (Boolean) -> Unit,
    onShowMenu: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    
    // Interceptar botón "atrás" cuando hay diálogos abiertos
    BackHandler(enabled = showVoiceSelector || showMenu) {
        Log.d("GeminiLive", "⬅️ Botón atrás presionado, cerrando diálogos")
        when {
            showVoiceSelector -> {
                Log.d("GeminiLive", "Cerrando VoiceSelector")
                onShowVoiceSelector(false)
            }
            showMenu -> {
                Log.d("GeminiLive", "Cerrando Menu")
                onShowMenu(false)
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface, // background-dark
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        Box {
                            IconButton(onClick = { 
                                Log.d("GeminiLive", "🍔 Botón MENÚ tocado (hamburger)")
                                onShowMenu(!showMenu) 
                            }) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Menú",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Menú desplegable del hamburger
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { 
                                    Log.d("GeminiLive", "❌ Menú cerrado sin selección")
                                    onShowMenu(false) 
                                }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Configurar voz") },
                                    onClick = {
                                        Log.d("GeminiLive", "🎙️ Opción 'Configurar voz' seleccionada")
                                        onShowMenu(false)
                                        onShowVoiceSelector(true)
                                        Log.d("GeminiLive", "✅ showVoiceSelector = true")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Settings, contentDescription = null)
                                    }
                                )
                            }
                        }
                    },
                    actions = {
                        // Botón "Volver" en la derecha
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Botones de control superiores (Iniciar/Detener conversación)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isSessionActive) {
                            // Botón "Iniciar conversación en vivo"
                            Button(
                                onClick = onToggleSession,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary // Verde
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Iniciar conversación en vivo",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        } else {
                            // Botón "Detener conversación"
                            Button(
                                onClick = onToggleSession,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error // Rojo
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Stop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onError
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Detener conversación",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onError
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Área principal: Mensaje central o transcripción
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (transcription.isEmpty() && !isSessionActive) {
                        // Mensaje de bienvenida
                        Text(
                            text = "¿Con qué puedo ayudarte?",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, // gray-300
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    } else {
                        // Mostrar transcripción y estado
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            // Indicador de estado visual
                            StateIndicator(
                                state = conversationState,
                                modifier = Modifier.size(80.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Texto de estado
                            Text(
                                text = when (conversationState) {
                                    ConversationState.LISTENING -> "Te estoy escuchando..."
                                    ConversationState.AI_SPEAKING -> "Lyrion está hablando"
                                    ConversationState.PROCESSING -> "Procesando..."
                                    ConversationState.CONNECTING -> "Conectando..."
                                    ConversationState.ERROR -> "Error de conexión"
                                    else -> "Listo"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            
                            if (transcription.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = transcription,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, // gray-400
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                
                // Footer: Input de texto con micrófono
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Barra de input
                        Surface(
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant, // gray-800
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                // Botón agregar (+ )
                                IconButton(onClick = { /* Agregar archivo/imagen */ }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Agregar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                // Campo de texto
                                BasicTextField(
                                    value = textInput,
                                    onValueChange = { textInput = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 12.dp),
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (textInput.isEmpty()) {
                                            Text(
                                                "Pregunta lo que quieras",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.outline // gray-500
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                
                                // Botón de micrófono (toggle para silenciar/activar micrófono sin cerrar sesión)
                                IconButton(
                                    onClick = onToggleMicrophone,
                                    enabled = isSessionActive // Solo activo cuando hay sesión
                                ) {
                                    Icon(
                                        if (isMicrophoneMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = if (isMicrophoneMuted) "Activar micrófono" else "Silenciar micrófono",
                                        tint = when {
                                            !isSessionActive -> MaterialTheme.colorScheme.outline // gray-500 cuando sesión inactiva
                                            isMicrophoneMuted -> MaterialTheme.colorScheme.error // red-500 cuando silenciado
                                            else -> MaterialTheme.colorScheme.primary // green-500 cuando activo
                                        }
                                    )
                                }
                                
                                // Botón enviar texto
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant, // gray-600
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (textInput.isNotBlank()) {
                                                onSendText(textInput)
                                                textInput = ""
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = "Enviar",
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Dialog de selección de voz
        if (showVoiceSelector) {
            LaunchedEffect(Unit) {
                Log.d("GeminiLive", "📱 Mostrando VoiceSelectorDialog")
            }
            VoiceSelectorDialog(
                selectedVoice = selectedVoice,
                onVoiceSelected = onVoiceSelected,
                onDismiss = { 
                    Log.d("GeminiLive", "❌ Diálogo de voz cerrado")
                    onShowVoiceSelector(false) 
                }
            )
        }
    }
}

@Composable
fun StateIndicator(
    state: ConversationState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "state")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == ConversationState.LISTENING || state == ConversationState.AI_SPEAKING) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val color = when (state) {
        ConversationState.LISTENING -> MaterialTheme.colorScheme.primary // green
        ConversationState.AI_SPEAKING -> MaterialTheme.colorScheme.tertiary // blue
        ConversationState.PROCESSING -> MaterialTheme.colorScheme.secondary // yellow
        ConversationState.ERROR -> MaterialTheme.colorScheme.error // red
        else -> MaterialTheme.colorScheme.outline // gray
    }
    
    Box(
        modifier = modifier.scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.2f),
            modifier = Modifier.fillMaxSize()
        ) {}
        
        Icon(
            when (state) {
                ConversationState.LISTENING -> Icons.Default.Mic
                ConversationState.AI_SPEAKING -> Icons.Default.VolumeUp
                ConversationState.PROCESSING -> Icons.Default.Sync
                ConversationState.ERROR -> Icons.Default.ErrorOutline
                ConversationState.CONNECTING -> Icons.Default.Wifi
                else -> Icons.Default.CheckCircle
            },
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
fun VoiceSelectorDialog(
    selectedVoice: GeminiVoice,
    onVoiceSelected: (GeminiVoice) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar voz") },
        text = {
            Column {
                GeminiVoice.entries.forEach { voice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                onVoiceSelected(voice)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = voice == selectedVoice,
                            onClick = null // Deshabilitado, el Row ya es clickeable
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = voice.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

/*
==================== CÓDIGO VIEJO COMENTADO - NO ELIMINAR ====================
Este código está duplicado arriba en ModernGeminiLiveScreen. 
Lo mantenemos comentado por si necesitamos referencias.
==============================================================================
*/    
    /*
    // Colores dinámicos según estado
    val stateColor = when (conversationState) {
        ConversationState.LISTENING -> MaterialTheme.colorScheme.primary // Verde
        ConversationState.AI_SPEAKING -> MaterialTheme.colorScheme.tertiary // Azul
        ConversationState.PROCESSING -> MaterialTheme.colorScheme.secondary // Amarillo
        ConversationState.ERROR -> MaterialTheme.colorScheme.error // Rojo
        ConversationState.AI_INTERRUPTED -> MaterialTheme.colorScheme.secondary // Naranja
        else -> MaterialTheme.colorScheme.outline // Gris
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gemini Live 2.5 Flash") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = stateColor
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                
                // Estado visual grande y claro
                StateIndicator(
                    state = conversationState,
                    stateColor = stateColor
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Botón principal (TOGGLE de sesión)
                AnimatedMainButton(
                    isSessionActive = isSessionActive,
                    conversationState = conversationState,
                    stateColor = stateColor,
                    onToggle = onToggleSession
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Transcripción con scroll
                TranscriptionCard(
                    transcription = transcription,
                    conversationState = conversationState
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Modo texto (opcional, mantenido como solicitaste)
                TextInputCard(
                    textInput = textInput,
                    onTextChange = { textInput = it },
                    onSend = {
                        if (textInput.isNotBlank()) {
                            onSendText(textInput)
                            textInput = ""
                        }
                    },
                    enabled = isSessionActive
                )
            }
        }
    }
}

// ==================== COMPONENTES UI MODERNOS ====================

@Composable
fun StateIndicator(
    state: ConversationState,
    stateColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "state")
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Ícono de estado
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            // Círculo pulsante de fondo
            if (state == ConversationState.LISTENING || state == ConversationState.AI_SPEAKING) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .alpha(alpha)
                        .background(stateColor.copy(alpha = 0.2f), CircleShape)
                )
            }
            
            // Ícono central
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(stateColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (state) {
                        ConversationState.LISTENING -> "🎤"
                        ConversationState.AI_SPEAKING -> "🗣️"
                        ConversationState.PROCESSING -> "⏳"
                        ConversationState.CONNECTING -> "🔄"
                        ConversationState.ERROR -> "❌"
                        ConversationState.AI_INTERRUPTED -> "⏸️"
                        else -> "💤"
                    },
                    style = MaterialTheme.typography.displayMedium
                )
            }
        }
        
        // Texto de estado
        Text(
            text = when (state) {
                ConversationState.DISCONNECTED -> "Desconectado"
                ConversationState.CONNECTING -> "Conectando..."
                ConversationState.IDLE -> "Listo para escuchar"
                ConversationState.LISTENING -> "Te estoy escuchando..."
                ConversationState.PROCESSING -> "Procesando tu mensaje..."
                ConversationState.AI_SPEAKING -> "Lyrion está hablando"
                ConversationState.AI_INTERRUPTED -> "IA interrumpida"
                ConversationState.ERROR -> "Error de conexión"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        // Subtexto descriptivo
        Text(
            text = when (state) {
                ConversationState.LISTENING -> "Habla con naturalidad"
                ConversationState.AI_SPEAKING -> "Escucha la respuesta"
                ConversationState.PROCESSING -> "Generando respuesta..."
                ConversationState.IDLE -> "Toca para iniciar conversación"
                ConversationState.CONNECTING -> "Estableciendo conexión..."
                ConversationState.ERROR -> "Verifica tu conexión"
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AnimatedMainButton(
    isSessionActive: Boolean,
    conversationState: ConversationState,
    stateColor: Color,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "button")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSessionActive) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(200.dp)
    ) {
        // Anillo animado externo (solo cuando está activo)
        if (isSessionActive) {
            Box(
                modifier = Modifier
                    .size(200.dp * scale)
                    .alpha(1f - (scale - 1f) * 10)
                    .background(
                        color = stateColor.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
            )
        }
        
        // Botón principal
        FloatingActionButton(
            onClick = onToggle,
            modifier = Modifier
                .size(140.dp)
                .scale(if (isSessionActive) scale else 1f),
            containerColor = if (isSessionActive) stateColor else MaterialTheme.colorScheme.surfaceVariant,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = if (isSessionActive) 16.dp else 8.dp
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = if (isSessionActive) "Detener sesión" else "Iniciar sesión",
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSessionActive) "DETENER" else "INICIAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TranscriptionCard(
    transcription: String,
    conversationState: ConversationState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📝 Transcripción",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                
                // Indicador de estado
                if (conversationState == ConversationState.AI_SPEAKING) {
                    Text(
                        text = "● EN VIVO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (transcription.isNotBlank()) {
                Text(
                    text = transcription,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = "Las respuestas aparecerán aquí...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun TextInputCard(
    textInput: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "💬 Modo Texto",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { 
                        Text(
                            "Escribe aquí...",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ) 
                    },
                    enabled = enabled,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                Button(
                    onClick = onSend,
                    enabled = enabled && textInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text("Enviar", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
*/

// ==================== FIN DEL CÓDIGO VIEJO COMENTADO ====================