package io.orabel.orabelandroid.ui.screens.search

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import io.orabel.orabelandroid.data.social.*
import io.orabel.orabelandroid.ui.screens.search.tabs.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.util.Log
import io.orabel.orabelandroid.ui.theme.OrabelPrimary

/**
 * Pantalla principal del Feed Social con 5 pestañas (For You, Reels, Mi Red, Grupos, Mensajes).
 * Copia exacta de SocialFeedView.swift de iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeedScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onOpenMisDatos: () -> Unit,
    onOpenUserProfile: (ProfileDTO) -> Unit = {},
    // onOpenFriendsDetail: () -> Unit = {}, // Handled internally now
    onOpenDirectChat: (ProfileDTO) -> Unit = {},
    selectedBottomNav: Int,
    onBottomNavSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository.getInstance(context) }
    
    // Estado de la pestaña seleccionada (preservado durante la navegación)
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    
    // Estado de navegación interna (Friends Detail)
    var showFriendsDetail by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showCreatePostSheet by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupDescription by remember { mutableStateOf("") }
    
    // UI State for tabs contentss
    BackHandler(enabled = showFriendsDetail) {
        showFriendsDetail = false
    }
    
    // Filtros/Tabs copiados de iOS: "For You", "Reels", "Mi Red", "Grupos", "Mensajes"
    // Nota: Reordenamos para coincidir con la UI de iconos:
    // 0: For You (Sparkles/AutoAwesome)
    // 1: Reels (Video)
    // 2: Mi Red (Person/People)
    // 3: Grupos (Groups) - NUEVO
    // 4: Mensajes (Message)
    val tabs = listOf(
        TabInfo("For You", Icons.Default.AutoAwesome),
        TabInfo("Reels", Icons.Default.VideoLibrary),
        TabInfo("Mi Red", Icons.Default.People),
        TabInfo("Grupos", Icons.Default.Groups),
        TabInfo("Mensajes", Icons.Default.Message)
    )
    
    // Estados de datos
    var posts by remember { mutableStateOf<List<PostDTO>>(emptyList()) }
    var reels by remember { mutableStateOf<List<PostDTO>>(emptyList()) }
    var suggestedUsers by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    var inboxThreads by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    var myGroups by remember { mutableStateOf<List<GroupDTO>>(emptyList()) }
    var storiesGroups by remember { mutableStateOf<List<UserStoriesGroup>>(emptyList()) }
    
    // Estados de datos para Mi Red (Friends View)
    var pendingRequests by remember { mutableStateOf<List<FriendRequestWithUser>>(emptyList()) }
    var outgoingRequests by remember { mutableStateOf<List<FriendRequestWithUser>>(emptyList()) }
    var followingUsers by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    var followers by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    var mutualFriends by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    
    var searchResults by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingTab by remember { mutableStateOf(false) }
    
    // Current user profile for stories
    val currentUserProfile by repository.currentUserProfile.collectAsState()
    
    // Navigation state for stories
    var showStoriesViewer by remember { mutableStateOf(false) }
    var selectedStoriesGroup by remember { mutableStateOf<UserStoriesGroup?>(null) }
    var showUploadStory by remember { mutableStateOf(false) }

    // Function to fetch groups
    fun fetchMyGroups() {
        scope.launch {
            try {
                myGroups = repository.fetchMyGroups()
            } catch (e: Exception) {
                android.util.Log.e("SocialFeedScreen", "Error fetching groups: ${e.message}")
            }
        }
    }
    
    // Cargar datos al cambiar de pestaña
    LaunchedEffect(selectedTab) {
        isLoadingTab = true
        try {
            when (selectedTab) {
                0 -> { // For You
                    posts = repository.fetchPosts()
                    if (suggestedUsers.isEmpty()) {
                        suggestedUsers = repository.fetchAvailableUsers()
                    }
                }
                1 -> { // Reels
                    reels = repository.fetchReels()
                }
                2 -> { // Mi Red
                    if (suggestedUsers.isEmpty()) {
                        suggestedUsers = repository.fetchAvailableUsers()
                    }
                    // Fetch stories
                    launch { storiesGroups = repository.fetchStories() }
                    // Fetch all friends data concurrently
                    launch { pendingRequests = repository.fetchIncomingFriendRequests() }
                    launch { outgoingRequests = repository.fetchOutgoingFriendRequests() }
                    launch { followingUsers = repository.fetchFollowingUsers() }
                    launch { followers = repository.fetchFollowers() }
                    launch { mutualFriends = repository.fetchMutualFriends() }
                }
                3 -> { // Grupos
                    fetchMyGroups()
                }
                4 -> { // Mensajes
                    inboxThreads = repository.fetchInbox()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SocialFeedScreen", "Error cargando datos: ${e.message}")
        }
        isLoadingTab = false
    }
    
    // Load current user profile
    LaunchedEffect(Unit) {
        repository.fetchCurrentUserProfile()
    }
    
    // Función de búsqueda
    fun performSearch() {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return
        }
        scope.launch {
            isSearching = true
            try {
                searchResults = repository.searchProfiles(searchQuery)
            } catch (e: Exception) {
                android.util.Log.e("SocialFeedScreen", "Error en búsqueda: ${e.message}")
            }
            isSearching = false
        }
    }
    
    // Function to handle group creation
    fun crearGrupo() {
        if (newGroupName.isBlank()) return
        scope.launch {
            try {
                repository.createGroup(newGroupName, newGroupDescription, "público")
                showCreateGroupDialog = false
                newGroupName = ""
                newGroupDescription = ""
                // Refresh groups
                fetchMyGroups()
            } catch (e: Exception) {
                android.util.Log.e("SocialFeedScreen", "Error creating group: ${e.message}")
            }
        }
    }
    
    // Refresh function for Friends Detail
    fun refreshFriendsData() {
        scope.launch {
            isLoadingTab = true
            try {
                pendingRequests = repository.fetchIncomingFriendRequests()
                outgoingRequests = repository.fetchOutgoingFriendRequests()
                followingUsers = repository.fetchFollowingUsers()
                followers = repository.fetchFollowers()
                mutualFriends = repository.fetchMutualFriends()
                 if (suggestedUsers.isEmpty()) {
                    suggestedUsers = repository.fetchAvailableUsers()
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialFeedScreen", "Error updating friends data: ${e.message}")
            }
            isLoadingTab = false
        }
    }
    
    if (showFriendsDetail) {
        // Show Friends Detail Screen
        FriendsDetailScreen(
            pendingRequests = pendingRequests,
            outgoingRequests = outgoingRequests,
            followingUsers = followingUsers,
            followers = followers,
            mutualFriends = mutualFriends,
            suggestedUsers = suggestedUsers,
            isLoading = isLoadingTab,
            onRefresh = { refreshFriendsData() },
            onUserClick = onOpenUserProfile,
            onAcceptRequest = { id ->
                scope.launch {
                    try {
                        repository.updateConnectionStatus(id, "amigos")
                        refreshFriendsData()
                    } catch (e: Exception) {
                        Log.e("SocialFeedScreen", "Error accepting request: ${e.message}")
                    }
                }
            },
            onRejectRequest = { id ->
                scope.launch {
                    try {
                        repository.updateConnectionStatus(id, "rechazada")
                        refreshFriendsData()
                    } catch (e: Exception) {
                        Log.e("SocialFeedScreen", "Error rejecting request: ${e.message}")
                    }
                }
            },
            onCancelRequest = { id ->
                scope.launch {
                    try {
                        repository.cancelFriendRequest(id)
                        refreshFriendsData()
                    } catch (e: Exception) {
                        Log.e("SocialFeedScreen", "Error cancelling request: ${e.message}")
                    }
                }
            },
            onFollowUser = { user ->
                scope.launch {
                    try {
                        repository.followUser(user.id)
                        refreshFriendsData()
                    } catch (e: Exception) {
                        Log.e("SocialFeedScreen", "Error following user: ${e.message}")
                    }
                }
            },
            onUnfollowUser = { user ->
                scope.launch {
                    try {
                        repository.unfollowUser(user.id)
                        refreshFriendsData()
                    } catch (e: Exception) {
                        Log.e("SocialFeedScreen", "Error unfollowing user: ${e.message}")
                    }
                }
            },
            onRemoveFriend = { user ->
                scope.launch {
                    try {
                        repository.removeFriend(user.id)
                        refreshFriendsData()
                    } catch (e: Exception) {
                        Log.e("SocialFeedScreen", "Error removing friend: ${e.message}")
                    }
                }
            },
            onBack = { showFriendsDetail = false },
            modifier = modifier
        )
    } else {
        // Main Social Feed Content
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header con pestañas de iconos y botón Mis Datos
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column {
                    // Fila superior: Título
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Social",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Botón "Mis Datos"
                        FilledTonalButton(
                            onClick = onOpenMisDatos,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mis Datos", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    
                    // Barra de búsqueda (solo visible en Mi Red como en iOS)
                    if (selectedTab == 2) { // Mi Red
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar personas...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { 
                                        searchQuery = "" 
                                        isSearching = false
                                        searchResults = emptyList()
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Borrar")
                                    }
                                }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp)
                        )
                    }
                    
                    // Pestañas de iconos
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            TabIcon(
                                icon = tab.icon,
                                label = tab.label,
                                isSelected = selectedTab == index,
                                onClick = { selectedTab = index }
                            )
                        }
                    }
                }
            }
            
            // Contenido de la pestaña seleccionada
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> ForYouTabScreen(
                        posts = posts,
                        suggestedUsers = suggestedUsers,
                        isLoading = isLoadingTab,
                        onUserClick = { user -> onOpenUserProfile(user) }
                    )
                    1 -> ReelsTabScreen(
                        reels = reels,
                        isLoading = isLoadingTab,
                        onCreateReel = { 
                            showCreatePostSheet = true
                        },
                        onRefresh = {
                            scope.launch {
                                reels = repository.fetchReels(limit = 20)
                            }
                        },
                        onProfileClick = { userId -> 
                            // Create minimal ProfileDTO with just the ID
                            onOpenUserProfile(ProfileDTO(id = userId))
                        }
                    )
                    2 -> MiRedTabScreen(
                        suggestedUsers = suggestedUsers,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        searchQuery = searchQuery,
                        isLoading = isLoadingTab,
                        onSearchQueryChange = { 
                            searchQuery = it 
                            if (it.isNotEmpty()) performSearch()
                        },
                        onSearch = { performSearch() },
                        onUserClick = { user -> onOpenUserProfile(user) },
                        onViewNetwork = { showFriendsDetail = true },
                        // Stories parameters
                        storiesGroups = storiesGroups,
                        currentUserId = repository.getCurrentUserId(),
                        currentUserAvatar = currentUserProfile?.avatarUrl,
                        currentUserName = currentUserProfile?.displayName,
                        onAddStoryClick = { showUploadStory = true },
                        onStoryClick = { group ->
                            selectedStoriesGroup = group
                            showStoriesViewer = true
                        }
                    )
                    3 -> GruposTabScreen(
                        grupos = myGroups,
                        isLoading = isLoadingTab,
                        onGrupoClick = { /* TODO: Detalles de grupo */ },
                        onCrearGrupo = { crearGrupo() }
                    )
                    4 -> MensajesTabScreen(
                        threads = inboxThreads,
                        isLoading = isLoadingTab,
                        onThreadClick = { user -> onOpenDirectChat(user) }
                    )
                }
                
                // FAB para crear publicación (solo en For You)
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = { showCreatePostSheet = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        containerColor = OrabelPrimary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Crear publicación"
                        )
                    }
                }
            }
        }
    }
    
    // Sheet de creación de post
    if (showCreatePostSheet) {
        CreatePostSheet(
            onDismiss = { showCreatePostSheet = false },
            onPostCreated = {
                showCreatePostSheet = false
                scope.launch {
                    // Refrescar el feed apropiado según la tab activa
                    if (selectedTab == 1) {
                        reels = repository.fetchReels(limit = 20)
                    } else {
                        posts = repository.fetchPosts(limit = 20)
                    }
                }
            },
            initialContentType = if (selectedTab == 1) "reel" else "post"
        )
    }

    // Diálogo de Creación de Grupo
    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Crear Nuevo Grupo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Nombre del grupo") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newGroupDescription,
                        onValueChange = { newGroupDescription = it },
                        label = { Text("Descripción (opcional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { crearGrupo() },
                    enabled = newGroupName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary)
                ) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Upload Story Screen
    if (showUploadStory) {
        io.orabel.orabelandroid.ui.screens.stories.UploadStoryScreen(
            repository = repository,
            onBack = { showUploadStory = false },
            onUploadSuccess = {
                showUploadStory = false
                // Refresh stories
                scope.launch {
                    storiesGroups = repository.fetchStories()
                }
            }
        )
    }
    
    // Stories Viewer
    if (showStoriesViewer && selectedStoriesGroup != null) {
        io.orabel.orabelandroid.ui.screens.stories.StoriesViewerScreen(
            storiesGroup = selectedStoriesGroup!!,
            repository = repository,
            onClose = {
                showStoriesViewer = false
                selectedStoriesGroup = null
            },
            onNavigateNext = {
                // Find next group
                val currentIndex = storiesGroups.indexOf(selectedStoriesGroup)
                if (currentIndex < storiesGroups.size - 1) {
                    selectedStoriesGroup = storiesGroups[currentIndex + 1]
                } else {
                    showStoriesViewer = false
                    selectedStoriesGroup = null
                }
            },
            onNavigatePrevious = {
                // Find previous group
                val currentIndex = storiesGroups.indexOf(selectedStoriesGroup)
                if (currentIndex > 0) {
                    selectedStoriesGroup = storiesGroups[currentIndex - 1]
                } else {
                    showStoriesViewer = false
                    selectedStoriesGroup = null
                }
            }
        )
    }
}

