package io.orabel.orabelandroid.data.social

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Date
import java.util.UUID
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.orabel.orabelandroid.data.ObjectBoxStore
import io.objectbox.Box
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import io.objectbox.query.QueryBuilder.StringOrder

/**
 * Repositorio para manejar operaciones de la red social con Supabase.
 */
class SocialRepository private constructor(context: Context) {
    private val appContext = context.applicationContext

    private val senderDisplayNameCache = mutableMapOf<String, String>()

    private data class PendingStatusUpdate(
        val status: String,
        val deliveredAt: Long?,
        val seenAt: Long?
    )

    // Si un evento de status llega ANTES de que el mensaje exista local/UI, lo guardamos aquí.
    // Caso típico: el receptor marca "delivered" muy rápido.
    private val pendingStatusUpdates = ConcurrentHashMap<Long, PendingStatusUpdate>()
    
    // ObjectBox for local message storage (ephemeral pattern)
    private val localMessagesBox: Box<LocalMessageEntity> = 
        ObjectBoxStore.store.boxFor(LocalMessageEntity::class.java)
    
    companion object {
        private const val TAG = "SocialRepository"
        private const val SUPABASE_URL = "https://tcjhnibhoplfslqzprgl.supabase.co"
        private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRjamhuaWJob3BsZnNscXpwcmdsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTYzNjk1MTMsImV4cCI6MjA3MTk0NTUxM30.tEGffrsw9ed0ez7bLek1P8vrF8U7w5xEnOfyMgGOdqA"
        
        @Volatile
        private var instance: SocialRepository? = null
        
        fun getInstance(context: Context): SocialRepository {
            return instance ?: synchronized(this) {
                instance ?: SocialRepository(context).also { instance = it }
            }
        }
    }
    
    // Use SupabaseClient directly for session management
    private fun getAccessToken(): String? = io.orabel.orabelandroid.auth.SupabaseClient.client.auth.currentAccessTokenOrNull()
    
    // Helper to get String ID
    fun getCurrentUserId(): String? = io.orabel.orabelandroid.auth.SupabaseClient.client.auth.currentUserOrNull()?.id
    
    fun getCurrentUserUUID(): UUID? {
        return getCurrentUserId()?.let { 
            try { UUID.fromString(it) } catch (e: Exception) { null }
        }
    }

    // 🔥 NUEVO: StateFlow para perfil reactivo
    private val _currentUserProfile = MutableStateFlow<ProfileDTO?>(null)
    val currentUserProfile: StateFlow<ProfileDTO?> = _currentUserProfile.asStateFlow()

    /**
     * Obtiene el perfil del usuario actual.
     */
    suspend fun fetchCurrentUserProfile(): ProfileDTO? = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        android.util.Log.d(TAG, "fetchCurrentUserProfile: fetching for userId=$userId")
        
        if (userId == null) {
            android.util.Log.e(TAG, "fetchCurrentUserProfile: User ID is null")
            return@withContext null
        }
        
