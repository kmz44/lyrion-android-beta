/*
 * Copyright (C) 2024 Lyrion
 * Pantalla para visualizar el Diario de Salud
 */

package io.orabel.orabelandroid.ui.screens.health

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.HealthDiaryEntry
import io.orabel.orabelandroid.data.HealthDiaryRepository
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.theme.ModernOrabelTheme
import io.orabel.orabelandroid.ui.screens.profile.ProfileActivity
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log

class HealthDiaryActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val healthDiaryRepository by inject<HealthDiaryRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.i("HealthDiaryActivity", "🏥 HealthDiaryActivity iniciada")

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                HealthDiaryScreen(
                    healthDiaryRepository = healthDiaryRepository,
                    onNavigateBack = { 
                        val intent = Intent(this@HealthDiaryActivity, ProfileActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDiaryScreen(
    healthDiaryRepository: HealthDiaryRepository,
    onNavigateBack: () -> Unit
) {
    val entries by healthDiaryRepository.getAllEntries().collectAsState(initial = emptyList())
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    LaunchedEffect(entries) {
        Log.i("HealthDiaryScreen", "📋 Entradas obtenidas: ${entries.size}")
        entries.forEachIndexed { index, entry ->
            Log.i("HealthDiaryScreen", "  [$index] ID: ${entry.id}, Categoría: ${entry.category}, Texto: ${entry.userReportText}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diario de Salud") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No hay entradas en el diario",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Los síntomas y eventos de salud se registrarán automáticamente cuando los menciones en el chat",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries) { entry ->
                    HealthDiaryEntryCard(
                        entry = entry,
                        dateFormatter = dateFormatter
                    )
                }
            }
        }
    }
}

@Composable
fun HealthDiaryEntryCard(
    entry: HealthDiaryEntry,
    dateFormatter: SimpleDateFormat
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryChip(category = entry.category)
                Text(
                    text = dateFormatter.format(entry.recordedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = entry.userReportText,
                style = MaterialTheme.typography.bodyMedium
            )
            
            if (entry.concernLevel > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nivel de preocupación: ${entry.concernLevel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            entry.concernLevel >= 3 -> MaterialTheme.colorScheme.error
                            entry.concernLevel >= 2 -> MaterialTheme.colorScheme.outline
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(category: String) {
    val (text, color) = when (category) {
        "symptom" -> "Síntoma" to MaterialTheme.colorScheme.error
        "emotional" -> "Emocional" to MaterialTheme.colorScheme.primary
        "accident" -> "Accidente" to MaterialTheme.colorScheme.tertiary
        else -> "General" to MaterialTheme.colorScheme.outline
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
