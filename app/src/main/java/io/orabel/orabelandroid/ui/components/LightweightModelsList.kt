/*
 * Pantalla para mostrar y descargar modelos ultra ligeros
 */

package io.orabel.orabelandroid.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orabel.orabelandroid.data.LightweightModelInfo
import io.orabel.orabelandroid.ui.theme.InterFontFamily
import io.orabel.orabelandroid.ui.theme.OrabelPrimary

@Composable
fun LightweightModelsList(
    models: List<LightweightModelInfo>,
    onModelSelected: (LightweightModelInfo) -> Unit,
    onContinueWithoutModels: () -> Unit,
    isDownloading: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Modelos de IA Disponibles",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = InterFontFamily,
            color = OrabelPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "Selecciona un modelo de IA o continúa sin descargar ninguno",
            fontSize = 16.sp,
            fontFamily = InterFontFamily,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        LazyColumn {
            items(models) { model ->
                ModelCard(
                    model = model,
                    onSelect = { onModelSelected(model) },
                    isDownloading = isDownloading
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            item {
                // Botón para continuar sin modelos
                OutlinedButton(
                    onClick = onContinueWithoutModels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    enabled = !isDownloading
                ) {
                    Text(
                        text = "Continuar sin modelos de IA",
                        fontFamily = InterFontFamily,
                        fontSize = 16.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Puedes descargar modelos más tarde desde la configuración",
                    fontSize = 12.sp,
                    fontFamily = InterFontFamily,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ModelCard(
    model: LightweightModelInfo,
    onSelect: () -> Unit,
    isDownloading: Boolean
) {
    var showTerms by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = model.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFontFamily,
                color = OrabelPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = model.description,
                fontSize = 14.sp,
                fontFamily = InterFontFamily
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Tamaño: ${model.sizeInMB} MB",
                fontSize = 12.sp,
                fontFamily = InterFontFamily
            )
            
            if (model.licenseInfo.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Licencia: ${model.licenseInfo}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFontFamily,
                    color = OrabelPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Términos de uso
            if (model.termsOfUse.isNotEmpty()) {
                OutlinedButton(
                    onClick = { showTerms = !showTerms },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (showTerms) "Ocultar términos de uso" else "Ver términos de uso",
                        fontFamily = InterFontFamily
                    )
                }
                
                if (showTerms) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = model.termsOfUse,
                            fontSize = 12.sp,
                            fontFamily = InterFontFamily,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrabelPrimary
                )
            ) {
                Text(
                    text = if (isDownloading) "Descargando..." else "Descargar y Aceptar Términos",
                    fontFamily = InterFontFamily
                )
            }
        }
    }
}
