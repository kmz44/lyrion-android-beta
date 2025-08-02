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
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.ui.screens.chat.LOGD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single
import java.util.Date

@Single
class ChatsDB {
    private val chatsBox = ObjectBoxStore.store.boxFor(Chat::class.java)

    /** Get all chats from the database sorted by dateUsed in descending order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getChats(): Flow<List<Chat>> =
        chatsBox
            .query()
            .orderDesc(Chat_.dateUsed)
            .build()
            .flow()
            .flowOn(Dispatchers.IO)

    fun loadDefaultChat(context: android.content.Context): Chat {
        val defaultChat =
            if (getChatsCount() == 0L) {
                addChat(context.getString(R.string.untitled))
                getRecentlyUsedChat()!!
            } else {
                // Given that chatsDB has at least one chat
                // chatsDB.getRecentlyUsedChat() will never return null
                getRecentlyUsedChat()!!
            }
        LOGD("Default chat is $defaultChat")
        return defaultChat
    }

    /**
     * Get the most recently used chat from the database. This function might return null, if there
     * are no chats in the database.
     */
    fun getRecentlyUsedChat(): Chat? =
        chatsBox
            .query()
            .orderDesc(Chat_.dateUsed)
            .build()
            .findFirst()

    fun addChat(
        chatName: String,
        systemPrompt: String = "You are a helpful assistant.",
        llmModelId: Long = -1,
    ): Long =
        chatsBox.put(
            Chat(
                name = chatName,
                systemPrompt = systemPrompt,
                dateCreated = Date(),
                dateUsed = Date(),
                llmModelId = llmModelId,
            ),
        )

    /** Update the chat in the database. ObjectBox overwrites the entry if it already exists. */
    fun updateChat(modifiedChat: Chat) {
        chatsBox.put(modifiedChat)
    }

    fun deleteChat(chat: Chat) {
        chatsBox.remove(chat)
    }

    fun getChatsCount(): Long = chatsBox.count()
}
