/*
 * Copyright (C) 2025 Lyrion
 * Servicio de Asistente de Voz en Background
 * Mantiene Vosk escuchando hotword con pantalla bloqueada/apagada
 */

package io.orabel.orabelandroid.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import io.orabel.orabelandroid.R
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.json.JSONObject
import java.io.File
import java.util.Locale

class VoiceAssistantService : Service(), RecognitionListener {
    
    companion object {
        private const val TAG = "VoiceAssistantService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_assistant_channel"
        
        // Hotwords
        private const val HOTWORD_START = "asistente"
        private const val HOTWORD_ACTIVATE_GEMINI = "inteligencia" // Solo "inteligencia" es suficiente
        
        // Estados
        private const val STATE_LISTENING_HOTWORD = 0
        private const val STATE_WAITING_COMMAND = 1
        private const val STATE_GEMINI_ACTIVE = 2
    }
    
    // Vosk
    private var voskModel: Model? = null
    private var voskRecognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    
    // TTS
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    
    // WakeLock
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Estado
    private var currentState = STATE_LISTENING_HOTWORD
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 VoiceAssistantService onCreate")
        
        // Inicializar TTS
        initializeTTS()
        
        // Adquirir WakeLock
        acquireWakeLock()
        
        // Crear notificación y mover a foreground
        startForeground()
        
