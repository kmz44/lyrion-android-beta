package io.orabel.orabelandroid.whisper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Grabadora de audio optimizada para Whisper
 * Adaptada de whisperIME-master con mejoras para Kotlin
 */
class WhisperRecorder(private val context: Context) {
    
    interface RecorderListener {
        fun onUpdateReceived(message: String)
        fun onRecordingComplete(audioData: ByteArray)
    }
    
    companion object {
        private const val TAG = "WhisperRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION
        private const val MAX_RECORDING_SECONDS = 30
        
        const val MSG_RECORDING = "Grabando..."
        const val MSG_RECORDING_DONE = "¡Grabación completada!"
        const val MSG_RECORDING_ERROR = "Error en la grabación"
    }
    
    private var listener: RecorderListener? = null
    private val isRecording = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingJob: Job? = null
    
    fun setListener(listener: RecorderListener) {
        this.listener = listener
    }
    
    fun start() {
        if (!isRecording.compareAndSet(false, true)) {
            Log.d(TAG, "La grabación ya está en progreso")
            return
        }
        
        recordingJob = scope.launch {
            try {
                recordAudio()
            } catch (e: Exception) {
                Log.e(TAG, "Error en la grabación", e)
                withContext(Dispatchers.Main) {
                    listener?.onUpdateReceived("$MSG_RECORDING_ERROR: ${e.message}")
                }
            } finally {
                isRecording.set(false)
            }
        }
    }
    
    fun stop() {
        Log.d(TAG, "Deteniendo grabación")
        isRecording.set(false)
    }
    
    fun isInProgress(): Boolean {
        return isRecording.get()
    }
    
    private suspend fun recordAudio() {
        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            withContext(Dispatchers.Main) {
                listener?.onUpdateReceived("Se necesita permiso para grabar audio")
            }
            return
        }
        
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        
        val audioRecord = AudioRecord.Builder()
            .setAudioSource(AUDIO_SOURCE)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            withContext(Dispatchers.Main) {
                listener?.onUpdateReceived("Error al inicializar AudioRecord")
            }
            return
        }
        
        audioRecord.startRecording()
        withContext(Dispatchers.Main) {
            listener?.onUpdateReceived(MSG_RECORDING)
        }
        
        val outputBuffer = ByteArrayOutputStream()
        val audioData = ByteArray(bufferSize)
        val maxBytes = SAMPLE_RATE * 2 * MAX_RECORDING_SECONDS // 2 bytes por muestra
        var totalBytesRead = 0
        
        while (isRecording.get() && totalBytesRead < maxBytes) {
            val bytesRead = audioRecord.read(audioData, 0, bufferSize)
            if (bytesRead > 0) {
                outputBuffer.write(audioData, 0, bytesRead)
                totalBytesRead += bytesRead
            } else {
                Log.w(TAG, "Error en AudioRecord, bytes leídos: $bytesRead")
                break
            }
        }
        
        audioRecord.stop()
        audioRecord.release()
        
        val recordedData = outputBuffer.toByteArray()
        
        withContext(Dispatchers.Main) {
            listener?.onUpdateReceived(MSG_RECORDING_DONE)
            listener?.onRecordingComplete(recordedData)
        }
        
        // Guardar en RecordBuffer para procesamiento
        RecordBuffer.setOutputBuffer(recordedData)
    }
    
    fun shutdown() {
        isRecording.set(false)
        recordingJob?.cancel()
        scope.cancel()
    }
}
