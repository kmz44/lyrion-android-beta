package io.orabel.orabelandroid.ui.screens.stories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import coil.compose.AsyncImage
import io.orabel.orabelandroid.data.social.SocialRepository
import io.orabel.orabelandroid.data.social.StoryUploadRequest
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadStoryScreen(
    repository: SocialRepository,
    onBack: () -> Unit,
    onUploadSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf("followers") } // "followers" o "friends"
    var isUploading by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf<String?>(null) }
    
    // Media picker
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedMediaUri = uri
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subir Estado") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Cerrar")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val uri = selectedMediaUri
                                if (uri != null) {
                                    isUploading = true
                                    try {
                                        // TODO: Upload media to Supabase Storage
                                        // Por ahora usamos un placeholder URL
                                        val mediaUrl = uri.toString()
                                        
                                        val request = StoryUploadRequest(
                                            mediaUrl = mediaUrl,
                                            mediaType = "image", // TODO: detectar tipo
                                            caption = caption.takeIf { it.isNotBlank() },
                                            visibility = visibility
                                        )
                                        
                                        val result = repository.uploadStory(request)
                                        if (result.isSuccess) {
                                            onUploadSuccess()
                                        } else {
                                            showError = result.exceptionOrNull()?.message
                                        }
                                    } catch (e: Exception) {
                                        showError = e.message
                                    } finally {
                                        isUploading = false
                                    }
                                }
                            }
                        },
                        enabled = selectedMediaUri != null && !isUploading
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Publicar", color = OrabelPrimary)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Media preview
            if (selectedMediaUri != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = selectedMediaUri,
                            contentDescription = "Vista previa",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Badge de visibilidad
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (visibility == "friends") Icons.Default.People else Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (visibility == "friends") "Solo amigos" else "Seguidores",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                // Select media button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { mediaPickerLauncher.launch("image/*") },
                                modifier = Modifier.size(80.dp)
                            ) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    "Seleccionar imagen",
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Seleccionar imagen o video",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Caption
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Descripción (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Visibility selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = OrabelPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "¿Quién puede ver tu estado?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OrabelPrimary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Followers option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = { visibility = "followers" },
                        colors = CardDefaults.cardColors(
                            containerColor = if (visibility == "followers") 
                                OrabelPrimary.copy(alpha = 0.15f) 
                            else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = visibility == "followers",
                                onClick = { visibility = "followers" },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = OrabelPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = if (visibility == "followers") OrabelPrimary else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Seguidores y Amigos",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (visibility == "followers") FontWeight.Bold else FontWeight.Medium,
                                    color = if (visibility == "followers") OrabelPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Visible para todos tus seguidores y amigos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (visibility == "followers") OrabelPrimary.copy(alpha = 0.7f) else Color.Gray
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Friends only option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = { visibility = "friends" },
                        colors = CardDefaults.cardColors(
                            containerColor = if (visibility == "friends") 
                                OrabelPrimary.copy(alpha = 0.15f) 
                            else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = visibility == "friends",
                                onClick = { visibility = "friends" },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = OrabelPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = if (visibility == "friends") OrabelPrimary else Color.Gray
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Solo Amigos",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (visibility == "friends") FontWeight.Bold else FontWeight.Medium,
                                    color = if (visibility == "friends") OrabelPrimary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Visible solo para tus amigos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (visibility == "friends") OrabelPrimary.copy(alpha = 0.7f) else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
            
            // Error message
            showError?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Error: $error",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
