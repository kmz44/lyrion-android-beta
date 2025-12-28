/*
 * Copyright (C) 2024 Lyrion
 * Actividad para compartir informe de salud mediante código QR
 */

package io.orabel.orabelandroid.health

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.data.HealthDiaryRepository
import io.orabel.orabelandroid.ui.theme.ModernOrabelTheme
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import io.orabel.orabelandroid.ui.screens.search.SearchActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject
import android.util.Log

class HealthReportShareActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val healthReportGenerator by inject<HealthReportGenerator>()
    private val localHealthServer by inject<LocalHealthServer>()
    private val healthDiaryRepository by inject<HealthDiaryRepository>()
    
    companion object {
        private const val TAG = "HealthReportShare"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.i(TAG, "🏥 Iniciando sistema de compartir informe de salud")

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(darkTheme = isDarkTheme) {
                HealthReportShareScreen(
                    healthReportGenerator = healthReportGenerator,
                    localHealthServer = localHealthServer,
                    healthDiaryRepository = healthDiaryRepository,
                    onNavigateBack = { 
                        finish()
                        startActivity(Intent(this@HealthReportShareActivity, SearchActivity::class.java))
                    },
                    onClose = { finish() }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🛑 Actividad destruida")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthReportShareScreen(
    healthReportGenerator: HealthReportGenerator,
    localHealthServer: LocalHealthServer,
    healthDiaryRepository: HealthDiaryRepository,
    onNavigateBack: () -> Unit,
    onClose: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var serverUrl by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var serverInfo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    
    val context = LocalContext.current

    // Función para generar código QR
    fun generateQRCode(url: String): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            Log.e("QRGenerator", "Error generando QR: ${e.message}")
            null
        }
    }

    // Inicializar el proceso
    LaunchedEffect(Unit) {
        // Función de debugging para verificar el estado de la base de datos
        suspend fun debugHealthDatabase() {
            try {
                Log.i("HealthDebug", "🔍 Verificando base de datos de salud...")
                
                // Obtener todas las entradas directamente del repositorio
                val entries = healthDiaryRepository.getAllEntries().first()
                Log.i("HealthDebug", "📊 Total de entradas encontradas: ${entries.size}")
                
                entries.forEachIndexed { index, entry ->
                    Log.i("HealthDebug", "📝 Entrada $index:")
                    Log.i("HealthDebug", "  - Texto: ${entry.userReportText}")
                    Log.i("HealthDebug", "  - Fecha: ${entry.recordedAt}")
                    Log.i("HealthDebug", "  - Categoría: ${entry.category}")
                    Log.i("HealthDebug", "  - Info extraída: ${entry.extractedInfo}")
                    Log.i("HealthDebug", "  - Nivel preocupación: ${entry.concernLevel}")
                }
                
                if (entries.isEmpty()) {
                    Log.w("HealthDebug", "⚠️ NO HAY ENTRADAS EN LA BASE DE DATOS!")
                    Log.w("HealthDebug", "💡 Verifique que el usuario haya reportado síntomas en el chat")
                }
                
                Log.i("HealthDebug", "✅ Verificación de BD completada")
            } catch (e: Exception) {
                Log.e("HealthDebug", "❌ Error verificando BD: ${e.message}", e)
            }
        }
        
        try {
            Log.i("HealthReportShare", "🔄 Iniciando proceso de generación de informe...")
            
            // DEBUGGING: Verificar estado de la base de datos primero
            debugHealthDatabase()
            
            // Generar informe de salud
            val healthReport = healthReportGenerator.generateHealthReport()
            Log.i("HealthReportShare", "✅ Informe generado")
            
            // Iniciar servidor local
            val url = localHealthServer.startServer(healthReport)
            if (url != null) {
                serverUrl = url
                serverInfo = localHealthServer.getServerInfo()
                
                // Generar código QR
                qrBitmap = generateQRCode(url)
                Log.i("HealthReportShare", "✅ QR generado para URL: $url")
            } else {
                errorMessage = "Error iniciando servidor local"
                Log.e("HealthReportShare", "❌ Error iniciando servidor")
            }
            
        } catch (e: Exception) {
            errorMessage = "Error: ${e.message}"
            Log.e("HealthReportShare", "❌ Error en proceso: ${e.message}", e)
        } finally {
            isLoading = false
        }
    }

    // Función de debugging para verificar el estado de la base de datos
    suspend fun debugHealthDatabase() {
        try {
            Log.i("HealthDebug", "🔍 Verificando base de datos de salud...")
            
            // Obtener todas las entradas directamente del repositorio
            val entries = healthDiaryRepository.getAllEntries().first()
            Log.i("HealthDebug", "� Total de entradas encontradas: ${entries.size}")
            
            entries.forEachIndexed { index, entry ->
                Log.i("HealthDebug", "📝 Entrada $index:")
                Log.i("HealthDebug", "  - Texto: ${entry.userReportText}")
                Log.i("HealthDebug", "  - Fecha: ${entry.recordedAt}")
                Log.i("HealthDebug", "  - Categoría: ${entry.category}")
                Log.i("HealthDebug", "  - Info extraída: ${entry.extractedInfo}")
                Log.i("HealthDebug", "  - Nivel preocupación: ${entry.concernLevel}")
            }
            
            if (entries.isEmpty()) {
                Log.w("HealthDebug", "⚠️ NO HAY ENTRADAS EN LA BASE DE DATOS!")
                Log.w("HealthDebug", "💡 Verifique que el usuario haya reportado síntomas en el chat")
            }
            
            Log.i("HealthDebug", "✅ Verificación de BD completada")
        } catch (e: Exception) {
            Log.e("HealthDebug", "❌ Error verificando BD: ${e.message}", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compartir Informe de Salud") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                isLoading -> {
                    LoadingSection()
                }
                errorMessage != null -> {
                    ErrorSection(errorMessage = errorMessage!!, onRetry = {
                        isLoading = true
                        errorMessage = null
                    })
                }
                serverUrl != null && qrBitmap != null -> {
                    SuccessSection(
                        qrBitmap = qrBitmap!!,
                        serverUrl = serverUrl!!,
                        serverInfo = serverInfo
                    )
                }
                else -> {
                    ErrorSection(errorMessage = "Estado desconocido", onRetry = {})
                }
            }
        }
    }
}

