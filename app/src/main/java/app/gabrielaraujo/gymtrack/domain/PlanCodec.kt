package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.resolveExerciseId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ficha exportada para compartilhar (formato `{ "schemaVersion": 1, "workoutPlan": { ... } }`). */
@Serializable
private data class PlanExport(val schemaVersion: Int = 1, val workoutPlan: WorkoutPlan)

object PlanCodec {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun encode(plan: WorkoutPlan): String = json.encodeToString(PlanExport.serializer(), PlanExport(workoutPlan = plan))

    /**
     * Lê uma ficha compartilhada como uma ficha nova (id novo, inativa, no primeiro dia).
     * Cada `exerciseId` é ligado ao exercício da biblioteca quando bate por grafia ou apelido
     * (ex.: `"Agachamento-Hack"` ou `"hack_squat"` → `agachamento_hack`). Um id desconhecido é mantido:
     * a ficha nunca é recusada por causa de exercício.
     * @return null se o texto não for uma ficha válida.
     */
    fun decode(text: String, knownExerciseIds: Set<String>, now: Long, newId: () -> String): WorkoutPlan? {
        val plan = runCatching { json.decodeFromString(PlanExport.serializer(), text).workoutPlan }.getOrNull() ?: return null
        return plan.copy(
            id = newId(),
            currentDayIndex = 0,
            createdAt = now,
            updatedAt = now,
            isActive = false,
            days = plan.days.map { day ->
                day.copy(exercises = day.exercises.map { it.copy(exerciseId = resolveExerciseId(it.exerciseId, knownExerciseIds)) })
            },
        )
    }
}
