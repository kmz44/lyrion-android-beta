/*
 *
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */

package io.orabel.orabelandroid.ui.screens.chat

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModel
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.data.Chat
import io.orabel.orabelandroid.data.ChatMessage
import io.orabel.orabelandroid.data.ChatsDB
import io.orabel.orabelandroid.data.MessagesDB
import io.orabel.orabelandroid.data.HealthDiaryRepository
import io.orabel.orabelandroid.llm.ModelsRepository
import io.orabel.orabelandroid.llm.LLMManager
import io.orabel.orabelandroid.prism4j.OrabelPrismGrammarLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import java.util.Date

const val LOGTAG = "[OrabelAndroid]"
val LOGD: (String) -> Unit = { Log.d(LOGTAG, it) }

@KoinViewModel
class ChatScreenViewModel(
    val context: Context,
    val messagesDB: MessagesDB,
    val chatsDB: ChatsDB,
    val modelsRepository: ModelsRepository,
    private val healthDiaryRepository: HealthDiaryRepository
) : ViewModel() {
    val llmManager = LLMManager(context)

    val currChatState = mutableStateOf<Chat?>(null)

    val isGeneratingResponse = mutableStateOf(false)
    val partialResponse = mutableStateOf("")

    val showSelectModelListDialogState = mutableStateOf(false)
    val showMoreOptionsPopupState = mutableStateOf(false)

    val isInitializingModel = mutableStateOf(false)
    var responseGenerationJob: Job? = null

    // 🆕 Estado para MÚLTIPLES archivos adjuntos (imágenes, PDFs, documentos)
    data class AttachedFile(
        val uri: android.net.Uri,
        val base64: String,
        val mimeType: String,
        val fileName: String
    )
    val attachedFiles = mutableStateOf<List<AttachedFile>>(emptyList())

    val markwon: Markwon

    init {
        currChatState.value = chatsDB.loadDefaultChat(context)
        
        val prism4j = Prism4j(OrabelPrismGrammarLocator())
        markwon =
            Markwon
                .builder(context)
                .usePlugin(CorePlugin.create())
                .usePlugin(SyntaxHighlightPlugin.create(prism4j, Prism4jThemeDarkula.create()))
                .usePlugin(
                    object : AbstractMarkwonPlugin() {
                        override fun configureTheme(builder: MarkwonTheme.Builder) {
                            val jetbrainsMonoFont =
                                ResourcesCompat.getFont(context, R.font.jetbrains_mono)!!
                            builder
                                .codeBlockTypeface(jetbrainsMonoFont)
                                .codeBlockTextColor(Color.WHITE)
                                .codeBlockTextSize(spToPx(12f)) // Aumentado para mejor legibilidad
                                .codeBlockBackgroundColor(Color.parseColor("#1E1E1E"))
                                .codeBlockMargin(spToPx(8f)) // Más margen para fórmulas
                                .codeTypeface(jetbrainsMonoFont)
                                .codeTextSize(spToPx(12f)) // Aumentado para fórmulas inline
                                .codeTextColor(Color.WHITE)
                                .codeBackgroundColor(Color.parseColor("#2D2D2D"))
                                .linkColor(Color.parseColor("#6366F1")) // Color Orabel
                                .headingTextSizeMultipliers(floatArrayOf(1.5f, 1.3f, 1.2f, 1.1f, 1.05f, 1f))
                                .blockMargin(spToPx(12f)) // Más espacio entre bloques
                                .listItemColor(Color.BLACK)
                        }
                    },
                ).build()
    }

    private fun spToPx(sp: Float): Int =
        TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
            .toInt()

    fun getChats(): Flow<List<Chat>> = chatsDB.getChats()

    fun getChatMessages(): Flow<List<ChatMessage>>? {
        return currChatState.value?.let { chat ->
            return messagesDB.getMessages(chat.id)
        }
    }

    fun updateChatLLM(modelId: Long) {
        currChatState.value = currChatState.value?.copy(llmModelId = modelId)
        chatsDB.updateChat(currChatState.value!!)
    }

    fun updateChat(chat: Chat) {
        currChatState.value = chat
        chatsDB.updateChat(chat)
        loadModel()
    }

    fun sendUserQuery(query: String) {
        currChatState.value?.let { chat ->
            chat.dateUsed = Date()
            chatsDB.updateChat(chat)
            
            // 🆕 Verificar si hay archivos adjuntos
            val hasAttachedFiles = attachedFiles.value.isNotEmpty()
            
            if (hasAttachedFiles) {
                // Enviar mensaje con archivos adjuntos
                val files = attachedFiles.value
                val fileDescriptions = files.map { file ->
                    when {
                        file.mimeType.startsWith("image/") -> "📷 ${file.fileName}"
                        file.mimeType.startsWith("application/pdf") -> "📄 ${file.fileName}"
                        file.mimeType.contains("word") -> "📝 ${file.fileName}"
                        else -> "📎 ${file.fileName}"
                    }
                }.joinToString(", ")
                
                messagesDB.addUserMessage(chat.id, "$fileDescriptions\n$query")
                
                isGeneratingResponse.value = true
                partialResponse.value = ""
                
                responseGenerationJob = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Enviar con archivos a Gemini Vision
                        llmManager.geminiClient?.sendMessageWithMultipleFiles(
                            prompt = query,
                            files = files.map { Pair(it.base64, it.mimeType) }
                        )?.collect { token ->
                            withContext(Dispatchers.Main) {
                                partialResponse.value += token
                            }
                        }
                        
                        val fullResponse = partialResponse.value
                        if (fullResponse.isNotEmpty()) {
                            messagesDB.addAssistantMessage(chat.id, fullResponse)
                        }
                        
                        withContext(Dispatchers.Main) {
                            isGeneratingResponse.value = false
                            partialResponse.value = ""
                            clearAttachedImage() // Limpiar archivos después de enviar
                        }
                    } catch (e: Exception) {
                        LOGD("❌ Error al enviar mensaje con archivos: ${e.message}")
                        withContext(Dispatchers.Main) {
                            isGeneratingResponse.value = false
                            partialResponse.value = ""
                        }
                    }
                }
            } else {
                // Enviar mensaje normal (sin archivos)
                messagesDB.addUserMessage(chat.id, query)
                
                isGeneratingResponse.value = true
                responseGenerationJob =
                    CoroutineScope(Dispatchers.Default).launch {
                        // PRIMERO: Analizar el mensaje para el diario de salud
                        try {
                            Log.e(LOGTAG, "=== INICIO ANÁLISIS DIARIO DE SALUD ===")
                            Log.e(LOGTAG, "🩺 MENSAJE A ANALIZAR: '$query'")
                            Log.e(LOGTAG, "🩺 CHAT ID: ${chat.id}")
                            val wasRelevant = healthDiaryRepository.analyzeAndSaveIfRelevant(query, chat.id)
                            Log.e(LOGTAG, "🩺 ¿SE GUARDÓ EN DIARIO?: $wasRelevant")
                            Log.e(LOGTAG, "=== FIN ANÁLISIS DIARIO DE SALUD ===")
                        } catch (e: Exception) {
                            Log.e(LOGTAG, "🩺 ERROR EN ANÁLISIS DE SALUD: ${e.message}", e)
                            e.printStackTrace()
                        }
                        
                        // SEGUNDO: Generar la respuesta del LLM
                        partialResponse.value = ""
                        try {
                            llmManager.getResponse(query).collect { partialResponse.value += it }
                            // Completar la generación normalmente
                            llmManager.stopCompletion()
                        } catch (e: Exception) {
                            LOGD("Response generation was cancelled or failed: ${e.message}")
                            // Incluso si hay error o cancelación, intentar limpiar el estado
                            try {
                                llmManager.cancelCompletion()
                            } catch (cleanupError: Exception) {
                                LOGD("Error during cleanup: ${cleanupError.message}")
                            }
                        } finally {
                            // SIEMPRE guardar la respuesta, incluso si es parcial
                            if (partialResponse.value.isNotEmpty()) {
                                messagesDB.addAssistantMessage(chat.id, partialResponse.value)
                            }
                            withContext(Dispatchers.Main) { 
                                isGeneratingResponse.value = false 
                            }
                        }
                    }
            }
        }
    }

    fun stopGeneration() {
        isGeneratingResponse.value = false
        responseGenerationJob?.let { job ->
            if (job.isActive) {
                job.cancel()
                // La respuesta parcial se guardará en el bloque finally de sendUserQuery
                // Solo necesitamos limpiar el estado sin afectar el guardado
                try {
                    llmManager.cancelCompletion()
                } catch (e: Exception) {
                    LOGD("Error cancelling completion: ${e.message}")
                }
            }
        }
    }

    fun switchChat(chat: Chat) {
        stopGeneration()
        currChatState.value = chat
    }

    fun deleteChat(chat: Chat) {
        stopGeneration()
        chatsDB.deleteChat(chat)
        messagesDB.deleteMessages(chat.id)
        currChatState.value = null
    }

    fun deleteModel(modelId: Long) {
        modelsRepository.deleteModel(modelId)
        if (currChatState.value?.llmModelId == modelId) {
            currChatState.value = currChatState.value?.copy(llmModelId = -1)
            llmManager.close()
        }
    }

    /**
     * Load the model for the current chat. If chat is configured with a LLM (i.e. chat.llModelId !=
     * -1), then load the model. If not, show the model list dialog. Once the model is finalized,
     * read the system prompt and user messages from the database and add them to the model.
     */
    fun loadModel() {
        currChatState.value?.let { chat ->
            if (chat.llmModelId == -1L) {
                showSelectModelListDialogState.value = true
            } else {
                val model = modelsRepository.getModelFromId(chat.llmModelId)
                if (model != null) {
                    isInitializingModel.value = true
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            // Verificar si es un modelo online de Gemini PRIMERO
                            val isGeminiModel = modelsRepository.isGeminiOnlineModel(model.id)
                            
                            // Solo verificar archivo para modelos locales
                            if (!isGeminiModel) {
                                val modelFile = java.io.File(model.path)
                                if (!modelFile.exists()) {
                                    LOGD("Model file does not exist: ${model.path}")
                                    withContext(Dispatchers.Main) { 
                                        isInitializingModel.value = false
                                        showSelectModelListDialogState.value = true
                                    }
                                    return@launch
                                }
                            }
                            
                            LOGD("Loading model from: ${if (isGeminiModel) "Gemini Online: ${model.url}" else model.path}")
                            
                            val success = if (isGeminiModel) {
                                llmManager.loadModel(
                                    modelId = model.id,
                                    modelPath = "",
                                    modelUrl = model.url,
                                    minP = chat.minP,
                                    temperature = chat.temperature,
                                    isGeminiOnlineModel = true
                                )
                            } else {
                                llmManager.loadModel(
                                    modelId = model.id,
                                    modelPath = model.path,
                                    modelUrl = "",
                                    minP = chat.minP,
                                    temperature = chat.temperature,
                                    isGeminiOnlineModel = false
                                )
                            }
                            
                            if (!success) {
                                withContext(Dispatchers.Main) { 
                                    isInitializingModel.value = false
                                    showSelectModelListDialogState.value = true
                                }
                                return@launch
                            }
                            
                            LOGD("Model loaded successfully")
                            
                            if (chat.systemPrompt.isNotEmpty()) {
                                llmManager.addSystemPrompt(chat.systemPrompt)
                                LOGD("System prompt added")
                            }
                            
                            // Para modelos locales, cargar historial de mensajes
                            if (!isGeminiModel) {
                                messagesDB.getMessagesForModel(chat.id).forEach { message ->
                                    if (message.isUserMessage) {
                                        llmManager.addUserMessage(message.message)
                                        LOGD("User message added: ${message.message}")
                                    } else {
                                        llmManager.addAssistantMessage(message.message)
                                        LOGD("Assistant message added: ${message.message}")
                                    }
                                }
                            }
                            
                            withContext(Dispatchers.Main) { isInitializingModel.value = false }
                        } catch (e: Exception) {
                            LOGD("Error loading model: ${e.message}")
                            e.printStackTrace()
                            withContext(Dispatchers.Main) { 
                                isInitializingModel.value = false
                                showSelectModelListDialogState.value = true
                            }
                        }
                    }
                } else {
                    showSelectModelListDialogState.value = true
                }
            }
        }
    }

    /**
     * Show the model selection dialog
     */
    fun showModelSelectionDialog() {
        showSelectModelListDialogState.value = true
    }
    
    /**
     * 🆕 Adjunta archivos (imágenes, PDFs, documentos)
     * Soporta múltiples archivos y varios formatos
     */
    fun attachImage(fileUri: android.net.Uri, activityContext: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LOGD("� Procesando archivo: $fileUri")
                
                // Leer archivo y convertir a Base64
                val inputStream = activityContext.contentResolver.openInputStream(fileUri)
                val fileBytes = inputStream?.readBytes()
                inputStream?.close()
                
                if (fileBytes == null) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            activityContext,
                            "❌ Error al leer el archivo",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                
                // Obtener tipo MIME
                val mimeType = activityContext.contentResolver.getType(fileUri) ?: "application/octet-stream"
                
                // Validar tipos soportados por Gemini
                val supportedTypes = listOf(
                    "image/png", "image/jpeg", "image/webp", "image/heic", "image/heif",
                    "application/pdf",
                    "text/plain", "text/html", "text/css", "text/javascript", "text/typescript", "text/csv",
                    "application/rtf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation" // .pptx
                )
                
                if (!supportedTypes.any { mimeType.startsWith(it) || mimeType.contains(it) }) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            activityContext,
                            "⚠️ Tipo de archivo no soportado: $mimeType\nSoportados: imágenes, PDF, texto, Word, Excel, PowerPoint",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                
                // Convertir a Base64
                val base64File = android.util.Base64.encodeToString(fileBytes, android.util.Base64.NO_WRAP)
                
                // Obtener nombre del archivo
                val fileName = fileUri.lastPathSegment ?: "archivo_adjunto"
                
                LOGD("📎 Archivo adjuntado: $fileName. Tamaño: ${fileBytes.size} bytes, MIME: $mimeType")
                
                // Añadir a la lista de archivos adjuntos
                withContext(Dispatchers.Main) {
                    val newFile = AttachedFile(
                        uri = fileUri,
                        base64 = base64File,
                        mimeType = mimeType,
                        fileName = fileName
                    )
                    attachedFiles.value = attachedFiles.value + newFile
                    
                    val fileType = when {
                        mimeType.startsWith("image/") -> "📷 Imagen"
                        mimeType.startsWith("application/pdf") -> "📄 PDF"
                        mimeType.contains("word") -> "📝 Word"
                        mimeType.contains("sheet") -> "📊 Excel"
                        mimeType.contains("presentation") -> "📽️ PowerPoint"
                        else -> "📎 Archivo"
                    }
                    
                    android.widget.Toast.makeText(
                        activityContext,
                        "$fileType adjuntado (${attachedFiles.value.size} total). Escribe y presiona enviar.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                LOGD("❌ Error al procesar archivo: ${e.message}")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        activityContext,
                        "❌ Error: ${e.message}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * 🆕 Elimina un archivo adjunto específico
     */
    fun removeAttachedFile(file: AttachedFile) {
        attachedFiles.value = attachedFiles.value.filter { it != file }
    }
    
    /**
     * 🆕 Limpia TODOS los archivos adjuntos
     */
    fun clearAttachedImage() {
        attachedFiles.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        llmManager.close()
    }
}
