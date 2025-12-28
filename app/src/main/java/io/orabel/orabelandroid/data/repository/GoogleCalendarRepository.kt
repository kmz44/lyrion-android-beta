package io.orabel.orabelandroid.data.repository

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import io.orabel.orabelandroid.auth.LoginActivity
import io.orabel.orabelandroid.data.*
import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repositorio para acceder a Google Calendar API usando el provider_token de Supabase OAuth
 * CON SOPORTE DE CACHÉ LOCAL para funcionamiento offline
 */
class GoogleCalendarRepository(private val context: Context) : KoinComponent {

    companion object {
        private const val TAG = "GoogleCalendarRepository"
        private const val APPLICATION_NAME = "Lyrion Calendar"
    }

    // ObjectBox para caché local
    private val boxStore: BoxStore by inject()
    private val cachedEventsBox: Box<CachedCalendarEvent> by lazy {
        boxStore.boxFor(CachedCalendarEvent::class.java)
    }
    private val pendingSyncBox: Box<PendingCalendarSync> by lazy {
        boxStore.boxFor(PendingCalendarSync::class.java)
    }

    /**
     * Crea el servicio de Google Calendar usando el token OAuth guardado
     * Intenta refrescar el token automáticamente si ha expirado
     */
    private suspend fun getCalendarService(): Calendar? {
        return try {
            var providerToken = LoginActivity.getProviderToken(context)
            
            if (providerToken.isNullOrEmpty()) {
                Log.e(TAG, "No provider token available. User needs to login with Google.")
                return null
            }

            // Crear credencial con el access token
            val credential = object : com.google.api.client.http.HttpRequestInitializer {
                override fun initialize(request: com.google.api.client.http.HttpRequest?) {
                    request?.headers?.authorization = "Bearer $providerToken"
                }
            }

            // Crear servicio de Calendar API
            Calendar.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName(APPLICATION_NAME)
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Error creating calendar service", e)
            null
        }
    }
    
    /**
     * Ejecuta una operación de Calendar API con retry automático si el token expira
     */
    private suspend fun <T> executeWithTokenRefresh(operation: suspend (Calendar) -> T): T? {
        var service = getCalendarService() ?: return null
        
        return try {
            // Intentar la operación
            operation(service)
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            if (e.statusCode == 401) {
                // Token expirado - intentar refrescar
                Log.w(TAG, "⚠️ Token expirado (401), intentando refrescar...")
                
                val newToken = LoginActivity.refreshProviderToken(context)
                if (newToken != null) {
                    // Recrear servicio con nuevo token
                    val refreshedService = getCalendarService()
                    if (refreshedService != null) {
                        // Reintentar operación con nuevo token
                        Log.d(TAG, "🔄 Reintentando operación con token refrescado")
                        try {
                            operation(refreshedService)
                        } catch (retryError: Exception) {
                            Log.e(TAG, "❌ Error en reintento después de refrescar token", retryError)
                            throw retryError
                        }
                    } else {
                        Log.e(TAG, "❌ No se pudo recrear servicio después de refrescar token")
                        throw e
                    }
                } else {
                    Log.e(TAG, "❌ No se pudo refrescar token")
                    throw e
                }
            } else {
                // Otro error HTTP
                throw e
            }
        }
    }

