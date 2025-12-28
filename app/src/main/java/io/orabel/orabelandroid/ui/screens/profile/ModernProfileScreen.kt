package io.orabel.orabelandroid.ui.screens.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import io.orabel.orabelandroid.ui.components.* // Importar componentes incluyendo swipeableNavigation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.orabel.orabelandroid.data.social.*
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.ui.theme.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onSignOut: () -> Unit,
    lastNavigationIndex: Int
) {
    var selectedBottomNav by remember { mutableStateOf(4) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository.getInstance(context) }
    
    val userProfileUiState by viewModel.userProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // 🔥 NUEVO: Observar perfil social de forma reactiva desde el repositorio
    val fullProfile by repository.currentUserProfile.collectAsState()
    
    // Otros estados sociales
    var userStats by remember { mutableStateOf(UserStatsDTO()) }
    var userPosts by remember { mutableStateOf<List<PostDTO>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Posts, 1: Videos

    // Estados de configuración (BottomSheet)
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var currentApiKey by remember { mutableStateOf("") }
    
    // Cargar API key guardada
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("gemini_settings", android.content.Context.MODE_PRIVATE)
        currentApiKey = prefs.getString("user_api_key", "") ?: ""
    }

    // Cargar datos del perfil completo y social
    LaunchedEffect(Unit) {
        val userId = repository.getCurrentUserUUID()
        android.util.Log.d("ModernProfileScreen", "LaunchedEffect: userId=$userId")
        
        if (userId != null) {
            // Force fetch to ensure latest data from Supabase - el repositorio actualizará el StateFlow
            repository.fetchCurrentUserProfile()
            
            // Fetch stats and posts independently
            launch { 
                userStats = repository.countUserStats(userId) 
                android.util.Log.d("ModernProfileScreen", "Fetched stats: $userStats")
            }
            launch { 
                userPosts = repository.fetchUserPosts(userId) 
                android.util.Log.d("ModernProfileScreen", "Fetched posts: ${userPosts.size}")
            }
        } else {
            android.util.Log.e("ModernProfileScreen", "User ID is null, cannot fetch profile")
        }
    }

    Scaffold(
        bottomBar = {
            ModernBottomNavigation(
                selectedItem = selectedBottomNav,
                onItemSelected = { index ->
                    selectedBottomNav = index
                    when (index) {
                        0 -> onNavigateToSearch() // Búsqueda
                        1 -> onNavigateToChat() // Chat
                        2 -> onNavigateToHome() // Inicio
                        3 -> onNavigateToCalendar() // Historial
                        4 -> { /* Ya estamos aqui - Perfil */ }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .swipeableNavigation(
                currentIndex = 4, // Perfil está en índice 4
                onSwipeLeft = { }, // No hay siguiente
                onSwipeRight = { onNavigateToCalendar() } // Ir a Historial (índice 3)
            )
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp) // Espacio para bottom nav
            ) {
                // ================= HEADER =================
                item(span = { GridItemSpan(3) }) {
                    Column {
                        // Banner & Avatar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            // Banner
                            if (!fullProfile?.bannerUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = fullProfile?.bannerUrl,
                                    contentDescription = "Banner",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(OrabelPrimary, OrabelPrimary.copy(alpha = 0.6f))
                                            )
                                        )
                                )
                            }

                            // Botón "Más" (Settings)
                            IconButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Más opciones",
                                    tint = Color.White
                                )
                            }
                            
                            // Avatar superpuesto
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 16.dp)
                                    .offset(y = 40.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(108.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(4.dp)
                                ) {
                                    // STRICTLY SUPABASE DATA (No Google Auth Fallback)
                                    io.orabel.orabelandroid.ui.screens.search.components.AvatarView(
                                        url = fullProfile?.avatarUrl,
                                        name = fullProfile?.username ?: "User",
                                        size = 100
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(44.dp))
                        
                            // Info Usuario
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            // STRICTLY SUPABASE DATA
                            val displayName = fullProfile?.nombre
                            val lastName = fullProfile?.apellido
                            val username = fullProfile?.username
                            
                            // Construct full name or fallback to username
                            val fullName = if (!displayName.isNullOrBlank()) {
                                "$displayName ${lastName ?: ""}".trim()
                            } else {
                                null
                            }

                            Text(
                                text = fullName ?: username ?: "Cargando...",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            
                            val occupation = fullProfile?.occupation
                            if (!occupation.isNullOrBlank()) {
                                Text(
                                    text = occupation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            
                            val bio = fullProfile?.bio
                            if (!bio.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = bio,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            // Stats
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                ProfileStatItem(count = userStats.posts, label = "POSTS")
                                ProfileStatItem(count = userStats.followers, label = "SEGUIDORES")
                                ProfileStatItem(count = userStats.following, label = "SEGUIDOS")
                            }

                            // Actions Row
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Button(
                                    onClick = { /* TODO: Edit Profile */ },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text("Editar perfil", color = MaterialTheme.colorScheme.onSurface)
                                }
                                
                                OutlinedButton(
                                    onClick = { /* TODO: Share Profile */ },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Compartir perfil")
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Tabs
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TabItem(
                                icon = Icons.Default.GridOn,
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                modifier = Modifier.weight(1f)
                            )
                            TabItem(
                                icon = Icons.Default.VideoLibrary,
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } // End Header

                // ================= GRID ITEMS =================
                if (userPosts.isEmpty()) {
                    item(span = { GridItemSpan(3) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aún no tienes publicaciones", color = Color.Gray)
                        }
                    }
                } else {
                    items(userPosts) { post ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(1.dp)
                                .background(Color.LightGray)
                        ) {
                            AsyncImage(
                                model = post.thumbnailUrl ?: post.mediaUrl ?: post.imageUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            if (post.mediaType == "video" || post.contentType == "reel") {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                                )
                            }
                        }
                    }
                }
            } // End Grid
        } // End Box Content
    } // End Scaffold

    // Estados para nuevas opciones iOS
    var showUserDataDialog by remember { mutableStateOf(false) } // Deprecated, keeping for safety or removing if unused
    var showMisDatosScreen by remember { mutableStateOf(false) }

    // FULL SCREEN OVERLAYS
    if (showMisDatosScreen) {
        val repository = remember { io.orabel.orabelandroid.data.social.SocialRepository.getInstance(context) }
        io.orabel.orabelandroid.ui.screens.search.MisDatosScreen(
            repository = repository,
            onBack = { showMisDatosScreen = false }
        )
        // Return early to hide underlying content or use Box with zIndex if animation needed. 
        // Returning here replaces the content.
        return
    }

    // ================= SETTINGS SHEET =================
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Más opciones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // --- Opciones de Inicio (Movidas aquí) ---
                
                // Gemini Live Audio 2.5
                ProfileMenuItem(
                    icon = Icons.Default.Mic,
                    title = "🎙️ Gemini Live Audio 2.5",
                    subtitle = "Conversación por voz en tiempo real",
                    onClick = { 
                        val intent = Intent(context, io.orabel.orabelandroid.ui.screens.gemini_live.GeminiLiveActivity::class.java)
                        context.startActivity(intent)
                        showSettingsSheet = false 
                    },
                    iconColor = Color(0xFF00C853), // Green
                    bgColor = Color(0xFF00C853).copy(alpha = 0.1f)
                )

                Divider(modifier = Modifier.padding(start = 56.dp))

                // Conocer Opciones Offline
                ProfileMenuItem(
                    icon = Icons.Default.CloudOff,
                    title = "🔌 Opciones Offline",
                    subtitle = "Funciones sin internet",
                    onClick = { 
                        val intent = Intent(context, io.orabel.orabelandroid.ui.screens.offlineoptions.OfflineOptionsActivity::class.java)
                        context.startActivity(intent)
                        showSettingsSheet = false 
                    },
                    iconColor = Color(0xFF607D8B), // Blue Grey
                    bgColor = Color(0xFF607D8B).copy(alpha = 0.1f)
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // --- iOS Style Options ---
                
                // 1. Datos del Usuario (Funcional)
                ProfileMenuItem(
                    icon = Icons.Default.ContactPage,
                    title = "Datos del Usuario",
                    subtitle = "Ver y editar información",
                    onClick = { 
                        showMisDatosScreen = true 
                        showSettingsSheet = false 
                    },
                    iconColor = Color(0xFFAF52DE), // Purple
                    bgColor = Color(0xFFAF52DE).copy(alpha = 0.1f)
                )

                Divider(modifier = Modifier.padding(start = 56.dp))

                // 2. Atributos
                ProfileMenuItem(
                    icon = Icons.Default.BarChart,
                    title = "Atributos",
                    subtitle = "Estadísticas y niveles",
                    onClick = { /* TODO */ },
                    iconColor = Color(0xFF5856D6), // Indigo
                    bgColor = Color(0xFF5856D6).copy(alpha = 0.1f)
                )

                Divider(modifier = Modifier.padding(start = 56.dp))

                // 3. Ajustes de Voz
                ProfileMenuItem(
                    icon = Icons.Default.GraphicEq,
                    title = "Ajustes de Voz",
                    subtitle = "Configuración de voz IA",
                    onClick = { /* TODO */ },
                    iconColor = Color(0xFF007AFF), // Blue
                    bgColor = Color(0xFF007AFF).copy(alpha = 0.1f)
                )

                Divider(modifier = Modifier.padding(start = 56.dp))

                // 4. IA de Inicio
                ProfileMenuItem(
                    icon = Icons.Default.SmartToy,
                    title = "IA de Inicio",
                    subtitle = "Inteligencia artificial",
                    onClick = { /* TODO */ },
                    iconColor = Color(0xFFFF2D55), // Pink
                    bgColor = Color(0xFFFF2D55).copy(alpha = 0.1f)
                )

                Divider(modifier = Modifier.padding(start = 56.dp))

                // 5. Personalizar Inicio
                ProfileMenuItem(
                    icon = Icons.Default.Palette,
                    title = "Personalizar Inicio",
                    subtitle = "Estilo y diseño",
                    onClick = { /* TODO */ },
                    iconColor = Color(0xFFFF9500), // Orange
                    bgColor = Color(0xFFFF9500).copy(alpha = 0.1f)
                )

                Divider(modifier = Modifier.padding(start = 56.dp))

                // 6. Privacidad
                ProfileMenuItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacidad",
                    subtitle = "Seguridad y datos",
                    onClick = { /* TODO */ },
                    iconColor = Color(0xFF5AC8FA), // Teal
                    bgColor = Color(0xFF5AC8FA).copy(alpha = 0.1f)
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // --- Restored Android Options (Styled) ---

                // Diario de Salud
                ProfileMenuItem(
                    icon = Icons.Default.HealthAndSafety,
                    title = "Diario de Salud",
                    subtitle = "Ver registro de síntomas",
                    onClick = { 
                        val intent = Intent(context, io.orabel.orabelandroid.ui.screens.health.HealthDiaryActivity::class.java)
                        context.startActivity(intent)
                        showSettingsSheet = false
                    },
                    iconColor = Color(0xFF34C759), // Green
                    bgColor = Color(0xFF34C759).copy(alpha = 0.1f)
                )

                Divider(modifier = Modifier.padding(start = 56.dp))

                // Perfil Médico
                ProfileMenuItem(
                    icon = Icons.Default.MedicalServices,
                    title = "Perfil Médico",
                    subtitle = "Información médica personal",
                    onClick = { 
                        val intent = Intent(context, io.orabel.orabelandroid.ui.screens.profile.MedicalProfileActivity::class.java)
                        context.startActivity(intent)
                        showSettingsSheet = false
                    },
                    iconColor = Color(0xFFFF3B30), // Red
                    bgColor = Color(0xFFFF3B30).copy(alpha = 0.1f)
                )

                Divider(modifier = Modifier.padding(start = 56.dp))

                // Configuración General
                ProfileMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Configuración general",
                    subtitle = "Preferencias de la aplicación",
                    onClick = { /* TODO */ },
                    iconColor = Color(0xFF8E8E93), // Gray
                    bgColor = Color(0xFF8E8E93).copy(alpha = 0.1f)
                )
                
                Divider(modifier = Modifier.padding(start = 56.dp))
                
                // Tema de la App
                ProfileMenuItem(
                    icon = Icons.Default.DarkMode,
                    title = "Tema de la App",
                    subtitle = getThemeModeDescription(context),
                    onClick = { showThemeDialog = true; showSettingsSheet = false },
                    iconColor = MaterialTheme.colorScheme.primary,
                    bgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
                
                Divider(modifier = Modifier.padding(start = 56.dp))
                
                // API Key
                ProfileMenuItem(
                    icon = Icons.Default.Key,
                    title = "API Key de Gemini",
                    subtitle = if (currentApiKey.isBlank()) "Usar predeterminado" else "Personalizada",
                    onClick = { showApiKeyDialog = true; showSettingsSheet = false },
                    iconColor = Color(0xFFFFCC00), // Yellow
                    bgColor = Color(0xFFFFCC00).copy(alpha = 0.1f)
                )
                
                Divider(modifier = Modifier.padding(start = 56.dp))

                // Soporte
                ProfileMenuItem(
                    icon = Icons.Default.Help,
                    title = "Ayuda y soporte",
                    subtitle = "Centro de ayuda",
                    onClick = { /* TODO */ },
                    iconColor = Color(0xFF007AFF), // Blue
                    bgColor = Color(0xFF007AFF).copy(alpha = 0.1f)
                )

                // Botón Cerrar Sesión (Siempre visible si hay usuario)
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                ProfileMenuItem(
                    icon = Icons.Default.Logout,
                    title = "Cerrar sesión",
                    subtitle = "Salir de tu cuenta",
                    onClick = { showSignOutDialog = true; showSettingsSheet = false },
                    iconColor = Color.Red,
                    bgColor = Color.Red.copy(alpha = 0.1f)
                )
            }
        }
    }

    // ================= USER DATA DIALOG (Functional) =================
    if (showUserDataDialog) {
        AlertDialog(
            onDismissRequest = { showUserDataDialog = false },
            title = { Text("Datos del Usuario") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val userData = fullProfile
                    if (userData != null) {
                        UserDataRow("Nombre", userData.displayName ?: "No definido")
                        UserDataRow("Usuario", userData.username ?: "No definido")
                        UserDataRow("Bio", userData.bio ?: "Sin biografía")
                        UserDataRow("País", userData.pais ?: "No definido")
                        UserDataRow("Ocupación", userData.occupation ?: "No definido")
                        UserDataRow("Estado", userData.estadoRegion ?: "No definido")
                    } else {
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUserDataDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    // ================= DIALOGS =================
    if (showThemeDialog) {
        val themePrefs = context.getSharedPreferences("theme_settings", android.content.Context.MODE_PRIVATE)
        var selectedMode by remember { 
            mutableStateOf(themePrefs.getString(io.orabel.orabelandroid.ui.theme.ThemeManager.KEY_THEME_MODE, 
                io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_AUTO_TIME) ?: io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_AUTO_TIME)
        }
        
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Configuración de Tema") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeOption("Automático", "Según hora", selectedMode == io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_AUTO_TIME) { selectedMode = io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_AUTO_TIME }
                    ThemeOption("Sistema", "Del dispositivo", selectedMode == io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_SYSTEM) { selectedMode = io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_SYSTEM }
                    ThemeOption("Oscuro", "Siempre oscuro", selectedMode == io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_ALWAYS_DARK) { selectedMode = io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_ALWAYS_DARK }
                    ThemeOption("Claro", "Siempre claro", selectedMode == io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_ALWAYS_LIGHT) { selectedMode = io.orabel.orabelandroid.ui.theme.ThemeManager.MODE_ALWAYS_LIGHT }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    themePrefs.edit().putString(io.orabel.orabelandroid.ui.theme.ThemeManager.KEY_THEME_MODE, selectedMode).apply()
                    viewModel.setThemeMode(selectedMode)
                    showThemeDialog = false
                }) { Text("Aplicar") }
            },
            dismissButton = { TextButton(onClick = { showThemeDialog = false }) { Text("Cancelar") } }
        )
    }
    
    // API KEY DIALOG (Simplified)
    if (showApiKeyDialog) {
        var apiKeyInput by remember { mutableStateOf(currentApiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("API Key") },
            text = { OutlinedTextField(value = apiKeyInput, onValueChange = { apiKeyInput = it }, label = { Text("Key") }) },
            confirmButton = {
                TextButton(onClick = {
                    context.getSharedPreferences("gemini_settings", android.content.Context.MODE_PRIVATE)
                        .edit().putString("user_api_key", apiKeyInput.trim()).apply()
                    currentApiKey = apiKeyInput.trim()
                    showApiKeyDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showApiKeyDialog = false }) { Text("Cancelar") } }
        )
    }

    // SIGN OUT DIALOG
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Seguro que quieres salir?") },
            confirmButton = {
                TextButton(onClick = { showSignOutDialog = false; viewModel.signOut { onSignOut() } }) { Text("Salir") }
            },
            dismissButton = { TextButton(onClick = { showSignOutDialog = false }) { Text("No") } }
        )
    }
}

// ================= COMPOSABLES AUXILIARES =================

@Composable
private fun UserDataRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProfileStatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
    }
}

@Composable
private fun TabItem(icon: ImageVector, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clickable(onClick = onClick).height(48.dp), contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else Color.Gray)
        if (selected) Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
fun ThemeOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
fun getThemeModeDescription(context: android.content.Context): String {
    // ... misma lógica simplificada ...
    return "Configurar tema"
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector, 
    title: String, 
    subtitle: String, 
    onClick: () -> Unit,
    iconColor: Color = PrimaryColor,
    bgColor: Color = PrimaryColor.copy(alpha = 0.1f)
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(bgColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontWeight = FontWeight.Medium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}
