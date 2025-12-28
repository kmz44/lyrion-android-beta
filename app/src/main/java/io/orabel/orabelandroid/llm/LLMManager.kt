/*
 * Copyright (C) 2024 Lyrion
 * Manager unificado para LLMs locales y online (Gemini)
 */

package io.orabel.orabelandroid.llm

import android.content.Context
import android.util.Log
import io.orabel.orabelandroid.gemini.GeminiApiClient
import io.orabel.orabelandroid.ai.SmartPromptBuilder
import io.orabel.orabelandroid.ai.CommandProcessor
import io.orabel.orabelandroid.ai.CommandResult
import io.orabel.orabel.Orabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Manager unificado que maneja tanto modelos locales (Orabel) como online (Gemini)
 */
class LLMManager(private val context: Context) {
    
    companion object {
        private const val TAG = "LLMManager"
    }
    
    private var localOrabel: Orabel? = null
    var geminiClient: GeminiApiClient? = null // 🆕 Público para acceso desde ViewModel
        private set
    private var isUsingGemini = false
    private var currentModelId: Long = -1L
    
    // 🧠 Sistema de prompts inteligentes con contexto personalizado
    private val smartPromptBuilder = SmartPromptBuilder(context)
    
    // ⚡ Procesador de comandos manuales
    private val commandProcessor = CommandProcessor(context)
    
