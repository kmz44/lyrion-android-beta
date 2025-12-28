package io.orabel.orabelandroid.whisper

/**
 * Almacena el vocabulario de Whisper y tokens especiales
 */
class WhisperVocab {
    // Tokens especiales de Whisper
    var tokenEOT = 50256      // End of transcript
    var tokenSOT = 50257      // Start of transcript
    var tokenPREV = 50360     // Previous
    var tokenSOLM = 50361     // Start of lm
    var tokenNOT = 50362      // No timestamps
    var tokenBEG = 50363      // Begin
    
    // Tasks
    val tokenTRANSLATE = 50358  // Translate task
    val tokenTRANSCRIBE = 50359 // Transcribe task
    
    // Tipos de vocabulario
    val nVocabEnglish = 51864       // Solo inglés
    val nVocabMultilingual = 51865  // Multilingüe
    
    // Mapeo de token ID a palabra (bytes UTF-8)
    val tokenToWord = mutableMapOf<Int, ByteArray>()
    
    // Número total de tokens en el vocabulario
    var nVocab = 0
}
