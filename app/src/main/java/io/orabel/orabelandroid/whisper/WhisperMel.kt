package io.orabel.orabelandroid.whisper

/**
 * Configuración para el cálculo del espectrograma Mel
 */
class WhisperMel {
    var nLen = 0           // Longitud de las muestras de audio
    var nLenOrg = 0        // Longitud original antes de padding
    var nMel = 0           // Número de bins mel
    var data: FloatArray = FloatArray(0)  // Espectrograma mel calculado
}
