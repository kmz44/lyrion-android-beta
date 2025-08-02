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
import io.orabel.orabel.Orabel
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.data.Chat
import io.orabel.orabelandroid.data.ChatMessage
import io.orabel.orabelandroid.data.ChatsDB
import io.orabel.orabelandroid.data.MessagesDB
import io.orabel.orabelandroid.llm.ModelsRepository
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
) : ViewModel() {
    val Orabel = Orabel()

    val currChatState = mutableStateOf<Chat?>(null)

    val isGeneratingResponse = mutableStateOf(false)
    val partialResponse = mutableStateOf("")

    val showSelectModelListDialogState = mutableStateOf(false)
    val showMoreOptionsPopupState = mutableStateOf(false)

    val isInitializingModel = mutableStateOf(false)
    var responseGenerationJob: Job? = null

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
                    partialResponse.value = ""
                    try {
                        Orabel.getResponse(query).collect { partialResponse.value += it }
                        // Completar la generación normalmente
                        Orabel.stopCompletion()
                    } catch (e: Exception) {
                        LOGD("Response generation was cancelled or failed: ${e.message}")
                        // Incluso si hay error o cancelación, intentar limpiar el estado C++
                        try {
                            Orabel.cancelCompletion()
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
                // Solo necesitamos limpiar el estado C++ sin afectar el guardado
                try {
                    Orabel.cancelCompletion()
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
            Orabel.close()
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
                            // Verificar que el archivo del modelo existe
                            val modelFile = java.io.File(model.path)
                            if (!modelFile.exists()) {
                                LOGD("Model file does not exist: ${model.path}")
                                withContext(Dispatchers.Main) { 
                                    isInitializingModel.value = false
                                    showSelectModelListDialogState.value = true
                                }
                                return@launch
                            }
                            
                            LOGD("Loading model from: ${model.path}")
                            Orabel.create(model.path, chat.minP, chat.temperature, true)
                            LOGD("Model loaded successfully")
                            
                            if (chat.systemPrompt.isNotEmpty()) {
                                Orabel.addSystemPrompt(chat.systemPrompt)
                                LOGD("System prompt added")
                            }
                            
                            messagesDB.getMessagesForModel(chat.id).forEach { message ->
                                if (message.isUserMessage) {
                                    Orabel.addUserMessage(message.message)
                                    LOGD("User message added: ${message.message}")
                                } else {
                                    Orabel.addAssistantMessage(message.message)
                                    LOGD("Assistant message added: ${message.message}")
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

    override fun onCleared() {
        super.onCleared()
        Orabel.close()
    }
}
