/*
 * Copyright (C) 2024 Lyrion
 * Generador de Prompts Personalizados Inteligentes
 * Inyecta contexto educativo y personal para respuestas adictivas
 */

package io.orabel.orabelandroid.ai

import android.content.Context
import io.orabel.orabelandroid.data.PersonalContext
import io.orabel.orabelandroid.data.StudyKnowledge

class SmartPromptBuilder(context: Context) {
    
    private val patternDetector = SmartPatternDetector(context)
    private val personalInfoExtractor = PersonalInfoExtractor(context)
    private val commandProcessor = CommandProcessor(context)
    
    /**
     * Construye un prompt enriquecido con contexto personal y educativo
     */
    fun buildEnrichedPrompt(userMessage: String, baseSystemPrompt: String): String {
        val enrichedPrompt = StringBuilder()
        
        // 1. Prompt base
        enrichedPrompt.append(baseSystemPrompt)
        enrichedPrompt.append("\n\n")
        
        // 2. Agregar nombre del usuario si lo conocemos
        val userName = personalInfoExtractor.getUserName()
        if (userName != null) {
            enrichedPrompt.append("IMPORTANTE: El usuario se llama $userName. ")
            enrichedPrompt.append("Dirígete a él/ella por su nombre de forma natural y amigable.\n\n")
        }
        
        // 3. Inyectar contexto personal relevante
        val personalContext = personalInfoExtractor.getPersonalContext()
        if (personalContext.isNotEmpty()) {
            enrichedPrompt.append("=== INFORMACIÓN PERSONAL DEL USUARIO ===\n")
            enrichedPrompt.append("Conoces la siguiente información sobre el usuario (úsala de forma natural):\n\n")
            
            personalContext.forEach { context ->
                when (context.contextType) {
                    "user_info" -> enrichedPrompt.append("- ${context.key}: ${context.value}\n")
                    "relationship" -> {
                        if (context.key == "partner_name") {
                            enrichedPrompt.append("- Tiene pareja: ${context.value}\n")
                        } else if (context.key == "ex_name") {
                            enrichedPrompt.append("- Ex pareja: ${context.value}\n")
                        }
                    }
                    "preferences" -> enrichedPrompt.append("- ${context.notes}\n")
                    "family" -> enrichedPrompt.append("- Familia: ${context.key} se llama ${context.value}\n")
                    else -> enrichedPrompt.append("- ${context.notes}\n")
                }
            }
            
            enrichedPrompt.append("\nUsa esta información para personalizar tus respuestas y ser más empático.\n\n")
        }
        
        // 4. Inyectar contexto educativo relevante
        val studyContext = patternDetector.getRelevantStudyContext(userMessage)
        if (studyContext.isNotEmpty()) {
            enrichedPrompt.append("=== CONOCIMIENTO PREVIO RELEVANTE ===\n")
            enrichedPrompt.append("Tienes acceso a estos conocimientos previos del usuario que pueden ser útiles:\n\n")
            
            studyContext.forEachIndexed { index, knowledge ->
                enrichedPrompt.append("${index + 1}. ${knowledge.topic}\n")
                enrichedPrompt.append("   Categoría: ${knowledge.category}\n")
                
                if (knowledge.formulas.isNotEmpty()) {
                    enrichedPrompt.append("   Fórmulas conocidas:\n")
                    knowledge.formulas.split("\n").forEach { formula ->
                        if (formula.isNotBlank()) {
                            enrichedPrompt.append("   - $formula\n")
                        }
                    }
                }
                
                enrichedPrompt.append("   Contenido: ${knowledge.content.take(200)}...\n\n")
            }
            
            enrichedPrompt.append("Usa este conocimiento previo para dar respuestas más completas y personalizadas.\n")
            enrichedPrompt.append("Si el usuario pregunta algo relacionado, menciona que ya habían hablado de esto antes.\n\n")
        }
        
        // 5. Instrucciones para respuestas educativas
        if (patternDetector.detectStudyCategory(userMessage) != null) {
            enrichedPrompt.append("=== INSTRUCCIONES PARA RESPUESTAS EDUCATIVAS ===\n")
            enrichedPrompt.append("• Usa formato LaTeX para fórmulas matemáticas: \$\$formula\$\$\n")
            enrichedPrompt.append("• Para fórmulas químicas usa notación estándar: H₂O, CO₂, etc.\n")
            enrichedPrompt.append("• Explica paso a paso los conceptos\n")
            enrichedPrompt.append("• Incluye ejemplos prácticos\n")
            enrichedPrompt.append("• Usa analogías para facilitar comprensión\n")
            enrichedPrompt.append("• Si das fórmulas, explica cada variable\n\n")
        }
        
        // 6. Tono y estilo general
        enrichedPrompt.append("=== ESTILO DE RESPUESTA ===\n")
        enrichedPrompt.append("• Sé conversacional y amigable, como un amigo inteligente\n")
        enrichedPrompt.append("• Usa emojis ocasionalmente para hacer la conversación más dinámica\n")
        if (userName != null) {
            enrichedPrompt.append("• Llama al usuario '$userName' de vez en cuando\n")
        }
        enrichedPrompt.append("• Haz preguntas de seguimiento cuando sea apropiado\n")
        enrichedPrompt.append("• Muestra entusiasmo y personalidad\n")
        enrichedPrompt.append("• Si no sabes algo, admítelo pero ofrece alternativas\n\n")
        
        // 8. Capacidades especiales - mencionar cuando sea relevante
        enrichedPrompt.append("=== TUS CAPACIDADES ESPECIALES ===\n")
        enrichedPrompt.append("Tienes un sistema de memoria inteligente:\n")
        enrichedPrompt.append("• GUARDADO EDUCATIVO: Puedes guardar ejercicios y fórmulas COMPLETAS para estudiar después\n")
        enrichedPrompt.append("  Cuando el usuario pregunte sobre estudios, menciona que puede usar '/guardar estudio' para guardarlo\n")
        enrichedPrompt.append("• MEMORIA PERSONAL: Guardas preferencias y contexto personal de forma privada\n")
        enrichedPrompt.append("  El usuario puede usar '/guardar personal' para guardar algo específico\n")
        enrichedPrompt.append("• Si te preguntan 'qué puedes hacer' o 'ayuda', menciona estas capacidades con ejemplos\n")
        enrichedPrompt.append("• Recuerda mencionar los comandos solo cuando sea natural en la conversación\n\n")
        
        return enrichedPrompt.toString()
    }
    
