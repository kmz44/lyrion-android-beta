package io.orabel.orabelandroid.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.orabel.orabelandroid.MainActivity
import io.orabel.orabelandroid.R
import io.orabel.orabelandroid.ui.theme.OrabelTheme
import io.orabel.orabelandroid.data.PersonalContext
import io.objectbox.Box
import io.objectbox.BoxStore
import org.koin.android.ext.android.inject
import kotlinx.coroutines.launch

/**
 * Activity de inicio de sesión con Google usando Supabase Auth
 * Requiere autenticación antes de acceder a la aplicación
 */
class LoginActivity : ComponentActivity() {

    private var isAuthInProgress by mutableStateOf(false)
    
    // Inyectar BoxStore para guardar nombre automáticamente
    private val boxStore: BoxStore by inject()
    private val personalBox: Box<PersonalContext> by lazy {
        boxStore.boxFor(PersonalContext::class.java)
    }
    
    // 🔥 NUEVO: Gestor de sesión offline para persistencia sin internet
    private val offlineSessionManager by lazy { OfflineSessionManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verificar si ya hay sesión activa
        lifecycleScope.launch {
            try {
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                if (session != null) {
                    // Usuario ya autenticado, ir a MainActivity
                    navigateToMain()
                    return@launch
                }
            } catch (e: Exception) {
                // No hay sesión, mostrar pantalla de login
            }
        }

        setContent {
            OrabelTheme {
                LoginScreen(
                    onGoogleSignInClick = { handleGoogleSignIn() },
                    isLoading = isAuthInProgress
                )
            }
        }
    }

