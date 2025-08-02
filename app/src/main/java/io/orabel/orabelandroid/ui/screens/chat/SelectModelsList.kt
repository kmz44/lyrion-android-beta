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

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.data.LLMModel
import io.orabel.orabelandroid.ui.components.createAlertDialog
import io.orabel.orabelandroid.ui.screens.model_setup.ModernModelSetupActivity
import io.orabel.orabelandroid.ui.theme.AppFontFamily
import io.orabel.orabelandroid.ui.theme.InterFontFamily
import io.orabel.orabelandroid.ui.theme.OrabelDanger
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import io.orabel.orabelandroid.ui.theme.OrabelTextPrimary
import io.orabel.orabelandroid.ui.theme.OrabelTextSecondary
import java.io.File

@Composable
fun SelectModelsList(
    onDismissRequest: () -> Unit,
    modelsList: List<LLMModel>,
    onModelListItemClick: (LLMModel) -> Unit,
    onModelDeleteClick: (LLMModel) -> Unit,
    showModelDeleteIcon: Boolean = true,
) {
    val context = LocalContext.current
    
    Popup(
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                OrabelPrimary.copy(alpha = 0.1f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = OrabelPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Seleccionar Modelo",
                        fontFamily = AppFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                }

                // Lista de modelos disponibles
                if (modelsList.isNotEmpty()) {
                    modelsList.forEach { model ->
                        ModelOptionItem(
                            model = model,
                            onModelListItemClick = onModelListItemClick,
                            onModelDeleteClick = onModelDeleteClick,
                            showModelDeleteIcon = showModelDeleteIcon,
                        )
                    }
                    
                    // Separador antes del botón "Cargar"
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Botón para cargar nuevo modelo
                Button(
                    onClick = {
                        Intent(context, ModernModelSetupActivity::class.java).also {
                            it.putExtra("openChatScreen", true) // Cambiar a true para abrir chat automáticamente
                            context.startActivity(it)
                        }
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OrabelPrimary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cargar",
                            fontFamily = AppFontFamily,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelOptionItem(
    model: LLMModel,
    onModelListItemClick: (LLMModel) -> Unit,
    onModelDeleteClick: (LLMModel) -> Unit,
    showModelDeleteIcon: Boolean,
) {
    val context = LocalContext.current
    
    // Model selection option - like ChatMoreOptionsPopup items
    TextButton(
        onClick = {
            onModelListItemClick(model)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = OrabelPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = model.name,
                        fontFamily = AppFontFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "%.1f GB".format(File(model.path).length() / (1e+9)),
                        fontFamily = AppFontFamily,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
            
            if (showModelDeleteIcon) {
                IconButton(
                    onClick = {
                        createAlertDialog(
                            dialogTitle = context.getString(R.string.delete_model),
                            dialogText = context.getString(R.string.delete_model_confirmation, model.name),
                            dialogPositiveButtonText = context.getString(R.string.delete),
                            dialogNegativeButtonText = context.getString(R.string.cancel),
                            onPositiveButtonClick = { onModelDeleteClick(model) },
                            onNegativeButtonClick = {},
                        )
                    }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = context.getString(R.string.delete_model_desc),
                        tint = OrabelDanger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