        // Inicializar Vosk
        initializeVosk()
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Lyrion::VoiceAssistant"
        ).apply {
            acquire()
            Log.d(TAG, "🔋 WakeLock adquirido - CPU se mantendrá activo")
        }
    }
    
    private fun startForeground() {
        createNotificationChannel()
        
        val notification = buildNotification("Escuchando hotword '$HOTWORD_START'")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        Log.d(TAG, "✅ Servicio iniciado en foreground con tipo MICROPHONE")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Canal normal para el servicio foreground
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Asistente de Voz",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio de detección de voz en segundo plano"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "✅ Canal de notificación creado")
        }
    }
    
    private fun buildNotification(contentText: String): Notification {
        val intent = Intent(this, io.orabel.orabelandroid.ui.screens.main.ModernMainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎤 Lyrion Asistente")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun initializeTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "ES")
                ttsReady = true
                Log.d(TAG, "✅ TTS inicializado correctamente")
            } else {
                Log.e(TAG, "❌ Error inicializando TTS")
            }
        }
    }
    
    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.d(TAG, "🔊 TTS: $text")
        }
    }
    
    private fun initializeVosk() {
        try {
            LibVosk.setLogLevel(LogLevel.INFO)
            
            // Verificar si el modelo existe (mismo path que IALiveActivity)
            val modelPath = File(filesDir, "models/vosk-model-small-es-0.42")
            if (!modelPath.exists()) {
                Log.w(TAG, "⚠️ Modelo Vosk no encontrado, copiando desde assets...")
                updateNotification("Preparando modelo de voz...")
                
                // Copiar modelo desde assets
                try {
                    copyModelFromAssets("vosk-model-small-es-0.42", modelPath)
                    Log.d(TAG, "✅ Modelo Vosk copiado exitosamente")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error copiando modelo desde assets: ${e.message}", e)
                    updateNotification("❌ Error: No se pudo copiar modelo de voz")
                    return
                }
            }
            
            // Cargar modelo
            voskModel = Model(modelPath.absolutePath)
            voskRecognizer = Recognizer(voskModel, 16000.0f)
            
            // Iniciar SpeechService
            speechService = SpeechService(voskRecognizer, 16000.0f)
            speechService?.startListening(this)
            
            Log.d(TAG, "✅ Vosk inicializado y escuchando hotword")
            currentState = STATE_LISTENING_HOTWORD
            updateNotification("Escuchando hotword '$HOTWORD_START'")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando Vosk: ${e.message}", e)
            updateNotification("❌ Error al iniciar reconocimiento de voz")
        }
    }
    
    // RecognitionListener implementation
    override fun onPartialResult(hypothesis: String?) {
        hypothesis?.let {
            try {
                val result = JSONObject(it)
                val partial = result.optString("partial", "").lowercase(Locale.getDefault())
                
                Log.d(TAG, "📝 Partial reconocido: '$partial' (Estado: $currentState)")
                
                when (currentState) {
                    STATE_LISTENING_HOTWORD -> {
                        if (partial.contains(HOTWORD_START)) {
                            Log.d(TAG, "🎯 Hotword '$HOTWORD_START' detectado!")
                            speak("Escucho")
                            updateNotification("Esperando comando...")
                            currentState = STATE_WAITING_COMMAND
                        }
                    }
                    
                    STATE_WAITING_COMMAND -> {
                        // Detectar "inteligencia" o "inteligencia artificial" o variaciones
                        if (partial.contains("inteligencia") || 
                            partial.contains("artificial") ||
                            partial.contains("gemini")) {
                            Log.d(TAG, "🎯 Comando de activación detectado en partial: '$partial'")
                            speak("Abriendo Gemini Live")
                            activateGeminiLive()
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando partial result: ${e.message}")
            }
        }
    }
    
    override fun onResult(hypothesis: String?) {
        hypothesis?.let {
            try {
                val result = JSONObject(it)
                val text = result.optString("text", "").lowercase(Locale.getDefault())
                
                Log.d(TAG, "📝 Result completo: '$text' (Estado: $currentState)")
                
                if (text.isEmpty()) return
                
                when (currentState) {
                    STATE_WAITING_COMMAND -> {
                        // Detectar "inteligencia" o "inteligencia artificial" o variaciones
                        if (text.contains("inteligencia") || 
                            text.contains("artificial") ||
                            text.contains("gemini")) {
                            Log.d(TAG, "🎯 Comando de activación detectado en result: '$text'")
                            speak("Abriendo Gemini Live")
                            activateGeminiLive()
                        } else {
                            // Volver a escuchar hotword después de 5 segundos sin comando válido
                            Log.d(TAG, "⏳ Comando no reconocido: '$text', volviendo a escuchar hotword en 5 seg")
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                currentState = STATE_LISTENING_HOTWORD
                                updateNotification("Escuchando hotword '$HOTWORD_START'")
                            }, 5000)
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando result: ${e.message}")
            }
        }
    }
    
    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }
    
    override fun onError(exception: Exception?) {
        Log.e(TAG, "❌ Error en reconocimiento: ${exception?.message}")
    }
    
    override fun onTimeout() {
        Log.d(TAG, "⏱️ Timeout en reconocimiento")
    }
    
    private fun activateGeminiLive() {
        // Verificar conexión a internet
        if (!isInternetAvailable()) {
            Log.w(TAG, "⚠️ Sin conexión a internet")
            speak("No hay conexión a internet")
            updateNotification("Sin conexión - Esperando hotword")
            
            // Volver a escuchar hotword
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                currentState = STATE_LISTENING_HOTWORD
                updateNotification("Escuchando hotword '$HOTWORD_START'")
            }, 3000)
            return
        }
        
        // Detener Vosk temporalmente
        currentState = STATE_GEMINI_ACTIVE
        speechService?.stop()
        Log.d(TAG, "⏸️ Vosk pausado - Iniciando Gemini Live")
        updateNotification("🤖 Gemini Live activo")
        
        try {
            // SOLUCIÓN: Intent directo con flags correctos para Android 10+
            val intent = Intent(this, io.orabel.orabelandroid.ui.screens.gemini_live.GeminiLiveActivity::class.java).apply {
                // ✅ CRÍTICO: Combinación de flags que permite abrir Activity desde Service
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                putExtra("from_voice_assistant", true)
            }
            
            startActivity(intent)
            Log.d(TAG, "✅ Gemini Live iniciado directamente desde servicio")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al abrir Gemini Live: ${e.message}")
            speak("Error al abrir Gemini Live")
            
            // Volver a escuchar hotword
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                speechService = SpeechService(voskRecognizer, 16000.0f)
                speechService?.startListening(this)
                currentState = STATE_LISTENING_HOTWORD
                updateNotification("Escuchando hotword '$HOTWORD_START'")
            }, 2000)
        }
        
        // El servicio sigue corriendo, esperará que GeminiLiveActivity finalice
    }
    
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                   capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }
    }
    
    /**
     * Llamado desde GeminiLiveActivity cuando el usuario finaliza la sesión
     */
    fun onGeminiLiveFinished() {
        Log.d(TAG, "▶️ Gemini Live finalizado - Reactivando Vosk")
        
        // Reiniciar Vosk
        speechService = SpeechService(voskRecognizer, 16000.0f)
        speechService?.startListening(this)
        
        currentState = STATE_LISTENING_HOTWORD
        updateNotification("Escuchando hotword '$HOTWORD_START'")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📥 onStartCommand")
        
        // Manejar comandos específicos
        when (intent?.action) {
            "GEMINI_FINISHED" -> {
                onGeminiLiveFinished()
            }
        }
        
        return START_STICKY // Reiniciar servicio si el sistema lo mata
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // No es un servicio vinculado
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 VoiceAssistantService onDestroy")
        
        // Detener Vosk
        speechService?.stop()
        speechService = null
        voskModel?.close()
        voskRecognizer = null
        
        // Liberar TTS
        tts?.stop()
        tts?.shutdown()
        
        // Liberar WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "🔋 WakeLock liberado")
            }
        }
    }
    
    /**
     * Copia el modelo Vosk desde assets al directorio interno
     * (Misma función que usa IALiveActivity)
     */
    private fun copyModelFromAssets(assetPath: String, targetDir: File) {
        targetDir.mkdirs()
        
        fun copyAssetFolder(assetPath: String, targetPath: String) {
            val assetManager = assets
            val files = assetManager.list(assetPath) ?: return
            
            val targetDirFile = File(targetPath)
            targetDirFile.mkdirs()
            
            for (file in files) {
                val assetFile = "$assetPath/$file"
                val targetFile = File(targetDirFile, file)
                
                val subFiles = assetManager.list(assetFile)
                if (subFiles != null && subFiles.isNotEmpty()) {
                    // Es un directorio, recursión
                    copyAssetFolder(assetFile, targetFile.absolutePath)
                } else {
                    // Es un archivo, copiarlo
                    assetManager.open(assetFile).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
        
        copyAssetFolder(assetPath, targetDir.absolutePath)
    }
}
