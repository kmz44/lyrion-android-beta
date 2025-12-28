package io.orabel.orabelandroid.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import io.orabel.orabelandroid.auth.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Repositorio para acceder a Google Classroom API mediante REST (SOLO LECTURA)
 * Usado por modelos LLM online para leer tareas pendientes del usuario
 * Usa OkHttp + Gson en lugar de biblioteca cliente de Google
 */
class GoogleClassroomRepository(private val context: Context) {

    companion object {
        private const val TAG = "GoogleClassroomRepository"
        private const val BASE_URL = "https://classroom.googleapis.com/v1"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Ejecuta una request HTTP con retry automático si el token expira
     */
    private suspend fun executeWithTokenRefresh(requestBuilder: (String) -> Request): okhttp3.Response {
        var providerToken = LoginActivity.getProviderToken(context)
        
        if (providerToken.isNullOrEmpty()) {
            throw Exception("No provider token available. User needs to login with Google.")
        }
        
        // Primera intento con token actual
        var request = requestBuilder(providerToken)
        var response = httpClient.newCall(request).execute()
        
        // Si recibimos 401 (token expirado), intentar refrescar
        if (response.code == 401) {
            response.close() // Cerrar respuesta anterior
            
            Log.w(TAG, "⚠️ Token expirado (401), intentando refrescar...")
            val newToken = LoginActivity.refreshProviderToken(context)
            
            if (newToken != null) {
                // Reintentar con nuevo token
                Log.d(TAG, "🔄 Reintentando request con token refrescado")
                request = requestBuilder(newToken)
                response = httpClient.newCall(request).execute()
            } else {
                throw Exception("No se pudo refrescar el token de autenticación")
            }
        }
        
        return response
    }

    /**
     * Obtiene todos los cursos activos del usuario
     */
    suspend fun getCourses(): Result<List<ClassroomCourse>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithTokenRefresh { token ->
                Request.Builder()
                    .url("$BASE_URL/courses?courseStates=ACTIVE&pageSize=100")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()
            }
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Error loading courses: ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("Error ${response.code}: $errorBody"))
            }

            val responseBody = response.body?.string() ?: "{}"
            val coursesResponse = gson.fromJson(responseBody, CoursesResponse::class.java)

            val courses = coursesResponse.courses?.map { course ->
                ClassroomCourse(
                    id = course.id ?: "",
                    name = course.name ?: "Sin nombre",
                    section = course.section ?: "",
                    descriptionHeading = course.descriptionHeading ?: "",
                    room = course.room ?: "",
                    ownerId = course.ownerId ?: "",
                    enrollmentCode = course.enrollmentCode ?: ""
                )
            } ?: emptyList()

            Log.d(TAG, "Successfully loaded ${courses.size} active courses from Google Classroom")
            Result.success(courses)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading courses", e)
            Result.failure(e)
        }
    }


    /**
     * Obtiene las tareas (coursework) de un curso específico
     */
    suspend fun getCourseWork(courseId: String): Result<List<ClassroomAssignment>> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithTokenRefresh { token ->
                Request.Builder()
                    .url("$BASE_URL/courses/$courseId/courseWork?pageSize=100")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()
            }
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Error loading course work: ${response.code} - $errorBody")
                return@withContext Result.failure(Exception("Error ${response.code}"))
            }

            val responseBody = response.body?.string() ?: "{}"
            val courseWorkResponse = gson.fromJson(responseBody, CourseWorkResponse::class.java)

            val assignments = courseWorkResponse.courseWork?.map { work ->
                ClassroomAssignment(
                    id = work.id ?: "",
                    courseId = work.courseId ?: "",
                    title = work.title ?: "Sin título",
                    description = work.description ?: "",
                    state = work.state ?: "PUBLISHED",
                    creationTime = parseGoogleTimestamp(work.creationTime ?: ""),
                    updateTime = parseGoogleTimestamp(work.updateTime ?: ""),
                    dueDate = work.dueDate?.let { date ->
                        try {
                            parseDueDate(date, work.dueTime)
                        } catch (e: Exception) {
                            null
                        }
                    },
                    maxPoints = work.maxPoints ?: 0.0,
                    workType = work.workType ?: "ASSIGNMENT"
                )
            } ?: emptyList()

            Log.d(TAG, "Successfully loaded ${assignments.size} assignments from course $courseId")
            Result.success(assignments)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading course work for course $courseId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Parsea timestamp de Google (formato ISO 8601)
     */
    private fun parseGoogleTimestamp(timestamp: String): Long {
        return try {
            if (timestamp.isEmpty()) return 0L
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            format.parse(timestamp)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Parsea la fecha de entrega desde el formato de Google Classroom
     */
    private fun parseDueDate(date: ClassroomDate, time: ClassroomTime?): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(
            date.year ?: 2024,
            (date.month ?: 1) - 1,
            date.day ?: 1,
            time?.hours ?: 23,
            time?.minutes ?: 59,
            0
        )
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Verifica si una tarea ha sido entregada por el usuario
     */
    suspend fun getSubmissionStatus(courseId: String, courseWorkId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = executeWithTokenRefresh { token ->
                Request.Builder()
                    .url("$BASE_URL/courses/$courseId/courseWork/$courseWorkId/studentSubmissions?pageSize=1")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()
            }
            
            if (!response.isSuccessful) {
                return@withContext Result.success(false)
            }

            val responseBody = response.body?.string() ?: "{}"
            val submissionsResponse = gson.fromJson(responseBody, SubmissionsResponse::class.java)

            val submission = submissionsResponse.studentSubmissions?.firstOrNull()
            val isSubmitted = submission?.state == "TURNED_IN" || submission?.state == "RETURNED"

            Result.success(isSubmitted)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking submission status", e)
            Result.success(false)
        }
    }

    /**
     * Obtiene TODAS las tareas pendientes de TODOS los cursos activos
     * Útil para que el LLM tenga una vista completa de las tareas del usuario
     */
    suspend fun getAllPendingAssignments(): Result<List<ClassroomAssignmentWithCourse>> = withContext(Dispatchers.IO) {
        try {
            // Obtener todos los cursos activos
            val coursesResult = getCourses()
            if (coursesResult.isFailure) {
                return@withContext Result.failure(coursesResult.exceptionOrNull()!!)
            }

            val courses = coursesResult.getOrNull() ?: emptyList()
            val allAssignments = mutableListOf<ClassroomAssignmentWithCourse>()

            // Para cada curso, obtener sus tareas
            for (course in courses) {
                val assignmentsResult = getCourseWork(course.id)
                if (assignmentsResult.isSuccess) {
                    val assignments = assignmentsResult.getOrNull() ?: emptyList()
                    
                    // Filtrar solo tareas con fecha de entrega futura o sin fecha
                    val now = System.currentTimeMillis()
                    val pendingAssignments = assignments.filter { assignment ->
                        assignment.dueDate == null || assignment.dueDate >= now
                    }

                    // Combinar con información del curso
                    pendingAssignments.forEach { assignment ->
                        allAssignments.add(
                            ClassroomAssignmentWithCourse(
                                assignment = assignment,
                                courseName = course.name,
                                courseSection = course.section
                            )
                        )
                    }
                }
            }

            // Ordenar por fecha de entrega (más cercana primero)
            val sortedAssignments = allAssignments.sortedBy { it.assignment.dueDate ?: Long.MAX_VALUE }

            Log.d(TAG, "Successfully loaded ${sortedAssignments.size} pending assignments from ${courses.size} courses")
            Result.success(sortedAssignments)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all pending assignments", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene un resumen de tareas para el LLM
     * Formato fácil de leer para el modelo
     */
    suspend fun getAssignmentsSummaryForLLM(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val assignmentsResult = getAllPendingAssignments()
            if (assignmentsResult.isFailure) {
                return@withContext Result.failure(assignmentsResult.exceptionOrNull()!!)
            }

            val assignments = assignmentsResult.getOrNull() ?: emptyList()

            if (assignments.isEmpty()) {
                return@withContext Result.success("No tienes tareas pendientes en Google Classroom.")
            }

            val summary = buildString {
                appendLine("=== TAREAS PENDIENTES EN GOOGLE CLASSROOM ===\n")
                appendLine("Total de tareas pendientes: ${assignments.size}\n")

                assignments.forEachIndexed { index, item ->
                    appendLine("${index + 1}. ${item.assignment.title}")
                    appendLine("   Curso: ${item.courseName} ${if (item.courseSection.isNotEmpty()) "- ${item.courseSection}" else ""}")
                    
                    if (item.assignment.description.isNotEmpty()) {
                        appendLine("   Descripción: ${item.assignment.description.take(200)}")
                    }
                    
                    item.assignment.dueDate?.let { dueDate ->
                        val date = Date(dueDate)
                        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale("es", "ES"))
                        appendLine("   Fecha de entrega: ${dateFormat.format(date)}")
                    } ?: appendLine("   Sin fecha de entrega")
                    
                    if (item.assignment.maxPoints > 0) {
                        appendLine("   Puntos: ${item.assignment.maxPoints}")
                    }
                    
                    appendLine()
                }
            }

            Result.success(summary)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating assignments summary", e)
            Result.failure(e)
        }
    }
}

