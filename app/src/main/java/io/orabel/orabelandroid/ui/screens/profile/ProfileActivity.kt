package io.orabel.orabelandroid.ui.screens.profile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.MainActivity
import io.orabel.orabelandroid.ui.screens.search.SearchActivity
import io.orabel.orabelandroid.ui.screens.calendar.CalendarActivity
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.auth.LoginActivity
import org.koin.android.ext.android.inject

class ProfileActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            val viewModel: ProfileViewModel = viewModel()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                ModernProfileScreen(
                    viewModel = viewModel,
                    onNavigateToHome = { openMainActivity() },
                    onNavigateToSearch = { openSearchActivity() },
                    onNavigateToChat = { openChatActivity() },
                    onNavigateToCalendar = { openCalendarActivity() },
                    onSignOut = { openLoginActivity() },
                    lastNavigationIndex = 4
                )
            }
        }
    }
    
    private fun openMainActivity() {
        orabelPreferences.setLastNavigationIndex(2) // Inicio ahora es índice 2
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openSearchActivity() {
        orabelPreferences.setLastNavigationIndex(0) // Búsqueda ahora es índice 0
        val intent = Intent(this, SearchActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openChatActivity() {
        orabelPreferences.setLastNavigationIndex(1) // Chat ahora es índice 1
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openCalendarActivity() {
        orabelPreferences.setLastNavigationIndex(3)
        val intent = Intent(this, CalendarActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openLoginActivity() {
        // Limpiar SharedPreferences del perfil
        val sharedPreferences = getSharedPreferences("user_profile", MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
        
        // Redirigir a LoginActivity
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