@Composable
private fun TabIcon(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) OrabelPrimary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) OrabelPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(26.dp)
        )
    }
}

private data class TabInfo(
    val label: String,
    val icon: ImageVector
)

/**
 * Bottom sheet para crear un nuevo post
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePostSheet(
    onDismiss: () -> Unit,
    onPostCreated: () -> Unit,
    initialContentType: String = "post" // "post" o "reel"
) {
    val context = LocalContext.current
    val repository = remember { SocialRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var caption by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var inputCategory by remember { mutableStateOf("") }
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var contentType by remember { mutableStateOf(initialContentType) } // "post" o "reel"
    var isLoading by remember { mutableStateOf(false) }
    
    // Launcher para seleccionar imagen
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedMediaUri = uri
        if (uri != null) contentType = "post"
    }
    
    // Launcher para seleccionar video
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedMediaUri = uri
        if (uri != null) contentType = "reel"
    }
    
    fun createPost() {
        val isReel = contentType == "reel"
        if (caption.isBlank()) return
        if (inputCategory.isBlank()) return
        if (isReel && title.isBlank()) return
        
        scope.launch {
            isLoading = true
            try {
                val userId = repository.getCurrentUserUUID()?.toString()
                if (userId == null) {
                    Log.e("CreatePostSheet", "User ID is null")
                    isLoading = false
                    return@launch
                }

                var uploadedMediaUrl: String? = null
                
                if (selectedMediaUri != null) {
                    withContext(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(selectedMediaUri!!)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()
                        
                        if (bytes != null) {
                            val result = if (isReel) {
                                repository.uploadReelVideo(userId, bytes)
                            } else {
                                repository.uploadPostMedia(userId, bytes)
                            }
                            uploadedMediaUrl = result.getOrNull()
                        }
                    }
                }

                if (selectedMediaUri != null && uploadedMediaUrl == null) {
                     Log.e("CreatePostSheet", "Failed to upload media")
                     isLoading = false
                     return@launch
                }

                // URL por defecto si no se sube imagen (placeholder)
                val finalMediaUrl = uploadedMediaUrl 
                    ?: "https://tcjhnibhoplfslqzprgl.supabase.co/storage/v1/object/public/post_media/placeholder.png"
                
                val post = if (isReel) {
                    repository.createVideoPost(
                        caption = caption,
                        videoUrl = finalMediaUrl,
                        thumbnailUrl = null,
                        durationSeconds = null,
                        contentType = contentType,
                        title = title,
                        category = inputCategory
                    )
                } else {
                    repository.createPost(
                        caption = caption,
                        mediaUrl = finalMediaUrl,
                        mediaType = "image", // Siempre "image" para posts normales
                        contentType = contentType,
                        title = null,
                        category = inputCategory
                    )
                }

                if (post != null) {
                    Log.d("CreatePostSheet", "Post creado exitosamente: ${post.id}")
                    onPostCreated()
                } else {
                    Log.e("CreatePostSheet", "Post result is null")
                }
            } catch (e: Exception) {
                Log.e("CreatePostSheet", "Error creating post: ${e.message}", e)
            } finally {
                isLoading = false
            }
        }
    }
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (contentType == "reel") "Crear reel" else "Crear publicación",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // Selector de tipo de contenido
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = contentType == "post",
                    onClick = { contentType = "post" },
                    label = { Text("Post") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                )
                FilterChip(
                    selected = contentType == "reel",
                    onClick = { contentType = "reel" },
                    label = { Text("Reel") },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                )
            }
            
            // Preview de media seleccionada
            selectedMediaUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (contentType == "reel") {
                        // Preview de video (thumbnail o icono)
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .align(Alignment.Center),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    // Botón para remover media
                    IconButton(
                        onClick = { selectedMediaUri = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remover", tint = Color.White)
                    }
                }
            }
            
            // Botones para seleccionar media
            if (selectedMediaUri == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Foto")
                    }
                    OutlinedButton(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Video")
                    }
                }
            }
            
            // Título (solo para reels)
            if (contentType == "reel") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título *") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true
                )
            }
            
            // Caption/Descripción
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text(if (contentType == "reel") "Descripción *" else "¿Qué estás pensando? *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                enabled = !isLoading
            )
            
            // Categoría (para posts y reels)
            OutlinedTextField(
                value = inputCategory,
                onValueChange = { inputCategory = it },
                label = { Text("Categoría *") },
                placeholder = { Text("ej: comedia, educación, deportes") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true
            )
            
            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading
                ) {
                    Text("Cancelar")
                }
                
                Button(
                    onClick = { createPost() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && caption.isNotBlank() && inputCategory.isNotBlank() && (
                        if (contentType == "reel") title.isNotBlank() else true
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Publicar")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
