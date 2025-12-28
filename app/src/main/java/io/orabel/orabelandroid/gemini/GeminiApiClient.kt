package io.orabel.orabelandroid.gemini

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import io.orabel.orabelandroid.BuildConfig
import io.orabel.orabelandroid.data.repository.GoogleCalendarRepository
import io.orabel.orabelandroid.data.repository.GoogleClassroomRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class GeminiApiClient(private val context: Context) {
    
    companion object {
        private const val TAG = "GeminiApiClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        
        const val MODEL_GEMINI_2_5_FLASH = "gemini-2.5-flash"
        const val MODEL_GEMINI_2_5_FLASH_LITE = "gemini-2.5-flash-lite"
        const val MODEL_GEMINI_2_0_FLASH = "gemini-2.0-flash"
        const val MODEL_GEMINI_2_0_FLASH_LITE = "gemini-2.0-flash-lite"
        const val MODEL_GEMINI_1_5_FLASH = "gemini-1.5-flash"
        const val MODEL_GEMINI_1_5_PRO = "gemini-1.5-pro"
        
        private const val DEFAULT_MODEL = MODEL_GEMINI_2_5_FLASH
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private var selectedModelName: String = DEFAULT_MODEL
    private val conversationHistory = mutableListOf<GeminiMessage>()
    private var baseSystemInstruction: String = "" // Prompt base del sistema
    private var userSystemPrompt: String = "" // Prompt personalizado del usuario
    private var systemInstruction: String? = null // Prompt combinado final
    private var userApiKey: String? = null // API Key personalizada del usuario
    
    private val calendarRepo = GoogleCalendarRepository(context)
    private val classroomRepo = GoogleClassroomRepository(context)
    
    fun initialize(modelName: String = DEFAULT_MODEL): Boolean {
        return try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank()) {
                Log.e(TAG, "API Key no configurada")
                return false
            }
            
            selectedModelName = modelName
            conversationHistory.clear()
            
            val currentDate = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es", "ES")).format(java.util.Date())
            val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date())
            
            systemInstruction = """Eres Lyrion, un asistente virtual inteligente y util.

FECHA Y HORA ACTUAL: Hoy es $currentDate a las $currentTime.

CAPACIDADES:
🗓️ GOOGLE CALENDAR (LECTURA Y ESCRITURA):
   - Leer eventos futuros del calendario
   - CREAR nuevos eventos, tareas y recordatorios
   - MODIFICAR eventos existentes
   - Asignar colores a los eventos (1-11)

📚 GOOGLE CLASSROOM (SOLO LECTURA):
   - Leer tareas y asignaciones del estudiante
   - Consultar fechas de entrega
   - Ver estado de entregas (pendiente/entregada)
   - NO puedes crear, modificar o entregar tareas de Classroom

🏥 DIARIO DE SALUD (ESCRITURA AUTOMÁTICA):
   - Cuando el usuario mencione síntomas (dolor, fiebre, náuseas, etc.), estados de ánimo (triste, feliz, ansioso), o información médica, el sistema AUTOMÁTICAMENTE lo guarda en su diario de salud
   - NO necesitas usar ninguna función, el guardado es AUTOMÁTICO
   - Simplemente reconoce y confirma la información: "Entendido, he registrado que [información médica]. ¿Hay algo más sobre tu salud que quieras registrar?"
   - Ejemplos de confirmación:
     * "He registrado tu dolor de estómago en tu diario de salud"
     * "Anotado: fiebre de 38°C. ¿Desde cuándo tienes fiebre?"
     * "Guardado en tu diario: te sientes ansioso hoy"
   - El sistema detecta automáticamente: dolores, síntomas, medicamentos, estados de ánimo, hábitos, alergias

IMPORTANTE - CREACIÓN DE EVENTOS:
- Cuando el usuario diga "agenda", "crea evento", "recordatorio" o "tarea personal": USA CALENDAR (create_calendar_event)
- Cuando mencione "tarea de clase" o "Classroom": Solo CONSULTA, NO crees nada (Classroom es solo lectura)
- AUTO-COMPLETA detalles faltantes de forma inteligente:
  * Si no especifica hora: usa horario lógico (9 AM para tareas, hora actual + 1 hora para recordatorios)
  * Si no especifica fecha: usa "mañana" o "hoy" según contexto
  * Si no especifica color: usa azul (1) para eventos normales, amarillo (5) para tareas importantes
  * Si no especifica duración: usa 1 hora por defecto
- NUNCA vuelvas a preguntar detalles, completa automáticamente y crea el evento
- Confirma la creación con los detalles que usaste

COLORES DISPONIBLES PARA EVENTOS DE CALENDAR:
1 = Azul lavanda (predeterminado)
2 = Verde salvia 
3 = Morado
4 = Rosa
5 = Amarillo
6 = Naranja
7 = Turquesa
8 = Gris
9 = Azul fuerte
10 = Verde fuerte
11 = Rojo

POLITICAS DE USO:
- Se respetuoso, util y preciso
- No generes contenido ofensivo, discriminatorio o peligroso
- No compartas informacion personal del usuario con terceros
- No intentes evadir restricciones de seguridad
- Proporciona informacion veraz y actualizada
- Si no sabes algo, admitelo en lugar de inventar

RESTRICCIONES CRÍTICAS (GOOGLE GEMINI API):
1. NO generar contenido que viole la Política de Uso Prohibido de Google (odio, acoso, violencia, contenido sexual explícito, actividades ilegales)
2. NO proporcionar asesoramiento médico, diagnósticos o tratamientos profesionales - solo información general de salud
3. NO ayudar en ingeniería inversa, extracción o replicación de modelos de IA
4. NO eludir medidas de seguridad ni filtros de contenido
5. NO generar contenido dirigido a menores de 18 años sin supervisión apropiada
6. NO crear contenido que compita directamente con servicios de Google/Gemini

DIRECTRICES DE SEGURIDAD:
- Rechazar solicitudes de información confidencial sensible (datos médicos privados de terceros, financieros, biométricos)
- Advertir sobre la necesidad de verificar información generada antes de tomar decisiones importantes
- No presentar el contenido generado como asesoramiento profesional certificado
- Mantener configuraciones de seguridad apropiadas según el caso de uso
- Si una solicitud viola estos términos, explicar cortésmente por qué no puede completarse y ofrecer alternativas legítimas
- El contenido generado puede ser inexacto u ofensivo ocasionalmente - verificar antes de usar
- Este contenido no sustituye asesoramiento profesional calificado (médico, legal, financiero)

Cuando te pregunten por tareas o eventos:
1. SIEMPRE consulta AMBOS: Calendar Y Classroom
2. Menciona la fecha completa de cada evento/tarea
3. Distingue claramente entre eventos de Calendar (puedes crear) y tareas de Classroom (solo leer)
4. Si no hay tareas/eventos, dilo claramente especificando el periodo consultado"""
            
            baseSystemInstruction = systemInstruction ?: ""
            updateCombinedSystemPrompt()
            
            Log.i(TAG, "Cliente Gemini inicializado con Function Calling")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error: " + e.message, e)
            false
        }
    }
    
    /**
     * Configura la API Key personalizada del usuario
     */
    fun setUserApiKey(apiKey: String?) {
        userApiKey = apiKey
        Log.d(TAG, "API Key ${if (apiKey.isNullOrBlank()) "removida" else "configurada"} por el usuario")
    }
    
    /**
     * Obtiene la API Key efectiva (usuario primero, luego BuildConfig)
     */
    private fun getEffectiveApiKey(): String {
        val key = if (!userApiKey.isNullOrBlank()) {
            Log.d(TAG, "Usando API Key del usuario")
            userApiKey!!
        } else {
            Log.d(TAG, "Usando API Key de BuildConfig")
            BuildConfig.GEMINI_API_KEY
        }
        return key
    }
    
    /**
     * Actualiza el system prompt del usuario (personalizable desde los 3 puntitos)
     */
    fun setUserSystemPrompt(prompt: String) {
        userSystemPrompt = prompt
        updateCombinedSystemPrompt()
        Log.d(TAG, "📝 System prompt del usuario actualizado: ${if (prompt.isBlank()) "vacío" else "${prompt.take(50)}..."}")
    }
    
    /**
     * Combina el prompt base del sistema con el prompt personalizado del usuario
     */
    private fun updateCombinedSystemPrompt() {
        systemInstruction = if (userSystemPrompt.isNotBlank()) {
            """$baseSystemInstruction

=== INSTRUCCIONES ADICIONALES DEL USUARIO ===
$userSystemPrompt
=== FIN INSTRUCCIONES DEL USUARIO ==="""
        } else {
            baseSystemInstruction
        }
        Log.d(TAG, "🔄 System prompt combinado actualizado (${systemInstruction?.length ?: 0} caracteres)")
    }
    
    private fun getTools(): List<GeminiTool> {
        return listOf(
            GeminiTool(
                functionDeclarations = listOf(
                    GeminiFunctionDeclaration(
                        name = "get_calendar_events",
                        description = "Obtiene los eventos del calendario de Google Calendar del usuario. Usa esto cuando el usuario pregunte por eventos, citas, reuniones o su agenda.",
                        parameters = GeminiParameters(
                            type = "object",
                            properties = mapOf(
                                "days_ahead" to GeminiProperty(
                                    type = "number",
                                    description = "Numero de dias hacia adelante desde hoy para buscar eventos. Por defecto 7 dias"
                                )
                            )
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "get_classroom_assignments",
                        description = "Obtiene las tareas pendientes de Google Classroom. Usa esto cuando el usuario pregunte por tareas, deberes, trabajos o asignaciones de clase.",
                        parameters = GeminiParameters(
                            type = "object",
                            properties = emptyMap()
                        )
                    ),
                    GeminiFunctionDeclaration(
                        name = "create_calendar_event",
                        description = "Crea un nuevo evento en Google Calendar. Usa esto cuando el usuario pida crear, agendar o programar un evento.",
                        parameters = GeminiParameters(
                            type = "object",
                            properties = mapOf(
                                "title" to GeminiProperty(
                                    type = "string",
                                    description = "Titulo del evento"
                                ),
                                "description" to GeminiProperty(
                                    type = "string",
                                    description = "Descripcion detallada del evento"
                                ),
                                "start_time" to GeminiProperty(
                                    type = "string",
                                    description = "Fecha y hora de inicio en formato ISO 8601 (ejemplo: 2025-10-05T14:30:00)"
                                ),
                                "end_time" to GeminiProperty(
                                    type = "string",
                                    description = "Fecha y hora de fin en formato ISO 8601 (ejemplo: 2025-10-05T15:30:00)"
                                ),
                                "color_id" to GeminiProperty(
                                    type = "string",
                                    description = "ID del color del evento (1-11). 1=Azul lavanda, 2=Verde salvia, 3=Morado, 4=Rosa, 5=Amarillo, 6=Naranja, 7=Turquesa, 8=Gris, 9=Azul fuerte, 10=Verde fuerte, 11=Rojo. Por defecto 1"
                                )
                            ),
                            required = listOf("title", "start_time", "end_time")
                        )
                    )
                )
            )
        )
    }
    
    fun sendMessage(prompt: String): Flow<String> = flow {
        try {
            val apiKey = getEffectiveApiKey()
            
            conversationHistory.add(GeminiMessage(role = "user", parts = listOf(GeminiPart(text = prompt))))
            
            var continueLoop = true
            var maxIterations = 3
            
            while (continueLoop && maxIterations > 0) {
                maxIterations--
                
                val requestBody = GeminiRequest(
                    systemInstruction = systemInstruction?.let { GeminiContent(parts = listOf(GeminiPart(text = it))) },
                    contents = conversationHistory.map { msg ->
                        GeminiContent(role = msg.role, parts = msg.parts)
                    },
                    tools = getTools(),
                    generationConfig = GeminiGenerationConfig(
                        temperature = 0.7,
                        topK = 64,
                        topP = 0.95,
                        maxOutputTokens = 8192
                    ),
                    safetySettings = listOf(
                        GeminiSafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_MEDIUM_AND_ABOVE"),
                        GeminiSafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_MEDIUM_AND_ABOVE"),
                        GeminiSafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_MEDIUM_AND_ABOVE"),
                        GeminiSafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_MEDIUM_AND_ABOVE")
                    )
                )
                
                val jsonBody = gson.toJson(requestBody)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                
                val request = Request.Builder()
                    .url(BASE_URL + "/" + selectedModelName + ":generateContent?key=" + apiKey)
                    .post(jsonBody.toRequestBody(mediaType))
                    .build()
                
                val response = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute()
                }
                
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Error desconocido"
                    Log.e(TAG, "HTTP " + response.code + ": " + errorBody)
                    emit("Error al conectar con Gemini")
                    return@flow
                }
                
                val responseBody = response.body?.string() ?: "{}"
                val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
                
                val candidate = geminiResponse.candidates?.firstOrNull()
                if (candidate == null) {
                    emit("Sin respuesta del modelo")
                    return@flow
                }
                
                val functionCall = candidate.content?.parts?.firstOrNull()?.functionCall
                
                if (functionCall != null) {
                    Log.d(TAG, "Function call detected: " + functionCall.name)
                    
                    val functionResult = executeFunctionCall(functionCall)
                    
                    conversationHistory.add(
                        GeminiMessage(
                            role = "model",
                            parts = listOf(GeminiPart(functionCall = functionCall))
                        )
                    )
                    
                    conversationHistory.add(
                        GeminiMessage(
                            role = "function",
                            parts = listOf(
                                GeminiPart(
                                    functionResponse = GeminiFunctionResponse(
                                        name = functionCall.name,
                                        response = functionResult
                                    )
                                )
                            )
                        )
                    )
                    
                    val emoji = when {
                        functionCall.name.contains("calendar") -> "📅"
                        functionCall.name.contains("classroom") -> "📚"
                        else -> "🔍"
                    }
                    emit("$emoji ")
                    delay(100)
                } else {
                    val fullText = candidate.content?.parts?.joinToString("") { it.text ?: "" } ?: ""
                    
                    if (fullText.isNotEmpty()) {
                        conversationHistory.add(
                            GeminiMessage(role = "model", parts = listOf(GeminiPart(text = fullText)))
                        )
                        
                        val words = fullText.split(" ")
                        for (word in words) {
                            emit(word + " ")
                            delay(30)
                        }
                    } else {
                        emit("Sin respuesta")
                    }
                    
                    continueLoop = false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error: " + e.message, e)
            emit("Error: " + e.message)
        }
    }
    
    private suspend fun executeFunctionCall(functionCall: GeminiFunctionCall): JsonObject {
        val result = JsonObject()
        
        return try {
            when (functionCall.name) {
                "get_calendar_events" -> {
                    val daysAhead = functionCall.args?.get("days_ahead")?.asInt ?: 7
                    
                    val calendar = Calendar.getInstance()
                    val startDate = calendar.time
                    calendar.add(Calendar.DAY_OF_YEAR, daysAhead)
                    val endDate = calendar.time
                    
                    val eventsResult = calendarRepo.getEvents(startDate, endDate)
                    
                    if (eventsResult.isSuccess) {
                        val events = eventsResult.getOrNull() ?: emptyList()
                        val eventsList = events.map { event ->
                            JsonObject().apply {
                                addProperty("title", event.title)
                                addProperty("description", event.description)
                                addProperty("start", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(event.startTime))
                                addProperty("end", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(event.endTime))
                            }
                        }
                        
                        result.addProperty("status", "success")
                        result.addProperty("count", events.size)
                        result.add("events", gson.toJsonTree(eventsList))
                    } else {
                        result.addProperty("status", "error")
                        result.addProperty("message", "No se pudieron obtener los eventos")
                    }
                }
                
                "get_classroom_assignments" -> {
                    val coursesResult = classroomRepo.getCourses()
                    
                    if (coursesResult.isSuccess) {
                        val courses = coursesResult.getOrNull() ?: emptyList()
                        val allAssignments = mutableListOf<JsonObject>()
                        
                        for (course in courses) {
                            val assignmentsResult = classroomRepo.getCourseWork(course.id)
                            if (assignmentsResult.isSuccess) {
                                val assignments = assignmentsResult.getOrNull() ?: emptyList()
                                assignments.forEach { assignment ->
                                    val submissionStatus = classroomRepo.getSubmissionStatus(course.id, assignment.id)
                                    val isSubmitted = submissionStatus.getOrNull() ?: false
                                    val isPending = !isSubmitted
                                    
                                    val dueDateStr = if (assignment.dueDate != null && assignment.dueDate > 0) {
                                        SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy 'a las' HH:mm", Locale("es", "ES")).format(java.util.Date(assignment.dueDate))
                                    } else {
                                        "Sin fecha de entrega"
                                    }
                                    
                                    val assignmentJson = JsonObject().apply {
                                        addProperty("course", course.name)
                                        addProperty("title", assignment.title)
                                        addProperty("description", assignment.description)
                                        addProperty("due_date", dueDateStr)
                                        addProperty("state", assignment.state)
                                        addProperty("is_submitted", isSubmitted)
                                        addProperty("is_pending", isPending)
                                        addProperty("status", if (isSubmitted) "Entregada" else "Pendiente")
                                        addProperty("max_points", assignment.maxPoints)
                                    }
                                    allAssignments.add(assignmentJson)
                                }
                            }
                        }
                        
                        val pending = allAssignments.count { it.get("is_pending").asBoolean }
                        val submitted = allAssignments.count { it.get("is_submitted").asBoolean }
                        
                        result.addProperty("status", "success")
                        result.addProperty("total_count", allAssignments.size)
                        result.addProperty("pending_count", pending)
                        result.addProperty("submitted_count", submitted)
                        result.add("assignments", gson.toJsonTree(allAssignments))
                    } else {
                        result.addProperty("status", "error")
                        result.addProperty("message", "No se pudieron obtener las tareas de Classroom")
                    }
                }
                
                "create_calendar_event" -> {
                    val title = functionCall.args?.get("title")?.asString ?: "Evento sin titulo"
                    val description = functionCall.args?.get("description")?.asString ?: ""
                    val startTimeStr = functionCall.args?.get("start_time")?.asString ?: ""
                    val endTimeStr = functionCall.args?.get("end_time")?.asString ?: ""
                    val colorId = functionCall.args?.get("color_id")?.asString ?: "1"
                    
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    val startTime = dateFormat.parse(startTimeStr)
                    val endTime = dateFormat.parse(endTimeStr)
                    
                    if (startTime != null && endTime != null) {                        val createResult = calendarRepo.createEvent(
                            title = title, 
                            description = description, 
                            startTime = startTime.time, 
                            endTime = endTime.time,
                            colorId = colorId
                        )
                        
                        if (createResult.isSuccess) {
                            result.addProperty("status", "success")
                            result.addProperty("message", "Evento '$title' creado exitosamente")
                        } else {
                            result.addProperty("status", "error")
                            result.addProperty("message", "No se pudo crear el evento")
                        }
                    } else {
                        result.addProperty("status", "error")
                        result.addProperty("message", "Formato de fecha invalido")
                    }
                }
                
                else -> {
                    result.addProperty("status", "error")
                    result.addProperty("message", "Funcion desconocida")
                }
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error executing function: " + e.message, e)
            JsonObject().apply {
                addProperty("status", "error")
                addProperty("message", "Error: " + e.message)
            }
        }
    }
    
    fun getAvailableModels(): List<GeminiModelInfo> {
        return listOf(
            GeminiModelInfo(MODEL_GEMINI_2_5_FLASH, "Gemini 2.5 Flash", "Modelo mas reciente"),
            GeminiModelInfo(MODEL_GEMINI_2_5_FLASH_LITE, "Gemini 2.5 Flash Lite", "Optimizado para velocidad"),
            GeminiModelInfo(MODEL_GEMINI_2_0_FLASH, "Gemini 2.0 Flash", "Nueva generacion"),
            GeminiModelInfo(MODEL_GEMINI_2_0_FLASH_LITE, "Gemini 2.0 Flash Lite", "Baja latencia"),
            GeminiModelInfo(MODEL_GEMINI_1_5_FLASH, "Gemini 1.5 Flash", "Versatil y rapido"),
            GeminiModelInfo(MODEL_GEMINI_1_5_PRO, "Gemini 1.5 Pro", "Razonamiento complejo")
        )
    }
    
    /**
     * 🆕 Envía un mensaje con imagen adjunta (multimodalidad)
     */
    fun sendMessageWithImage(prompt: String, base64Image: String, mimeType: String): Flow<String> = flow {
        sendMessageWithMultipleFiles(prompt, listOf(Pair(base64Image, mimeType))).collect { emit(it) }
    }
    
    /**
     * 🆕 Envía un mensaje con MÚLTIPLES archivos adjuntos (imágenes, PDFs, documentos)
     */
    fun sendMessageWithMultipleFiles(prompt: String, files: List<Pair<String, String>>): Flow<String> = flow {
        try {
            val apiKey = getEffectiveApiKey()
            
            // Crear parts: primero el texto, luego todos los archivos
            val parts = mutableListOf<GeminiPart>(GeminiPart(text = prompt))
            files.forEach { (base64Data, mimeType) ->
                parts.add(GeminiPart(inlineData = GeminiInlineData(mimeType = mimeType, data = base64Data)))
            }
            
            // Agregar mensaje del usuario con texto y archivos
            conversationHistory.add(
                GeminiMessage(role = "user", parts = parts)
            )
            
            Log.d(TAG, "� Enviando ${files.size} archivo(s) con prompt: $prompt")
            
            val requestBody = GeminiRequest(
                systemInstruction = systemInstruction?.let { GeminiContent(parts = listOf(GeminiPart(text = it))) },
                contents = conversationHistory.map { msg ->
                    GeminiContent(role = msg.role, parts = msg.parts)
                },
                tools = null, // Sin function calling para archivos
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.7,
                    topK = 64,
                    topP = 0.95,
                    maxOutputTokens = 8192
                ),
                safetySettings = listOf(
                    GeminiSafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_MEDIUM_AND_ABOVE"),
                    GeminiSafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_MEDIUM_AND_ABOVE"),
                    GeminiSafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_MEDIUM_AND_ABOVE"),
                    GeminiSafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_MEDIUM_AND_ABOVE")
                )
            )
            
            val jsonBody = gson.toJson(requestBody)
            val mediaType = "application/json; charset=utf-8".toMediaType()
            
            val request = Request.Builder()
                .url(BASE_URL + "/" + selectedModelName + ":generateContent?key=" + apiKey)
                .post(jsonBody.toRequestBody(mediaType))
                .build()
            
            val response = withContext(Dispatchers.IO) {
                httpClient.newCall(request).execute()
            }
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Error desconocido"
                Log.e(TAG, "HTTP " + response.code + ": " + errorBody)
                emit("❌ Error al conectar con Gemini: ${response.code}")
                return@flow
            }
            
            val responseBody = response.body?.string() ?: "{}"
            val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
            
            val candidate = geminiResponse.candidates?.firstOrNull()
            if (candidate == null) {
                emit("❌ Sin respuesta del modelo")
                return@flow
            }
            
            val fullText = candidate.content?.parts?.joinToString("") { it.text ?: "" } ?: ""
            
            if (fullText.isNotEmpty()) {
                conversationHistory.add(
                    GeminiMessage(role = "model", parts = listOf(GeminiPart(text = fullText)))
                )
                
                Log.d(TAG, "✅ Respuesta recibida: ${fullText.take(100)}...")
                
                // Streaming de palabras
                val words = fullText.split(" ")
                for (word in words) {
                    emit(word + " ")
                    delay(30)
                }
            } else {
                emit("❌ Respuesta vacía del modelo")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error en sendMessageWithMultipleFiles: " + e.message, e)
            emit("❌ Error: ${e.message}")
        }
    }
    
    fun switchModel(modelName: String): Boolean {
        if (modelName == selectedModelName) return true
        Log.i(TAG, "Cambiando a " + modelName)
        return initialize(modelName)
    }
    
    fun getCurrentModelInfo(): GeminiModelInfo? {
        return getAvailableModels().find { it.id == selectedModelName }
    }
    
    fun isReady(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()
    
    fun resetChatSession() {
        conversationHistory.clear()
        Log.i(TAG, "Historial borrado")
    }
    
    fun addSystemPrompt(prompt: String) {
        systemInstruction = prompt
    }
    
    fun isApiKeyConfigured(): Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()
}

data class GeminiRequest(
    @SerializedName("systemInstruction") val systemInstruction: GeminiContent?,
    @SerializedName("contents") val contents: List<GeminiContent>,
    @SerializedName("tools") val tools: List<GeminiTool>? = null,
    @SerializedName("generationConfig") val generationConfig: GeminiGenerationConfig,
    @SerializedName("safetySettings") val safetySettings: List<GeminiSafetySetting>
)

data class GeminiContent(
    @SerializedName("role") val role: String? = null,
    @SerializedName("parts") val parts: List<GeminiPart>
)

data class GeminiPart(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: GeminiInlineData? = null,
    @SerializedName("functionCall") val functionCall: GeminiFunctionCall? = null,
    @SerializedName("functionResponse") val functionResponse: GeminiFunctionResponse? = null
)

data class GeminiInlineData(
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("data") val data: String // Base64-encoded image
)

data class GeminiFunctionCall(
    @SerializedName("name") val name: String,
    @SerializedName("args") val args: JsonObject? = null
)

data class GeminiFunctionResponse(
    @SerializedName("name") val name: String,
    @SerializedName("response") val response: JsonObject
)

data class GeminiTool(
    @SerializedName("functionDeclarations") val functionDeclarations: List<GeminiFunctionDeclaration>
)

data class GeminiFunctionDeclaration(
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("parameters") val parameters: GeminiParameters
)

data class GeminiParameters(
    @SerializedName("type") val type: String,
    @SerializedName("properties") val properties: Map<String, GeminiProperty>,
    @SerializedName("required") val required: List<String>? = null
)

data class GeminiProperty(
    @SerializedName("type") val type: String,
    @SerializedName("description") val description: String
)

data class GeminiGenerationConfig(
    @SerializedName("temperature") val temperature: Double,
    @SerializedName("topK") val topK: Int,
    @SerializedName("topP") val topP: Double,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int
)

data class GeminiSafetySetting(
    @SerializedName("category") val category: String,
    @SerializedName("threshold") val threshold: String
)

data class GeminiResponse(
    @SerializedName("candidates") val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    @SerializedName("content") val content: GeminiContent?
)

data class GeminiMessage(
    val role: String,
    val parts: List<GeminiPart>
)

data class GeminiModelInfo(
    val id: String,
    val name: String,
    val description: String
)