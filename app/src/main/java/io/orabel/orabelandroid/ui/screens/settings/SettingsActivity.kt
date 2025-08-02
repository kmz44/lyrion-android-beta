package io.orabel.orabelandroid.ui.screens.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.screens.model_setup.ModernModelSetupActivity
import io.orabel.orabelandroid.ui.screens.welcome.WelcomeActivity
import io.orabel.orabelandroid.ui.theme.*
import org.koin.android.ext.android.inject

class SettingsActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            // Efecto para debug - eliminar en producción
            LaunchedEffect(isDarkTheme) {
                android.util.Log.d("SettingsActivity", "Theme changed to: ${if (isDarkTheme) "Dark" else "Light"}")
            }
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    onDarkThemeToggle = { isDark ->
                        android.util.Log.d("SettingsActivity", "Toggle clicked: $isDark")
                        orabelPreferences.setDarkTheme(isDark)
                        // Ya no necesitamos recrear la actividad porque el tema es reactivo
                    },
                    onNavigateToHome = ::navigateToHome,
                    onNavigateToSearch = ::navigateToSearch,
                    onNavigateToChat = ::navigateToChat,
                    onNavigateToProfile = ::navigateToProfile
                )
            }
        }
    }
    
    private fun navigateToHome() {
        orabelPreferences.setLastNavigationIndex(0)
        val intent = Intent(this, ModernMainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun navigateToSearch() {
        orabelPreferences.setLastNavigationIndex(1)
        val intent = Intent(this, ModernModelSetupActivity::class.java)
        intent.putExtra("openChatScreen", true) // Abrir chat automáticamente después de cargar modelo
        startActivity(intent)
        finish()
    }
    
    private fun navigateToChat() {
        orabelPreferences.setLastNavigationIndex(2)
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun navigateToProfile() {
        orabelPreferences.setLastNavigationIndex(3)
        val intent = Intent(this, WelcomeActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
fun SettingsScreen(
    isDarkTheme: Boolean,
    onDarkThemeToggle: (Boolean) -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    var selectedBottomNav by remember { mutableStateOf(-1) } // Ninguno seleccionado porque configuración no está en el menú
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Main content
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .systemBarsPadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    // Espaciador superior
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                item {
                    // Título y descripción
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Configuración",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        
                        Text(
                            text = "Personaliza tu experiencia con Lyrion",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
                
                item {
                    // Sección de Apariencia
                    SettingsSection(
                        title = "Apariencia",
                        icon = Icons.Default.Palette
                    ) {
                        // Modo Oscuro
                        SettingsItem(
                            title = "Modo Oscuro",
                            subtitle = if (isDarkTheme) "Activado" else "Desactivado",
                            icon = if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            trailing = {
                                Switch(
                                    checked = isDarkTheme,
                                    onCheckedChange = onDarkThemeToggle,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = PrimaryColor,
                                        checkedTrackColor = PrimaryColor.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        )
                    }
                }
                
                item {
                    // Sección de Información
                    SettingsSection(
                        title = "Información",
                        icon = Icons.Default.Info
                    ) {
                        SettingsItem(
                            title = "Versión",
                            subtitle = "1.0.0 beta",
                            icon = Icons.Default.Build
                        )
                        
                        SettingsItem(
                            title = "Desarrollador",
                            subtitle = "Kevin Marquez Melendez",
                            icon = Icons.Default.Person
                        )
                    }
                }
                
                item {
                    // Espaciador inferior
                    Spacer(modifier = Modifier.height(100.dp))
                }
            }
        }
        
        // Bottom Navigation con configuración
        ModernBottomNavigation(
            selectedItem = selectedBottomNav,
            onItemSelected = { index ->
                selectedBottomNav = index
                when (index) {
                    0 -> onNavigateToHome() // Home
                    1 -> onNavigateToSearch() // Búsqueda
                    2 -> onNavigateToChat() // Chat
                    3 -> onNavigateToProfile() // Perfil
                }
            }
        )
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ModernCard(
        elevation = 6
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header de la sección
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PrimaryColor,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            
            // Contenido de la sección
            content()
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        
        // Contenido
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                )
            }
        }
        
        // Trailing content
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun SettingsBottomNavigation(
    selectedItem: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
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
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Inicio
                NavigationItem(
                    icon = Icons.Default.Home,
                    label = "Inicio",
                    selected = selectedItem == 0,
                    onClick = { onItemSelected(0) }
                )
                
                // Búsqueda
                NavigationItem(
                    icon = Icons.Default.Search,
                    label = "Búsqueda",
                    selected = selectedItem == 1,
                    onClick = { onItemSelected(1) }
                )
                
                // Chat
                NavigationItem(
                    icon = Icons.Default.Chat,
                    label = "Chat",
                    selected = selectedItem == 2,
                    onClick = { onItemSelected(2) }
                )
                
                // Perfil
                NavigationItem(
                    icon = Icons.Default.Person,
                    label = "Perfil",
                    selected = selectedItem == 3,
                    onClick = { onItemSelected(3) }
                )
                
                // Configuración
                NavigationItem(
                    icon = Icons.Default.Settings,
                    label = "Config",
                    selected = selectedItem == 4,
                    onClick = { onItemSelected(4) }
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) PrimaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) PrimaryColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        )
    }
}
