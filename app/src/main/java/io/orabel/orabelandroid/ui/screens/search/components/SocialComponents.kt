package io.orabel.orabelandroid.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.orabel.orabelandroid.data.social.ProfileDTO
import io.orabel.orabelandroid.data.social.PostDTO
import io.orabel.orabelandroid.ui.theme.OrabelPrimary

// ========== AVATAR VIEW ==========

@Composable
fun AvatarView(
    url: String?,
    name: String?,
    size: Int = 48,
    modifier: Modifier = Modifier
) {
    val initials = name?.take(1)?.uppercase() ?: "U"
    
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(OrabelPrimary.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = "Avatar de $name",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = OrabelPrimary
                )
            )
        }
    }
}

// ========== TARJETA DE USUARIO SUGERIDO ==========

@Composable
fun SuggestedUserCard(
    user: ProfileDTO,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AvatarView(
                url = user.avatarUrl,
                name = user.username,
                size = 60
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = user.username ?: "Usuario",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            if (!user.nombre.isNullOrBlank()) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrabelPrimary
                ),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text(
                    text = "Ver perfil",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// ========== TARJETA DE PUBLICACIÓN ==========

@Composable
fun SocialPostCard(
    post: PostDTO,
    onLikeClick: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Encabezado con autor
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvatarView(
                    url = post.author?.avatarUrl,
                    name = post.author?.username,
                    size = 40,
                    modifier = Modifier.clickable(onClick = onProfileClick)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.author?.username ?: "Usuario",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "2h", // Placeholder iOS style
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                
                // Botón de seguir al lado del nombre (como en iOS)
                Text(
                    text = "Seguir",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = OrabelPrimary,
                    modifier = Modifier
                        .clickable { /* TODO: Follow */ }
                        .padding(horizontal = 8.dp)
                )

                IconButton(onClick = { /* Más opciones */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz, // Más iOS que MoreVert
                        contentDescription = "Más opciones"
                    )
                }
            }
            
            // Contenido
            // Contenido
            post.content?.let { content ->
                if (content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Media (Video o Imagen)
            val videoUrl = post.videoUrl
            
            if (!videoUrl.isNullOrBlank()) {
                var isPlaying by remember { mutableStateOf(false) }
                
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .clickable { isPlaying = !isPlaying },
                    contentAlignment = Alignment.Center
                ) {
                    ReelVideoPlayer(
                        videoUrl = videoUrl,
                        isPlaying = isPlaying, // Controlado por estado local
                        resizeMode = 0 // FIT para posts
                    )
                    
                    if (!isPlaying) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir",
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(8.dp),
                            tint = Color.White
                        )
                    }
                }
            } else if (!post.imageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "Imagen de la publicación",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Acciones
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onLikeClick) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder, // Outlined por defecto como iOS
                            contentDescription = "Me gusta",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text("${post.likesCount}", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    IconButton(onClick = onCommentClick) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline, // bubble.right en iOS
                            contentDescription = "Comentarios",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text("${post.commentsCount}", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    IconButton(onClick = { /* Compartir */ }) {
                        Icon(
                            imageVector = Icons.Default.Send, // paperplane en iOS
                            contentDescription = "Enviar",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                
                IconButton(onClick = { /* Guardar */ }) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder, // bookmark en iOS
                        contentDescription = "Guardar",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

// ========== FILA DE SOLICITUD DE AMISTAD ==========

@Composable
fun FriendRequestRow(
    user: ProfileDTO,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        AvatarView(
            url = user.avatarUrl,
            name = user.username,
            size = 48
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username ?: "Usuario",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Quiere ser tu amigo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Aceptar", fontSize = 12.sp)
                }
                
                OutlinedButton(
                    onClick = onReject,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text("Rechazar", fontSize = 12.sp)
                }
            }
        }
    }
}

// ========== FILA DE USUARIO (SIGUIENDO/SEGUIDOR) ==========

@Composable
fun UserRow(
    user: ProfileDTO,
    subtitle: String,
    actionButton: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(
            url = user.avatarUrl,
            name = user.username,
            size = 48
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username ?: "Usuario",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        actionButton?.invoke()
    }
}

// ========== BURBUJA DE MENSAJE ==========

@Composable
fun MessageBubble(
    content: String,
    isFromMe: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromMe) 16.dp else 4.dp,
                bottomEnd = if (isFromMe) 4.dp else 16.dp
            ),
            color = if (isFromMe) OrabelPrimary else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = content,
                modifier = Modifier
                    .padding(12.dp)
                    .widthIn(max = 250.dp),
                color = if (isFromMe) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ========== SECCIÓN DE STORIES ==========

@Composable
fun StoriesSection(
    users: List<ProfileDTO>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Stories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(users.size) { index ->
                val user = users[index]
                StoryItem(user = user)
            }
        }
    }
}

@Composable
fun StoryItem(
    user: ProfileDTO,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(OrabelPrimary, OrabelPrimary.copy(alpha = 0.6f))
                    ),
                    shape = CircleShape
                )
                .padding(3.dp)
        ) {
            AvatarView(
                url = user.avatarUrl,
                name = user.username,
                size = 62,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = user.username?.take(10) ?: "Usuario",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ========== INDICADOR DE ESTADO (DOBLE CHECK) ==========

@Composable
fun StatusIndicator(status: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
        if (status == "read") {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Leído",
                tint = Color(0xFF007AFF), // iOS Blue
                modifier = Modifier.size(16.dp)
            )
        } else if (status == "delivered") {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Entregado",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        } else {
            // Sent or default
            Icon(
                imageVector = Icons.Default.Check, // Or Done if available
                contentDescription = "Enviado",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
