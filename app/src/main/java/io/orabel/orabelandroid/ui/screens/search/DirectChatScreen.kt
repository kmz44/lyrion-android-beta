package io.orabel.orabelandroid.ui.screens.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orabel.orabelandroid.data.social.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.clickable
import io.orabel.orabelandroid.ui.screens.search.components.AvatarView
import io.orabel.orabelandroid.ui.screens.search.components.StatusIndicator
import io.orabel.orabelandroid.utils.UserActivityManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DirectChatScreen(
    targetUser: ProfileDTO,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository.getInstance(context) }
    val activityManager = remember { UserActivityManager.getInstance(context) }
    val listState = rememberLazyListState()
    
    var messages by remember { mutableStateOf<List<MessageDTO>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSending by remember { mutableStateOf(false) }
    var isTyping by remember { mutableStateOf(false) }
    var userStatus by remember { mutableStateOf("offline") }
    var myStatus by remember { mutableStateOf("offline") } // Para feedback de los botones de test
    
    val prefs = context.getSharedPreferences("supabase_session", android.content.Context.MODE_PRIVATE)
    // Usar el mismo origen de userId que SocialRepository para garantizar coincidencia
    val currentUserId = repository.getCurrentUserUUID()?.toString() ?: prefs.getString("user_id", "") ?: ""
    
    var lastMessageId by remember { mutableStateOf(0L) }
    var realtimeConnected by remember { mutableStateOf(false) }
    
    // 1. Fetch initial messages and enter chat state
    LaunchedEffect(targetUser.id) {
        isLoading = true
        messages = repository.fetchMessages(targetUser.id.toString())
        lastMessageId = messages.maxOfOrNull { it.id } ?: 0L
        isLoading = false
        
        // Notificar al ActivityManager que entramos a chat directo (actualiza chat_status)
        activityManager.enterDirectChat(targetUser.id.toString())
    }
    
    // 2. Realtime message subscription (SIN POLLING)
    LaunchedEffect(targetUser.id) {
        launch {
            try {
                repository.subscribeToMessages(targetUser.id.toString()).collect { newMessage ->
                    android.util.Log.d("DirectChat", "📨 Realtime message received: ${newMessage.content.take(20)}...")
                    realtimeConnected = true
                    
                    if (messages.none { it.id == newMessage.id }) {
                        messages = messages + newMessage
                        lastMessageId = maxOf(lastMessageId, newMessage.id)
                        if (!listState.canScrollForward) {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                        
                        // Auto-marcar como entregado si no es mensaje nuestro
                        if (newMessage.senderId.toString() != currentUserId) {
                            launch {
                                repository.markMessageAsDelivered(newMessage.id)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DirectChat", "❌ Realtime error: ${e.message}")
                realtimeConnected = false
            }
        }
    }
    
    // 3. Subscribe to typing indicator (Realtime)
    LaunchedEffect(targetUser.id) {
        repository.subscribeToTypingIndicator(targetUser.id.toString()).collect { typing ->
            isTyping = typing
        }
    }
    
    // 4. Subscribe to CHAT_STATUS (Realtime ÚNICAMENTE - sin polling)
    LaunchedEffect(targetUser.id) {
        // 1. Iniciar fetch inicial en paralelo (no bloquear suscripción)
        launch {
            try {
                val initialChatStatus = repository.getChatStatus(targetUser.id.toString(), currentUserId ?: "")
                // Solo actualizar si no hemos recibido ya un evento de realtime (race condition benigna)
                if (userStatus == "offline") { 
                    userStatus = initialChatStatus
                }
            } catch (e: Exception) {
                // Ignore error on initial fetch
            }
        }
        
        // 2. Iniciar Realtime subscription INMEDIATAMENTE para CHAT_STATUS
        launch {
            try {
                repository.subscribeToChatStatus(targetUser.id.toString(), currentUserId ?: "").collect { chatStatus ->
                    android.util.Log.d("DirectChat", "🟢 [UI] Realtime CHAT_STATUS update: $chatStatus")
                    userStatus = chatStatus
                }
            } catch (e: Exception) {
                android.util.Log.e("DirectChat", "❌ Chat Status Realtime error: ${e.message}")
            }
        }
    }
    
    // 5. Subscribe to MESSAGE STATUS changes (Realtime para palomas)
    LaunchedEffect(targetUser.id) {
        launch {
            try {
                repository.subscribeToMessageStatusChanges(targetUser.id.toString()).collect { (messageId, newStatus) ->
                    android.util.Log.d("DirectChat", "🕊️ [PALOMAS] Message $messageId status -> $newStatus")
                    
                    val deliveredAt = if (newStatus == "delivered" || newStatus == "read") System.currentTimeMillis() else null
                    val seenAt = if (newStatus == "read") System.currentTimeMillis() else null
                    
                    // Actualizar el mensaje en la lista (UI)
                    messages = messages.map { msg ->
                        if (msg.id == messageId) {
                            msg.copy(
                                status = newStatus,
                                deliveredAt = deliveredAt ?: msg.deliveredAt,
                                seenAt = seenAt ?: msg.seenAt
                            )
                        } else {
                            msg
                        }
                    }
                    
                    // Guardar el cambio en local storage para persistencia
                    launch {
                        repository.updateLocalMessageStatus(messageId, newStatus, deliveredAt, seenAt)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DirectChat", "❌ [PALOMAS] Realtime error: ${e.message}")
            }
        }
    }
    
    // 6. Mark visible messages as read when they appear on screen
    LaunchedEffect(messages.size) {
        launch {
            // Marcar mensajes recibidos como leídos
            messages.filter { msg ->
                msg.senderId.toString() != currentUserId && 
                (msg.status == "delivered" || msg.status == "sent")
            }.forEach { msg ->
                repository.markMessageAsRead(msg.id)
            }
        }
    }
    
    // 7. Reset chat_status on leave
    DisposableEffect(Unit) {
        onDispose {
            activityManager.exitDirectChat()
            scope.launch {
                repository.setTypingIndicator(targetUser.id.toString(), false)
            }
        }
    }
    
    // Typing indicator timeout
    LaunchedEffect(messageText) {
        if (messageText.isNotBlank()) {
            repository.setTypingIndicator(targetUser.id.toString(), true)
            delay(3000)
            repository.setTypingIndicator(targetUser.id.toString(), false)
        } else {
            repository.setTypingIndicator(targetUser.id.toString(), false)
        }
    }
    
    fun sendMessage() {
        if (messageText.isBlank() || isSending) return
        val textToSend = messageText.trim()
        messageText = ""
        
        scope.launch {
            isSending = true
            val result = repository.sendMessage(targetUser.id.toString(), textToSend)
            result.onSuccess { newMessage ->
                messages = messages + newMessage
                // Update lastMessageId to prevent polling duplication
                lastMessageId = maxOf(lastMessageId, newMessage.id)
                listState.animateScrollToItem(messages.size - 1)
            }
            isSending = false
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        // 1. LIQUID BACKGROUND
        LiquidBackground()
        
        Column(modifier = Modifier.fillMaxSize()) {
            // 2. GLASS HEADER
            GlassHeader(targetUser = targetUser, userStatus = userStatus, onBack = onBack)
            
            // DEBUG: Botón para probar chat_status (SOLO PARA PRUEBAS)
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Mi chat_status actual: $myStatus", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { 
                            myStatus = "chatting"
                            activityManager.enterDirectChat(targetUser.id.toString()) // ✅ Con partnerId
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Chat: Activo", color = Color.White, fontSize = 12.sp)
                    }
                    
                    Button(
                        onClick = { 
                            myStatus = "offline"
                            activityManager.exitDirectChat() // ✅ Limpia partnerId
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Chat: Offline", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            
            // 3. MESSAGES LIST
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp), // Space for input
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isLoading) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                } else if (messages.isEmpty()) {
                    item {
                        EmptyStateView(targetUser.username)
                    }
                } else {
                    // Date Header (Dummy for now)
                    item {
                        DateHeader(text = "Hoy")
                    }
                    
                    items(messages) { message ->
                        val senderIdStr = message.senderId.toString()
                        val isFromMe = senderIdStr == currentUserId
                        
                        // DEBUG LOG
                        android.util.Log.d("DirectChat", "MSG: ${message.content.take(10)} | senderId=$senderIdStr | currentUserId=$currentUserId | isFromMe=$isFromMe")
                        
                        GlassMessageBubble(
                            message = message,
                            isFromMe = isFromMe
                        )
                    }
                }
            }
        }
        
        // 4. TYPING INDICATOR & INPUT AREA
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            // Typing indicator
            androidx.compose.animation.AnimatedVisibility(
                visible = isTyping,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically()
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${targetUser.username ?: "Usuario"} está escribiendo",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TypingDots()
                }
            }
            
            FloatingInputArea(
                text = messageText,
                onTextChanged = { messageText = it },
                onSend = { sendMessage() },
                isSending = isSending
            )
        }
    }
}

// ========== COMPONENTS ==========

@Composable
fun LiquidBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid")
    val animate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientMove"
    )
    
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
        // Gradient 1 (Blue)
        Box(
            modifier = Modifier
                .size(400.dp)
                .offset(x = (-50).dp, y = (-50).dp)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Blue.copy(alpha = 0.2f), Color.Transparent)
                    )
                )
        )
        // Gradient 2 (Purple)
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 50.dp, y = 50.dp)
                .blur(80.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Magenta.copy(alpha = 0.15f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun GlassHeader(targetUser: ProfileDTO, userStatus: String, onBack: () -> Unit) {
    val statusColor = when (userStatus) {
        "chatting", "online", "available" -> Color(0xFF00E676) // Verde si es cualquier estado activo
        "away" -> Color(0xFFFFC107) // Ámbar si está ausente
        else -> Color(0xFF9E9E9E) // Gris para offline
    }
    
    val statusText = when (userStatus) {
        "chatting", "online", "available" -> "En línea"
        "away" -> "Ausente"
        else -> "Desconectado"
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f))
            .blur(radius = 0.dp) // Compose blur can be expensive, simulating transparency
            .padding(top = 40.dp, bottom = 12.dp) // Status bar padding approx
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back Button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // User Info
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = targetUser.username ?: "Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor.copy(alpha = 0.9f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clip(CircleShape)
            ) {
                AvatarView(url = targetUser.avatarUrl, name = targetUser.username, size = 40)
            }
        }
    }
}


