package io.orabel.orabelandroid.data

import java.text.SimpleDateFormat
import java.util.*

data class WeeklyReport(
    val fromDateIso: String,
    val toDateIso: String,
    val totalExerciseMinutes: Int,
    val estimatedCalories: Int,
    val adherence: Float, // 0..1
    val improvements: String,
    val commitmentScore: Int // 0..100
)

object HealthAnalytics {
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun last7DaysReport(db: HealthDB): WeeklyReport {
        val today = sdf.format(Date())
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val startIso = sdf.format(cal.time)

        val stats = db.getStatsForLast30Days().filter { it.dateIso >= startIso }
        val days = stats.size.coerceAtLeast(1)

        var totalMinutes = 0
        var estimatedCalories = 0
        var completedDays = 0

        // Complementar con ExerciseDay para métricas
        for (i in 0 until days) {
            val dateIso = stats[i].dateIso
            val day = db.getExerciseDayByDate(dateIso)
            if (day?.completed == true) {
                completedDays++
                totalMinutes += day.durationMinutes
                estimatedCalories += day.caloriesBurned
            }
        }

        val adherence = completedDays.toFloat() / days.toFloat()
        val commitment = (adherence * 70 + (totalMinutes.coerceAtMost(300) / 300f) * 30).toInt()

        val improvements = when {
            adherence < 0.4 -> "Recomendación: reduce la duración o programa recordatorios más temprano."
            adherence < 0.7 -> "Vas bien. Intenta bloques cortos entre 10-15 min para mejorar constancia."
            else -> "Excelente adherencia. Considera subir 5-10% la intensidad."
        }

        return WeeklyReport(
            fromDateIso = startIso,
            toDateIso = today,
            totalExerciseMinutes = totalMinutes,
            estimatedCalories = estimatedCalories,
            adherence = adherence,
            improvements = improvements,
            commitmentScore = commitment.coerceIn(0, 100)
        )
    }

    fun detectDropPattern(db: HealthDB, thresholdDays: Int = 3): Boolean {
        // True si no hay día completado en los últimos thresholdDays
        for (i in 0 until thresholdDays) {
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
            val iso = sdf.format(cal.time)
            val day = db.getExerciseDayByDate(iso)
            if (day?.completed == true) return false
        }
        return true
    }
}
