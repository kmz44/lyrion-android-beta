/*
 * Copyright (C) 2024 Lyrion
 * First time setup screen with automatic model download
 */

package io.orabel.orabelandroid.ui.screens.first_setup

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.data.ModelsDB
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.utils.ModelDownloader
import io.orabel.orabelandroid.utils.AsyncFileOperations
import org.koin.android.ext.android.inject
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirstSetupActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private val modelsDB by inject<ModelsDB>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {            
                FirstSetupScreen(
                    onAcceptTerms = { showModelSelection() },
                    onDeclineTerms = { finish() },
                    onContinueWithoutModels = { continueWithoutModels() }
                )
            }
        }
    }
    
    private fun showModelSelection() {
        // Abrir la actividad de modelos ligeros que ahora mostrará ambos modelos
        val intent = Intent(this, io.orabel.orabelandroid.ui.screens.model_setup.LightweightModelSetupActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun downloadStandardModel() {
        // Usar una URL válida y accesible - modelo Phi-3 Mini desde Hugging Face
        val modelUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
        val modelName = "Phi-3 Mini 4K Instruct"
        val modelFileName = "Phi-3-mini-4k-instruct-q4.gguf"
        
        // Iniciar descarga asíncrona
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                // Obtener la ruta del modelo usando la versión asíncrona
                val modelPath = modelsDB.getModelStoragePathAsync(modelFileName)
                val modelFile = File(modelPath)
                
                // Verificar si el modelo ya está descargado y es válido (de forma asíncrona)
                if (ModelDownloader.isValidModelDownloaded(this@FirstSetupActivity, modelFileName)) {
                    withContext(Dispatchers.Main) {
                        // El modelo ya existe y es válido, usarlo directamente
                        val modelId = modelsDB.addModel(
                            name = modelName,
                            url = modelUrl,
                            path = modelFile.absolutePath
                        )
                        
                        orabelPreferences.setSelectedModelId(modelId)
                        orabelPreferences.setFirstTimeSetupCompleted()
                        navigateToMainScreen()
                    }
                    return@launch
                }
                
                // Crear el directorio si no existe (de forma asíncrona)
                modelFile.parentFile?.let { parentDir ->
                    AsyncFileOperations.createDirectories(parentDir)
                }
                
                // Eliminar archivo existente si está corrupto (de forma asíncrona)
                if (AsyncFileOperations.fileExists(modelFile)) {
                    AsyncFileOperations.deleteFile(modelFile)
                }
                
                // Iniciar descarga desde el hilo principal
                withContext(Dispatchers.Main) {
                    // Iniciar descarga
                    ModelDownloader.downloadModel(
                        context = this@FirstSetupActivity,
                        url = modelUrl,
                        destinationFile = modelFile,
                        onProgress = { progress ->
                            // Actualizar progreso en la UI
                            runOnUiThread {
                                // Este callback se puede usar para actualizar la UI si es necesario
                                // Por ahora, el progreso se muestra mediante el DownloadManager
                            }
                        },
                        onSuccess = { filePath ->
                            // Agregar modelo a la base de datos
                            val modelId = modelsDB.addModel(
                                name = modelName,
                                url = modelUrl,
                                path = filePath
                            )
                            
                            // Seleccionar como modelo predeterminado
                            orabelPreferences.setSelectedModelId(modelId)
                            
                            // Marcar setup como completado
                            orabelPreferences.setFirstTimeSetupCompleted()
                            
                            // Navegar a pantalla principal
                            navigateToMainScreen()
                        },
                        onError = { error ->
                            runOnUiThread {
                                Toast.makeText(this@FirstSetupActivity, getString(R.string.first_setup_download_error, error), Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FirstSetupActivity, "Error durante la preparación del modelo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun continueWithoutModels() {
        // Marcar setup como completado sin descargar modelos
        orabelPreferences.setFirstTimeSetupCompleted()
        
        // Navegar a pantalla principal sin modelo seleccionado
        navigateToMainScreen()
    }
    
    private fun navigateToMainScreen() {
        val intent = Intent(this, ModernMainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

@Composable
fun FirstSetupScreen(
    onAcceptTerms: () -> Unit,
    onDeclineTerms: () -> Unit,
    onContinueWithoutModels: () -> Unit
) {
    var showTerms by remember { mutableStateOf(false) }
    var hasAcceptedTerms by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .systemBarsPadding()
    ) {
        if (!showTerms) {
            // Pantalla de bienvenida
            WelcomeSetupContent(
                onContinue = { showTerms = true }
            )
        } else if (!hasAcceptedTerms) {
            // Pantalla de términos de uso
            TermsOfUseContent(
                onAccept = { 
                    hasAcceptedTerms = true
                    isDownloading = true
                    onAcceptTerms()
                },
                onDecline = onDeclineTerms
            )
        } else {
            // Pantalla de descarga
            DownloadProgressContent(
                progress = downloadProgress,
                isDownloading = isDownloading
            )
        }
    }
}

@Composable
fun WelcomeSetupContent(
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Logo
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(PrimaryColor, PrimaryColor.copy(alpha = 0.7f))
                    ),
                    shape = RoundedCornerShape(60.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = "Lyrion",
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
        }
        
        // Título
        Text(
            text = stringResource(R.string.first_setup_welcome_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center
        )
        
        // Descripción
        Text(
            text = stringResource(R.string.first_setup_welcome_description),
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        // Información del modelo
        ModernCard(
            elevation = 6
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Configura tu experiencia personalizada",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Puedes comenzar con modelos de IA descargables o usar la aplicación sin ellos. Siempre podrás cambiar tu configuración más tarde.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Botón
        ModernButton(
            text = "Continuar",
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            style = ButtonStyle.Primary,
            leadingIcon = Icons.AutoMirrored.Filled.ArrowForward
        )
    }
}

@Composable
fun TermsOfUseContent(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Título
        Text(
            text = stringResource(R.string.first_setup_terms_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Contenido scrollable
        ModernCard(
            modifier = Modifier.weight(1f),
            elevation = 6
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = """
Términos y Condiciones de Uso de Lyrion

¡Bienvenido a Lyrion! Estos Términos y Condiciones ("Términos") rigen tu uso de la aplicación móvil Lyrion (la "Aplicación"), desarrollada por Kevin Marquez Melendez. Al descargar, instalar o utilizar Lyrion, aceptas y te comprometes a cumplir con estos Términos. Si no estás de acuerdo con alguna parte de estos Términos, no utilices la Aplicación.

1. Acerca de Lyrion y su Funcionamiento
Lyrion es una herramienta innovadora que te permite interactuar con modelos de inteligencia artificial (IA) directamente desde tu dispositivo Android, sin necesidad de conexión a internet. Está diseñada pensando en la privacidad y el rendimiento, ofreciendo una experiencia potente y totalmente local.

La Aplicación utiliza llama.cpp como su motor de inferencia principal, el cual está licenciado bajo la permisiva Licencia MIT. Lyrion es compatible con diversos modelos de lenguaje grandes (LLM) de código abierto en formato GGUF, incluyendo, pero no limitándose a: LLaMA/LLaMA 2/LLaMA 3, Mistral 7B y Mixtral MoE, modelos Phi de Microsoft, Gemma, TinyLlama, Qwen models, ChatGLM, entre otros. Muchos de estos modelos son extraídos y puestos a disposición a través de plataformas como Hugging Face.

Lyrion también incluye funcionalidades avanzadas de procesamiento offline que utilizan las siguientes tecnologías:

• Reconocimiento de Texto (OCR): Utiliza ML Kit Text Recognition de Google, que proporciona capacidades de reconocimiento de texto completamente offline. ML Kit es una biblioteca de aprendizaje automático de Google que funciona en el dispositivo sin requerir conexión a internet.

• Síntesis de Voz (TTS): Emplea el motor de síntesis de voz nativo de Android (Android TextToSpeech), que convierte texto en audio de manera local en el dispositivo, compatible con múltiples idiomas y voces.

• Traducción de Texto: Utiliza ML Kit Translate de Google, que ofrece traducción offline entre español e inglés (y viceversa) completamente en el dispositivo, sin necesidad de conexión a internet.

• Reconocimiento de Voz (STT): Implementa capacidades de reconocimiento de voz offline utilizando tecnologías nativas de Android y bibliotecas especializadas para procesamiento de audio local.

2. Privacidad y Procesamiento Local
Lyrion está diseñada para proteger tu privacidad. Todo el procesamiento de la IA ocurre directamente en tu dispositivo. Esto significa que:

No se recopila, almacena ni transmite ningún dato personal o información de tus interacciones con la IA a nuestros servidores ni a terceros. Tus chats e inferencias permanecen completamente en tu dispositivo.

No requerimos acceso a internet para las funcionalidades principales de IA, solo para la descarga inicial de modelos y actualizaciones de la Aplicación.

3. Licencia de Uso de la Aplicación
Sujeto al cumplimiento de estos Términos, te otorgamos una licencia limitada, no exclusiva, intransferible y revocable para descargar, instalar y usar la Aplicación en tu dispositivo móvil personal con el fin de interactuar con modelos de IA localmente.

4. Responsabilidad por los Modelos de IA de Terceros
Lyrion te permite descargar y utilizar modelos de lenguaje grandes desarrollados por terceros (por ejemplo, Meta Llama, Mistral, Gemma, modelos Phi de Microsoft, etc.). Estos modelos están sujetos a sus propias licencias de uso independientes y políticas de uso aceptable proporcionadas por sus respectivos creadores.

Es tu exclusiva responsabilidad revisar, comprender y cumplir con los términos de licencia de cualquier modelo de IA que descargues y utilices dentro de Lyrion. Esto incluye, pero no se limita a:

Licencia Comunitaria de Meta (para modelos Llama): Si utilizas modelos Llama de Meta, debes cumplir con la Licencia Comunitaria de Meta. Ten en cuenta que si tu empresa tiene más de 700 millones de usuarios activos mensuales, es posible que necesites una licencia comercial diferente directamente de Meta. Además, debes respetar la Política de Uso Aceptable (AUP) de Meta Llama, la cual prohíbe el uso de los modelos para actividades ilegales, dañinas, difamatorias, engañosas, que infrinjan derechos de terceros, o que promuevan la violencia, el odio o la discriminación.

Otras Licencias (incluyendo modelos Phi de Microsoft y modelos de Hugging Face): Para modelos como Mistral, Gemma, modelos Phi de Microsoft, o cualquier otro modelo GGUF compatible, especialmente aquellos extraídos de plataformas como Hugging Face, deberás consultar y cumplir con las licencias específicas de cada uno. Las licencias comunes incluyen MIT, Apache 2.0, o licencias personalizadas.

Lyrion no se hace responsable por el uso que hagas de los modelos de IA de terceros ni por el contenido generado por estos modelos. Eres el único responsable de asegurar que tu uso de dichos modelos cumpla con sus términos de licencia.

5. Contenido Generado por la IA y Tu Responsabilidad
Al utilizar Lyrion, puedes generar contenido a través de los modelos de IA. Es crucial que entiendas tus responsabilidades legales con respecto a este contenido:

Autoría y Propiedad Intelectual: En México, la Ley Federal del Derecho de Autor establece que la autoría recae únicamente en las personas físicas. Esto significa que el contenido generado exclusivamente por una IA, sin una aportación creativa humana sustancial, generalmente no es elegible para protección de derechos de autor. Eres responsable de cualquier derecho de autor o propiedad intelectual sobre el contenido que tú crees o modifiques utilizando la IA como herramienta.

Veracidad y Precisión: Los modelos de IA pueden generar información incorrecta, engañosa o desactualizada. Eres el único responsable de verificar la exactitud de cualquier contenido generado por la IA antes de confiar en él o compartirlo.

Contenido Prohibido: No utilizarás Lyrion para generar, difundir o promover contenido que sea:

Ilegal, difamatorio, calumnioso, obsceno, pornográfico o que promueva actividades ilícitas.
Que incite al odio, la violencia, la discriminación racial, étnica, de género o de cualquier otra índole.
Que viole los derechos de privacidad, publicidad, propiedad intelectual (incluyendo derechos de autor, marcas registradas, patentes) o cualquier otro derecho de terceros.
Que contenga software malicioso, virus o cualquier otro código diseñado para dañar o interferir con la funcionalidad de cualquier software o hardware.
Que se haga pasar por otra persona o entidad sin autorización.
Que busque engañar, defraudar o manipular a otros.

Cumplimiento Legal: Eres responsable de garantizar que tu uso de Lyrion y el contenido que generes cumplan con todas las leyes y regulaciones aplicables en tu jurisdicción, incluyendo, pero no limitándose a, leyes de derechos de autor, privacidad, protección de datos y leyes relativas al discurso de odio o la desinformación, tanto en México como a nivel internacional.

6. Licencias de Código Abierto y Agradecimientos
Lyrion se beneficia de las contribuciones de la comunidad de código abierto. Agradecemos a los desarrolladores de estas herramientas. A continuación, se detallan las licencias de los componentes clave:

llama.cpp: Licenciado bajo la Licencia MIT.
Copyright (c) 2023 Georgi Gerganov
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

ML Kit (Google): Utilizado para reconocimiento de texto (OCR) y traducción offline.
ML Kit Text Recognition y ML Kit Translate son productos de Google LLC, utilizados bajo los términos de servicio de Google ML Kit. Estas bibliotecas proporcionan capacidades de reconocimiento de texto y traducción completamente offline en el dispositivo.

Android TextToSpeech: Motor de síntesis de voz nativo de Android.
Desarrollado por Google como parte del sistema operativo Android, licenciado bajo la Licencia Apache 2.0.

Bibliotecas de Android: Utilizamos diversas bibliotecas de Android para funcionalidades del sistema.
Incluyendo pero no limitándose a: AndroidX, Jetpack Compose, Camera2 API, y otras bibliotecas del sistema Android, todas licenciadas bajo la Licencia Apache 2.0.

Kotlin: Lenguaje de programación principal utilizado en el desarrollo.
Desarrollado por JetBrains, licenciado bajo la Licencia Apache 2.0.

Agradecimientos especiales a:
• Georgi Gerganov y la comunidad de llama.cpp por el excelente motor de inferencia
• Google por ML Kit y las herramientas de desarrollo Android
• JetBrains por el lenguaje Kotlin
• La comunidad de código abierto por los modelos de IA disponibles
• Hugging Face por facilitar el acceso a modelos de IA de código abierto

7. Requisitos del Sistema
Para un rendimiento óptimo de Lyrion, se recomienda: Android API 26+ (Android 8.0 o superior), mínimo 4GB de RAM (más es mejor para modelos grandes), y 2GB de almacenamiento interno disponible.

8. Exclusión de Garantías
LA APLICACIÓN SE PROPORCIONA "TAL CUAL" Y "SEGÚN DISPONIBILIDAD", SIN GARANTÍAS DE NINGÚN TIPO, YA SEAN EXPRESAS O IMPLÍCITAS. HASTA DONDE LO PERMITA LA LEY APLICABLE, RECHAZAMOS TODAS LAS GARANTÍAS, INCLUYENDO, ENTRE OTRAS, LAS GARANTÍAS DE COMERCIABILIDAD, IDONEIDAD PARA UN PROPÓSITO PARTICULAR Y NO VIOLACIÓN. NO GARANTIZAMOS QUE LA APLICACIÓN FUNCIONE DE FORMA ININTERRUMPIDA, SEGURA O LIBRE DE ERRORES, NI QUE LOS RESULTADOS OBTENIDOS DE SU USO SEAN EXACTOS O FIABLES.

9. Limitación de Responsabilidad
EN NINGÚN CASO, EL DESARROLLADOR DE LYRION SERÁ RESPONSABLE POR DAÑOS INDIRECTOS, INCIDENTALES, ESPECIALES, CONSECUENTES O PUNITIVOS, INCLUYENDO, ENTRE OTROS, PÉRDIDA DE GANANCIAS, PÉRDIDA DE DATOS O FONDO DE COMERCIO, DERIVADOS DE O RELACIONADOS CON TU ACCESO O USO, O LA IMPOSIBILIDAD DE ACCEDER O USAR LA APLICACIÓN O CUALQUIER CONTENIDO GENERADO POR LA IA, INCLUSO SI SE HA ADVERTIDO DE LA POSIBILIDAD DE DICHOS DAÑOS.

10. Indemnización
Aceptas indemnizar y eximir de responsabilidad al desarrollador de Lyrion, sus afiliados, directores, empleados y agentes de cualquier reclamo, demanda, daño, obligación, pérdida, responsabilidad, costo o deuda y gastos (incluidos, entre otros, honorarios de abogados) que surjan de: (i) tu uso de y acceso a la Aplicación; (ii) tu violación de cualquier término de estos Términos; (iii) tu violación de cualquier derecho de terceros, incluido, entre otros, cualquier derecho de autor, propiedad o privacidad; o (iv) cualquier reclamo de que tu contenido o el contenido generado por la IA cause daño a un tercero.

11. Terminación
Podemos rescindir o suspender tu acceso a la Aplicación inmediatamente, sin previo aviso ni responsabilidad, por cualquier motivo, incluyendo, sin limitación, si incumples estos Términos. Tras la rescisión, tu derecho a usar la Aplicación cesará de inmediato.

12. Modificaciones a los Términos
Nos reservamos el derecho de modificar o reemplazar estos Términos en cualquier momento. Te notificaremos de cualquier cambio significativo publicando los nuevos Términos en la Aplicación o a través de otros medios razonables (por ejemplo, un aviso en la tienda de aplicaciones o correo electrónico, si proporcionaste uno). Se te pedirá que aceptes los nuevos Términos para continuar utilizando la Aplicación. El uso continuado de la Aplicación después de la fecha de entrada en vigor de los Términos modificados constituirá tu aceptación de dichos cambios.

13. Ley Aplicable y Resolución de Disputas
Estos Términos se regirán e interpretarán de acuerdo con las leyes de México, sin dar efecto a sus disposiciones sobre conflictos de leyes.

Cualquier disputa, controversia o reclamo que surja de o esté relacionado con estos Términos, incluyendo su validez, interpretación o cumplimiento, se resolverá preferentemente mediante negociación de buena fe entre las partes. Si la disputa no se resuelve mediante negociación dentro de 30 días, las partes acuerdan someterse a arbitraje vinculante administrado por la Cámara de Comercio Internacional (CCI) de conformidad con sus Reglas de Arbitraje. El arbitraje se llevará a cabo en Ciudad de México, México en idioma español. La decisión del árbitro(s) será final y vinculante para ambas partes.

14. Contacto
Si tienes alguna pregunta sobre estos Términos, puedes contactarnos en: kevinmarquezmelendez10@gmail.com
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                )
            }
        }
        
        // Botones
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ModernButton(
                text = stringResource(R.string.first_setup_terms_decline),
                onClick = onDecline,
                modifier = Modifier.weight(1f),
                style = ButtonStyle.Secondary
            )
            
            ModernButton(
                text = stringResource(R.string.first_setup_terms_accept),
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                style = ButtonStyle.Primary,
                leadingIcon = Icons.Default.Download
            )
        }
    }
}

@Composable
fun DownloadProgressContent(
    progress: Float,
    isDownloading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icono de descarga
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(PrimaryColor, PrimaryColor.copy(alpha = 0.7f))
                    ),
                    shape = RoundedCornerShape(60.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Descargando",
                tint = Color.White,
                modifier = Modifier.size(60.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Título
        Text(
            text = if (isDownloading) stringResource(R.string.first_setup_downloading) else stringResource(R.string.first_setup_download_complete),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Descripción
        Text(
            text = if (isDownloading) stringResource(R.string.first_setup_download_description) else stringResource(R.string.first_setup_ready),
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            ),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Barra de progreso
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = PrimaryColor,
                trackColor = MaterialTheme.colorScheme.surface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.first_setup_progress_format, (progress * 100).toInt()),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            )
        }
    }
}
