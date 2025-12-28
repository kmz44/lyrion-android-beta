/*
 * Copyright (C) 2024 Lyrion
 * Chat screen activity for Lyrion Android app
 */

package io.orabel.orabelandroid.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Spanned
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.data.Chat
import io.orabel.orabelandroid.data.ModelsDB
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.screens.search.SearchActivity // Importante para navegar a búsqueda
import io.orabel.orabelandroid.ui.screens.model_setup.ModernModelSetupActivity
import io.orabel.orabelandroid.ui.components.swipeableNavigation // Importante para swipe
import io.orabel.orabelandroid.ui.components.AppBarTitleText
import io.orabel.orabelandroid.ui.components.MediumLabelText
import io.orabel.orabelandroid.ui.screens.welcome.WelcomeActivity
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.utils.PerformanceUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Paths

class ChatActivity : ComponentActivity() {
    private val viewModel: ChatScreenViewModel by inject()
    private val modelsDB: ModelsDB by inject()
    private val orabelPreferences: OrabelPreferences by inject()
    
    // Selector de archivos para cambio de modelo
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { copyAndSetNewModel(it) }
    }
    
    // 🆕 Selector de archivos para multimodalidad (imágenes, PDFs, documentos)
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleFileSelected(uri) }
    }
    
    private fun handleFileSelected(uri: Uri) {
        val fileName = uri.lastPathSegment ?: "archivo"
        val mimeType = contentResolver.getType(uri)
        val fileType = when {
            mimeType?.startsWith("image/") == true -> "� Imagen"
            mimeType?.startsWith("application/pdf") == true -> "📄 PDF"
            mimeType?.contains("word") == true -> "📝 Word"
            mimeType?.contains("sheet") == true -> "📊 Excel"
            mimeType?.contains("presentation") == true -> "📽️ PowerPoint"
            else -> "📎 Archivo"
        }
        Toast.makeText(this, "$fileType seleccionado: $fileName", Toast.LENGTH_SHORT).show()
        viewModel.attachImage(uri, this)
    }
    
    fun openImagePicker() {
        imagePickerLauncher.launch("*/*") // Todos los tipos de archivos
    }
    
    private fun goBackToMainMenu() {
        val intent = Intent(this, ModernMainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openSearch() {
        val intent = Intent(this, SearchActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun openModelSetup() {
        val intent = Intent(this, ModernModelSetupActivity::class.java)
        startActivity(intent)
    }
    
    private fun copyAndSetNewModel(uri: Uri) {
        var fileName = ""
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            fileName = cursor.getString(nameIndex)
        }
        
        if (fileName.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    contentResolver.openInputStream(uri).use { inputStream ->
                        FileOutputStream(File(filesDir, fileName)).use { outputStream ->
                            inputStream?.copyTo(outputStream)
                        }
                    }
                    
                    val modelId = modelsDB.addModel(
                        fileName,
                        "",
                        Paths.get(filesDir.absolutePath, fileName).toString(),
                    )
                    
                    // Cambiar al nuevo modelo
                    orabelPreferences.setSelectedModelId(modelId)
                    viewModel.updateChatLLM(modelId)
                    viewModel.loadModel()
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ChatActivity,
                            getString(R.string.model_changed_success),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ChatActivity, 
                            "Error: ${e.message}", 
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Monitor chat screen performance
        PerformanceUtils.measureTime("ChatActivity.onCreate") {
            try {
                PerformanceUtils.logMemoryUsage(this, "ChatActivity")
            } catch (e: Exception) {
                // Ignore errors in release builds
            }
        }
        
        setContent {
            // Hacer el tema reactivo a los cambios en las preferencias usando StateFlow
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            val isSystemInDarkMode = androidx.compose.foundation.isSystemInDarkTheme()
            
            // Actualizar tema cuando cambie el modo del sistema
            LaunchedEffect(isSystemInDarkMode) {
                orabelPreferences.updateDarkTheme(isSystemInDarkMode)
            }
            
            // Efecto adicional para asegurar que los cambios se detecten
            LaunchedEffect(isDarkTheme) {
                android.util.Log.d("ChatActivity", "Theme changed to: ${if (isDarkTheme) "Dark" else "Light"}")
            }
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = "chat",
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable("edit-chat") {
                        EditChatSettingsScreen(
                            viewModel,
                            onBackClicked = { navController.navigateUp() },
                        )
                    }
                    composable("chat") {
                        ChatActivityScreenUI(
                            viewModel,
                            onEditChatParamsClick = { navController.navigate("edit-chat") },
                            onSelectNewModelFileClick = { viewModel.showModelSelectionDialog() },
                            onBackToMainMenuClick = { goBackToMainMenu() },
                            onImportModelsClick = { openModelSetup() },
                            onSearchClick = { openSearch() },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatActivityScreenUI(
    viewModel: ChatScreenViewModel,
    onEditChatParamsClick: () -> Unit,
    onSelectNewModelFileClick: () -> Unit,
    onBackToMainMenuClick: () -> Unit,
    onImportModelsClick: () -> Unit,
    onSearchClick: () -> Unit, // Nuevo callback
) {
    val context = LocalContext.current
    val currChat by remember { viewModel.currChatState }
    val scope = rememberCoroutineScope()
    
    // 🆕 Estado para el BottomSheet del historial de chats (reemplaza el drawer)
    var showChatHistorySheet by remember { mutableStateOf(false) }
    
    LaunchedEffect(currChat) { viewModel.loadModel() }
    
    // 🆕 BottomSheet para historial de chats (en lugar del drawer deslizable)
    if (showChatHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showChatHistorySheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = {
                // Indicador visual de arrastre para cerrar deslizando hacia abajo
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        ) {
            ChatHistorySheetContent(
                viewModel = viewModel,
                onChatSelected = { chat ->
                    viewModel.switchChat(chat)
                    showChatHistorySheet = false
                },
                onNewChatClick = {
                    val chatCount = viewModel.chatsDB.getChatsCount()
                    val newChatId = viewModel.chatsDB.addChat(
                        chatName = context.getString(R.string.untitled) + " ${chatCount + 1}"
                    )
                    viewModel.switchChat(
                        Chat(
                            id = newChatId,
                            name = context.getString(R.string.untitled) + " ${chatCount + 1}",
                            systemPrompt = context.getString(R.string.you_are_helpful_assistant)
                        )
                    )
                    showChatHistorySheet = false
                }
            )
        }
    }
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = currChat?.name ?: stringResource(R.string.select_a_chat),
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = AppFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (currChat != null && currChat?.llmModelId != -1L) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        OrabelAccent.copy(alpha = 0.1f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_lyrion_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = viewModel.modelsRepository
                                        .getModelFromId(currChat!!.llmModelId)
                                        ?.name ?: "",
                                    fontFamily = AppFontFamily,
                                    fontSize = 12.sp,
                                    color = OrabelAccent,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    // 🆕 Botón de historial de chats (reemplaza el menú hamburguesa)
                    IconButton(onClick = { showChatHistorySheet = true }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = stringResource(R.string.view_chats_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    if (currChat != null) {
                        Box {
                            IconButton(
                                onClick = { viewModel.showMoreOptionsPopupState.value = true },
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.options_desc),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            
                            if (viewModel.showMoreOptionsPopupState.value) {
                                ChatMoreOptionsPopup(
                                    onDismiss = { viewModel.showMoreOptionsPopupState.value = false },
                                    onEditClick = onEditChatParamsClick,
                                    onDeleteClick = { /* TODO: Implement delete functionality */ },
                                    onChangeModelClick = onSelectNewModelFileClick,
                                    onBackToMainClick = onBackToMainMenuClick,
                                    onImportModelsClick = onImportModelsClick
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .swipeableNavigation(
                    currentIndex = 1, // Chat está en índice 1
                    onSwipeLeft = { onBackToMainMenuClick() }, // Ir a Inicio (índice 2)
                    onSwipeRight = { onSearchClick() } // Ir a Búsqueda (índice 0)
                ),
        ) {
            if (currChat != null) {
                ScreenUI(viewModel)
            }
        }
    }
    
    var showSelectModelsListDialog by remember { viewModel.showSelectModelListDialogState }
    if (showSelectModelsListDialog) {
        val modelsList by viewModel.modelsRepository.getAvailableModels().collectAsState(emptyList())
        SelectModelsList(
            onDismissRequest = { showSelectModelsListDialog = false },
            modelsList,
            onModelListItemClick = { model ->
                viewModel.updateChatLLM(model.id)
                viewModel.loadModel()
                showSelectModelsListDialog = false
            },
            onModelDeleteClick = { model ->
                viewModel.deleteModel(model.id)
                Toast.makeText(
                    viewModel.context,
                    context.getString(R.string.model_deleted, model.name),
                    Toast.LENGTH_LONG,
                ).show()
            },
        )
    }
}

/**
 * Contenido del BottomSheet para el historial de chats
 * Similar a ChatsListView de iOS
 */
@Composable
private fun ChatHistorySheetContent(
    viewModel: ChatScreenViewModel,
    onChatSelected: (Chat) -> Unit,
    onNewChatClick: () -> Unit
) {
    val chats by viewModel.getChats().collectAsState(emptyList())
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lyrion_logo),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = Color.Unspecified
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Lyrion IA",
                color = Color.White,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Botón nuevo chat
        OutlinedButton(
            onClick = onNewChatClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.new_chat),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Título de historial
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.previous_chats),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Lista de chats
        if (chats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay chats anteriores",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = AppFontFamily
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chats.take(10)) { chat -> // Mostrar últimos 10 chats
                    ChatHistoryItem(
                        chat = chat,
                        onClick = { onChatSelected(chat) }
                    )
                }
            }
        }
    }
}

/**
 * Item individual del historial de chats
 */
@Composable
private fun ChatHistoryItem(
    chat: Chat,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Indicador visual
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    MaterialTheme.colorScheme.primary,
                    CircleShape
                )
        )
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.name,
                fontSize = 15.sp,
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = android.text.format.DateUtils.getRelativeTimeSpanString(chat.dateUsed.time).toString(),
                fontSize = 12.sp,
                fontFamily = AppFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Icono de flecha
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ColumnScope.ScreenUI(viewModel: ChatScreenViewModel) {
    MessagesList(viewModel)
    MessageInput(viewModel)
}

@Composable
private fun ColumnScope.MessagesList(viewModel: ChatScreenViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    viewModel.getChatMessages()?.let { chatMessagesFlow ->
        val messages by chatMessagesFlow.collectAsState(emptyList())
        val isGeneratingResponse by remember { viewModel.isGeneratingResponse }
        val partialResponse by remember { viewModel.partialResponse }
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size)
            }
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().weight(1f)) {
            items(messages) { chatMessage ->
                MessageListItem(
                    viewModel.markwon.render(viewModel.markwon.parse(chatMessage.message)),
                    chatMessage.isUserMessage,
                    onCopyClicked = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(context.getString(R.string.copy), chatMessage.message)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.message_copied), Toast.LENGTH_SHORT).show()
                    },
                    onShareClicked = {
                        context.startActivity(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, chatMessage.message)
                            },
                        )
                    },
                )
            }
            if (isGeneratingResponse) {
                item {
                    if (partialResponse.isNotEmpty()) {
                        MessageListItem(
                            viewModel.markwon.render(viewModel.markwon.parse(partialResponse)),
                            false,
                            {},
                            {},
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .animateItem(),
                        ) {
                            Icon(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp),
                                painter = painterResource(R.drawable.ic_lyrion_logo),
                                contentDescription = "Lyrion",
                                tint = Color.Unspecified,
                            )
                            Text(
                                text = "🧠 Orabel está pensando...",
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                fontFamily = AppFontFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyItemScope.MessageListItem(
    messageStr: Spanned,
    isUserMessage: Boolean,
    onCopyClicked: () -> Unit,
    onShareClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isUserMessage) {
        Row(modifier = modifier.fillMaxWidth().animateItem()) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(20.dp),
                    painter = painterResource(id = R.drawable.ic_lyrion_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                )
            }
            Column(modifier = Modifier) {
                ChatMessageText(
                    modifier = Modifier
                        .padding(6.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    OrabelAccent.copy(alpha = 0.1f)
                                )
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                        .fillMaxSize(),
                    textColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                    textSize = 16f,
                    message = messageStr,
                )
                Row {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.copy),
                        modifier = Modifier.clickable { onCopyClicked() },
                        fontSize = 8.sp,
                        fontFamily = AppFontFamily,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.share),
                        modifier = Modifier.clickable { onShareClicked() },
                        fontSize = 8.sp,
                        fontFamily = AppFontFamily,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().animateItem(),
            horizontalArrangement = Arrangement.End,
        ) {
            ChatMessageText(
                modifier = Modifier
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp)
                    .widthIn(max = 250.dp),
                textColor = android.graphics.Color.WHITE,
                textSize = 16f,
                message = messageStr,
            )
        }
    }
}

@Composable
private fun MessageInput(viewModel: ChatScreenViewModel) {
    val currChat by remember { viewModel.currChatState }
    val context = LocalContext.current as ChatActivity
    
    if ((currChat?.llmModelId ?: -1L) == -1L) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = stringResource(R.string.select_a_model),
            fontFamily = AppFontFamily,
        )
    } else {
        var questionText by remember { mutableStateOf("") }
        val isGeneratingResponse by remember { viewModel.isGeneratingResponse }
        val isInitializingModel by remember { viewModel.isInitializingModel }
        val keyboardController = LocalSoftwareKeyboardController.current
        
        // 🆕 Estado de archivos adjuntos
        val attachedFiles by remember { viewModel.attachedFiles }
        
        // 🆕 Verificar si el modelo actual es online (Gemini)
        val currentModel = remember(currChat?.llmModelId) {
            currChat?.llmModelId?.let { viewModel.modelsRepository.getModelFromId(it) }
        }
        val isOnlineModel = remember(currentModel) {
            currentModel?.name?.contains("Gemini", ignoreCase = true) == true ||
            currentModel?.name?.contains("Online", ignoreCase = true) == true
        }
        
        // 🎨 Contenedor vertical para preview + input
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // 🖼️ Preview de archivos adjuntos (scroll horizontal si hay múltiples)
            if (attachedFiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    attachedFiles.forEach { file ->
                        // Chip para cada archivo con ícono + nombre + botón eliminar
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val fileIcon = when {
                                file.mimeType.startsWith("image/") -> "📷"
                                file.mimeType.startsWith("application/pdf") -> "📄"
                                file.mimeType.contains("word") -> "📝"
                                file.mimeType.contains("sheet") -> "📊"
                                file.mimeType.contains("presentation") -> "📽️"
                                else -> "📎"
                            }
                            
                            Text(
                                text = fileIcon,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = file.fileName.take(15) + if (file.fileName.length > 15) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            IconButton(
                                onClick = { viewModel.removeAttachedFile(file) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Quitar ${file.fileName}",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                }
            }
        
            // 🎨 DISEÑO ESTILO WHATSAPP: Todo en una sola fila horizontal
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            // 📎 Botón adjuntar imagen (solo icono +, sin texto)
            if (isOnlineModel && !isGeneratingResponse && !isInitializingModel) {
                IconButton(
                    onClick = { context.openImagePicker() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adjuntar imagen",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // 💬 Campo de texto
            TextField(
                modifier = Modifier.weight(1f),
                value = questionText,
                onValueChange = { questionText = it },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedIndicatorColor = OrabelPrimary,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.onSurface,
                ),
                placeholder = {
                    Text(
                        text = if (isGeneratingResponse || isInitializingModel) {
                            stringResource(R.string.loading_model)
                        } else {
                            stringResource(R.string.ask_a_question)
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                },
                keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.Sentences),
            )
            
            // 🎤 Botón Gemini Live (icono de ondas/micrófono, sin texto)
            if (isOnlineModel && !isGeneratingResponse && !isInitializingModel) {
                IconButton(
                    onClick = {
                        val intent = Intent(context, io.orabel.orabelandroid.ui.screens.gemini_live.GeminiLiveActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RadioButtonChecked, // Círculo con ondas (Live)
                        contentDescription = "Gemini Live",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // ➡️ Botón enviar / Detener generación
            if (isGeneratingResponse || isInitializingModel) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                    CircularProgressIndicator(color = OrabelPrimary, modifier = Modifier.size(40.dp))
                    if (isGeneratingResponse) {
                        IconButton(onClick = { viewModel.stopGeneration() }) {
                            Icon(Icons.Default.Stop, contentDescription = stringResource(R.string.stop_desc), tint = OrabelError)
                        }
                    }
                }
            } else {
                IconButton(
                    enabled = questionText.isNotEmpty(),
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (questionText.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    onClick = {
                        keyboardController?.hide()
                        viewModel.sendUserQuery(questionText)
                        questionText = ""
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.send_text_desc),
                        tint = if (questionText.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        } // Fin Row
    } // Fin Column (preview + input)
}
