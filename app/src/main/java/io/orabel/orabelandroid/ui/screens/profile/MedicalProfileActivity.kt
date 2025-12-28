package io.orabel.orabelandroid.ui.screens.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.data.UserMedicalProfile
import io.orabel.orabelandroid.data.UserMedicalProfileRepository
import io.orabel.orabelandroid.ui.components.ModernTopBar
import io.orabel.orabelandroid.ui.theme.ModernOrabelTheme
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.*

class MedicalProfileActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val userMedicalProfileRepository by inject<UserMedicalProfileRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                MedicalProfileScreen(
                    userMedicalProfileRepository = userMedicalProfileRepository,
                    onNavigateBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalProfileScreen(
    userMedicalProfileRepository: UserMedicalProfileRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    
    // Estados para el formulario
    var fullName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("No especificado") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var bloodType by remember { mutableStateOf("No especificado") }
    var allergies by remember { mutableStateOf("") }
    var currentMedications by remember { mutableStateOf("") }
    var chronicConditions by remember { mutableStateOf("") }
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }
    var emergencyContactRelation by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    
    // Cargar perfil existente
    LaunchedEffect(Unit) {
        try {
            val existingProfile = userMedicalProfileRepository.getUserProfileSync()
            existingProfile?.let { profile ->
                fullName = profile.fullName
                age = if (profile.age > 0) profile.age.toString() else ""
                gender = profile.gender
                height = if (profile.height > 0) profile.height.toString() else ""
                weight = if (profile.weight > 0) profile.weight.toString() else ""
                bloodType = profile.bloodType
                allergies = profile.allergies
                currentMedications = profile.currentMedications
                chronicConditions = profile.chronicConditions
                emergencyContactName = profile.emergencyContactName
                emergencyContactPhone = profile.emergencyContactPhone
                emergencyContactRelation = profile.emergencyContactRelation
                notes = profile.notes
            }
        } catch (e: Exception) {
            // Perfil nuevo, mantener valores vacíos
        } finally {
            isLoading = false
        }
    }
    
    val genderOptions = listOf("Masculino", "Femenino", "Otro", "No especificado")
    val bloodTypeOptions = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "No especificado")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil Médico") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "👤 Información Personal",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Complete su perfil médico para generar reportes de salud más completos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                // Datos Personales
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📋 Datos Personales",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text("Nombre completo") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = age,
                            onValueChange = { if (it.all { char -> char.isDigit() }) age = it },
                            label = { Text("Edad") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Dropdown para género
                        var genderExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = genderExpanded,
                            onExpandedChange = { genderExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = gender,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Género") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = genderExpanded,
                                onDismissRequest = { genderExpanded = false }
                            ) {
                                genderOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            gender = option
                                            genderExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Datos Físicos
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📏 Datos Físicos",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        OutlinedTextField(
                            value = height,
                            onValueChange = { 
                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    height = it
                                }
                            },
                            label = { Text("Estatura (metros, ej: 1.75)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = weight,
                            onValueChange = { 
                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    weight = it
                                }
                            },
                            label = { Text("Peso (kg, ej: 70.5)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Mostrar IMC calculado
                        if (height.isNotEmpty() && weight.isNotEmpty()) {
                            val heightValue = height.toDoubleOrNull()
                            val weightValue = weight.toDoubleOrNull()
                            if (heightValue != null && weightValue != null && heightValue > 0) {
                                val bmi = weightValue / (heightValue * heightValue)
                                val bmiCategory = when {
                                    bmi < 18.5 -> "Bajo peso"
                                    bmi < 25.0 -> "Peso normal"
                                    bmi < 30.0 -> "Sobrepeso"
                                    else -> "Obesidad"
                                }
                                
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Padding(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = "IMC: ${String.format("%.1f", bmi)} ($bmiCategory)",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Información Médica Crítica
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "⚠️ Información Médica Crítica",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        // Dropdown para tipo de sangre
                        var bloodTypeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = bloodTypeExpanded,
                            onExpandedChange = { bloodTypeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = bloodType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Tipo de sangre") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bloodTypeExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = bloodTypeExpanded,
                                onDismissRequest = { bloodTypeExpanded = false }
                            ) {
                                bloodTypeOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            bloodType = option
                                            bloodTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        OutlinedTextField(
                            value = allergies,
                            onValueChange = { allergies = it },
                            label = { Text("Alergias (separadas por comas)") },
                            placeholder = { Text("Ej: Penicilina, Nueces, Polen") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        
                        OutlinedTextField(
                            value = currentMedications,
                            onValueChange = { currentMedications = it },
                            label = { Text("Medicamentos actuales") },
                            placeholder = { Text("Ej: Ibuprofeno 600mg, Vitamina D") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                        
                        OutlinedTextField(
                            value = chronicConditions,
                            onValueChange = { chronicConditions = it },
                            label = { Text("Condiciones crónicas") },
                            placeholder = { Text("Ej: Hipertensión, Diabetes tipo 2") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                }
                
                // Contacto de Emergencia
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📞 Contacto de Emergencia",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        OutlinedTextField(
                            value = emergencyContactName,
                            onValueChange = { emergencyContactName = it },
                            label = { Text("Nombre del contacto") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = emergencyContactPhone,
                            onValueChange = { emergencyContactPhone = it },
                            label = { Text("Teléfono del contacto") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        OutlinedTextField(
                            value = emergencyContactRelation,
                            onValueChange = { emergencyContactRelation = it },
                            label = { Text("Relación") },
                            placeholder = { Text("Ej: Esposa, Hijo, Hermano") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Notas adicionales
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "📝 Notas Adicionales",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Información adicional") },
                            placeholder = { Text("Cualquier información médica adicional relevante") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )
                    }
                }
                
                // Botón Guardar
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            try {
                                val profile = UserMedicalProfile(
                                    fullName = fullName.trim(),
                                    age = age.toIntOrNull() ?: 0,
                                    gender = gender,
                                    height = height.toDoubleOrNull() ?: 0.0,
                                    weight = weight.toDoubleOrNull() ?: 0.0,
                                    bloodType = bloodType,
                                    allergies = allergies.trim().ifEmpty { "Ninguna conocida" },
                                    currentMedications = currentMedications.trim().ifEmpty { "Ninguno" },
                                    chronicConditions = chronicConditions.trim().ifEmpty { "Ninguna" },
                                    emergencyContactName = emergencyContactName.trim(),
                                    emergencyContactPhone = emergencyContactPhone.trim(),
                                    emergencyContactRelation = emergencyContactRelation.trim(),
                                    notes = notes.trim()
                                )
                                
                                userMedicalProfileRepository.saveUserProfile(profile)
                                showSuccessMessage = true
                            } catch (e: Exception) {
                                // Manejar error
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving && fullName.isNotBlank()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isSaving) "Guardando..." else "Guardar Perfil Médico")
                }
                
                // Mensaje de éxito
                if (showSuccessMessage) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Padding(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "✅ Perfil médico guardado exitosamente",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    
                    LaunchedEffect(showSuccessMessage) {
                        kotlinx.coroutines.delay(3000)
                        showSuccessMessage = false
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Padding(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier, content = { content() })
}
