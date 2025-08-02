package io.orabel.orabelandroid.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.ui.theme.*

@Composable
fun ModernProfileScreen(
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditProfileClick: () -> Unit
) {
    var selectedBottomNav by remember { mutableStateOf(3) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        ModernTopBar(
            title = "Perfil",
            onBackClick = onBackClick
        )
        
        // Profile Content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                // Profile Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile Picture
                    Box(
                        modifier = Modifier.size(128.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("https://lh3.googleusercontent.com/aida-public/AB6AXuCtexEHexv9oyVBeKvuNZpourwZXb--ZM--No6VBykpGPT1Jwp7XbZ7XY_JRqlTCMkK_sR_9OxCpoL8XGht9ttNwqyzb0Ye1_rXrLdQecVBYw4ZtHUbEV4paCisbIdBeIzZTrQMeTMxxJeabxXtsvjWAKoJc1F0KVtmLOiLsPj_Kcvjk_ukzzwBL3hZrTp3EdsCUCYyGFqvuhDsvWRa0TsJAamW6R3cbTtshSqLTG1DphFjWPHiQNdjDrXL_Iid9NwKm5qPRtkCOz0")
                                .build(),
                            contentDescription = "Foto de perfil",
                            modifier = Modifier
                                .size(128.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentScale = ContentScale.Crop
                        )
                        
                        // Edit Button
                        IconButton(
                            onClick = onEditProfileClick,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar perfil",
                                tint = PrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Name and Username
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Sophia Carter",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "@sophia.carter",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            }
            
            item {
                // About Section
                ModernCard(
                    elevation = 2
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Acerca de",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Soy una entusiasta de la IA, explorando la intersección entre la tecnología y la creatividad. ¡Hablemos sobre el futuro de la IA!",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }
            
            item {
                // Settings Section
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Configuración",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProfileMenuItem(
                            icon = Icons.Default.Settings,
                            title = "Configuración general",
                            subtitle = "Preferencias de la aplicación",
                            onClick = onSettingsClick
                        )
                        
                        ProfileMenuItem(
                            icon = Icons.Default.Notifications,
                            title = "Notificaciones",
                            subtitle = "Administrar notificaciones",
                            onClick = { /* TODO */ }
                        )
                        
                        ProfileMenuItem(
                            icon = Icons.Default.Security,
                            title = "Privacidad y seguridad",
                            subtitle = "Configuración de privacidad",
                            onClick = { /* TODO */ }
                        )
                        
                        ProfileMenuItem(
                            icon = Icons.Default.Help,
                            title = "Ayuda y soporte",
                            subtitle = "Obtener ayuda",
                            onClick = { /* TODO */ }
                        )
                        
                        ProfileMenuItem(
                            icon = Icons.Default.Info,
                            title = "Acerca de",
                            subtitle = "Información de la aplicación",
                            onClick = { /* TODO */ }
                        )
                    }
                }
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
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ModernCard(
        onClick = onClick,
        elevation = 1
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = PrimaryColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PrimaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                )
            }
            
            // Arrow
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Ir",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
