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

    // 🆕 Estado para imagen adjunta (NO se envía automáticamente)
    val attachedImageUri = mutableStateOf<android.net.Uri?>(null)
    val attachedImageBase64 = mutableStateOf<String?>(null)
    val attachedImageMimeType = mutableStateOf<String?>(null)

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
     * 🆕 Envía una imagen al modelo Gemini con visión REAL
     */
    fun sendImageToModel(imageUri: android.net.Uri, activityContext: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LOGD("🖼️ Procesando imagen: $imageUri")
                
                // Leer imagen y convertir a Base64
                val inputStream = activityContext.contentResolver.openInputStream(imageUri)
                val imageBytes = inputStream?.readBytes()
                inputStream?.close()
                
                if (imageBytes == null) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            activityContext,
                            "❌ Error al leer la imagen",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                
                // Obtener tipo MIME de la imagen
                val mimeType = activityContext.contentResolver.getType(imageUri) ?: "image/jpeg"
                
                // Convertir a Base64
                val base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
                
                LOGD("� Imagen convertida a Base64. Tamaño: ${imageBytes.size} bytes, MIME: $mimeType")
                
                // Crear mensaje multimodal con imagen
                val imageMessage = "Analiza esta imagen que he adjuntado"
                
                // Guardar mensaje en la base de datos con la imagen
                val chat = currChatState.value
                if (chat != null) {
                    messagesDB.addUserMessage(
                        chatId = chat.id,
                        message = "📷 [Imagen adjunta]\n$imageMessage"
                    )
                    
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            activityContext,
                            "📎 Imagen adjuntada. Enviando a Gemini...",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    // Enviar a Gemini con imagen
                    isGeneratingResponse.value = true
                    partialResponse.value = ""
                    
                    responseGenerationJob = CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Usar el GeminiApiClient existente pero con imagen
                            llmManager.geminiClient?.sendMessageWithImage(imageMessage, base64Image, mimeType)?.collect { token ->
                                withContext(Dispatchers.Main) {
                                    partialResponse.value += token
                                }
                            }
                            
                            val fullResponse = partialResponse.value
                            if (fullResponse.isNotEmpty()) {
                                messagesDB.addAssistantMessage(
                                    chatId = chat.id,
                                    message = fullResponse
                                )
                            }
                            
                            withContext(Dispatchers.Main) {
                                isGeneratingResponse.value = false
                                partialResponse.value = ""
                            }
                        } catch (e: Exception) {
                            LOGD("❌ Error al enviar imagen: ${e.message}")
                            withContext(Dispatchers.Main) {
                                isGeneratingResponse.value = false
                                partialResponse.value = ""
                                android.widget.Toast.makeText(
                                    activityContext,
                                    "❌ Error al analizar la imagen: ${e.message}",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            activityContext,
                            "❌ Selecciona un modelo primero",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                LOGD("❌ Error al procesar imagen: ${e.message}")
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

    override fun onCleared() {
        super.onCleared()
        llmManager.close()
    }
}
