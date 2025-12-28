package io.orabel.orabelandroid.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.orabel.orabelandroid.data.repository.CalendarEvent
import io.orabel.orabelandroid.data.repository.GoogleCalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * ViewModel para la pantalla de calendario
 */
class CalendarViewModel(context: Context) : ViewModel() {

    companion object {
        private const val TAG = "CalendarViewModel"
    }

    private val repository = GoogleCalendarRepository(context)

    // Estado de los eventos del calendario
    private val _calendarState = MutableStateFlow<CalendarState>(CalendarState.Loading)
    val calendarState: StateFlow<CalendarState> = _calendarState.asStateFlow()

    // Mes actual seleccionado
    private val _currentMonth = MutableStateFlow(Calendar.getInstance())
    val currentMonth: StateFlow<Calendar> = _currentMonth.asStateFlow()

    // Vista actual (mes, semana, día)
    private val _currentView = MutableStateFlow(CalendarView.MONTH)
    val currentView: StateFlow<CalendarView> = _currentView.asStateFlow()

    init {
        loadEventsForCurrentMonth()
    }

    /**
     * Carga los eventos del mes actual (con soporte de caché)
     */
    fun loadEventsForCurrentMonth() {
        viewModelScope.launch {
            _calendarState.value = CalendarState.Loading

            try {
                val calendar = _currentMonth.value
                val startDate = getStartOfMonth(calendar)
                val endDate = getEndOfMonth(calendar)

                // Usar getEventsWithCache en lugar de getEvents
                val result = repository.getEventsWithCache(startDate, endDate)

                result.fold(
                    onSuccess = { events ->
                        _calendarState.value = CalendarState.Success(events)
                        Log.d(TAG, "Loaded ${events.size} events for current month")
                        
                        // Intentar sincronizar eventos pendientes en background
                        syncPendingEvents()
                    },
                    onFailure = { error ->
                        _calendarState.value = CalendarState.Error(
                            error.message ?: "Error desconocido al cargar eventos"
                        )
                        Log.e(TAG, "Error loading events", error)
                    }
                )
            } catch (e: Exception) {
                _calendarState.value = CalendarState.Error(e.message ?: "Error desconocido")
                Log.e(TAG, "Exception loading events", e)
            }
        }
    }

    /**
     * Sincroniza eventos pendientes con Google Calendar
     */
    private fun syncPendingEvents() {
        viewModelScope.launch {
            try {
                val result = repository.syncPendingEvents()
                result.fold(
                    onSuccess = { count ->
                        if (count > 0) {
                            Log.i(TAG, "Synced $count pending events")
                            // Recargar eventos después de sincronizar
                            loadEventsForCurrentMonth()
                        }
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Could not sync pending events: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing pending events", e)
            }
        }
    }

    /**
     * Cambia al mes anterior
     */
    fun goToPreviousMonth() {
        val newMonth = _currentMonth.value.clone() as Calendar
        newMonth.add(Calendar.MONTH, -1)
        _currentMonth.value = newMonth
        loadEventsForCurrentMonth()
    }

    /**
     * Cambia al mes siguiente
     */
    fun goToNextMonth() {
        val newMonth = _currentMonth.value.clone() as Calendar
        newMonth.add(Calendar.MONTH, 1)
        _currentMonth.value = newMonth
        loadEventsForCurrentMonth()
    }

    /**
     * Cambia a un mes específico
     */
    fun goToMonth(year: Int, month: Int) {
        val newMonth = Calendar.getInstance()
        newMonth.set(Calendar.YEAR, year)
        newMonth.set(Calendar.MONTH, month)
        _currentMonth.value = newMonth
        loadEventsForCurrentMonth()
    }

    /**
     * Cambia la vista del calendario (mes, semana, día)
     */
    fun changeView(view: CalendarView) {
        _currentView.value = view
    }

    /**
     * Crea un nuevo evento
     */
    fun createEvent(
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        location: String = "",
        isAllDay: Boolean = false
    ) {
        viewModelScope.launch {
            val result = repository.createEvent(
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                location = location,
                isAllDay = isAllDay
            )

            result.fold(
                onSuccess = {
                    Log.d(TAG, "Event created successfully: $title")
                    // Recargar eventos
                    loadEventsForCurrentMonth()
                },
                onFailure = { error ->
                    Log.e(TAG, "Error creating event", error)
                    _calendarState.value = CalendarState.Error(
                        "Error al crear evento: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Actualiza un evento existente
     */
    fun updateEvent(
        eventId: String,
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        location: String = "",
        isAllDay: Boolean = false
    ) {
        viewModelScope.launch {
            val result = repository.updateEvent(
                eventId = eventId,
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                location = location,
                isAllDay = isAllDay
            )

            result.fold(
                onSuccess = {
                    Log.d(TAG, "Event updated successfully: $title")
                    // Recargar eventos
                    loadEventsForCurrentMonth()
                },
                onFailure = { error ->
                    Log.e(TAG, "Error updating event", error)
                    _calendarState.value = CalendarState.Error(
                        "Error al actualizar evento: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Elimina un evento
     */
    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            val result = repository.deleteEvent(eventId)

            result.fold(
                onSuccess = {
                    Log.d(TAG, "Event deleted successfully: $eventId")
                    // Recargar eventos
                    loadEventsForCurrentMonth()
                },
                onFailure = { error ->
                    Log.e(TAG, "Error deleting event", error)
                    _calendarState.value = CalendarState.Error(
                        "Error al eliminar evento: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Obtiene el primer día del mes
     */
    private fun getStartOfMonth(calendar: Calendar): Date {
        val startCal = calendar.clone() as Calendar
        startCal.set(Calendar.DAY_OF_MONTH, 1)
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)
        return startCal.time
    }

    /**
     * Obtiene el último día del mes
     */
    private fun getEndOfMonth(calendar: Calendar): Date {
        val endCal = calendar.clone() as Calendar
        endCal.set(Calendar.DAY_OF_MONTH, endCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        endCal.set(Calendar.HOUR_OF_DAY, 23)
        endCal.set(Calendar.MINUTE, 59)
        endCal.set(Calendar.SECOND, 59)
        endCal.set(Calendar.MILLISECOND, 999)
        return endCal.time
    }
}

/**
 * Estado del calendario
 */
sealed class CalendarState {
    data object Loading : CalendarState()
    data class Success(val events: List<CalendarEvent>) : CalendarState()
    data class Error(val message: String) : CalendarState()
}

/**
 * Vista del calendario
 */
enum class CalendarView {
    MONTH,  // Vista de mes completo
    WEEK,   // Vista de semana
    DAY     // Vista de día
}
