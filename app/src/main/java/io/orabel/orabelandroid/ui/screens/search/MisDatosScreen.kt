package io.orabel.orabelandroid.ui.screens.search

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.orabel.orabelandroid.data.social.ProfileDTO
import io.orabel.orabelandroid.data.social.SocialRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MisDatosScreen(
    repository: SocialRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Estado del perfil
    var profile by remember { mutableStateOf<ProfileDTO?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Campos editables
    var nombre by remember { mutableStateOf("") }
    var apellido by remember { mutableStateOf("") }
    var edad by remember { mutableStateOf("") }
    var altura by remember { mutableStateOf("") }
    var peso by remember { mutableStateOf("") }
    var estadoCivil by remember { mutableStateOf("") }
    var pais by remember { mutableStateOf("") }
    var estadoRegion by remember { mutableStateOf("") }
    var ocupacion by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    
    // Imagen
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBannerUri by remember { mutableStateOf<Uri?>(null) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedBannerUri = uri
    }

    LaunchedEffect(Unit) {
        profile = repository.fetchCurrentUserProfile()
        profile?.let {
            nombre = it.nombre ?: ""
            apellido = it.apellido ?: ""
            edad = it.edad?.toString() ?: ""
            altura = it.altura_cm?.toString() ?: ""
            peso = it.peso_kg?.toString() ?: ""
            estadoCivil = it.estadoCivil ?: ""
            pais = it.pais ?: ""
            estadoRegion = it.estadoRegion ?: ""
            ocupacion = it.occupation ?: ""
            bio = it.bio ?: ""
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Datos del Usuario") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFFF2F2F7)) // iOS System Grouped Background
            ) {
                // Sección Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { bannerPickerLauncher.launch("image/*") }
                ) {
                    if (selectedBannerUri != null) {
                        AsyncImage(
                            model = selectedBannerUri,
                            contentDescription = "Banner seleccionado",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (!profile?.bannerUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = profile?.bannerUrl,
                            contentDescription = "Banner actual",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Toca para agregar banner", color = Color.White)
                        }
                    }
                    
                    // Overlay icon
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar Banner",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Sección Avatar (Superpuesto visualmente en el diseño original, aqui simplificado debajo del banner)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .offset(y = (-50).dp), // Subir avatar para superponer al banner
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background) // Borde
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(Color.Gray)
                                .clickable { imagePickerLauncher.launch("image/*") }
                        ) {
                            if (selectedImageUri != null) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Avatar seleccionado",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (!profile?.avatarUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = profile?.avatarUrl,
                                    contentDescription = "Avatar actual",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                            Text("Cambiar Foto")
                        }
                    }
                }
                
                Text(
                    text = "Actualiza tu información personal.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Sección Información Básica
                FormSection(title = "INFORMACIÓN BÁSICA") {
                    FormRow(label = "Nombre", value = nombre, onValueChange = { nombre = it })
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    FormRow(label = "Apellido", value = apellido, onValueChange = { apellido = it })
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    FormRow(label = "Edad", value = edad, onValueChange = { edad = it }, keyboardType = KeyboardType.Number)
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    FormRow(label = "Estado Civil", value = estadoCivil, onValueChange = { estadoCivil = it })
                }

                // Sección Físico
                FormSection(title = "FÍSICO") {
                    FormRow(label = "Altura (cm)", value = altura, onValueChange = { altura = it }, keyboardType = KeyboardType.Number)
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    FormRow(label = "Peso (kg)", value = peso, onValueChange = { peso = it }, keyboardType = KeyboardType.Number)
                }

                // Sección Ubicación
                FormSection(title = "UBICACIÓN") {
                    FormRow(label = "País", value = pais, onValueChange = { pais = it })
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    FormRow(label = "Estado/Provincia", value = estadoRegion, onValueChange = { estadoRegion = it })
                }

                // Sección Perfil Profesional
                FormSection(title = "PERFIL PROFESIONAL") {
                    FormRow(label = "Ocupación", value = ocupacion, onValueChange = { ocupacion = it }, placeholder = "Ej. Digital Creator")
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Biografía", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            maxLines = 5
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Botón Guardar
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            try {
                                val currentUserId = repository.getCurrentUserUUID()?.toString()
                                if (currentUserId != null) {
                                    var avatarUrlToSave = profile?.avatarUrl
                                    var bannerUrlToSave = profile?.bannerUrl
                                    
                                    // 1. Subir Avatar
                                    if (selectedImageUri != null) {
                                        context.contentResolver.openInputStream(selectedImageUri!!)?.use { inputStream ->
                                            val bytes = inputStream.readBytes()
                                            val result = repository.uploadAvatar(currentUserId, bytes)
                                            result.onSuccess { url -> avatarUrlToSave = url }
                                            result.onFailure { Toast.makeText(context, "Error subiendo avatar", Toast.LENGTH_SHORT).show() }
                                        }
                                    }

                                    // 2. Subir Banner
                                    if (selectedBannerUri != null) {
                                        context.contentResolver.openInputStream(selectedBannerUri!!)?.use { inputStream ->
                                            val bytes = inputStream.readBytes()
                                            val result = repository.uploadBanner(currentUserId, bytes)
                                            result.onSuccess { url -> bannerUrlToSave = url }
                                            result.onFailure { Toast.makeText(context, "Error subiendo banner", Toast.LENGTH_SHORT).show() }
                                        }
                                    }

                                    // 3. Actualizar datos
                                    val newProfile = profile!!.copy(
                                        nombre = nombre,
                                        apellido = apellido,
                                        edad = edad.toIntOrNull(),
                                        altura_cm = altura.toIntOrNull(),
                                        peso_kg = peso.toIntOrNull(),
                                        estadoCivil = estadoCivil,
                                        pais = pais,
                                        estadoRegion = estadoRegion,
                                        occupation = ocupacion,
                                        bio = bio,
                                        avatarUrl = avatarUrlToSave,
                                        bannerUrl = bannerUrlToSave
                                    )
                                    
                                    val updateResult = repository.updateUserProfile(currentUserId, newProfile)
                                    updateResult.onSuccess {
                                        Toast.makeText(context, "Perfil guardado", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                    updateResult.onFailure {
                                        Toast.makeText(context, "Error guardando perfil", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAF52DE))
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardando...")
                    } else {
                        Text("Guardar Cambios")
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().background(Color.White)
        ) {
            content()
        }
    }
}

@Composable
fun FormRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.width(100.dp))
        Spacer(modifier = Modifier.width(16.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f)
        )
    }
}
