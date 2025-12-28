/*
 * Copyright (C) 2024 Lyrion
 * Procesador de Comandos Manuales para Guardado
 * Permite al usuario guardar información explícitamente
 */

package io.orabel.orabelandroid.ai

import android.content.Context
import android.util.Log
import io.orabel.orabelandroid.data.StudyKnowledge
import io.orabel.orabelandroid.data.PersonalContext
import io.objectbox.Box
import io.objectbox.BoxStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Procesa comandos especiales del usuario para guardar información manualmente
 */
class CommandProcessor(context: Context) : KoinComponent {
    
    companion object {
        private const val TAG = "CommandProcessor"
    }
    
    private val boxStore: BoxStore by inject()
    private val studyBox: Box<StudyKnowledge> by lazy {
        boxStore.boxFor(StudyKnowledge::class.java)
    }
    private val personalBox: Box<PersonalContext> by lazy {
        boxStore.boxFor(PersonalContext::class.java)
    }
    
    // Usar PersonalInfoExtractor en lugar de SmartPatternDetector para info personal
    private val personalExtractor = PersonalInfoExtractor(context)
    private val patternDetector = SmartPatternDetector(context)
    
    /**
     * Detecta si el mensaje contiene un comando especial
     */
    fun detectCommand(message: String): Command? {
        val lowerMessage = message.lowercase()
        
        return when {
            // Comandos de guardado de estudio
            lowerMessage.contains("/guardar estudio") || 
            lowerMessage.contains("/guardar_estudio") ||
            lowerMessage.contains("guarda esto como estudio") ||
            lowerMessage.contains("guardar en mis clases") ||
            lowerMessage.contains("guardar para estudiar") -> {
                Command.SAVE_STUDY
            }
            
            // Comandos de guardado personal
            lowerMessage.contains("/guardar personal") || 
            lowerMessage.contains("/guardar_personal") ||
            lowerMessage.contains("guarda esto personal") ||
            lowerMessage.contains("guardar en mi perfil") -> {
                Command.SAVE_PERSONAL
            }
            
            // Ver conocimiento guardado
            lowerMessage.contains("/mis clases") ||
            lowerMessage.contains("/ver estudio") ||
            lowerMessage.contains("muestra mis clases") -> {
                Command.VIEW_STUDY
            }
            
            // Ver información personal
            lowerMessage.contains("/mi perfil") ||
            lowerMessage.contains("/ver personal") ||
            lowerMessage.contains("muestra mi información") -> {
                Command.VIEW_PERSONAL
            }
            
            // Capacidades de la IA
            lowerMessage.contains("qué puedes hacer") ||
            lowerMessage.contains("que puedes hacer") ||
            lowerMessage.contains("ayuda") ||
            lowerMessage.contains("/ayuda") -> {
                Command.SHOW_CAPABILITIES
            }
            
            else -> null
        }
    }
    
    /**
     * Procesa el comando y retorna un mensaje de respuesta
     */
    fun processCommand(command: Command, message: String, context: String = ""): CommandResult {
        return when (command) {
            Command.SAVE_STUDY -> saveStudyManually(message, context)
            Command.SAVE_PERSONAL -> savePersonalManually(message, context)
            Command.VIEW_STUDY -> viewStudyKnowledge()
            Command.VIEW_PERSONAL -> viewPersonalContext()
            Command.SHOW_CAPABILITIES -> showCapabilities()
        }
    }
    
