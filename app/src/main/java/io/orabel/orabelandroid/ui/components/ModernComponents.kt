package io.orabel.orabelandroid.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orabel.orabelandroid.ui.theme.PrimaryColor
import io.orabel.orabelandroid.ui.theme.TextSecondary

/**
 * Modificador que permite detectar gestos de swipe horizontal para navegar entre pantallas.
 * @param currentIndex Índice actual de la pantalla (0-4)
 * @param onSwipeLeft Llamado cuando se desliza hacia la izquierda (ir a la siguiente pantalla)
 * @param onSwipeRight Llamado cuando se desliza hacia la derecha (ir a la pantalla anterior)
 */
fun Modifier.swipeableNavigation(
    currentIndex: Int,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    swipeThreshold: Float = 100f
): Modifier = this.pointerInput(currentIndex) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragEnd = {
            if (totalDrag < -swipeThreshold && currentIndex < 4) {
                // Swipe hacia la izquierda -> ir a la siguiente pantalla
                onSwipeLeft()
            } else if (totalDrag > swipeThreshold && currentIndex > 0) {
                // Swipe hacia la derecha -> ir a la pantalla anterior
                onSwipeRight()
            }
            totalDrag = 0f
        },
        onDragCancel = { totalDrag = 0f },
        onHorizontalDrag = { _, dragAmount ->
            totalDrag += dragAmount
        }
    )
}

@Composable
fun ModernTopBar(
    title: String,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        )
                    )
                )
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Botón de retroceso
                if (showBackButton && onBackClick != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }
                
                // Título
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
                
                // Acciones
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
        }
    }
}

@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    elevation: Int = 4,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = elevation.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = PrimaryColor.copy(alpha = 0.1f),
                spotColor = PrimaryColor.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(16.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

@Composable
fun ModernCard(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    primaryActionText: String? = null,
    onPrimaryActionClick: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    elevation: Int = 4,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
) {
    ModernCard(
        modifier = modifier,
        onClick = onClick,
        elevation = elevation,
        backgroundColor = backgroundColor
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(28.dp)
                            .padding(end = 8.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (primaryActionText != null || secondaryActionText != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (primaryActionText != null && onPrimaryActionClick != null) {
                        ModernButton(
                            text = primaryActionText,
                            onClick = onPrimaryActionClick,
                            style = ButtonStyle.Primary
                        )
                    }
                    if (secondaryActionText != null && onSecondaryActionClick != null) {
                        ModernButton(
                            text = secondaryActionText,
                            onClick = onSecondaryActionClick,
                            style = ButtonStyle.Secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle.Primary,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null
) {
    val colors = when (style) {
        ButtonStyle.Primary -> ButtonDefaults.buttonColors(
            containerColor = PrimaryColor,
            contentColor = Color.White,
            disabledContainerColor = PrimaryColor.copy(alpha = 0.3f),
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        )
        ButtonStyle.Secondary -> ButtonDefaults.outlinedButtonColors(
            contentColor = PrimaryColor,
            disabledContentColor = PrimaryColor.copy(alpha = 0.3f)
        )
        ButtonStyle.Tertiary -> ButtonDefaults.textButtonColors(
            contentColor = PrimaryColor,
            disabledContentColor = PrimaryColor.copy(alpha = 0.3f)
        )
    }
    
    when (style) {
        ButtonStyle.Primary -> {
            Button(
                onClick = onClick,
                modifier = modifier
                    .height(56.dp)
                    .shadow(
                        elevation = if (enabled) 8.dp else 0.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = PrimaryColor.copy(alpha = 0.3f),
                        spotColor = PrimaryColor.copy(alpha = 0.3f)
                    ),
                enabled = enabled,
                colors = colors,
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                ButtonContent(text, leadingIcon, trailingIcon)
            }
        }
        ButtonStyle.Secondary -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.height(56.dp),
                enabled = enabled,
                colors = colors,
                shape = RoundedCornerShape(28.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.horizontalGradient(
                        colors = listOf(PrimaryColor, PrimaryColor.copy(alpha = 0.7f))
                    ),
                    width = 2.dp
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                ButtonContent(text, leadingIcon, trailingIcon)
            }
        }
        ButtonStyle.Tertiary -> {
            TextButton(
                onClick = onClick,
                modifier = modifier.height(56.dp),
                enabled = enabled,
                colors = colors,
                shape = RoundedCornerShape(28.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
            ) {
                ButtonContent(text, leadingIcon, trailingIcon)
            }
        }
    }
}

@Composable
private fun ButtonContent(
    text: String,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        )
        
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

enum class ButtonStyle {
    Primary,
    Secondary,
    Tertiary
}

@Composable
fun ModernBottomNavigation(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isGlassStyle: Boolean = false
) {
    if (isGlassStyle) {
        // Estilo Liquid Glass (Layout idéntico al original, solo cambia estética)
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = Color.Transparent, // Fondo transparente base
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(Color.Black.copy(alpha = 0.3f)) // Fondo semitransparente oscuro
                    .border(
                         BorderStroke(
                            width = 1.dp, 
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.3f), 
                                    Color.White.copy(alpha = 0.05f)
                                )
                            )
                         )
                    )
            ) {
                NavigationItemsRow(
                    selectedItem = selectedItem,
                    onItemSelected = onItemSelected,
                    unselectedColor = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    } else {
        // Estilo Original
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            ) {
                NavigationItemsRow(
                    selectedItem = selectedItem,
                    onItemSelected = onItemSelected,
                    unselectedColor = TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun NavigationItemsRow(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    unselectedColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Búsqueda
        NavigationItem(
            icon = Icons.Default.Search,
            label = "Búsqueda",
            selected = selectedItem == 0,
            onClick = { onItemSelected(0) },
            unselectedColor = unselectedColor
        )
        
        // Chat 
        NavigationItem(
            icon = Icons.Default.Chat,
            label = "Chat",
            selected = selectedItem == 1,
            onClick = { onItemSelected(1) },
            unselectedColor = unselectedColor
        )
        
        // Inicio
        NavigationItem(
            icon = Icons.Default.Home,
            label = "Inicio",
            selected = selectedItem == 2,
            onClick = { onItemSelected(2) },
            unselectedColor = unselectedColor
        )
        
        // Historial (Antes Calendario)
        NavigationItem(
            icon = Icons.Default.History, // Icono Historial
            label = "Historial",
            selected = selectedItem == 3,
            onClick = { onItemSelected(3) },
            unselectedColor = unselectedColor
        )
        
        // Perfil
        NavigationItem(
            icon = Icons.Default.Person,
            label = "Perfil",
            selected = selectedItem == 4,
            onClick = { onItemSelected(4) },
            unselectedColor = unselectedColor
        )
    }
}

@Composable
private fun NavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    unselectedColor: Color
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) PrimaryColor else unselectedColor,
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) PrimaryColor else unselectedColor
            )
        )
    }
}
