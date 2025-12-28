package io.orabel.orabelandroid.whisper

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin

/**
 * Utilidades para procesamiento de audio Whisper
 * Incluye cálculo de espectrogramas Mel, FFT y carga de vocabulario
 */
class WhisperUtil {
    
    companion object {
        private const val TAG = "WhisperUtil"
        
        // Constantes de Whisper
        const val WHISPER_SAMPLE_RATE = 16000
        const val WHISPER_N_FFT = 400
        const val WHISPER_N_MEL = 80
        const val WHISPER_HOP_LENGTH = 160
        const val WHISPER_CHUNK_SIZE = 30
    }
    
    private val vocab = WhisperVocab()
    private val filters = WhisperFilter()
    private val mel = WhisperMel()
    
    // Getters para tokens especiales
    fun getTokenTranslate() = vocab.tokenTRANSLATE
    fun getTokenTranscribe() = vocab.tokenTRANSCRIBE
    fun getTokenEOT() = vocab.tokenEOT
    fun getTokenSOT() = vocab.tokenSOT
    fun getTokenPREV() = vocab.tokenPREV
    fun getTokenSOLM() = vocab.tokenSOLM
    fun getTokenNOT() = vocab.tokenNOT
    fun getTokenBEG() = vocab.tokenBEG
    
    fun getWordFromToken(token: Int): ByteArray? = vocab.tokenToWord[token]
    
    /**
     * Carga filtros Mel y vocabulario desde archivo binario
     */
    fun loadFiltersAndVocab(multilingual: Boolean, vocabPath: String): Boolean {
        return try {
            val vocabFile = File(vocabPath)
            if (!vocabFile.exists()) {
                Log.e(TAG, "Archivo de vocabulario no encontrado: $vocabPath")
                return false
            }
            
            val bytes = vocabFile.readBytes()
            val vocabBuf = ByteBuffer.wrap(bytes).apply {
                order(ByteOrder.nativeOrder())
            }
            
            Log.d(TAG, "Tamaño archivo vocab: ${vocabBuf.limit()}")
            
            // Verificar magic number @magic:USEN (0x5553454e)
            val magic = vocabBuf.int
            if (magic != 0x5553454e) {
                Log.e(TAG, "Magic number inválido: $magic")
                return false
            }
            
            Log.d(TAG, "Magic number válido: $magic")
            
            // Cargar filtros Mel
            filters.nMel = vocabBuf.int
            filters.nFft = vocabBuf.int
            Log.d(TAG, "n_mel: ${filters.nMel}, n_fft: ${filters.nFft}")
            
            val filterDataSize = filters.nMel * filters.nFft
            filters.data = FloatArray(filterDataSize) {
                vocabBuf.float
            }
            
            // Cargar vocabulario
            val nVocab = vocabBuf.int
            Log.d(TAG, "nVocab: $nVocab")
            
            repeat(nVocab) { i ->
                val len = vocabBuf.int
                val wordBytes = ByteArray(len)
                vocabBuf.get(wordBytes, 0, len)
                vocab.tokenToWord[i] = wordBytes
            }
            
            // Agregar tokens adicionales
            val nVocabAdditional = if (!multilingual) {
                51864 // English only
            } else {
                // Ajustar tokens para multilingüe
                vocab.tokenEOT++
                vocab.tokenSOT++
                vocab.tokenPREV++
                vocab.tokenSOLM++
                vocab.tokenNOT++
                vocab.tokenBEG++
                51865 // Multilingual
            }
            
            // Agregar tokens especiales al vocabulario
            for (i in nVocab until nVocabAdditional) {
                val word = when {
                    i > vocab.tokenBEG -> "[_TT_${i - vocab.tokenBEG}]"
                    i == vocab.tokenEOT -> "[_EOT_]"
                    i == vocab.tokenSOT -> "[_SOT_]"
                    i == vocab.tokenPREV -> "[_PREV_]"
                    i == vocab.tokenNOT -> "[_NOT_]"
                    i == vocab.tokenBEG -> "[_BEG_]"
                    else -> "[_extra_token_$i]"
                }
                vocab.tokenToWord[i] = word.toByteArray(Charsets.UTF_8)
            }
            
            vocab.nVocab = nVocabAdditional
            Log.d(TAG, "✅ Vocabulario cargado: $nVocabAdditional tokens")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cargando filtros y vocabulario", e)
            false
        }
    }
    
