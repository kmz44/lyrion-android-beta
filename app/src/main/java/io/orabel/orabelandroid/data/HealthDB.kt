package io.orabel.orabelandroid.data

import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import io.objectbox.query.QueryBuilder
import org.koin.core.annotation.Single
import java.text.SimpleDateFormat
import java.util.*

@Single
class HealthDB {
    private val profileBox = ObjectBoxStore.store.boxFor(HealthProfile::class.java)
    private val scheduleBox = ObjectBoxStore.store.boxFor(HealthScheduleItem::class.java)
    private val dayBox = ObjectBoxStore.store.boxFor(ExerciseDay::class.java)
    private val taskBox = ObjectBoxStore.store.boxFor(DailyTask::class.java)
    private val foodBox = ObjectBoxStore.store.boxFor(FoodEntry::class.java)
    private val statsBox = ObjectBoxStore.store.boxFor(DailyStats::class.java)
    private val gamificationBox = ObjectBoxStore.store.boxFor(GamificationState::class.java)
    private val achievementBox = ObjectBoxStore.store.boxFor(Achievement::class.java)

    // === Profile ===
    fun getOrCreateProfile(): HealthProfile {
        val all = profileBox.all
        return if (all.isEmpty()) {
            val p = HealthProfile()
            profileBox.put(p)
            p
        } else all.first()
    }

    fun saveProfile(p: HealthProfile) {
        profileBox.put(p)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeProfile(): Flow<List<HealthProfile>> =
        profileBox.query().build().flow().flowOn(Dispatchers.IO)

    // === Schedules ===
    fun getSchedules(): List<HealthScheduleItem> = scheduleBox.all
    fun saveSchedule(item: HealthScheduleItem) { scheduleBox.put(item) }
    fun deleteSchedule(id: Long) { scheduleBox.remove(id) }

    // === Exercise Days ===
    fun upsertExerciseDay(day: ExerciseDay) { dayBox.put(day) }
    fun getExerciseDayByDate(dateIso: String): ExerciseDay? =
        dayBox.all.firstOrNull { it.dateIso == dateIso }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeExerciseDays(): Flow<List<ExerciseDay>> =
        dayBox.query().order(ExerciseDay_.dateIso).build().flow().flowOn(Dispatchers.IO)

    // === Daily Tasks ===
    fun getTasks(dateIso: String): List<DailyTask> =
        taskBox.all.filter { it.dateIso == dateIso }
    
    fun saveTask(task: DailyTask) { taskBox.put(task) }
    fun deleteTask(id: Long) { taskBox.remove(id) }
    
    fun getTasksForNext30Days(): List<DailyTask> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, 30)
        val future = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        return taskBox.all.filter { it.dateIso >= today && it.dateIso <= future }
    }

    // === Food Entries ===
    fun getFoodEntries(dateIso: String): List<FoodEntry> =
        foodBox.all.filter { it.dateIso == dateIso }
    
    fun saveFoodEntry(entry: FoodEntry) { foodBox.put(entry) }
    fun deleteFoodEntry(id: Long) { foodBox.remove(id) }
    
    fun getTotalCalories(dateIso: String): Int =
        getFoodEntries(dateIso).sumOf { it.calories }

    // === Statistics ===
    fun getOrCreateDailyStats(dateIso: String): DailyStats {
        val existing = statsBox.all.firstOrNull { it.dateIso == dateIso }
        return existing ?: DailyStats(dateIso = dateIso).also { statsBox.put(it) }
    }
    
    fun updateDailyStats(dateIso: String) {
        val stats = getOrCreateDailyStats(dateIso)
        val exerciseDay = getExerciseDayByDate(dateIso)
        val tasks = getTasks(dateIso)
        val calories = getTotalCalories(dateIso)
        
        stats.exerciseCompleted = exerciseDay?.completed ?: false
        stats.tasksCompleted = tasks.count { it.completed }
        stats.totalTasks = tasks.size
        stats.caloriesConsumed = calories
        
        statsBox.put(stats)
    }
    
    fun getStatsForLast30Days(): List<DailyStats> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        val past30 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        return statsBox.all.filter { it.dateIso >= past30 && it.dateIso <= today }
            .sortedBy { it.dateIso }
    }

    // === Gamificación ===
    fun getGamification(): GamificationState {
        val all = gamificationBox.all
        return if (all.isEmpty()) GamificationState().also { gamificationBox.put(it) } else all.first()
    }

    fun addPoints(points: Int, dateIso: String) {
        val state = getGamification()
        state.points += points
        // streak
        val last = state.lastCompletedDateIso
        if (last.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val lastDate = sdf.parse(last)
            val current = sdf.parse(dateIso)
            if (lastDate != null && current != null) {
                val diff = ((current.time - lastDate.time) / (1000 * 60 * 60 * 24)).toInt()
                state.streak = if (diff == 1) state.streak + 1 else if (diff == 0) state.streak else 1
            } else {
                // Si hay problema de parseo, reiniciar racha de forma segura
                state.streak = 1
            }
        } else {
            state.streak = 1
        }
        state.lastCompletedDateIso = dateIso
        gamificationBox.put(state)

        // achievements simples
        if (state.points >= 100 && achievementBox.all.none { it.code == "POINTS_100" }) {
            achievementBox.put(
                Achievement(
                    code = "POINTS_100",
                    title = "100 puntos",
                    description = "Has acumulado 100 puntos"
                )
            )
        }
        if (state.streak >= 5 && achievementBox.all.none { it.code == "STREAK_5" }) {
            achievementBox.put(
                Achievement(
                    code = "STREAK_5",
                    title = "Racha de 5 días",
                    description = "Entrenaste 5 días seguidos"
                )
            )
        }
    }

    fun getAchievements(): List<Achievement> = achievementBox.all.sortedBy { it.achievedAt }
}
