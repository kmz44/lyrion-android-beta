package io.orabel.orabelandroid.ui.screens.calendar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.orabel.orabelandroid.data.OrabelPreferences
import io.orabel.orabelandroid.data.repository.CalendarEvent
import io.orabel.orabelandroid.ui.components.*
import io.orabel.orabelandroid.ui.theme.*
import io.orabel.orabelandroid.ui.screens.main.ModernMainActivity
import io.orabel.orabelandroid.ui.screens.search.SearchActivity
import io.orabel.orabelandroid.ui.screens.chat.ChatActivity
import io.orabel.orabelandroid.ui.screens.profile.ProfileActivity
import io.orabel.orabelandroid.ui.components.ModernBottomNavigation
import io.orabel.orabelandroid.ui.components.swipeableNavigation
import io.orabel.orabelandroid.ui.viewmodel.CalendarState
import io.orabel.orabelandroid.ui.viewmodel.CalendarView
import io.orabel.orabelandroid.ui.viewmodel.CalendarViewModel
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.*

class CalendarActivity : ComponentActivity() {
    
    private val orabelPreferences by inject<OrabelPreferences>()
    private lateinit var viewModel: CalendarViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicializar ViewModel con contexto
        viewModel = CalendarViewModel(this)

        setContent {
            val isDarkTheme by orabelPreferences.isDarkThemeFlow.collectAsState()
            val calendarState by viewModel.calendarState.collectAsState()
            val currentMonth by viewModel.currentMonth.collectAsState()
            val currentView by viewModel.currentView.collectAsState()
            
            ModernOrabelTheme(
                darkTheme = isDarkTheme
            ) {
                CalendarScreen(
                    calendarState = calendarState,
                    currentMonth = currentMonth,
                    currentView = currentView,
                    onNavigateToHome = { openMainActivity() },
                    onNavigateToSearch = { openSearchActivity() },
                    onNavigateToChat = { openChatActivity() },
                    onNavigateToProfile = { openProfileActivity() },
                    onPreviousMonth = { viewModel.goToPreviousMonth() },
                    onNextMonth = { viewModel.goToNextMonth() },
                    onChangeView = { view -> viewModel.changeView(view) },
                    onRefresh = { viewModel.loadEventsForCurrentMonth() },
                    lastNavigationIndex = 3
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
    
    private fun openProfileActivity() {
        orabelPreferences.setLastNavigationIndex(4)
        val intent = Intent(this, ProfileActivity::class.java)
        startActivity(intent)
        finish()
    }
}

@Composable
fun CalendarScreen(
    calendarState: CalendarState,
    currentMonth: Calendar,
    currentView: CalendarView,
    onNavigateToHome: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onChangeView: (CalendarView) -> Unit,
    onRefresh: () -> Unit,
    lastNavigationIndex: Int
) {
    var selectedBottomNav by remember { mutableStateOf(3) }
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    var showClassroomDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar con navegación de mes
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Column {
                // Título y acciones
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Calendario",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón de Classroom
                        IconButton(onClick = { showClassroomDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = "Google Classroom",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        
                        // Botón de actualizar
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualizar",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Botón de agregar evento
                        IconButton(onClick = { /* TODO: Abrir diálogo de nuevo evento */ }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Agregar evento",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                // Navegación de mes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Mes anterior",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Text(
                        text = SimpleDateFormat("MMMM yyyy", Locale("es", "ES"))
                            .format(currentMonth.time),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    IconButton(onClick = onNextMonth) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = "Mes siguiente",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Selector de vista (Mes/Semana/Día) - Próximamente
                /* 
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CalendarViewButton("Mes", currentView == CalendarView.MONTH) {
                        onChangeView(CalendarView.MONTH)
                    }
                    CalendarViewButton("Semana", currentView == CalendarView.WEEK) {
                        onChangeView(CalendarView.WEEK)
                    }
                    CalendarViewButton("Día", currentView == CalendarView.DAY) {
                        onChangeView(CalendarView.DAY)
                    }
                }
                */
            }
        }
        
        // Contenido del calendario
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .swipeableNavigation(
                    currentIndex = 3, // Historial está en índice 3
                    onSwipeLeft = { onNavigateToProfile() }, // Ir a Perfil (índice 4)
                    onSwipeRight = { onNavigateToHome() } // Ir a Inicio (índice 2)
                )
        ) {
            when (calendarState) {
                is CalendarState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Cargando eventos de Google Calendar...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                
                is CalendarState.Success -> {
                    // Mostrar vista de calendario mensual con eventos
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        // Vista de calendario mensual estilo Google Calendar
                        CalendarMonthView(
                            currentMonth = currentMonth,
                            events = calendarState.events,
                            onDayClick = { date ->
                                selectedDate = date
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                is CalendarState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Error al cargar eventos",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = calendarState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reintentar")
                            }
                        }
                    }
                }
            }
        }
        
        // Diálogo de eventos del día seleccionado
        selectedDate?.let { date ->
            val eventsForDay = when (calendarState) {
                is CalendarState.Success -> {
                    // Filtrar eventos del día seleccionado
                    calendarState.events.filter { event ->
                        val eventCal = Calendar.getInstance()
                        eventCal.timeInMillis = event.startTime
                        val selectedCal = Calendar.getInstance()
                        selectedCal.time = date
                        
                        eventCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                        eventCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
                    }
                }
                else -> emptyList()
            }
            
            DayEventsDialog(
                date = date,
                events = eventsForDay,
                onDismiss = { selectedDate = null },
                onEventClick = { event ->
                    // TODO: Abrir detalles del evento
                    selectedDate = null
                }
            )
        }
        
        // Bottom Navigation
        ModernBottomNavigation(
            selectedItem = selectedBottomNav,
            onItemSelected = { index ->
                selectedBottomNav = index
                when (index) {
                    0 -> onNavigateToSearch() // Búsqueda
                    1 -> onNavigateToChat() // Chat
                    2 -> onNavigateToHome() // Inicio
                    3 -> { /* Historial - ya estamos aquí */ }
                    4 -> onNavigateToProfile() // Perfil
                }
            }
        )
    }
    
    // Diálogo de Classroom
    if (showClassroomDialog) {
        AlertDialog(
            onDismissRequest = { showClassroomDialog = false },
            confirmButton = {
                TextButton(onClick = { showClassroomDialog = false }) {
                    Text("Cerrar")
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text("Google Classroom")
                }
            },
            text = {
                Text(
                    "Para ver tus tareas de Google Classroom, pregúntale a Lyrion:\n\n" +
                    "• \"¿Qué tareas tengo pendientes?\"\n" +
                    "• \"Muéstrame mis deberes de Classroom\"\n" +
                    "• \"¿Tengo trabajos por entregar?\"\n\n" +
                    "Lyrion consultará automáticamente tus cursos y tareas actuales.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        )
    }
}

@Composable
fun EventCard(event: CalendarEvent) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale("es", "ES"))
    val dayFormat = SimpleDateFormat("EEE, d MMM", Locale("es", "ES"))
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: Abrir detalles del evento */ },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Indicador de color a la izquierda
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(60.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Título del evento
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Hora
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (event.isAllDay) {
                            dayFormat.format(Date(event.startTime)) + " (Todo el día)"
                        } else {
                            "${dateFormat.format(Date(event.startTime))} - ${dateFormat.format(Date(event.endTime))}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Ubicación (si existe)
                if (event.location.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = event.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Descripción (si existe)
                if (event.description.isNotEmpty()) {
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 2
                    )
                }
            }
        }
    }
}
