package app.gabrielaraujo.gymtrack.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.newId
import app.gabrielaraujo.gymtrack.data.repository.ExerciseRepository
import app.gabrielaraujo.gymtrack.data.repository.WorkoutPlanRepository
import app.gabrielaraujo.gymtrack.domain.PlanCodec
import app.gabrielaraujo.gymtrack.domain.workout.activePlanOf
import app.gabrielaraujo.gymtrack.domain.workout.duplicatePlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlansUiState(
    val plans: List<WorkoutPlan> = emptyList(),
    val activePlanId: String? = null,
)

/** Lista de fichas: ativar, duplicar, importar e excluir. */
class PlansViewModel(
    private val plans: WorkoutPlanRepository,
    exercises: ExerciseRepository,
    /** Escopo para gravações que devem terminar mesmo se a tela fechar. */
    private val appScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val state: StateFlow<PlansUiState> = plans.plans
        .map { list -> PlansUiState(list, activePlanOf(list)?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    /** Ids da biblioteca, para ligar os exercícios de uma ficha importada. */
    private val exerciseIds: StateFlow<Set<String>> = exercises.exercises
        .map { list -> list.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun setActive(planId: String) = appScope.launch { plans.setActivePlan(planId) }

    fun delete(planId: String) = appScope.launch { plans.deletePlan(planId) }

    fun duplicate(plan: WorkoutPlan) = appScope.launch { plans.savePlan(duplicatePlan(plan, clock())) }

    /**
     * Importa uma ficha compartilhada em JSON como ficha nova (ver [PlanCodec.decode]).
     * @return false se o texto não for uma ficha válida.
     */
    fun import(text: String): Boolean {
        val plan = PlanCodec.decode(text, exerciseIds.value, clock(), ::newId) ?: return false
        appScope.launch { plans.savePlan(plan) }
        return true
    }
}