// ============= MODELOS DE RESPUESTA JSON de Google Classroom API =============

data class CoursesResponse(
    @SerializedName("courses") val courses: List<CourseJson>?
)

data class CourseJson(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("section") val section: String?,
    @SerializedName("descriptionHeading") val descriptionHeading: String?,
    @SerializedName("room") val room: String?,
    @SerializedName("ownerId") val ownerId: String?,
    @SerializedName("enrollmentCode") val enrollmentCode: String?
)

data class CourseWorkResponse(
    @SerializedName("courseWork") val courseWork: List<CourseWorkJson>?
)

data class CourseWorkJson(
    @SerializedName("id") val id: String?,
    @SerializedName("courseId") val courseId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("creationTime") val creationTime: String?,
    @SerializedName("updateTime") val updateTime: String?,
    @SerializedName("dueDate") val dueDate: ClassroomDate?,
    @SerializedName("dueTime") val dueTime: ClassroomTime?,
    @SerializedName("maxPoints") val maxPoints: Double?,
    @SerializedName("workType") val workType: String?
)

data class ClassroomDate(
    @SerializedName("year") val year: Int?,
    @SerializedName("month") val month: Int?,
    @SerializedName("day") val day: Int?
)

data class ClassroomTime(
    @SerializedName("hours") val hours: Int?,
    @SerializedName("minutes") val minutes: Int?
)

