package app.gabrielaraujo.gymtrack.domain.statistics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Ofensiva: cada treino concluído soma +1, desde que o intervalo entre treinos
 * não passe de [STREAK_WINDOW_DAYS] dias. Ficar mais que isso sem treinar zera a contagem.
 *
 * Ex.: treino na segunda (dia 0) mantém a ofensiva viva até a segunda seguinte (dia 7);
 * na terça (dia 8) sem treinar, ela zera.
 */
const val STREAK_WINDOW_DAYS = 7L

data class StreakInfo(
    /** Treinos na sequência atual (0 se a ofensiva quebrou ou não há treinos). */
    val count: Int,
    /** Maior sequência já alcançada. */
    val best: Int,
    /** Data do último treino, se houver. */
    val lastWorkout: LocalDate?,
    /** Último dia para treinar sem perder a ofensiva (null se não há ofensiva ativa). */
    val deadline: LocalDate?,
    /** Dias restantes até [deadline] (0 = hoje é o último dia). */
    val daysLeft: Int,
) {
    val active: Boolean get() = count > 0
}

fun computeStreak(
    workoutTimes: Collection<Long>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): StreakInfo {
    if (workoutTimes.isEmpty()) return StreakInfo(0, 0, null, null, 0)
    val dates = workoutTimes.sorted().map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    var count = 0
    var best = 0
    var previous: LocalDate? = null
    for (date in dates) {
        count = if (previous != null && ChronoUnit.DAYS.between(previous, date) > STREAK_WINDOW_DAYS) 1 else count + 1
        best = maxOf(best, count)
        previous = date
    }

    val last = dates.last()
    val deadline = last.plusDays(STREAK_WINDOW_DAYS)
    return if (today.isAfter(deadline)) {
        StreakInfo(0, best, last, null, 0)
    } else {
        StreakInfo(count, best, last, deadline, ChronoUnit.DAYS.between(today, deadline).toInt().coerceAtLeast(0))
    }
}