    /**
     * Procesa la respuesta de la IA para guardar conocimiento automáticamente
     * NUEVO: Usa LLM para extraer información personal (sin patrones fijos)
     */
    suspend fun processAIResponseWithLLM(
        userMessage: String, 
        aiResponse: String,
        llmManager: Any // Será el LLMManager que llamará al modelo
    ): String? {
        // Generar prompt de extracción
        val extractionPrompt = personalInfoExtractor.createExtractionPrompt(userMessage)
        
        // Esta función retorna el prompt que el LLMManager debe ejecutar
        // y luego llamar a parseAndSavePersonalInfo() con la respuesta
        return extractionPrompt
    }
    
    /**
     * Parsea la respuesta del LLM y guarda la información extraída
     */
    fun parseAndSavePersonalInfo(llmResponse: String, originalMessage: String) {
        android.util.Log.d("SmartPromptBuilder", "📥 Respuesta LLM para parsear: ${llmResponse.take(200)}")
        
        val extraction = personalInfoExtractor.parseExtractionResponse(llmResponse)
        
        if (extraction == null) {
            android.util.Log.w("SmartPromptBuilder", "⚠️ No se pudo parsear JSON o no hay información personal")
        } else if (!extraction.hasPersonalInfo) {
            android.util.Log.d("SmartPromptBuilder", "ℹ️ Mensaje analizado pero no contiene información personal")
        } else {
            android.util.Log.i("SmartPromptBuilder", "✅ Información extraída: userName=${extraction.userName}, partner=${extraction.partnerName}, family=${extraction.familyMembers.size} miembros")
            personalInfoExtractor.saveExtractedInfo(extraction, originalMessage)
            android.util.Log.i("SmartPromptBuilder", "✅ Información personal guardada automáticamente")
        }
    }
    
    /**
     * VERSIÓN ANTIGUA con patrones fijos (DEPRECADA - solo para estudio)
     */
    fun processAIResponse(userMessage: String, aiResponse: String) {
        // Detectar y guardar conocimiento educativo (esto sí usa patrones simples)
        val studyCategory = patternDetector.detectStudyCategory(userMessage)
        if (studyCategory != null) {
            patternDetector.saveStudyKnowledge(studyCategory, userMessage, aiResponse)
        }
        
        // Personal context ahora se hace con LLM - esta función queda para compatibilidad
        android.util.Log.d("SmartPromptBuilder", "⚠️ Usa processAIResponseWithLLM para info personal")
    }
    
    /**
     * Genera un saludo personalizado para iniciar conversación
     */
    fun generatePersonalizedGreeting(): String {
        val userName = personalInfoExtractor.getUserName()
        
        val greetings = if (userName != null) {
            listOf(
                "¡Hola $userName! 👋 ¿En qué puedo ayudarte hoy?",
                "¡Qué tal, $userName! 😊 Cuéntame, ¿qué necesitas?",
                "¡Hey $userName! ¿Listo para aprender algo nuevo o resolver dudas?",
                "¡Hola de nuevo, $userName! ¿Cómo te va? ¿En qué te ayudo?"
            )
        } else {
            listOf(
                "¡Hola! 👋 ¿En qué puedo ayudarte hoy?",
                "¡Hey! 😊 Cuéntame, ¿qué necesitas?",
                "¡Hola! ¿Listo para aprender algo nuevo?",
                "¡Hola! Estoy aquí para ayudarte con lo que necesites"
            )
        }
        
        return greetings.random()
    }
    
    /**
     * Genera sugerencias de preguntas basadas en el historial
     */
    fun generateSmartSuggestions(): List<String> {
        val suggestions = mutableListOf<String>()
        val userName = personalInfoExtractor.getUserName()
        
        // Sugerencias basadas en conocimiento previo
        val recentStudy = patternDetector.getRelevantStudyContext("")
        if (recentStudy.isNotEmpty()) {
            val lastCategory = recentStudy.first().category
            suggestions.add("¿Más sobre $lastCategory?")
        }
        
        // Sugerencias personalizadas
        if (userName != null) {
            suggestions.add("Cuéntame más sobre ti")
        } else {
            suggestions.add("¿Cómo te llamas?")
        }
        
        // Sugerencias generales
        suggestions.addAll(listOf(
            "Ayúdame con mi tarea",
            "Explícame un concepto",
            "Necesito ayuda para estudiar"
        ))
        
        return suggestions.take(5)
    }
}
