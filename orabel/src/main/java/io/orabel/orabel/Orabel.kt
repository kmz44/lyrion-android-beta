package io.orabel.orabel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class Orabel {
    private var nativePtr = 0L

    companion object {
        init {
            System.loadLibrary("orabel")
        }
    }

    suspend fun create(
        modelPath: String,
        minP: Float,
        temperature: Float,
        storeChats: Boolean,
    ) = withContext(Dispatchers.IO) {
        nativePtr = loadModel(modelPath, minP, temperature, storeChats)
    }

    fun addUserMessage(message: String) {
        assert(nativePtr != 0L) { "Model is not loaded. Use Orabel.create to load the model" }
        addChatMessage(nativePtr, message, "user")
    }

    fun addSystemPrompt(prompt: String) {
        assert(nativePtr != 0L) { "Model is not loaded. Use Orabel.create to load the model" }
        addChatMessage(nativePtr, prompt, "system")
    }

    fun addAssistantMessage(message: String) {
        assert(nativePtr != 0L) { "Model is not loaded. Use Orabel.create to load the model" }
        addChatMessage(nativePtr, message, "assistant")
    }

    fun getResponse(query: String): Flow<String> =
        flow {
            assert(nativePtr != 0L) { "Model is not loaded. Use Orabel.create to load the model" }
            startCompletion(nativePtr, query)
            var piece = completionLoop(nativePtr)
            while (piece != "[EOG]") {
                emit(piece)
                piece = completionLoop(nativePtr)
            }
            stopCompletionInternal(nativePtr)
        }
    
    fun close() {
        close(nativePtr)
    }
    
    fun stopCompletion() {
        assert(nativePtr != 0L) { "Model is not loaded. Use Orabel.create to load the model" }
        stopCompletionInternal(nativePtr)
    }
    
    fun cancelCompletion() {
        assert(nativePtr != 0L) { "Model is not loaded. Use Orabel.create to load the model" }
        cancelCompletionInternal(nativePtr)
    }

    private external fun loadModel(
        modelPath: String,
        minP: Float,
        temperature: Float,
        storeChats: Boolean,
    ): Long

    private external fun addChatMessage(
        modelPtr: Long,
        message: String,
        role: String,
    )

    private external fun close(modelPtr: Long)

    private external fun startCompletion(
        modelPtr: Long,
        prompt: String,
    )

    private external fun completionLoop(modelPtr: Long): String

    private external fun stopCompletionInternal(modelPtr: Long)
    
    private external fun cancelCompletionInternal(modelPtr: Long)
}