    /**
     * Maneja el callback de deeplink después de OAuth
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        // Procesar deeplink de callback de Supabase
        intent.data?.let { uri ->
            if (uri.scheme == "lyrion" && uri.host == "auth-callback") {
                processAuthCallback(uri)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        // También procesar intent al resumir
        intent?.data?.let { uri ->
            if (uri.scheme == "lyrion" && uri.host == "auth-callback") {
                processAuthCallback(uri)
            }
        }
    }

    /**
     * Procesa el callback de autenticación
     */
    private fun processAuthCallback(uri: Uri) {
        lifecycleScope.launch {
            try {
                isAuthInProgress = true
                
                // Extraer parámetros de la URL de callback
                val code = uri.getQueryParameter("code")
                
                if (code != null) {
                    // Intercambiar código por sesión (PKCE flow)
                    SupabaseClient.client.auth.exchangeCodeForSession(code)
                    
                    // Verificar sesión y guardar tokens de Google
                    val session = SupabaseClient.client.auth.currentSessionOrNull()
                    if (session != null) {
                        // Guardar información del usuario
                        saveUserProfile(session)
                        
                        // 🔥 NUEVO: Guardar sesión offline para persistencia sin internet
                        session.user?.let { user ->
                            val userName = user.userMetadata?.get("full_name")?.toString() 
                                ?: user.userMetadata?.get("name")?.toString() 
                                ?: user.email?.substringBefore("@") 
                                ?: "Usuario"
                            
                            val avatarUrl = user.userMetadata?.get("avatar_url")?.toString() 
                                ?: user.userMetadata?.get("picture")?.toString()
                            
                            offlineSessionManager.saveOfflineSession(
                                email = user.email ?: "",
                                userId = user.id,
                                displayName = userName,
                                avatarUrl = avatarUrl
                            )
                            Log.d("LoginActivity", "✅ Sesión offline guardada para uso sin internet")
                        }
                        
                        // Guardar provider_token para acceder a Google Calendar API
                        session.providerToken?.let { token ->
                            saveProviderToken(token)
                            Log.d("LoginActivity", "Provider token saved for Google Calendar access")
                        }
                        
                        // Guardar provider_refresh_token para renovar el acceso
                        session.providerRefreshToken?.let { refreshToken ->
                            saveProviderRefreshToken(refreshToken)
                            Log.d("LoginActivity", "Provider refresh token saved")
                        }
                        
                        Toast.makeText(
                            this@LoginActivity,
                            "¡Bienvenido! ${session.user?.email}",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        navigateToMain()
                    }
                } else {
                    // No se recibió código, el usuario canceló
                    isAuthInProgress = false
                    Log.d("LoginActivity", "Usuario canceló autenticación")
                }
            } catch (e: Exception) {
                isAuthInProgress = false
                Log.e("LoginActivity", "Error en autenticación: ${e.message}")
                Toast.makeText(
                    this@LoginActivity,
                    "Error al iniciar sesión: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Inicia el flujo de autenticación con Google
     */
    private fun handleGoogleSignIn() {
        lifecycleScope.launch {
            try {
                isAuthInProgress = true
                
                // Iniciar OAuth con Google usando Custom Tabs con scopes de Calendar y Classroom
                SupabaseClient.client.auth.signInWith(Google) {
                    scopes.addAll(listOf(
                        "openid",
                        "email",
                        "profile",
                        "https://www.googleapis.com/auth/calendar",
                        "https://www.googleapis.com/auth/classroom.courses.readonly",
                        "https://www.googleapis.com/auth/classroom.coursework.me",
                        "https://www.googleapis.com/auth/classroom.coursework.students"
                    ))
                }
                
                // El flujo continúa en onNewIntent cuando regresa del navegador
            } catch (e: Exception) {
                isAuthInProgress = false
                Toast.makeText(
                    this@LoginActivity,
                    "Error al conectar con Google: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Guarda el perfil del usuario en Supabase
     */
    private suspend fun saveUserProfile(session: io.github.jan.supabase.auth.user.UserSession) {
        try {
            val user = session.user ?: return
            
            // Obtener metadata del usuario desde Google
            val userName = user.userMetadata?.get("full_name")?.toString() 
                ?: user.userMetadata?.get("name")?.toString() 
                ?: user.email?.substringBefore("@") 
                ?: "Usuario"
            
            val userEmail = user.email ?: ""
            val userPhoto = user.userMetadata?.get("avatar_url")?.toString() 
                ?: user.userMetadata?.get("picture")?.toString() 
                ?: ""
            
            // Guardar en SharedPreferences (local)
            val prefs = getSharedPreferences("user_profile", MODE_PRIVATE)
            prefs.edit().apply {
                putString("user_id", user.id)
                putString("user_name", userName)
                putString("user_email", userEmail)
                putString("user_photo", userPhoto)
                apply()
            }
            
            // 🧠 NUEVO: Guardar nombre en PersonalContext para respuestas personalizadas
            try {
                // Verificar si ya existe
                val existingName = personalBox.all.find { it.key == "name" }
                
                if (existingName == null) {
                    // Crear nuevo contexto con el nombre
                    val nameContext = PersonalContext(
                        contextType = "user_info",
                        key = "name",
                        value = userName,
                        notes = "Nombre obtenido desde Google Sign-In",
                        timestamp = System.currentTimeMillis(),
                        lastUsed = System.currentTimeMillis(),
                        relevanceScore = 100, // Máxima relevancia
                        useCount = 0,
                        category = "personal",
                        emotion = "",
                        isActive = true
                    )
                    personalBox.put(nameContext)
                    Log.i("LoginActivity", "✅ Nombre '$userName' guardado en PersonalContext")
                } else {
                    // Actualizar si cambió
                    if (existingName.value != userName) {
                        existingName.value = userName
                        existingName.timestamp = System.currentTimeMillis()
                        personalBox.put(existingName)
                        Log.i("LoginActivity", "✅ Nombre actualizado a '$userName'")
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "❌ Error guardando nombre en PersonalContext: ${e.message}", e)
            }
        } catch (e: Exception) {
            // Error al guardar perfil, continuar de todos modos
            e.printStackTrace()
        }
    }

    /**
     * Navega a la actividad principal
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    /**
     * Guarda el provider token de Google para acceder a Calendar API
     */
    private fun saveProviderToken(token: String) {
        val prefs = getSharedPreferences("google_auth", MODE_PRIVATE)
        prefs.edit().putString("provider_token", token).apply()
    }

    /**
     * Guarda el provider refresh token de Google
     */
    private fun saveProviderRefreshToken(refreshToken: String) {
        val prefs = getSharedPreferences("google_auth", MODE_PRIVATE)
        prefs.edit().putString("provider_refresh_token", refreshToken).apply()
    }

    companion object {
        /**
         * Recupera el provider token de Google
         */
        fun getProviderToken(context: Context): String? {
            val prefs = context.getSharedPreferences("google_auth", MODE_PRIVATE)
            return prefs.getString("provider_token", null)
        }

        /**
         * Recupera el provider refresh token de Google
         */
        fun getProviderRefreshToken(context: Context): String? {
            val prefs = context.getSharedPreferences("google_auth", MODE_PRIVATE)
            return prefs.getString("provider_refresh_token", null)
        }
        
        /**
         * Refresca el access token usando Supabase
         */
        suspend fun refreshProviderToken(context: Context): String? {
            return try {
                Log.d("LoginActivity", "🔄 Intentando refrescar provider token...")
                
                // Refrescar la sesión de Supabase para obtener un nuevo provider_token
                SupabaseClient.client.auth.refreshCurrentSession()
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                
                // Guardar el nuevo token
                val newToken = session?.providerToken
                if (newToken != null) {
                    val prefs = context.getSharedPreferences("google_auth", Context.MODE_PRIVATE)
                    prefs.edit().putString("provider_token", newToken).apply()
                    Log.d("LoginActivity", "✅ Provider token refrescado exitosamente")
                    newToken
                } else {
                    Log.e("LoginActivity", "❌ No se recibió nuevo provider token")
                    null
                }
            } catch (e: Exception) {
                Log.e("LoginActivity", "❌ Error al refrescar provider token: ${e.message}")
                null
            }
        }
    }
}

/**
 * Pantalla de inicio de sesión con Google
 */
@Composable
fun LoginScreen(
    onGoogleSignInClick: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                // Logo de la aplicación
                Image(
                    painter = painterResource(id = R.drawable.ic_lyrion_logo),
                    contentDescription = "Logo Lyrion",
                    modifier = Modifier.size(120.dp)
                )

                // Título
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Bienvenido a Lyrion",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    Text(
                        text = "Tu asistente de salud inteligente sin internet",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Botón de Google Sign-In
                Button(
                    onClick = onGoogleSignInClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 8.dp
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Ícono de Google (puedes usar un ícono real aquí)
                            Text(
                                text = "G",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4285F4)
                            )
                            
                            Text(
                                text = "Continuar con Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Información de privacidad
                Text(
                    text = "Al continuar, aceptas nuestros Términos de Servicio y Política de Privacidad",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
