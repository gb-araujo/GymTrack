package app.gabrielaraujo.gymtrack.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.Exercise
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.data.repository.ExerciseRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutPlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlanEditorUiState(
    /** Rascunho em edição (null enquanto carrega). */
    val plan: WorkoutPlan? = null,
    val exercises: List<Exercise> = emptyList(),
)

/**
 * Editor de ficha. O rascunho fica no ViewModel (sobrevive à rotação da tela) e só vai para o banco
 * ao salvar; o repositório mescla nome e dias com o que mudou na ficha enquanto o editor estava aberto.
 * @param planId ficha a editar; null cria uma nova.
 */
class PlanEditorViewModel(
    planId: String?,
    private val plans: WorkoutPlanRepository,
    exercises: ExerciseRepository,
    /** Escopo para gravações que devem terminar mesmo se a tela fechar. */
    private val appScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val draft = MutableStateFlow<WorkoutPlan?>(null)

    val state: StateFlow<PlanEditorUiState> = combine(draft, exercises.exercises) { plan, list -> PlanEditorUiState(plan, list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanEditorUiState())

    init {
        viewModelScope.launch {
            val existing = planId?.let { plans.getPlan(it) }
            draft.compareAndSet(null, existing ?: newPlan())
        }
    }

    private fun newPlan(): WorkoutPlan {
        val now = clock()
        return WorkoutPlan(id = newId(), name = "Nova Ficha", createdAt = now, updatedAt = now)
    }

    fun update(transform: (WorkoutPlan) -> WorkoutPlan) = draft.update { it?.let(transform) }

    fun save() {
        val plan = draft.value ?: return
        appScope.launch { plans.savePlan(plan) }
    }
}
