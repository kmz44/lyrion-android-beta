/*
 * Actividad para seleccionar y descargar modelos ultra ligeros
 */

package io.orabel.orabelandroid.ui.screens.model_setup

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import io.orabel.orabelandroid.data.LightweightModelInfo
import io.orabel.orabelandroid.data.ModelsDB
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.components.AppProgressDialog
import io.orabel.orabelandroid.ui.components.LightweightModelsList
import io.orabel.orabelandroid.ui.components.hideProgressDialog
import io.orabel.orabelandroid.ui.components.showProgressDialog
import io.orabel.orabelandroid.ui.components.setProgressDialogText
import io.orabel.orabelandroid.ui.components.setProgressDialogTitle
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.theme.ModernOrabelTheme
import io.orabel.orabelandroid.ui.theme.OrabelBackgroundDark
import io.orabel.orabelandroid.ui.theme.OrabelPrimary
import io.orabel.orabelandroid.ui.theme.OrabelSecondary
import io.orabel.orabelandroid.utils.ModelDownloader
import io.orabel.orabelandroid.utils.AsyncFileOperations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class LightweightModelSetupActivity : ComponentActivity(), KoinComponent {
    private val modelsDB: ModelsDB by inject()
    private val orabelPreferences: OrabelPreferences by inject()
    private var isDownloading = mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    OrabelPrimary,
                                    OrabelSecondary,
                                    OrabelBackgroundDark
                                )
                            )
                        )
                ) {
                    val models = modelsDB.getLightweightModels()
                    LightweightModelsList(
                        models = models,
                        onModelSelected = { model -> downloadModel(model) },
                        onContinueWithoutModels = { continueWithoutModels() },
                        isDownloading = isDownloading.value
                    )
                    AppProgressDialog()
                }
            }
        }
    }
    
    private fun downloadModel(model: LightweightModelInfo) {
        Log.d("LightweightModelSetup", "Starting download for model: ${model.name}")
        val fileName = "${model.name}.gguf"
        
        isDownloading.value = true
        setProgressDialogTitle("Descargando modelo")
        setProgressDialogText("Descargando ${model.name} (${model.sizeInMB} MB)...")
        showProgressDialog()
        
        // Usar corrutina para operaciones asíncronas
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val modelPath = modelsDB.getModelStoragePathAsync(fileName)
                val destinationFile = java.io.File(modelPath)
                
                withContext(Dispatchers.Main) {
                    ModelDownloader.downloadModel(
                        context = this@LightweightModelSetupActivity,
                        url = model.url,
                        destinationFile = destinationFile,
                        onProgress = { progress ->
                            // Actualizar progreso si es necesario
                        },
                        onSuccess = { path ->
                            Log.d("LightweightModelSetup", "Download success callback triggered with path: $path")
                            
                            // Hacer todo el procesamiento en un solo bloque para evitar problemas de concurrencia
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val modelId = modelsDB.addModel(model.name, model.url, path)
                                    orabelPreferences.setSelectedModelId(modelId)
                                    orabelPreferences.setFirstTimeSetupCompleted()
                                    
                                    Log.d("LightweightModelSetup", "Model saved with ID: $modelId")
                                    
                                    // Navegar en el hilo principal
                                    withContext(Dispatchers.Main) {
                                        try {
                                            isDownloading.value = false
                                            hideProgressDialog()
                                            
                                            Log.d("LightweightModelSetup", "Navigating to ChatActivity")
                                            
                                            // Crear intent y navegar
                                            val intent = Intent(this@LightweightModelSetupActivity, ChatActivity::class.java).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                            }
                                            startActivity(intent)
                                            
                                            // Mostrar Toast después de iniciar la navegación
                                            Toast.makeText(
                                                this@LightweightModelSetupActivity,
                                                "¡Modelo cargado! Abriendo chatbot...",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            
                                            // Cerrar la actividad actual
                                            finish()
                                            
                                            Log.d("LightweightModelSetup", "Navigation completed successfully")
                                            
                                        } catch (navError: Exception) {
                                            Log.e("LightweightModelSetup", "Error during navigation", navError)
                                            Toast.makeText(
                                                this@LightweightModelSetupActivity,
                                                "Error al abrir el chatbot. Reinicia la aplicación.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("LightweightModelSetup", "Error in onSuccess callback", e)
                                    withContext(Dispatchers.Main) {
                                        isDownloading.value = false
                                        hideProgressDialog()
                                        Toast.makeText(
                                            this@LightweightModelSetupActivity,
                                            "Error al configurar el modelo: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        onError = { error ->
                            isDownloading.value = false
                            hideProgressDialog()
                            Toast.makeText(
                                this@LightweightModelSetupActivity,
                                "Error al descargar: $error",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isDownloading.value = false
                    hideProgressDialog()
                    Toast.makeText(
                        this@LightweightModelSetupActivity,
                        "Error durante la preparación: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    private fun continueWithoutModels() {
        // Marcar setup como completado sin descargar modelos
        orabelPreferences.setFirstTimeSetupCompleted()
        
        // Navegar a la pantalla de chat sin modelo seleccionado
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
        finish()
    }
}
