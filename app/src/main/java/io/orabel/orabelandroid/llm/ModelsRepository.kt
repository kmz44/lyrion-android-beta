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

package io.orabel.orabelandroid.llm

import android.content.Context
import android.os.Environment
import io.orabel.orabelandroid.data.LLMModel
import io.orabel.orabelandroid.data.ModelsDB
import io.orabel.orabelandroid.gemini.GeminiApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.Single
import java.io.File

@Single
class ModelsRepository(
    private val context: Context,
    private val modelsDB: ModelsDB,
) {
    private val geminiApiClient = GeminiApiClient(context)
    
    // IDs especiales para modelos online de Gemini (valores negativos para distinguirlos)
    companion object {
        const val GEMINI_2_5_FLASH_ID = -1001L
        const val GEMINI_2_5_FLASH_LITE_ID = -1002L
        const val GEMINI_2_0_FLASH_ID = -1003L
        const val GEMINI_2_0_FLASH_LITE_ID = -1004L
        const val GEMINI_1_5_FLASH_ID = -1005L
        const val GEMINI_1_5_PRO_ID = -1006L
        
        fun checkIfModelsDownloaded(context: Context): Boolean {
            // Check in the external files directory
            val modelsDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "models")
            if (modelsDir.exists()) {
                modelsDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.name.endsWith(".gguf")) {
                        return true
                    }
                }
            }
            
            // Also check the old location for backward compatibility
            context.filesDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".gguf")) {
                    return true
                }
            }
            
            return false
        }
    }

    
    init {
        // Limpiar modelos locales que no existen
        for (model in modelsDB.getModelsList()) {
            if (!File(model.path).exists()) {
                deleteModel(model.id)
            }
        }
    }

    /**
     * Obtiene los modelos online de Gemini como LLMModel
     */
    private fun getGeminiOnlineModels(): List<LLMModel> {
        return if (geminiApiClient.isApiKeyConfigured()) {
            listOf(
                LLMModel(
                    id = GEMINI_2_5_FLASH_ID,
                    name = "🌐 Gemini 2.5 Flash (Online)",
                    url = "gemini-2.5-flash",
                    path = "GEMINI_ONLINE_MODEL"
                ),
                LLMModel(
                    id = GEMINI_2_5_FLASH_LITE_ID,
                    name = "🌐 Gemini 2.5 Flash Lite (Online)",
                    url = "gemini-2.5-flash-lite",
                    path = "GEMINI_ONLINE_MODEL"
                ),
                LLMModel(
                    id = GEMINI_2_0_FLASH_ID,
                    name = "🌐 Gemini 2.0 Flash (Online)",
                    url = "gemini-2.0-flash",
                    path = "GEMINI_ONLINE_MODEL"
                ),
                LLMModel(
                    id = GEMINI_2_0_FLASH_LITE_ID,
                    name = "🌐 Gemini 2.0 Flash Lite (Online)",
                    url = "gemini-2.0-flash-lite",
                    path = "GEMINI_ONLINE_MODEL"
                ),
                LLMModel(
                    id = GEMINI_1_5_FLASH_ID,
                    name = "🌐 Gemini 1.5 Flash (Online)",
                    url = "gemini-1.5-flash",
                    path = "GEMINI_ONLINE_MODEL"
                ),
                LLMModel(
                    id = GEMINI_1_5_PRO_ID,
                    name = "🌐 Gemini 1.5 Pro (Online)",
                    url = "gemini-1.5-pro",
                    path = "GEMINI_ONLINE_MODEL"
                )
            )
        } else {
            emptyList()
        }
    }

    fun getModelFromId(id: Long): LLMModel? {
        // Primero verificar si es un modelo online de Gemini
        return when (id) {
            GEMINI_2_5_FLASH_ID -> getGeminiOnlineModels().find { it.id == id }
            GEMINI_2_5_FLASH_LITE_ID -> getGeminiOnlineModels().find { it.id == id }
            GEMINI_2_0_FLASH_ID -> getGeminiOnlineModels().find { it.id == id }
            GEMINI_2_0_FLASH_LITE_ID -> getGeminiOnlineModels().find { it.id == id }
            GEMINI_1_5_FLASH_ID -> getGeminiOnlineModels().find { it.id == id }
            GEMINI_1_5_PRO_ID -> getGeminiOnlineModels().find { it.id == id }
            else -> modelsDB.getModel(id) // Modelo local tradicional
        }
    }

    fun getAvailableModels(): Flow<List<LLMModel>> {
        val localModels = modelsDB.getModels()
        val geminiModels = flowOf(getGeminiOnlineModels())
        
        return combine(localModels, geminiModels) { local, gemini ->
            // Primero los modelos online de Gemini, luego los locales
            gemini + local
        }
    }

    fun getAvailableModelsList(): List<LLMModel> {
        return getGeminiOnlineModels() + modelsDB.getModelsList()
    }
    
    /**
     * Verifica si un modelo es online de Gemini
     */
    fun isGeminiOnlineModel(id: Long): Boolean {
        return id < 0 // Todos los modelos online tienen IDs negativos
    }
    
    /**
     * Obtiene el GeminiApiClient para uso directo
     */
    fun getGeminiApiClient(): GeminiApiClient {
        return geminiApiClient
    }

    fun deleteModel(id: Long) {
        // No permitir eliminar modelos online de Gemini
        if (isGeminiOnlineModel(id)) {
            return
        }
        
        modelsDB.getModel(id)?.let {
            File(it.path).delete()
            modelsDB.deleteModel(it.id)
        }
    }
}
