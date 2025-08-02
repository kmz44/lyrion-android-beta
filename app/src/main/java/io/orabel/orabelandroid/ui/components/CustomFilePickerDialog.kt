/*
 * Copyright (C) 2024 Orabel IA
 * Custom file picker dialog with Orabel branding
 */

package io.orabel.orabelandroid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.ui.theme.AppFontFamily
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import io.orabel.orabelandroid.ui.theme.OrabelTextPrimary
import io.orabel.orabelandroid.ui.theme.OrabelTextSecondary

@Composable
fun CustomFilePickerDialog(
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onBrowseModels: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
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
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
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
                            text = "Seleccionar Modelo",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = AppFontFamily,
                            color = OrabelTextPrimary
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_lyrion_logo),
                            contentDescription = "Cerrar",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Description
                Text(
                    text = "Elige cómo quieres obtener tu modelo de IA para empezar a chatear",
                    fontSize = 14.sp,
                    fontFamily = AppFontFamily,
                    color = OrabelTextSecondary,
                    textAlign = TextAlign.Center
                )
                
                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Browse models button
                    Button(
                        onClick = {
                            onDismiss()
                            onBrowseModels()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrabelPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🌐 Modelos Disponibles",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = AppFontFamily
                        )
                    }
                    
                    // Pick file button
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onPickFile()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = OrabelPrimary
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = OrabelPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "📂 Buscar Archivo Local",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = AppFontFamily
                        )
                    }
                }
                
                // Footer text
                Text(
                    text = "Solo se aceptan archivos .gguf compatibles",
                    fontSize = 12.sp,
                    fontFamily = AppFontFamily,
                    color = OrabelTextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
