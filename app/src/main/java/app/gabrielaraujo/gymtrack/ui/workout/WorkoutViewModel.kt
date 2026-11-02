package app.gabrielaraujo.gymtrack.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.SetRecord
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.repository.ExerciseRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutPlanRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutSessionRepository
import app.gabrielaraujo.gymtrack.session.ActiveSession
import app.gabrielaraujo.gymtrack.session.ActiveWorkoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutUiState(
    /** Treino em andamento, ou o recém-concluído enquanto o resumo está aberto. */
    val active: ActiveSession? = null,
    /** Ficha do treino (descanso e faixa de repetições de cada exercício). */
    val plan: WorkoutPlan? = null,
    val exercises: List<Exercise> = emptyList(),
    val healthImportEnabled: Boolean = false,
)

/** Execução do treino e resumo ao concluir. O estado em si é do [ActiveWorkoutManager] (escopo do app). */
class WorkoutViewModel(
    private val workout: ActiveWorkoutManager,
    private val plans: WorkoutPlanRepository,
    exercises: ExerciseRepository,
    sessions: WorkoutSessionRepository,
    healthImportEnabled: Flow<Boolean>,
    /** Escopo para gravações que devem terminar mesmo se a tela fechar. */
    private val appScope: CoroutineScope,
) : ViewModel() {
    val state: StateFlow<WorkoutUiState> = combine(
        workout.active,
        plans.plans,
        exercises.exercises,
        sessions.completedSessions,
        healthImportEnabled,
    ) { active, planList, exerciseList, done, importOn ->
        // O resumo acompanha o treino gravado (ex.: medidas do relógio que chegam depois de concluir).
        val shown = active?.takeIf { it.session.completed }
            ?.let { a -> done.find { it.id == a.session.id }?.let { a.copy(session = it) } }
            ?: active
        WorkoutUiState(
            active = shown,
            plan = planList.find { it.id == active?.session?.workoutPlanId },
            exercises = exerciseList,
            healthImportEnabled = importOn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState(active = workout.active.value))

    fun updateSet(exerciseIndex: Int, setIndex: Int, transform: (SetRecord) -> SetRecord) = workout.updateSet(exerciseIndex, setIndex, transform)

    fun addSet(exerciseIndex: Int) = workout.addSet(exerciseIndex)

    fun removeSet(exerciseIndex: Int, setIndex: Int) = workout.removeSet(exerciseIndex, setIndex)

    fun completeExercise(exerciseIndex: Int) = workout.completeExercise(exerciseIndex)

    fun uncompleteExercise(exerciseIndex: Int) = workout.uncompleteExercise(exerciseIndex)

    fun setCurrentExercise(index: Int) = workout.setCurrentExercise(index)

    fun substituteExercise(exerciseIndex: Int, newExerciseId: String) {
        val exercise = state.value.exercises.find { it.id == newExerciseId } ?: return
        workout.substituteExercise(exerciseIndex, exercise.id, exercise.name)
    }

    fun startRest(seconds: Int, exerciseId: String) = workout.startRest(seconds, exerciseId)

    fun adjustRest(deltaSeconds: Int) = workout.adjustRest(deltaSeconds)

    fun stopRest() = workout.stopRest()

    fun complete() {
        workout.complete()
    }

    fun abandon() = workout.abandon()

    fun dismissSummary() = workout.dismissSummary()

    /** Aceita a progressão sugerida: grava a nova carga inicial do exercício na ficha do treino. */
    fun acceptProgression(workoutExerciseId: String, weight: Double) {
        val planId = workout.active.value?.session?.workoutPlanId ?: return
        appScope.launch { plans.updateExerciseInitialWeight(planId, workoutExerciseId, weight) }
    }
}
