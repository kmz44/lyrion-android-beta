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

package io.orabel.orabelandroid.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.ui.theme.AppFontFamily
import io.orabel.orabelandroid.ui.theme.OrabelDanger
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import io.orabel.orabelandroid.ui.theme.OrabelTextPrimary
import io.orabel.orabelandroid.ui.theme.OrabelTextSecondary

private var title = ""
private var text = ""
private var positiveButtonText = ""
private var negativeButtonText = ""
private lateinit var positiveButtonOnClick: (() -> Unit)
private lateinit var negativeButtonOnClick: (() -> Unit)
private val alertDialogShowStatus = mutableStateOf(false)

@Composable
fun AppAlertDialog() {
    val visible by remember { alertDialogShowStatus }
    if (visible) {
        Dialog(
            onDismissRequest = { /* All alert dialogs are non-cancellable */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
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
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    OrabelPrimary.copy(alpha = 0.1f),
                                    CircleShape
                                )
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_lyrion_logo),
                                contentDescription = "Orabel Logo",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = title,
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = OrabelTextPrimary,
                            fontSize = 18.sp
                        )
                    }
                    
                    // Content text
                    Text(
                        text = text,
                        fontFamily = AppFontFamily,
                        color = OrabelTextSecondary,
                        fontSize = 14.sp
                    )
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                alertDialogShowStatus.value = false
                                negativeButtonOnClick()
                            }
                        ) {
                            Text(
                                text = negativeButtonText,
                                fontFamily = AppFontFamily,
                                color = OrabelTextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                alertDialogShowStatus.value = false
                                positiveButtonOnClick()
                            }
                        ) {
                            Text(
                                text = positiveButtonText,
                                fontFamily = AppFontFamily,
                                color = OrabelPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

fun createAlertDialog(
    dialogTitle: String,
    dialogText: String,
    dialogPositiveButtonText: String,
    dialogNegativeButtonText: String?,
    onPositiveButtonClick: (() -> Unit),
    onNegativeButtonClick: (() -> Unit)?,
) {
    title = dialogTitle
    text = dialogText
    positiveButtonOnClick = onPositiveButtonClick
    onNegativeButtonClick?.let { negativeButtonOnClick = it }
    positiveButtonText = dialogPositiveButtonText
    dialogNegativeButtonText?.let { negativeButtonText = it }
    alertDialogShowStatus.value = true
}
