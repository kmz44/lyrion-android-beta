package io.orabel.orabelandroid.ui.screens.geminilive

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.orabel.orabelandroid.BuildConfig
import io.orabel.orabelandroid.ui.theme.ModernOrabelTheme
import io.orabel.orabelandroid.data.OrabelPreferences
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.android.ext.android.inject as koinInject
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import android.util.Base64
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.data.HealthDiaryRepository
import org.koin.android.ext.android.inject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Activity base para "Gemini Live": envía micrófono y (opcionalmente) frames de cámara/pantalla por WebSocket
 * y reproduce el audio de retorno (24kHz PCM) del modelo.
 *
 * NOTA: Esta implementación usa el WebSocket de la Live API (v1beta) directamente con OkHttp.
 * Se envía un setup inicial y luego blobs de audio/imagen en mensajes JSON + binarios simples.
 * Para producción se recomienda usar tokens efímeros y/o el SDK oficial cuando exponga Live en Java.
 * 
 * FUNCIONALIDAD DE SALUD: Integra HealthDiaryRepository para analizar automáticamente
 * los mensajes y guardar síntomas, estados de ánimo, dolores, etc.
 */
class GeminiLiveActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val healthDiaryRepository: HealthDiaryRepository by inject()
    private val orabelPreferences: OrabelPreferences by koinInject()

    private var ws: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isStreaming = false
    
    // Voice Activity Detection (VAD)
    private var isUserSpeaking = false
    private var isBotSpeaking = false
    private var silenceFramesCount = 0
    private val SILENCE_THRESHOLD = -50.0 // dB
    private val SILENCE_FRAMES_TO_SEND = 30 // ~1 segundo de silencio a 16kHz
    private val audioBuffer = ByteArrayOutputStream()
    private var isConversationActive = false

    // System prompts
    private var baseSystemPrompt: String = ""
    private var userSystemPrompt: String = ""
    
    // Tracking de mensajes para análisis de salud
    private val conversationMessages = mutableListOf<String>()

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    // CameraX
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraAnalysis: ImageAnalysis? = null
    private var isCameraSharing = false
    private var lastCameraSentAtMs = 0L
    private var gotFirstAudio = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (!granted) finish()
    }

    private val requestProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(result.resultCode, result.data!!)
            // Android 14+: iniciar foreground service de tipo mediaProjection antes de capturar
            runCatching {
                val svc = Intent(this, GeminiProjectionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
            }
            startScreenCapture()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cargar el system prompt base desde el archivo de recursos
        loadBaseSystemPrompt()
        
        // Cargar el system prompt del usuario desde preferencias (si existe)
        loadUserSystemPrompt()

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(darkTheme = isDarkTheme) {
                UI(
                    onStart = { startLive() },
                    onStop = { stopLive() },
                    onShareCamera = { toggleCameraShare() },
                    onShareScreen = { requestScreenShare() },
                    currentUserPrompt = userSystemPrompt,
                    onSaveUserPrompt = { newPrompt -> saveUserSystemPrompt(newPrompt) }
                )
            }
        }

        ensurePermissions()
    }
    
    /**
     * Carga el system prompt base desde res/raw/gemini_base_system_prompt.txt
     * Este prompt NO puede ser modificado por el usuario
     */
    private fun loadBaseSystemPrompt() {
        try {
            val inputStream = resources.openRawResource(R.raw.gemini_base_system_prompt)
            val reader = BufferedReader(InputStreamReader(inputStream))
            baseSystemPrompt = reader.use { it.readText() }
            Log.d(TAG, "✅ Base system prompt cargado: ${baseSystemPrompt.length} caracteres")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando base system prompt", e)
            baseSystemPrompt = "Eres Lyrion, un asistente de IA especializado en salud y bienestar."
        }
    }
    
    /**
     * Carga el system prompt personalizable del usuario desde SharedPreferences
     */
    private fun loadUserSystemPrompt() {
        val prefs = getSharedPreferences("gemini_live_prefs", Context.MODE_PRIVATE)
        userSystemPrompt = prefs.getString("user_system_prompt", "") ?: ""
        Log.d(TAG, "User system prompt: ${if (userSystemPrompt.isEmpty()) "vacío" else "${userSystemPrompt.length} caracteres"}")
    }
    
    /**
     * Guarda el system prompt del usuario
     */
    fun saveUserSystemPrompt(prompt: String) {
        userSystemPrompt = prompt
        val prefs = getSharedPreferences("gemini_live_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_system_prompt", prompt).apply()
        Log.d(TAG, "💾 User system prompt guardado")
    }

    private fun ensurePermissions() {
        val basePerms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= 33) {
            basePerms += Manifest.permission.POST_NOTIFICATIONS
        }
        val needed = basePerms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) {
            requestPermissions.launch(needed.toTypedArray())
        }
    }

    private fun requestScreenShare() {
        // Android 14+: iniciar el servicio en primer plano antes de solicitar captura
        runCatching {
            val svc = Intent(this, GeminiProjectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
        }
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestProjection.launch(mgr.createScreenCaptureIntent())
    }

    private fun startLive() {
        if (isStreaming) return
        // Verificar API key antes de iniciar
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isNullOrBlank()) {
            Log.e(TAG, "Falta configurar GEMINI_API_KEY en local.properties")
            return
        }
        isStreaming = true
        openWebSocket()
        startMicCapture()
        // La cámara puede añadirse luego; la pantalla se activa desde botón
    }

    private fun stopLive() {
        isStreaming = false
        runCatching { audioRecord?.stop(); audioRecord?.release() }.onFailure { }
        audioRecord = null
        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.release() }.onFailure { }
        audioTrack = null
        ws?.close(1000, "bye")
        ws = null
        stopScreenCapture()
    }

    override fun onDestroy() {
        stopLive()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------ WebSocket ------------------
    private fun openWebSocket() {
        val client = OkHttpClient()

        // Live API endpoint (v1beta)
        val baseUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        val apiKey = BuildConfig.GEMINI_API_KEY
        val urlWithKey = if (apiKey.isNullOrBlank()) baseUrl else "$baseUrl?key=$apiKey"
        val req = Request.Builder()
            .url(urlWithKey)
            .build()

        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WS open")
                
                // Combinar system prompts: base (fijo) + usuario (personalizable)
                val combinedSystemPrompt = buildString {
                    append(baseSystemPrompt)
                    if (userSystemPrompt.isNotEmpty()) {
                        append("\n\n")
                        append("--- INSTRUCCIONES ADICIONALES DEL USUARIO ---\n")
                        append(userSystemPrompt)
                    }
                }
                
                Log.d(TAG, "📝 Enviando system prompt combinado (${combinedSystemPrompt.length} caracteres)")
                
                // Enviar setup inicial con system instruction
                val setupJson = JSONObject().apply {
                    put("setup", JSONObject().apply {
                        put("model", "models/gemini-2.5-flash-native-audio-preview-09-2025")
                        
                        // Agregar system instruction
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", combinedSystemPrompt)
                                })
                            })
                        })
                        
                        put("generationConfig", JSONObject().apply {
                            put("responseModalities", JSONArray().apply {
                                put("AUDIO")
                            })
                        })
                        
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Zephyr")
                                })
                            })
                        })
                    })
                }
                
                webSocket.send(setupJson.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Fallback: algunos backends envían audio PCM16 como binario
                val data = bytes.toByteArray()
                if (data.isNotEmpty()) {
                    playPcm(data, 24000)
                    if (!gotFirstAudio) {
                        gotFirstAudio = true
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WS closing $code $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WS failure", t)
            }
        })
    }

    private fun sendAudioPcm(pcm16: ByteArray) {
        val b64 = Base64.encodeToString(pcm16, Base64.NO_WRAP)
        val msg = """
            {"realtimeInput":{"mediaChunks":[{"data":"$b64","mimeType":"audio/pcm; rate=16000"}]}}
        """.trimIndent()
        ws?.send(msg)
    }
    
    /**
     * Calcula la amplitud RMS del audio en dB
     */
    private fun calculateAmplitude(audioData: ByteArray): Double {
        var sum = 0.0
        val shortArray = ShortArray(audioData.size / 2)
        ByteBuffer.wrap(audioData).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArray)
        
        for (sample in shortArray) {
            sum += (sample * sample).toDouble()
        }
        
        val rms = kotlin.math.sqrt(sum / shortArray.size)
        return if (rms > 0) 20 * kotlin.math.log10(rms) else -100.0
    }
    
    /**
     * Envía señal de que el usuario terminó su turno
     */
    private fun sendTurnComplete() {
        val msg = """
            {"clientContent":{"turnComplete":true}}
        """.trimIndent()
        ws?.send(msg)
        Log.d(TAG, "✅ Turn complete enviado")
    }

    private fun ensureAudioTrack(sampleRate: Int) {
        val sr = if (sampleRate in 8000..48000) sampleRate else 24000
        if (audioTrack != null && audioTrack!!.sampleRate == sr) return
        runCatching { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.release() }
        val min = AudioTrack.getMinBufferSize(
            sr,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sr)
                        .build()
                )
                .setBufferSizeInBytes(min.coerceAtLeast(4096))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                sr,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                min.coerceAtLeast(4096),
                AudioTrack.MODE_STREAM
            )
        }
        audioTrack?.play()
    }

    private fun playPcm(bytes: ByteArray, sampleRate: Int) {
        // Indicar que la IA está hablando
        if (!isBotSpeaking) {
            isBotSpeaking = true
            Log.d(TAG, "🤖 IA comenzó a hablar")
        }
        
        ensureAudioTrack(sampleRate)
        audioTrack?.write(bytes, 0, bytes.size)
        
        // Detectar cuando termina de hablar (después del último chunk de audio)
        scope.launch {
            delay(500) // Esperar medio segundo después del último audio
            if (isBotSpeaking) {
                isBotSpeaking = false
                Log.d(TAG, "🤖 IA terminó de hablar")
            }
        }
    }

    private fun handleServerMessage(text: String) {
        // Busca bloques de audio con base64 y mimeType de audio, reproduce al vuelo.
        try {
            val json = JSONObject(text)
            
            // Extraer texto de la respuesta para análisis de salud
            val responseText = extractTextFromResponse(json)
            if (responseText.isNotEmpty()) {
                conversationMessages.add(responseText)
                Log.d(TAG, "📝 Texto extraído: ${responseText.take(100)}...")
                
                // Analizar si hay información de salud relevante
                scope.launch {
                    try {
                        val wasRelevant = healthDiaryRepository.analyzeAndSaveIfRelevant(
                            responseText,
                            chatId = 0L // GeminiLive no usa chatId tradicional, usar 0
                        )
                        if (wasRelevant) {
                            Log.d(TAG, "✅ Información de salud guardada en bitácora")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error analizando salud", e)
                    }
                }
            }
            
            // Buscamos recursivamente audio data
            val results = mutableListOf<Pair<Int, ByteArray>>()
            scanForAudio(json, results)
            results.forEach { (sr, data) -> playPcm(data, sr) }
            if (results.isNotEmpty() && !gotFirstAudio) {
                gotFirstAudio = true
            }
            if (results.isEmpty()) {
                Log.d(TAG, "WS text: $text")
            }
        } catch (e: JSONException) {
            Log.d(TAG, "WS text (no JSON): $text")
        }
    }
    
    /**
     * Extrae texto de la respuesta del servidor (puede estar en diferentes formatos)
     */
    private fun extractTextFromResponse(json: JSONObject): String {
        val textParts = mutableListOf<String>()
        
        fun scanForText(any: Any) {
            when (any) {
                is JSONObject -> {
                    // Buscar campos "text" comunes
                    any.optString("text", "").takeIf { it.isNotEmpty() }?.let { textParts.add(it) }
                    any.optString("content", "").takeIf { it.isNotEmpty() }?.let { textParts.add(it) }
                    
                    // Revisar partes del mensaje
                    any.optJSONArray("parts")?.let { parts ->
                        for (i in 0 until parts.length()) {
                            parts.optJSONObject(i)?.let { part ->
                                scanForText(part)
                            }
                        }
                    }
                    
                    // Recursión en todos los campos
                    val keys = any.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        any.opt(k)?.let { child -> scanForText(child) }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until any.length()) {
                        any.opt(i)?.let { child -> scanForText(child) }
                    }
                }
            }
        }
        
        scanForText(json)
        return textParts.joinToString("\n").trim()
    }

    private fun scanForAudio(any: Any, out: MutableList<Pair<Int, ByteArray>>) {
        when (any) {
            is JSONObject -> {
                // Si tiene mimeType y data, comprobar si es audio
                val keys = any.keys()
                var mime: String? = null
                var data: String? = null
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = any.opt(k)
                    if (k.equals("mimeType", true) && v is String) mime = v
                    if (k.equals("data", true) && v is String) data = v
                }
                if (mime != null && mime!!.startsWith("audio/", true) && !data.isNullOrBlank()) {
                    val sr = mime!!.substringAfter("rate=", "24000").toIntOrNull() ?: 24000
                    val pcm = try { Base64.decode(data, Base64.DEFAULT) } catch (_: Throwable) { null }
                    if (pcm != null) out.add(sr to pcm)
                }
                // Recursión
                val it = any.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    any.opt(k)?.let { child -> scanForAudio(child, out) }
                }
            }
            is JSONArray -> {
                for (i in 0 until any.length()) {
                    any.opt(i)?.let { child -> scanForAudio(child, out) }
                }
            }
        }
    }

    // ------------------ Mic capture ------------------
    private fun startMicCapture() {
        val minBuf = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2
        )
        audioRecord?.startRecording()
        
        scope.launch {
            val buf = ByteArray(minBuf)
            while (isActive && isStreaming && audioRecord != null) {
                val n = audioRecord!!.read(buf, 0, buf.size)
                if (n > 0) {
                    val audioChunk = if (n == buf.size) buf else buf.copyOf(n)
                    
                    // Detectar actividad de voz
                    val amplitude = calculateAmplitude(audioChunk)
                    val isSpeaking = amplitude > SILENCE_THRESHOLD
                    
                    if (isSpeaking && !isBotSpeaking) {
                        // Usuario está hablando
                        if (!isUserSpeaking) {
                            Log.d(TAG, "🎤 Usuario comenzó a hablar")
                            isUserSpeaking = true
                            audioBuffer.reset()
                        }
                        
                        // Acumular audio
                        audioBuffer.write(audioChunk)
                        silenceFramesCount = 0
                        
                    } else if (isUserSpeaking && !isBotSpeaking) {
                        // Usuario dejó de hablar (silencio detectado)
                        silenceFramesCount++
                        audioBuffer.write(audioChunk) // Incluir el silencio final
                        
                        if (silenceFramesCount >= SILENCE_FRAMES_TO_SEND) {
                            // Enviar todo el audio acumulado
                            val completeAudio = audioBuffer.toByteArray()
                            if (completeAudio.size > 16000) { // Al menos 1 segundo de audio
                                Log.d(TAG, "📤 Enviando audio completo: ${completeAudio.size} bytes")
                                sendAudioPcm(completeAudio)
                                sendTurnComplete() // Indicar que el turno del usuario terminó
                            }
                            
                            // Reset para próxima frase
                            isUserSpeaking = false
                            audioBuffer.reset()
                            silenceFramesCount = 0
                        }
                    }
                }
            }
        }
    }

    // ------------------ Screen capture ------------------
    private fun startScreenCapture() {
        val proj = mediaProjection ?: return
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        val width = display?.mode?.physicalWidth ?: 1080
        val height = display?.mode?.physicalHeight ?: 1920
        val dpi = resources.displayMetrics.densityDpi

        screenWidth = width / 2
        screenHeight = height / 2
    imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        val surface: Surface = imageReader!!.surface
        proj.createVirtualDisplay(
            "GeminiLiveVD",
            screenWidth,
            screenHeight,
            dpi,
            0,
            surface,
            null,
            null
        )

        scope.launch {
            while (isActive && isStreaming && imageReader != null) {
                val image = imageReader!!.acquireLatestImage()
                if (image != null) {
                    try {
                        val jpeg = rgbaImageToJpeg(image)
                        if (jpeg != null) sendScreenFrame(jpeg)
                    } catch (_: Throwable) {
                    } finally {
                        image.close()
                    }
                }
                delay(1000) // 1 fps
            }
        }
    }

    private fun sendScreenFrame(jpegData: ByteArray) {
        val b64 = Base64.encodeToString(jpegData, Base64.NO_WRAP)
        val msg = """
            {"realtimeInput":{"mediaChunks":[{"data":"$b64","mimeType":"image/jpeg"}]}}
        """.trimIndent()
        ws?.send(msg)
    }

    private fun rgbaImageToJpeg(image: Image): ByteArray? {
        return try {
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride // should be 4
            val rowStride = plane.rowStride
            val width = image.width
            val height = image.height

            val rowPadding = rowStride - pixelStride * width
            val bmpWidth = rowStride / pixelStride
            val bitmap = Bitmap.createBitmap(bmpWidth, height, Bitmap.Config.ARGB_8888)
            plane.buffer.rewind()
            bitmap.copyPixelsFromBuffer(plane.buffer)

            val cropped = if (rowPadding != 0) Bitmap.createBitmap(bitmap, 0, 0, width, height) else bitmap
            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 70, baos)
            baos.toByteArray()
        } catch (e: Throwable) {
            Log.e(TAG, "rgbaImageToJpeg error", e)
            null
        }
    }

    // ------------------ Camera capture (ImageAnalysis) ------------------
    private fun toggleCameraShare() {
        if (isCameraSharing) stopCameraShare() else startCameraShare()
    }

    private fun startCameraShare() {
        if (isCameraSharing) return
        isCameraSharing = true

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val provider = cameraProvider ?: return@addListener

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { image: ImageProxy ->
                try {
                    val now = System.currentTimeMillis()
                    if (now - lastCameraSentAtMs >= 1000) {
                        val jpeg = yuv420ToJpeg(image)
                        if (jpeg != null && isStreaming) {
                            sendCameraFrame(jpeg)
                            lastCameraSentAtMs = now
                        }
                    }
                } catch (_: Throwable) {
                } finally {
                    image.close()
                }
            }

            cameraAnalysis = analysis
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, cameraSelector, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "Camera bind error", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraShare() {
        isCameraSharing = false
        runCatching { cameraProvider?.unbindAll() }
        cameraAnalysis = null
    }

    private fun sendCameraFrame(jpegData: ByteArray) {
        val b64 = Base64.encodeToString(jpegData, Base64.NO_WRAP)
        val msg = """
            {"realtimeInput":{"mediaChunks":[{"data":"$b64","mimeType":"image/jpeg"}]}}
        """.trimIndent()
        ws?.send(msg)
    }

    private fun yuv420ToJpeg(image: ImageProxy): ByteArray? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = yPlane.buffer.remaining()
        val uSize = uPlane.buffer.remaining()
        val vSize = vPlane.buffer.remaining()

        // Convertir a NV21
        val nv21 = ByteArray(ySize + uSize + vSize)
        yPlane.buffer.get(nv21, 0, ySize)

        val chromaRowStride = uPlane.rowStride
        val chromaPixelStride = uPlane.pixelStride
        var offset = ySize
        val width = image.width
        val height = image.height
        val uvHeight = height / 2

        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val row = ByteArray(chromaRowStride)
        for (rowIndex in 0 until uvHeight) {
            val rowStart = rowIndex * chromaRowStride
            uBuffer.position(rowStart)
            vBuffer.position(rowStart)
            uBuffer.get(row, 0, chromaRowStride)
            for (col in 0 until width / 2) {
                val u = row[col * chromaPixelStride]
                val v = vBuffer.get(rowStart + col * chromaPixelStride)
                nv21[offset++] = v
                nv21[offset++] = u
            }
        }

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 70, out)
            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "yuv420ToJpeg error", e)
            null
        }
    }

    private fun stopScreenCapture() {
        imageReader?.close(); imageReader = null
        mediaProjection?.stop(); mediaProjection = null
    // Detener el servicio en primer plano si estaba corriendo
    runCatching { stopService(Intent(this, GeminiProjectionService::class.java)) }
    }

    companion object {
        private const val TAG = "GeminiLive"
    }
}

