package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.WorkoutSession

/*
 * Auditoria de qualidade dos treinos gravados. Só aponta; nunca altera dados históricos.
 */

enum class DataIssue {
    /** Duração acima de [MAX_PLAUSIBLE_DURATION_MIN]: o treino provavelmente ficou aberto. */
    LONG_DURATION,

    /** Série concluída com carga 0 num exercício que não é de peso corporal. */
    ZERO_LOAD,

    /** Série concluída com 0 repetições (não entra nos cálculos). */
    ZERO_REPS,

    /** Totais gravados na sessão diferentes do recalculado a partir das séries. */
    STORED_TOTALS_MISMATCH,

    /** Outra sessão do mesmo dia de treino começou com menos de [DUPLICATE_WINDOW_MS] de diferença. */
    POSSIBLE_DUPLICATE,
}

const val MAX_PLAUSIBLE_DURATION_MIN = 4 * 60
const val DUPLICATE_WINDOW_MS = 5 * 60_000L

private val bodyweightEquipment = setOf("peso corporal", "paralelas", "banco romano")

fun auditSessions(sessions: Collection<WorkoutSession>, catalog: Map<String, Exercise>): Map<String, Set<DataIssue>> {
    val result = mutableMapOf<String, MutableSet<DataIssue>>()
    fun flag(id: String, issue: DataIssue) = result.getOrPut(id) { mutableSetOf() }.add(issue)

    val completed = sessions.filter { it.completed && !it.deleted }
    for (s in completed) {
        if ((s.duration ?: 0) > MAX_PLAUSIBLE_DURATION_MIN) flag(s.id, DataIssue.LONG_DURATION)
        for (ex in s.exercises) {
            val equipment = catalog[ex.exerciseId]?.equipment?.lowercase()
            val bodyweight = equipment == null || equipment in bodyweightEquipment
            for (set in ex.sets.filter { it.completed }) {
                if (set.reps <= 0) flag(s.id, DataIssue.ZERO_REPS)
                if (set.weight == 0.0 && !bodyweight) flag(s.id, DataIssue.ZERO_LOAD)
            }
        }
        val totals = sessionTotals(s.exercises)
        val storedVolume = s.totalVolume
        val storedSets = s.totalSets
        if ((storedVolume != null && kotlin.math.abs(storedVolume - totals.volume) > 0.01) || (storedSets != null && storedSets != totals.sets)) {
            flag(s.id, DataIssue.STORED_TOTALS_MISMATCH)
        }
    }

    completed.groupBy { it.workoutDayId }.values.forEach { group ->
        val sorted = group.sortedBy { it.startTime }
        sorted.zipWithNext().forEach { (a, b) ->
            if (b.startTime - a.startTime < DUPLICATE_WINDOW_MS) {
                flag(a.id, DataIssue.POSSIBLE_DUPLICATE)
                flag(b.id, DataIssue.POSSIBLE_DUPLICATE)
            }
        }
    }
    return result
}
