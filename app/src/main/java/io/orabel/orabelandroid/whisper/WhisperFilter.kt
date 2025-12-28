package io.orabel.orabelandroid.whisper

/**
 * Filtros Mel para transformar espectrograma de frecuencias a escala Mel
 */
class WhisperFilter {
    var nMel = 0     // Número de bins mel (típicamente 80)
    var nFft = 0     // Tamaño FFT (típicamente 400)
    var data: FloatArray = FloatArray(0)  // Matriz de filtros [nMel x nFft]
}
