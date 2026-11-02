package app.gabrielaraujo.gymtrack.domain.progression

import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.SessionExercise
import app.gabrielaraujo.gymtrack.data.WorkoutSession

/** Volume (kg) e número de séries concluídas de uma sessão. */
data class SessionTotals(val volume: Double, val sets: Int)

fun sessionTotals(exercises: List<SessionExercise>): SessionTotals {
    var volume = 0.0
    var sets = 0
    for (ex in exercises) for (s in ex.sets) if (s.completed) {
        volume += s.weight * s.reps
        sets++
    }
    return SessionTotals(volume, sets)
}

data class BestSet(val weight: Double, val reps: Int)

/** Melhor série concluída: maior carga e, empatando, mais repetições. */
fun bestSet(exercise: SessionExercise): BestSet? {
    val done = exercise.sets.filter { it.completed }
    if (done.isEmpty()) return null
    val maxWeight = done.maxOf { it.weight }
    return BestSet(maxWeight, done.filter { it.weight == maxWeight }.maxOf { it.reps })
}

fun BestSet.beats(weight: Double, reps: Int): Boolean =
    this.weight > weight || (this.weight == weight && this.reps > reps)

fun BestSet.beats(record: PersonalRecord?): Boolean =
    record == null || beats(record.weight, record.reps)

/** Mantém, por exercício, o recorde de maior carga (desempate por reps). */
fun bestRecordsByExercise(records: Collection<PersonalRecord>): Map<String, PersonalRecord> {
    val map = LinkedHashMap<String, PersonalRecord>()
    for (pr in records) {
        val existing = map[pr.exerciseId]
        if (existing == null || BestSet(pr.weight, pr.reps).beats(existing)) map[pr.exerciseId] = pr
    }
    return map
}

data class RecomputedRecords(
    /** Recorde atual por exerciseId. */
    val records: Map<String, PersonalRecord>,
    /** exerciseIds que bateram recorde em cada sessão (na ordem cronológica). */
    val prsBySession: Map<String, List<String>>,
)

/**
 * Recalcula os recordes percorrendo as sessões em ordem cronológica.
 * [baseline] são recordes que não vêm de nenhuma sessão conhecida (ex.: importados)
 * e servem de ponto de partida.
 */
fun recomputeRecords(
    sessions: Collection<WorkoutSession>,
    baseline: Collection<PersonalRecord> = emptyList(),
): RecomputedRecords {
    val records = LinkedHashMap(bestRecordsByExercise(baseline))
    val prsBySession = LinkedHashMap<String, List<String>>()
    for (session in sessions.sortedBy { it.startTime }) {
        val achieved = mutableListOf<String>()
        val date = session.endTime ?: session.startTime
        for (ex in session.exercises) {
            val best = bestSet(ex) ?: continue
            if (best.beats(records[ex.exerciseId])) {
                records[ex.exerciseId] = PersonalRecord(
                    exerciseId = ex.exerciseId,
                    exerciseName = ex.name,
                    weight = best.weight,
                    reps = best.reps,
                    volume = best.weight * best.reps,
                    date = date,
                    sessionId = session.id,
                )
                if (ex.exerciseId !in achieved) achieved += ex.exerciseId
            }
        }
        prsBySession[session.id] = achieved
    }
    return RecomputedRecords(records, prsBySession)
}

private fun PersonalRecord.sameValues(other: PersonalRecord) =
    weight == other.weight && reps == other.reps && sessionId == other.sessionId &&
        date == other.date && exerciseName == other.exerciseName

/**
 * Compara os recordes gravados com os recalculados e devolve só o que precisa ser gravado:
 * recordes novos/alterados e tombstones dos que deixaram de existir.
 */
fun diffRecords(
    existing: Collection<PersonalRecord>,
    computed: Map<String, PersonalRecord>,
    now: Long,
    ownerId: String?,
): List<PersonalRecord> {
    val byId = existing.associateBy { it.exerciseId }
    val changes = mutableListOf<PersonalRecord>()
    for ((id, pr) in computed) {
        val old = byId[id]
        if (old == null || old.deleted || !old.sameValues(pr)) {
            changes += pr.copy(ownerId = ownerId ?: old?.ownerId, updatedAt = now, dirty = true, deleted = false)
        }
    }
    for (old in existing) {
        if (!old.deleted && old.exerciseId !in computed) {
            changes += old.copy(deleted = true, dirty = true, updatedAt = now, ownerId = ownerId ?: old.ownerId)
        }
    }
    return changes
}
