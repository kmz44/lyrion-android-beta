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

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import java.util.Date

@Entity
data class Chat(
    @Id var id: Long = 0,
    var name: String = "",
    var systemPrompt: String = "",
    var dateCreated: Date = Date(),
    var dateUsed: Date = Date(),
    var llmModelId: Long = -1L,
    var minP: Float = 0.05f,
    var temperature: Float = 1.0f,
    var isTask: Boolean = false,
)

@Entity
data class ChatMessage(
    @Id var id: Long = 0,
    var chatId: Long = 0,
    var message: String = "",
    var isUserMessage: Boolean = false,
)

@Entity
data class LLMModel(
    @Id var id: Long = 0,
    var name: String = "",
    var url: String = "",
    var path: String = "",
)

@Entity
data class Task(
    @Id var id: Long = 0,
    var name: String = "",
    var systemPrompt: String = "",
    var modelId: Long = -1,
    @Transient var modelName: String = "",
)
