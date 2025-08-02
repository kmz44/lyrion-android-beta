/*
 *
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express o            painter = painterResource(id = R.drawable.ic_lyrion_logo),
            contentDescription = "Logo",
            modifier = Modifier.size(24.dp)plied.
 * See the License for the specific language governing permissions and
 */

package io.orabel.orabelandroid.ui.screens.chat

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.data.Chat
import io.orabel.orabelandroid.ui.components.AppAlertDialog
import io.orabel.orabelandroid.ui.theme.AppFontFamily
import io.orabel.orabelandroid.ui.theme.OrabelAccent
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import io.orabel.orabelandroid.ui.theme.OrabelSecondary
import io.orabel.orabelandroid.ui.theme.OrabelTextPrimary

@Composable
fun DrawerUI(
    viewModel: ChatScreenViewModel,
    onItemClick: (Chat) -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            OrabelPrimary.copy(alpha = 0.05f),
                            OrabelSecondary.copy(alpha = 0.05f),
                            OrabelAccent.copy(alpha = 0.05f)
                        )
                    )
                )
                .windowInsetsPadding(WindowInsets.safeContent)
                .padding(16.dp)
                .requiredWidth(320.dp)
                .fillMaxHeight(),
    ) {
        // Header con gradiente
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(OrabelPrimary, OrabelSecondary)
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lyrion_logo),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Lyrion IA",
                color = Color.White,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Botón de nuevo chat modernizado
        Button(
            colors = ButtonDefaults.buttonColors(
                containerColor = OrabelPrimary,
                contentColor = Color.White
            ),
            onClick = {
                val chatCount = viewModel.chatsDB.getChatsCount()
                val newChatId =
                    viewModel.chatsDB.addChat(chatName = context.getString(R.string.untitled) + " ${chatCount + 1}")
                onItemClick(Chat(id = newChatId, name = context.getString(R.string.untitled) + " ${chatCount + 1}", systemPrompt = context.getString(R.string.you_are_helpful_assistant)))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Add, 
                    contentDescription = stringResource(R.string.new_chat_desc),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.new_chat), 
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Título de historial modernizado
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(
                        OrabelAccent,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.previous_chats),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        ChatsList(viewModel, onItemClick)
    }
    AppAlertDialog()
}

@Composable
private fun ColumnScope.ChatsList(
    viewModel: ChatScreenViewModel,
    onItemClick: (Chat) -> Unit,
) {
    val chats by viewModel.getChats().collectAsState(emptyList())
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(chats) { chat -> ChatListItem(chat, onItemClick) }
    }
}

@Composable
private fun LazyItemScope.ChatListItem(
    chat: Chat,
    onItemClick: (Chat) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f),
                            OrabelAccent.copy(alpha = 0.05f)
                        )
                    ),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onItemClick(chat) }
                .animateItem(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Indicador visual
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    OrabelPrimary,
                    CircleShape
                )
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.name, 
                fontSize = 16.sp, 
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = DateUtils.getRelativeTimeSpanString(chat.dateUsed.time).toString(),
                fontSize = 12.sp,
                fontFamily = AppFontFamily,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        // Icono de flecha
        Icon(
            painter = painterResource(id = R.drawable.ic_lyrion_logo),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color.Unspecified
        )
    }
}
