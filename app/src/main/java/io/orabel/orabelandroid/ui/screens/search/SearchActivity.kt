package io.orabel.orabelandroid.ui.screens.search

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.screens.calendar.CalendarActivity
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.screens.profile.ProfileActivity
import io.orabel.orabelandroid.health.HealthReportShareActivity
import io.orabel.orabelandroid.ui.screens.study.StudyKnowledgeActivity
import io.orabel.orabelandroid.ui.screens.personal.PersonalContextActivity
import org.koin.android.ext.android.inject

class SearchActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                SearchNavigation(
                    onNavigateToHome = { openMainActivity() },
                    onNavigateToCalendar = { openCalendarActivity() },
                    onNavigateToChat = { openChatActivity() },
                    onNavigateToProfile = { openProfileActivity() },
                    onOpenHealthReport = { openHealthReportShare() },
                    onOpenStudyKnowledge = { openStudyKnowledge() },
                    onOpenPersonalContext = { openPersonalContext() }
                )
            }
        }
    }
    
    private fun openMainActivity() {
        orabelPreferences.setLastNavigationIndex(2) // Inicio ahora es índice 2
        val intent = Intent(this, ModernMainActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openCalendarActivity() {
        orabelPreferences.setLastNavigationIndex(3)
        val intent = Intent(this, CalendarActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openChatActivity() {
        orabelPreferences.setLastNavigationIndex(1) // Chat ahora es índice 1
        val intent = Intent(this, ChatActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openProfileActivity() {
        orabelPreferences.setLastNavigationIndex(4)
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
        finish()
    }
    
    private fun openHealthReportShare() {
        try {
            android.util.Log.d("SearchActivity", "🚀 Intentando abrir HealthReportShareActivity...")
            val intent = Intent()
            intent.setClassName(this, "io.orabel.orabelandroid.health.HealthReportShareActivity")
            android.util.Log.d("SearchActivity", "✅ Intent creado correctamente")
            startActivity(intent)
            android.util.Log.d("SearchActivity", "✅ startActivity llamado")
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "❌ Error abriendo HealthReportShareActivity: ${e.message}", e)
        }
    }
    
    private fun openStudyKnowledge() {
        try {
            val intent = Intent(this, StudyKnowledgeActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "❌ Error abriendo StudyKnowledgeActivity: ${e.message}", e)
        }
    }
    
    private fun openPersonalContext() {
        try {
            val intent = Intent(this, PersonalContextActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "❌ Error abriendo PersonalContextActivity: ${e.message}", e)
        }
    }
}

/**
 * Navegación interna de la sección de búsqueda/social.
 */
@Composable
fun SearchNavigation(
    onNavigateToHome: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onOpenHealthReport: () -> Unit,
    onOpenStudyKnowledge: () -> Unit,
    onOpenPersonalContext: () -> Unit
) {
    val navController = rememberNavController()
    var selectedBottomNav by remember { mutableStateOf(0) } // Búsqueda seleccionada (ahora índice 0)
    
    // Estado para almacenar el usuario seleccionado para navegación
    var selectedUser by remember { mutableStateOf<io.orabel.orabelandroid.data.social.ProfileDTO?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Contenido principal con navegación y soporte para swipe horizontal
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .swipeableNavigation(
                    currentIndex = 0, // Búsqueda está en índice 0
                    onSwipeLeft = { onNavigateToChat() }, // Ir a Chat (índice 1)
                    onSwipeRight = { } // No hay pantalla anterior
                )
        ) {
            NavHost(
                navController = navController,
                startDestination = "social_feed"
            ) {
                composable("social_feed") {
                    SocialFeedScreen(
                        onNavigateToHome = onNavigateToHome,
                        onNavigateToCalendar = onNavigateToCalendar,
                        onNavigateToChat = onNavigateToChat,
                        onNavigateToProfile = onNavigateToProfile,
                        onOpenMisDatos = { navController.navigate("mis_datos") },
                        onOpenUserProfile = { user ->
                            selectedUser = user
                            navController.navigate("user_profile")
                        },
                        // onOpenFriendsDetail removed as it is handled internally
                        onOpenDirectChat = { user ->
                            selectedUser = user
                            navController.navigate("direct_chat")
                        },
                        selectedBottomNav = selectedBottomNav,
                        onBottomNavSelected = { index ->
                            selectedBottomNav = index
                            when (index) {
                                0 -> { /* Ya estamos en búsqueda */ }
                                1 -> onNavigateToChat()
                                2 -> onNavigateToHome()
                                3 -> onNavigateToCalendar()
                                4 -> onNavigateToProfile()
                            }
                        }
                    )
                }
                
                composable("mis_datos") {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val repository = remember { io.orabel.orabelandroid.data.social.SocialRepository.getInstance(context) }
                    io.orabel.orabelandroid.ui.screens.search.MisDatosScreen(
                        repository = repository,
                        onBack = { navController.popBackStack() }
                    )
                }
                
                composable("user_profile") {
                    selectedUser?.let { user ->
                        UserProfileScreen(
                            user = user,
                            onBack = { navController.popBackStack() },
                            onMessageClick = {
                                navController.navigate("direct_chat")
                            }
                        )
                    }
                }
                
                composable("direct_chat") {
                    selectedUser?.let { user ->
                        DirectChatScreen(
                            targetUser = user,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                
                // friends_detail composable removed
            }
        }
        
        // Navegación inferior
        ModernBottomNavigation(
            selectedItem = selectedBottomNav,
            onItemSelected = { index ->
                selectedBottomNav = index
                when (index) {
                    0 -> { /* Ya estamos en búsqueda */ }
                    1 -> onNavigateToChat()
                    2 -> onNavigateToHome()
                    3 -> onNavigateToCalendar()
                    4 -> onNavigateToProfile()
                }
            }
        )
    }
}