    /**
     * Carga un modelo (local o online)
     */
    suspend fun loadModel(
        modelId: Long,
        modelPath: String,
        modelUrl: String,
        minP: Float,
        temperature: Float,
        isGeminiOnlineModel: Boolean
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Cerrar cualquier modelo anterior PERO mantener la configuración temporal
                val oldIsUsingGemini = isUsingGemini
                val oldCurrentModelId = currentModelId
                
                // Limpiar recursos anteriores sin resetear flags aún
                if (isUsingGemini) {
                    geminiClient = null
                    Log.d(TAG, "🌐 Cliente Gemini anterior cerrado")
                } else {
                    try {
                        localOrabel?.close()
                        Log.d(TAG, "💾 Modelo local anterior cerrado")
                    } catch (e: UnsatisfiedLinkError) {
                        Log.w(TAG, "⚠️ No se pudo cerrar el modelo local correctamente (biblioteca nativa): ${e.message}")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Error cerrando modelo local: ${e.message}")
                    }
                    localOrabel = null
                }
                
                // Ahora configurar el nuevo modelo
                currentModelId = modelId
                isUsingGemini = isGeminiOnlineModel
                
                if (isGeminiOnlineModel) {
                    // Cargar modelo online de Gemini
                    Log.i(TAG, "🌐 Cargando modelo online de Gemini: $modelUrl")
                    geminiClient = GeminiApiClient(context)
                    
                    // Cargar API key personalizada del usuario si existe
                    val prefs = context.getSharedPreferences("gemini_settings", Context.MODE_PRIVATE)
                    val userApiKey = prefs.getString("user_api_key", null)
                    if (!userApiKey.isNullOrBlank()) {
                        Log.i(TAG, "🔑 Usando API key personalizada del usuario")
                        geminiClient!!.setUserApiKey(userApiKey)
                    } else {
                        Log.i(TAG, "🔑 Usando API key predeterminada")
                    }
                    
                    val success = geminiClient!!.initialize(modelUrl)
                    if (success) {
                        Log.i(TAG, "✅ Modelo Gemini cargado exitosamente")
                    } else {
                        Log.e(TAG, "❌ Error cargando modelo Gemini")
                        // Restaurar estado anterior en caso de error
                        isUsingGemini = oldIsUsingGemini
                        currentModelId = oldCurrentModelId
                    }
                    success
                } else {
                    // Cargar modelo local con Orabel
                    Log.i(TAG, "💾 Cargando modelo local: $modelPath")
                    localOrabel = Orabel()
                    localOrabel!!.create(modelPath, minP, temperature, true)
                    Log.i(TAG, "✅ Modelo local cargado exitosamente")
                    true
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error cargando modelo: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Agrega un mensaje del sistema
     * SOLO funciona para Gemini online, el modelo local NO usa system prompts para ahorrar recursos
     */
    fun addSystemPrompt(systemPrompt: String) {
        try {
            if (isUsingGemini) {
                // Para Gemini, actualizar el system prompt personalizado del usuario
                geminiClient?.setUserSystemPrompt(systemPrompt)
                Log.d(TAG, "📝 System prompt para Gemini actualizado: ${systemPrompt.take(50)}...")
            } else {
                // Modelo local: NO agregar system prompts para ahorrar recursos
                Log.d(TAG, "� Modelo local: Omitiendo system prompt para ahorrar recursos")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error agregando system prompt: ${e.message}", e)
        }
    }
    
    /**
     * Configura la API Key personalizada del usuario para Gemini
     */
    fun setGeminiApiKey(apiKey: String?) {
        try {
            geminiClient?.setUserApiKey(apiKey)
            Log.d(TAG, "🔑 API Key de Gemini ${if (apiKey.isNullOrBlank()) "removida" else "configurada"}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error configurando API Key: ${e.message}", e)
        }
    }
    
    /**
     * Agrega un mensaje del usuario al historial
     */
    fun addUserMessage(message: String) {
        try {
            if (isUsingGemini) {
                // Para Gemini, los mensajes se manejan internamente en el chat session
                Log.d(TAG, "📝 Mensaje del usuario para Gemini (manejado internamente)")
            } else {
                localOrabel?.addUserMessage(message)
                Log.d(TAG, "📝 Mensaje del usuario agregado al modelo local")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error agregando mensaje del usuario: ${e.message}", e)
        }
    }
    
    /**
     * Agrega un mensaje del asistente al historial
     */
    fun addAssistantMessage(message: String) {
        try {
            if (isUsingGemini) {
                // Para Gemini, los mensajes se manejan internamente en el chat session
                Log.d(TAG, "📝 Mensaje del asistente para Gemini (manejado internamente)")
            } else {
                localOrabel?.addAssistantMessage(message)
                Log.d(TAG, "📝 Mensaje del asistente agregado al modelo local")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error agregando mensaje del asistente: ${e.message}", e)
        }
    }
    
    /**
     * Obtiene respuesta del modelo como stream con contexto inteligente y procesamiento de comandos
     */
    fun getResponse(query: String, systemPrompt: String = ""): Flow<String> = flow {
        try {
            Log.d(TAG, "🔍 getResponse - isUsingGemini: $isUsingGemini, currentModelId: $currentModelId")
            Log.d(TAG, "🔍 getResponse - geminiClient: ${geminiClient?.isReady()}, localOrabel: ${localOrabel != null}")
            
            // ⚡ PRIMERO: Verificar si es un comando especial
            val command = commandProcessor.detectCommand(query)
            if (command != null) {
                Log.d(TAG, "⚡ Comando detectado: $command")
                // Si es un comando de guardado, necesitamos esperar la respuesta del LLM
                // Si es comando de vista o ayuda, responder directamente
                when (command) {
                    io.orabel.orabelandroid.ai.Command.VIEW_STUDY,
                    io.orabel.orabelandroid.ai.Command.VIEW_PERSONAL,
                    io.orabel.orabelandroid.ai.Command.SHOW_CAPABILITIES -> {
                        // Ejecutar comando inmediatamente
                        val result = commandProcessor.processCommand(command, query)
                        when (result) {
                            is CommandResult.Success -> emit(result.message)
                            is CommandResult.Error -> emit(result.message)
                        }
                        return@flow // No continuar con el LLM
                    }
                    else -> {
                        // Comandos de guardado se procesan después de la respuesta
                        Log.d(TAG, "⚡ Comando de guardado detectado, procesará después de la respuesta")
                    }
                }
            }
            
            // 🧠 Enriquecer el prompt con contexto inteligente SOLO para Gemini online
            val enrichedPrompt = if (isUsingGemini) {
                // Solo enriquecer para modelo online
                if (systemPrompt.isNotBlank()) {
                    smartPromptBuilder.buildEnrichedPrompt(query, systemPrompt)
                } else {
                    // Si no hay system prompt, crear uno básico
                    val basicSystemPrompt = """
                        Eres Lyrion, un asistente de IA inteligente, amigable y útil.
                        Ayudas con estudios, consejos personales, y conversaciones del día a día.
                    """.trimIndent()
                    smartPromptBuilder.buildEnrichedPrompt(query, basicSystemPrompt)
                }
            } else {
                // Modelo local: NO usar contexto para ahorrar recursos
                ""
            }
            
            if (enrichedPrompt.isNotBlank()) {
                Log.d(TAG, "🧠 Prompt enriquecido con contexto para Gemini (${enrichedPrompt.length} caracteres)")
            } else {
                Log.d(TAG, "💾 Modelo local: Sin contexto (ahorro de recursos)")
            }
            
            var fullResponse = StringBuilder()
            
            if (isUsingGemini) {
                // Usar Gemini API
                geminiClient?.let { client ->
                    Log.d(TAG, "🌐 Enviando query a Gemini: ${query.take(100)}...")
                    if (client.isReady()) {
                        // 🔥 IMPORTANTE: Actualizar el system prompt con contexto personal ANTES de enviar
                        // Gemini maneja el system prompt internamente
                        client.setUserSystemPrompt(enrichedPrompt)
                        Log.d(TAG, "🧠 System prompt actualizado con contexto personal para Gemini")
                        
                        client.sendMessage(query).collect { response ->
                            fullResponse.append(response)
                            emit(response)
                        }
                    } else {
                        emit("❌ Error: Cliente Gemini no está listo")
                    }
                } ?: run {
                    emit("❌ Error: Cliente Gemini no inicializado")
                }
            } else {
                // Usar modelo local SIN contexto adicional (ahorro de recursos)
                localOrabel?.let { orabel ->
                    Log.d(TAG, "💾 Enviando query directa a modelo local (sin contexto): ${query.take(100)}...")
                    
                    // ⚡ MODELO LOCAL: Solo enviar la pregunta directa, SIN prompts de sistema ni contexto
                    // Esto ahorra recursos y evita que lea información del usuario
                    orabel.getResponse(query).collect { response ->
                        fullResponse.append(response)
                        emit(response)
                    }
                } ?: run {
                    emit("❌ Error: Modelo local no cargado")
                }
            }
            
            // 🧠 Guardar conocimiento automáticamente después de la respuesta completa
            // SOLO para modelo ONLINE (Gemini), el modelo local NO guarda contexto
            if (isUsingGemini) {
                withContext(Dispatchers.IO) {
                    try {
                        // Si había un comando de guardado, procesarlo AHORA con la respuesta completa
                        if (command != null && 
                            (command == io.orabel.orabelandroid.ai.Command.SAVE_STUDY || 
                             command == io.orabel.orabelandroid.ai.Command.SAVE_PERSONAL)) {
                            val result = commandProcessor.processCommand(command, query, fullResponse.toString())
                            when (result) {
                                is CommandResult.Success -> {
                                    Log.i(TAG, "⚡ Comando ejecutado: ${result.message.take(100)}")
                                    // Emitir mensaje de confirmación
                                    emit("\n\n" + result.message)
                                }
                                is CommandResult.Error -> {
                                    Log.e(TAG, "⚡ Error ejecutando comando: ${result.message}")
                                }
                            }
                        } else {
                            // Guardado automático normal solo para Gemini
                            smartPromptBuilder.processAIResponse(query, fullResponse.toString())
                            Log.d(TAG, "🧠 Conocimiento educativo procesado (solo Gemini)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error guardando conocimiento: ${e.message}", e)
                    }
                }
                
                // 🔥 EXTRACCIÓN DE INFO PERSONAL (SOLO GEMINI)
                // Esta llamada NO bloquea la respuesta al usuario
                extractPersonalInfoAsync(query)
            } else {
                Log.d(TAG, "💾 Modelo local: Omitiendo guardado de contexto para ahorrar recursos")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo respuesta: ${e.message}", e)
            emit("❌ Error: ${e.message}")
        }
    }
    
    /**
     * Detiene la generación de respuesta
     */
    fun stopCompletion() {
        try {
            if (!isUsingGemini) {
                localOrabel?.stopCompletion()
                Log.d(TAG, "🛑 Generación detenida en modelo local")
            }
            // Para Gemini, la cancelación se maneja en el nivel de Flow/coroutine
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deteniendo generación: ${e.message}", e)
        }
    }
    
    /**
     * Cancela la generación de respuesta
     */
    fun cancelCompletion() {
        try {
            if (!isUsingGemini) {
                localOrabel?.cancelCompletion()
                Log.d(TAG, "❌ Generación cancelada en modelo local")
            }
            // Para Gemini, la cancelación se maneja en el nivel de Flow/coroutine
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelando generación: ${e.message}", e)
        }
    }
    
    /**
     * Cierra el modelo actual
     */
    fun close() {
        try {
            if (isUsingGemini) {
                geminiClient = null
                Log.d(TAG, "🌐 Cliente Gemini cerrado")
            } else {
                try {
                    localOrabel?.close()
                    Log.d(TAG, "💾 Modelo local cerrado")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "⚠️ No se pudo cerrar el modelo local correctamente (biblioteca nativa): ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Error cerrando modelo local: ${e.message}")
                }
                localOrabel = null
            }
            isUsingGemini = false
            currentModelId = -1L
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cerrando modelo: ${e.message}", e)
        }
    }
    
    /**
     * Verifica si está usando Gemini
     */
    fun isUsingGeminiModel(): Boolean = isUsingGemini
    
    /**
     * Obtiene el ID del modelo actual
     */
    fun getCurrentModelId(): Long = currentModelId
    
    /**
     * 🧠 Genera un saludo personalizado con el nombre del usuario
     */
    fun generatePersonalizedGreeting(): String {
        return smartPromptBuilder.generatePersonalizedGreeting()
    }
    
    /**
     * 🧠 Genera sugerencias inteligentes basadas en el historial
     */
    fun generateSmartSuggestions(): List<String> {
        return smartPromptBuilder.generateSmartSuggestions()
    }
    
    /**
     * 🔥 Extrae información personal de forma asíncrona (NO bloquea la respuesta al usuario)
     * Esta función se ejecuta en segundo plano DESPUÉS de responder al usuario
     */
    private fun extractPersonalInfoAsync(userMessage: String) {
        // Lanzar en un scope separado para NO bloquear
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "🤖 Iniciando extracción asíncrona de información personal...")
                
                val extractionPrompt = smartPromptBuilder.processAIResponseWithLLM(
                    userMessage,
                    "", // No necesitamos la respuesta de la IA para analizar el mensaje del usuario
                    this@LLMManager
                )
                
                if (extractionPrompt != null) {
                    val extractionResponse = StringBuilder()
                    
                    // Hacer llamada DIRECTA al LLM (sin usar el flow público)
                    if (isUsingGemini) {
                        geminiClient?.let { client ->
                            if (client.isReady()) {
                                client.sendMessage(extractionPrompt).collect { chunk ->
                                    extractionResponse.append(chunk)
                                }
                            }
                        }
                    } else {
                        localOrabel?.let { orabel ->
                            orabel.getResponse(extractionPrompt).collect { chunk ->
                                extractionResponse.append(chunk)
                            }
                        }
                    }
                    
                    // Parsear y guardar
                    smartPromptBuilder.parseAndSavePersonalInfo(
                        extractionResponse.toString(),
                        userMessage
                    )
                    Log.i(TAG, "✅ Extracción asíncrona completada y guardada")
                }
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Error en extracción asíncrona (no crítico): ${e.message}")
            }
        }
    }
}
