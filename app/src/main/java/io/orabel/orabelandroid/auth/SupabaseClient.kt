package io.orabel.orabelandroid.auth

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import android.util.Log
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Singleton para gestionar el cliente de Supabase
 * Proporciona autenticación con Google OAuth usando Custom Tabs
 * CON PERSISTENCIA DE SESIÓN AUTOMÁTICA y Realtime
 */
object SupabaseClient {
    
    // URL del proyecto Supabase
    private const val SUPABASE_URL = "https://tcjhnibhoplfslqzprgl.supabase.co"
    
    // API Key (anon/public key)
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRjamhuaWJob3BsZnNscXpwcmdsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTYzNjk1MTMsImV4cCI6MjA3MTk0NTUxM30.tEGffrsw9ed0ez7bLek1P8vrF8U7w5xEnOfyMgGOdqA"
    
    // Esquema de deeplink para callback de OAuth
    private const val DEEP_LINK_SCHEME = "lyrion"
    private const val DEEP_LINK_HOST = "auth-callback"
    
    private var _client: SupabaseClient? = null
    
    /**
     * Cliente de Supabase configurado con persistencia de sesión
     * Debe llamarse init(context) antes de usar
     */
    val client: SupabaseClient
        get() = _client ?: throw IllegalStateException("SupabaseClient no inicializado. Llama a init(context) primero.")
    
    /**
     * Inicializa el cliente de Supabase con el contexto de la aplicación
     * DEBE llamarse en Application.onCreate()
     */
    fun init(context: Context) {
        if (_client != null) {
            Log.d("SupabaseClient", "Cliente ya inicializado")
            return
        }
        
        _client = createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_KEY
        ) {
            // ⚠️ IMPORTANTE: Usar OkHttp para soporte de WebSockets (Realtime)
            httpEngine = OkHttp.create()
            
            install(Auth) {
                // Configurar OAuth para que abra en Custom Tabs (navegador interno)
                scheme = DEEP_LINK_SCHEME
                host = DEEP_LINK_HOST
                
                // Usar Custom Tabs para mejor experiencia de usuario
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
                
                // Usar flujo PKCE para mayor seguridad
                flowType = FlowType.PKCE
                
                // IMPORTANTE: Habilitar almacenamiento automático de sesión
                // Esto guarda la sesión en EncryptedSharedPreferences
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                autoSaveToStorage = true
            }
            
            install(Postgrest)
            
            install(Realtime) {
                // Configuración opcional de Realtime
            }
        }
        
        Log.d("SupabaseClient", "Cliente inicializado con persistencia de sesión y Realtime")
    }
    
    /**
     * Obtiene la URL de deeplink para callback de OAuth
     */
    fun getDeepLinkUrl(): String = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST"
}