    /**
     * Obtiene los eventos del calendario en un rango de fechas
     */
    suspend fun getEvents(startDate: Date, endDate: Date): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        try {
            val startDateTime = DateTime(startDate)
            val endDateTime = DateTime(endDate)

            val calendarEvents = executeWithTokenRefresh { service ->
                val events = service.events().list("primary")
                    .setTimeMin(startDateTime)
                    .setTimeMax(endDateTime)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()

                events.items.map { event ->
                    CalendarEvent(
                        id = event.id ?: "",
                        title = event.summary ?: "Sin título",
                        description = event.description ?: "",
                        startTime = event.start?.dateTime?.value ?: event.start?.date?.value ?: 0L,
                        endTime = event.end?.dateTime?.value ?: event.end?.date?.value ?: 0L,
                        location = event.location ?: "",
                        isAllDay = event.start?.date != null
                    )
                }
            } ?: return@withContext Result.failure(
                Exception("Calendar service not available. Please login with Google.")
            )

            Log.d(TAG, "Successfully loaded ${calendarEvents.size} events from Google Calendar")
            Result.success(calendarEvents)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading calendar events (Ask Gemini)", e)
            Result.failure(e)
        }
    }

    /**
     * Crea un nuevo evento en Google Calendar
     */
    suspend fun createEvent(
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        location: String = "",
        isAllDay: Boolean = false,
        colorId: String = "1"
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val calendarEvent = executeWithTokenRefresh { service ->
                val event = Event().apply {
                    summary = title
                    this.description = description
                    this.location = location
                    this.colorId = colorId

                    if (isAllDay) {
                        start = EventDateTime().setDate(DateTime(Date(startTime)))
                        end = EventDateTime().setDate(DateTime(Date(endTime)))
                    } else {
                        start = EventDateTime().setDateTime(DateTime(Date(startTime)))
                        end = EventDateTime().setDateTime(DateTime(Date(endTime)))
                    }
                }

                val createdEvent = service.events().insert("primary", event).execute()

                CalendarEvent(
                    id = createdEvent.id,
                    title = createdEvent.summary ?: title,
                    description = createdEvent.description ?: description,
                    startTime = createdEvent.start?.dateTime?.value ?: createdEvent.start?.date?.value ?: startTime,
                    endTime = createdEvent.end?.dateTime?.value ?: createdEvent.end?.date?.value ?: endTime,
                    location = createdEvent.location ?: location,
                    isAllDay = isAllDay
                )
            } ?: return@withContext Result.failure(
                Exception("Calendar service not available. Please login with Google.")
            )

            Log.d(TAG, "Successfully created event: $title")
            Result.success(calendarEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating event", e)
            Result.failure(e)
        }
    }

    /**
     * Actualiza un evento existente
     */
    suspend fun updateEvent(
        eventId: String,
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        location: String = "",
        isAllDay: Boolean = false
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            val service = getCalendarService() ?: return@withContext Result.failure(
                Exception("Calendar service not available. Please login with Google.")
            )

            val event = service.events().get("primary", eventId).execute()

            event.apply {
                summary = title
                this.description = description
                this.location = location

                if (isAllDay) {
                    start = EventDateTime().setDate(DateTime(Date(startTime)))
                    end = EventDateTime().setDate(DateTime(Date(endTime)))
                } else {
                    start = EventDateTime().setDateTime(DateTime(Date(startTime)))
                    end = EventDateTime().setDateTime(DateTime(Date(endTime)))
                }
            }

            val updatedEvent = service.events().update("primary", eventId, event).execute()

            val calendarEvent = CalendarEvent(
                id = updatedEvent.id,
                title = updatedEvent.summary ?: title,
                description = updatedEvent.description ?: description,
                startTime = updatedEvent.start?.dateTime?.value ?: updatedEvent.start?.date?.value ?: startTime,
                endTime = updatedEvent.end?.dateTime?.value ?: updatedEvent.end?.date?.value ?: endTime,
                location = updatedEvent.location ?: location,
                isAllDay = isAllDay
            )

            Log.d(TAG, "Successfully updated event: $title")
            Result.success(calendarEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating event", e)
            Result.failure(e)
        }
    }

    /**
     * Elimina un evento del calendario
     */
    suspend fun deleteEvent(eventId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val service = getCalendarService() ?: return@withContext Result.failure(
                Exception("Calendar service not available. Please login with Google.")
            )

            service.events().delete("primary", eventId).execute()

            Log.d(TAG, "Successfully deleted event: $eventId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting event", e)
            Result.failure(e)
        }
    }

    // ====================== FUNCIONES DE CACHÉ LOCAL ======================

    /**
     * Obtiene eventos con soporte de caché local
     * Si no hay conexión, devuelve eventos del caché
     */
    suspend fun getEventsWithCache(startDate: Date, endDate: Date): Result<List<CalendarEvent>> = withContext(Dispatchers.IO) {
        try {
            // Intentar obtener eventos de Google Calendar
            val onlineResult = getEvents(startDate, endDate)
            
            if (onlineResult.isSuccess) {
                val events = onlineResult.getOrNull() ?: emptyList()
                
                // Guardar en caché local
                cacheEvents(events, startDate, endDate)
                
                Log.d(TAG, "Events loaded from Google Calendar and cached locally")
                return@withContext Result.success(events)
            } else {
                // Sin conexión - intentar cargar desde caché
                Log.w(TAG, "No connection to Google Calendar, loading from cache")
                val cachedEvents = getCachedEvents(startDate, endDate)
                
                if (cachedEvents.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${cachedEvents.size} events from local cache")
                    return@withContext Result.success(cachedEvents)
                } else {
                    return@withContext Result.failure(
                        Exception("Sin conexión y no hay eventos en caché")
                    )
                }
            }
        } catch (e: Exception) {
            // Error de red - intentar caché
            Log.e(TAG, "Error loading events, trying cache", e)
            val cachedEvents = getCachedEvents(startDate, endDate)
            
            if (cachedEvents.isNotEmpty()) {
                return@withContext Result.success(cachedEvents)
            } else {
                return@withContext Result.failure(e)
            }
        }
    }

    /**
     * Guarda eventos en caché local (solo del mes actual)
     */
    private fun cacheEvents(events: List<CalendarEvent>, startDate: Date, endDate: Date) {
        try {
            val monthYearFormat = SimpleDateFormat("MM-yyyy", Locale.getDefault())
            val monthYear = monthYearFormat.format(startDate)

            // Eliminar eventos antiguos de este mes
            val oldEvents = cachedEventsBox.query()
                .equal(CachedCalendarEvent_.monthYear, monthYear, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .find()
            
            // Eliminar cada evento individualmente
            oldEvents.forEach { event ->
                cachedEventsBox.remove(event)
            }

            // Guardar nuevos eventos
            events.forEach { event ->
                val cached = CachedCalendarEvent(
                    googleEventId = event.id,
                    title = event.title,
                    description = event.description,
                    startTime = event.startTime,
                    endTime = event.endTime,
                    location = event.location,
                    isAllDay = event.isAllDay,
                    isSynced = true,
                    lastSyncTime = System.currentTimeMillis(),
                    monthYear = monthYear
                )
                cachedEventsBox.put(cached)
            }

            Log.d(TAG, "Cached ${events.size} events for month $monthYear")
        } catch (e: Exception) {
            Log.e(TAG, "Error caching events", e)
        }
    }

    /**
     * Obtiene eventos del caché local
     */
    private fun getCachedEvents(startDate: Date, endDate: Date): List<CalendarEvent> {
        return try {
            val startTime = startDate.time
            val endTime = endDate.time

            val cachedEvents = cachedEventsBox.query()
                .greaterOrEqual(CachedCalendarEvent_.startTime, startTime)
                .lessOrEqual(CachedCalendarEvent_.endTime, endTime)
                .build()
                .find()

            cachedEvents.map { cached ->
                CalendarEvent(
                    id = cached.googleEventId,
                    title = cached.title,
                    description = cached.description,
                    startTime = cached.startTime,
                    endTime = cached.endTime,
                    location = cached.location,
                    isAllDay = cached.isAllDay
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached events", e)
            emptyList()
        }
    }

    /**
     * Crea evento offline (se guardará en cola de sincronización)
     */
    suspend fun createEventOffline(
        title: String,
        description: String,
        startTime: Long,
        endTime: Long,
        location: String = "",
        isAllDay: Boolean = false
    ): Result<CalendarEvent> = withContext(Dispatchers.IO) {
        try {
            // Crear entrada en cola de sincronización
            val pendingSync = PendingCalendarSync(
                operationType = "CREATE",
                eventTitle = title,
                eventDescription = description,
                eventStartTime = startTime,
                eventEndTime = endTime,
                eventLocation = location,
                eventIsAllDay = isAllDay,
                createdAt = System.currentTimeMillis()
            )
            pendingSyncBox.put(pendingSync)

            // Crear evento temporal en caché
            val monthYearFormat = SimpleDateFormat("MM-yyyy", Locale.getDefault())
            val monthYear = monthYearFormat.format(Date(startTime))
            
            val cached = CachedCalendarEvent(
                googleEventId = "temp_${System.currentTimeMillis()}",
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                location = location,
                isAllDay = isAllDay,
                isSynced = false,
                monthYear = monthYear
            )
            cachedEventsBox.put(cached)

            Log.d(TAG, "Event created offline, queued for sync: $title")
            
            Result.success(
                CalendarEvent(
                    id = cached.googleEventId,
                    title = title,
                    description = description,
                    startTime = startTime,
                    endTime = endTime,
                    location = location,
                    isAllDay = isAllDay
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating offline event", e)
            Result.failure(e)
        }
    }

    /**
     * Sincroniza eventos pendientes con Google Calendar
     * Se debe llamar cuando se detecte conexión a internet
     */
    suspend fun syncPendingEvents(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val pendingEvents = pendingSyncBox.all
            
            if (pendingEvents.isEmpty()) {
                return@withContext Result.success(0)
            }

            val service = getCalendarService()
            if (service == null) {
                Log.w(TAG, "Cannot sync without Google Calendar service")
                return@withContext Result.failure(Exception("No service available"))
            }

            var syncedCount = 0
            val failedEvents = mutableListOf<PendingCalendarSync>()

            pendingEvents.forEach { pending ->
                try {
                    when (pending.operationType) {
                        "CREATE" -> {
                            val result = createEvent(
                                title = pending.eventTitle,
                                description = pending.eventDescription,
                                startTime = pending.eventStartTime,
                                endTime = pending.eventEndTime,
                                location = pending.eventLocation,
                                isAllDay = pending.eventIsAllDay
                            )
                            
                            if (result.isSuccess) {
                                // Eliminar de cola de sincronización
                                pendingSyncBox.remove(pending)
                                syncedCount++
                                Log.d(TAG, "Synced CREATE event: ${pending.eventTitle}")
                            } else {
                                failedEvents.add(pending)
                            }
                        }
                        "UPDATE" -> {
                            val result = updateEvent(
                                eventId = pending.googleEventId,
                                title = pending.eventTitle,
                                description = pending.eventDescription,
                                startTime = pending.eventStartTime,
                                endTime = pending.eventEndTime,
                                location = pending.eventLocation,
                                isAllDay = pending.eventIsAllDay
                            )
                            
                            if (result.isSuccess) {
                                pendingSyncBox.remove(pending)
                                syncedCount++
                                Log.d(TAG, "Synced UPDATE event: ${pending.eventTitle}")
                            } else {
                                failedEvents.add(pending)
                            }
                        }
                        "DELETE" -> {
                            val result = deleteEvent(pending.googleEventId)
                            
                            if (result.isSuccess) {
                                pendingSyncBox.remove(pending)
                                syncedCount++
                                Log.d(TAG, "Synced DELETE event: ${pending.googleEventId}")
                            } else {
                                failedEvents.add(pending)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing event: ${pending.eventTitle}", e)
                    pending.retryCount++
                    pending.lastError = e.message ?: "Unknown error"
                    pendingSyncBox.put(pending)
                    failedEvents.add(pending)
                }
            }

            Log.i(TAG, "Sync complete: $syncedCount synced, ${failedEvents.size} failed")
            Result.success(syncedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing pending events", e)
            Result.failure(e)
        }
    }
    /**
     * Obtiene el número de eventos pendientes de sincronización
     */
    fun getPendingSyncCount(): Long {
        return pendingSyncBox.count()
    }
}

/**
 * Modelo de datos para un evento del calendario
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val location: String,
    val isAllDay: Boolean
)
