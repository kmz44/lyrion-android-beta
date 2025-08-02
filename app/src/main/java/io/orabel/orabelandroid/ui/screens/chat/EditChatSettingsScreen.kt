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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ThermostatAuto
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.ui.components.SmallLabelText
import io.orabel.orabelandroid.ui.theme.AppFontFamily
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import io.orabel.orabelandroid.ui.theme.OrabelSecondary
import io.orabel.orabelandroid.ui.theme.OrabelSuccess
import io.orabel.orabelandroid.ui.theme.OrabelTextPrimary
import io.orabel.orabelandroid.ui.theme.OrabelTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditChatSettingsScreen(
    viewModel: ChatScreenViewModel,
    onBackClicked: () -> Unit,
) {
    val context = LocalContext.current
    viewModel.currChatState.value?.let { chat ->
        var chatName by remember { mutableStateOf(chat.name) }
        var systemPrompt by remember { mutableStateOf(chat.systemPrompt) }
        var minP by remember { mutableFloatStateOf(chat.minP) }
        var temperature by remember { mutableFloatStateOf(chat.temperature) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = OrabelPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.edit_chat_settings),
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = OrabelTextPrimary,
                                fontSize = 22.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { onBackClicked() },
                            modifier = Modifier
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            OrabelPrimary.copy(alpha = 0.15f),
                                            OrabelPrimary.copy(alpha = 0.05f)
                                        )
                                    ),
                                    CircleShape
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back_desc),
                                tint = OrabelPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.updateChat(
                                    chat.copy(
                                        name = chatName,
                                        systemPrompt = systemPrompt,
                                        minP = minP,
                                        temperature = temperature,
                                    ),
                                )
                                onBackClicked()
                            },
                            modifier = Modifier
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            OrabelSuccess.copy(alpha = 0.15f),
                                            OrabelSuccess.copy(alpha = 0.05f)
                                        )
                                    ),
                                    CircleShape
                                )
                                .size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Done,
                                contentDescription = stringResource(R.string.save_settings_desc),
                                tint = OrabelSuccess,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .fillMaxSize()
                    .padding(20.dp)
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Chat Name Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                OrabelPrimary.copy(alpha = 0.15f),
                                                OrabelPrimary.copy(alpha = 0.05f)
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = OrabelPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.chat_name),
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = OrabelTextPrimary,
                                fontSize = 18.sp
                            )
                        }
                        
                        OutlinedTextField(
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrabelPrimary,
                                unfocusedBorderColor = OrabelSecondary.copy(alpha = 0.3f),
                                focusedLabelColor = OrabelPrimary,
                                unfocusedLabelColor = OrabelTextSecondary,
                                cursorColor = OrabelPrimary,
                                focusedTextColor = OrabelTextPrimary,
                                unfocusedTextColor = OrabelTextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            value = chatName,
                            onValueChange = { chatName = it },
                            label = { 
                                Text(
                                    "Nombre del chat",
                                    fontFamily = AppFontFamily
                                ) 
                            },
                            textStyle = TextStyle(
                                fontFamily = AppFontFamily,
                                color = OrabelTextPrimary,
                                fontSize = 16.sp
                            ),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.Words,
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }

                // System Prompt Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                OrabelSecondary.copy(alpha = 0.15f),
                                                OrabelSecondary.copy(alpha = 0.05f)
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = OrabelSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.system_prompt),
                                fontFamily = AppFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = OrabelTextPrimary,
                                fontSize = 18.sp
                            )
                        }
                        
                        OutlinedTextField(
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrabelSecondary,
                                unfocusedBorderColor = OrabelSecondary.copy(alpha = 0.3f),
                                focusedLabelColor = OrabelSecondary,
                                unfocusedLabelColor = OrabelTextSecondary,
                                cursorColor = OrabelSecondary,
                                focusedTextColor = OrabelTextPrimary,
                                unfocusedTextColor = OrabelTextPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            value = systemPrompt,
                            onValueChange = { systemPrompt = it },
                            label = { 
                                Text(
                                    "Prompt del sistema",
                                    fontFamily = AppFontFamily
                                ) 
                            },
                            textStyle = TextStyle(
                                fontFamily = AppFontFamily,
                                color = OrabelTextPrimary,
                                fontSize = 16.sp
                            ),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                            shape = RoundedCornerShape(16.dp),
                            maxLines = 5
                        )
                        
                        if (chat.isTask) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                OrabelSecondary.copy(alpha = 0.1f),
                                                OrabelSecondary.copy(alpha = 0.05f)
                                            )
                                        ),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                SmallLabelText(
                                    context.getString(R.string.updates_task_notice),
                                    textColor = OrabelTextSecondary
                                )
                            }
                        }
                    }
                }

                // Min P Parameter Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                OrabelPrimary.copy(alpha = 0.15f),
                                                OrabelPrimary.copy(alpha = 0.05f)
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = OrabelPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.min_p),
                                    fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OrabelTextPrimary,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "%.2f".format(minP),
                                    fontFamily = AppFontFamily,
                                    color = OrabelPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Text(
                            stringResource(R.string.min_p_description),
                            fontFamily = AppFontFamily,
                            color = OrabelTextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Slider(
                            value = minP,
                            onValueChange = { minP = it },
                            valueRange = 0.0f..1.0f,
                            steps = 100,
                            colors = SliderDefaults.colors(
                                thumbColor = OrabelPrimary,
                                activeTrackColor = OrabelPrimary,
                                inactiveTrackColor = OrabelSecondary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }

                // Temperature Parameter Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                OrabelSecondary.copy(alpha = 0.15f),
                                                OrabelSecondary.copy(alpha = 0.05f)
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.ThermostatAuto,
                                    contentDescription = null,
                                    tint = OrabelSecondary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.temperature),
                                    fontFamily = AppFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OrabelTextPrimary,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "%.1f".format(temperature),
                                    fontFamily = AppFontFamily,
                                    color = OrabelSecondary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Text(
                            stringResource(R.string.temperature_description),
                            fontFamily = AppFontFamily,
                            color = OrabelTextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.0f..5.0f,
                            steps = 50,
                            colors = SliderDefaults.colors(
                                thumbColor = OrabelSecondary,
                                activeTrackColor = OrabelSecondary,
                                inactiveTrackColor = OrabelSecondary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}
