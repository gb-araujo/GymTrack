package app.gabrielaraujo.gymtrack.domain.workout

import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import app.gabrielaraujo.gymtrack.data.WorkoutSession
import app.gabrielaraujo.gymtrack.data.newId

/**
 * Ficha ativa. Se duas chegarem ativas de aparelhos diferentes, vale a ativada por último
 * (critério determinístico: todos os aparelhos escolhem a mesma). Sem ativa, a primeira.
 */
fun activePlanOf(plans: List<WorkoutPlan>): WorkoutPlan? {
    val live = plans.filter { !it.deleted }
    return live.filter { it.isActive }.maxWithOrNull(compareBy<WorkoutPlan>({ it.updatedAt }, { it.id }))
        ?: live.firstOrNull()
}

/** Cópia independente da ficha (ids novos), inativa e começando do primeiro dia. */
fun duplicatePlan(plan: WorkoutPlan, now: Long): WorkoutPlan = plan.copy(
    id = newId(),
    name = "${plan.name} (cópia)",
    currentDayIndex = 0,
    createdAt = now,
    updatedAt = now,
    isActive = false,
    ownerId = null,
    dirty = false,
    deleted = false,
    days = plan.days.map { d -> d.copy(id = newId(), exercises = d.exercises.map { it.copy(id = newId()) }) },
)

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