@Composable
private fun UI(
    onStart: () -> Unit,
    onStop: () -> Unit,
    onShareCamera: () -> Unit,
    onShareScreen: () -> Unit,
    currentUserPrompt: String,
    onSaveUserPrompt: (String) -> Unit
) {
    var running by remember { mutableStateOf(false) }
    var showSystemPromptDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gemini Live") },
                actions = {
                    IconButton(onClick = { showSystemPromptDialog = true }) {
                        Icon(Icons.Default.Settings, "Configurar system prompt")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Habla y escucha respuestas en tiempo real. Opcional: comparte cámara o pantalla.")
            
            Text(
                "✅ Sistema configurado para análisis de salud automático",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    if (!running) { onStart(); running = true }
                }, enabled = !running) { Text("Iniciar") }
                Button(onClick = {
                    if (running) { onStop(); running = false }
                }, enabled = running) { Text("Detener") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onShareCamera) { Text("Compartir cámara") }
                OutlinedButton(onClick = onShareScreen) { Text("Compartir pantalla") }
            }
        }
    }
    
    if (showSystemPromptDialog) {
        SystemPromptDialog(
            currentPrompt = currentUserPrompt,
            onSave = onSaveUserPrompt,
            onDismiss = { showSystemPromptDialog = false }
        )
    }
}

@Composable
private fun SystemPromptDialog(
    currentPrompt: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var userPrompt by remember { 
        mutableStateOf(currentPrompt) 
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar System Prompt Personalizado") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "El sistema ya incluye un prompt base especializado en análisis de salud " +
                    "(síntomas, estados de ánimo, dolores, etc.). Este prompt base NO puede " +
                    "ser modificado para garantizar la funcionalidad correcta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Divider()
                
                Text(
                    "Agrega instrucciones adicionales personalizadas (opcional):",
                    style = MaterialTheme.typography.titleSmall
                )
                
                OutlinedTextField(
                    value = userPrompt,
                    onValueChange = { userPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    placeholder = { 
                        Text("Ejemplo: También quiero que respondas en un tono más formal...") 
                    },
                    maxLines = 10
                )
                
                Text(
                    "Estas instrucciones se combinarán con el prompt base del sistema.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(userPrompt)
                onDismiss()
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
