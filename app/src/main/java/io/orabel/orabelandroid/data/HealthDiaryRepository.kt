/*
 * Copyright (C) 2024 Lyrion
 * Repository para el Diario de Salud - Sistema de registro de síntomas y eventos
 */

package io.orabel.orabelandroid.data

import android.content.Context
import android.util.Log
import io.objectbox.kotlin.flow
import io.objectbox.kotlin.toFlow
import io.objectbox.query.OrderFlags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.util.Date
import java.util.regex.Pattern

@OptIn(ExperimentalCoroutinesApi::class)
@Single
class HealthDiaryRepository(private val context: Context) {
    private val healthDiaryBox = ObjectBoxStore.store.boxFor(HealthDiaryEntry::class.java)

    init {
        Log.e(TAG, "=== HEALTHDIARY REPOSITORY INICIALIZADO ===")
        Log.e(TAG, "🏥 HealthDiaryRepository inicializado correctamente")
        
        // Limpiar entradas de prueba anteriores
        cleanupTestEntries()
        
        Log.e(TAG, "📊 Entradas existentes en BD: ${healthDiaryBox.count()}")
        Log.e(TAG, "=== FIN INICIALIZACIÓN ===")
    }

    /**
     * Limpia entradas de prueba que puedan haber quedado de versiones anteriores
     */
    private fun cleanupTestEntries() {
        try {
            // Eliminar entradas que contengan "TEST:" en el texto
            val testEntries = healthDiaryBox.query(HealthDiaryEntry_.userReportText.startsWith("TEST:")).build().find()
            testEntries.forEach { entry ->
                Log.e(TAG, "🗑️ Eliminando entrada de prueba: ${entry.userReportText}")
                healthDiaryBox.remove(entry)
            }
            
            // Eliminar entradas con textos repetitivos sospechosos
            val suspiciousTexts = arrayOf(
                "Tengo dolor de cabeza fuerte",
                "Me duele el estómago",
                "Siento mucho cansancio", 
                "Tengo fiebre y tos",
                "Estoy muy triste",
                "Tuve un accidente"
            )
            
            suspiciousTexts.forEach { text ->
                val duplicates = healthDiaryBox.query(HealthDiaryEntry_.userReportText.equal(text)).build().find()
                if (duplicates.size > 1) {
                    // Mantener solo la más reciente y eliminar el resto
                    val sorted = duplicates.sortedByDescending { it.recordedAt }
                    sorted.drop(1).forEach { entry ->
                        Log.e(TAG, "🗑️ Eliminando duplicado: ${entry.userReportText}")
                        healthDiaryBox.remove(entry)
                    }
                }
            }
            
            Log.e(TAG, "✅ Limpieza de entradas de prueba completada")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error durante limpieza: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "HealthDiaryRepository"
        
        // Palabras clave para detectar categorías
        private val SYMPTOM_KEYWORDS = arrayOf(
            "dolor", "duele", "dolió", "doliendo", "ache", "pain", "fiebre", "fever", "tos", "cough", 
            "náuseas", "nausea", "mareo", "dizzy", "cansancio", "tired", "fatiga", "cansado",
            "dolor de cabeza", "headache", "migraña", "resfriado", "gripe", "flu",
            "estómago", "stomach", "diarrea", "estreñimiento", "vómito", "rash",
            "picazón", "alergia", "allergy", "inflamación", "hinchazón", "sangrado",
            "malestar", "molestia", "ardor", "quemazón", "punzadas", "palpitaciones",
            "dificultad", "respirar", "ahogo", "congestion", "mucosidad", "flema"
        )
        
        private val EMOTIONAL_KEYWORDS = arrayOf(
            "triste", "sad", "deprimido", "depressed", "ansioso", "anxious", "ansiedad",
            "estrés", "stress", "preocupado", "worried", "feliz", "happy", "enojado",
            "angry", "frustrado", "frustrated", "pánico", "panic", "miedo", "fear",
            "agotado", "exhausted", "abrumado", "overwhelmed", "solo", "lonely",
            "melancólico", "nostálgico", "nostalgic", "desanimado", "desesperado",
            "irritable", "malhumorado", "nervioso", "tenso", "relajado", "tranquilo"
        )
        
        private val ACCIDENT_KEYWORDS = arrayOf(
            "accidente", "accident", "caída", "fall", "golpe", "hit", "lesión", "injury",
            "cortadura", "cut", "fractura", "fracture", "torcedura", "sprain", "quemadura",
            "burn", "mordedura", "bite", "raspón", "scratch", "magulladura", "bruise"
        )
    }

    /**
     * Analiza un texto y determina si contiene información relevante para el diario de salud
     */
    suspend fun analyzeAndSaveIfRelevant(
        userText: String,
        chatId: Long
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.e(TAG, "=== INICIO ANÁLISIS SÍNTOMAS ===")
                Log.e(TAG, "🔍 TEXTO A ANALIZAR: '$userText'")
                val relevantInfo = extractHealthInfo(userText)
                Log.e(TAG, "📊 INFORMACIÓN EXTRAÍDA: '$relevantInfo'")
                
                if (relevantInfo.isNotEmpty()) {
                    val category = categorizeText(userText)
                    val userConcern = detectUserConcern(userText)
                    val concernLevel = calculateConcernLevel(userText)
                    
                    Log.e(TAG, "📋 CATEGORÍA: $category, PREOCUPACIÓN: $userConcern, NIVEL: $concernLevel")
                    
                    val entry = HealthDiaryEntry(
                        userReportText = userText.trim(),
                        category = category,
                        recordedAt = Date(),
                        chatId = chatId,
                        extractedInfo = relevantInfo,
                        userConcern = userConcern,
                        concernLevel = concernLevel
                    )
                    
                    val entryId = healthDiaryBox.put(entry)
                    Log.e(TAG, "✅ ENTRADA GUARDADA CON ID: $entryId")
                    Log.e(TAG, "📄 DETALLES: $category - ${relevantInfo.take(50)}...")
                    
                    // Verificar que se guardó
                    val totalEntries = healthDiaryBox.count()
                    Log.e(TAG, "📈 TOTAL DE ENTRADAS EN BD: $totalEntries")
                    Log.e(TAG, "=== ANÁLISIS EXITOSO ===")
                    
                    true
                } else {
                    Log.e(TAG, "❌ NO HAY INFORMACIÓN RELEVANTE DE SALUD")
                    Log.e(TAG, "=== ANÁLISIS SIN RESULTADO ===")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ ERROR GUARDANDO ENTRADA: ${e.message}", e)
                false
            }
        }
    }

