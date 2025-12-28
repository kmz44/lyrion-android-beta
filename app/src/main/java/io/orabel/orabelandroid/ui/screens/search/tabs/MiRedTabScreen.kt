package io.orabel.orabelandroid.ui.screens.search.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.social.ProfileDTO
import io.orabel.orabelandroid.data.social.UserStoriesGroup
import io.orabel.orabelandroid.ui.screens.search.components.AvatarView
import io.orabel.orabelandroid.ui.screens.search.components.StoriesSection
import io.orabel.orabelandroid.ui.screens.search.components.SuggestedUserCard
import io.orabel.orabelandroid.ui.theme.OrabelPrimary

/**
 * Pantalla "Mi Red" - Red de contactos, búsqueda y descubrimiento.
 */
@Composable
fun MiRedTabScreen(
    suggestedUsers: List<ProfileDTO>,
    searchResults: List<ProfileDTO>,
    isSearching: Boolean,
    searchQuery: String,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onUserClick: (ProfileDTO) -> Unit,
    onViewNetwork: () -> Unit,
    modifier: Modifier = Modifier,
    // Stories parameters
    storiesGroups: List<UserStoriesGroup> = emptyList(),
    currentUserId: String? = null,
    currentUserAvatar: String? = null,
    currentUserName: String? = null,
    onAddStoryClick: () -> Unit = {},
    onStoryClick: (UserStoriesGroup) -> Unit = {}
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Barra de búsqueda
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            onSearch = onSearch,
            modifier = Modifier.padding(16.dp)
        )
        
        if (isSearching || searchQuery.isNotEmpty()) {
            // Mostrar resultados de búsqueda
            SearchResultsList(
                results = searchResults,
                isSearching = isSearching,
                onUserClick = onUserClick
            )
        } else {
            // Contenido normal
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Stories
                item {
                    StoriesSection(
                        storiesGroups = storiesGroups,
                        currentUserId = currentUserId,
                        currentUserAvatar = currentUserAvatar,
                        currentUserName = currentUserName,
                        onAddStoryClick = onAddStoryClick,
                        onStoryClick = onStoryClick,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
                
                // Botón "Ver mi red"
                item {
                    ViewNetworkButton(
                        onClick = onViewNetwork,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
                
                // Sección de descubrimiento
                item {
                    DiscoverySection(
                        users = suggestedUsers,
                        onUserClick = onUserClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Buscar personas...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "Buscar")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(25.dp)
    )
}

@Composable
private fun SearchResultsList(
    results: List<ProfileDTO>,
    isSearching: Boolean,
    onUserClick: (ProfileDTO) -> Unit
) {
    if (isSearching) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.SearchOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No se encontraron resultados",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(results) { user ->
                SearchResultItem(
                    user = user,
                    onClick = { onUserClick(user) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    user: ProfileDTO,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(
                url = user.avatarUrl,
                name = user.username,
                size = 50
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username ?: "Usuario",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (!user.nombre.isNullOrBlank()) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun ViewNetworkButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary),
        contentPadding = PaddingValues(vertical = 14.dp)
    ) {
        Icon(Icons.Default.Hub, contentDescription = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Ver mi red",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DiscoverySection(
    users: List<ProfileDTO>,
    onUserClick: (ProfileDTO) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Descubrir personas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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
