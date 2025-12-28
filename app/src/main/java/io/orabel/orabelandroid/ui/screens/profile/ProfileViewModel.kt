package io.orabel.orabelandroid.ui.screens.profile

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.orabel.orabelandroid.auth.SupabaseClient
import io.orabel.orabelandroid.auth.OfflineSessionManager
import io.orabel.orabelandroid.data.OrabelPreferences
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

data class UserProfile(
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val userPhoto: String = "",
    val isAuthenticated: Boolean = false
)

class ProfileViewModel : ViewModel(), KoinComponent {
    
    private val orabelPreferences: OrabelPreferences by inject()
    private val context: Context by inject()
    
    // 🔥 NUEVO: Gestor de sesión offline
    private val offlineSessionManager by lazy { OfflineSessionManager(context) }
    
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadUserProfile()
    }
    
    fun loadUserProfile() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                
                // Intentar cargar desde sesión online
                val session = SupabaseClient.client.auth.currentSessionOrNull()
                
                if (session != null) {
                    // Sesión online disponible
                    val user = session.user
                    
                    // Obtener datos del usuario
                    val userName = user?.userMetadata?.get("full_name")?.toString()?.trim('"')
                        ?: user?.userMetadata?.get("name")?.toString()?.trim('"')
                        ?: "Usuario"
                    
                    val userEmail = user?.email?.trim('"') ?: ""
                    
                    val userPhoto = user?.userMetadata?.get("avatar_url")?.toString()?.trim('"')
                        ?: user?.userMetadata?.get("picture")?.toString()?.trim('"')
                        ?: ""
                    
                    _userProfile.value = UserProfile(
                        userId = user?.id ?: "",
                        userName = userName,
                        userEmail = userEmail,
                        userPhoto = userPhoto,
                        isAuthenticated = true
                    )
                    
                    Log.d("ProfileViewModel", "User loaded from online session: $userName ($userEmail)")
                } else {
                    // No hay sesión online, intentar cargar desde sesión offline
                    val offlineSession = offlineSessionManager.getOfflineSessionData()
                    if (offlineSession != null) {
                        _userProfile.value = UserProfile(
                            userId = offlineSession.userId,
                            userName = offlineSession.displayName ?: "Usuario",
                            userEmail = offlineSession.email,
                            userPhoto = offlineSession.avatarUrl ?: "",
                            isAuthenticated = true
                        )
                        Log.d("ProfileViewModel", "📴 User loaded from offline session: ${offlineSession.email}")
                    } else {
                        Log.w("ProfileViewModel", "No active session found (online or offline)")
                        _userProfile.value = UserProfile(isAuthenticated = false)
                    }
                }
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading user profile from online", e)
                
                // Fallback a sesión offline
                try {
                    val offlineSession = offlineSessionManager.getOfflineSessionData()
                    if (offlineSession != null) {
                        _userProfile.value = UserProfile(
                            userId = offlineSession.userId,
                            userName = offlineSession.displayName ?: "Usuario",
                            userEmail = offlineSession.email,
                            userPhoto = offlineSession.avatarUrl ?: "",
                            isAuthenticated = true
                        )
                        Log.d("ProfileViewModel", "📴 User loaded from offline session (fallback): ${offlineSession.email}")
                    } else {
                        _userProfile.value = UserProfile(isAuthenticated = false)
                    }
                } catch (offlineError: Exception) {
                    Log.e("ProfileViewModel", "Error loading offline session", offlineError)
                    _userProfile.value = UserProfile(isAuthenticated = false)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun signOut(onSignOutComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                // Cerrar sesión online
                SupabaseClient.client.auth.signOut()
                
                // 🔥 NUEVO: Borrar sesión offline también
                offlineSessionManager.clearOfflineSession()
                
                _userProfile.value = UserProfile(isAuthenticated = false)
                Log.d("ProfileViewModel", "✅ User signed out successfully (online + offline)")
                onSignOutComplete()
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error signing out", e)
                
                // Aunque falle el signOut online, borrar sesión offline de todos modos
                try {
                    offlineSessionManager.clearOfflineSession()
                    Log.d("ProfileViewModel", "✅ Offline session cleared despite online error")
                    onSignOutComplete()
                } catch (offlineError: Exception) {
                    Log.e("ProfileViewModel", "Error clearing offline session", offlineError)
                }
            }
        }
    }
    
    /**
     * Establece el modo de tema
     */
    fun setThemeMode(mode: String) {
        orabelPreferences.setThemeMode(mode)
        Log.d("ProfileViewModel", "Theme mode set to: $mode")
    }
}