@Composable
fun GlassMessageBubble(message: MessageDTO, isFromMe: Boolean) {
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isFromMe) 20.dp else 4.dp,
        bottomEnd = if (isFromMe) 4.dp else 20.dp
    )
    
    val backgroundBrush = if (isFromMe) {
        Brush.linearGradient(
            colors = listOf(Color(0xFF2E5CFF).copy(alpha = 0.9f), Color(0xFF9D3CFF).copy(alpha = 0.9f))
        )
    } else {
        SolidColor(Color.White.copy(alpha = 0.1f))
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(backgroundBrush)
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = bubbleShape
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Reply context - Solo mostrar si hay contenido real
            val hasValidReplyContext = !message.replyContextContent.isNullOrBlank() && 
                                       message.replyContextContent != "null"
            
            if (hasValidReplyContext) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(if (isFromMe) Color.White.copy(alpha = 0.5f) else Color(0xFF2E5CFF))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        val senderName = if (!message.replyContextSenderUsername.isNullOrBlank() && 
                                            message.replyContextSenderUsername != "null") {
                            message.replyContextSenderUsername
                        } else {
                            "Usuario"
                        }
                        Text(
                            text = senderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isFromMe) Color.Yellow.copy(alpha = 0.8f) else Color(0xFFFFA500),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = message.replyContextContent ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1
                        )
                    }
                }
            }

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
                
                // Mostrar palomas para mensajes ENVIADOS por mí
                if (isFromMe) {
                    StatusIndicator(status = message.status)
                }
            }
        }
    }
}

@Composable
fun FloatingInputArea(
    text: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.1f)) // Ultra thin material simulation
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(30.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Plus Button
        IconButton(onClick = { /* Attach */ }) {
            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White.copy(alpha = 0.7f))
        }
        
        // Text Field
        BasicTextField(
            value = text,
            onValueChange = onTextChanged,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .heightIn(min = 24.dp, max = 100.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text("Mensaje...", color = Color.White.copy(alpha = 0.5f))
                    }
                    innerTextField()
                }
            }
        )
        
        // Send Button
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = if (text.isNotEmpty()) 
                            listOf(Color(0xFF2E5CFF), Color(0xFF9D3CFF)) 
                        else 
                            listOf(Color.Gray.copy(0.3f), Color.Gray.copy(0.3f))
                    )
                )
                .clickable(enabled = text.isNotEmpty() && !isSending, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp).offset(x = (-1).dp, y = 1.dp)
                )
            }
        }
    }
}

@Composable
fun DateHeader(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyStateView(name: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No hay mensajes",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = "Saluda a ${name ?: "este usuario"}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun TypingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .offset(y = offset.dp)
                    .background(Color.White.copy(alpha = 0.7f), CircleShape)
            )
        }
    }
}
