/*
 *
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */

package io.orabel.orabelandroid.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import java.util.Date

@Entity
data class Chat(
    @Id var id: Long = 0,
    var name: String = "",
    var systemPrompt: String = "",
    var dateCreated: Date = Date(),
    var dateUsed: Date = Date(),
    var llmModelId: Long = -1L,
    var minP: Float = 0.05f,
    var temperature: Float = 1.0f,
    var isTask: Boolean = false,
)

@Entity
data class ChatMessage(
    @Id var id: Long = 0,
    var chatId: Long = 0,
    var message: String = "",
    var isUserMessage: Boolean = false,
)

@Entity
data class LLMModel(
    @Id var id: Long = 0,
    var name: String = "",
    var url: String = "",
    var path: String = "",
)

@Entity
data class Task(
    @Id var id: Long = 0,
    var name: String = "",
    var systemPrompt: String = "",
    var modelId: Long = -1,
    @Transient var modelName: String = "",
)

// === Salud ===
@Entity
data class HealthProfile(
    @Id var id: Long = 0,
    var age: Int = 0,
    var heightCm: Int = 0,
    var weightKg: Float = 0f,
    // Lista separada por comas de condiciones médicas relevantes
    var conditions: String = "",
    // Lista separada por comas de equipo disponible (mancuernas, banda elástica, etc.)
    var equipment: String = "",
    // Última generación de rutina base en JSON (estructura propia)
    var baseRoutineJson: String = "",
    var updatedAt: Date = Date(),
)

@Entity
data class HealthScheduleItem(
    @Id var id: Long = 0,
    // Hora en formato HH:mm (24h)
    var time24h: String = "07:00",
    var enabled: Boolean = true,
    // Título del recordatorio
    var title: String = "Recordatorio de Salud",
    // Descripción opcional del recordatorio
    var description: String = "",
    // ID del evento del calendario (si se sincroniza)
    var calendarEventId: Long = 0,
)

@Entity
data class ExerciseDay(
    @Id var id: Long = 0,
    @Index var dateIso: String = "", // YYYY-MM-DD
    // Rutina concreta del día en JSON (derivada de la base y/o generada online)
    var routineJson: String = "",
    var completed: Boolean = false,
    var completedAt: Date? = null,
    // Estado auto-reportado
    var mood: Int = 0,    // 1-5
    var energy: Int = 0,  // 1-5
    // Métricas estimadas de sesión
    var durationMinutes: Int = 0,
    var caloriesBurned: Int = 0,
)

@Entity
data class DailyTask(
    @Id var id: Long = 0,
    @Index var dateIso: String = "", // "yyyy-MM-dd"
    var title: String = "",
    var description: String = "",
    var completed: Boolean = false,
    var completedAt: Date? = null,
    var scheduledTime: String = "08:00", // Hora programada
    var category: String = "general" // ejercicio, trabajo, personal, etc.
)

@Entity
data class FoodEntry(
    @Id var id: Long = 0,
    @Index var dateIso: String = "", // "yyyy-MM-dd"
    var mealName: String = "",
    var calories: Int = 0,
    var description: String = "",
    var timestamp: Date = Date()
)

@Entity
data class DailyStats(
    @Id var id: Long = 0,
    @Index var dateIso: String = "", // "yyyy-MM-dd"
    var exerciseCompleted: Boolean = false,
    var tasksCompleted: Int = 0,
    var totalTasks: Int = 0,
    var caloriesConsumed: Int = 0,
    var createdAt: Date = Date()
)

// === Gamificación ===
@Entity
data class GamificationState(
    @Id var id: Long = 0,
    var points: Int = 0,
    var streak: Int = 0,
    var lastCompletedDateIso: String = ""
)

@Entity
data class Achievement(
    @Id var id: Long = 0,
    @Index var code: String = "", // p.ej. FIRST_WORKOUT, FIVE_DAYS_STREAK
    var title: String = "",
    var description: String = "",
    var achievedAt: Date = Date()
)

// === Diario de Salud (Gemini AI) ===
@Entity
data class HealthDiaryEntry(
    @Id var id: Long = 0,
    // Texto original del usuario tal como lo reportó
    var userReportText: String = "",
    // Categoría automática detectada por IA
    var category: String = "", // "symptom", "emotional", "accident", "general"
    // Timestamp exacto cuando se guardó
    var recordedAt: Date = Date(),
    // ID del chat donde se registró (para contexto)
    var chatId: Long = 0,
    // Extracto relevante identificado por IA (keywords)
    var extractedInfo: String = "",
    // Si el usuario expresó preocupación
    var userConcern: Boolean = false,
    // Nivel de preocupación (0-3: 0=bajo, 1=moderado, 2=alto, 3=urgente)
    var concernLevel: Int = 0
)