data class SubmissionsResponse(
    @SerializedName("studentSubmissions") val studentSubmissions: List<StudentSubmission>?
)

data class StudentSubmission(
    @SerializedName("id") val id: String?,
    @SerializedName("courseWorkId") val courseWorkId: String?,
    @SerializedName("userId") val userId: String?,
    @SerializedName("state") val state: String?,
    @SerializedName("assignedGrade") val assignedGrade: Double?,
    @SerializedName("draftGrade") val draftGrade: Double?
)

// ============= MODELOS DE DATOS PARA LA APP =============

/**
 * Modelo de datos para un curso de Classroom
 */
data class ClassroomCourse(
    val id: String,
    val name: String,
    val section: String,
    val descriptionHeading: String,
    val room: String,
    val ownerId: String,
    val enrollmentCode: String
)

/**
 * Modelo de datos para una tarea de Classroom
 */
data class ClassroomAssignment(
    val id: String,
    val courseId: String,
    val title: String,
    val description: String,
    val state: String,
    val creationTime: Long,
    val updateTime: Long,
    val dueDate: Long?,  // Timestamp en milisegundos, null si no tiene fecha
    val maxPoints: Double,
    val workType: String  // ASSIGNMENT, SHORT_ANSWER_QUESTION, MULTIPLE_CHOICE_QUESTION
)

/**
 * Modelo combinado de tarea con información del curso
 */
data class ClassroomAssignmentWithCourse(
    val assignment: ClassroomAssignment,
    val courseName: String,
    val courseSection: String
)
