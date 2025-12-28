package io.orabel.orabelandroid.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * Entidad ObjectBox para cachear eventos de Google Calendar localmente
 * Permite acceso offline al calendario del mes actual
 */
@Entity
data class CachedCalendarEvent(
    @Id var id: Long = 0,
    var googleEventId: String = "",  // ID del evento en Google Calendar
    var title: String = "",
    var description: String = "",
    var startTime: Long = 0L,  // Timestamp en milisegundos
    var endTime: Long = 0L,
    var location: String = "",
    var isAllDay: Boolean = false,
    var isSynced: Boolean = true,  // false si fue creado offline y necesita sincronizarse
    var lastSyncTime: Long = System.currentTimeMillis(),
    var monthYear: String = ""  // Formato "MM-YYYY" para filtrar por mes
)

/**
 * Entidad ObjectBox para eventos pendientes de sincronización
 * Cuando el usuario crea/modifica eventos sin conexión
 */
@Entity
data class PendingCalendarSync(
    @Id var id: Long = 0,
    var operationType: String = "",  // CREATE, UPDATE, DELETE
    var eventTitle: String = "",
    var eventDescription: String = "",
    var eventStartTime: Long = 0L,
    var eventEndTime: Long = 0L,
    var eventLocation: String = "",
    var eventIsAllDay: Boolean = false,
    var googleEventId: String = "",  // Solo para UPDATE y DELETE
    var createdAt: Long = System.currentTimeMillis(),
    var retryCount: Int = 0,
    var lastError: String = ""
)
