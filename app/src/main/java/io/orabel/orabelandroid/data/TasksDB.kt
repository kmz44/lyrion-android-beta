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

import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single

// @Single - Comentado porque no usamos tareas en Orabel IA
class TasksDB {
    private val tasksBox = ObjectBoxStore.store.boxFor(Task::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTasks(): Flow<List<Task>> =
        tasksBox
            .query()
            .build()
            .flow()
            .flowOn(Dispatchers.IO)

    fun addTask(
        name: String,
        systemPrompt: String,
        modelId: Long,
    ) {
        tasksBox.put(Task(name = name, systemPrompt = systemPrompt, modelId = modelId))
    }

    fun deleteTask(taskId: Long) {
        tasksBox.remove(taskId)
    }

    fun updateTask(task: Task) {
        tasksBox.put(task)
    }
}
