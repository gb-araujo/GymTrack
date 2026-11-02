package app.gabrielaraujo.gymtrack.domain.statistics

import app.gabrielaraujo.gymtrack.data.WorkoutSession
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private const val WEEK_MS = 7L * 86_400_000

/** Treinos feitos no mês de [today]. */
fun workoutsInMonth(
    workoutTimes: Collection<Long>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Int {
    val month = YearMonth.from(today)
    return workoutTimes.count { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) == month }
}

/**
 * Treinos por semana nas últimas [weeks] semanas (a mais antiga primeiro). A última janela termina
 * em [now], então inclui a semana corrente.
 */
fun weeklyCounts(workoutTimes: Collection<Long>, now: Long, weeks: Int = 8): List<Int> =
    (weeks - 1 downTo 0).map { i ->
        val end = now - i * WEEK_MS
        val start = end - WEEK_MS
        workoutTimes.count { it > start && it <= end }
    }

/** Treinos nos últimos 7 dias até [now]. */
fun workoutsInLastWeek(workoutTimes: Collection<Long>, now: Long): Int = workoutTimes.count { it >= now - WEEK_MS }

/**
 * Evolução de carga de um exercício: a maior carga das séries concluídas em cada treino,
 * em ordem cronológica (treinos sem carga, como os de peso corporal, ficam de fora).
 */
fun loadHistory(sessions: Collection<WorkoutSession>, exerciseId: String): List<Double> =
    sessions.sortedBy { it.startTime }
        .mapNotNull { s -> s.exercises.find { it.exerciseId == exerciseId } }
        .mapNotNull { ex -> ex.sets.filter { it.completed }.maxOfOrNull { it.weight } }
        .filter { it > 0 }