@Composable
fun LoadingSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            color = OrabelPrimary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "🔄 Generando informe de salud...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Recopilando historial médico y activando servidor seguro",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ErrorSection(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "❌ Error",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = OrabelPrimary)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reintentar")
        }
    }
}

@Composable
fun SuccessSection(
    qrBitmap: Bitmap,
    serverUrl: String,
    serverInfo: Map<String, String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header informativo
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🏥 Servidor Médico Activado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "El informe está listo para ser compartido de forma segura",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Código QR
        Card(
            modifier = Modifier.size(280.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Código QR para acceder al informe",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "📱 Escanear con cámara",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Instrucciones
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "📋 Instrucciones para el Médico",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                InstructionItem("1️⃣", "Conectarse a la misma red Wi-Fi")
                InstructionItem("2️⃣", "Escanear el código QR con la cámara")
                InstructionItem("3️⃣", "Acceder al informe médico completo")
                InstructionItem("🔒", "El acceso se cierra automáticamente al salir")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Información técnica (colapsible)
        var showTechInfo by remember { mutableStateOf(false) }
        
        TextButton(onClick = { showTechInfo = !showTechInfo }) {
            Icon(
                imageVector = if (showTechInfo) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Información técnica")
        }

        if (showTechInfo) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "🔧 Detalles del Servidor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("🌐 URL: $serverUrl", style = MaterialTheme.typography.bodySmall)
                    Text("🔑 Token: ${serverInfo["token"]?.take(8)}...", style = MaterialTheme.typography.bodySmall)
                    Text("📡 Puerto: ${serverInfo["port"]}", style = MaterialTheme.typography.bodySmall)
                    Text("⚡ Estado: ${serverInfo["status"]}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Advertencias de seguridad
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔒 Información de Seguridad",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Sus datos nunca salen del dispositivo\n• Solo accesible en la red Wi-Fi local\n• El servidor se autodestruye al cerrar\n• Solo para uso médico profesional",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
fun InstructionItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
