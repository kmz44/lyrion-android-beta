package io.orabel.orabelandroid.ui.screens.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.orabel.orabelandroid.data.social.SocialRepository
import io.orabel.orabelandroid.data.social.StoryDTO
import io.orabel.orabelandroid.data.social.UserStoriesGroup
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StoriesViewerScreen(
    storiesGroup: UserStoriesGroup,
    repository: SocialRepository,
    onClose: () -> Unit,
    onNavigateNext: () -> Unit,
    onNavigatePrevious: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentIndex by remember { mutableStateOf(0) }
    var isPaused by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    
    val currentStory = storiesGroup.stories.getOrNull(currentIndex)
    
    // Auto-advance timer
    LaunchedEffect(currentIndex, isPaused) {
        if (!isPaused && currentStory != null) {
            val duration = (currentStory.durationSeconds * 1000).toLong()
            val steps = 100
            val stepDuration = duration / steps
            
            for (i in 0..steps) {
                if (!isPaused) {
                    progress = i.toFloat() / steps
                    delay(stepDuration)
                } else {
                    break
                }
            }
            
            if (!isPaused) {
                // Advance to next story
                if (currentIndex < storiesGroup.stories.size - 1) {
                    currentIndex++
                    progress = 0f
                } else {
                    onNavigateNext()
                }
            }
        }
    }
    
    // Mark story as viewed
    LaunchedEffect(currentStory) {
        currentStory?.let {
            repository.markStoryAsViewed(it.id)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val third = size.width / 3f
                        when {
                            offset.x < third -> {
                                // Left tap: previous story or group
                                if (currentIndex > 0) {
                                    currentIndex--
                                    progress = 0f
                                } else {
                                    onNavigatePrevious()
                                }
                            }
                            offset.x > third * 2 -> {
                                // Right tap: next story or group
                                if (currentIndex < storiesGroup.stories.size - 1) {
                                    currentIndex++
                                    progress = 0f
                                } else {
                                    onNavigateNext()
                                }
                            }
                            else -> {
                                // Center tap: pause/resume
                                isPaused = !isPaused
                            }
                        }
                    },
                    onLongPress = {
                        isPaused = true
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (isPaused) isPaused = false
                    }
                )
            }
    ) {
        // Story content
        currentStory?.let { story ->
            AsyncImage(
                model = story.mediaUrl,
                contentDescription = "Historia",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            
            // Top gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Progress bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                storiesGroup.stories.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .background(
                                Color.White.copy(alpha = 0.3f),
                                RoundedCornerShape(2.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(
                                    when {
                                        index < currentIndex -> 1f
                                        index == currentIndex -> progress
                                        else -> 0f
                                    }
                                )
                                .background(Color.White, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            
            // User info header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(top = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                AsyncImage(
                    model = storiesGroup.avatarUrl,
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = storiesGroup.displayName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatTimestamp(story.createdAt),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = Color.White
                    )
                }
            }
            
            // Caption (if exists)
            story.caption?.let { caption ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Black.copy(alpha = 0.6f)
                        )
                    ) {
                        Text(
                            text = caption,
                            color = Color.White,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // Pause indicator
            if (isPaused) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Pause,
                        contentDescription = "Pausado",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(80.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(date: Date): String {
    val now = Date()
    val diff = now.time - date.time
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours < 1 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
    }
}
