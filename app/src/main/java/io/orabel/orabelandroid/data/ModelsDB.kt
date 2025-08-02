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

package io.orabel.orabelandroid.data

import android.os.Environment
import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import android.content.Context
import io.orabel.orabelandroid.utils.AsyncFileOperations

@OptIn(ExperimentalCoroutinesApi::class)
@Single
class ModelsDB(private val context: Context) {
    private val modelsBox = ObjectBoxStore.store.boxFor(LLMModel::class.java)
    
    /**
     * Creates a file path suitable for storing model files in external storage (async version)
     * @param fileName Name of the model file
     * @return The absolute path where the model should be stored
     */
    suspend fun getModelStoragePathAsync(fileName: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val modelsDir = AsyncFileOperations.getModelsDirectory(context)
                File(modelsDir, fileName).absolutePath
            } catch (e: Exception) {
                // Fallback to internal storage
                val modelsDir = File(context.filesDir, "models")
                AsyncFileOperations.createDirectories(modelsDir)
                File(modelsDir, fileName).absolutePath
            }
        }
    }
    
    /**
     * Creates a file path suitable for storing model files in external storage (synchronous for compatibility)
     * @param fileName Name of the model file
     * @return The absolute path where the model should be stored
     */
    fun getModelStoragePath(fileName: String): String {
        // Primero intentamos usar almacenamiento externo
        val externalDir = context.getExternalFilesDir(null)
        
        // Creamos el directorio de modelos dentro del directorio específico de la app
        val modelsDir = if (externalDir != null && externalDir.exists()) {
            File(externalDir, "models").also { if (!it.exists()) it.mkdirs() }
        } else {
            // Como respaldo, usar el almacenamiento interno si el externo no está disponible
            File(context.filesDir, "models").also { if (!it.exists()) it.mkdirs() }
        }
        
        return File(modelsDir, fileName).absolutePath
    }

    /**
     * Verifica si hay suficiente espacio disponible para descargar un modelo
     * @param sizeInMB Tamaño del modelo en megabytes
     * @return true si hay suficiente espacio, false en caso contrario
     */
    fun hasEnoughStorageSpace(sizeInMB: Long): Boolean {
        val externalDir = context.getExternalFilesDir(null) ?: context.filesDir
        val freeSpace = externalDir.freeSpace / (1024 * 1024) // Convertir a MB
        return freeSpace > sizeInMB + 100 // Añadir 100MB como margen de seguridad
    }

    fun addModel(
        name: String,
        url: String,
        path: String,
    ): Long {
        val model = LLMModel(name = name, url = url, path = path)
        return modelsBox.put(model)
    }

    fun getModel(id: Long): LLMModel? = modelsBox.get(id)

    fun getModels(): Flow<List<LLMModel>> =
        modelsBox
            .query()
            .build()
            .flow()
            .flowOn(Dispatchers.IO)

    fun getModelsList(): List<LLMModel> = modelsBox.all

    /**
     * Devuelve una lista de modelos recomendados para dispositivos Android.
     * Incluye información detallada sobre licencias y términos de uso.
     * @return Lista de modelos con sus URLs, descripciones y términos de uso
     */
    fun getLightweightModels(): List<LightweightModelInfo> {
        return listOf(
            LightweightModelInfo(
                name = "Llama-3.2-1B-Instruct-IQ4_NL",
                url = "https://huggingface.co/unsloth/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-IQ4_NL.gguf",
                description = "Llama 3.2 1B sirve para mayor rapidez en español. La descarga es de aproximadamente 3-5 minutos y es ideal para dispositivos de baja potencia con excelente rendimiento en conversaciones.",
                sizeInMB = 800L,
                licenseInfo = "Licencia Llama 3.2 Community License",
                termsOfUse = "Al descargar este modelo, acepta los términos de la Licencia Comunitaria de Llama 3.2 de Meta. Este modelo puede ser usado para fines comerciales y no comerciales, pero está sujeto a restricciones específicas. Para uso comercial a gran escala (más de 700 millones de usuarios activos mensuales), se requiere una licencia personalizada de Meta. El modelo no debe ser usado para actividades ilegales, generar contenido dañino o violar derechos de terceros.\n\nAtribuciones:\n• Modelo proporcionado por Meta AI bajo Licencia Comunitaria Llama 3.2\n• Distribución facilitada por Hugging Face (huggingface.co)\n• Agradecimientos especiales a Hugging Face por permitirnos utilizar sus enlaces de descarga y servicios de hosting de modelos\n• Formato GGUF optimizado por la comunidad de código abierto"
            ),
            LightweightModelInfo(
                name = "Phi-3-mini-4k-instruct-q4",
                url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
                description = "Phi-3 Mini sirve para respuestas precisas y rápidas en español. La descarga es de aproximadamente 2-3 minutos y es perfecto para dispositivos con recursos limitados ofreciendo alta calidad en tareas de razonamiento.",
                sizeInMB = 600L,
                licenseInfo = "Licencia MIT",
                termsOfUse = "Al descargar este modelo, acepta los términos de la Licencia MIT de Microsoft. Este modelo puede ser usado libremente para fines comerciales y no comerciales. Se permite la modificación, distribución y uso privado. Microsoft no proporciona garantías sobre el modelo y no se hace responsable de daños resultantes de su uso. Se requiere incluir el aviso de copyright original en cualquier redistribución.\n\nAtribuciones:\n• Modelo desarrollado por Microsoft bajo Licencia MIT\n• Distribución facilitada por Hugging Face (huggingface.co)\n• Agradecimientos especiales a Hugging Face por permitirnos utilizar sus enlaces de descarga y servicios de hosting de modelos\n• Formato GGUF optimizado para mejor rendimiento en dispositivos móviles"
            )
        )
    }

    /**
     * Añade un modelo ligero a la base de datos y devuelve su ID
     * @param modelInfo La información del modelo ligero a añadir
     * @return ID del modelo añadido, o -1 si no hay suficiente espacio
     */
    fun addLightweightModel(modelInfo: LightweightModelInfo): Long {
        // Verificar si hay suficiente espacio antes de añadir el modelo
        if (!hasEnoughStorageSpace(modelInfo.sizeInMB)) {
            return -1L // Indicar que no se pudo añadir por falta de espacio
        }
        
        val path = getModelStoragePath(modelInfo.name + ".gguf")
        return addModel(modelInfo.name, modelInfo.url, path)
    }

    fun deleteModel(id: Long) {
        modelsBox.get(id)?.let { model ->
            File(model.path).delete()
            modelsBox.remove(model)
        }
    }
}
