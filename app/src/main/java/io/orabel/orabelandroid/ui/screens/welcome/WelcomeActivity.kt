/*
 * Copyright (C) 2024 Orabel IA
 * Renovated for teens with modern UI/UX
 */

package io.orabel.orabelandroid.ui.screens.welcome

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.ui.screens.model_setup.ModernModelSetupActivity
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.screens.settings.SettingsActivity
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.utils.NetworkUtils
import io.orabel.orabelandroid.data.OrabelPreferences
import org.koin.android.ext.android.inject

class WelcomeActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContentView(ComposeView(this).apply {
            setContent {
                val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
                
                ModernOrabelTheme(
                    darkTheme = isDarkTheme
                ) {
                    ModernWelcomeScreen(
                        onLocalModeClick = { openModelSetup() },
                        onCloudModeClick = { showCloudModeComingSoon() },
                        onMoreInfoClick = { openMoreInformation() },
                        onNavigateToHome = { openMainActivity() },
                        onNavigateToChat = { openChat() },
                        onNavigateToSearch = { openModelSetup() },
                        onNavigateToSettings = { openSettings() }
                    )
                }
            }
        })
    }
    
    private fun openMainActivity() {
        orabelPreferences.setLastNavigationIndex(0)
        val intent = Intent(this, ModernMainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openChat() {
        orabelPreferences.setLastNavigationIndex(2)
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
    }
    
    private fun openModelSetup() {
        orabelPreferences.setLastNavigationIndex(1)
        val intent = Intent(this, ModernModelSetupActivity::class.java)
        intent.putExtra("openChatScreen", true)
        startActivity(intent)
        finish()
    }
    
    private fun openSettings() {
        // No guarda índice de navegación porque configuración no está en el menú inferior
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }
      private fun showCloudModeComingSoon() {
        Toast.makeText(this, getString(R.string.cloud_mode_coming_soon), Toast.LENGTH_LONG).show()
    }
    
    private fun openMoreInformation() {
        if (NetworkUtils.isInternetAvailable(this)) {
            val intent = Intent(this, Class.forName("io.orabel.orabelandroid.ui.screens.webview.WebViewActivity"))
            intent.putExtra("url", "https://masinformacion.usasavorwarts.com/")
            intent.putExtra("title", getString(R.string.more_info))
            startActivity(intent)
        } else {
            Toast.makeText(this, getString(R.string.no_internet_connection), Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun WelcomeScreen(
    onLocalModeClick: () -> Unit,
    onCloudModeClick: () -> Unit,
    onMoreInfoClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        OrabelSecondary,
                        OrabelPrimary
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo placeholder
            Card(
                modifier = Modifier
                    .size(100.dp)
                    .padding(bottom = 32.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = OrabelPrimary)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💭",
                        fontSize = 48.sp
                    )
                }
            }
            
            // Welcome title
            Text(
                text = stringResource(R.string.welcome_to_orabel),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Subtitle
            Text(
                text = stringResource(R.string.welcome_subtitle),
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            // Mode selection title
            Text(
                text = stringResource(R.string.choose_your_mode),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Local Mode Card
            ModeCard(
                title = stringResource(R.string.local_mode),
                description = stringResource(R.string.local_mode_desc),
                emoji = "🏠",
                isAvailable = true,
                onClick = onLocalModeClick,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Cloud Mode Card
            ModeCard(
                title = stringResource(R.string.cloud_mode),
                description = stringResource(R.string.cloud_mode_desc),
                emoji = "☁️",
                isAvailable = false,
                onClick = onCloudModeClick,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // More Information Button
            OutlinedButton(
                onClick = onMoreInfoClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.White, Color.White.copy(alpha = 0.7f))
                    )
                )
            ) {
                Text(
                    text = stringResource(R.string.more_info),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Footer
            Text(
                text = stringResource(R.string.welcome_footer),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ModeCard(
    title: String,
    description: String,
    emoji: String,
    isAvailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = isAvailable) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAvailable) Color.White else Color.White.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emoji,
                fontSize = 48.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAvailable) OrabelPrimary else OrabelPrimary.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = description,
                fontSize = 14.sp,
                color = if (isAvailable) Color.Gray else Color.Gray.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)            )
            
            if (!isAvailable) {
                Text(
                    text = stringResource(R.string.coming_soon),
                    fontSize = 12.sp,
                    color = OrabelAccent,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun ModernWelcomeScreen(
    onLocalModeClick: () -> Unit,
    onCloudModeClick: () -> Unit,
    onMoreInfoClick: () -> Unit,
    onNavigateToHome: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var selectedBottomNav by remember { mutableStateOf(3) } // Perfil seleccionado por defecto
    var showAboutDialog by remember { mutableStateOf(false) }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Top bar con icono de configuración
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuración",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                // Espaciador superior
                Spacer(modifier = Modifier.height(16.dp))
            
            // Logo y título
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Logo circular con gradiente
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(PrimaryColor, PrimaryColor.copy(alpha = 0.7f))
                            ),
                            shape = RoundedCornerShape(60.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Perfil",
                        tint = Color.White,
                        modifier = Modifier.size(60.dp)
                    )
                }
                
                // Título principal
                Text(
                    text = "Perfil de Usuario",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    textAlign = TextAlign.Center
                )
                
                // Subtítulo
                Text(
                    text = "Configuración y información de la aplicación",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            
            // Sección principal
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Botón Acerca de Lyrion
                ModernCard(
                    onClick = { showAboutDialog = true },
                    elevation = 6
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Icono
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = PrimaryColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Acerca de",
                                tint = PrimaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Contenido
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Acerca de Lyrion",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Información sobre la aplicación y tecnologías",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            )
                        }
                        
                        // Flecha
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Ir",
                            tint = PrimaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // Anuncio de Inicio de Sesión
                ModernCard(
                    elevation = 4
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Icono
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = SecondaryColor.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Inicio de Sesión",
                                tint = SecondaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        // Contenido
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Inicio de Sesión",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = stringResource(R.string.login_coming_soon),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SecondaryColor,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            )
                        }
                        
                        // Icono de coming soon
                        Text(
                            text = "🚀",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
            
            // Espaciador flexible
            Spacer(modifier = Modifier.weight(1f))
            
            // Texto de información
            Text(
                text = "¡Gracias por usar Lyrion! Tu privacidad y autonomía son nuestra prioridad.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
    
    // Bottom Navigation
    ModernBottomNavigation(
        selectedItem = selectedBottomNav,
        onItemSelected = { index ->
            selectedBottomNav = index
            when (index) {
                0 -> onNavigateToHome() // Home
                1 -> onNavigateToSearch() // Búsqueda -> Setup de modelos
                2 -> onNavigateToChat() // Chat
                3 -> { /* Perfil - ya estamos aquí */ }
            }
        }
    )
}

    // Diálogo de Acerca de Lyrion
    if (showAboutDialog) {
        AboutLyrionDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
fun AboutLyrionDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Acerca de Lyrion",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(20.dp)
                                )
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_lyrion_logo),
                                contentDescription = "Cerrar",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    
                    // Contenido scrollable
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.about_lyrion_content),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    
                    // Botón de cerrar
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Cerrar",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}
