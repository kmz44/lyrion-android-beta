/*
 * Copyright (C) 2024 Lyrion
 * Activity para visualizar conocimiento educativo guardado
 */

package io.orabel.orabelandroid.ui.screens.study

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.StudyKnowledge
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.components.ModernTopBar
import io.orabel.orabelandroid.ui.theme.*
import io.objectbox.Box
import io.objectbox.BoxStore
import org.koin.android.ext.android.inject

class StudyKnowledgeActivity : ComponentActivity() {
    
    private val boxStore: BoxStore by inject()
    private val orabelPreferences by inject<OrabelPreferences>()
    
    private val studyBox: Box<StudyKnowledge> by lazy {
        boxStore.boxFor(StudyKnowledge::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(darkTheme = isDarkTheme) {
                StudyKnowledgeScreen(
                    studyBox = studyBox,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyKnowledgeScreen(
    studyBox: Box<StudyKnowledge>,
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedKnowledge by remember { mutableStateOf<StudyKnowledge?>(null) }
    
    // Obtener todos los conocimientos y agrupar por categoría
    val allKnowledge = remember { studyBox.all.sortedByDescending { it.timestamp } }
    val categories = remember { allKnowledge.groupBy { it.category } }
    
    Scaffold(
        topBar = {
            ModernTopBar(
                title = "📚 Compartir Clases",
                showBackButton = true,
                onBackClick = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        
        if (selectedKnowledge != null) {
            // Vista detallada de un conocimiento
            KnowledgeDetailView(
                knowledge = selectedKnowledge!!,
                onBack = { selectedKnowledge = null }
            )
        } else if (selectedCategory != null) {
            // Vista de lista de temas de una categoría
            CategoryKnowledgeList(
                category = selectedCategory!!,
                knowledgeList = categories[selectedCategory] ?: emptyList(),
                onKnowledgeClick = { selectedKnowledge = it },
                onBack = { selectedCategory = null }
            )
        } else {
            // Vista principal: lista de categorías
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                if (categories.isEmpty()) {
                    // Vista vacía
                    EmptyStudyState()
                } else {
                    // Estadísticas
                    StatsCard(totalKnowledge = allKnowledge.size, categories = categories.size)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Lista de categorías
                    Text(
                        text = "Categorías de Estudio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(categories.toList()) { (category, knowledgeList) ->
                            CategoryCard(
                                category = category,
                                count = knowledgeList.size,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStudyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.School,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No hay conocimiento guardado",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Empieza a guardar ejercicios y fórmulas",
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
                Text("1. Automático: Pregunta sobre matemáticas, química, etc.")
                Text("2. Manual: Usa /guardar estudio antes de tu pregunta")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ejemplo:", fontWeight = FontWeight.Bold)
                Text("/guardar estudio ¿Cómo resuelvo ecuaciones cuadráticas?")
            }
        }
    }
}

@Composable
fun StatsCard(totalKnowledge: Int, categories: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = OrabelPrimary.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.LibraryBooks,
                value = "$totalKnowledge",
                label = "Temas Guardados"
            )
            StatItem(
                icon = Icons.Default.Category,
                value = "$categories",
                label = "Categorías"
            )
        }
    }
}

@Composable
fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OrabelPrimary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
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
fun CategoryCard(category: String, count: Int, onClick: () -> Unit) {
    val categoryEmoji = when (category) {
        "matemáticas" -> "📐"
        "química" -> "🧪"
        "física" -> "⚛️"
        "redacción" -> "📝"
        "programación" -> "💻"
        else -> "📚"
    }
    
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = categoryEmoji,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(end = 16.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$count tema${if (count != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CategoryKnowledgeList(
    category: String,
    knowledgeList: List<StudyKnowledge>,
    onKnowledgeClick: (StudyKnowledge) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ModernTopBar(
            title = category.replaceFirstChar { it.uppercase() },
            showBackButton = true,
            onBackClick = onBack
        )
        
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(knowledgeList) { knowledge ->
                KnowledgeCard(
                    knowledge = knowledge,
                    onClick = { onKnowledgeClick(knowledge) }
                )
            }
        }
    }
}

@Composable
fun KnowledgeCard(knowledge: StudyKnowledge, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = knowledge.topic,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = knowledge.content.take(150) + "...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (knowledge.formulas.isNotEmpty()) {
                    Chip(text = "🔢 ${knowledge.formulas.split("\n").size} fórmulas")
                }
                if (knowledge.difficulty.isNotEmpty()) {
                    Chip(text = "📊 ${knowledge.difficulty}")
                }
            }
        }
    }
}

@Composable
fun Chip(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
fun KnowledgeDetailView(knowledge: StudyKnowledge, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ModernTopBar(
            title = "Detalle",
            showBackButton = true,
            onBackClick = onBack
        )
        
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = OrabelPrimary.copy(alpha = 0.1f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = knowledge.topic,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = knowledge.category.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = OrabelPrimary
                        )
                    }
                }
            }
            
            item {
                SectionCard(title = "📖 Contenido Completo", content = knowledge.content)
            }
            
            if (knowledge.formulas.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "🔢 Fórmulas",
                        content = knowledge.formulas,
                        isFormula = true
                    )
                }
            }
            
            if (knowledge.examples.isNotEmpty()) {
                item {
                    SectionCard(title = "💡 Ejemplos", content = knowledge.examples)
                }
            }
            
            if (knowledge.keywords.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🏷️ Palabras Clave", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = knowledge.keywords.replace(",", " • "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String, content: String, isFormula: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFormula) 
                MaterialTheme.colorScheme.tertiaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = content,
                style = if (isFormula) 
                    MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                else 
                    MaterialTheme.typography.bodyMedium
            )
        }
    }
}
