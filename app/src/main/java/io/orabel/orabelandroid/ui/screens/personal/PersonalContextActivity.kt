/*
 * Copyright (C) 2024 Lyrion
 * Activity para visualizar y gestionar contexto personal
 */

package io.orabel.orabelandroid.ui.screens.personal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.PersonalContext
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.components.ModernTopBar
import io.orabel.orabelandroid.ui.theme.*
import io.objectbox.Box
import io.objectbox.BoxStore
import org.koin.android.ext.android.inject

class PersonalContextActivity : ComponentActivity() {
    
    private val boxStore: BoxStore by inject()
    private val orabelPreferences by inject<OrabelPreferences>()
    
    private val personalBox: Box<PersonalContext> by lazy {
        boxStore.boxFor(PersonalContext::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(darkTheme = isDarkTheme) {
                PersonalContextScreen(
                    personalBox = personalBox,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalContextScreen(
    personalBox: Box<PersonalContext>,
    onBack: () -> Unit
) {
    // Obtener todos los contextos activos
    val allContext = remember { 
        personalBox.all
            .filter { it.isActive }
            .sortedByDescending { it.relevanceScore }
    }
    
    // Agrupar por tipo
    val contextByType = remember { allContext.groupBy { it.contextType } }
    
    // Extraer información especial
    val userName = remember { allContext.find { it.key == "name" }?.value }
    val partnerName = remember { allContext.find { it.key == "partner_name" }?.value }
    
    Scaffold(
        topBar = {
            ModernTopBar(
                title = "👤 Mi Perfil Personal",
                showBackButton = true,
                onBackClick = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (allContext.isEmpty()) {
                // Vista vacía
                EmptyPersonalState()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header con info principal
                    item {
                        UserHeaderCard(userName = userName, partnerName = partnerName)
                    }
                    
                    // Estadísticas
                    item {
                        PersonalStatsCard(
                            totalItems = allContext.size,
                            categories = contextByType.size
                        )
                    }
                    
                    // Categorías de información
                    contextByType.forEach { (type, contexts) ->
                        item {
                            PersonalCategoryCard(
                                type = type,
                                contexts = contexts
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPersonalState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No hay información personal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Empieza a compartir información para respuestas personalizadas",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("💡 Formas de guardar:", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Automático: Menciona tu nombre, gustos, etc.")
                Text("2. Manual: /guardar personal + tu información")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ejemplo:", fontWeight = FontWeight.Bold)
                Text("/guardar personal Me llamo Juan y me gusta el café")
            }
        }
    }
}

@Composable
fun UserHeaderCard(userName: String?, partnerName: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = OrabelPrimary.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = OrabelPrimary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = userName ?: "Usuario",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (partnerName != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "❤️ Pareja: $partnerName",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun PersonalStatsCard(totalItems: Int, categories: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            PersonalStatItem(
                icon = Icons.Default.Info,
                value = "$totalItems",
                label = "Datos Guardados"
            )
            PersonalStatItem(
                icon = Icons.Default.Category,
                value = "$categories",
                label = "Categorías"
            )
        }
    }
}

@Composable
fun PersonalStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OrabelPrimary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = OrabelPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun PersonalCategoryCard(type: String, contexts: List<PersonalContext>) {
    val typeInfo = getTypeInfo(type)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = typeInfo.emoji,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column {
                    Text(
                        text = typeInfo.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${contexts.size} elemento${if (contexts.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            contexts.take(5).forEach { context ->
                PersonalContextItem(context)
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (contexts.size > 5) {
                Text(
                    text = "... y ${contexts.size - 5} más",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun PersonalContextItem(context: PersonalContext) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = context.key.replace("_", " ").replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = OrabelPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = context.value,
                style = MaterialTheme.typography.bodyMedium
            )
            if (context.notes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = context.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

data class TypeInfo(val emoji: String, val title: String)

fun getTypeInfo(type: String): TypeInfo {
    return when (type) {
        "user_info" -> TypeInfo("👤", "Información Personal")
        "relationship" -> TypeInfo("❤️", "Relaciones")
        "seduction" -> TypeInfo("💕", "Romance")
        "gifts" -> TypeInfo("🎁", "Regalos e Ideas")
        "preferences" -> TypeInfo("⭐", "Preferencias")
        "daily_life" -> TypeInfo("🌤️", "Vida Diaria")
        else -> TypeInfo("📌", type.replaceFirstChar { it.uppercase() })
    }
}
