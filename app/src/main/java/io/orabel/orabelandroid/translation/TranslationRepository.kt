package io.orabel.orabelandroid.translation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TranslationRepository {
    
    private val _translationState = MutableStateFlow(TranslationState())
    val translationState: StateFlow<TranslationState> = _translationState.asStateFlow()
    
    private var spanishToEnglishTranslator: Translator? = null
    private var englishToSpanishTranslator: Translator? = null
    
    private val downloadConditions = DownloadConditions.Builder()
        .requireWifi()
        .build()
    
    init {
        initializeTranslators()
    }
    
    private fun initializeTranslators() {
        // Español a Inglés
        val spanishToEnglishOptions = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.SPANISH)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
        spanishToEnglishTranslator = Translation.getClient(spanishToEnglishOptions)
        
        // Inglés a Español
        val englishToSpanishOptions = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.SPANISH)
            .build()
        englishToSpanishTranslator = Translation.getClient(englishToSpanishOptions)
    }
    
    suspend fun downloadModels(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _translationState.value = _translationState.value.copy(
                    isDownloading = true,
                    downloadError = null
                )
                
                // Descargar modelo español-inglés
                spanishToEnglishTranslator?.downloadModelIfNeeded(downloadConditions)?.await()
                
                // Descargar modelo inglés-español
                englishToSpanishTranslator?.downloadModelIfNeeded(downloadConditions)?.await()
                
                _translationState.value = _translationState.value.copy(
                    isDownloading = false,
                    areModelsDownloaded = true
                )
                
                true
            } catch (e: Exception) {
                _translationState.value = _translationState.value.copy(
                    isDownloading = false,
                    downloadError = e.message
                )
                false
            }
        }
    }
    
    suspend fun translateSpanishToEnglish(text: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                _translationState.value = _translationState.value.copy(
                    isTranslating = true,
                    translationError = null
                )
                
                val result = spanishToEnglishTranslator?.translate(text)?.await()
                
                _translationState.value = _translationState.value.copy(
                    isTranslating = false,
                    lastTranslation = result
                )
                
                result
            } catch (e: Exception) {
                _translationState.value = _translationState.value.copy(
                    isTranslating = false,
                    translationError = e.message
                )
                null
            }
        }
    }
    
    suspend fun translateEnglishToSpanish(text: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                _translationState.value = _translationState.value.copy(
                    isTranslating = true,
                    translationError = null
                )
                
                val result = englishToSpanishTranslator?.translate(text)?.await()
                
                _translationState.value = _translationState.value.copy(
                    isTranslating = false,
                    lastTranslation = result
                )
                
                result
            } catch (e: Exception) {
                _translationState.value = _translationState.value.copy(
                    isTranslating = false,
                    translationError = e.message
                )
                null
            }
        }
    }
    
    suspend fun detectLanguageAndTranslate(text: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Detectar idioma simple basado en palabras comunes
                val language = detectLanguage(text)
                
                when (language) {
                    Language.SPANISH -> translateSpanishToEnglish(text)
                    Language.ENGLISH -> translateEnglishToSpanish(text)
                    Language.UNKNOWN -> {
                        // Si no puede detectar, intenta ambos
                        translateSpanishToEnglish(text) ?: translateEnglishToSpanish(text)
                    }
                }
            } catch (e: Exception) {
                _translationState.value = _translationState.value.copy(
                    translationError = e.message
                )
                null
            }
        }
    }
    
    private fun detectLanguage(text: String): Language {
        val lowercaseText = text.lowercase()
        
        // Palabras comunes en español
        val spanishWords = listOf(
            "el", "la", "de", "que", "y", "a", "en", "un", "es", "se", "no", "te", "lo", "le",
            "da", "su", "por", "son", "con", "para", "como", "está", "han", "del", "al", "pero",
            "todo", "esta", "una", "con", "también", "puede", "ser", "hacer", "más", "muy",
            "aquí", "donde", "cuando", "porque", "cómo", "qué", "cuál", "entonces", "sí", "así"
        )
        
        // Palabras comunes en inglés
        val englishWords = listOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not",
            "on", "with", "he", "as", "you", "do", "at", "this", "but", "his", "by", "from",
            "they", "we", "say", "her", "she", "or", "an", "will", "my", "one", "all", "would",
            "there", "their", "what", "so", "up", "out", "if", "about", "who", "get", "which"
        )
        
        val words = lowercaseText.split(Regex("\\s+"))
        var spanishCount = 0
        var englishCount = 0
        
        for (word in words) {
            if (spanishWords.contains(word)) spanishCount++
            if (englishWords.contains(word)) englishCount++
        }
        
        return when {
            spanishCount > englishCount -> Language.SPANISH
            englishCount > spanishCount -> Language.ENGLISH
            else -> Language.UNKNOWN
        }
    }
    
    fun clearError() {
        _translationState.value = _translationState.value.copy(
            downloadError = null,
            translationError = null
        )
    }
    
    fun cleanup() {
        spanishToEnglishTranslator?.close()
        englishToSpanishTranslator?.close()
    }
}

data class TranslationState(
    val isDownloading: Boolean = false,
    val areModelsDownloaded: Boolean = false,
    val downloadError: String? = null,
    val isTranslating: Boolean = false,
    val lastTranslation: String? = null,
    val translationError: String? = null
)

enum class Language {
    SPANISH, ENGLISH, UNKNOWN
}
