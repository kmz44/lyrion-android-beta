package io.orabel.orabelandroid.ui.screens.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.orabel.orabelandroid.data.social.UserStoriesGroup
import io.orabel.orabelandroid.ui.theme.OrabelPrimary

/**
 * Sección horizontal de historias/estados para Mi Red tab.
 */
@Composable
fun StoriesSection(
    storiesGroups: List<UserStoriesGroup>,
    currentUserId: String?,
    currentUserAvatar: String?,
    currentUserName: String?,
    onAddStoryClick: () -> Unit,
    onStoryClick: (UserStoriesGroup) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        // "Add Story" card (current user)
        item {
            AddStoryCard(
                avatarUrl = currentUserAvatar,
                userName = currentUserName ?: "Tu estado",
                onClick = onAddStoryClick
            )
        }
        
        // Stories from other users
        items(storiesGroups) { group ->
            StoryCard(
                group = group,
                onClick = { onStoryClick(group) }
            )
        }
    }
}

@Composable
private fun AddStoryCard(
    avatarUrl: String?,
    userName: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(70.dp)
        ) {
            // Avatar
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Tu avatar",
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )
            
            // Add icon
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .background(OrabelPrimary, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Agregar estado",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Tu estado",
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StoryCard(
    group: UserStoriesGroup,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(70.dp)
        ) {
            // Story ring (gradient if unviewed)
            val ringModifier = if (group.hasUnviewedStory) {
                Modifier
                    .size(70.dp)
                    .border(
                        width = 3.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFF6B6B),
                                Color(0xFFFFD93D),
                                Color(0xFF6BCF7F)
                            )
                        ),
                        shape = CircleShape
                    )
                    .padding(3.dp)
            } else {
                Modifier
                    .size(70.dp)
                    .border(2.dp, Color.Gray, CircleShape)
                    .padding(3.dp)
            }
            
            // Avatar
            AsyncImage(
                model = group.avatarUrl,
                contentDescription = "Avatar de ${group.displayName}",
                modifier = ringModifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.LightGray),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = group.displayName,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontWeight = if (group.hasUnviewedStory) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
