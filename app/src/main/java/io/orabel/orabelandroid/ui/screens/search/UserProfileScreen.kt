package io.orabel.orabelandroid.ui.screens.search

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.orabel.orabelandroid.data.social.*
import io.orabel.orabelandroid.ui.screens.search.components.AvatarView
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    user: ProfileDTO,
    onBack: () -> Unit,
    onMessageClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository.getInstance(context) }
    
    var relationshipStatus by remember { mutableStateOf("none") }
    var isLoadingAction by remember { mutableStateOf(false) }
    
    // Estado para el perfil completo
    var fullProfile by remember { mutableStateOf(user) }
    var isLoadingProfile by remember { mutableStateOf(false) }
    
    // Stats & Data
    var userStats by remember { mutableStateOf(UserStatsDTO()) }
    var userPosts by remember { mutableStateOf<List<PostDTO>>(emptyList()) }
    var followersList by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Posts, 1: Videos

    // Cargar perfil completo si solo tenemos el ID
    LaunchedEffect(user.id) {
        // Si el perfil no tiene datos completos (solo ID), cargar desde la BD
        if (user.username == null || user.avatarUrl == null) {
            isLoadingProfile = true
            launch {
                repository.fetchUserProfileById(user.id)?.let { completeProfile ->
                    fullProfile = completeProfile
                }
                isLoadingProfile = false
            }
        }
    }

    // Cargar datos
    LaunchedEffect(user.id) {
        // 1. Relationship
        launch { relationshipStatus = repository.checkRelationshipStatus(user.id) }
        // 2. Stats
        launch { userStats = repository.countUserStats(user.id) }
        // 3. Posts
        launch { userPosts = repository.fetchUserPosts(user.id) }
        // 4. Followers (Social Proof)
        launch { followersList = repository.fetchFollowersOf(user.id) }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fullProfile.username ?: "Perfil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onMessageClick) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Mensaje")
                    }
                }
            )
        }
    ) { paddingValues ->
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // ================= HEADER SECTION (Full Width) =================
            item(span = { GridItemSpan(3) }) {
                Column {
                    // Banner & Avatar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        // Banner
                        if (!fullProfile.bannerUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = fullProfile.bannerUrl,
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
                        
                        // Avatar superpuesto (moved down slightly from banner bottom)
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
                                    .background(MaterialTheme.colorScheme.background) // Border effect
                                    .padding(4.dp)
                            ) {
                                AvatarView(
                                    url = fullProfile.avatarUrl,
                                    name = fullProfile.username,
                                    size = 100
                                )
                            }
                        }
                    }
                    
                    // Espacio para avatar
                    Spacer(modifier = Modifier.height(44.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // User Info
                        Text(
                            text = fullProfile.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        fullProfile.occupation?.takeIf { it.isNotBlank() }?.let { occupation ->
                            Text(
                                text = occupation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        fullProfile.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = bio,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        if (!fullProfile.pais.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📍 ${fullProfile.estadoRegion ?: ""}, ${fullProfile.pais}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
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
                            ProfileStatItem(count = userStats.friends, label = "AMIGOS")
                        }
                        
                        // Followed By
                        if (followersList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            FollowedBySection(followersList)
                        }

                        // Actions
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Botón de Seguir
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoadingAction = true
                                        val isSiguiendo = relationshipStatus == "siguiendo" || relationshipStatus == "amigos_siguiendo"
                                        if (isSiguiendo) {
                                            repository.unfollowUser(user.id)
                                            relationshipStatus = repository.checkRelationshipStatus(user.id)
                                        } else {
                                            repository.followUser(user.id)
                                            relationshipStatus = repository.checkRelationshipStatus(user.id)
                                        }
                                        // Recargar stats después de seguir/dejar de seguir
                                        userStats = repository.countUserStats(user.id)
                                        isLoadingAction = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isLoadingAction,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (relationshipStatus == "siguiendo" || relationshipStatus == "amigos_siguiendo") 
                                        MaterialTheme.colorScheme.surfaceVariant 
                                    else OrabelPrimary
                                )
                            ) {
                                if (isLoadingAction) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (relationshipStatus == "siguiendo" || relationshipStatus == "amigos_siguiendo") "Siguiendo" else "Seguir",
                                        color = if (relationshipStatus == "siguiendo" || relationshipStatus == "amigos_siguiendo") 
                                            MaterialTheme.colorScheme.onSurface 
                                        else Color.White
                                    )
                                }
                            }
                            
                            // Botón de Amistad (3 estados)
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isLoadingAction = true
                                        when (relationshipStatus) {
                                            "none", "siguiendo" -> {
                                                // Enviar solicitud de amistad
                                                repository.sendFriendRequest(user.id)
                                                relationshipStatus = repository.checkRelationshipStatus(user.id)
                                            }
                                            "solicitud_enviada" -> {
                                                // Cancelar solicitud enviada
                                                val requests = repository.fetchOutgoingFriendRequests()
                                                val requestToCancel = requests.find { it.user.id == user.id }
                                                requestToCancel?.let {
                                                    repository.cancelFriendRequest(it.connectionId)
                                                    relationshipStatus = repository.checkRelationshipStatus(user.id)
                                                    // Recargar stats después de cancelar solicitud
                                                    userStats = repository.countUserStats(user.id)
                                                }
                                            }
                                            "solicitud_recibida" -> {
                                                // Aceptar solicitud recibida
                                                android.util.Log.d("UserProfileScreen", "🔵 Accepting friend request from user: ${user.id}")
                                                val requests = repository.fetchIncomingFriendRequests()
                                                android.util.Log.d("UserProfileScreen", "🔵 Found ${requests.size} incoming requests")
                                                val requestToAccept = requests.find { it.user.id == user.id }
                                                if (requestToAccept != null) {
                                                    android.util.Log.d("UserProfileScreen", "🔵 Found matching request with connectionId: ${requestToAccept.connectionId}")
                                                    val result = repository.updateConnectionStatus(requestToAccept.connectionId, "amigos")
                                                    if (result.isSuccess) {
                                                        android.util.Log.d("UserProfileScreen", "✅ Successfully accepted friend request")
                                                    } else {
                                                        android.util.Log.e("UserProfileScreen", "❌ Failed to accept friend request: ${result.exceptionOrNull()?.message}")
                                                    }
                                                    relationshipStatus = repository.checkRelationshipStatus(user.id)
                                                    android.util.Log.d("UserProfileScreen", "🔵 New relationship status: $relationshipStatus")
                                                    // Recargar stats después de aceptar amistad
                                                    userStats = repository.countUserStats(user.id)
                                                } else {
                                                    android.util.Log.e("UserProfileScreen", "❌ No matching request found for user: ${user.id}")
                                                }
                                            }
                                            "amigos" -> {
                                                // Ya son amigos, no hacer nada o mostrar opción de eliminar
                                            }
                                            "amigos_siguiendo" -> {
                                                // Ya son amigos, no hacer nada o mostrar opción de eliminar
                                            }
                                        }
                                        isLoadingAction = false
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isLoadingAction && relationshipStatus != "amigos" && relationshipStatus != "amigos_siguiendo",
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = when (relationshipStatus) {
                                        "amigos", "amigos_siguiendo" -> OrabelPrimary.copy(alpha = 0.1f)
                                        else -> Color.Transparent
                                    }
                                )
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isLoadingAction) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = when (relationshipStatus) {
                                                "amigos", "amigos_siguiendo" -> Icons.Default.People
                                                "solicitud_enviada" -> Icons.Default.HourglassEmpty
                                                "solicitud_recibida" -> Icons.Default.PersonAdd
                                                else -> Icons.Default.PersonAdd
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when (relationshipStatus) {
                                                "amigos", "amigos_siguiendo" -> "Amigos"
                                                "solicitud_enviada" -> "Pendiente"
                                                "solicitud_recibida" -> "Aceptar"
                                                else -> "Agregar"
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Botón de Mensaje (separado)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onMessageClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Enviar Mensaje")
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
            } // End of Header Item

            // ================= GRID CONTENT =================
            if (userPosts.isEmpty()) {
                 item(span = { GridItemSpan(3) }) {
                     Box(
                         modifier = Modifier
                             .fillMaxWidth()
                             .padding(40.dp),
                         contentAlignment = Alignment.Center
                     ) {
                         Column(horizontalAlignment = Alignment.CenterHorizontally) {
                             Icon(
                                 imageVector = Icons.Default.ImageNotSupported,
                                 contentDescription = null,
                                 modifier = Modifier.size(48.dp),
                                 tint = Color.Gray
                             )
                             Spacer(modifier = Modifier.height(8.dp))
                             Text("No hay publicaciones aún", color = Color.Gray)
                         }
                     }
                 }
            } else {
                items(userPosts) { post ->
                    // Just show square thumbnails
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
                                 modifier = Modifier
                                     .align(Alignment.TopEnd)
                                     .padding(4.dp)
                                     .size(20.dp)
                             )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun FollowedBySection(followers: List<ProfileDTO>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Avatars stack
        Box(modifier = Modifier.width((24 + (followers.take(3).size - 1) * 16).dp)) {
            followers.take(3).forEachIndexed { index, follower ->
                Box(
                    modifier = Modifier
                        .padding(start = (index * 16).dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .background(Color.Gray)
                ) {
                    AvatarView(url = follower.avatarUrl, name = follower.username, size = 24)
                }
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        val first = followers.firstOrNull()
        if (first != null) {
            Text(
                text = buildString {
                    append("Seguido por ")
                    append(first.username ?: "Usuario")
                    if (followers.size > 1) {
                        append(" y ${followers.size - 1} más")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else Color.Gray
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
