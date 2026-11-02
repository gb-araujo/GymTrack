package app.gabrielaraujo.gymtrack.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.data.repository.ExerciseRepository
import app.gabrielaraujo.gymtrack.data.repository.GoalRepository
import app.gabrielaraujo.gymtrack.data.repository.PersonalRecordRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutSessionRepository
import app.gabrielaraujo.gymtrack.domain.health.BodyWeightEntry
import app.gabrielaraujo.gymtrack.domain.goals.GoalProgress
import app.gabrielaraujo.gymtrack.domain.progression.bestRecordsByExercise
import app.gabrielaraujo.gymtrack.domain.goals.goalProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Uma meta com o progresso calculado do histórico. */
data class GoalItem(val goal: Goal, val progress: GoalProgress, val exerciseName: String? = null)

data class GoalsUiState(
    val goals: List<GoalItem> = emptyList(),
    /** Há pesagens do Health Connect (sem elas, metas de peso corporal não progridem). */
    val hasBodyWeights: Boolean = false,
    /** Catálogo para escolher o exercício de uma meta de carga. */
    val exercises: List<Exercise> = emptyList(),
)

/** Metas: progresso calculado do histórico, criação e exclusão. */
class GoalsViewModel(
    private val goals: GoalRepository,
    sessions: WorkoutSessionRepository,
    records: PersonalRecordRepository,
    exercises: ExerciseRepository,
    bodyWeights: Flow<List<BodyWeightEntry>>,
    /** Escopo para gravações que devem terminar mesmo se a tela fechar. */
    private val appScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val state: StateFlow<GoalsUiState> = combine(
        goals.goals,
        sessions.completedSessions,
        records.personalRecords,
        exercises.exercises,
        bodyWeights,
    ) { goalList, done, recordList, exerciseList, weights ->
        val best = bestRecordsByExercise(recordList)
        val now = clock()
        GoalsUiState(
            goals = goalList.map { goal ->
                GoalItem(
                    goal = goal,
                    progress = goalProgress(goal, done, best, weights, now),
                    exerciseName = goal.exerciseId?.let { id -> exerciseList.find { it.id == id }?.name },
                )
            },
            hasBodyWeights = weights.isNotEmpty(),
            exercises = exerciseList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    /** @param exerciseId exercício de uma meta de carga (obrigatório nesse tipo). */
    fun add(title: String, type: String, target: Double, unit: String, exerciseId: String? = null) = appScope.launch {
        goals.saveGoal(Goal(id = newId(), type = type, title = title, target = target, unit = unit, exerciseId = exerciseId, createdAt = clock()))
    }

    fun delete(id: String) = appScope.launch { goals.deleteGoal(id) }
}