    /**
     * Guarda contenido educativo manualmente
     */
    private fun saveStudyManually(userMessage: String, aiResponse: String): CommandResult {
        try {
            // Limpiar el comando del mensaje
            val cleanMessage = userMessage
                .replace(Regex("/guardar[_\\s]estudio", RegexOption.IGNORE_CASE), "")
                .replace(Regex("guarda esto como estudio", RegexOption.IGNORE_CASE), "")
                .replace(Regex("guardar (en mis clases|para estudiar)", RegexOption.IGNORE_CASE), "")
                .trim()
            
            // Detectar categoría automáticamente
            val category = patternDetector.detectStudyCategory(cleanMessage) ?: "general"
            
            // Extraer fórmulas
            val formulas = patternDetector.extractFormulas(aiResponse)
            
            // Crear el conocimiento
            val knowledge = StudyKnowledge(
                category = category,
                topic = cleanMessage.take(200), // Título del tema
                content = aiResponse, // Contenido COMPLETO de la respuesta
                formulas = formulas.joinToString("\n"),
                examples = "", // Se puede mejorar con extracción de ejemplos
                timestamp = System.currentTimeMillis(),
                relevanceScore = 100, // Usuario lo guardó manualmente = máxima relevancia
                useCount = 0,
                keywords = cleanMessage.lowercase().split(" ").filter { it.length > 3 }.joinToString(","),
                difficulty = "media"
            )
            
            studyBox.put(knowledge)
            
            Log.i(TAG, "📚 Conocimiento educativo guardado manualmente: $category - ${knowledge.topic}")
            
            return CommandResult.Success(
                """
                ✅ **Guardado en tu biblioteca de estudio**
                
                📂 Categoría: ${category.capitalize()}
                📝 Tema: ${knowledge.topic}
                ${if (formulas.isNotEmpty()) "🔢 Fórmulas extraídas: ${formulas.size}" else ""}
                
                Puedes acceder a esto después con "/mis clases" o desde el botón "Compartir Clases" en el menú.
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando conocimiento educativo: ${e.message}", e)
            return CommandResult.Error("❌ Error al guardar: ${e.message}")
        }
    }
    
    /**
     * Guarda contexto personal manualmente
     */
    private fun savePersonalManually(userMessage: String, aiResponse: String): CommandResult {
        try {
            val cleanMessage = userMessage
                .replace(Regex("/guardar[_\\s]personal", RegexOption.IGNORE_CASE), "")
                .replace(Regex("guarda esto personal", RegexOption.IGNORE_CASE), "")
                .trim()
            
            // Guardar como nota personal general (sin necesidad de LLM para comandos manuales)
            val context = PersonalContext(
                contextType = "preferences", // Tipo genérico para guardado manual
                key = "note_${System.currentTimeMillis()}",
                value = cleanMessage,
                notes = "Guardado manual: $cleanMessage",
                timestamp = System.currentTimeMillis(),
                lastUsed = System.currentTimeMillis(),
                relevanceScore = 100, // Guardado manual = alta relevancia
                useCount = 0,
                category = "personal",
                emotion = "",
                isActive = true
            )
            personalBox.put(context)
            
            Log.i(TAG, "🔒 Contexto personal guardado manualmente")
            
            return CommandResult.Success(
                """
                ✅ **Guardado en tu perfil personal**
                
                📝 Información guardada de forma privada
                
                Accede con "/mi perfil" o el botón "Yo" en el menú.
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando contexto personal: ${e.message}", e)
            return CommandResult.Error("❌ Error al guardar: ${e.message}")
        }
    }
    
    /**
     * Muestra el conocimiento educativo guardado
     */
    private fun viewStudyKnowledge(): CommandResult {
        val allKnowledge = studyBox.all.sortedByDescending { it.timestamp }
        
        if (allKnowledge.isEmpty()) {
            return CommandResult.Success(
                """
                📚 **Tu biblioteca de estudio está vacía**
                
                Puedes guardar información de varias formas:
                1. Automáticamente: Solo pregúntame sobre matemáticas, química, física, etc.
                2. Manualmente: Usa "/guardar estudio" antes de tu pregunta
                
                Ejemplo: "/guardar estudio ¿Cómo se resuelven ecuaciones cuadráticas?"
                """.trimIndent()
            )
        }
        
        val categories = allKnowledge.groupBy { it.category }
        val summary = buildString {
            append("📚 **Tu Biblioteca de Estudio**\n\n")
            categories.forEach { (category, items) ->
                append("**${category.capitalize()}** (${items.size} temas)\n")
                items.take(3).forEach { knowledge ->
                    append("  • ${knowledge.topic.take(50)}...\n")
                }
                if (items.size > 3) {
                    append("  ... y ${items.size - 3} más\n")
                }
                append("\n")
            }
            append("\n💡 Usa el botón **'Compartir Clases'** para ver todo el contenido con fórmulas.")
        }
        
        return CommandResult.Success(summary)
    }
    
    /**
     * Muestra el contexto personal guardado
     */
    private fun viewPersonalContext(): CommandResult {
        val allContext = personalBox.all.filter { it.isActive }
        
        if (allContext.isEmpty()) {
            return CommandResult.Success(
                """
                🔒 **Tu perfil personal está vacío**
                
                Guarda información personal para respuestas más personalizadas:
                - Tu nombre
                - Tus gustos
                - Información de pareja
                - Preferencias diarias
                
                Usa "/guardar personal" seguido de tu información.
                """.trimIndent()
            )
        }
        
        val userName = personalExtractor.getUserName()
        val summary = buildString {
            append("🔒 **Tu Perfil Personal**\n\n")
            if (userName != null) {
                append("👤 Nombre: $userName\n\n")
            }
            
            val types = allContext.groupBy { it.contextType }
            types.forEach { (type, items) ->
                append("**${type.capitalize()}**: ${items.size} elemento(s)\n")
            }
            
            append("\n💡 Usa el botón **'Yo'** para gestionar tu información personal.")
        }
        
        return CommandResult.Success(summary)
    }
    
    /**
     * Muestra las capacidades de la IA
     */
    private fun showCapabilities(): CommandResult {
        val dollarSign = "$"
        return CommandResult.Success(
            """
            🤖 **Soy Lyrion, tu asistente inteligente avanzado**
            
            📚 **Sistema de Aprendizaje Educativo**
            • Detecta automáticamente cuando preguntas sobre matemáticas, química, física, redacción o programación
            • Guarda TODOS los ejercicios y fórmulas COMPLETOS
            • Puedes estudiar después con "/mis clases" o "Compartir Clases"
            • Guardado manual: "/guardar estudio" + tu pregunta
            
            🔒 **Memoria Personal Privada**
            • Recuerda tu nombre, gustos, y preferencias
            • Se adapta a ti con respuestas personalizadas
            • Todo es privado y solo tuyo
            • Guardado manual: "/guardar personal" + tu información
            
            💡 **Comandos Útiles**
            • `/guardar estudio` - Guarda ejercicios y fórmulas
            • `/guardar personal` - Guarda info personal
            • `/mis clases` - Ver tu biblioteca de estudio
            • `/mi perfil` - Ver tu información personal
            • `/ayuda` - Mostrar este mensaje
            
            🎯 **Características Especiales**
            • Respuestas con tu nombre
            • Fórmulas en LaTeX: ${dollarSign}${dollarSign}E=mc^2${dollarSign}${dollarSign}
            • Recordatorio de temas previos
            • Sugerencias inteligentes
            
            ¿En qué puedo ayudarte hoy? 😊
            """.trimIndent()
        )
    }
}

/**
 * Tipos de comandos disponibles
 */
enum class Command {
    SAVE_STUDY,
    SAVE_PERSONAL,
    VIEW_STUDY,
    VIEW_PERSONAL,
    SHOW_CAPABILITIES
}

/**
 * Resultado de ejecutar un comando
 */
sealed class CommandResult {
    data class Success(val message: String) : CommandResult()
    data class Error(val message: String) : CommandResult()
}
