/*
 * Copyright (C) 2025 Lyrion
 * Manejo de captura y reproducción de audio para Gemini Live
 */

package io.orabel.orabelandroid.gemini

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gestor de audio para Gemini Live
 * Captura audio del micrófono (16kHz PCM) y reproduce respuestas (24kHz PCM)
 */
class GeminiAudioManager(private val context: Context) {
    
    companion object {
        private const val TAG = "GeminiAudioManager"
        
        // Configuración de entrada (micrófono)
        private const val INPUT_SAMPLE_RATE = 16000
        private const val INPUT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val INPUT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Configuración de salida (parlante) - MONO (Gemini envía 24kHz MONO)
        private const val OUTPUT_SAMPLE_RATE = 24000
        private const val OUTPUT_CHANNEL_CONFIG = 0x4 // CHANNEL_OUT_MONO correcto
        private const val OUTPUT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // Tamaño de buffer (0.1 segundos de audio)
        private const val INPUT_BUFFER_SIZE_MS = 100
        private const val OUTPUT_BUFFER_SIZE_MS = 100
    }
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val audioMutex = Mutex() // Protección thread-safe
    
    /**
     * Verifica si tiene permiso de micrófono
     */
    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Inicia la captura de audio del micrófono
     * @return Flow de ByteArray con audio PCM 16kHz
     */
    fun startRecording(): Flow<ByteArray> = flow {
        // CRÍTICO: Verificar que no haya una grabación activa
        if (isRecording || audioRecord != null) {
            Log.w(TAG, "⚠️ Ya hay una grabación activa, deteniendo la anterior primero...")
            stopRecording()
            // Dar tiempo para que se libere el recurso
            kotlinx.coroutines.delay(100)
        }
        
        if (!hasAudioPermission()) {
            Log.e(TAG, "❌ Permiso de micrófono no otorgado")
            return@flow
        }
        
        try {
            val minBufferSize = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                INPUT_CHANNEL_CONFIG,
                INPUT_AUDIO_FORMAT
            )
            
            // Buffer para 100ms de audio
            val bufferSize = maxOf(
                minBufferSize,
                (INPUT_SAMPLE_RATE * INPUT_BUFFER_SIZE_MS / 1000) * 2 // *2 por 16-bit
            )
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                INPUT_SAMPLE_RATE,
                INPUT_CHANNEL_CONFIG,
                INPUT_AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord no inicializado")
                return@flow
            }
            
            audioRecord?.startRecording()
            isRecording = true
            Log.d(TAG, "🎤 Grabación iniciada (${INPUT_SAMPLE_RATE}Hz, buffer: $bufferSize bytes)")
            
            val audioBuffer = ByteArray(bufferSize)
            
            while (isRecording && audioRecord != null) {
                val bytesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
                
                if (bytesRead > 0) {
                    // Emitir datos de audio
                    val audioData = audioBuffer.copyOf(bytesRead)
                    emit(audioData)
                } else if (bytesRead < 0) {
                    Log.e(TAG, "Error leyendo audio: $bytesRead")
                    break
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error en grabación", e)
        } finally {
            stopRecording()
        }
    }
    
    /**
     * Detiene la captura de audio
     * IDEMPOTENTE: Se puede llamar múltiples veces sin crash
     */
    fun stopRecording() {
        try {
            if (!isRecording && audioRecord == null) {
                // Ya está detenido, no hacer nada
                Log.d(TAG, "🎤 Grabación ya está detenida (ignorando)")
                return
            }
            
            isRecording = false
            
            // Solo llamar a stop() si el AudioRecord está en estado RECORDSTATE_RECORDING
            audioRecord?.let { record ->
                try {
                    if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop()
                    }
                } catch (e: IllegalStateException) {
                    // AudioRecord ya está detenido, ignorar
                    Log.d(TAG, "🎤 AudioRecord ya estaba detenido")
                } catch (e: Exception) {
                    // Ignorar cualquier otro error en stop()
                    Log.w(TAG, "⚠️ Error menor deteniendo AudioRecord (ignorado): ${e.message}")
                }
            }
            
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error liberando AudioRecord (ignorado): ${e.message}")
            }
            
