package io.orabel.orabelandroid.whisper

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Buffer compartido para datos de audio grabados
 * Compatible con la implementación original de WhisperIME
 */
object RecordBuffer {
    
    @Volatile
    private var outputBuffer: ByteArray? = null
    
    @Synchronized
    fun setOutputBuffer(buffer: ByteArray) {
        outputBuffer = buffer
    }
    
    @Synchronized
    fun getOutputBuffer(): ByteArray? {
        return outputBuffer
    }
    
    /**
     * Convierte el buffer de audio a muestras PCM_FLOAT normalizadas
     */
    fun getSamples(): FloatArray {
        val buffer = getOutputBuffer() ?: return floatArrayOf()
        
        val numSamples = buffer.size / 2
        val byteBuffer = ByteBuffer.wrap(buffer)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        // Convertir a formato PCM_FLOAT
        val samples = FloatArray(numSamples)
        var maxAbsValue = 0.0f
        
        for (i in 0 until numSamples) {
            samples[i] = byteBuffer.short / 32768.0f
            if (abs(samples[i]) > maxAbsValue) {
                maxAbsValue = abs(samples[i])
            }
        }
        
        // Normalizar las muestras
        if (maxAbsValue > 0.0f) {
            for (i in samples.indices) {
                samples[i] /= maxAbsValue
            }
        }
        
        return samples
    }
    
    @Synchronized
    fun clear() {
        outputBuffer = null
    }
}
