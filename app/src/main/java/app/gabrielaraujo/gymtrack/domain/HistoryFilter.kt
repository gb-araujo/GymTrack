package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.WorkoutSession
import java.text.Normalizer
import java.time.LocalDate
import java.time.ZoneId

/** Filtros da lista do histórico. */
data class HistoryFilter(
    val query: String = "",
    val period: ReportPeriod = ReportPeriod.ALL,
    /** Ficha (workoutPlanId); null = todas. */
    val planId: String? = null,
    val onlyWithRecords: Boolean = false,
) {
    val isActive: Boolean get() = query.isNotBlank() || period != ReportPeriod.ALL || planId != null || onlyWithRecords
}

/** Texto sem acentos e em minúsculas, para a busca achar "supino" em "Supino Reto" e "rosca" em "Rosca". */
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

/** A busca considera o nome do treino, a letra do dia e os exercícios feitos. */
fun WorkoutSession.matchesQuery(query: String): Boolean {
    val q = normalizeForSearch(query)
    if (q.isEmpty()) return true
    return normalizeForSearch(workoutDayName).contains(q) ||
        normalizeForSearch(workoutDayLabel) == q ||
        exercises.any { normalizeForSearch(it.name).contains(q) }
}

fun filterSessions(sessions: List<WorkoutSession>, filter: HistoryFilter, today: LocalDate, zone: ZoneId): List<WorkoutSession> =
    sessions.filter { s ->
        filter.period.contains(s.startTime, today, zone) &&
            (filter.planId == null || s.workoutPlanId == filter.planId) &&
            (!filter.onlyWithRecords || s.prsAchieved.isNotEmpty()) &&
            s.matchesQuery(filter.query)
    }
