/*
 * Copyright (C) 2024 Lyrion
 * Detector Inteligente de Patr    /**
     * NUEVA VERSIÓN: Usa LLM para detectar información personal
     * Sin patrones fijos - funciona en cualquier idioma y forma de expresión
     */
    suspend fun analyzePersonalInfoWithLLM(userMessage: String, llmResponse: String): PersonalInfoExtraction? {
        // Esta función será llamada por LLMManager después de cada respuesta
        // El LLM analizará el mensaje y extraerá información personal
        return null // Implementación en LLMManager
    }fica automáticamente contexto educativo y personal en las conversaciones
 */

package io.orabel.orabelandroid.ai

import android.content.Context
import io.orabel.orabelandroid.data.PersonalContext
import io.orabel.orabelandroid.data.StudyKnowledge
import io.orabel.orabelandroid.data.ObjectBoxStore
import io.objectbox.kotlin.boxFor
import java.util.Locale

class SmartPatternDetector(private val context: Context) {
    
    private val studyBox = ObjectBoxStore.store.boxFor<StudyKnowledge>()
    private val personalBox = ObjectBoxStore.store.boxFor<PersonalContext>()
    
    // ========== PATRONES EDUCATIVOS ==========
    
    private val studyPatterns = mapOf(
        "matemáticas" to listOf(
            "ecuación", "fórmula", "derivada", "integral", "álgebra", "geometría",
            "trigonometría", "cálculo", "resolver", "demostrar", "calcular"
        ),
        "química" to listOf(
            "molécula", "átomo", "elemento", "compuesto", "reacción", "valencia",
            "tabla periódica", "ácido", "base", "pH", "oxidación", "enlace"
        ),
        "física" to listOf(
            "fuerza", "energía", "velocidad", "aceleración", "masa", "newton",
            "trabajo", "potencia", "ley", "gravedad", "óptica", "mecánica"
        ),
        "redacción" to listOf(
            "ensayo", "redactar", "escribir", "texto", "párrafo", "introducción",
            "conclusión", "argumento", "tesis", "ortografía", "gramática"
        ),
        "programación" to listOf(
            "código", "función", "variable", "clase", "algoritmo", "bucle",
            "array", "lista", "método", "objeto", "debugging", "compilar"
        )
    )
    
    // ========== SISTEMA ELIMINADO - AHORA USA LLM PARA DETECTAR ==========
    // Los patrones fijos son obsoletos y limitados
    // El LLM analizará automáticamente el mensaje del usuario
    
    /**
     * Detecta si el mensaje contiene contenido educativo
     * @return Categoría detectada o null
     */
    fun detectStudyCategory(message: String): String? {
        val lowerMessage = message.lowercase(Locale.getDefault())
        
        studyPatterns.forEach { (category, keywords) ->
            if (keywords.any { keyword -> lowerMessage.contains(keyword) }) {
                return category
            }
        }
        
        return null
    }
    
    /**
     * Extrae fórmulas matemáticas/químicas del texto
     * Busca patrones como: $formula$, $$formula$$, o estructuras comunes
     */
    fun extractFormulas(text: String): List<String> {
        val formulas = mutableListOf<String>()
        
        // Buscar fórmulas en LaTeX
        val latexRegex = """\$\$?(.*?)\$\$?""".toRegex()
        formulas.addAll(latexRegex.findAll(text).map { it.groupValues[1] })
        
        // Buscar ecuaciones químicas (contienen elementos + números)
        val chemRegex = """([A-Z][a-z]?\d*)+\s*[+→=]\s*([A-Z][a-z]?\d*)+""".toRegex()
        formulas.addAll(chemRegex.findAll(text).map { it.value })
        
        return formulas.distinct()
    }
    
    /**
     * Guarda conocimiento educativo automáticamente
     */
    fun saveStudyKnowledge(category: String, message: String, aiResponse: String) {
        val formulas = extractFormulas(aiResponse)
        
        val knowledge = StudyKnowledge(
            category = category,
            topic = extractTopic(message),
            content = aiResponse,
            formulas = formulas.joinToString(separator = "\n"),
            keywords = extractKeywords(message),
            timestamp = System.currentTimeMillis(),
            relevanceScore = 70
        )
        
        studyBox.put(knowledge)
    }
    
    /**
     * Extrae el tema principal del mensaje
     */
    private fun extractTopic(message: String): String {
        // Tomar las primeras 5 palabras significativas como tema
        val words = message.split(" ").filter { it.length > 3 }
        return words.take(5).joinToString(" ")
    }
    
    /**
     * Extrae palabras clave del mensaje
     */
    private fun extractKeywords(message: String): String {
        val stopWords = setOf("el", "la", "de", "que", "y", "a", "en", "un", "ser", "se", "no", "por", "con", "para")
        val words = message.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length > 3 && it !in stopWords }
            .distinct()
            .take(10)
        
        return words.joinToString(", ")
    }
    
    /**
     * Obtiene contexto relevante para enriquecer el prompt
     */
    fun getRelevantStudyContext(message: String): List<StudyKnowledge> {
        val category = detectStudyCategory(message)
        if (category != null) {
            // Obtener todas las entradas de esta categoría y ordenar en memoria
            val all = studyBox.all
            return all.filter { it.category == category }
                .sortedByDescending { it.relevanceScore }
                .take(3) // Top 3 más relevantes
        }
        return emptyList()
    }
}