            audioRecord = null
            Log.d(TAG, "🎤 Grabación detenida")
        } catch (e: Exception) {
            // Capturar cualquier excepción, incluyendo JobCancellationException
            Log.w(TAG, "⚠️ Excepción al detener grabación (ignorado): ${e.javaClass.simpleName}")
        }
    }
    
    /**
     * Inicia la reproducción de audio con configuración optimizada
     * CRÍTICO: Verifica que no haya audioTrack previo antes de crear uno nuevo
     */
    fun startPlayback() {
        try {
            // Si ya existe audioTrack, detenerlo primero
            if (audioTrack != null) {
                Log.w(TAG, "⚠️ Ya existe audioTrack activo, deteniendo primero...")
                stopPlayback()
            }
            
            val minBufferSize = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                OUTPUT_CHANNEL_CONFIG,
                OUTPUT_AUDIO_FORMAT
            )
            
            // Buffer para 100ms de audio MONO
            // MONO requiere menos buffer que STEREO
            // AUMENTADO A 1 SEGUNDO para evitar underruns
            val bufferSize = maxOf(
                minBufferSize * 8, // Multiplicador más alto
                (OUTPUT_SAMPLE_RATE * 1000 / 1000) * 2 // 1 segundo @ 16-bit mono (más grande)
            )
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(OUTPUT_AUDIO_FORMAT)
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setChannelMask(OUTPUT_CHANNEL_CONFIG)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            
            audioTrack?.play()
            isPlaying = true
            Log.d(TAG, "🔊 Reproducción iniciada (${OUTPUT_SAMPLE_RATE}Hz, buffer=${bufferSize} bytes)")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando reproducción", e)
        }
    }
    
    /**
     * Reproduce audio PCM 24kHz en thread de fondo con protección thread-safe
     */
    fun playAudio(audioData: ByteArray) {
        scope.launch(Dispatchers.IO) {
            audioMutex.withLock {
                try {
                    // Verificar que audioTrack está activo ANTES de escribir
                    if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                        Log.w(TAG, "⚠️ AudioTrack no disponible, ignorando audio")
                        return@withLock
                    }
                    
                    if (!isPlaying) {
                        Log.w(TAG, "⚠️ Reproducción detenida, ignorando audio")
                        return@withLock
                    }
                    
                    // Gemini envía audio MONO 24kHz PCM 16-bit - reproducir directamente
                    // NO convertir a stereo, el audio ya viene en el formato correcto
                    
                    // Escribir audio en modo bloqueante (pero en thread de fondo)
                    val bytesWritten = audioTrack?.write(
                        audioData, 
                        0, 
                        audioData.size,
                        AudioTrack.WRITE_BLOCKING
                    ) ?: 0
                    
                    if (bytesWritten < 0) {
                        Log.e(TAG, "❌ Error escribiendo audio: $bytesWritten")
                    } else {
                        Log.d(TAG, "🔊 Audio reproducido: $bytesWritten bytes")
                    }
                    
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "❌ AudioTrack en estado inválido", e)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error reproduciendo audio", e)
                }
            }
        }
    }
    
    /**
     * Detiene la reproducción de forma thread-safe
     * CRÍTICO: Ahora es BLOQUEANTE para evitar race conditions al reiniciar
     */
    fun stopPlayback() {
        runBlocking(Dispatchers.IO) {
            audioMutex.withLock {
                try {
                    isPlaying = false
                    audioTrack?.let { track ->
                        try {
                            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                                track.pause()
                                track.flush() // Limpiar buffer antes de stop
                            }
                            track.stop()
                        } catch (e: IllegalStateException) {
                            Log.w(TAG, "⚠️ AudioTrack ya estaba detenido")
                        }
                        track.release()
                    }
                    audioTrack = null
                    Log.d(TAG, "🔊 Reproducción detenida")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error deteniendo reproducción (ignorado): ${e.message}")
                }
            }
        }
    }
    
    /**
     * Limpia recursos y cancela todas las coroutines pendientes
     * SAFE: No lanza excepciones aunque haya errores internos
     * USO: Solo llamar en onDestroy() de la Activity
     */
    fun cleanup() {
        try {
            stopRecording()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error en cleanup/stopRecording (ignorado): ${e.javaClass.simpleName}")
        }
        
        try {
            // stopPlayback ya maneja sus propias excepciones
            stopPlayback()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error en cleanup/stopPlayback (ignorado): ${e.javaClass.simpleName}")
        }
        
        try {
            scope.cancel() // Cancela TODAS las coroutines pendientes
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error cancelando scope (ignorado): ${e.javaClass.simpleName}")
        }
    }
    
    /**
     * Reinicia el audio manager sin cancelar el scope
     * USO: Para reiniciar sesión con nueva voz
     */
    fun reset() {
        try {
            Log.d(TAG, "🔄 Reiniciando AudioManager...")
            stopRecording()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error en reset/stopRecording (ignorado): ${e.javaClass.simpleName}")
        }
        
        try {
            stopPlayback()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error en reset/stopPlayback (ignorado): ${e.javaClass.simpleName}")
        }
        
        // NO cancelar el scope, solo limpiar recursos de audio
        Log.d(TAG, "✅ AudioManager reiniciado")
    }
    
    /**
     * Verifica si está grabando
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * Verifica si está reproduciendo
     */
    fun isPlaying(): Boolean = isPlaying
}
