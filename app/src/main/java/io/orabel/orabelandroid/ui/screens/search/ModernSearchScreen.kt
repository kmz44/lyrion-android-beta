package io.orabel.orabelandroid.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.ui.theme.*

@Composable
fun ModernSearchScreen(
    onBackClick: () -> Unit,
    onModelSelected: (AIModel) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedBottomNav by remember { mutableStateOf(1) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        ModernTopBar(
            title = "Buscar",
            onBackClick = onBackClick
        )
        
        // Search Content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // Search Bar
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Buscar",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField()
                            }
                            
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Limpiar",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                )
            }
            
            item {
                // Section Title
                Text(
                    text = "Modelo de IA local",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
            
            // AI Models List
            items(aiModels.filter { 
                searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true) 
            }) { model ->
                AIModelCard(
                    model = model,
                    onClick = { onModelSelected(model) }
                )
            }
        }
        
        // Bottom Navigation
        ModernBottomNavigation(
            selectedItem = selectedBottomNav,
            onItemSelected = { selectedBottomNav = it }
        )
    }
}

@Composable
fun AIModelCard(
    model: AIModel,
    onClick: () -> Unit
) {
    ModernCard(
        onClick = onClick,
        elevation = 2
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = model.color.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = model.name,
                    tint = model.color,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Model Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = model.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                )
            }
            
            // Action Button or Status
            when (model.status) {
                ModelStatus.Selected -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Seleccionado",
                        tint = AccentColor1,
                        modifier = Modifier.size(24.dp)
                    )
                }
                ModelStatus.Available -> {
                    OutlinedButton(
                        onClick = onClick,
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryColor
                        )
                    ) {
                        Text(
                            text = "Detalles",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                ModelStatus.Downloading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PrimaryColor,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Descargando...",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = PrimaryColor
                            )
                        )
                    }
                }
            }
        }
    }
}

data class AIModel(
    val name: String,
    val description: String,
    val color: Color,
    val status: ModelStatus
)

enum class ModelStatus {
    Selected,
    Available,
    Downloading
}

val aiModels = listOf(
    AIModel(
        name = "GPT-3.5",
        description = "Predeterminado",
        color = AccentColor1,
        status = ModelStatus.Selected
    ),
    AIModel(
        name = "GPT-3.5 Turbo",
        description = "Más rápido, menos preciso",
        color = SecondaryColor,
        status = ModelStatus.Available
    ),
    AIModel(
        name = "GPT-4",
        description = "Más preciso, más lento",
        color = AccentColor2,
        status = ModelStatus.Downloading
    ),
    AIModel(
        name = "Llama 2",
        description = "Modelo local eficiente",
        color = PrimaryColor,
        status = ModelStatus.Available
    )
)
