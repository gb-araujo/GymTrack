package app.gabrielaraujo.gymtrack.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.repository.WorkoutPlanRepository
import app.gabrielaraujo.gymtrack.domain.activePlanOf
import app.gabrielaraujo.gymtrack.domain.duplicatePlan
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
    /** Escopo para gravações que devem terminar mesmo se a tela fechar. */
    private val appScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val state: StateFlow<PlansUiState> = plans.plans
        .map { list -> PlansUiState(list, activePlanOf(list)?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    fun setActive(planId: String) = appScope.launch { plans.setActivePlan(planId) }

    fun delete(planId: String) = appScope.launch { plans.deletePlan(planId) }

    fun duplicate(plan: WorkoutPlan) = appScope.launch { plans.savePlan(duplicatePlan(plan, clock())) }

    /** Salva uma ficha importada (já com id novo). */
    fun import(plan: WorkoutPlan) = appScope.launch { plans.savePlan(plan) }
}
