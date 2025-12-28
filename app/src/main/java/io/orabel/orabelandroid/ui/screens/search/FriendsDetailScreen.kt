package io.orabel.orabelandroid.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.social.*
import io.orabel.orabelandroid.ui.screens.search.components.AvatarView
import io.orabel.orabelandroid.ui.screens.search.components.FriendRequestRow
import io.orabel.orabelandroid.ui.screens.search.components.UserRow
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import kotlinx.coroutines.launch

/**
 * Pantalla de detalle de la red de amigos.
 * Muestra: solicitudes recibidas, enviadas, amigos, siguiendo, seguidores, descubrir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsDetailScreen(
    onBack: () -> Unit,
    onUserClick: (ProfileDTO) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository.getInstance(context) }
    
    // Estados
    var isLoading by remember { mutableStateOf(true) }
    var pendingRequests by remember { mutableStateOf<List<FriendRequestWithUser>>(emptyList()) }
    var outgoingRequests by remember { mutableStateOf<List<FriendRequestWithUser>>(emptyList()) }
    var mutualFriends by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    var followingUsers by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    var followers by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    var suggestedUsers by remember { mutableStateOf<List<ProfileDTO>>(emptyList()) }
    
    // Cargar datos
    LaunchedEffect(Unit) {
        isLoading = true
        try {
            pendingRequests = repository.fetchIncomingFriendRequests()
            outgoingRequests = repository.fetchOutgoingFriendRequests()
            mutualFriends = repository.fetchMutualFriends()
            followingUsers = repository.fetchFollowingUsers()
            followers = repository.fetchFollowers()
            suggestedUsers = repository.fetchAvailableUsers()
        } catch (e: Exception) {
            android.util.Log.e("FriendsDetailScreen", "Error cargando datos: ${e.message}")
        }
        isLoading = false
    }
    
    // Funciones de acción
    fun acceptRequest(connectionId: java.util.UUID) {
        scope.launch {
            repository.updateConnectionStatus(connectionId, "amigos")
            // Recargar solicitudes
            pendingRequests = repository.fetchIncomingFriendRequests()
            mutualFriends = repository.fetchMutualFriends()
        }
    }
    
    fun rejectRequest(connectionId: java.util.UUID) {
        scope.launch {
            repository.updateConnectionStatus(connectionId, "deleted")
            pendingRequests = repository.fetchIncomingFriendRequests()
        }
    }
    
    fun cancelOutgoingRequest(connectionId: java.util.UUID) {
        scope.launch {
            repository.updateConnectionStatus(connectionId, "deleted")
            outgoingRequests = repository.fetchOutgoingFriendRequests()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Red") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // SOLICITUDES RECIBIDAS
                item {
                    SectionHeader(
                        title = "Solicitudes Recibidas",
                        count = pendingRequests.size
                    )
                }
                
                if (pendingRequests.isEmpty()) {
                    item { EmptySection("No hay solicitudes recibidas") }
                } else {
                    items(pendingRequests) { request ->
                        FriendRequestRow(
                            user = request.user,
                            onAccept = { acceptRequest(request.connectionId) },
                            onReject = { rejectRequest(request.connectionId) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                    }
                }
                
                // SOLICITUDES ENVIADAS
                item {
                    SectionHeader(
                        title = "Solicitudes Enviadas",
                        count = outgoingRequests.size
                    )
                }
                
                if (outgoingRequests.isEmpty()) {
                    item { EmptySection("No hay solicitudes enviadas") }
                } else {
                    items(outgoingRequests) { request ->
                        UserRow(
                            user = request.user,
                            subtitle = "Pendiente",
                            actionButton = {
                                TextButton(onClick = { cancelOutgoingRequest(request.connectionId) }) {
                                    Text("Cancelar", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            onClick = { onUserClick(request.user) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                    }
                }
                
                // AMIGOS
                item {
                    SectionHeader(title = "Amigos", count = mutualFriends.size)
                }
                
                if (mutualFriends.isEmpty()) {
                    item { EmptySection("Aún no tienes amigos") }
                } else {
                    items(mutualFriends) { user ->
                        UserRow(
                            user = user,
                            subtitle = "Amigo",
                            onClick = { onUserClick(user) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                    }
                }
                
                // SIGUIENDO
                item {
                    SectionHeader(title = "Siguiendo", count = followingUsers.size)
                }
                
                if (followingUsers.isEmpty()) {
                    item { EmptySection("No sigues a nadie aún") }
                } else {
                    items(followingUsers) { user ->
                        UserRow(
                            user = user,
                            subtitle = "Siguiendo",
                            onClick = { onUserClick(user) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                    }
                }
                
                // SEGUIDORES
                item {
                    SectionHeader(title = "Seguidores", count = followers.size)
                }
                
                if (followers.isEmpty()) {
                    item { EmptySection("Aún no tienes seguidores") }
                } else {
                    items(followers) { user ->
                        UserRow(
                            user = user,
                            subtitle = "Te sigue",
                            onClick = { onUserClick(user) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                    }
                }
                
                // DESCUBRIR PERSONAS
                item {
                    SectionHeader(title = "Descubrir Personas", count = null)
                }
                
                items(suggestedUsers.take(10)) { user ->
                    UserRow(
                        user = user,
                        subtitle = user.occupation ?: "Usuario",
                        onClick = { onUserClick(user) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (count != null) "$title ($count)" else title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptySection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}
