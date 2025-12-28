package io.orabel.orabelandroid.ui.screens.search.tabs

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.orabel.orabelandroid.data.social.PostDTO
import io.orabel.orabelandroid.data.social.SocialRepository
import io.orabel.orabelandroid.ui.screens.search.components.AvatarView
import io.orabel.orabelandroid.ui.screens.search.components.ReelVideoPlayer
import io.orabel.orabelandroid.ui.screens.social.CommentsBottomSheet
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pantalla "Reels" - Feed de videos en formato vertical a pantalla completa.
 */
@Composable
fun ReelsTabScreen(
    reels: List<PostDTO>,
    isLoading: Boolean,
    onCreateReel: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onProfileClick: (UUID) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    } else if (reels.isEmpty()) {
        EmptyReelsMessage(onCreateReel = onCreateReel, modifier = modifier)
    } else {
        val pagerState = rememberPagerState(pageCount = { reels.size })
        
        VerticalPager(
            state = pagerState,
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) { page ->
            ReelItem(
                reel = reels[page],
                isPlaying = pagerState.currentPage == page,
                onRefresh = onRefresh,
                onCreateReel = onCreateReel,
                onProfileClick = onProfileClick
            )
        }
    }
}

@Composable
private fun EmptyReelsMessage(
    onCreateReel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color.White.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hay reels",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Los reels de tus amigos aparecerán aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onCreateReel,
            colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Crear Reel")
        }
    }
}

