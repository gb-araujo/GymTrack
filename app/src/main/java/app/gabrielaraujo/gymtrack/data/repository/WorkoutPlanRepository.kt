package app.gabrielaraujo.gymtrack.data.repository

import androidx.room.withTransaction
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import app.gabrielaraujo.gymtrack.data.WorkoutPlan
import kotlinx.coroutines.flow.Flow

/**
 * Fichas de treino. Toda escrita marca a ficha como `dirty` e avança `updatedAt` (base do LWW).
 * Leitura e gravação acontecem na mesma transação, para não regravar uma versão que a
 * sincronização tenha acabado de trazer.
 */
class WorkoutPlanRepository(
    private val db: GymTrackDatabase,
    private val currentUid: () -> String? = { null },
    /** Chamado após cada escrita que entra na fila de envio (agenda a sincronização). */
    private val onPendingChange: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    val plans: Flow<List<WorkoutPlan>> = dao.observePlans()

    suspend fun plansSnapshot(): List<WorkoutPlan> = dao.getPlans()

    /** A ficha, ou null se não existe ou foi excluída. */
    suspend fun getPlan(id: String): WorkoutPlan? = dao.getPlan(id)?.takeIf { !it.deleted }

    /**
     * Salva nome e dias da ficha (o que o editor muda). Ficha ativa e exclusão vêm do banco, que pode
     * ter mudado (ex.: pela sincronização) enquanto o editor estava aberto com uma cópia.
     */
    suspend fun savePlan(plan: WorkoutPlan) = edit {
        val base = dao.getPlan(plan.id)?.copy(name = plan.name, days = plan.days, deleted = false) ?: plan
        dao.upsertPlan(base.copy(updatedAt = clock(), ownerId = currentUid() ?: base.ownerId, dirty = true))
    }

    suspend fun setActivePlan(planId: String) = edit {
        val now = clock()
        val updated = dao.getPlans().filter { !it.deleted }.mapNotNull { p ->
            val shouldBeActive = p.id == planId
            if (p.isActive != shouldBeActive) {
                p.copy(isActive = shouldBeActive, updatedAt = now, ownerId = currentUid() ?: p.ownerId, dirty = true)
            } else null
        }
        if (updated.isNotEmpty()) dao.upsertPlans(updated)
    }

    suspend fun deletePlan(planId: String) = edit {
        val plan = dao.getPlan(planId) ?: return@edit
        dao.upsertPlan(plan.copy(deleted = true, isActive = false, dirty = true, updatedAt = clock(), ownerId = currentUid() ?: plan.ownerId))
    }

    suspend fun updateExerciseInitialWeight(planId: String, workoutExerciseId: String, weight: Double) = edit {
        val plan = dao.getPlan(planId) ?: return@edit
        val days = plan.days.map { day ->
            day.copy(exercises = day.exercises.map { ex -> if (ex.id == workoutExerciseId) ex.copy(initialWeight = weight) else ex })
        }
        dao.upsertPlan(plan.copy(days = days, updatedAt = clock(), ownerId = currentUid() ?: plan.ownerId, dirty = true))
    }

    private suspend fun edit(block: suspend () -> Unit) {
        db.withTransaction { block() }
        onPendingChange()
    }
}
