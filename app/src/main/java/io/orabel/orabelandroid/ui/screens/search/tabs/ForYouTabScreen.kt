package io.orabel.orabelandroid.ui.screens.search.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.social.PostDTO
import io.orabel.orabelandroid.data.social.ProfileDTO
import io.orabel.orabelandroid.ui.screens.social.EnhancedPostCard
import io.orabel.orabelandroid.ui.screens.search.components.SuggestedUserCard
import java.util.UUID

/**
 * Pantalla "For You" - Feed de publicaciones principales.
 */
@Composable
fun ForYouTabScreen(
    posts: List<PostDTO>,
    suggestedUsers: List<ProfileDTO>,
    isLoading: Boolean,
    onUserClick: (ProfileDTO) -> Unit = {},
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Publicaciones
            if (posts.isEmpty()) {
                item {
                    EmptyFeedMessage()
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    EnhancedPostCard(
                        post = post,
                        onProfileClick = { userId ->
                            // Create minimal ProfileDTO with just the ID for navigation
                            onUserClick(ProfileDTO(id = userId))
                        },
                        onRefresh = onRefresh,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            
            // Sugerencias
            if (suggestedUsers.isNotEmpty()) {
                item {
                    SuggestionsSection(
                        users = suggestedUsers,
                        onUserClick = onUserClick
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFeedMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Image, // Closest to photo.on.rectangle.angled
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hay publicaciones",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Las publicaciones de tus amigos aparecerán aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuggestionsSection(
    users: List<ProfileDTO>,
    onUserClick: (ProfileDTO) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Sugerencias para ti",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(users) { user ->
                SuggestedUserCard(
                    user = user,
                    onClick = { onUserClick(user) }
                )
            }
        }
    }
}