@Composable
private fun ReelItem(
    reel: PostDTO,
    isPlaying: Boolean,
    onRefresh: () -> Unit = {},
    onCreateReel: () -> Unit = {},
    onProfileClick: (UUID) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { SocialRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var isUserPaused by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }
    var likeCount by remember { mutableStateOf(reel.likesCount) }
    var commentCount by remember { mutableStateOf(reel.commentsCount) }
    var showComments by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var showEditReel by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    
    // Check if current user is owner
    val isReelOwner = remember(reel.creatorId) {
        val currentUserId = repository.getCurrentUserUUID()?.toString()
        currentUserId != null && reel.creatorId.toString().equals(currentUserId, ignoreCase = true)
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) isUserPaused = false
    }
    
    // Initialize like state - obtener reacción directamente de la API
    LaunchedEffect(reel.id) {
        val userReaction = repository.getUserReactionForPost(reel.id.toString())
        isLiked = userReaction != null // Si tiene reacción, está "liked"
    }

    val finalIsPlaying = isPlaying && !isUserPaused

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { isUserPaused = !isUserPaused },
        contentAlignment = Alignment.Center
    ) {
        // Video/Imagen de fondo
        val videoUrl = reel.videoUrl
        if (!videoUrl.isNullOrBlank()) {
            ReelVideoPlayer(
                videoUrl = videoUrl,
                isPlaying = finalIsPlaying,
                resizeMode = 4, // ZOOM para Reels pantalla completa
                modifier = Modifier.fillMaxSize()
            )
        } else if (!reel.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = reel.imageUrl,
                contentDescription = "Reel",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Gradient overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                    )
                )
        )

        if (isUserPaused) {
             Icon(
                 imageVector = Icons.Filled.PlayArrow,
                 contentDescription = "Reanudar",
                 modifier = Modifier
                     .size(80.dp)
                     .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                     .padding(20.dp),
                 tint = Color.White.copy(alpha = 0.7f)
             )
        }
        
        // Top controls (mute button)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 20.dp)
        ) {
            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (isMuted) "Activar sonido" else "Silenciar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        // Overlay con información
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            // Información del autor y contenido
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.clickable {
                            onProfileClick(reel.creatorId)
                        }
                    ) {
                        AvatarView(
                            url = reel.author?.avatarUrl,
                            name = reel.author?.username,
                            size = 40
                        )
                        Column {
                            // Construir nombre completo (nombre + apellido)
                            val nombre = reel.creator?.nombre ?: reel.author?.nombre
                            val apellido = reel.creator?.apellido ?: reel.author?.apellido
                            val nombreCompleto = buildString {
                                if (!nombre.isNullOrBlank()) {
                                    append(nombre)
                                    if (!apellido.isNullOrBlank()) {
                                        append(" ")
                                        append(apellido)
                                    }
                                }
                            }
                            
                            // Mostrar: 1) nombre completo, 2) username, 3) "Usuario"
                            val displayName = if (nombreCompleto.isNotBlank()) {
                                nombreCompleto
                            } else {
                                reel.creator?.username ?: reel.author?.username ?: "Usuario"
                            }
                            
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            // Si mostramos nombre completo, mostrar username debajo
                            if (nombreCompleto.isNotBlank()) {
                                val username = reel.creator?.username ?: reel.author?.username
                                if (!username.isNullOrBlank()) {
                                    Text(
                                        text = "@$username",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        // Follow button placeholder
                        Button(
                            onClick = { /* TODO: implement follow */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Seguir", fontSize = 12.sp, color = Color.White)
                        }
                    }
                    
                    reel.content?.let { content ->
                        if (content.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 3
                            )
                        }
                    }
                    
                    // Category badge
                    reel.category?.let { category ->
                        if (category.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = category,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                modifier = Modifier
                                    .background(OrabelPrimary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Acciones laterales (siguiendo iOS)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Create button
                    ReelActionButton(
                        icon = Icons.Default.CameraAlt,
                        count = null,
                        label = "Crear",
                        onClick = { onCreateReel() }
                    )
                    
                    // Like button
                    ReelActionButton(
                        icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        count = likeCount,
                        label = null,
                        tint = if (isLiked) Color.Red else Color.White,
                        onClick = {
                            scope.launch {
                                try {
                                    if (isLiked) {
                                        // Remove reaction
                                        val result = repository.removeReaction(reel.id.toString())
                                        if (result.isSuccess) {
                                            isLiked = false
                                            likeCount = maxOf(0, likeCount - 1)
                                            onRefresh()
                                        }
                                    } else {
                                        // Add reaction with heart emoji
                                        val result = repository.reactToPost(reel.id.toString(), "❤️")
                                        if (result.isSuccess) {
                                            isLiked = true
                                            likeCount += 1
                                            onRefresh()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ReelItem", "Error toggling like: ${e.message}")
                                }
                            }
                        }
                    )
                    
                    // Comment button
                    ReelActionButton(
                        icon = Icons.Default.ChatBubbleOutline,
                        count = commentCount,
                        label = null,
                        onClick = { showComments = true }
                    )
                    
                    // Share button
                    ReelActionButton(
                        icon = Icons.Default.Share,
                        count = null,
                        label = "Share",
                        onClick = { /* TODO: implement share */ }
                    )
                    
                    // Menu button (solo para el due\u00f1o del reel)
                    if (isReelOwner) {
                        var showMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "M\u00e1s opciones",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Edit, null)
                                            Text("Editar")
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        showEditReel = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                                        }
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteAlert = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Comments bottom sheet
    if (showComments) {
        CommentsBottomSheet(
            postId = reel.id.toString(),
            onDismiss = { showComments = false },
            onProfileClick = onProfileClick
        )
    }
    
    // Edit reel sheet
    if (showEditReel) {
        // TODO: Implement EditReelSheet similar to EditPostSheet
        showEditReel = false
    }
    
    // Delete confirmation dialog
    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("\u00bfEliminar reel?") },
            text = { Text("Esta acci\u00f3n no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val result = repository.deletePost(reel.id.toString())
                                if (result.isSuccess) {
                                    showDeleteAlert = false
                                    onRefresh()
                                }
                            } catch (e: Exception) {
                                Log.e("ReelItem", "Error deleting reel: ${e.message}")
                            }
                        }
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlert = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ReelActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int?,
    label: String? = null,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        if (count != null && count > 0) {
            Text(
                text = when {
                    count >= 1000000 -> "${count / 1000000}M"
                    count >= 1000 -> "${count / 1000}K"
                    else -> "$count"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        } else if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}
