package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import java.time.LocalDate
import java.time.ZoneId

/*
 * Tendência de um exercício no período: compara a primeira com a última sessão do período,
 * só quando há pelo menos [MIN_SESSIONS_FOR_TREND] sessões com o exercício. Com menos que isso
 * não se tira conclusão. Exercícios não treinados no período não aparecem (não são "estagnados").
 *
 * Métrica usada: 1RM estimado quando todas as sessões o têm; senão a carga da melhor série
 * (só sessões com carga); exercícios só com peso corporal usam as repetições.
 */

const val MIN_SESSIONS_FOR_TREND = 3

enum class TrendDirection { UP, SAME, DOWN }

data class ExerciseTrend(
    val exerciseId: String,
    val name: String,
    val metric: ExerciseMetric,
    val first: Double,
    val last: Double,
    val sessions: Int,
) {
    val change: Double get() = last - first
    val direction: TrendDirection
        get() = when {
            change > EPSILON -> TrendDirection.UP
            change < -EPSILON -> TrendDirection.DOWN
            else -> TrendDirection.SAME
        }

    private companion object { const val EPSILON = 1e-6 }
}

fun exerciseTrend(exerciseId: String, name: String, history: List<ExerciseSessionEntry>): ExerciseTrend? {
    if (history.size < MIN_SESSIONS_FOR_TREND) return null
    val metric = when {
        history.all { it.estimatedOneRepMax != null } -> ExerciseMetric.ONE_REP_MAX
        history.all { it.bodyweightOnly } -> ExerciseMetric.REPS
        else -> ExerciseMetric.BEST_SET
    }
    val values = history.mapNotNull { it.metric(metric) }
    if (values.size < MIN_SESSIONS_FOR_TREND) return null
    return ExerciseTrend(exerciseId, name, metric, values.first(), values.last(), values.size)
}

data class ProgressOverview(
    val workouts: Int,
    val averagePerWeek: Double?,
    val prs: Int,
    /** Exercícios com dados suficientes para avaliar a tendência. */
    val trends: List<ExerciseTrend>,
) {
    val improving: List<ExerciseTrend> get() = trends.filter { it.direction == TrendDirection.UP }.sortedByDescending { it.change / it.first.coerceAtLeast(1.0) }
}

fun progressOverview(
    sessions: Collection<WorkoutSession>,
    catalog: Map<String, Exercise>,
    period: ReportPeriod,
    today: LocalDate,
    zone: ZoneId,
): ProgressOverview {
    val inPeriod = sessions.filter { it.completed && !it.deleted && period.contains(it.startTime, today, zone) }
    val consistency = consistencyReport(inPeriod.map { it.startTime }, period, today, zone)
    val ids = inPeriod.flatMap { s -> s.exercises.map { it.exerciseId } }.distinct()
    val trends = ids.mapNotNull { id ->
        val history = exerciseHistory(inPeriod, id)
        if (history.isEmpty()) return@mapNotNull null
        exerciseTrend(id, exerciseDisplayName(id, catalog[id], history.last().name), history)
    }
    return ProgressOverview(
        workouts = inPeriod.size,
        averagePerWeek = consistency.averagePerWeek,
        prs = inPeriod.sumOf { it.prsAchieved.size },
        trends = trends,
    )
}
