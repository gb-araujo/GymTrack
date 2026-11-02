package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.PersonalRecord
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import kotlin.math.min
import kotlin.math.roundToInt

/** Tipos de meta (gravados em [Goal.type]). */
object GoalTypes {
    const val WORKOUT_COUNT = "workout_count"
    const val WEEKLY_FREQUENCY = "weekly_frequency"
    const val EXERCISE_WEIGHT = "exercise_weight"
    const val BODY_WEIGHT = "body_weight"
    const val VOLUME = "volume"
}

/** Progresso de uma meta: valor atual e porcentagem (0..100). */
data class GoalProgress(val current: Double, val percent: Int) {
    val done: Boolean get() = percent >= 100
}

/**
 * Progresso de uma meta, calculado do histórico (não é gravado na meta).
 * @param records recorde atual por exerciseId (metas de carga num exercício).
 * @param bodyWeights pesagens, da mais antiga para a mais recente (metas de peso corporal).
 */
fun goalProgress(
    goal: Goal,
    sessions: Collection<WorkoutSession>,
    records: Map<String, PersonalRecord>,
    bodyWeights: List<BodyWeightEntry>,
    now: Long,
): GoalProgress {
    val latestWeight = bodyWeights.lastOrNull()?.kg
    val current = when (goal.type) {
        GoalTypes.WORKOUT_COUNT -> sessions.size.toDouble()
        GoalTypes.WEEKLY_FREQUENCY -> workoutsInLastWeek(sessions.map { it.startTime }, now).toDouble()
        GoalTypes.VOLUME -> sessions.sumOf { it.totalVolume ?: 0.0 }
        GoalTypes.EXERCISE_WEIGHT -> goal.exerciseId?.let { records[it]?.weight } ?: goal.current
        GoalTypes.BODY_WEIGHT -> latestWeight ?: goal.current
        else -> goal.current
    }
    val percent = when {
        // O de peso corporal parte da pesagem de quando a meta foi criada (serve para perder ou ganhar).
        goal.type == GoalTypes.BODY_WEIGHT && latestWeight != null -> {
            val start = weightAt(bodyWeights, goal.createdAt) ?: latestWeight
            (bodyWeightProgress(start, latestWeight, goal.target) * 100).roundToInt()
        }
        goal.target <= 0 -> 0
        else -> min(100, ((current / goal.target) * 100).roundToInt())
    }
    return GoalProgress(current, percent)
}