        try {
            val urlString = "$SUPABASE_URL/rest/v1/users?id=eq.$userId&select=*"
            android.util.Log.d(TAG, "fetchCurrentUserProfile: url=$urlString")
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            val response = conn.readResponse()
            android.util.Log.d(TAG, "fetchCurrentUserProfile: responseCode=${conn.responseCode}, body=$response")
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val profile = parseProfile(jsonArray.getJSONObject(0))
                    android.util.Log.d(TAG, "fetchCurrentUserProfile: Parsed profile -> $profile")
                    _currentUserProfile.value = profile // Emitir actualización
                    profile
                } else {
                    android.util.Log.w(TAG, "fetchCurrentUserProfile: User not found in DB or empty array")
                    null
                }
            } else {
                android.util.Log.e(TAG, "fetchCurrentUserProfile: Request failed with code ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching current profile: ${e.message}", e)
            null
        }
    }
    
    // ========== HELPERS ==========
    
    private fun createConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_KEY)
        getAccessToken()?.let { token ->
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 10000 // 10 segundos
        conn.readTimeout = 10000
        return conn
    }
    
    private fun HttpURLConnection.readResponse(): String {
        return try {
            if (responseCode in 200..299) {
                inputStream.bufferedReader().use { it.readText() }
            } else {
                errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun parseProfile(json: JSONObject): ProfileDTO {
        fun optStringNullable(key: String): String? {
            return if (json.has(key) && !json.isNull(key)) json.getString(key) else null
        }

        return ProfileDTO(
            id = UUID.fromString(json.getString("id")), // ID should not be null
            username = optStringNullable("username"),
            email = optStringNullable("email"),
            nombre = optStringNullable("nombre"),
            apellido = optStringNullable("apellido"),
            avatarUrl = optStringNullable("avatar_url"),
            bannerUrl = optStringNullable("banner_url"),
            bio = optStringNullable("bio"),
            occupation = optStringNullable("occupation"),
            pais = optStringNullable("pais"),
            edad = if (json.has("edad") && !json.isNull("edad")) json.getInt("edad") else null,
            altura_cm = if (json.has("altura_cm") && !json.isNull("altura_cm")) json.getInt("altura_cm") else null,
            peso_kg = if (json.has("peso_kg") && !json.isNull("peso_kg")) json.getInt("peso_kg") else null,
            estadoCivil = optStringNullable("estado_civil"),
            estadoRegion = optStringNullable("estado_region"),
            status = optStringNullable("status"),
            chatStatus = optStringNullable("chat_status")
        )
    }
    
    private fun parseProfiles(jsonArray: JSONArray): List<ProfileDTO> {
        val profiles = mutableListOf<ProfileDTO>()
        for (i in 0 until jsonArray.length()) {
            try {
                profiles.add(parseProfile(jsonArray.getJSONObject(i)))
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing profile: ${e.message}")
            }
        }
        return profiles
    }
    
    private fun parseMessage(json: JSONObject): MessageDTO {
        val createdAtStr = json.optString("created_at", "")
        val createdAt = try {
            if (createdAtStr.isNotEmpty()) {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(createdAtStr) ?: Date()
            } else Date()
        } catch (e: Exception) {
            Date()
        }
        val isRead = json.optBoolean("is_read", false)

        return MessageDTO(
            id = json.optLong("id"),
            senderId = UUID.fromString(json.optString("sender_id")),
            receiverId = UUID.fromString(json.optString("receiver_id")),
            content = json.optString("content", ""),
            isTemporary = true,
            deliveredAt = null,
            seenAt = if (isRead) System.currentTimeMillis() else null,
            createdAt = createdAt,
            status = if (isRead) "read" else "sent"
        )
    }
    
    // ========== BÚSQUEDA DE USUARIOS ==========
    
    /**
     * Busca perfiles de usuario por nombre o username.
     */
    suspend fun searchProfiles(query: String): List<ProfileDTO> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "$SUPABASE_URL/rest/v1/users?or=(username.ilike.*$encodedQuery*,nombre.ilike.*$encodedQuery*)&select=*"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            Log.d(TAG, "searchProfiles response: $response")
            
            if (conn.responseCode == 200) {
                parseProfiles(JSONArray(response))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching profiles: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene usuarios disponibles/sugeridos.
     */
    suspend fun fetchAvailableUsers(): List<ProfileDTO> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/users?select=*&order=last_seen.desc.nullslast&limit=50"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            Log.d(TAG, "fetchAvailableUsers response code: ${conn.responseCode}")
            
            if (conn.responseCode == 200) {
                parseProfiles(JSONArray(response))
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching available users: ${e.message}")
            emptyList()
        }
    }
    
    // ========== CONEXIONES / SEGUIMIENTOS ==========
    
    /**
     * Obtiene usuarios que el usuario actual sigue.
     */
    suspend fun fetchFollowingUsers(): List<ProfileDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        try {
            val urlString = "$SUPABASE_URL/rest/v1/followers?follower_id=eq.$userId&select=following:users!followers_followed_id_fkey(*)"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            Log.d(TAG, "fetchFollowingUsers: ${conn.responseCode}")
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val profiles = mutableListOf<ProfileDTO>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.has("following")) {
                        profiles.add(parseProfile(obj.getJSONObject("following")))
                    }
                }
                profiles
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching following users: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene seguidores del usuario actual.
     */
    suspend fun fetchFollowers(): List<ProfileDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        try {
            val urlString = "$SUPABASE_URL/rest/v1/followers?followed_id=eq.$userId&select=follower:users!followers_follower_id_fkey(*)"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            Log.d(TAG, "fetchFollowers: ${conn.responseCode}")
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val profiles = mutableListOf<ProfileDTO>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.has("follower")) {
                        profiles.add(parseProfile(obj.getJSONObject("follower")))
                    }
                }
                profiles
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching followers: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene amigos mutuos (status = amigos).
     */
    suspend fun fetchMutualFriends(): List<ProfileDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        try {
            val urlString = "$SUPABASE_URL/rest/v1/friends?user_id=eq.$userId&status=eq.active&select=friend:users!friends_friend_id_fkey(*)"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val profiles = mutableListOf<ProfileDTO>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.has("friend")) {
                        profiles.add(parseProfile(obj.getJSONObject("friend")))
                    }
                }
                profiles
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching mutual friends: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene solicitudes de amistad recibidas (pendientes).
     */
    suspend fun fetchIncomingFriendRequests(): List<FriendRequestWithUser> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        try {
            val urlString = "$SUPABASE_URL/rest/v1/friend_requests?receiver_id=eq.$userId&status=eq.pending&select=id,sender:users!friend_requests_sender_id_fkey(*)"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            Log.d(TAG, "fetchIncomingFriendRequests: ${conn.responseCode}")
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val requests = mutableListOf<FriendRequestWithUser>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.has("sender")) {
                        requests.add(FriendRequestWithUser(
                            connectionId = UUID.fromString(obj.getString("id")),
                            user = parseProfile(obj.getJSONObject("sender"))
                        ))
                    }
                }
                requests
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching incoming requests: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene solicitudes de amistad enviadas (pendientes).
     */
    suspend fun fetchOutgoingFriendRequests(): List<FriendRequestWithUser> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        try {
            val urlString = "$SUPABASE_URL/rest/v1/friend_requests?sender_id=eq.$userId&status=eq.pending&select=id,receiver:users!friend_requests_receiver_id_fkey(*)"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val requests = mutableListOf<FriendRequestWithUser>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.has("receiver")) {
                        requests.add(FriendRequestWithUser(
                            connectionId = UUID.fromString(obj.getString("id")),
                            user = parseProfile(obj.getJSONObject("receiver"))
                        ))
                    }
                }
                requests
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching outgoing requests: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Seguir a un usuario.
     */
    suspend fun followUser(targetId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user session"))
        try {
            Log.d(TAG, "🔵 [FOLLOW] Iniciando followUser para: $targetId")
            
            // Insertar en la tabla followers
            val insertUrl = "$SUPABASE_URL/rest/v1/followers"
            val insertConn = createConnection(insertUrl)
            insertConn.requestMethod = "POST"
            insertConn.setRequestProperty("Prefer", "return=minimal")
            insertConn.doOutput = true
            
            val body = JSONObject().apply {
                put("follower_id", userId)
                put("followed_id", targetId.toString())
            }
            
            OutputStreamWriter(insertConn.outputStream).use { 
                it.write(body.toString())
            }
            
            if (insertConn.responseCode in 200..299 || insertConn.responseCode == 409) {
                Log.d(TAG, "✅ [FOLLOW] Ahora sigues a $targetId")
                Result.success(Unit)
            } else {
                val error = insertConn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "❌ [FOLLOW] Error ${insertConn.responseCode}: $error")
                Result.failure(Exception("Error following user"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [FOLLOW] Exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Dejar de seguir a un usuario.
     */
    suspend fun unfollowUser(targetId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user session"))
        try {
            Log.d(TAG, "🔴 [UNFOLLOW] Iniciando unfollowUser para: $targetId")
            
            // Eliminar de la tabla followers
            val deleteUrl = "$SUPABASE_URL/rest/v1/followers?follower_id=eq.$userId&followed_id=eq.$targetId"
            val deleteConn = createConnection(deleteUrl)
            deleteConn.requestMethod = "DELETE"
            deleteConn.setRequestProperty("Prefer", "return=minimal")
            
            val responseCode = deleteConn.responseCode
            if (responseCode in 200..299) {
                Log.d(TAG, "✅ [UNFOLLOW] Dejaste de seguir exitosamente")
                Result.success(Unit)
            } else {
                val error = deleteConn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "❌ [UNFOLLOW] Error $responseCode: $error")
                Result.failure(Exception("Error unfollowing user"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [UNFOLLOW] Exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Enviar solicitud de amistad.
     */
    suspend fun sendFriendRequest(targetId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user session"))
        try {
            Log.d(TAG, "🟣 [FRIEND_REQ] Iniciando sendFriendRequest para: $targetId")
            
            // Verificar si ya existe una solicitud pendiente
            val checkUrl = "$SUPABASE_URL/rest/v1/friend_requests?sender_id=eq.$userId&receiver_id=eq.$targetId&status=eq.pending&select=id"
            val checkConn = createConnection(checkUrl)
            checkConn.requestMethod = "GET"
            val checkResponse = checkConn.readResponse()
            
            if (checkConn.responseCode == 200 && JSONArray(checkResponse).length() > 0) {
                Log.d(TAG, "✅ [FRIEND_REQ] Ya existe una solicitud pendiente")
                return@withContext Result.success(Unit)
            }
            
            // Crear nueva solicitud de amistad
            val insertUrl = "$SUPABASE_URL/rest/v1/friend_requests"
            val insertConn = createConnection(insertUrl)
            insertConn.requestMethod = "POST"
            insertConn.doOutput = true
            
            val body = JSONObject().apply {
                put("sender_id", userId.toString())
                put("receiver_id", targetId.toString())
                put("status", "pending")
            }
            
            OutputStreamWriter(insertConn.outputStream).use { 
                it.write(body.toString())
            }
            
            if (insertConn.responseCode in 200..299) {
                Log.d(TAG, "✅ [FRIEND_REQ] Solicitud enviada exitosamente")
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ [FRIEND_REQ] Error: ${insertConn.responseCode}")
                Result.failure(Exception("Error sending friend request"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [FRIEND_REQ] Exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Actualizar estado de friend request (aceptar/rechazar/eliminar).
     * @param connectionId ID de la solicitud de amistad (friend_request id)
     * @param newStatus "amigos" para aceptar, "rejected" para rechazar, "deleted" para eliminar
     */
    suspend fun updateConnectionStatus(connectionId: UUID, newStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user session"))
        try {
            Log.d(TAG, "🟢 [UPDATE_CONNECTION] Starting with connectionId=$connectionId, newStatus=$newStatus, currentUser=$userId")
            
            when (newStatus) {
                "amigos" -> {
                    // Aceptar solicitud usando la función RPC (mismo método que iOS)
                    // Esta función se ejecuta con SECURITY DEFINER y puede insertar filas para ambos usuarios
                    Log.d(TAG, "🟢 [UPDATE_CONNECTION] Calling accept_friend_request RPC")
                    val rpcUrl = "$SUPABASE_URL/rest/v1/rpc/accept_friend_request"
                    val rpcConn = createConnection(rpcUrl)
                    rpcConn.requestMethod = "POST"
                    rpcConn.doOutput = true
                    rpcConn.setRequestProperty("Content-Type", "application/json")
                    
                    val rpcBody = JSONObject().apply {
                        put("request_id", connectionId.toString())
                    }
                    
                    OutputStreamWriter(rpcConn.outputStream).use {
                        it.write(rpcBody.toString())
                    }
                    
                    val rpcResponseCode = rpcConn.responseCode
                    Log.d(TAG, "🟢 [UPDATE_CONNECTION] RPC response code: $rpcResponseCode")
                    
                    if (rpcResponseCode in 200..299) {
                        Log.d(TAG, "✅ [UPDATE_CONNECTION] Successfully accepted friend request via RPC")
                        Result.success(Unit)
                    } else {
                        val errorResponse = try { 
                            rpcConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        } catch (e: Exception) { "" }
                        Log.e(TAG, "❌ [UPDATE_CONNECTION] RPC failed: $rpcResponseCode - $errorResponse")
                        Result.failure(Exception("Error accepting friend request: $rpcResponseCode"))
                    }
                }
                "rejected", "deleted" -> {
                    // Rechazar o eliminar solicitud
                    Log.d(TAG, "🟠 [UPDATE_CONNECTION] Rejecting/deleting request: $connectionId")
                    val deleteUrl = "$SUPABASE_URL/rest/v1/friend_requests?id=eq.$connectionId"
                    val deleteConn = createConnection(deleteUrl)
                    deleteConn.requestMethod = "DELETE"
                    val deleteResponseCode = deleteConn.responseCode
                    
                    if (deleteResponseCode in 200..299) {
                        Log.d(TAG, "✅ [UPDATE_CONNECTION] Successfully rejected/deleted request")
                        Result.success(Unit)
                    } else {
                        Log.e(TAG, "❌ [UPDATE_CONNECTION] Error deleting friend request: $deleteResponseCode")
                        Result.failure(Exception("Error deleting friend request: $deleteResponseCode"))
                    }
                }
                else -> {
                    Log.e(TAG, "❌ [UPDATE_CONNECTION] Invalid status: $newStatus")
                    Result.failure(Exception("Invalid status: $newStatus"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [UPDATE_CONNECTION] Exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Cancelar una solicitud de amistad enviada.
     * @param requestId ID de la solicitud de amistad en friend_requests
     */
    suspend fun cancelFriendRequest(requestId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🟠 [CANCEL_REQUEST] Cancelando solicitud: $requestId")
            
            val deleteUrl = "$SUPABASE_URL/rest/v1/friend_requests?id=eq.$requestId"
            val deleteConn = createConnection(deleteUrl)
            deleteConn.requestMethod = "DELETE"
            
            if (deleteConn.responseCode in 200..299) {
                Log.d(TAG, "✅ [CANCEL_REQUEST] Solicitud cancelada exitosamente")
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ [CANCEL_REQUEST] Error: ${deleteConn.responseCode}")
                Result.failure(Exception("Error canceling friend request"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [CANCEL_REQUEST] Exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Eliminar una amistad (unfriend).
     * @param friendId UUID del amigo a eliminar
     */
    suspend fun removeFriend(friendId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user session"))
        try {
            Log.d(TAG, "🔴 [REMOVE_FRIEND] Eliminando amistad con: $friendId")
            
            // Eliminar ambas entradas bidireccionales en la tabla friends
            // 1. userId -> friendId
            val deleteUrl1 = "$SUPABASE_URL/rest/v1/friends?user_id=eq.$userId&friend_id=eq.$friendId"
            val deleteConn1 = createConnection(deleteUrl1)
            deleteConn1.requestMethod = "DELETE"
            deleteConn1.responseCode // Ejecutar
            
            // 2. friendId -> userId
            val deleteUrl2 = "$SUPABASE_URL/rest/v1/friends?user_id=eq.$friendId&friend_id=eq.$userId"
            val deleteConn2 = createConnection(deleteUrl2)
            deleteConn2.requestMethod = "DELETE"
            
            if (deleteConn2.responseCode in 200..299) {
                Log.d(TAG, "✅ [REMOVE_FRIEND] Amistad eliminada exitosamente")
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ [REMOVE_FRIEND] Error: ${deleteConn2.responseCode}")
                Result.failure(Exception("Error removing friend"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [REMOVE_FRIEND] Exception: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Verificar el estado de relación con un usuario.
     * Retorna: "none", "siguiendo", "solicitud_enviada", "solicitud_recibida", "amigos"
     */
    suspend fun checkRelationshipStatus(targetId: UUID): String = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext "none"
        try {
            var isAmigos = false
            var isSiguiendo = false
            var hasSolicitudEnviada = false
            var hasSolicitudRecibida = false
            
            // 1. Verificar si ya son amigos
            val friendsUrl = "$SUPABASE_URL/rest/v1/friends?user_id=eq.$userId&friend_id=eq.$targetId&status=eq.active&select=id"
            val friendsConn = createConnection(friendsUrl)
            friendsConn.requestMethod = "GET"
            val friendsResponse = friendsConn.readResponse()
            
            if (friendsConn.responseCode == 200) {
                val jsonArray = JSONArray(friendsResponse)
                isAmigos = jsonArray.length() > 0
            }
            
            // 2. Verificar si hay una solicitud de amistad enviada
            val sentRequestUrl = "$SUPABASE_URL/rest/v1/friend_requests?sender_id=eq.$userId&receiver_id=eq.$targetId&status=eq.pending&select=id"
            val sentRequestConn = createConnection(sentRequestUrl)
            sentRequestConn.requestMethod = "GET"
            val sentRequestResponse = sentRequestConn.readResponse()
            
            if (sentRequestConn.responseCode == 200) {
                val jsonArray = JSONArray(sentRequestResponse)
                hasSolicitudEnviada = jsonArray.length() > 0
            }
            
            // 3. Verificar si hay una solicitud de amistad recibida
            val receivedRequestUrl = "$SUPABASE_URL/rest/v1/friend_requests?sender_id=eq.$targetId&receiver_id=eq.$userId&status=eq.pending&select=id"
            val receivedRequestConn = createConnection(receivedRequestUrl)
            receivedRequestConn.requestMethod = "GET"
            val receivedRequestResponse = receivedRequestConn.readResponse()
            
            if (receivedRequestConn.responseCode == 200) {
                val jsonArray = JSONArray(receivedRequestResponse)
                hasSolicitudRecibida = jsonArray.length() > 0
            }
            
            // 4. Verificar si lo seguimos en la tabla followers
            val followersUrl = "$SUPABASE_URL/rest/v1/followers?follower_id=eq.$userId&followed_id=eq.$targetId&select=id"
            val followersConn = createConnection(followersUrl)
            followersConn.requestMethod = "GET"
            val followersResponse = followersConn.readResponse()
            
            if (followersConn.responseCode == 200) {
                val jsonArray = JSONArray(followersResponse)
                isSiguiendo = jsonArray.length() > 0
            }
            
            // Devolver estado compuesto
            return@withContext when {
                hasSolicitudRecibida -> "solicitud_recibida"
                hasSolicitudEnviada -> "solicitud_enviada"
                isAmigos && isSiguiendo -> "amigos_siguiendo"
                isAmigos -> "amigos"
                isSiguiendo -> "siguiendo"
                else -> "none"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking relationship: ${e.message}")
            "none"
        }
    }
    
    // ========== MENSAJES ==========
    
    /**
     * Obtiene la bandeja de entrada (hilos de mensajes).
     */
    suspend fun fetchInbox(): List<ProfileDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        try {
            // Obtener TODAS las conversaciones (donde soy sender O receiver)
            // Usamos una query 'or' para obtener ambos lados
            val urlString = "$SUPABASE_URL/rest/v1/direct_messages?or=(receiver_id.eq.$userId,sender_id.eq.$userId)&select=sender_id,receiver_id&order=created_at.desc"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val contactIds = mutableSetOf<String>()
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val sender = obj.getString("sender_id")
                    val receiver = obj.getString("receiver_id")
                    
                    // Agregar el OTRO usuario a la lista de contactos
                    if (sender != userId) contactIds.add(sender)
                    if (receiver != userId) contactIds.add(receiver)
                }
                
                if (contactIds.isEmpty()) return@withContext emptyList()
                
                // Obtener perfiles de los contactos
                val idsString = contactIds.joinToString(",")
                val usersUrl = "$SUPABASE_URL/rest/v1/users?id=in.($idsString)&select=*"
                val usersConn = createConnection(usersUrl)
                usersConn.requestMethod = "GET"
                
                val usersResponse = usersConn.readResponse()
                if (usersConn.responseCode == 200) {
                    val allProfiles = parseProfiles(JSONArray(usersResponse))
                    
                    // DEDUPLICACIÓN: Si hay múltiples perfiles con el mismo email,
                    // mantener solo el que tiene nombre completo (nombre + apellido)
                    // o el primero encontrado si ninguno tiene nombre
                    val seenEmails = mutableMapOf<String, ProfileDTO>()
                    
                    for (profile in allProfiles) {
                        // Usar el campo email real (de la tabla users) para deduplicar
                        val emailKey = profile.email?.lowercase()?.trim()
                        
                        if (!emailKey.isNullOrBlank()) {
                            val existingProfile = seenEmails[emailKey]
                            if (existingProfile == null) {
                                seenEmails[emailKey] = profile
                            } else {
                                // Preferir el perfil que tiene nombre y apellido
                                val currentHasName = !profile.nombre.isNullOrBlank() && !profile.apellido.isNullOrBlank()
                                val existingHasName = !existingProfile.nombre.isNullOrBlank() && !existingProfile.apellido.isNullOrBlank()
                                
                                if (currentHasName && !existingHasName) {
                                    seenEmails[emailKey] = profile
                                }
                            }
                        } else {
                            // Sin email, usar ID como fallback (no deduplicable)
                            seenEmails[profile.id.toString()] = profile
                        }
                    }
                    
                    seenEmails.values.toList()
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching inbox: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene mensajes con un usuario específico.
     * IMPLEMENTACIÓN DE PATRÓN EFÍMERO (Ephemeral Messaging):
     * 1. Carga mensajes locales (ya leídos) desde ObjectBox
     * 2. Carga mensajes remotos (no leídos) desde Supabase
     * 3. Marca mensajes remotos como leídos
     * 4. Guarda mensajes leídos en ObjectBox
     * 5. Elimina mensajes leídos de Supabase
     * 6. Retorna mensajes locales + remotos combinados
     */
    suspend fun fetchMessages(targetUserId: String): List<MessageDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        
        try {
            // PASO 1: Cargar mensajes locales (Cache)
            val localMessages = try {
                // Buscar mensajes donde:
                // (senderId == currentUser AND receiverId == targetUser) OR
                // (senderId == targetUser AND receiverId == currentUser)
                val query = localMessagesBox.query().build()
                val allMessages = query.find()
                
                // Filtrar manualmente para evitar problemas con el query builder
                allMessages.filter { msg ->
                    (msg.senderId == userId && msg.receiverId == targetUserId) ||
                    (msg.senderId == targetUserId && msg.receiverId == userId)
                }.sortedBy { it.createdAt }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying local messages: ${e.message}", e)
                emptyList()
            }
            
            Log.d(TAG, "📦 [FETCH_MSG] Loaded ${localMessages.size} local messages from Cache")
            
            // PASO 2: Cargar SOLO mensajes nuevos (no leídos) desde Supabase
            // Los mensajes leídos ya fueron eliminados del servidor y están en el cache local
            // Usar select con JOIN para obtener información del sender
            val selectQuery = "*,sender:users!direct_messages_sender_id_fkey(username,nombre,apellido,avatar_url)"
            val urlString = "$SUPABASE_URL/rest/v1/direct_messages?or=(and(sender_id.eq.$userId,receiver_id.eq.$targetUserId),and(sender_id.eq.$targetUserId,receiver_id.eq.$userId))&select=$selectQuery&order=created_at.asc"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            val response = conn.readResponse()
            
            val remoteMessagesList = mutableListOf<MessageDTO>()
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                for (i in 0 until jsonArray.length()) {
                    try {
                        val msgJson = jsonArray.getJSONObject(i)
                        val messageId = msgJson.getLong("id")
                        val senderId = msgJson.getString("sender_id")
                        val receiverId = msgJson.getString("receiver_id")
                        val content = msgJson.getString("content")
                        val createdAtStr = msgJson.optString("created_at", "")
                        val status = msgJson.optString("status", "sent") // Default to sent if null
                        
                        val createdAt = try {
                            if (createdAtStr.isNotEmpty()) {
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(createdAtStr) ?: Date()
                            } else Date()
                        } catch (e: Exception) { Date() }
                        
                        // Parse timestamps for deliveredAt and seenAt
                        val deliveredAtStr = msgJson.optString("delivered_at", "")
                        val deliveredAt = try {
                            if (deliveredAtStr.isNotEmpty() && deliveredAtStr != "null") {
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(deliveredAtStr)?.time
                            } else null
                        } catch (e: Exception) { null }
                        
                        val seenAtStr = msgJson.optString("seen_at", "")
                        val seenAt = try {
                            if (seenAtStr.isNotEmpty() && seenAtStr != "null") {
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(seenAtStr)?.time
                            } else null
                        } catch (e: Exception) { null }
                        
                        // Extraer información del sender desde el JOIN
                        val senderInfo = if (msgJson.has("sender") && !msgJson.isNull("sender")) {
                            val senderJson = msgJson.getJSONObject("sender")
                            PostCreator(
                                username = if (senderJson.has("username") && !senderJson.isNull("username")) senderJson.getString("username") else null,
                                nombre = if (senderJson.has("nombre") && !senderJson.isNull("nombre")) senderJson.getString("nombre") else null,
                                apellido = if (senderJson.has("apellido") && !senderJson.isNull("apellido")) senderJson.getString("apellido") else null,
                                avatar_url = if (senderJson.has("avatar_url") && !senderJson.isNull("avatar_url")) senderJson.getString("avatar_url") else null
                            )
                        } else null
                        
                        val msg = MessageDTO(
                            id = messageId,
                            senderId = UUID.fromString(senderId),
                            receiverId = UUID.fromString(receiverId),
                            content = content,
                            isTemporary = true, // Ephemeral messaging
                            deliveredAt = deliveredAt,
                            seenAt = seenAt,
                            createdAt = createdAt,
                            status = status,
                            replyToId = if (msgJson.has("reply_to_id") && !msgJson.isNull("reply_to_id")) msgJson.getLong("reply_to_id") else null,
                            replyContextContent = msgJson.optString("reply_context_content", null),
                            replyContextSenderUsername = msgJson.optString("reply_context_sender_username", null),
                            sender = senderInfo,
                            receiver = null,
                            replyToMessage = null
                        )
                        
                        // EPHEMERAL PATTERN: Si es mensaje recibido (no enviado por mí), marcarlo como leído,
                        // guardarlo localmente y eliminarlo del servidor
                        if (senderId != userId) {
                            markMessageAsReadAndSave(messageId, msg)
                        } else {
                            // Si es mensaje enviado por mí, también guardarlo localmente si no existe
                            // (Para casos donde se envió desde otro dispositivo)
                            val existsLocally = localMessages.any { it.messageId == messageId }
                            if (!existsLocally) {
                                val localEntity = LocalMessageEntity.fromDTO(msg, readAt = System.currentTimeMillis())
                                localMessagesBox.put(localEntity)
                                Log.d(TAG, "💾 [FETCH_MSG] Saved own message $messageId to local storage")
                            }
                        }
                        
                        remoteMessagesList.add(msg)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing remote message: ${e.message}")
                    }
                }
                Log.d(TAG, "☁️ [FETCH_MSG] Fetched ${remoteMessagesList.size} remote messages from Supabase")
            }
            
            // PASO 3: Combinar mensajes locales y remotos, eliminar duplicados y ordenar
            val allMessages = mutableListOf<MessageDTO>()
            
            // Agregar mensajes locales
            allMessages.addAll(localMessages.map { it.toDTO() })
            
            // Agregar mensajes remotos que no estén ya en local
            val localMessageIds = localMessages.map { it.messageId }.toSet()
            val newRemoteMessages = remoteMessagesList.filter { it.id !in localMessageIds }
            allMessages.addAll(newRemoteMessages)
            
            // Ordenar por fecha de creación
            val sortedMessages = allMessages.sortedBy { it.createdAt.time }
            
            Log.d(TAG, "📨 [FETCH_MSG] Returning ${sortedMessages.size} total messages (${localMessages.size} local + ${newRemoteMessages.size} new remote)")
            
            sortedMessages
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching messages: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Helper function: Marca mensaje como leído, guarda localmente y elimina del servidor.
     * Implementa el patrón de mensajes efímeros (ephemeral messaging pattern):
     * 1. Guarda el mensaje localmente en ObjectBox PRIMERO (seguridad)
     * 2. Marca el mensaje como leído en Supabase
     * 3. Elimina el mensaje del servidor (cada dispositivo mantiene su propia copia local)
     */
    private suspend fun markMessageAsReadAndSave(messageId: Long, msg: MessageDTO) {
        try {
            // 1. Guardar localmente PRIMERO (para evitar pérdida de datos)
            val localEntity = LocalMessageEntity.fromDTO(msg, readAt = System.currentTimeMillis())
            localMessagesBox.put(localEntity)
            Log.d(TAG, "💾 [EPHEMERAL] Saved message $messageId to local storage FIRST")
            
            // NOTA: Las notificaciones en background fueron eliminadas.
            // Esta función solo marca mensajes como READ cuando el usuario los abre.
            
            // 2. Marcar como leído en Supabase
            val patchUrl = "$SUPABASE_URL/rest/v1/direct_messages?id=eq.$messageId"
            val patchConn = createConnection(patchUrl)
            patchConn.requestMethod = "PATCH"
            patchConn.doOutput = true
            patchConn.setRequestProperty("Prefer", "return=minimal")
            
            val patchBody = JSONObject().apply {
                put("status", "read")
                // put("seen_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(Date()))
            }
            
            OutputStreamWriter(patchConn.outputStream).use {
                it.write(patchBody.toString())
            }
            
            if (patchConn.responseCode in 200..299) {
                Log.d(TAG, "✅ [EPHEMERAL] Marked message $messageId as read")
                
                // 3. Eliminar del servidor SOLO después de guardar localmente
                val deleteUrl = "$SUPABASE_URL/rest/v1/direct_messages?id=eq.$messageId"
                val deleteConn = createConnection(deleteUrl)
                deleteConn.requestMethod = "DELETE"
                
                if (deleteConn.responseCode in 200..299) {
                    Log.d(TAG, "🗑️ [EPHEMERAL] Deleted message $messageId from server after saving locally")
                } else {
                    Log.w(TAG, "⚠️ [EPHEMERAL] Could not delete message from server: ${deleteConn.responseCode}")
                }
            } else {
                Log.w(TAG, "⚠️ [EPHEMERAL] Could not mark message as read: ${patchConn.responseCode}")
                // Incluso si falla el marcado como leído, ya tenemos el mensaje guardado localmente
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in markMessageAsReadAndSave: ${e.message}", e)
        }
    }
    
    private fun ensureMessagesNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "messages_channel",
                "Mensajes",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de mensajes directos"
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun safeNotificationId(messageId: Long): Int {
        val bucket = (messageId % 100_000L).toInt()
        return 10_000 + bucket
    }

    private fun computeDisplayName(profile: ProfileDTO?): String {
        if (profile == null) return "Usuario"

        val username = profile.username?.trim()
        if (!username.isNullOrBlank()) return username

        val fullName = listOfNotNull(profile.nombre?.trim(), profile.apellido?.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (fullName.isNotBlank()) fullName else "Usuario"
    }

    /**
     * Muestra una notificación de mensaje entrante.
     * IMPORTANTE: Esta función no inicia listeners en segundo plano.
     * Debe llamarse SOLO cuando la app esté en foreground (app abierta).
     */
    suspend fun showIncomingMessageNotification(message: MessageDTO) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔔 showIncomingMessageNotification: start messageId=${message.id}")

            val nm = NotificationManagerCompat.from(appContext)
            if (!nm.areNotificationsEnabled()) {
                Log.w(TAG, "🔕 Notifications disabled at app level (areNotificationsEnabled=false)")
                return@withContext
            }

            // Crear canal de notificaciones si no existe
            ensureMessagesNotificationChannel()

            // Resolver nombre del remitente (con cache simple en memoria)
            val senderIdStr = message.senderId.toString()
            val cachedName = synchronized(senderDisplayNameCache) { senderDisplayNameCache[senderIdStr] }
            val senderName = if (!cachedName.isNullOrBlank()) {
                cachedName
            } else {
                val profile = try {
                    fetchUserProfileById(message.senderId)
                } catch (_: Exception) {
                    null
                }
                val computed = computeDisplayName(profile)
                synchronized(senderDisplayNameCache) { senderDisplayNameCache[senderIdStr] = computed }
                computed
            }

            val content = message.content.ifBlank { "Nuevo mensaje" }

            // Intent para abrir el chat cuando se toca la notificación
            val intent = Intent(appContext, io.orabel.orabelandroid.ui.screens.search.SearchActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_chat_with_user_id", senderIdStr)
            }

            val pendingIntent = PendingIntent.getActivity(
                appContext,
                safeNotificationId(message.id),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(appContext, "messages_channel")
                .setSmallIcon(io.orabel.orabelandroid.R.drawable.ic_message)
                .setContentTitle(senderName)
                .setContentText(content)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(content)
                        .setBigContentTitle(senderName)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .build()

            try {
                NotificationManagerCompat.from(appContext).notify(safeNotificationId(message.id), notification)
                Log.d(TAG, "🔔 Notification shown (foreground) from $senderName")
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied to show notification", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing notification: ${e.message}", e)
        }
    }
    
    /**
     * Envía un mensaje a un usuario.
     * Ahora con soporte para reply context.
     */
    suspend fun sendMessage(
        targetUserId: String, 
        content: String,
        replyToId: Long? = null,
        replyContext: String? = null,
        replyContextSender: String? = null
    ): Result<MessageDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user session"))
        
        // Obtener información del usuario actual para guardarla con el mensaje
        val currentUserProfile = _currentUserProfile.value ?: try {
            fetchCurrentUserProfile()
        } catch (e: Exception) {
            null
        }
        
        try {
            val url = "$SUPABASE_URL/rest/v1/direct_messages"
            val conn = createConnection(url)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Prefer", "return=representation")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("sender_id", userId)
                put("receiver_id", targetUserId)
                put("content", content)
                put("is_temporary", true) // Efímero por defecto
                
                // Reply context (opcional)
                replyToId?.let { put("reply_to_id", it) }
                replyContext?.let { put("reply_context_content", it) }
                replyContextSender?.let { put("reply_context_sender_username", it) }
            }
            
            OutputStreamWriter(conn.outputStream).use {
                it.write(body.toString())
            }
            
            val response = conn.readResponse()
            
            if (conn.responseCode in 200..299) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val msgJson = jsonArray.getJSONObject(0)
                    val messageId = msgJson.getLong("id")
                    val senderId = msgJson.getString("sender_id")
                    val receiverId = msgJson.getString("receiver_id")
                    val messageContent = msgJson.getString("content")
                    val createdAtStr = msgJson.optString("created_at", "")
                    
                    val createdAt = try {
                        if (createdAtStr.isNotEmpty()) {
                            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(createdAtStr) ?: Date()
                        } else Date()
                    } catch (e: Exception) {
                        Date()
                    }
                    
                    val sentMessage = MessageDTO(
                        id = messageId,
                        senderId = UUID.fromString(senderId),
                        receiverId = UUID.fromString(receiverId),
                        content = messageContent,
                        isTemporary = true,
                        deliveredAt = null,
                        seenAt = null,
                        createdAt = createdAt,
                        status = "sent",
                        replyToId = replyToId,
                        replyContextContent = replyContext,
                        replyContextSenderUsername = replyContextSender,
                        sender = currentUserProfile?.let { 
                            PostCreator(
                                username = it.username,
                                nombre = it.nombre,
                                apellido = it.apellido,
                                avatar_url = it.avatarUrl
                            )
                        },
                        receiver = null,
                        replyToMessage = null
                    )
                    
                    // Guardar mensaje enviado localmente (para que aparezca inmediatamente)
                    val localEntity = LocalMessageEntity.fromDTO(sentMessage, readAt = System.currentTimeMillis())
                    localMessagesBox.put(localEntity)
                    Log.d(TAG, "💾 [SEND] Saved sent message to local storage")

                    // Si el status (delivered/read) llegó antes de que guardáramos este mensaje,
                    // lo aplicamos ahora para no quedarnos atascados en "sent".
                    val pending = pendingStatusUpdates.remove(messageId)
                    val finalMessage = if (pending != null) {
                        try {
                            localEntity.status = pending.status
                            if (pending.deliveredAt != null) localEntity.deliveredAt = pending.deliveredAt
                            if (pending.seenAt != null) localEntity.seenAt = pending.seenAt
                            localMessagesBox.put(localEntity)
                            Log.d(TAG, "💾 [SEND] Applied pending status to message $messageId: ${pending.status}")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ [SEND] Failed applying pending status for message $messageId: ${e.message}")
                        }

                        sentMessage.copy(
                            status = pending.status,
                            deliveredAt = pending.deliveredAt ?: sentMessage.deliveredAt,
                            seenAt = pending.seenAt ?: sentMessage.seenAt
                        )
                    } else {
                        sentMessage
                    }
                    
                    Result.success(finalMessage)
                } else {
                    Result.failure(Exception("Empty response"))
                }
            } else {
                Result.failure(Exception("Error sending message: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene la lista de conversaciones desde el cache local (ObjectBox).
     * Retorna una conversación por cada usuario único con el que se ha chateado.
     * Incluye el último mensaje y la información del usuario.
     */
    data class ConversationItem(
        val otherUserId: String,
        val otherUsername: String?,
        val otherUserFullName: String?,
        val otherUserAvatarUrl: String?,
        val lastMessage: String,
        val lastMessageDate: Date,
        val unreadCount: Int = 0
    )
    
    suspend fun fetchConversations(): List<ConversationItem> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        
        try {
            // Obtener todos los mensajes locales
            val allMessages = localMessagesBox.query().build().find()
            
            if (allMessages.isEmpty()) {
                Log.d(TAG, "📭 [CONVERSATIONS] No local messages found")
                return@withContext emptyList()
            }
            
            // Agrupar por usuario (sender o receiver que no sea el currentUser)
            val conversationsMap = mutableMapOf<String, MutableList<LocalMessageEntity>>()
            
            allMessages.forEach { msg ->
                val otherUserId = when {
                    msg.senderId == userId -> msg.receiverId
                    msg.receiverId == userId -> msg.senderId
                    else -> null
                }
                
                if (otherUserId != null) {
                    conversationsMap.getOrPut(otherUserId) { mutableListOf() }.add(msg)
                }
            }
            
            // Crear lista de conversaciones con el último mensaje de cada una
            val conversations = conversationsMap.map { (otherUserId, messages) ->
                val sortedMessages = messages.sortedByDescending { it.createdAt }
                val lastMessage = sortedMessages.first()
                
                // El nombre del otro usuario puede venir de cualquier mensaje donde él es el sender
                val messageFromOther = messages.find { it.senderId == otherUserId }
                val otherUsername = messageFromOther?.senderUsername
                val otherAvatarUrl = messageFromOther?.senderAvatarUrl
                
                // Construir nombre completo si está disponible
                val otherUserFullName = when {
                    messageFromOther != null -> {
                        val parts = listOfNotNull(
                            messageFromOther.senderUsername?.takeIf { it.isNotEmpty() }
                        )
                        if (parts.isNotEmpty()) parts.joinToString(" ") else null
                    }
                    else -> null
                }
                
                ConversationItem(
                    otherUserId = otherUserId,
                    otherUsername = otherUsername,
                    otherUserFullName = otherUserFullName ?: otherUsername ?: "Usuario",
                    otherUserAvatarUrl = otherAvatarUrl,
                    lastMessage = lastMessage.content,
                    lastMessageDate = Date(lastMessage.createdAt),
                    unreadCount = 0 // Por ahora no rastreamos mensajes no leídos
                )
            }.sortedByDescending { it.lastMessageDate }
            
            Log.d(TAG, "📬 [CONVERSATIONS] Found ${conversations.size} conversations")
            conversations
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching conversations: ${e.message}", e)
            emptyList()
        }
    }
    
    // ========== POSTS ==========
    
    /**
     * Helper para parsear JSON de un post.
     */
    private fun parsePost(json: JSONObject): PostDTO {
        fun optStringNullable(key: String): String? {
            return if (json.has(key) && !json.isNull(key)) json.getString(key) else null
        }

        val creatorJson = json.optJSONObject("creator")
        val creator = creatorJson?.let {
            PostCreatorDTO(
                username = if (it.has("username") && !it.isNull("username")) it.getString("username") else null,
                nombre = if (it.has("nombre") && !it.isNull("nombre")) it.getString("nombre") else null,
                apellido = if (it.has("apellido") && !it.isNull("apellido")) it.getString("apellido") else null,
                avatarUrl = if (it.has("avatar_url") && !it.isNull("avatar_url")) it.getString("avatar_url") else null
            )
        }
        
        // Parse post_reactions array con datos del usuario
        val reactionsArray = json.optJSONArray("post_reactions")
        val postReactions = mutableListOf<ReactionDTO>()
        val postId = UUID.fromString(json.getString("id"))
        if (reactionsArray != null) {
            for (i in 0 until reactionsArray.length()) {
                try {
                    val reactionJson = reactionsArray.getJSONObject(i)
                    
                    // Verificar que los campos requeridos existan
                    if (!reactionJson.has("id") || !reactionJson.has("emoji")) {
                        Log.w(TAG, "Skipping reaction without id or emoji")
                        continue
                    }
                    
                    // Parsear datos del usuario de la reacción
                    val userJson = reactionJson.optJSONObject("user")
                    val reactionUser = if (userJson != null) {
                        CommentUserDTO(
                            username = userJson.optString("username", null),
                            nombre = userJson.optString("nombre", null),
                            apellido = userJson.optString("apellido", null),
                            avatarUrl = userJson.optString("avatar_url", null)
                        )
                    } else {
                        null
                    }
                    
                    // user_id para el ReactionDTO (puede no venir por RLS)
                    val userIdStr = json.optString("creator_id", "00000000-0000-0000-0000-000000000000")
                    
                    postReactions.add(
                        ReactionDTO(
                            id = UUID.fromString(reactionJson.getString("id")),
                            postId = postId,
                            userId = UUID.fromString(userIdStr),
                            emoji = reactionJson.getString("emoji"),
                            createdAt = Date(),
                            user = reactionUser
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing reaction: ${e.message}")
                }
            }
        }
        val reactionCount = postReactions.size
        
        Log.d(TAG, "parsePost: Post ${json.optString("id").take(8)}... tiene ${postReactions.size} reacciones: ${postReactions.map { it.emoji }}")
        
        val commentsArray = json.optJSONArray("comments")
        val commentsCount = commentsArray?.length() ?: 0
        
        return PostDTO(
            id = UUID.fromString(json.getString("id")),
            creatorId = UUID.fromString(json.getString("creator_id")),
            caption = optStringNullable("caption"),
            mediaUrl = optStringNullable("media_url"),
            mediaType = json.optString("media_type", "image"),
            thumbnailUrl = optStringNullable("thumbnail_url"),
            contentType = json.optString("content_type", "post"),
            likesCount = json.optInt("likes_count", 0),
            commentsCount = commentsCount,
            reactionCount = reactionCount,
            isAnonymous = json.optBoolean("is_anonymous", false),
            durationSeconds = if (json.has("duration_seconds") && !json.isNull("duration_seconds")) 
                json.optInt("duration_seconds") else null,
            createdAt = null,
            creator = creator,
            postReactions = if (postReactions.isNotEmpty()) postReactions else null
        )
    }
    
    /**
     * Obtiene todas las publicaciones recientes del feed.
     */
    suspend fun fetchPosts(limit: Int = 20): List<PostDTO> = withContext(Dispatchers.IO) {
        try {
            // Query con datos completos del usuario en reacciones
            val urlString = "$SUPABASE_URL/rest/v1/posts?select=*,creator:users(username,nombre,apellido,avatar_url),post_reactions(id,emoji,user:users(username,nombre,apellido,avatar_url)),comments(id)&order=created_at.desc&limit=$limit"
            
            Log.d(TAG, "📰 [FETCH_POSTS] Query URL: $urlString")
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            val responseCode = conn.responseCode
            
            Log.d(TAG, "📥 [FETCH_POSTS] Status: $responseCode")
            
            if (responseCode == 200) {
                val jsonArray = JSONArray(response)
                val posts = mutableListOf<PostDTO>()
                
                for (i in 0 until jsonArray.length()) {
                    try {
                        posts.add(parsePost(jsonArray.getJSONObject(i)))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing post: ${e.message}")
                    }
                }
                
                Log.d(TAG, "✅ [FETCH_POSTS] Encontrados ${posts.size} posts")
                posts
            } else {
                Log.e(TAG, "❌ [FETCH_POSTS] Error: $responseCode - $response")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [FETCH_POSTS] Exception: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Obtiene los reels (videos cortos).
     * Filtra por media_type=video y content_type='reel' o 'both'
     */
    suspend fun fetchReels(limit: Int = 20): List<PostDTO> = withContext(Dispatchers.IO) {
        try {
            // Query con datos completos del usuario en reacciones
            val urlString = "$SUPABASE_URL/rest/v1/posts?select=*,creator:users(username,nombre,apellido,avatar_url),post_reactions(id,emoji,user:users(username,nombre,apellido,avatar_url)),comments(id)&media_type=eq.video&or=(content_type.eq.reel,content_type.eq.both)&order=created_at.desc&limit=$limit"
            
            Log.d(TAG, "🎬 [FETCH_REELS] Query URL: $urlString")
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            val responseCode = conn.responseCode
            
            Log.d(TAG, "📥 [FETCH_REELS] Status: $responseCode")
            
            if (responseCode == 200) {
                val jsonArray = JSONArray(response)
                val reels = mutableListOf<PostDTO>()
                
                for (i in 0 until jsonArray.length()) {
                    try {
                        reels.add(parsePost(jsonArray.getJSONObject(i)))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing reel: ${e.message}")
                    }
                }
                
                Log.d(TAG, "✅ [FETCH_REELS] Encontrados ${reels.size} reels")
                reels
            } else {
                Log.e(TAG, "❌ [FETCH_REELS] Error: $responseCode - $response")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [FETCH_REELS] Exception: ${e.message}")
            emptyList()
        }
    }
    /**
     * Obtiene los posts de un usuario específico.
     */
    suspend fun fetchUserPosts(userId: UUID): List<PostDTO> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/posts?creator_id=eq.$userId&select=*,creator:users(username,avatar_url),post_reactions(id),comments(id)&order=created_at.desc"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val posts = mutableListOf<PostDTO>()
                for (i in 0 until jsonArray.length()) {
                    try {
                        posts.add(parsePost(jsonArray.getJSONObject(i)))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user post: ${e.message}")
                    }
                }
                posts
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user posts: ${e.message}")
            emptyList()
        }
    }

    /**
     * Cuenta estadísticas del usuario (posts, seguidores, seguidos).
     * Nota: En una implementación real idealmente usaríamos RPC o HEAD count query.
     * Por simplicidad y compatibilidad REST, hacemos 3 queries count.
     */
    suspend fun countUserStats(userId: UUID): UserStatsDTO = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "countUserStats: Starting for userId=$userId")
            
            // Count Posts
            val postsConn = createConnection("$SUPABASE_URL/rest/v1/posts?creator_id=eq.$userId&select=id")
            postsConn.requestMethod = "HEAD"
            postsConn.setRequestProperty("Prefer", "count=exact")
            val postsResponseCode = postsConn.responseCode
            val postsCountStr = postsConn.getHeaderField("Content-Range")
            val postsCount = if (postsResponseCode in 200..299 && postsCountStr != null) {
                postsCountStr.substringAfter("/").toIntOrNull() ?: 0
            } else 0
            Log.d(TAG, "countUserStats: Posts count=$postsCount (header=$postsCountStr)")

            // Count Followers (usando tabla followers)
            val followersConn = createConnection("$SUPABASE_URL/rest/v1/followers?followed_id=eq.$userId&select=id")
            followersConn.requestMethod = "HEAD"
            followersConn.setRequestProperty("Prefer", "count=exact")
            val followersResponseCode = followersConn.responseCode
            val followersCountStr = followersConn.getHeaderField("Content-Range")
            val followersCount = if (followersResponseCode in 200..299 && followersCountStr != null) {
                followersCountStr.substringAfter("/").toIntOrNull() ?: 0
            } else 0
            Log.d(TAG, "countUserStats: Followers count=$followersCount (header=$followersCountStr)")

            // Count Following (usando tabla followers)
            val followingConn = createConnection("$SUPABASE_URL/rest/v1/followers?follower_id=eq.$userId&select=id")
            followingConn.requestMethod = "HEAD"
            followingConn.setRequestProperty("Prefer", "count=exact")
            val followingResponseCode = followingConn.responseCode
            val followingCountStr = followingConn.getHeaderField("Content-Range")
            val followingCount = if (followingResponseCode in 200..299 && followingCountStr != null) {
                followingCountStr.substringAfter("/").toIntOrNull() ?: 0
            } else 0
            Log.d(TAG, "countUserStats: Following count=$followingCount (header=$followingCountStr)")

            // Count Friends (usando tabla friends)
            val friendsConn = createConnection("$SUPABASE_URL/rest/v1/friends?user_id=eq.$userId&status=eq.active&select=id")
            friendsConn.requestMethod = "HEAD"
            friendsConn.setRequestProperty("Prefer", "count=exact")
            val friendsResponseCode = friendsConn.responseCode
            val friendsCountStr = friendsConn.getHeaderField("Content-Range")
            val friendsCount = if (friendsResponseCode in 200..299 && friendsCountStr != null) {
                friendsCountStr.substringAfter("/").toIntOrNull() ?: 0
            } else 0
            Log.d(TAG, "countUserStats: Friends count=$friendsCount (header=$friendsCountStr)")

            val stats = UserStatsDTO(postsCount, followersCount, followingCount, friendsCount)
            Log.d(TAG, "countUserStats: Final stats=$stats")
            stats
        } catch (e: Exception) {
            Log.e(TAG, "Error counting stats: ${e.message}", e)
            UserStatsDTO()
        }
    }

    /**
     * Obtiene los seguidores de un usuario específico (para la sección "Seguido por").
     */
    suspend fun fetchFollowersOf(userId: UUID): List<ProfileDTO> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/followers?followed_id=eq.$userId&select=follower:users!followers_follower_id_fkey(*)&limit=5"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val profiles = mutableListOf<ProfileDTO>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.has("follower")) {
                        profiles.add(parseProfile(obj.getJSONObject("follower")))
                    }
                }
                profiles
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching followers of user: ${e.message}")
            emptyList()
        }
    }

    /**
     * Obtiene el perfil completo de un usuario por su ID.
     */
    suspend fun fetchUserProfileById(userId: UUID): ProfileDTO? = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/users?id=eq.$userId&select=*"
            android.util.Log.d(TAG, "fetchUserProfileById: url=$urlString")
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            android.util.Log.d(TAG, "fetchUserProfileById: responseCode=${conn.responseCode}, body=$response")
            
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val profile = parseProfile(jsonArray.getJSONObject(0))
                    android.util.Log.d(TAG, "fetchUserProfileById: Parsed profile -> $profile")
                    profile
                } else {
                    android.util.Log.w(TAG, "fetchUserProfileById: User not found in DB or empty array")
                    null
                }
            } else {
                android.util.Log.e(TAG, "fetchUserProfileById: Request failed with code ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "fetchUserProfileById: Exception -> ${e.message}", e)
            null
        }
    }
    // ========== ACTUALIZACIÓN DE PERFIL ==========

    /**
     * Actualiza el perfil del usuario.
     */
    suspend fun updateUserProfile(userId: String, profile: ProfileDTO): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/users?id=eq.$userId"
            val conn = createConnection(urlString)
            conn.requestMethod = "PATCH"
            conn.doOutput = true
            
            val jsonBody = JSONObject().apply {
                // Mapear campos de ProfileDTO a columnas de Supabase
                // No actualizamos ID ni email/username aquí generalmente, solo datos de perfil
                put("nombre", profile.nombre)
                put("apellido", profile.apellido)
                put("edad", profile.edad)
                put("altura_cm", profile.altura_cm)
                put("peso_kg", profile.peso_kg)
                put("estado_civil", profile.estadoCivil)
                put("pais", profile.pais)
                put("estado_region", profile.estadoRegion)
                put("occupation", profile.occupation)
                put("bio", profile.bio)
                
                // Cache-busting: Añadimos un parámetro timestamp a las URLs si han cambiado
                // Esto fuerza a Coil/AsyncImage a recargar la imagen
                val timestamp = System.currentTimeMillis()
                
                if (profile.avatarUrl != null) {
                    val cleanAvatarUrl = profile.avatarUrl.substringBefore("?t=")
                    put("avatar_url", "$cleanAvatarUrl?t=$timestamp")
                }
                if (profile.bannerUrl != null) {
                    val cleanBannerUrl = profile.bannerUrl.substringBefore("?t=")
                    put("banner_url", "$cleanBannerUrl?t=$timestamp")
                }
            }
            
            OutputStreamWriter(conn.outputStream).use {
                it.write(jsonBody.toString())
            }
            
            val responseCode = conn.responseCode
            Log.d(TAG, "updateUserProfile response: $responseCode")
            
            if (responseCode in 200..299) {
                _currentUserProfile.value = profile // Emitir actualización inmediata
                Result.success(Unit)
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error updating profile: $error")
                Result.failure(Exception("Error updating profile ($responseCode)"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating profile: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Sube un avatar al bucket de storage 'avatars'.
     * Retorna la URL pública.
     */
    suspend fun uploadAvatar(userId: String, byteArray: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "$userId/avatar.jpg" // Estructura {userId}/filename requerida por RLS
            val urlString = "$SUPABASE_URL/storage/v1/object/avatars/$fileName"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "POST" 
            
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("x-upsert", "true") 
            conn.doOutput = true
            
            conn.outputStream.use { it.write(byteArray) }
            
            if (conn.responseCode in 200..299) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/avatars/$fileName"
                Result.success(publicUrl)
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error uploading avatar: $error")
                Result.failure(Exception("Error uploading avatar: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading avatar: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sube un banner al bucket de storage 'avatars'.
     * Retorna la URL pública.
     */
    suspend fun uploadBanner(userId: String, byteArray: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fileName = "$userId/banner.jpg" // Estructura {userId}/filename requerida por RLS
            val urlString = "$SUPABASE_URL/storage/v1/object/banners/$fileName"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.setRequestProperty("x-upsert", "true") 
            conn.doOutput = true
            
            conn.outputStream.use { it.write(byteArray) }
            
            if (conn.responseCode in 200..299) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/banners/$fileName"
                Result.success(publicUrl)
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error uploading banner: $error")
                Result.failure(Exception("Error uploading banner: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading banner: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sube una imagen de post al bucket 'post_media'.
     */
    suspend fun uploadPostMedia(userId: String, byteArray: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val imageId = UUID.randomUUID().toString()
            val fileName = "$userId/$imageId.jpg"
            val urlString = "$SUPABASE_URL/storage/v1/object/post_media/$fileName"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.doOutput = true
            
            conn.outputStream.use { it.write(byteArray) }
            
            if (conn.responseCode in 200..299) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/post_media/$fileName"
                Result.success(publicUrl)
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error uploading post media: $error")
                Result.failure(Exception("Error uploading post media: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading post media: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sube un video de reel al bucket 'reel_videos'.
     */
    suspend fun uploadReelVideo(userId: String, byteArray: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val videoId = UUID.randomUUID().toString()
            val fileName = "$userId/$videoId.mp4"
            val urlString = "$SUPABASE_URL/storage/v1/object/reel_videos/$fileName"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "video/mp4")
            conn.doOutput = true
            
            conn.outputStream.use { it.write(byteArray) }
            
            if (conn.responseCode in 200..299) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/reel_videos/$fileName"
                Result.success(publicUrl)
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error uploading reel video: $error")
                Result.failure(Exception("Error uploading reel video: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading reel video: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sube un thumbnail de video al bucket 'video_thumbnails'.
     */
    suspend fun uploadVideoThumbnail(userId: String, byteArray: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val imageId = UUID.randomUUID().toString()
            val fileName = "$userId/${imageId}_thumb.jpg"
            val urlString = "$SUPABASE_URL/storage/v1/object/video_thumbnails/$fileName"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "image/jpeg")
            conn.doOutput = true
            
            conn.outputStream.use { it.write(byteArray) }
            
            if (conn.responseCode in 200..299) {
                val publicUrl = "$SUPABASE_URL/storage/v1/object/public/video_thumbnails/$fileName"
                Result.success(publicUrl)
            } else {
                val error = conn.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Error uploading video thumbnail: $error")
                Result.failure(Exception("Error uploading video thumbnail: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception uploading video thumbnail: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Crea un post de video (Reel).
     */
    suspend fun createVideoPost(
        caption: String,
        videoUrl: String,
        thumbnailUrl: String?,
        durationSeconds: Int?,
        contentType: String = "both",
        title: String? = null,
        category: String? = null
    ): PostDTO? = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserUUID() ?: return@withContext null
            val urlString = "$SUPABASE_URL/rest/v1/posts"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("creator_id", userId.toString())
                put("caption", caption)
                put("media_url", videoUrl)
                put("media_type", "video")
                put("content_type", contentType)
                if (title != null) put("title", title)
                if (category != null) put("category", category)
                if (thumbnailUrl != null) put("thumbnail_url", thumbnailUrl)
                if (durationSeconds != null) put("duration_seconds", durationSeconds)
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            val response = conn.readResponse()
            val responseCode = conn.responseCode
            Log.d(TAG, "createVideoPost: responseCode=$responseCode, body=$response")
            
            if (responseCode in 200..299) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    parsePost(jsonArray.getJSONObject(0))
                } else {
                    Log.e(TAG, "createVideoPost: Empty response array")
                    null
                }
            } else {
                Log.e(TAG, "createVideoPost: Error response code=$responseCode, body=$response")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating video post: ${e.message}", e)
            null
        }
    }

    /**
     * Crea un post normal (imagen o texto).
     */
    suspend fun createPost(
        caption: String,
        mediaUrl: String,
        mediaType: String = "image",
        contentType: String = "post",
        title: String? = null,
        category: String? = null
    ): PostDTO? = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserUUID() ?: return@withContext null
            val urlString = "$SUPABASE_URL/rest/v1/posts"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("creator_id", userId.toString())
                put("caption", caption)
                put("media_url", mediaUrl)
                put("media_type", mediaType)
                put("content_type", contentType)
                if (title != null) put("title", title)
                if (category != null) put("category", category)
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            val response = conn.readResponse()
            val responseCode = conn.responseCode
            Log.d(TAG, "createPost: responseCode=$responseCode, body=$response")
            
            if (responseCode in 200..299) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    parsePost(jsonArray.getJSONObject(0))
                } else {
                    Log.e(TAG, "createPost: Empty response array")
                    null
                }
            } else {
                Log.e(TAG, "createPost: Error response code=$responseCode, body=$response")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating post: ${e.message}", e)
            null
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/posts?id=eq.$postId"
            val conn = createConnection(urlString)
            conn.requestMethod = "DELETE"
            
            if (conn.responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error deleting post: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reactToPost(postId: String, reactionType: String): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            Log.d(TAG, "reactToPost: postId=$postId, emoji=$reactionType, userId=$userId")
            
            // Primero eliminar reacción existente (si hay)
            val deleteUrl = "$SUPABASE_URL/rest/v1/post_reactions?post_id=eq.$postId&user_id=eq.$userId"
            val deleteConn = createConnection(deleteUrl)
            deleteConn.requestMethod = "DELETE"
            deleteConn.responseCode // Execute delete
            
            // Ahora insertar la nueva reacción
            val urlString = "$SUPABASE_URL/rest/v1/post_reactions"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("post_id", postId)
                put("user_id", userId)
                put("emoji", reactionType)  // Usar 'emoji' no 'reaction_type'
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            val responseCode = conn.responseCode
            Log.d(TAG, "reactToPost response: $responseCode")
            
            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Error reacting to post: $responseCode - $error")
                Result.failure(Exception("Error reacting: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception reacting to post", e)
            Result.failure(e)
        }
    }

    suspend fun removeReaction(postId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            Log.d(TAG, "removeReaction: postId=$postId, userId=$userId")
            
            val urlString = "$SUPABASE_URL/rest/v1/post_reactions?post_id=eq.$postId&user_id=eq.$userId"
            val conn = createConnection(urlString)
            conn.requestMethod = "DELETE"
            
            val responseCode = conn.responseCode
            Log.d(TAG, "removeReaction response: $responseCode")
            
            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                Log.e(TAG, "Error removing reaction: $responseCode - $error")
                Result.failure(Exception("Error removing: $error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception removing reaction", e)
            Result.failure(e)
        }
    }

    suspend fun getUserReactionForPost(postId: String): String? = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext null
        try {
            Log.d(TAG, "getUserReactionForPost: postId=$postId, userId=$userId")
            
            val urlString = "$SUPABASE_URL/rest/v1/post_reactions?post_id=eq.$postId&user_id=eq.$userId&select=emoji"
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            val responseCode = conn.responseCode
            
            Log.d(TAG, "getUserReactionForPost response: $responseCode, body: $response")
            
            if (responseCode == 200) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val emoji = jsonArray.getJSONObject(0).getString("emoji")
                    Log.d(TAG, "Usuario ya reaccionó con: $emoji")
                    emoji
                } else {
                    Log.d(TAG, "Usuario no ha reaccionado a este post")
                    null
                }
            } else {
                Log.e(TAG, "Error obteniendo reacción del usuario: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception obteniendo reacción del usuario", e)
            null
        }
    }

    // ========== COMENTARIOS ==========

    suspend fun fetchComments(postId: String): List<CommentDTO> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/comments?post_id=eq.$postId&select=*,user:users(username,nombre,apellido,avatar_url)&order=created_at.asc"
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val comments = mutableListOf<CommentDTO>()
                for (i in 0 until jsonArray.length()) {
                    try {
                        val obj = jsonArray.getJSONObject(i)
                        val userObj = obj.optJSONObject("user")
                        val user = if (userObj != null) {
                            CommentUserDTO(
                                username = userObj.optString("username"),
                                nombre = userObj.optString("nombre"),
                                apellido = userObj.optString("apellido"),
                                avatarUrl = userObj.optString("avatar_url")
                            )
                        } else null
                        
                        val createdAtStr = obj.optString("created_at", "")
                        val createdAt = try {
                            if (createdAtStr.isNotEmpty()) {
                                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(createdAtStr) ?: Date()
                            } else Date()
                        } catch (e: Exception) { Date() }

                        comments.add(CommentDTO(
                            id = UUID.fromString(obj.getString("id")),
                            postId = UUID.fromString(obj.getString("post_id")),
                            userId = UUID.fromString(obj.getString("user_id")),
                            content = obj.getString("content"),
                            createdAt = createdAt,
                            user = user,
                            parentId = if (obj.has("parent_id") && !obj.isNull("parent_id")) UUID.fromString(obj.getString("parent_id")) else null,
                            likesCount = obj.optInt("likes_count", 0),
                            dislikesCount = obj.optInt("dislikes_count", 0)
                        ))
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing comment: ${e.message}")
                    }
                }
                comments
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching comments: ${e.message}")
            emptyList()
        }
    }

    suspend fun addComment(postId: String, content: String, parentId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            val urlString = "$SUPABASE_URL/rest/v1/comments"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("post_id", postId)
                put("user_id", userId)
                put("content", content)
                if (parentId != null) put("parent_id", parentId)
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            if (conn.responseCode in 200..299) {
                // Log interaction (fire and forget)
                // logInteraction(postId, "post", "comment") 
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error adding comment: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteComment(commentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/comments?id=eq.$commentId"
            val conn = createConnection(urlString)
            conn.requestMethod = "DELETE"
            
            if (conn.responseCode in 200..299) Result.success(Unit) else Result.failure(Exception("Error deleting comment"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateComment(commentId: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/comments?id=eq.$commentId"
            val conn = createConnection(urlString)
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val body = JSONObject().apply { put("content", content) }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            if (conn.responseCode in 200..299) Result.success(Unit) else Result.failure(Exception("Error updating comment"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reactToComment(commentId: String, type: String): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            val urlString = "$SUPABASE_URL/rest/v1/comment_reactions"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("comment_id", commentId)
                put("user_id", userId)
                put("reaction_type", type)
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            if (conn.responseCode in 200..299) Result.success(Unit) else Result.failure(Exception("Error reacting to comment"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeCommentReaction(commentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            val urlString = "$SUPABASE_URL/rest/v1/comment_reactions?comment_id=eq.$commentId&user_id=eq.$userId"
            val conn = createConnection(urlString)
            conn.requestMethod = "DELETE"
            
            if (conn.responseCode in 200..299) Result.success(Unit) else Result.failure(Exception("Error removing comment reaction"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMyCommentReaction(commentId: String): String? = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext null
        try {
            val urlString = "$SUPABASE_URL/rest/v1/comment_reactions?comment_id=eq.$commentId&user_id=eq.$userId&select=reaction_type"
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    jsonArray.getJSONObject(0).optString("reaction_type")
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    // ========== GRUPOS ==========

    suspend fun createGroup(name: String, description: String?, type: String): Result<GroupDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            // 1. Crear grupo
            val urlString = "$SUPABASE_URL/rest/v1/groups"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("name", name)
                put("description", description ?: "")
                put("type", type)
                put("creator_id", userId)
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            val response = conn.readResponse()
            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Error creating group: ${conn.responseCode}"))
            }
            
            val jsonArray = JSONArray(response)
            if (jsonArray.length() == 0) return@withContext Result.failure(Exception("No group returned"))
            
            val groupObj = jsonArray.getJSONObject(0)
            val group = GroupDTO(
                id = UUID.fromString(groupObj.getString("id")),
                nombre = groupObj.getString("name"),
                descripcion = groupObj.optString("description"),
                tipo = groupObj.getString("type"),
                creadorId = UUID.fromString(groupObj.getString("creator_id")),
                imagenUrl = groupObj.optString("image_url", null)
            )
            
            // 2. Unirse como admin
            joinGroup(group.id.toString(), "admin")
            
            Result.success(group)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGroup(groupId: String, role: String = "member"): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            val urlString = "$SUPABASE_URL/rest/v1/group_members"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("group_id", groupId)
                put("user_id", userId)
                put("role", role)
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            if (conn.responseCode in 200..299) Result.success(Unit) else Result.failure(Exception("Error joining group"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMyGroups(): List<GroupDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        try {
            val urlString = "$SUPABASE_URL/rest/v1/group_members?user_id=eq.$userId&select=group:groups!group_members_group_id_fkey(*)"
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val groups = mutableListOf<GroupDTO>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.has("group")) {
                        val groupObj = obj.getJSONObject("group")
                        groups.add(GroupDTO(
                            id = UUID.fromString(groupObj.getString("id")),
                            nombre = groupObj.getString("name"),
                            descripcion = groupObj.optString("description"),
                            tipo = groupObj.getString("type"),
                            creadorId = UUID.fromString(groupObj.getString("creator_id")),
                            imagenUrl = groupObj.optString("image_url", null)
                        ))
                    }
                }
                groups
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching my groups: ${e.message}")
            emptyList()
        }
    }

    // ========== POST UPDATE ==========

    suspend fun updatePost(postId: String, caption: String, title: String? = null, category: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/posts?id=eq.$postId"
            val conn = createConnection(urlString)
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("caption", caption)
                if (title != null) put("title", title)
                if (category != null) put("category", category)
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            if (conn.responseCode in 200..299) Result.success(Unit) else Result.failure(Exception("Error updating post"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postComment(postId: String, content: String, parentId: UUID? = null): Boolean = withContext(Dispatchers.IO) {
        val result = addComment(postId, content, parentId?.toString())
        result.isSuccess
    }

    fun buildTree(comments: List<CommentDTO>): List<CommentNode> {
        val nodeMap = mutableMapOf<UUID, CommentNode>()
        val rootNodes = mutableListOf<CommentNode>()
        
        // Crear nodos para todos los comentarios
        comments.forEach { comment ->
            nodeMap[comment.id] = CommentNode(comment.id, comment, mutableListOf())
        }
        
        // Construir el árbol
        comments.forEach { comment ->
            val node = nodeMap[comment.id]!!
            if (comment.parentId == null) {
                rootNodes.add(node)
            } else {
                val parentNode = nodeMap[comment.parentId]
                if (parentNode != null) {
                    (parentNode.children as MutableList).add(node)
                } else {
                    // Si no hay parent, agregar a root
                    rootNodes.add(node)
                }
            }
        }
        
        return rootNodes
    }

    // ========== STORIES/ESTADOS ==========

    /**
     * Sube una nueva historia/estado.
     */
    suspend fun uploadStory(request: StoryUploadRequest): Result<StoryDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            val urlString = "$SUPABASE_URL/rest/v1/stories"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("user_id", userId)
                put("media_url", request.mediaUrl)
                put("media_type", request.mediaType)
                if (request.thumbnailUrl != null) put("thumbnail_url", request.thumbnailUrl)
                if (request.caption != null) put("caption", request.caption)
                put("duration_seconds", request.durationSeconds)
                put("visibility", request.visibility)
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            val response = conn.readResponse()
            if (conn.responseCode in 200..299) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val storyObj = jsonArray.getJSONObject(0)
                    Result.success(parseStory(storyObj))
                } else {
                    Result.failure(Exception("No story returned"))
                }
            } else {
                Result.failure(Exception("Error uploading story: ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading story: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Obtiene las historias activas de amigos y/o seguidores.
     * Retorna agrupadas por usuario.
     */
    suspend fun fetchStories(): List<UserStoriesGroup> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        try {
            // Consulta compleja: obtener historias activas donde:
            // - El creador es amigo del usuario actual (friends)
            // - O el usuario actual sigue al creador (followers)
            // La visibilidad se maneja en el servidor, aquí obtenemos todas las activas
            
            val urlString = "$SUPABASE_URL/rest/v1/stories?" +
                "is_active=eq.true" +
                "&expires_at=gt.${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(Date())}" +
                "&select=*,users!stories_user_id_fkey(id,username,avatar_url,nombre,apellido)" +
                "&order=created_at.desc"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val storiesList = mutableListOf<StoryDTO>()
                
                for (i in 0 until jsonArray.length()) {
                    val storyObj = jsonArray.getJSONObject(i)
                    storiesList.add(parseStory(storyObj))
                }
                
                // Agrupar por usuario
                val grouped = storiesList.groupBy { it.userId }
                grouped.map { (uid, stories) ->
                    val firstStory = stories.first()
                    UserStoriesGroup(
                        userId = uid,
                        username = firstStory.username,
                        avatarUrl = firstStory.avatarUrl,
                        displayName = firstStory.displayName,
                        stories = stories,
                        hasUnviewedStory = true // TODO: implementar lógica de visto
                    )
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching stories: ${e.message}")
            emptyList()
        }
    }

    /**
     * Marca una historia como vista.
     */
    suspend fun markStoryAsViewed(storyId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            val urlString = "$SUPABASE_URL/rest/v1/story_views"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("story_id", storyId.toString())
                put("viewer_id", userId)
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            if (conn.responseCode in 200..299) {
                // También incrementar el contador de vistas
                incrementStoryViews(storyId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error marking story as viewed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error marking story as viewed: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun incrementStoryViews(storyId: UUID): Unit = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/rpc/increment_story_views"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("story_id", storyId.toString())
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            conn.readResponse()
        } catch (e: Exception) {
            Log.e(TAG, "Error incrementing story views: ${e.message}")
        }
    }

    /**
     * Obtiene las historias del usuario actual.
     */
    suspend fun fetchMyStories(): List<StoryDTO> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext emptyList()
        try {
            val urlString = "$SUPABASE_URL/rest/v1/stories?" +
                "user_id=eq.$userId" +
                "&is_active=eq.true" +
                "&select=*,users!stories_user_id_fkey(id,username,avatar_url,nombre,apellido)" +
                "&order=created_at.desc"
            
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                val storiesList = mutableListOf<StoryDTO>()
                
                for (i in 0 until jsonArray.length()) {
                    val storyObj = jsonArray.getJSONObject(i)
                    storiesList.add(parseStory(storyObj))
                }
                
                storiesList
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching my stories: ${e.message}")
            emptyList()
        }
    }

    /**
     * Elimina una historia.
     */
    suspend fun deleteStory(storyId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/stories?id=eq.$storyId"
            val conn = createConnection(urlString)
            conn.requestMethod = "DELETE"
            
            if (conn.responseCode in 200..299) Result.success(Unit) 
            else Result.failure(Exception("Error deleting story"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStory(json: JSONObject): StoryDTO {
        val userObj = if (json.has("users")) json.getJSONObject("users") else null
        
        // Parse dates manually
        val createdAtStr = json.getString("created_at")
        val expiresAtStr = json.getString("expires_at")
        val createdAt = try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(createdAtStr) ?: Date()
        } catch (e: Exception) {
            Date()
        }
        val expiresAt = try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(expiresAtStr) ?: Date()
        } catch (e: Exception) {
            Date()
        }
        
        return StoryDTO(
            id = UUID.fromString(json.getString("id")),
            userId = UUID.fromString(json.getString("user_id")),
            mediaUrl = json.getString("media_url"),
            mediaType = json.getString("media_type"),
            thumbnailUrl = json.optString("thumbnail_url", null),
            caption = json.optString("caption", null),
            durationSeconds = json.optInt("duration_seconds", 5),
            createdAt = createdAt,
            expiresAt = expiresAt,
            viewsCount = json.optInt("views_count", 0),
            isActive = json.optBoolean("is_active", true),
            visibility = json.optString("visibility", "followers"),
            username = userObj?.optString("username"),
            avatarUrl = userObj?.optString("avatar_url"),
            nombre = userObj?.optString("nombre"),
            apellido = userObj?.optString("apellido")
        )
    }
    
    // ========== REALTIME MESSAGING ==========
    
    /**
     * Suscribe a mensajes nuevos en tiempo real para una conversación específica.
     * Retorna un Flow de mensajes nuevos que llegan.
     */
    fun subscribeToMessages(partnerId: String): Flow<MessageDTO> = callbackFlow {
        val currentUserId = getCurrentUserId() ?: run {
            Log.e(TAG, "❌ subscribeToMessages: No current user ID")
            close()
            return@callbackFlow
        }
        
        Log.d(TAG, "🔔 Setting up Realtime subscription for messages | Partner: $partnerId")
        
        // Usar un nombre de canal único por conversación para mayor estabilidad
        val channel = io.orabel.orabelandroid.auth.SupabaseClient.client.realtime.channel("chat:$currentUserId:$partnerId")
        
        // 1. Configurar el flow ANTES de suscribir al canal
        val postgresFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "direct_messages"
        }
        
        val realtimeJob = launch {
            try {
                // 2. Colectar eventos
                postgresFlow.collect { action ->
                    Log.d(TAG, "📨 [REALTIME] postgresChangeFlow received event in channel chat:$currentUserId:$partnerId")
                    
                    val jsonRecord = action.record as? JsonObject
                    if (jsonRecord == null) {
                        Log.w(TAG, "   ⚠️ Record is not a JsonObject")
                        return@collect
                    }

                    val senderId = try {
                        jsonRecord["sender_id"]?.jsonPrimitive?.content 
                            ?: jsonRecord["senderId"]?.jsonPrimitive?.content 
                            ?: ""
                    } catch (e: Exception) { "" }
                    
                    val receiverId = try {
                        jsonRecord["receiver_id"]?.jsonPrimitive?.content 
                            ?: jsonRecord["receiverId"]?.jsonPrimitive?.content 
                            ?: ""
                    } catch (e: Exception) { "" }
                    
                    Log.d(TAG, "   📬 [REALTIME] Candidate: sender=$senderId, receiver=$receiverId (Looking for partner=$partnerId, current=$currentUserId)")

                    if (senderId == partnerId && receiverId == currentUserId) {
                        Log.d(TAG, "   ✅ [REALTIME] Message matches partner, emitting to UI")
                        val message = parseMessageFromRealtimeRecord(jsonRecord)
                        trySend(message).isSuccess
                    } else {
                        Log.d(TAG, "   ⏭️ [REALTIME] Ignored (Sender mismatch or not for me)")
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "❌ [REALTIME] Flow error: ${e.message}", e)
                }
                close(e)
            }
        }
        
        // 3. Suscribir al canal
        launch {
            try {
                channel.subscribe()
                Log.d(TAG, "✅ [REALTIME] Channel subscribed: chat:$currentUserId:$partnerId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [REALTIME] Subscription error: ${e.message}")
            }
        }
        
        awaitClose {
            Log.d(TAG, "🔌 [REALTIME] Closing subscription for: $partnerId")
            realtimeJob.cancel()
            launch { 
                try {
                    channel.unsubscribe()
                    Log.d(TAG, "✅ [REALTIME] Unsubscribed from: $partnerId")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [REALTIME] Unsubscribe error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Suscribe a TODOS los mensajes nuevos donde el usuario actual es receiver.
     * Se usa en la lista de conversaciones para detectar mensajes entrantes.
     */
    fun subscribeToAllMessages(): Flow<MessageDTO> = callbackFlow {
        val currentUserId = getCurrentUserId() ?: run {
            Log.e(TAG, "❌ subscribeToAllMessages: No current user ID")
            close()
            return@callbackFlow
        }
        
        Log.d(TAG, "🔔 [INBOX] Setting up Realtime subscription for ALL incoming messages")
        
        val channel = io.orabel.orabelandroid.auth.SupabaseClient.client.realtime.channel("inbox:$currentUserId:${System.currentTimeMillis()}")
        
        val postgresFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "direct_messages"
        }
        
        val realtimeJob = launch {
            try {
                postgresFlow.collect { action ->
                    val jsonRecord = action.record as? JsonObject ?: return@collect
                    
                    val receiverId = jsonRecord["receiver_id"]?.jsonPrimitive?.content ?: ""
                    
                    // Solo emitir si el mensaje es para nosotros
                    if (receiverId == currentUserId) {
                        Log.d(TAG, "📨 [INBOX] New message received, emitting to UI")
                        val message = parseMessageFromRealtimeRecord(jsonRecord)
                        trySend(message).isSuccess
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "❌ [INBOX] Flow error: ${e.message}", e)
                }
                close(e)
            }
        }
        
        launch {
            try {
                channel.subscribe()
                Log.d(TAG, "✅ [INBOX] Channel subscribed for all incoming messages")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [INBOX] Subscription error: ${e.message}")
            }
        }
        
        awaitClose {
            Log.d(TAG, "🔌 [INBOX] Closing inbox subscription")
            realtimeJob.cancel()
            launch { 
                try {
                    channel.unsubscribe()
                    Log.d(TAG, "✅ [INBOX] Unsubscribed from inbox")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [INBOX] Unsubscribe error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Suscribe a cambios de status en mensajes (para palomas: sent → delivered → read).
     * Emite pares (messageId, newStatus) cuando un mensaje cambia de estado.
     */
    fun subscribeToMessageStatusChanges(partnerId: String): Flow<Pair<Long, String>> = callbackFlow {
        val currentUserId = getCurrentUserId() ?: run {
            Log.e(TAG, "❌ subscribeToMessageStatusChanges: No current user ID")
            close()
            return@callbackFlow
        }
        
        Log.d(TAG, "📬 [MSG_STATUS] Subscribing to message status changes | Partner: $partnerId")
        
        val channel = io.orabel.orabelandroid.auth.SupabaseClient.client.realtime.channel("msg_status:$currentUserId:$partnerId")
        
        val postgresFlow = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "direct_messages"
        }
        
        val realtimeJob = launch {
            try {
                postgresFlow.collect { action ->
                    val jsonRecord = action.record as? JsonObject ?: return@collect
                    
                    val senderId = jsonRecord["sender_id"]?.jsonPrimitive?.content ?: ""
                    val receiverId = jsonRecord["receiver_id"]?.jsonPrimitive?.content ?: ""
                    
                    // Filtrar: solo mensajes de esta conversación
                    if ((senderId == currentUserId && receiverId == partnerId) ||
                        (senderId == partnerId && receiverId == currentUserId)) {
                        
                        val messageId = jsonRecord["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@collect
                        val newStatus = jsonRecord["status"]?.jsonPrimitive?.content ?: "sent"
                        
                        Log.d(TAG, "📬 [MSG_STATUS] Message $messageId status changed to: $newStatus")
                        trySend(Pair(messageId, newStatus))
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e(TAG, "❌ [MSG_STATUS] Flow error: ${e.message}", e)
                }
                close(e)
            }
        }
        
        launch {
            try {
                channel.subscribe()
                Log.d(TAG, "✅ [MSG_STATUS] Subscribed to status changes")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [MSG_STATUS] Subscription error: ${e.message}")
            }
        }
        
        awaitClose {
            Log.d(TAG, "🔌 [MSG_STATUS] Closing subscription")
            realtimeJob.cancel()
            launch { 
                try {
                    channel.unsubscribe()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [MSG_STATUS] Unsubscribe error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Actualiza el estado del usuario (online/offline/chatting).
     */
    suspend fun updateUserStatus(status: String): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: run {
            Log.e(TAG, "❌ Cannot update status: userId is NULL (Session might be lost)")
            return@withContext Result.failure(Exception("No user"))
        }
        val token = getAccessToken()
        Log.d(TAG, "📡 [STATUS_UPDATE] Initiating update to '$status' for userId=$userId (Token exists: ${token != null})")
        
        try {
            val urlString = "$SUPABASE_URL/rest/v1/users?id=eq.$userId"
            val conn = createConnection(urlString)
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("status", status)
                put("is_active", status != "offline")
                put("last_active_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(Date()))
            }
            
            Log.d(TAG, "📤 [STATUS_UPDATE] Sending body: $body")
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            val responseCode = conn.responseCode
            val response = conn.readResponse()
            
            if (responseCode in 200..299) {
                Log.d(TAG, "✅ [STATUS_UPDATE] Success! ResponseCode=$responseCode")
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ [STATUS_UPDATE] Failed! Code=$responseCode, Response=$response")
                Result.failure(Exception("Error $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [STATUS_UPDATE] Exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Envía o actualiza el indicador de "escribiendo...".
     */
    suspend fun setTypingIndicator(partnerId: String, isTyping: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        try {
            val urlString = "$SUPABASE_URL/rest/v1/typing_indicators"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("user_id", userId)
                put("partner_id", partnerId)
                put("is_typing", isTyping)
                put("updated_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(Date()))
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            if (conn.responseCode in 200..299) Result.success(Unit) 
            else Result.failure(Exception("Error setting typing indicator"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Suscribe al indicador de "escribiendo..." del partner.
     */
    fun subscribeToTypingIndicator(partnerId: String): Flow<Boolean> = callbackFlow {
        val currentUserId = getCurrentUserId() ?: run {
            close()
            return@callbackFlow
        }
        
        val channel = io.orabel.orabelandroid.auth.SupabaseClient.client.realtime.channel("typing:$partnerId")
        
        val job = launch {
            // Escuchar tanto inserts como updates
            channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "typing_indicators"
            }.collect { action ->
                val record = action.record as? Map<*, *> ?: return@collect
                val userId = record["user_id"] as? String
                val partnerIdFromRecord = record["partner_id"] as? String
                
                // Solo procesar si es del partner escribiendo al currentUser
                if (userId == partnerId && partnerIdFromRecord == currentUserId) {
                    val isTyping = record["is_typing"] as? Boolean ?: false
                    trySend(isTyping)
                }
            }
        }
        
        channel.subscribe()
        
        awaitClose {
            job.cancel()
            launch { channel.unsubscribe() }
        }
    }
    
    /**
     * Suscribe al estado del partner (online/offline/chatting).
     */
    fun subscribeToUserStatus(partnerId: String): Flow<String> = callbackFlow {
        Log.d(TAG, "👤 Subscribing to user status for partner: $partnerId")
        val channel = io.orabel.orabelandroid.auth.SupabaseClient.client.realtime.channel("user_status:$partnerId")
        
        val job = launch {
            channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "users"
            }.collect { action ->
                val jsonRecord = action.record as? JsonObject ?: return@collect
                
                // Client-side filtering (fallback since server-side filter syntax is unsure)
                val userId = jsonRecord["id"]?.jsonPrimitive?.content
                if (userId != partnerId) return@collect
                
                // Ya viene filtrado, no hace falta verificar ID
                val status = jsonRecord["status"]?.jsonPrimitive?.content ?: "offline"
                val isActive = jsonRecord["is_active"]?.jsonPrimitive?.content?.toBoolean() ?: false
                
                val finalStatus = if (!isActive) "offline" else status
                Log.d(TAG, "✅ [REALTIME] Status update for $partnerId: $finalStatus (active=$isActive)")
                trySend(finalStatus)
            }
        }
        
        channel.subscribe()
        Log.d(TAG, "✅ Subscribed to status for: $partnerId")
        
        awaitClose {
            Log.d(TAG, "🔴 Unsubscribing from: $partnerId")
            job.cancel()
            launch { channel.unsubscribe() }
        }
    }
    
    /**
     * Obtiene el estado actual del usuario.
     */
    suspend fun getUserStatus(userId: String): String = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/users?id=eq.$userId&select=status,is_active"
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val obj = jsonArray.getJSONObject(0)
                    val isActive = obj.optBoolean("is_active", false)
                    val status = obj.optString("status", "offline")
                    val finalStatus = if (!isActive) "offline" else status
                    Log.d(TAG, "📊 Got user status for $userId: $finalStatus (isActive=$isActive, status=$status)")
                    return@withContext finalStatus
                }
            }
            Log.d(TAG, "📊 No status found for user $userId, defaulting to offline")
            "offline"
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting user status: ${e.message}")
            "offline"
        }
    }
    
    /**
     * Actualiza el estado de chat específico (chat_status: chatting/offline).
     * Independiente del campo 'status' general del usuario.
     * @param chatStatus 'chatting' o 'offline'
     * @param partnerId ID del usuario con quien está chateando (null si offline)
     */
    suspend fun updateChatStatus(chatStatus: String, partnerId: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: run {
            Log.e(TAG, "❌ Cannot update chat_status: userId is NULL")
            return@withContext Result.failure(Exception("No user"))
        }
        Log.d(TAG, "📡 [CHAT_STATUS_UPDATE] Setting chat_status='$chatStatus' partnerId='$partnerId' for userId=$userId")
        
        try {
            val urlString = "$SUPABASE_URL/rest/v1/users?id=eq.$userId"
            val conn = createConnection(urlString)
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("chat_status", chatStatus)
                put("current_chat_partner_id", partnerId)
            }
            
            Log.d(TAG, "📤 [CHAT_STATUS_UPDATE] Sending body: $body")
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            val responseCode = conn.responseCode
            val response = conn.readResponse()
            
            if (responseCode in 200..299) {
                Log.d(TAG, "✅ [CHAT_STATUS_UPDATE] Success! chat_status=$chatStatus")
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ [CHAT_STATUS_UPDATE] Failed! Code=$responseCode, Response=$response")
                Result.failure(Exception("Error $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [CHAT_STATUS_UPDATE] Exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Obtiene el estado de chat actual del usuario Y con quién está chateando.
     * Retorna 'chatting' solo si está chateando CON EL CURRENT USER.
     */
    suspend fun getChatStatus(userId: String, currentUserId: String): String = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/users?id=eq.$userId&select=chat_status,current_chat_partner_id"
            val conn = createConnection(urlString)
            conn.requestMethod = "GET"
            
            val response = conn.readResponse()
            if (conn.responseCode == 200) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val obj = jsonArray.getJSONObject(0)
                    val chatStatus = obj.optString("chat_status", "offline")
                    val chatPartnerId = obj.optString("current_chat_partner_id", null)
                    
                    // Solo retornar 'chatting' si está chateando CON NOSOTROS
                    val finalStatus = if (chatStatus == "chatting" && chatPartnerId == currentUserId) {
                        "chatting"
                    } else {
                        "offline"
                    }
                    
                    Log.d(TAG, "📊 Got chat_status for $userId: $finalStatus (raw=$chatStatus, partner=$chatPartnerId, me=$currentUserId)")
                    return@withContext finalStatus
                }
            }
            Log.d(TAG, "📊 No chat_status found for user $userId, defaulting to offline")
            "offline"
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting chat_status: ${e.message}")
            "offline"
        }
    }
    
    /**
     * Suscribe al estado de chat del partner (chatting/offline).
     * Independiente del campo 'status'.
     * Retorna 'chatting' solo si el partner está chateando CON NOSOTROS.
     * Incluye polling de respaldo cada 5 segundos para garantizar actualizaciones.
     */
    fun subscribeToChatStatus(partnerId: String, currentUserId: String): Flow<String> = callbackFlow {
        Log.d(TAG, "👤 Subscribing to chat_status for partner: $partnerId (currentUser=$currentUserId)")
        val channel = io.orabel.orabelandroid.auth.SupabaseClient.client.realtime.channel("chat_status:$partnerId:${System.currentTimeMillis()}")
        
        var lastStatus = "offline"
        
        // Job 1: Realtime subscription (prioritario)
        val realtimeJob = launch {
            channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "users"
            }.collect { action ->
                val jsonRecord = action.record as? JsonObject ?: return@collect
                
                // Client-side filtering
                val userId = jsonRecord["id"]?.jsonPrimitive?.content
                if (userId != partnerId) return@collect
                
                val chatStatus = jsonRecord["chat_status"]?.jsonPrimitive?.content ?: "offline"
                val chatPartnerId = jsonRecord["current_chat_partner_id"]?.jsonPrimitive?.content
                
                // Solo emitir 'chatting' si está chateando CON NOSOTROS
                val finalStatus = if (chatStatus == "chatting" && chatPartnerId == currentUserId) {
                    "chatting"
                } else {
                    "offline"
                }
                
                if (finalStatus != lastStatus) {
                    lastStatus = finalStatus
                    Log.d(TAG, "✅ [REALTIME] chat_status update for $partnerId: $finalStatus (raw=$chatStatus, partner=$chatPartnerId)")
                    trySend(finalStatus)
                }
            }
        }
        
        // Job 2: Polling de respaldo cada 5 segundos
        val pollingJob = launch {
            kotlinx.coroutines.delay(3000) // Esperar 3s antes de empezar a hacer polling
            while (true) {
                try {
                    val currentStatus = getChatStatus(partnerId, currentUserId)
                    if (currentStatus != lastStatus) {
                        lastStatus = currentStatus
                        Log.d(TAG, "🔄 [POLLING] chat_status update for $partnerId: $currentStatus")
                        trySend(currentStatus)
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "⏹️ [POLLING] Polling job cancelled for $partnerId")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [POLLING] Error checking chat_status: ${e.message}")
                }
                kotlinx.coroutines.delay(5000) // Polling cada 5 segundos
            }
        }
        
        channel.subscribe()
        Log.d(TAG, "✅ Subscribed to chat_status for: $partnerId (with polling backup)")
        
        awaitClose {
            Log.d(TAG, "🔴 Unsubscribing from chat_status: $partnerId")
            realtimeJob.cancel()
            pollingJob.cancel()
            launch { channel.unsubscribe() }
        }
    }
    
    private fun parseMessageFromRealtimeRecord(jsonRecord: JsonObject): MessageDTO {
        Log.d(TAG, "   🔍 Parsing JsonObject with keys: ${jsonRecord.keys}")
        
        val createdAtStr = jsonRecord["created_at"]?.jsonPrimitive?.content ?: ""
        val createdAt = try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(createdAtStr) ?: Date()
        } catch (e: Exception) {
            Date()
        }
        
        val deliveredAtStr = jsonRecord["delivered_at"]?.jsonPrimitive?.content
        val deliveredAt = deliveredAtStr?.let {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(it)?.time
            } catch (e: Exception) {
                null
            }
        }
        
        val seenAtStr = jsonRecord["seen_at"]?.jsonPrimitive?.content
        val seenAt = seenAtStr?.let {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(it)?.time
            } catch (e: Exception) {
                null
            }
        }
        
        // Extract IDs using JsonObject API
        val senderIdStr = jsonRecord["sender_id"]?.jsonPrimitive?.content 
            ?: jsonRecord["senderId"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("sender_id not found in record")
        val receiverIdStr = jsonRecord["receiver_id"]?.jsonPrimitive?.content 
            ?: jsonRecord["receiverId"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("receiver_id not found in record")
        
        return MessageDTO(
            id = jsonRecord["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            senderId = UUID.fromString(senderIdStr),
            receiverId = UUID.fromString(receiverIdStr),
            content = jsonRecord["content"]?.jsonPrimitive?.content ?: "",
            isTemporary = jsonRecord["is_temporary"]?.jsonPrimitive?.content?.toBoolean() ?: true,
            deliveredAt = deliveredAt,
            seenAt = seenAt,
            createdAt = createdAt,
            status = jsonRecord["status"]?.jsonPrimitive?.content,
            replyToId = jsonRecord["reply_to_id"]?.jsonPrimitive?.content?.toLongOrNull(),
            replyContextContent = jsonRecord["reply_context_content"]?.jsonPrimitive?.content,
            replyContextSenderUsername = jsonRecord["reply_context_sender_username"]?.jsonPrimitive?.content,
            sender = null,  // Se carga por separado en fetchMessages
            receiver = null,  // Se carga por separado en fetchMessages
            replyToMessage = null  // Se carga por separado si existe reply_to_id
        )
    }
    
    // ========== GESTIÓN DE ACTIVIDAD Y HEARTBEAT ==========
    
    /**
     * Actualiza el estado de actividad del usuario (is_active).
     * Usado para indicar si el usuario tiene la app abierta o no.
     */
    suspend fun updateUserActiveStatus(isActive: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId() ?: return@withContext Result.failure(Exception("No user"))
        Log.d(TAG, "📡 Setting is_active=$isActive for user $userId")
        try {
            val urlString = "$SUPABASE_URL/rest/v1/users?id=eq.$userId"
            val conn = createConnection(urlString)
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("is_active", isActive)
                put("last_heartbeat", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
                if (!isActive) {
                    put("last_seen", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date()))
                }
            }
            
            Log.d(TAG, "📤 Sending: ${body.toString()}")
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            val responseCode = conn.responseCode
            val response = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }
            
            if (responseCode in 200..299) {
                Log.d(TAG, "✅ is_active=$isActive updated. Response: $response")
                Result.success(Unit)
            } else {
                val error = "Error updating is_active: $responseCode - $response"
                Log.e(TAG, "❌ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception updating is_active: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Envía un heartbeat para mantener al usuario como activo.
     * Debe llamarse cada 30 segundos mientras la app está activa.
     */
    suspend fun sendHeartbeat(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "💓 Sending heartbeat...")
            val urlString = "$SUPABASE_URL/rest/v1/rpc/update_heartbeat"
            val conn = createConnection(urlString)
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")
            conn.doOutput = true
            
            // RPC sin parámetros
            OutputStreamWriter(conn.outputStream).use { it.write("{}") }
            
            val responseCode = conn.responseCode
            val response = if (responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
            }
            
            if (responseCode in 200..299) {
                Log.d(TAG, "💓 Heartbeat OK: $response")
                Result.success(Unit)
            } else {
                val error = "Heartbeat error $responseCode: $response"
                Log.e(TAG, "❌ $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Heartbeat exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Marca un mensaje como entregado.
     */
    suspend fun markMessageAsDelivered(messageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlString = "$SUPABASE_URL/rest/v1/direct_messages?id=eq.$messageId"
            val conn = createConnection(urlString)
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val body = JSONObject().apply {
                put("status", "delivered")
                put("delivered_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            
            if (conn.responseCode in 200..299) {
                Log.d(TAG, "✅ Message $messageId marked as delivered")
                
                // Actualizar en local storage
                updateLocalMessageStatus(messageId, "delivered", System.currentTimeMillis(), null)
                
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ Error marking message as delivered: ${conn.responseCode}")
                Result.failure(Exception("Error marking message as delivered"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception marking message as delivered: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Marca un mensaje como leído (read) en el servidor.
     * Implementa patrón efímero: marca como read, guarda localmente y ELIMINA del servidor.
     */
    suspend fun markMessageAsRead(messageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Actualizar en local storage PRIMERO (para evitar pérdida)
            updateLocalMessageStatus(messageId, "read", null, System.currentTimeMillis())
            Log.d(TAG, "💾 [EPHEMERAL] Saved read status to local storage FIRST")
            
            // 2. Marcar como leído en servidor
            val patchUrl = "$SUPABASE_URL/rest/v1/direct_messages?id=eq.$messageId"
            val patchConn = createConnection(patchUrl)
            patchConn.requestMethod = "PATCH"
            patchConn.setRequestProperty("Content-Type", "application/json")
            patchConn.doOutput = true
            
            val body = JSONObject().apply {
                put("status", "read")
                put("seen_at", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date()))
            }
            
            OutputStreamWriter(patchConn.outputStream).use { it.write(body.toString()) }
            
            if (patchConn.responseCode in 200..299) {
                Log.d(TAG, "✅ [EPHEMERAL] Marked message $messageId as read")
                
                // 3. ELIMINAR del servidor (patrón efímero)
                val deleteUrl = "$SUPABASE_URL/rest/v1/direct_messages?id=eq.$messageId"
                val deleteConn = createConnection(deleteUrl)
                deleteConn.requestMethod = "DELETE"
                
                if (deleteConn.responseCode in 200..299) {
                    Log.d(TAG, "🗑️ [EPHEMERAL] Deleted message $messageId from server after marking as read")
                } else {
                    Log.w(TAG, "⚠️ [EPHEMERAL] Could not delete message from server: ${deleteConn.responseCode}")
                }
                
                Result.success(Unit)
            } else {
                Log.e(TAG, "❌ Error marking message as read: ${patchConn.responseCode}")
                Result.failure(Exception("Error marking message as read"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception marking message as read: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Actualiza el status de un mensaje en local storage.
     * Esta función es pública para permitir actualizaciones desde la UI cuando se reciben eventos de Realtime.
     */
    fun updateLocalMessageStatus(
        messageId: Long,
        newStatus: String,
        deliveredAt: Long?,
        seenAt: Long?
    ) {
        try {
            val query = localMessagesBox.query().equal(LocalMessageEntity_.messageId, messageId).build()
            val existingMessages = query.find()
            
            if (existingMessages.isNotEmpty()) {
                val localMessage = existingMessages.first()
                localMessage.status = newStatus
                if (deliveredAt != null) localMessage.deliveredAt = deliveredAt
                if (seenAt != null) localMessage.seenAt = seenAt
                localMessagesBox.put(localMessage)
                Log.d(TAG, "💾 Updated message $messageId in local storage: status=$newStatus")

                // Si había un pending (por carrera), ya está resuelto.
                pendingStatusUpdates.remove(messageId)
            } else {
                // Aún no existe localmente (race): guardar para aplicarlo cuando se inserte.
                pendingStatusUpdates[messageId] = PendingStatusUpdate(
                    status = newStatus,
                    deliveredAt = deliveredAt,
                    seenAt = seenAt
                )
                Log.w(TAG, "⚠️ Message $messageId not found in local storage; cached pending status=$newStatus")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating message status in local: ${e.message}")
        }
    }
}
