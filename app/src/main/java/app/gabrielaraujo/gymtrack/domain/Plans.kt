package app.gabrielaraujo.gymtrack.domain

import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession

/**
 * Ficha ativa. Se duas chegarem ativas de aparelhos diferentes, vale a ativada por último
 * (critério determinístico: todos os aparelhos escolhem a mesma). Sem ativa, a primeira.
 */
fun activePlanOf(plans: List<WorkoutPlan>): WorkoutPlan? {
    val live = plans.filter { !it.deleted }
    return live.filter { it.isActive }.maxWithOrNull(compareBy<WorkoutPlan>({ it.updatedAt }, { it.id }))
        ?: live.firstOrNull()
}

/**
 * Índice do próximo treino sugerido da ficha: o dia seguinte ao do último treino concluído dela.
 * Derivado do histórico (não é gravado na ficha), então concluir um treino não altera a ficha
 * e não disputa a sincronização com edições feitas nela em outro aparelho.
 * Sem treinos da ficha, usa o [WorkoutPlan.currentDayIndex] gravado.
 */
fun nextDayIndex(plan: WorkoutPlan, sessions: Collection<WorkoutSession>): Int {
    if (plan.days.isEmpty()) return 0
    val last = sessions
        .filter { it.workoutPlanId == plan.id && it.completed && !it.deleted }
        .maxByOrNull { it.endTime ?: it.startTime }
    val doneIndex = last?.let { s -> plan.days.indexOfFirst { it.id == s.workoutDayId } } ?: -1
    return if (doneIndex >= 0) (doneIndex + 1) % plan.days.size else plan.currentDayIndex.mod(plan.days.size)
}