    /**
     * Calcula el espectrograma Mel de las muestras de audio
     * @param samples Array de muestras PCM float
     * @param nSamples Tamaño total del array (con padding)
     * @param meaningfulSamples Número de muestras reales (sin padding)
     * @param nThreads Número de threads para cálculo paralelo
     */
    fun getMelSpectrogram(
        samples: FloatArray,
        nSamples: Int,
        meaningfulSamples: Int,
        nThreads: Int
    ): FloatArray {
        val fftSize = WHISPER_N_FFT
        val fftStep = WHISPER_HOP_LENGTH
        
        mel.nMel = WHISPER_N_MEL
        mel.nLen = nSamples / fftStep
        mel.data = FloatArray(mel.nMel * mel.nLen)
        
        // Número de frames con datos reales
        val meaningfulFrames = meaningfulSamples / fftStep
        
        // Ventana Hann
        val hann = FloatArray(fftSize) { i ->
            (0.5 * (1.0 - cos(2.0 * PI * i / fftSize))).toFloat()
        }
        
        val nFft = 1 + fftSize / 2
        
        // Procesamiento paralelo con múltiples threads
        val workers = mutableListOf<Thread>()
        
        repeat(nThreads) { ith ->
            val thread = Thread {
                Log.d(TAG, "Thread $ith iniciado")
                
                val fftIn = FloatArray(fftSize)
                val fftOut = FloatArray(fftSize * 2)
                
                // Procesar frames asignados a este thread
                var i = ith
                while (i < meaningfulFrames) {
                    val offset = i * fftStep
                    
                    // Aplicar ventana Hann
                    for (j in 0 until fftSize) {
                        fftIn[j] = if (offset + j < meaningfulSamples) {
                            hann[j] * samples[offset + j]
                        } else {
                            0.0f
                        }
                    }
                    
                    // FFT -> mag^2
                    fft(fftIn, fftOut)
                    for (j in 0 until fftSize) {
                        val re = fftOut[2 * j]
                        val im = fftOut[2 * j + 1]
                        fftOut[j] = re * re + im * im
                    }
                    
                    // Simetría de FFT
                    for (j in 1 until fftSize / 2) {
                        fftOut[j] += fftOut[fftSize - j]
                    }
                    
                    // Aplicar filtros Mel
                    for (j in 0 until mel.nMel) {
                        var sum = 0.0
                        for (k in 0 until nFft) {
                            sum += fftOut[k] * filters.data[j * nFft + k]
                        }
                        
                        if (sum < 1e-10) sum = 1e-10
                        sum = log10(sum)
                        
                        mel.data[j * mel.nLen + i] = sum.toFloat()
                    }
                    
                    i += nThreads
                }
                
                // Padding para frames restantes
                i = ith + meaningfulFrames
                while (i < mel.nLen) {
                    for (j in 0 until mel.nMel) {
                        mel.data[j * mel.nLen + i] = -8.0f
                    }
                    i += nThreads
                }
            }
            
            workers.add(thread)
            thread.start()
        }
        
        // Esperar a que terminen todos los threads
        workers.forEach { it.join() }
        
        // Clamping y normalización
        var mmax = -1e20
        for (i in 0 until mel.nMel * mel.nLen) {
            if (mel.data[i] > mmax) {
                mmax = mel.data[i].toDouble()
            }
        }
        
        mmax -= 8.0
        for (i in 0 until mel.nMel * mel.nLen) {
            if (mel.data[i] < mmax) {
                mel.data[i] = mmax.toFloat()
            }
            mel.data[i] = ((mel.data[i] + 4.0) / 4.0).toFloat()
        }
        
        return mel.data
    }
    
    /**
     * Transformada Discreta de Fourier (DFT)
     */
    private fun dft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        for (k in 0 until inSize) {
            var re = 0.0f
            var im = 0.0f
            for (n in 0 until inSize) {
                val angle = (2 * PI * k * n / inSize).toFloat()
                re += input[n] * cos(angle)
                im -= input[n] * sin(angle)
            }
            output[k * 2] = re
            output[k * 2 + 1] = im
        }
    }
    
    /**
     * Transformada Rápida de Fourier (FFT) - Algoritmo Cooley-Tukey
     */
    private fun fft(input: FloatArray, output: FloatArray) {
        val inSize = input.size
        
        if (inSize == 1) {
            output[0] = input[0]
            output[1] = 0.0f
            return
        }
        
        if (inSize % 2 == 1) {
            dft(input, output)
            return
        }
        
        // Separar elementos pares e impares
        val even = FloatArray(inSize / 2)
        val odd = FloatArray(inSize / 2)
        
        for (i in 0 until inSize) {
            if (i % 2 == 0) {
                even[i / 2] = input[i]
            } else {
                odd[i / 2] = input[i]
            }
        }
        
        val evenFft = FloatArray(inSize)
        val oddFft = FloatArray(inSize)
        
        fft(even, evenFft)
        fft(odd, oddFft)
        
        // Combinar resultados
        for (k in 0 until inSize / 2) {
            val theta = (2 * PI * k / inSize).toFloat()
            val re = cos(theta)
            val im = -sin(theta)
            
            val reOdd = oddFft[2 * k]
            val imOdd = oddFft[2 * k + 1]
            
            output[2 * k] = evenFft[2 * k] + re * reOdd - im * imOdd
            output[2 * k + 1] = evenFft[2 * k + 1] + re * imOdd + im * reOdd
            
            output[2 * (k + inSize / 2)] = evenFft[2 * k] - re * reOdd + im * imOdd
            output[2 * (k + inSize / 2) + 1] = evenFft[2 * k + 1] - re * imOdd - im * reOdd
        }
    }
}
