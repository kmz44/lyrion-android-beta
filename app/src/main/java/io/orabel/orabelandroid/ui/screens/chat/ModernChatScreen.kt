package io.orabel.orabelandroid.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.ui.theme.*

@Composable
fun ModernChatScreen(
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var selectedBottomNav by remember { mutableStateOf(1) } // Chat ahora es índice 1
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        ModernTopBar(
            title = "Nuevo Chat",
            onBackClick = onBackClick,
            actions = {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menú",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        
        // Chat Content
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            if (inputText.isEmpty()) {
                // Estado inicial con sugerencias
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        // Mensaje de bienvenida
                        Text(
                            text = "Inicia una conversación con nuestro asistente de IA",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    item {
                        // Sugerencias de chat
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            items(chatSuggestions) { suggestion ->
                                ChatSuggestionCard(
                                    suggestion = suggestion,
                                    onClick = {
                                        inputText = suggestion.text
                                        onSendMessage(suggestion.text)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Aquí irían los mensajes del chat
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mensajes del chat irían aquí
                }
            }
        }
        
        // Input bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Campo de texto
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Escribe tu mensaje...",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            )
                        }
                        innerTextField()
                    }
                )
                
                // Botón de enviar
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = if (inputText.isNotBlank()) PrimaryColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Enviar",
                        tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Bottom Navigation
        ModernBottomNavigation(
            selectedItem = selectedBottomNav,
            onItemSelected = { selectedBottomNav = it }
        )
    }
}

@Composable
fun ChatSuggestionCard(
    suggestion: ChatSuggestion,
    onClick: () -> Unit
) {
    ModernCard(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(200.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Imagen
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(suggestion.imageUrl)
                    .build(),
                contentDescription = suggestion.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Crop
            )
            
            // Texto
            Text(
                text = suggestion.text,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

data class ChatSuggestion(
    val text: String,
    val imageUrl: String
)

val chatSuggestions = listOf(
    ChatSuggestion(
        text = "Escribe un poema",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuA2vxglc7MwvPUxlBKavdY1txlJFv-oN20yY00eLpAKyMjtO60AqYroiKDO7qlge3Sz5XaUqd7WQzG1IWQgC9yumtbkFfxjKLx9gZAEQb0bml3HvIijNrsC24CqaXNqIYhA8cSuY7MHuR7a0umDm0whlEHZczNLlFa_wgOUFMhxhI2_CvotJl84Jw_lo6GnHTzJvePeEsq_9P7kQRm5ORqdZZAj4rlkZl0an4pMjg2KCzGJTsyQJuOplY9KVaG5oFrYVAXYNCCKe_g"
    ),
    ChatSuggestion(
        text = "Explica la física cuántica",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCDCIMgwYOnzYlgRCHi5JaNEvOft-3eg7qio4am4AJzpoM3-X3ShGx9SAT4aJUnpJUEY5Eam8x-zigVz-OfPc9TDJognqZJuBAigS2mi0liLXDltPYjdDOGR7wi2lCn3ZBXBUc8OgPzyDLfPeHeK3vit8ELqIFWVsWcxswaxzK18vICtHJS7d6aYGciENdy-8OntQZjkeYajCF197Cxwaz0KyoihLLRSYGOIkFXUYgbsX3qQf0kXUzDq60iLspN4YXBh2MMyzwaPQ8"
    ),
    ChatSuggestion(
        text = "Traduce al francés",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBEMy_QPpzuZ41-47h-bDY1yvlO3XfTIHXP54T_tOPxaAJRg1lQssl0fWgpZDtSdwNMhUjKyP6uSB0f-d8j11gUeXyrkt3x4Crs2sw_vtVeGYC67v9xnL0RhoSiYZ6701K3SBo0bTfB_LF4sG_4W0w_SnYcvJQob984xcuPYGaml77dkL7dvvyi1fOSgZJS3jS9zBrSXsUc6fXqIibUbqXaExBKgEPnwAuP0oeiEldzlJi5multM1hay2grpMje2Cgce53__JVA9uA"
    ),
    ChatSuggestion(
        text = "Resume un libro",
        imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBklFzsLD-MrpC2BKvk_5G9346kcbEMIa6rnYo77B1hvY9Vx6dtOmzySxXRSn_B1Q0dR4RV5kPuciz0guwWpMS24BETd2Q-QYIXh01Ht6ZfahdWvvPvCYjYDWa0c9o0DonmeYsQ_zpQUW56-dS3SYViOIS9BGUwEuYYkp7N3nUHYe6rb09L3EqkUH0PKZjQvpzHmwBZcko-t4eIT8wFtZDs52iRfCGJkVEs_UIrb1lZxmVoAIGrannX_brJ26JqHh0u5G-qKszpQkQ"
    )
)