    /**
     * Extrae información relevante de salud del texto
     */
    private fun extractHealthInfo(text: String): String {
        val lowerText = text.lowercase()
        val relevantPhrases = mutableListOf<String>()
        
        Log.e(TAG, "🔍 ANALIZANDO TEXTO: '$text'")
        
        // Buscar frases con palabras clave
        val allKeywords = SYMPTOM_KEYWORDS + EMOTIONAL_KEYWORDS + ACCIDENT_KEYWORDS
        Log.e(TAG, "🔑 TOTAL PALABRAS CLAVE: ${allKeywords.size}")
        
        for (keyword in allKeywords) {
            if (lowerText.contains(keyword.lowercase())) {
                Log.e(TAG, "🎯 PALABRA CLAVE ENCONTRADA: '$keyword'")
                // Extraer contexto alrededor de la palabra clave
                val sentences = text.split(".", "!", "?", "\n")
                for (sentence in sentences) {
                    if (sentence.lowercase().contains(keyword.lowercase()) && sentence.trim().isNotEmpty()) {
                        relevantPhrases.add(sentence.trim())
                        Log.e(TAG, "📝 FRASE RELEVANTE AGREGADA: '${sentence.trim()}'")
                        break
                    }
                }
            }
        }
        
        val result = relevantPhrases.joinToString(" | ")
        Log.e(TAG, "✅ RESULTADO FINAL: '$result' (FRASES: ${relevantPhrases.size})")
        return result
    }

    /**
     * Categoriza el texto según su contenido
     */
    private fun categorizeText(text: String): String {
        val lowerText = text.lowercase()
        
        val symptomCount = SYMPTOM_KEYWORDS.count { lowerText.contains(it.lowercase()) }
        val emotionalCount = EMOTIONAL_KEYWORDS.count { lowerText.contains(it.lowercase()) }
        val accidentCount = ACCIDENT_KEYWORDS.count { lowerText.contains(it.lowercase()) }
        
        return when {
            symptomCount > 0 && symptomCount >= emotionalCount && symptomCount >= accidentCount -> "symptom"
            emotionalCount > 0 && emotionalCount >= accidentCount -> "emotional" 
            accidentCount > 0 -> "accident"
            else -> "general"
        }
    }

