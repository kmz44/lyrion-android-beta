package io.orabel.orabelandroid.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.orabel.orabelandroid.data.repository.CalendarEvent
import java.text.SimpleDateFormat
import java.util.*

/**
 * Vista de calendario mensual estilo Google Calendar
 * Muestra una cuadrícula de días con eventos
 */
@Composable
fun CalendarMonthView(
    currentMonth: Calendar,
    events: List<CalendarEvent>,
    onDayClick: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    val daysInMonth = getDaysInMonth(currentMonth)
    val eventsGroupedByDay = groupEventsByDay(events)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Encabezados de días de la semana
        WeekDaysHeader()

        // Grid de días del mes
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            daysInMonth.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    week.forEach { dayInfo ->
                        DayCell(
                            dayInfo = dayInfo,
                            events = eventsGroupedByDay[dayInfo.date] ?: emptyList(),
                            onDayClick = onDayClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Rellenar celdas vacías al final de la última semana
                    repeat(7 - week.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Encabezado con los días de la semana
 */
@Composable
private fun WeekDaysHeader() {
    val weekDays = listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        weekDays.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Celda individual de día en el calendario
 */
@Composable
private fun DayCell(
    dayInfo: DayInfo,
    events: List<CalendarEvent>,
    onDayClick: (Date) -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = isSameDay(dayInfo.date, Date())
    val hasEvents = events.isNotEmpty()
    
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isToday) 2.dp else 0.5.dp,
                color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onDayClick(dayInfo.date) }
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Número del día
            Text(
                text = dayInfo.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp
                ),
                color = when {
                    !dayInfo.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )
            
            // Indicadores de eventos
            if (hasEvents && dayInfo.isCurrentMonth) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Mostrar hasta 3 eventos como puntos
                    val eventsToShow = events.take(3)
                    eventsToShow.forEach { event ->
                        EventIndicator(event)
                    }
                    
                    // Indicador de más eventos
                    if (events.size > 3) {
                        Text(
                            text = "+${events.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Indicador visual de evento (punto de color o mini-tarjeta)
 */
@Composable
private fun EventIndicator(event: CalendarEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = event.title,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Diálogo para mostrar eventos de un día específico
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEventsDialog(
    date: Date,
    events: List<CalendarEvent>,
    onDismiss: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit
) {
    val dateFormat = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
    val timeFormat = SimpleDateFormat("HH:mm", Locale("es", "ES"))
    
    AlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Título con fecha
                Text(
                    text = dateFormat.format(date),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Lista de eventos del día
                if (events.isEmpty()) {
                    Text(
                        text = "No hay eventos este día",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(events) { event ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEventClick(event) },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Indicador de color
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(50.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(2.dp)
                                            )
                                    )
                                    
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = event.title,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        Text(
                                            text = if (event.isAllDay) {
                                                "Todo el día"
                                            } else {
                                                "${timeFormat.format(Date(event.startTime))} - ${timeFormat.format(Date(event.endTime))}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        
                                        if (event.location.isNotEmpty()) {
                                            Text(
                                                text = event.location,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Botón cerrar
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}

/**
 * Información de un día en el calendario
 */
private data class DayInfo(
    val date: Date,
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean
)

/**
 * Genera lista de días para mostrar en el calendario mensual
 * Incluye días del mes anterior y siguiente para completar semanas
 */
private fun getDaysInMonth(month: Calendar): List<DayInfo> {
    val days = mutableListOf<DayInfo>()
    
    // Clonar el calendario para no modificar el original
    val cal = month.clone() as Calendar
    
    // Ir al primer día del mes
    cal.set(Calendar.DAY_OF_MONTH, 1)
    
    // Obtener el día de la semana del primer día (1 = Domingo, 7 = Sábado)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    
    // Agregar días del mes anterior para completar la primera semana
    val prevMonthCal = cal.clone() as Calendar
    prevMonthCal.add(Calendar.MONTH, -1)
    val daysInPrevMonth = prevMonthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val daysFromPrevMonth = firstDayOfWeek - 1
    
    for (i in daysFromPrevMonth - 1 downTo 0) {
        prevMonthCal.set(Calendar.DAY_OF_MONTH, daysInPrevMonth - i)
        days.add(
            DayInfo(
                date = prevMonthCal.time,
                dayOfMonth = daysInPrevMonth - i,
                isCurrentMonth = false
            )
        )
    }
    
    // Agregar días del mes actual
    val daysInCurrentMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    for (day in 1..daysInCurrentMonth) {
        cal.set(Calendar.DAY_OF_MONTH, day)
        days.add(
            DayInfo(
                date = cal.time,
                dayOfMonth = day,
                isCurrentMonth = true
            )
        )
    }
    
    // Agregar días del mes siguiente para completar la última semana
    val nextMonthCal = cal.clone() as Calendar
    nextMonthCal.add(Calendar.MONTH, 1)
    val remainingDays = 7 - (days.size % 7)
    if (remainingDays < 7) {
        for (day in 1..remainingDays) {
            nextMonthCal.set(Calendar.DAY_OF_MONTH, day)
            days.add(
                DayInfo(
                    date = nextMonthCal.time,
                    dayOfMonth = day,
                    isCurrentMonth = false
                )
            )
        }
    }
    
    return days
}

/**
 * Agrupa eventos por día para fácil acceso
 */
private fun groupEventsByDay(events: List<CalendarEvent>): Map<Date, List<CalendarEvent>> {
    return events.groupBy { event ->
        // Normalizar fecha a medianoche para comparación
        val cal = Calendar.getInstance()
        cal.timeInMillis = event.startTime
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.time
    }
}

/**
 * Verifica si dos fechas son el mismo día
 */
private fun isSameDay(date1: Date, date2: Date): Boolean {
    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance()
    cal1.time = date1
    cal2.time = date2
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
