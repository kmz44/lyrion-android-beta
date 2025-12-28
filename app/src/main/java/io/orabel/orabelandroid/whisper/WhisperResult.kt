package io.orabel.orabelandroid.whisper

/**
 * Resultado del reconocimiento de voz de Whisper
 */
data class WhisperResult(
    val text: String,
    val language: String,
    val action: WhisperAction
)

enum class WhisperAction {
    TRANSCRIBE,  // Transcribir en el mismo idioma
    TRANSLATE    // Traducir a inglés
}
