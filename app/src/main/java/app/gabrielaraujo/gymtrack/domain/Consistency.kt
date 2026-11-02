package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.WorkoutSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * Regras de consistência.
 *
 * - Semana de calendário de segunda a domingo, no fuso do aparelho (o dia de um treino é o dia
 *   local em que ele começou). Semanas sem treino entram com zero.
 * - Média semanal: treinos ÷ semanas completas do período. A semana atual, ainda em andamento,
 *   não entra na média (puxaria a média para baixo no começo da semana); só é usada quando é a única.
 * - Semana consistente: pelo menos [minPerWeek] treinos. A sequência atual conta as semanas
 *   completas seguidas até a passada, mais a atual se ela já atingiu a meta (a semana atual
 *   ainda não terminada nunca quebra a sequência).
 * - Treinos = sessões concluídas (duas no mesmo dia contam 2); dias treinados = dias distintos.
 */

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

data class WeekCount(val weekStart: LocalDate, val count: Int)

/** Treinos por semana de [from] até [to] (inclusive), com zero nas semanas sem treino. */
fun weeklyCounts(dates: Collection<LocalDate>, from: LocalDate, to: LocalDate): List<WeekCount> {
    val byWeek = dates.groupingBy { it.weekStart() }.eachCount()
    val weeks = mutableListOf<WeekCount>()
    var week = from.weekStart()
    val last = to.weekStart()
    while (!week.isAfter(last)) {
        weeks += WeekCount(week, byWeek[week] ?: 0)
        week = week.plusWeeks(1)
    }
    return weeks
}

data class ConsistencyReport(
    val weeks: List<WeekCount>,
    val workouts: Int,
    val trainedDays: Set<LocalDate>,
    /** Treinos por semana (ver regras acima); null sem semanas no período. */
    val averagePerWeek: Double?,
    val currentStreakWeeks: Int,
    val bestStreakWeeks: Int,
    /** Treinos no período anterior de mesmo tamanho; null para "todo o histórico". */
    val previousPeriodWorkouts: Int?,
)

fun consistencyReport(
    workoutTimes: Collection<Long>,
    period: ReportPeriod,
    today: LocalDate,
    zone: ZoneId,
    minPerWeek: Int = 1,
): ConsistencyReport {
    val dates = workoutTimes.map { it.toLocalDate(zone) }.filter { !it.isAfter(today) }
    val start = period.startDate(today) ?: dates.minOrNull() ?: today
    val inPeriod = dates.filter { !it.isBefore(start) }
    val weeks = weeklyCounts(inPeriod, start, today)

    val currentWeek = today.weekStart()
    val complete = weeks.filter { it.weekStart.isBefore(currentWeek) }
    val average = when {
        complete.isNotEmpty() -> complete.sumOf { it.count }.toDouble() / complete.size
        weeks.isNotEmpty() -> weeks.sumOf { it.count }.toDouble()
        else -> null
    }

    // Sequências calculadas sobre todo o histórico, para não "cortar" uma sequência no início do período.
    val allWeeks = if (dates.isEmpty()) emptyList() else weeklyCounts(dates, dates.min(), today)
    var best = 0
    var run = 0
    for (w in allWeeks) {
        val inProgress = w.weekStart == currentWeek
        if (w.count >= minPerWeek) run++ else if (!inProgress) run = 0
        best = maxOf(best, run)
    }

    val previous = period.days?.let { days ->
        val prevStart = start.minusDays(days)
        dates.count { !it.isBefore(prevStart) && it.isBefore(start) }
    }

    return ConsistencyReport(
        weeks = weeks,
        workouts = inPeriod.size,
        trainedDays = inPeriod.toSet(),
        averagePerWeek = average,
        currentStreakWeeks = run,
        bestStreakWeeks = best,
        previousPeriodWorkouts = previous,
    )
}

data class WeekVolume(val weekStart: LocalDate, val volume: Double, val sessions: Int)

/**
 * Volume (carga × reps das séries válidas) por semana. Métrica complementar: depende de quais
 * exercícios entraram no treino, então subir ou cair não indica, sozinho, evolução.
 */
fun weeklyVolume(sessions: Collection<WorkoutSession>, from: LocalDate, to: LocalDate, zone: ZoneId): List<WeekVolume> {
    val byWeek = sessions
        .filter { it.completed && !it.deleted }
        .groupBy { it.startTime.toLocalDate(zone).weekStart() }
    return weeklyCounts(emptyList(), from, to).map { w ->
        val list = byWeek[w.weekStart].orEmpty()
        WeekVolume(w.weekStart, list.sumOf { sessionVolume(it) }, list.size)
    }
}

/** Volume recalculado a partir das séries (não depende do total gravado na sessão). */
fun sessionVolume(session: WorkoutSession): Double =
    session.exercises.sumOf { ex -> ex.sets.filter { it.isValidForReports() }.sumOf { it.weight * it.reps } }
