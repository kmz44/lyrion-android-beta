/*
 * Copyright (C) 2025 Lyrion
 * Cliente WebSocket REAL para Gemini 2.5 Flash Native Audio Live API
 * Implementación basada en la documentación oficial de Google
 * Documentación: https://ai.google.dev/gemini-api/docs/live
 */

package io.orabel.orabelandroid.gemini

import android.util.Base64
import android.util.Log
import io.orabel.orabelandroid.BuildConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket para Gemini 2.5 Flash Native Audio Live API
 * 
 * Modelo: gemini-2.5-flash-native-audio-preview-09-2025
 * Audio Input: PCM 16-bit, 16kHz, mono
 * Audio Output: PCM 16-bit, 24kHz, mono
 * 
 * Basado en la documentación oficial:
 * https://ai.google.dev/gemini-api/docs/live
 */
class GeminiLiveAudioClient(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
) {
    private companion object {
        const val TAG = "GeminiLiveAudio"
    // Modelo CORRECTO según documentación oficial (actualizado 2025)
    // Usar la versión más reciente de native audio (preview)
    // https://ai.google.dev/gemini-api/docs/models#gemini-2.5-flash-native-audio
    const val MODEL_NAME = "gemini-2.5-flash-native-audio-preview-09-2025"
        
        // URL base del WebSocket según documentación oficial
        // CRÍTICO: Usar v1beta (NO v1alpha) según https://ai.google.dev/api/live
        const val WS_BASE_URL = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }
    
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Sin timeout para streaming
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS) // Keep-alive cada 20s
        .build()
    
    private var webSocket: WebSocket? = null
    
    // 🔴 CRÍTICO: Usar var en lugar de val para poder recrear los canales al reconectar
    private var audioOutputChannel = Channel<ByteArray>(Channel.UNLIMITED)
    private var transcriptionChannel = Channel<String>(Channel.UNLIMITED)
    private var turnCompleteChannel = Channel<Unit>(Channel.UNLIMITED) // Señal de fin de turno
    
    @Volatile
    private var isConnected = false
    
    /**
     * Conecta al WebSocket de Gemini Live API
     */
    fun connect(
        systemInstruction: String,
        voiceConfig: String = "Aoede", // Voz por defecto
        onConnected: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (apiKey.isBlank()) {
            onError("API Key no configurada en BuildConfig.GEMINI_API_KEY")
            return
        }
        
        // 🔴 CRÍTICO: Recrear canales para reconexión
        // Si ya existen canales de una conexión anterior, cerrarlos y crear nuevos
        try {
            audioOutputChannel.close()
            transcriptionChannel.close()
            turnCompleteChannel.close()
        } catch (e: Exception) {
            // Ignorar si ya estaban cerrados
        }
        audioOutputChannel = Channel<ByteArray>(Channel.UNLIMITED)
        transcriptionChannel = Channel<String>(Channel.UNLIMITED)
        turnCompleteChannel = Channel<Unit>(Channel.UNLIMITED)
        Log.d(TAG, "🔄 Canales recreados para nueva conexión")
        
        // URL con API key como query parameter
        val url = "$WS_BASE_URL?key=$apiKey"
        
        Log.d(TAG, "🔄 Conectando a Gemini Live API...")
        Log.d(TAG, "Modelo: $MODEL_NAME")
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket CONECTADO exitosamente")
                Log.d(TAG, "Response: ${response.code} - ${response.message}")
                isConnected = true
                
                // Enviar mensaje de setup inmediatamente
                sendSetupMessage(systemInstruction, voiceConfig)
                onConnected()
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "📨 Mensaje de texto recibido")
                Log.d(TAG, "📨 MENSAJE COMPLETO: $text")
                handleTextMessage(text)
            }
            
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Gemini NO envía audio binario directo, sino JSON con base64
                // Intentar parsear como JSON primero
                try {
                    val jsonText = bytes.utf8()
                    
                    // CRÍTICO: Verificar SIEMPRE si contiene turnComplete/generationComplete
                    // (El mensaje puede ser muy largo por el audio en base64)
                    val hasTurnComplete = jsonText.contains("\"turnComplete\"")
                    val hasGenerationComplete = jsonText.contains("\"generationComplete\"")
                    if (hasTurnComplete || hasGenerationComplete) {
                        Log.d(TAG, "📨 ⚠️ MENSAJE CON SEÑAL DE FIN DE TURNO DETECTADA ⚠️")
                        Log.d(TAG, "📨 turnComplete: $hasTurnComplete, generationComplete: $hasGenerationComplete")
                    }
                    
                    Log.d(TAG, "📨 Mensaje binario como texto: ${jsonText.take(200)}...")
                    handleTextMessage(jsonText)
                } catch (e: Exception) {
                    // Si falla el parseo, tratar como audio PCM directo (fallback)
                    val audioBytes = bytes.toByteArray()
                    Log.d(TAG, "🔊 Audio PCM recibido: ${audioBytes.size} bytes (24kHz)")
                    
                    // IMPORTANTE: Solo enviar audio REAL al canal, no mensajes de protocolo
                    if (audioBytes.size > 100) {
                        audioOutputChannel.trySend(audioBytes)
                        Log.d(TAG, "✅ Audio enviado al reproductor: ${audioBytes.size} bytes")
                    } else {
                        Log.w(TAG, "⚠️ Audio muy pequeño (${audioBytes.size} bytes), probablemente mensaje de protocolo - IGNORADO")
                    }
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ ERROR WebSocket", t)
                Log.e(TAG, "Response: ${response?.code} - ${response?.message}")
                Log.e(TAG, "Body: ${response?.body?.string()}")
                isConnected = false
                onError("${t.message} (Code: ${response?.code})")
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "⚠️ WebSocket cerrándose: Code=$code, Reason=$reason")
                isConnected = false
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "🔌 WebSocket CERRADO: Code=$code, Reason=$reason")
                isConnected = false
            }
        })
    }
    
    /**
     * Envía el mensaje de configuración inicial (SETUP)
     * Formato basado en documentación oficial de WebSocket API
     * https://ai.google.dev/api/live#BidiGenerateContentSetup
     */
    private fun sendSetupMessage(systemInstruction: String, voiceConfig: String) {
        val setupMessage = JSONObject().apply {
            put("setup", JSONObject().apply {
                // Modelo con audio nativo
                put("model", "models/$MODEL_NAME")
                
                // Configuración de generación (camelCase según API)
                put("generationConfig", JSONObject().apply {
                    // CRÍTICO: Modalidad AUDIO para respuesta nativa de voz
                    put("responseModalities", JSONArray(listOf("AUDIO")))
                    
                    // Configuración de voz
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceConfig)
                            })
                        })
                    })
                })
                
                // Instrucción del sistema (camelCase según API)
                // FORMATO CORRECTO: Content type con parts[]
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
            })
        }
        
        val messageStr = setupMessage.toString()
        webSocket?.send(messageStr)
        Log.d(TAG, "📤 SETUP MESSAGE ENVIADO:")
        Log.d(TAG, messageStr)
    }
    
    /**
     * Envía audio PCM 16-bit, 16kHz, mono al servidor
     * Formato según documentación oficial: BidiGenerateContentRealtimeInput
     */
    fun sendAudio(audioData: ByteArray) {
        if (!isConnected) {
            Log.w(TAG, "⚠️ No conectado, ignorando audio")
            return
        }
        
        if (audioData.isEmpty()) {
            return
        }
        
        // Convertir audio a Base64
        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
        
        // FORMATO CORRECTO según documentación oficial de WebSocket API
        // https://ai.google.dev/api/live#BidiGenerateContentRealtimeInput
        val message = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                // Campo "audio" tipo Blob con mimeType y data
                put("audio", JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Audio)
                })
            })
        }
        
        val messageStr = message.toString()
        webSocket?.send(messageStr)
        Log.v(TAG, "🎤 Audio enviado: ${audioData.size} bytes (16kHz PCM)")
        // Log detallado del JSON (solo primeros 500 chars para no saturar logs)
        if (messageStr.length > 500) {
            Log.v(TAG, "📤 JSON Audio (primeros 500 chars): ${messageStr.take(500)}...")
        } else {
            Log.v(TAG, "📤 JSON Audio completo: $messageStr")
        }
    }
    
    /**
     * Envía mensaje de texto opcional (para comandos o preguntas de texto)
     * Formato según BidiGenerateContentClientContent
     */
    fun sendText(text: String) {
        if (!isConnected) {
            Log.w(TAG, "⚠️ No conectado")
            return
        }
        
        val message = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        })
                    })
                })
                // CRÍTICO: Indicar que el turno está completo para que Gemini genere la respuesta
                put("turnComplete", true)
            })
        }
        
        webSocket?.send(message.toString())
        Log.d(TAG, "📤 Texto enviado: $text")
        Log.d(TAG, "📤 JSON completo: ${message.toString()}")
    }
    
    /**
     * CRÍTICO: Indica a Gemini que el turno de audio ha terminado y debe generar respuesta
     * DEBE llamarse después de enviar todo el audio para provocar la respuesta del modelo
     * 
     * SOLUCIÓN: Enviar un paquete de audio VACÍO para señalar fin de turno
     * Esto fuerza al modelo a generar respuesta inmediatamente
     */
    fun commitAudioBuffer() {
        if (!isConnected) {
            Log.w(TAG, "⚠️ No conectado")
            return
        }
        
        try {
            // Enviar un chunk de audio vacío (señal de fin de turno)
            // Esto le dice a Gemini que el usuario terminó de hablar y debe generar respuesta
            val endOfTurnMessage = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray()) // Array vacío = fin de turno
                })
            }
            
            webSocket?.send(endOfTurnMessage.toString())
            Log.d(TAG, "📤 FIN DE TURNO enviado (mediaChunks vacío)")
            Log.d(TAG, "⏳ Esperando respuesta de Gemini...")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando fin de turno", e)
        }
    }
    
    /**
     * Procesa mensajes de texto del servidor
     */
    private fun handleTextMessage(text: String) {
        try {
            val json = JSONObject(text)
            
            // 1. Confirmación de setup
            if (json.has("setupComplete")) {
                // setupComplete es un objeto vacío {}, NO un boolean
                Log.d(TAG, "✅ SETUP COMPLETADO exitosamente")
                Log.d(TAG, "Sistema listo para recibir audio")
                return
            }
            
            // 2. Respuestas del servidor (serverContent)
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")
                
                // Turno del modelo (model_turn)
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            
                            // Transcripción de texto (opcional, si el modelo responde con texto)
                            if (part.has("text")) {
                                val transcription = part.getString("text")
                                transcriptionChannel.trySend(transcription)
                                Log.d(TAG, "📝 Transcripción: $transcription")
                            }
                            
                            // Audio inline en base64 (aunque normalmente viene como binario)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")
                                val audioBase64 = inlineData.getString("data")
                                val audioBytes = Base64.decode(audioBase64, Base64.NO_WRAP)
                                audioOutputChannel.trySend(audioBytes)
                                Log.d(TAG, "🔊 Audio inline recibido: ${audioBytes.size} bytes ($mimeType)")
                            }
                        }
                    }
                }
                
                // Mensajes de interrupción (si el usuario interrumpe al modelo)
                if (serverContent.has("interrupted")) {
                    val interrupted = serverContent.getBoolean("interrupted")
                    if (interrupted) {
                        Log.d(TAG, "⏸️ Modelo interrumpido por el usuario")
                    }
                }
                
                // Fin de turno del modelo (turnComplete O generationComplete)
                // CRÍTICO: La API puede enviar cualquiera de los dos campos
                if (serverContent.has("turnComplete") || serverContent.has("generationComplete")) {
                    val isComplete = serverContent.optBoolean("turnComplete", false) || 
                                    serverContent.optBoolean("generationComplete", false)
                    if (isComplete) {
                        Log.d(TAG, "✅ Turno del modelo completado (generationComplete o turnComplete)")
                        turnCompleteChannel.trySend(Unit)
                    }
                }
            }
            
            // 3. Errores del servidor
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorCode = error.optInt("code", -1)
                val errorMsg = error.optString("message", "Error desconocido")
                Log.e(TAG, "❌ Error del servidor: Code=$errorCode, Message=$errorMsg")
            }
            
            // 4. Información de uso (tokens, etc.)
            if (json.has("usageMetadata")) {
                val usage = json.getJSONObject("usageMetadata")
                Log.v(TAG, "📊 Metadata de uso: $usage")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error parseando mensaje JSON", e)
            Log.e(TAG, "Mensaje problemático: ${text.take(1000)}")
        }
    }
    
    /**
     * Flow de audio de salida (PCM 24kHz, 16-bit, mono)
     */
    fun receiveAudio(): Flow<ByteArray> = audioOutputChannel.receiveAsFlow()
    
    /**
     * Flow de transcripciones (texto de respuestas)
     */
    fun receiveTranscriptions(): Flow<String> = transcriptionChannel.receiveAsFlow()
    
    /**
     * Flow de eventos de fin de turno del modelo
     */
    fun receiveTurnComplete(): Flow<Unit> = turnCompleteChannel.receiveAsFlow()
    
    /**
     * Desconecta el WebSocket y libera recursos
     */
    fun disconnect() {
        if (webSocket != null) {
            Log.d(TAG, "🔌 Desconectando...")
            webSocket?.close(1000, "Cliente cerrado por el usuario")
            webSocket = null
            isConnected = false
            
            // Cerrar TODOS los canales
            try {
                audioOutputChannel.close()
                transcriptionChannel.close()
                turnCompleteChannel.close() // ← AGREGADO
            } catch (e: Exception) {
                Log.d(TAG, "⚠️ Canales ya estaban cerrados")
            }
            
            Log.d(TAG, "✅ Desconectado correctamente")
        }
    }
    
    /**
     * Verifica si está conectado
     */
    fun isConnected(): Boolean = isConnected
}
