package io.orabel.orabelandroid.ui.screens.social

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import io.orabel.orabelandroid.data.social.*
import io.orabel.orabelandroid.ui.screens.search.components.AvatarView
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Tarjeta de publicación completa siguiendo el estilo de iOS
 * Con soporte para:
 * - Reacciones con emojis (❤️, 😂, 🔥, etc.)
 * - Comentarios con hilos (respuestas anidadas)
 * - Edición y eliminación de posts
 * - Follow/Unfollow
 * - Animaciones y estilos idénticos a iOS
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedPostCard(
    post: PostDTO,
    onProfileClick: (UUID) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository.getInstance(context) }
    
    var showComments by remember { mutableStateOf(false) }
    var showReactionsList by remember { mutableStateOf(false) }
    var showEditPost by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var relationshipStatus by remember { mutableStateOf("none") }
    var isFollowingUser by remember { mutableStateOf(false) }
    
    // Optimistic reaction state
    var currentReaction by remember { mutableStateOf<String?>(null) }
    var optimisticReaction by remember { mutableStateOf<String?>(null) }
    
    val isPostOwner = remember(post.creatorId) {
        val currentUserId = repository.getCurrentUserUUID()?.toString()
        currentUserId != null && post.creatorId.toString().equals(currentUserId, ignoreCase = true)
    }
    
    // Initialize current reaction - obtener directamente de la API
    LaunchedEffect(post.id) {
        currentReaction = repository.getUserReactionForPost(post.id.toString())
    }
    
    // Check relationship status
    LaunchedEffect(post.creatorId) {
        if (!isPostOwner && !post.isAnonymous) {
            val status = repository.checkRelationshipStatus(post.creatorId)
            relationshipStatus = status
            isFollowingUser = status == "siguiendo" || status == "amigos"
        }
    }
    
    val displayReaction = optimisticReaction ?: currentReaction
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. HEADER
            PostHeader(
                post = post,
                isPostOwner = isPostOwner,
                relationshipStatus = relationshipStatus,
                isFollowing = isFollowingUser,
                onProfileClick = { onProfileClick(post.creatorId) },
                onFollowClick = {
                    scope.launch {
                        try {
                            if (isFollowingUser) {
                                // Unfollow
                                isFollowingUser = false // Optimistic update
                                val result = repository.unfollowUser(post.creatorId)
                                if (result.isSuccess) {
                                    relationshipStatus = "none"
                                } else {
                                    isFollowingUser = true // Revert on error
                                }
                            } else {
                                // Follow
                                isFollowingUser = true // Optimistic update
                                val result = repository.followUser(post.creatorId)
                                if (result.isSuccess) {
                                    relationshipStatus = "siguiendo"
                                } else {
                                    isFollowingUser = false // Revert on error
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("EnhancedPostCard", "Error toggling follow: ${e.message}")
                        }
                    }
                },
                onEditClick = { showEditPost = true },
                onDeleteClick = { showDeleteAlert = true }
            )
            
            // 2. CAPTION (antes del media como en iOS)
            if (!post.caption.isNullOrBlank()) {
                Text(
                    text = post.caption,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            
            // 3. MEDIA
            if (post.hasMedia) {
                if (post.isVideo) {
                    // Video player placeholder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                } else {
                    AsyncImage(
                        model = post.mediaUrl,
                        contentDescription = "Post media",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 500.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            
            // 4. STATS BAR (reacciones y comentarios)
            PostStatsBar(
                post = post,
                onReactionsClick = {
                    if (!post.postReactions.isNullOrEmpty()) {
                        showReactionsList = true
                    }
                },
                onCommentsClick = { showComments = true }
            )
            
            Divider(modifier = Modifier.padding(horizontal = 12.dp))
            
            // 5. ACTION BAR
            PostActionBar(
                currentReaction = displayReaction,
                reactionCount = post.reactionCount,
                commentsCount = post.commentsCount,
                onReactionSelect = { emoji ->
                    scope.launch {
                        val previousReaction = optimisticReaction ?: currentReaction
                        optimisticReaction = if (emoji == displayReaction) null else emoji
                        
                        try {
                            // Llamar API real
                            if (emoji == displayReaction) {
                                // Remover reacción
                                val result = repository.removeReaction(post.id.toString())
                                if (result.isFailure) {
                                    // Revertir optimistic update si falla
                                    optimisticReaction = previousReaction
                                } else {
                                    // Actualizar estado después de quitar reacción
                                    currentReaction = null
                                    optimisticReaction = null
                                    onRefresh()
                                }
                            } else {
                                // Agregar/cambiar reacción
                                val result = repository.reactToPost(post.id.toString(), emoji)
                                if (result.isFailure) {
                                    // Revertir optimistic update si falla
                                    optimisticReaction = previousReaction
                                } else {
                                    // Actualizar estado después de reaccionar
                                    currentReaction = emoji
                                    optimisticReaction = null
                                    onRefresh()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("EnhancedPostCard", "Error handling reaction: ${e.message}")
                            // Revertir optimistic update si hay error
                            optimisticReaction = previousReaction
                        }
                    }
                },
                onCommentClick = { showComments = true },
                onShareClick = { /* TODO */ }
            )
        }
    }
    
    // Sheets and Dialogs
    if (showComments) {
        CommentsBottomSheet(
            postId = post.id.toString(),
            onDismiss = { showComments = false },
            onProfileClick = onProfileClick
        )
    }
    
    if (showReactionsList && !post.postReactions.isNullOrEmpty()) {
        ReactionsListSheet(
            reactions = post.postReactions,
            onDismiss = { showReactionsList = false },
            onProfileClick = onProfileClick
        )
    }
    
    if (showEditPost) {
        EditPostSheet(
            post = post,
            onDismiss = { showEditPost = false },
            onPostUpdated = {
                showEditPost = false
                onRefresh()
            }
        )
    }
    
    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("¿Eliminar publicación?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val result = repository.deletePost(post.id.toString())
                                if (result.isSuccess) {
                                    showDeleteAlert = false
                                    onRefresh()
                                }
                            } catch (e: Exception) {
                                Log.e("EnhancedPostCard", "Error deleting post: ${e.message}")
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

// MARK: - Post Header
@Composable
private fun PostHeader(
    post: PostDTO,
    isPostOwner: Boolean,
    relationshipStatus: String,
    isFollowing: Boolean,
    onProfileClick: () -> Unit,
    onFollowClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (post.isAnonymous) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Anónimo",
                modifier = Modifier.size(40.dp),
                tint = Color.Gray
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "Anónimo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    post.createdAt?.let { formatRelativeTime(it) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        } else {
            AvatarView(
                url = post.creator?.avatarUrl,
                name = post.creator?.username,
                size = 40,
                modifier = Modifier.clickable(onClick = onProfileClick)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onProfileClick)
                ) {
                    Text(
                        text = formatUsername(post.creator?.username, post.creator?.nombre, post.creator?.apellido),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    post.category?.let {
                        Text(
                            " • $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }
                Text(
                    post.createdAt?.let { formatRelativeTime(it) } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Follow buttons (como iOS)
        if (!isPostOwner && !post.isAnonymous) {
            if (isFollowing) {
                // Botón "Siguiendo" (clickeable para dejar de seguir)
                Button(
                    onClick = onFollowClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Gray.copy(alpha = 0.15f),
                        contentColor = Color.Gray
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Siguiendo", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Botón "Seguir"
                Button(
                    onClick = onFollowClick,
                    colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Seguir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        
        // Menu button (para el dueño del post)
        if (isPostOwner) {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
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
                            onEditClick()
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
                            onDeleteClick()
                        }
                    )
                }
            }
        }
    }
}

// MARK: - Stats Bar
@Composable
private fun PostStatsBar(
    post: PostDTO,
    onReactionsClick: () -> Unit,
    onCommentsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reactions summary - solo emojis únicos sin conteos individuales
        if (!post.postReactions.isNullOrEmpty()) {
            Row(
                modifier = Modifier.clickable(onClick = onReactionsClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Obtener emojis únicos (máximo 3)
                val uniqueEmojis = post.postReactions
                    .map { it.emoji }
                    .distinct()
                    .take(3)
                
                // Mostrar solo los emojis en círculos pequeños
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                    uniqueEmojis.forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .shadow(2.dp, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 14.sp)
                        }
                    }
                }
                
                // Mostrar conteo total al final
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "${post.reactionCount}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Comments count
        if (post.commentsCount > 0) {
            TextButton(onClick = onCommentsClick) {
                Text(
                    "${post.commentsCount} comentarios",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
    }
}

// MARK: - Action Bar
@Composable
private fun PostActionBar(
    currentReaction: String?,
    reactionCount: Int,
    commentsCount: Int,
    onReactionSelect: (String) -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // React button con menú interactivo (long-press + arrastre)
        var showReactionMenu by remember { mutableStateOf(false) }
        var buttonPosition by remember { mutableStateOf(Offset.Zero) }
        var selectedReaction by remember { mutableStateOf<String?>(null) }
        
        Box(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        buttonPosition = coordinates.positionInWindow()
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // Quick tap: toggle like (👍)
                                if (currentReaction == null) {
                                    onReactionSelect("👍")
                                } else if (currentReaction == "👍") {
                                    onReactionSelect("👍") // Remove
                                } else {
                                    showReactionMenu = true // Show menu if other reaction
                                }
                            },
                            onLongPress = {
                                showReactionMenu = true
                            }
                        )
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentReaction != null) {
                        Text(currentReaction, fontSize = 18.sp)
                        Text(
                            when (currentReaction) {
                                "❤️" -> "Me encanta"
                                "😂" -> "Me divierte"
                                "🔥" -> "Fuego"
                                "😢" -> "Triste"
                                "👍" -> "Me gusta"
                                else -> "Reaccionado"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = OrabelPrimary
                        )
                    } else {
                        Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(
                            "Me gusta",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Contador de reacciones
                    if (reactionCount > 0) {
                        Text(
                            "$reactionCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            // Menú flotante de reacciones con arrastre
            if (showReactionMenu) {
                InteractiveReactionMenu(
                    buttonPosition = buttonPosition,
                    currentReaction = currentReaction,
                    selectedReaction = selectedReaction,
                    onReactionHovered = { reaction -> selectedReaction = reaction },
                    onReactionSelected = { reaction ->
                        onReactionSelect(reaction)
                        showReactionMenu = false
                        selectedReaction = null
                    },
                    onDismiss = {
                        showReactionMenu = false
                        selectedReaction = null
                    }
                )
            }
        }
        
        // Comment button
        TextButton(
            onClick = onCommentClick,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Comentar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                // Contador de comentarios
                if (commentsCount > 0) {
                    Text(
                        "$commentsCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
        
        // Share button
        TextButton(
            onClick = onShareClick,
            modifier = Modifier.weight(1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Compartir", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ReactionMenuItem(emoji: String, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 20.sp)
                Text(label)
            }
        },
        onClick = onClick
    )
}

// Helper functions
private fun formatUsername(username: String?, nombre: String?, apellido: String?): String {
    // Prioridad: 1) nombre completo (nombre + apellido), 2) solo nombre, 3) username, 4) "Usuario"
    val nombreCompleto = buildString {
        if (!nombre.isNullOrBlank()) {
            append(nombre)
            if (!apellido.isNullOrBlank()) {
                append(" ")
                append(apellido)
            }
        }
    }
    
    if (nombreCompleto.isNotBlank()) return nombreCompleto
    if (!username.isNullOrBlank()) return username
    return "Usuario"
}

private fun formatRelativeTime(date: Date): String {
    val now = Date()
    val diff = now.time - date.time
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        seconds < 60 -> "ahora"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
    }
}

// Placeholder composables for sheets
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsBottomSheet(
    postId: String,
    onDismiss: () -> Unit,
    onProfileClick: (UUID) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { SocialRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var flatComments by remember { mutableStateOf<List<CommentDTO>>(emptyList()) }
    var commentNodes by remember { mutableStateOf<List<CommentNode>>(emptyList()) }
    var newCommentText by remember { mutableStateOf("") }
    var replyToComment by remember { mutableStateOf<CommentDTO?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Cargar comentarios al abrir el sheet
    LaunchedEffect(postId) {
        try {
            val comments = repository.fetchComments(postId)
            flatComments = comments
            commentNodes = repository.buildTree(comments)
        } catch (e: Exception) {
            Log.e("CommentsSheet", "Error loading comments: ${e.message}")
        } finally {
            isLoading = false
        }
    }
    
    fun loadComments() {
        scope.launch {
            try {
                val comments = repository.fetchComments(postId)
                flatComments = comments
                commentNodes = repository.buildTree(comments)
            } catch (e: Exception) {
                Log.e("CommentsSheet", "Error reloading comments: ${e.message}")
            }
        }
    }
    
    fun postComment() {
        if (newCommentText.isBlank()) return
        
        val text = newCommentText
        val parentId = replyToComment?.id
        newCommentText = ""
        replyToComment = null
        
        scope.launch {
            try {
                val success = repository.postComment(postId, text, parentId)
                if (success) {
                    loadComments()
                } else {
                    Log.e("CommentsSheet", "Failed to post comment")
                }
            } catch (e: Exception) {
                Log.e("CommentsSheet", "Error posting comment: ${e.message}")
            }
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.ArrowBack, "Cerrar")
                }
                Text(
                    "Comentarios",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Divider()
            
            // Content
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (commentNodes.isEmpty()) {
                // Estado vacío
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No hay comentarios aún",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    items(commentNodes, key = { it.id }) { node ->
                        CommentThreadView(
                            node = node,
                            onReply = { comment ->
                                replyToComment = comment
                            },
                            onRefresh = { loadComments() },
                            onProfileClick = onProfileClick
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
            
            // Input area
            Column {
                // Reply context
                if (replyToComment != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(32.dp)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Respondiendo a",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    replyToComment!!.user?.username ?: "Usuario",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(onClick = { replyToComment = null }) {
                                Icon(Icons.Default.Close, "Cancelar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                
                Divider()
                
                // Input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    AvatarView(
                        url = null,
                        name = "Me",
                        size = 36
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        BasicTextField(
                            value = newCommentText,
                            onValueChange = { newCommentText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            decorationBox = { innerTextField ->
                                if (newCommentText.isEmpty()) {
                                    Text(
                                        "Agregar un comentario...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = { postComment() },
                        enabled = newCommentText.isNotBlank(),
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (newCommentText.isNotBlank()) OrabelPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            "Enviar",
                            tint = if (newCommentText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vista recursiva para mostrar un thread de comentarios con líneas visuales.
 * Basado en iOS CommentThreadView líneas 251-533
 */
@Composable
internal fun CommentThreadView(
    node: CommentNode,
    onReply: (CommentDTO) -> Unit,
    onRefresh: () -> Unit,
    onProfileClick: (UUID) -> Unit = {},
    indentLevel: Int = 0
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column {
        // Comentario raíz
        CommentRowContent(
            comment = node.comment,
            onReply = { onReply(node.comment) },
            onRefresh = onRefresh,
            onProfileClick = onProfileClick
        )
        
        // Hijos (si existen)
        if (node.children.isNotEmpty()) {
            Column {
                if (isExpanded) {
                    // Mostrar hijos con líneas de thread
                    Row(modifier = Modifier.padding(top = 12.dp)) {
                        // Línea vertical de thread
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .padding(end = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .offset(x = 16.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                            )
                        }
                        
                        // Columna de hijos
                        Column(
                            modifier = Modifier
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            node.children.forEach { childNode ->
                                Box {
                                    // Curva conectando la línea vertical al hijo
                                    Canvas(
                                        modifier = Modifier
                                            .size(30.dp, 18.dp)
                                            .offset(x = (-30).dp)
                                    ) {
                                        val path = Path().apply {
                                            moveTo(0f, size.height)
                                            quadraticBezierTo(
                                                size.width / 2, size.height,
                                                size.width, size.height
                                            )
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color.Gray.copy(alpha = 0.2f),
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                    
                                    CommentThreadView(
                                        node = childNode,
                                        onReply = onReply,
                                        onRefresh = onRefresh,
                                        onProfileClick = onProfileClick,
                                        indentLevel = indentLevel + 1
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Botón para expandir/colapsar
                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.padding(start = 18.dp, top = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(30.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isExpanded) "Ocultar respuestas" else "Ver ${node.children.size} respuestas",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Fila de contenido de un comentario individual.
 * Basado en iOS CommentRowContent líneas 333-533
 */
@Composable
internal fun CommentRowContent(
    comment: CommentDTO,
    onReply: () -> Unit,
    onRefresh: () -> Unit,
    onProfileClick: (UUID) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { SocialRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    val currentUserId = repository.getCurrentUserUUID()
    
    var likes by remember { mutableStateOf(comment.likesCount ?: 0) }
    var dislikes by remember { mutableStateOf(comment.dislikesCount ?: 0) }
    var myReaction by remember { mutableStateOf<String?>(null) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editedContent by remember { mutableStateOf(comment.content) }
    
    val isOwner = currentUserId == comment.userId
    
    // Cargar mi reacción actual
    LaunchedEffect(comment.id) {
        try {
            myReaction = repository.fetchMyCommentReaction(comment.id.toString())
        } catch (e: Exception) {
            Log.e("CommentRow", "Error fetching my reaction: ${e.message}")
        }
    }
    
    fun toggleLike() {
        scope.launch {
            try {
                if (myReaction == "like") {
                    likes -= 1
                    myReaction = null
                    repository.removeCommentReaction(comment.id.toString())
                } else {
                    if (myReaction == "dislike") dislikes -= 1
                    likes += 1
                    myReaction = "like"
                    repository.reactToComment(comment.id.toString(), "like")
                }
            } catch (e: Exception) {
                Log.e("CommentRow", "Error toggling like: ${e.message}")
            }
        }
    }
    
    fun toggleDislike() {
        scope.launch {
            try {
                if (myReaction == "dislike") {
                    dislikes -= 1
                    myReaction = null
                    repository.removeCommentReaction(comment.id.toString())
                } else {
                    if (myReaction == "like") likes -= 1
                    dislikes += 1
                    myReaction = "dislike"
                    repository.reactToComment(comment.id.toString(), "dislike")
                }
            } catch (e: Exception) {
                Log.e("CommentRow", "Error toggling dislike: ${e.message}")
            }
        }
    }
    
    fun deleteComment() {
        scope.launch {
            try {
                val result = repository.deleteComment(comment.id.toString())
                if (result.isSuccess) {
                    onRefresh()
                }
            } catch (e: Exception) {
                Log.e("CommentRow", "Error deleting comment: ${e.message}")
            }
        }
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AvatarView(
            url = comment.user?.avatarUrl,
            name = comment.user?.username,
            size = 36,
            modifier = Modifier.clickable { onProfileClick(comment.userId) }
        )
        
        Column(modifier = Modifier.weight(1f)) {
            // Bubble con contenido
            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            comment.user?.nombre ?: comment.user?.username ?: "Usuario",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onProfileClick(comment.userId) }
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                comment.createdAt.toRelativeString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            
                            if (isOwner) {
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            "Opciones",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Editar")
                                                }
                                            },
                                            onClick = {
                                                showMenu = false
                                                showEditDialog = true
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        null,
                                                        modifier = Modifier.size(20.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
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
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        comment.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Botones de acción
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextButton(onClick = onReply) {
                    Text(
                        "Responder",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                // Like button
                TextButton(onClick = { toggleLike() }) {
                    Icon(
                        if (myReaction == "like") Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Me gusta",
                        modifier = Modifier.size(16.dp),
                        tint = if (myReaction == "like") Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (likes > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("$likes", style = MaterialTheme.typography.labelMedium)
                    }
                }
                
                // Dislike button
                TextButton(onClick = { toggleDislike() }) {
                    Icon(
                        Icons.Default.ThumbDown,
                        "No me gusta",
                        modifier = Modifier.size(16.dp),
                        tint = if (myReaction == "dislike") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (dislikes > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("$dislikes", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    
    // Alert de confirmación de borrado
    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text("¿Eliminar comentario?") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteComment()
                    showDeleteAlert = false
                }) {
                    Text("Eliminar", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlert = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    
    // Diálogo de edición
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Editar comentario") },
            text = {
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val result = repository.updateComment(comment.id.toString(), editedContent)
                                if (result.isSuccess) {
                                    showEditDialog = false
                                    onRefresh()
                                }
                            } catch (e: Exception) {
                                Log.e("CommentRow", "Error updating comment: ${e.message}")
                            }
                        }
                    },
                    enabled = editedContent.isNotBlank()
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// Helper para formatear fechas relativas
private fun Date.toRelativeString(): String {
    val now = Date()
    val diff = now.time - this.time
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        seconds < 60 -> "ahora"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> "${days / 7}sem"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactionsListSheet(
    reactions: List<ReactionDTO>,
    onDismiss: () -> Unit,
    onProfileClick: (UUID) -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(16.dp)) {
            items(reactions) { reaction ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            reaction.userId.let { onProfileClick(it) }
                            onDismiss()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(reaction.emoji, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    AvatarView(url = reaction.user?.avatarUrl, name = reaction.user?.username, size = 40)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        // Mostrar nombre completo (nombre + apellido) o username
                        val displayName = buildString {
                            val nombre = reaction.user?.nombre
                            val apellido = reaction.user?.apellido
                            if (!nombre.isNullOrBlank()) {
                                append(nombre)
                                if (!apellido.isNullOrBlank()) {
                                    append(" ")
                                    append(apellido)
                                }
                            } else {
                                append(reaction.user?.username ?: "Usuario")
                            }
                        }
                        
                        Text(
                            displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Mostrar username debajo si se mostró el nombre completo
                        if (!reaction.user?.nombre.isNullOrBlank() && !reaction.user?.username.isNullOrBlank()) {
                            Text(
                                "@${reaction.user?.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPostSheet(post: PostDTO, onDismiss: () -> Unit, onPostUpdated: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { SocialRepository.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    val isReel = post.contentType == "reel" || post.contentType == "both"
    var editedCaption by remember { mutableStateOf(post.caption ?: "") }
    var editedTitle by remember { mutableStateOf(post.title ?: "") }
    var editedCategory by remember { mutableStateOf(post.category ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    
    fun saveChanges() {
        val isReel = post.contentType == "reel" || post.contentType == "both"
        if (editedCaption.isBlank()) return
        if (editedCategory.isBlank()) return
        if (isReel && editedTitle.isBlank()) return
        
        scope.launch {
            isLoading = true
            try {
                val result = repository.updatePost(
                    postId = post.id.toString(),
                    caption = editedCaption,
                    title = if (isReel && editedTitle.isNotBlank()) editedTitle else null,
                    category = editedCategory
                )
                if (result.isSuccess) {
                    onPostUpdated()
                    onDismiss()
                } else {
                    Log.e("EditPostSheet", "Error updating post: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("EditPostSheet", "Error updating post: ${e.message}")
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
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (isReel) "Editar reel" else "Editar publicación",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            // Preview de la imagen/video si existe
            post.mediaUrl?.let { url ->
                AsyncImage(
                    model = if (isReel) post.thumbnailUrl ?: url else url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Título (solo para reels)
            if (isReel) {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("Título *") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true
                )
            }
            
            OutlinedTextField(
                value = editedCaption,
                onValueChange = { editedCaption = it },
                label = { Text(if (isReel) "Descripción *" else "Descripción *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                enabled = !isLoading
            )
            
            // Categoría (para todos)
            OutlinedTextField(
                value = editedCategory,
                onValueChange = { editedCategory = it },
                label = { Text("Categoría *") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                singleLine = true,
                placeholder = { Text("ej: comedia, educación, deportes") }
            )
            
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
                    onClick = { saveChanges() },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading && editedCaption.isNotBlank() && editedCategory.isNotBlank() && (
                        if (isReel) editedTitle.isNotBlank() else true
                    ),
                    colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Guardar")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// MARK: - Interactive Reaction Menu
@Composable
private fun InteractiveReactionMenu(
    buttonPosition: Offset,
    currentReaction: String?,
    selectedReaction: String?,
    onReactionHovered: (String?) -> Unit,
    onReactionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val reactions = listOf("❤️", "😂", "🔥", "😢", "👍")
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    
    DisposableEffect(Unit) {
        onDispose { onDismiss() }
    }
    
    Popup(
        alignment = Alignment.BottomCenter,
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismiss,
        offset = with(density) {
            IntOffset(
                x = 0,
                y = -120.dp.roundToPx()
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragOffset = offset
                        },
                        onDrag = { change, _ ->
                            dragOffset = change.position
                            
                            // Detectar qué reacción está bajo el dedo
                            val itemWidth = size.width / reactions.size
                            val index = (dragOffset.x / itemWidth).toInt().coerceIn(0, reactions.lastIndex)
                            onReactionHovered(reactions.getOrNull(index))
                        },
                        onDragEnd = {
                            // Seleccionar la reacción si hay una hovereada
                            selectedReaction?.let { onReactionSelected(it) }
                            onDismiss()
                        },
                        onDragCancel = {
                            onDismiss()
                        }
                    )
                }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(32.dp)),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reactions.forEach { emoji ->
                        val isSelected = selectedReaction == emoji
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.3f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            label = "reactionScale"
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .scale(scale)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) OrabelPrimary.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .clickable { onReactionSelected(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                emoji,
                                fontSize = 32.sp,
                                modifier = Modifier.offset(y = if (isSelected) (-4).dp else 0.dp)
                            )
                        }
                    }
                }
                
                // Opción para quitar reacción si ya tiene una
                if (currentReaction != null) {
                    Divider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onReactionSelected(currentReaction)
                                onDismiss()
                            }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Quitar reacción",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
