package app.gabrielaraujo.gymtrack.data.repository

import androidx.room.withTransaction
import app.gabrielaraujo.gymtrack.data.Goal
import app.gabrielaraujo.gymtrack.data.GymTrackDatabase
import kotlinx.coroutines.flow.Flow

/** Metas do usuário. O progresso de cada meta é calculado a partir do histórico (ver `domain`), não gravado. */
class GoalRepository(
    private val db: GymTrackDatabase,
    private val currentUid: () -> String? = { null },
    /** Chamado após cada escrita que entra na fila de envio (agenda a sincronização). */
    private val onPendingChange: () -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.dao()

    val goals: Flow<List<Goal>> = dao.observeGoals()

    suspend fun saveGoal(goal: Goal) {
        dao.upsertGoal(goal.copy(ownerId = currentUid() ?: goal.ownerId, updatedAt = clock(), dirty = true))
        onPendingChange()
    }

    suspend fun deleteGoal(id: String) {
        db.withTransaction {
            val goal = dao.getGoal(id) ?: return@withTransaction
            dao.upsertGoal(goal.copy(deleted = true, dirty = true, updatedAt = clock(), ownerId = currentUid() ?: goal.ownerId))
        }
        onPendingChange()
    }
}