    /**
     * Detecta si el usuario expresa preocupación
     */
    private fun detectUserConcern(text: String): Boolean {
        val concernKeywords = arrayOf(
            "preocupado", "worried", "grave", "serious", "mal", "terrible", 
            "horrible", "no aguanto", "insoportable", "urgente", "ayuda",
            "qué hago", "qué puedo", "estoy asustado", "scared"
        )
        
        val lowerText = text.lowercase()
        return concernKeywords.any { lowerText.contains(it) }
    }

    /**
     * Calcula el nivel de preocupación basado en palabras clave
     * 0 = bajo, 1 = moderado, 2 = alto, 3 = urgente
     */
    private fun calculateConcernLevel(text: String): Int {
        val lowerText = text.lowercase()
        
        // Nivel 3 - Urgente
        val urgentKeywords = arrayOf(
            "urgente", "emergency", "no puedo", "insoportable", "terrible dolor",
            "sangre", "blood", "desmayo", "unconscious", "pecho", "chest pain"
        )
        if (urgentKeywords.any { lowerText.contains(it) }) return 3
        
        // Nivel 2 - Alto
        val highKeywords = arrayOf(
            "mucho dolor", "severe", "grave", "horrible", "no aguanto", 
            "empeora", "peor", "preocupado", "worried", "asustado"
        )
        if (highKeywords.any { lowerText.contains(it) }) return 2
        
        // Nivel 1 - Moderado  
        val moderateKeywords = arrayOf(
            "dolor", "pain", "mal", "sick", "fiebre", "fever", "tos", "cough",
            "triste", "sad", "ansioso", "anxiety", "estrés", "stress"
        )
        if (moderateKeywords.any { lowerText.contains(it) }) return 1
        
        // Nivel 0 - Bajo (por defecto)
        return 0
    }

    /**
     * Obtiene todas las entradas del diario ordenadas por fecha (más recientes primero)
     */
    fun getAllEntries(): Flow<List<HealthDiaryEntry>> {
        Log.i(TAG, "📋 getAllEntries() - Consultando entradas en BD")
        val totalCount = healthDiaryBox.count()
        Log.i(TAG, "📊 Total de entradas encontradas: $totalCount")
        
        return healthDiaryBox
            .query()
            .orderDesc(HealthDiaryEntry_.recordedAt)
            .build()
            .flow()
            .flowOn(Dispatchers.IO)
    }

    /**
     * Obtiene entradas por categoría
     */
    fun getEntriesByCategory(category: String): Flow<List<HealthDiaryEntry>> {
        return healthDiaryBox
            .query(HealthDiaryEntry_.category.equal(category))
            .order(HealthDiaryEntry_.recordedAt, OrderFlags.DESCENDING)
            .build()
            .subscribe()
            .toFlow()
            .flowOn(Dispatchers.IO)
    }

    /**
     * Obtiene entradas de un rango de fechas
     */
    fun getEntriesInDateRange(startDate: Date, endDate: Date): Flow<List<HealthDiaryEntry>> {
        return healthDiaryBox
            .query(HealthDiaryEntry_.recordedAt.between(startDate, endDate))
            .order(HealthDiaryEntry_.recordedAt, OrderFlags.DESCENDING)
            .build()
            .subscribe()
            .toFlow()
            .flowOn(Dispatchers.IO)
    }

    /**
     * Cuenta total de entradas
     */
    suspend fun getTotalEntriesCount(): Long {
        return withContext(Dispatchers.IO) {
            healthDiaryBox.count()
        }
    }

    /**
     * Obtiene estadísticas de categorías
     */
    suspend fun getCategoryStats(): Map<String, Long> {
        return withContext(Dispatchers.IO) {
            mapOf(
                "symptom" to healthDiaryBox.query(HealthDiaryEntry_.category.equal("symptom")).build().count(),
                "emotional" to healthDiaryBox.query(HealthDiaryEntry_.category.equal("emotional")).build().count(),
                "accident" to healthDiaryBox.query(HealthDiaryEntry_.category.equal("accident")).build().count(),
                "general" to healthDiaryBox.query(HealthDiaryEntry_.category.equal("general")).build().count()
            )
        }
    }
}
