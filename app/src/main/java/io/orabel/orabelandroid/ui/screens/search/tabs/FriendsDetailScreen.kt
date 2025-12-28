package io.orabel.orabelandroid.ui.screens.search.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orabel.orabelandroid.data.social.FriendRequestWithUser
import io.orabel.orabelandroid.data.social.ProfileDTO
import io.orabel.orabelandroid.data.social.SocialRepository
import io.orabel.orabelandroid.ui.screens.search.components.AvatarView
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Pantalla que muestra el detalle de la red del usuario:
 * - Solicitudes recibidas / enviadas
 * - Amigos
 * - Siguiendo / Seguidores
 * - Descubrimiento
 *
 * Equivalente a `FriendsView` en iOS.
 */
@Composable
fun FriendsDetailScreen(
    pendingRequests: List<FriendRequestWithUser>,
    outgoingRequests: List<FriendRequestWithUser>,
    followingUsers: List<ProfileDTO>,
    followers: List<ProfileDTO>,
    mutualFriends: List<ProfileDTO>,
    suggestedUsers: List<ProfileDTO>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onUserClick: (ProfileDTO) -> Unit,
    onAcceptRequest: (UUID) -> Unit,
    onRejectRequest: (UUID) -> Unit,
    onCancelRequest: (UUID) -> Unit,
    onFollowUser: (ProfileDTO) -> Unit,
    onUnfollowUser: (ProfileDTO) -> Unit,
    onRemoveFriend: (ProfileDTO) -> Unit,
    onBack: () -> Unit, // Added onBack
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top Bar
        OptIn(ExperimentalMaterial3Api::class)
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "Mi Red",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Atrás"
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Header
            item {
                Text(
                    text = "Conexiones",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

        // 1. Solicitudes Recibidas
        item {
            SectionHeader(
                title = "SOLICITUDES RECIBIDAS (${pendingRequests.size})",
                actionPoints = if (pendingRequests.isNotEmpty()) "Gestionar" else null
            )
        }

        if (pendingRequests.isEmpty()) {
            item { EmptySectionMessage("No hay solicitudes recibidas") }
        } else {
            items(pendingRequests) { item ->
                FriendRequestRow(
                    requestId = item.connectionId,
                    user = item.user,
                    onUserClick = { onUserClick(item.user) },
                    onAccept = { onAcceptRequest(item.connectionId) },
                    onReject = { onRejectRequest(item.connectionId) }
                )
                Divider(modifier = Modifier.padding(start = 16.dp))
            }
        }

        // 2. Solicitudes Enviadas
        item {
            SectionHeader(title = "SOLICITUDES ENVIADAS (${outgoingRequests.size})")
        }

        if (outgoingRequests.isEmpty()) {
            item { EmptySectionMessage("No hay solicitudes enviadas") }
        } else {
            items(outgoingRequests) { item ->
                OutgoingRequestRow(
                    requestId = item.connectionId,
                    user = item.user,
                    onUserClick = { onUserClick(item.user) },
                    onCancel = { onCancelRequest(item.connectionId) }
                )
                Divider(modifier = Modifier.padding(start = 16.dp))
            }
        }

        // 3. Amigos
        item {
            SectionHeader(title = "AMIGOS")
        }

        if (mutualFriends.isEmpty()) {
            item { EmptySectionMessage("Aún no tienes amigos") }
        } else {
            items(mutualFriends) { user ->
                MutualFriendRow(
                    user = user,
                    onUserClick = { onUserClick(user) },
                    onRemoveFriend = { onRemoveFriend(user) }
                )
                Divider(modifier = Modifier.padding(start = 16.dp))
            }
        }

        // 4. Siguiendo
        item {
            SectionHeader(title = "SIGUIENDO")
        }

        if (followingUsers.isEmpty()) {
            item { EmptySectionMessage("No sigues a nadie aún") }
        } else {
            items(followingUsers) { user ->
                FollowingUserRow(
                    user = user,
                    onUserClick = { onUserClick(user) },
                    onUnfollow = { onUnfollowUser(user) }
                )
                Divider(modifier = Modifier.padding(start = 16.dp))
            }
        }

        // 5. Seguidores
        item {
            SectionHeader(title = "SEGUIDORES")
        }

        if (followers.isEmpty()) {
            item { EmptySectionMessage("Aún no tienes seguidores") }
        } else {
            items(followers) { user ->
                FollowerRow(
                    user = user,
                    onUserClick = { onUserClick(user) },
                    onFollowBack = { onFollowUser(user) }
                )
                Divider(modifier = Modifier.padding(start = 16.dp))
            }
        }

        // 6. Descubrir Personas
        item {
            SectionHeader(title = "DESCUBRIR PERSONAS")
        }

        items(suggestedUsers) { user ->
            DiscoverUserRow(
                user = user,
                onUserClick = { onUserClick(user) },
                onFollow = { onFollowUser(user) }
            )
            Divider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}
}

@Composable
private fun SectionHeader(title: String, actionPoints: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (actionPoints != null) {
            Text(
                text = actionPoints,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = OrabelPrimary
            )
        }
    }
}

@Composable
private fun EmptySectionMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

// MARK: - Helper Rows

@Composable
fun FriendRequestRow(
    requestId: UUID,
    user: ProfileDTO,
    onUserClick: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var didAction by remember { mutableStateOf(false) }

    if (!didAction) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            AvatarView(url = user.avatarUrl, name = user.username, size = 48)

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = user.username ?: "Usuario",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "2 amigos en común", // Placeholder
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = "2h", // Placeholder time
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            didAction = true
                            onAccept()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("Aceptar", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            didAction = true
                            onReject()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("Rechazar", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun OutgoingRequestRow(
    requestId: UUID,
    user: ProfileDTO,
    onUserClick: () -> Unit,
    onCancel: () -> Unit
) {
    var didCancel by remember { mutableStateOf(false) }

    if (!didCancel) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(url = user.avatarUrl, name = user.username, size = 48)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username ?: "Usuario",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Solicitud pendiente",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800) // Orange manually
                )
            }

            Button(
                onClick = {
                     didCancel = true
                     onCancel()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Cancelar", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun MutualFriendRow(
    user: ProfileDTO,
    onUserClick: () -> Unit,
    onRemoveFriend: () -> Unit
) {
    var didRemove by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    
    if (!didRemove) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onUserClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(url = user.avatarUrl, name = user.username, size = 48)
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username ?: "Usuario",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Amigo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Eliminar", fontSize = 12.sp)
            }
        }
        
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Eliminar amistad") },
                text = { Text("¿Estás seguro de que quieres eliminar a ${user.username ?: "este usuario"} de tu lista de amigos?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDialog = false
                            didRemove = true
                            onRemoveFriend()
                        }
                    ) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun FollowingUserRow(
    user: ProfileDTO,
    onUserClick: () -> Unit,
    onUnfollow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUserClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(url = user.avatarUrl, name = user.username, size = 48)
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username ?: "Usuario",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Siguiendo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        Button(
            onClick = onUnfollow,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Siguiendo", fontSize = 12.sp)
        }
    }
}

@Composable
fun FollowerRow(
    user: ProfileDTO,
    onUserClick: () -> Unit,
    onFollowBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUserClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(url = user.avatarUrl, name = user.username, size = 48)
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username ?: "Usuario",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Te sigue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        Button(
            onClick = onFollowBack,
            colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Seguir", fontSize = 12.sp)
        }
    }
}

@Composable
fun DiscoverUserRow(
    user: ProfileDTO,
    onUserClick: () -> Unit,
    onFollow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUserClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(url = user.avatarUrl, name = user.username, size = 48)
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username ?: "Usuario",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            // TODO: show mutual users count if available
        }
        
        Button(
            onClick = onFollow,
            colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Seguir", fontSize = 12.sp)
        }
    }
}
